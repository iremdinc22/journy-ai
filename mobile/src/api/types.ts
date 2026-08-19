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

export type TripPreviewRequest = Partial<Pick<
  CreateTripRequest,
  'destination' | 'startingArea' | 'startDate' | 'endDate' | 'budget' | 'pace' | 'interests'
>> & {
  language?: 'en' | 'tr';
};

export type TripPreviewResponse = {
  estimatedStops: number;
  dailyWalkKm: number;
  dailyWalkRange: string;
  routeStyle: string;
  availablePlaceCount: number;
  matchedPlaceCount: number;
  confidence: string;
  summary: string;
  planningStyle: string;
  startingAreaInsight: string;
};

export type ItineraryStop = {
  id: string;
  order: number;
  title: string;
  category: string;
  timeWindow: string;
  note: string;
  optional: boolean;
  status?: 'PLANNED' | 'ARRIVED' | 'DONE' | 'SKIPPED';
  arrivedAt?: string | null;
  completedAt?: string | null;
  latitude: number;
  longitude: number;
};

export type ItineraryTimelineItem = {
  id: string;
  type: 'STOP' | 'TRAVEL';
  title: string;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  distanceKm?: number | null;
  fromStopId?: string | null;
  toStopId?: string | null;
  category?: string | null;
  note?: string | null;
  constraintStatus?: 'OK' | 'WARNING' | string;
  constraintWarning?: string | null;
};

export type ItineraryDay = {
  dayNumber: number;
  title: string;
  summary: string;
  walkKm: number;
  stopCount: number;
  stops: ItineraryStop[];
  timeline?: ItineraryTimelineItem[];
};

export type ItineraryResponse = {
  tripId: string;
  destination: string;
  days: ItineraryDay[];
};

export type RightNowResponse = {
  available: boolean;
  dayNumber: number;
  title: string;
  message: string;
  recommendationTitle?: string | null;
  recommendationMeta?: string | null;
  actionLabel?: string | null;
  stopId?: string | null;
  freeWindowMinutes: number;
  delayMinutes: number;
  context: string[];
  reasons: string[];
};

export type WeatherAdjustmentResponse = {
  available: boolean;
  dayNumber: number;
  rainWindow?: string | null;
  title: string;
  message: string;
  affectedStop?: string | null;
  indoorAlternative?: string | null;
  beforeStopCount: number;
  beforeWalkKm: number;
  afterStopCount: number;
  afterWalkKm: number;
  changes: string[];
  reasons: string[];
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
  provider?: string;
  providerPlaceId?: string;
  website?: string;
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
  latitude?: number;
  longitude?: number;
  provider?: string;
  providerPlaceId?: string;
};

export type AiChatResponse = {
  conversationId: string;
  message: string;
  suggestedAction: string | null;
  minutesSaved: number | null;
};

export type AgentIntent =
  | 'MAKE_DAY_LIGHTER'
  | 'ADD_FOOD_STOP'
  | 'REPLACE_STOP'
  | 'BUDGET_OPTIMIZE'
  | 'RAIN_REPLAN'
  | 'GENERAL_GUIDANCE';

export type AgentActionPreview = {
  intent: AgentIntent;
  title: string;
  message: string;
  suggestedAction: string;
  minutesSaved: number | null;
  affectedStops: string[];
  routeSummary: string;
  reasons: string[];
  requiresConfirmation: boolean;
};

export type AgentMessageResponse = {
  conversationId: string;
  message: string;
  intent: AgentIntent;
  preview: AgentActionPreview;
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

export type TasteFeedbackAction =
  | 'SAVED'
  | 'REMOVED'
  | 'VISITED'
  | 'SKIPPED'
  | 'NOT_INTERESTED'
  | 'TOO_EXPENSIVE'
  | 'TOO_FAR'
  | 'ALREADY_VISITED'
  | 'REPLACED';

export type TasteFeedbackRequest = {
  placeId?: string;
  placeName: string;
  category: string;
  action: TasteFeedbackAction;
  reason?: string;
};

export type AddPlaceToPlanRequest = {
  placeId: string;
  name: string;
  city: string;
  category: string;
  description: string;
  priceLevel: string;
  rating: number;
  address?: string;
  latitude?: number;
  longitude?: number;
  openingHours?: string;
  estimatedVisitMinutes?: number;
  tags?: string;
};

export type ProfileResponse = {
  id: string;
  fullName: string;
  travelStyle: string;
  currentTrip: {
    id: string;
    destination: string;
    startingArea?: string;
    startDate: string;
    endDate: string;
    dates: string;
    travelerType: string;
    budget: string;
    pace: string;
    interests: string[];
    stops: number;
    foodPicks: number;
    averageWalkKm: number;
    planningStrategy?: {
      title: string;
      description: string;
      signals: string[];
    };
  } | null;
  preferences: UserPreferences;
  tasteProfile: Array<{
    title: string;
    description: string;
    icon: string;
    score?: number;
  }>;
  favoriteCount: number;
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
