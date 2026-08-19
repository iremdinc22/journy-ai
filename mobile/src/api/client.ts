import { NativeModules, Platform } from 'react-native';

import { session } from './session';
import type { AuthResponse } from './types';

declare const process: {
  env?: Record<string, string | undefined>;
};

const localHost = Platform.OS === 'android' ? '10.0.2.2' : 'localhost';
const configuredBaseUrl = process.env?.EXPO_PUBLIC_API_BASE_URL;
const REQUEST_TIMEOUT_MS = 3500;
const LONG_REQUEST_TIMEOUT_MS = 45000;

function resolveDevHost() {
  const scriptUrl = NativeModules.SourceCode?.scriptURL as string | undefined;
  const hostMatch = scriptUrl?.match(/^[^:]+:\/\/([^:/]+)/);

  return hostMatch?.[1] ?? localHost;
}

export const API_BASE_URL = configuredBaseUrl ?? `http://${resolveDevHost()}:8080`;
const FALLBACK_API_BASE_URLS = fallbackBaseUrls();

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  auth?: boolean;
  timeoutMs?: number;
};

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

export class NetworkError extends Error {
  constructor(message = 'Could not reach the backend API') {
    super(message);
  }
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return sendRequest(path, options, true);
}

async function sendRequest<T>(path: string, options: RequestOptions, allowRefresh: boolean): Promise<T> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
  };

  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  if (options.auth !== false) {
    const token = session.getAccessToken();
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  }

  const request = {
    method: options.method ?? 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  };

  let response: Response | undefined;
  let networkError: unknown;
  for (const baseUrl of requestBaseUrls()) {
    try {
      response = await fetchWithTimeout(`${baseUrl}${path}`, request, options.timeoutMs);
      break;
    } catch (error) {
      networkError = error;
    }
  }

  if (!response) {
    throw new NetworkError(networkError instanceof Error ? networkError.message : undefined);
  }

  if (!response.ok) {
    if (allowRefresh && options.auth !== false && (response.status === 401 || response.status === 403)) {
      const refreshed = await refreshAccessToken();
      if (refreshed) {
        return sendRequest<T>(path, options, false);
      }
    }
    const message = await readErrorMessage(response);
    throw new ApiError(response.status, message || `Request failed with status ${response.status}`);
  }

  if (response.status === 204 || response.headers.get('content-length') === '0') {
    return undefined as T;
  }

  const text = await response.text();
  if (!text) {
    return undefined as T;
  }

  return JSON.parse(text) as T;
}

async function refreshAccessToken() {
  const refreshToken = session.getRefreshToken();
  if (!refreshToken) {
    return false;
  }

  try {
    let response: Response | undefined;
    for (const baseUrl of requestBaseUrls()) {
      try {
        response = await fetchWithTimeout(`${baseUrl}/api/auth/refresh`, {
          method: 'POST',
          headers: {
            Accept: 'application/json',
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ refreshToken }),
        }, LONG_REQUEST_TIMEOUT_MS);
        break;
      } catch {
        response = undefined;
      }
    }

    if (!response) {
      return false;
    }

    if (!response.ok) {
      session.clearAuth();
      return false;
    }

    const auth = await response.json() as AuthResponse;
    session.setAuth(auth);
    return true;
  } catch {
    return false;
  }
}

function requestBaseUrls() {
  return uniqueUrls([dynamicApiBaseUrl(), configuredBaseUrl, ...FALLBACK_API_BASE_URLS]);
}

function fallbackBaseUrls() {
  const devUrl = dynamicApiBaseUrl();
  if (Platform.OS === 'android') {
    return [devUrl, 'http://10.0.2.2:8080'];
  }
  if (Platform.OS === 'ios') {
    return [devUrl, 'http://localhost:8080'];
  }
  return [devUrl, 'http://localhost:8080'];
}

function dynamicApiBaseUrl() {
  return `http://${resolveDevHost()}:8080`;
}

function uniqueUrls(urls: Array<string | undefined>) {
  return urls.filter((url, index): url is string => Boolean(url) && urls.indexOf(url) === index);
}

function fetchWithTimeout(url: string, options: RequestInit, timeoutMs = REQUEST_TIMEOUT_MS) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  return fetch(url, { ...options, signal: controller.signal })
    .finally(() => clearTimeout(timeout));
}

async function readErrorMessage(response: Response) {
  const text = await response.text();
  if (!text) return '';

  try {
    const parsed = JSON.parse(text) as { message?: string; error?: string; details?: string[] };
    return parsed.message ?? parsed.error ?? parsed.details?.join('\n') ?? text;
  } catch {
    return text;
  }
}
