export type UserSummary = {
  id: string;
  fullName: string;
  email: string;
  travelStyle: string;
};

export type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  user: UserSummary;
};

export type TripResponse = {
  id: string;
  destination: string;
  startingArea?: string;
  startDate: string;
  endDate: string;
  days: number;
  travelerType: string;
  budget: string;
  pace: string;
  interests: string[];
  stats: {
    stops: number;
    foodPicks: number;
    averageWalkKm: number;
  };
};

export type CreateTripRequest = {
  destination: string;
  startingArea?: string;
  startDate: string;
  endDate: string;
  travelerType: 'SOLO' | 'COUPLE' | 'FRIENDS' | 'FAMILY';
  budget: 'LEAN' | 'BALANCED' | 'COMFORT';
  pace: 'RELAXED' | 'BALANCED' | 'FULL';
  interests: Array<
    | 'COFFEE'
    | 'MUSEUMS'
    | 'LOCAL_FOOD'
    | 'WALKING'
    | 'SHOPPING'
    | 'NIGHTLIFE'
    | 'CULTURE'
    | 'FREE_ACTIVITIES'
  >;
};

export type ItineraryStop = {
  order: number;
  title: string;
  category: string;
  timeWindow: string;
  note: string;
  latitude: number;
  longitude: number;
};

export type ItineraryDay = {
  dayNumber: number;
  title: string;
  summary: string;
  walkKm: number;
  stopCount: number;
  stops: ItineraryStop[];
};

export type ItineraryResponse = {
  tripId: string;
  destination: string;
  days: ItineraryDay[];
};

export type PlaceResponse = {
  id: string;
  name: string;
  city: string;
  category: string;
  description: string;
  priceLevel: string;
  rating: number;
  imageUrl: string;
  address?: string;
  latitude?: number;
  longitude?: number;
  openingHours?: string;
  estimatedVisitMinutes?: number;
  tags?: string;
};

export type DestinationResponse = {
  id: string;
  name: string;
  country: string;
  description: string;
  imageUrl: string;
  tags: string;
  bestFor: string;
  placeCount: number;
  averageDailyWalkKm: number;
  available: boolean;
  popular: boolean;
};

export type AiChatResponse = {
  conversationId: string;
  message: string;
  suggestedAction: string | null;
  minutesSaved: number | null;
};

export type AiItinerarySuggestionResponse = {
  title: string;
  message: string;
  suggestedAction: string;
  minutesSaved: number | null;
  stopsAffected: string[];
  routeSummary: string;
};

export type SavedPlaceResponse = {
  id: string;
  placeId: string;
  name: string;
  city: string;
  category: string;
  description: string;
  priceLevel: string;
  rating: number;
  imageUrl: string;
  address?: string;
  openingHours?: string;
  estimatedVisitMinutes?: number;
  tags?: string;
};

export type SavedPlaceRequest = Omit<SavedPlaceResponse, 'id'>;

export type ProfileResponse = {
  id: string;
  fullName: string;
  travelStyle: string;
  currentTrip: {
    destination: string;
    dates: string;
    stops: number;
    foodPicks: number;
    averageWalkKm: number;
  } | null;
  preferences: UserPreferences;
  tasteProfile: Array<{
    title: string;
    description: string;
    icon: string;
  }>;
  savedPlans: Array<{
    id: string;
    destination: string;
    summary: string;
    stops: number;
    foodPicks: number;
    averageWalkKm: number;
  }>;
  savedPlaces: Array<{
    placeId: string;
    name: string;
    city: string;
    category: string;
    imageUrl: string;
    rating: number;
  }>;
};

export type UserPreferences = {
  defaultPace: 'RELAXED' | 'BALANCED' | 'FULL';
  defaultBudget: 'LEAN' | 'BALANCED' | 'COMFORT';
  foodDiscovery: 'LOCAL_FIRST' | 'BEST_RATED' | 'BUDGET_FRIENDLY';
  planChangeNotifications: boolean;
  foodWindowNotifications: boolean;
};

export type NotificationResponse = {
  id: string;
  type: string;
  title: string;
  message: string;
  unread: boolean;
  createdAt: string;
};
