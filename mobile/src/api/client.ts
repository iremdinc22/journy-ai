import { NativeModules, Platform } from 'react-native';

import { session } from './session';

declare const process: {
  env?: Record<string, string | undefined>;
};

const localHost = Platform.OS === 'android' ? '10.0.2.2' : 'localhost';
const configuredBaseUrl = process.env?.EXPO_PUBLIC_API_BASE_URL;

function resolveDevHost() {
  const scriptUrl = NativeModules.SourceCode?.scriptURL as string | undefined;
  const hostMatch = scriptUrl?.match(/^[^:]+:\/\/([^:/]+)/);

  return hostMatch?.[1] ?? localHost;
}

export const API_BASE_URL = configuredBaseUrl ?? `http://${resolveDevHost()}:8080`;
const FALLBACK_API_BASE_URLS =
  !configuredBaseUrl && Platform.OS === 'ios' && API_BASE_URL !== 'http://localhost:8080'
    ? ['http://localhost:8080']
    : [];

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  auth?: boolean;
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
  for (const baseUrl of [API_BASE_URL, ...FALLBACK_API_BASE_URLS]) {
    try {
      response = await fetch(`${baseUrl}${path}`, request);
      break;
    } catch (error) {
      networkError = error;
    }
  }

  if (!response) {
    throw new NetworkError(networkError instanceof Error ? networkError.message : undefined);
  }

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new ApiError(response.status, message || `Request failed with status ${response.status}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
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
