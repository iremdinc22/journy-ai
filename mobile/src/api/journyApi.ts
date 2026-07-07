import { apiRequest } from './client';
import { session } from './session';
import type {
  AiChatResponse,
  AiItinerarySuggestionResponse,
  AddPlaceToPlanRequest,
  AuthResponse,
  CreateTripRequest,
  DestinationResponse,
  ItineraryResponse,
  ItineraryDay,
  NotificationResponse,
  PlaceResponse,
  ProfileResponse,
  SavedPlaceRequest,
  SavedPlaceResponse,
  TripResponse,
  UserPreferences,
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

  async logout() {
    const refreshToken = session.getRefreshToken();
    try {
      if (refreshToken) {
        await apiRequest<void>('/api/auth/logout', {
          method: 'POST',
          auth: false,
          body: { refreshToken },
        });
      }
    } finally {
      session.clearAuth();
    }
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

  addPlaceToDay(tripId: string, dayNumber: number, place: AddPlaceToPlanRequest) {
    return apiRequest<ItineraryDay>(`/api/trips/${tripId}/itinerary/days/${dayNumber}/stops`, {
      method: 'POST',
      body: place,
    });
  },
};

export const exploreApi = {
  places(category?: string) {
    const query = category && category !== 'For you' ? `?category=${encodeURIComponent(category)}` : '';
    return apiRequest<PlaceResponse[]>(`/api/explore/places${query}`);
  },

  destinations() {
    return apiRequest<DestinationResponse[]>('/api/explore/destinations');
  },
};

export const destinationApi = {
  search(query?: string) {
    const qs = query?.trim() ? `?query=${encodeURIComponent(query.trim())}` : '';
    return apiRequest<DestinationResponse[]>(`/api/destinations${qs}`);
  },

  popular() {
    return apiRequest<DestinationResponse[]>('/api/destinations/popular');
  },

  detail(id: string) {
    return apiRequest<DestinationResponse>(`/api/destinations/${id}`);
  },
};

export const aiApi = {
  chat(message: string, tripId?: string) {
    return apiRequest<AiChatResponse>('/api/ai/chat', {
      method: 'POST',
      body: { tripId, message },
    });
  },

  itinerarySuggestion(tripId: string, dayNumber: number, action: string) {
    return apiRequest<AiItinerarySuggestionResponse>('/api/ai/itinerary-suggestion', {
      method: 'POST',
      body: { tripId, dayNumber, action },
    });
  },

  applyItinerarySuggestion(tripId: string, dayNumber: number, action: string) {
    return apiRequest<ItineraryDay>('/api/ai/itinerary-apply', {
      method: 'POST',
      body: { tripId, dayNumber, action },
    });
  },
};

export const profileApi = {
  me() {
    return apiRequest<ProfileResponse>('/api/users/me');
  },

  updatePreferences(preferences: UserPreferences) {
    return apiRequest<ProfileResponse>('/api/users/me/preferences', {
      method: 'PUT',
      body: preferences,
    });
  },
};

export const savedPlaceApi = {
  list() {
    return apiRequest<SavedPlaceResponse[]>('/api/saved-places');
  },

  status(placeId: string) {
    return apiRequest<{ saved: boolean }>(`/api/saved-places/${encodeURIComponent(placeId)}/status`);
  },

  save(place: SavedPlaceRequest) {
    return apiRequest<SavedPlaceResponse>('/api/saved-places', {
      method: 'POST',
      body: place,
    });
  },

  remove(placeId: string) {
    return apiRequest<void>(`/api/saved-places/${encodeURIComponent(placeId)}`, {
      method: 'DELETE',
    });
  },
};

export const notificationApi = {
  list() {
    return apiRequest<NotificationResponse[]>('/api/notifications');
  },
};
