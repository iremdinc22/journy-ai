import { apiRequest } from './client';
import { session } from './session';
import type {
  AiChatResponse,
  AgentIntent,
  AgentMessageResponse,
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
  TripPreviewRequest,
  TripPreviewResponse,
  UserPreferences,
  WeatherAdjustmentResponse,
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
  list() {
    return apiRequest<TripResponse[]>('/api/trips');
  },

  current() {
    return apiRequest<TripResponse>('/api/trips/current');
  },

  preview(request: TripPreviewRequest) {
    return apiRequest<TripPreviewResponse>('/api/trips/preview', {
      method: 'POST',
      body: request,
      timeoutMs: 45000,
    });
  },

  detail(tripId: string) {
    return apiRequest<TripResponse>(`/api/trips/${tripId}`);
  },

  async create(request: CreateTripRequest) {
    const trip = await apiRequest<TripResponse>('/api/trips', {
      method: 'POST',
      body: request,
      timeoutMs: 60000,
    });
    session.setCurrentTrip(trip);
    return trip;
  },

  async generate(tripId: string) {
    const trip = await apiRequest<TripResponse>(`/api/trips/${tripId}/generate`, {
      method: 'POST',
      timeoutMs: 60000,
    });
    session.setCurrentTrip(trip);
    return trip;
  },

  itinerary(tripId: string) {
    return apiRequest<ItineraryResponse>(`/api/trips/${tripId}/itinerary`);
  },

  weatherAdjustment(tripId: string) {
    return apiRequest<WeatherAdjustmentResponse>(`/api/trips/${tripId}/itinerary/weather-adjustment`);
  },

  addPlaceToDay(tripId: string, dayNumber: number, place: AddPlaceToPlanRequest) {
    return apiRequest<ItineraryDay>(`/api/trips/${tripId}/itinerary/days/${dayNumber}/stops`, {
      method: 'POST',
      body: place,
    });
  },

  removeStop(tripId: string, dayNumber: number, stopId: string) {
    return apiRequest<ItineraryDay>(`/api/trips/${tripId}/itinerary/days/${dayNumber}/stops/${stopId}`, {
      method: 'DELETE',
    });
  },

  toggleStopOptional(tripId: string, dayNumber: number, stopId: string) {
    return apiRequest<ItineraryDay>(`/api/trips/${tripId}/itinerary/days/${dayNumber}/stops/${stopId}/optional`, {
      method: 'PATCH',
    });
  },

  reorderStop(tripId: string, dayNumber: number, stopId: string, targetOrder: number) {
    return apiRequest<ItineraryDay>(`/api/trips/${tripId}/itinerary/days/${dayNumber}/stops/${stopId}/reorder`, {
      method: 'POST',
      body: { targetOrder },
    });
  },

  moveStop(tripId: string, dayNumber: number, stopId: string, targetDayNumber: number) {
    return apiRequest<ItineraryResponse>(`/api/trips/${tripId}/itinerary/days/${dayNumber}/stops/${stopId}/move`, {
      method: 'POST',
      body: { targetDayNumber },
    });
  },

  async makeCurrent(tripId: string) {
    const trip = await apiRequest<TripResponse>(`/api/trips/${tripId}/current`, {
      method: 'PUT',
    });
    session.setCurrentTrip(trip);
    return trip;
  },

  async delete(tripId: string) {
    await apiRequest<void>(`/api/trips/${tripId}`, {
      method: 'DELETE',
    });
    if (session.getCurrentTrip()?.id === tripId) {
      session.clearCurrentTrip();
    }
  },
};

export const exploreApi = {
  places(category?: string, city?: string) {
    const params = new URLSearchParams();
    if (category && category !== 'For you') {
      params.set('category', category);
    }
    if (city?.trim()) {
      params.set('city', city.trim());
    }
    const query = params.toString() ? `?${params.toString()}` : '';
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

  itinerarySuggestion(tripId: string, dayNumber: number, action: string, language: 'en' | 'tr' = 'en') {
    return apiRequest<AiItinerarySuggestionResponse>('/api/ai/itinerary-suggestion', {
      method: 'POST',
      body: { tripId, dayNumber, action, language },
    });
  },

  applyItinerarySuggestion(tripId: string, dayNumber: number, action: string, language: 'en' | 'tr' = 'en') {
    return apiRequest<ItineraryDay>('/api/ai/itinerary-apply', {
      method: 'POST',
      body: { tripId, dayNumber, action, language },
    });
  },
};

export const agentApi = {
  message(message: string, tripId?: string, dayNumber = 1, language: 'en' | 'tr' = 'en') {
    return apiRequest<AgentMessageResponse>('/api/agent/message', {
      method: 'POST',
      body: { tripId, dayNumber, message, language },
      timeoutMs: 20000,
    });
  },

  apply(tripId: string, dayNumber: number, intent: AgentIntent, language: 'en' | 'tr' = 'en') {
    return apiRequest<ItineraryDay>('/api/agent/apply', {
      method: 'POST',
      body: { tripId, dayNumber, intent, language },
      timeoutMs: 20000,
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
