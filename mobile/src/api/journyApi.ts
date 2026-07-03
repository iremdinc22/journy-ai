import { apiRequest } from './client';
import { session } from './session';
import type {
  AiChatResponse,
  AuthResponse,
  CreateTripRequest,
  ItineraryResponse,
  NotificationResponse,
  PlaceResponse,
  ProfileResponse,
  TripResponse,
} from './types';

export const authApi = {
  async login(email: string, password: string) {
    const response = await apiRequest<AuthResponse>('/api/auth/login', {
      method: 'POST',
      auth: false,
      body: { email, password },
    });
    session.setAuth(response);
    return response;
  },

  async register(fullName: string, email: string, password: string) {
    const response = await apiRequest<AuthResponse>('/api/auth/register', {
      method: 'POST',
      auth: false,
      body: { fullName, email, password },
    });
    session.setAuth(response);
    return response;
  },
};

export const tripApi = {
  current() {
    return apiRequest<TripResponse>('/api/trips/current');
  },

  async create(request: CreateTripRequest) {
    const trip = await apiRequest<TripResponse>('/api/trips', {
      method: 'POST',
      body: request,
    });
    session.setCurrentTrip(trip);
    return trip;
  },

  async generate(tripId: string) {
    const trip = await apiRequest<TripResponse>(`/api/trips/${tripId}/generate`, {
      method: 'POST',
    });
    session.setCurrentTrip(trip);
    return trip;
  },

  itinerary(tripId: string) {
    return apiRequest<ItineraryResponse>(`/api/trips/${tripId}/itinerary`);
  },
};

export const exploreApi = {
  places(category?: string) {
    const query = category && category !== 'For you' ? `?category=${encodeURIComponent(category)}` : '';
    return apiRequest<PlaceResponse[]>(`/api/explore/places${query}`);
  },
};

export const aiApi = {
  chat(message: string, tripId?: string) {
    return apiRequest<AiChatResponse>('/api/ai/chat', {
      method: 'POST',
      body: { tripId, message },
    });
  },
};

export const profileApi = {
  me() {
    return apiRequest<ProfileResponse>('/api/users/me');
  },
};

export const notificationApi = {
  list() {
    return apiRequest<NotificationResponse[]>('/api/notifications');
  },
};
