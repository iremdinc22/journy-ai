from enum import Enum

from pydantic import BaseModel, Field


class AgentIntent(str, Enum):
    MAKE_DAY_LIGHTER = "MAKE_DAY_LIGHTER"
    ADD_FOOD_STOP = "ADD_FOOD_STOP"
    REPLACE_STOP = "REPLACE_STOP"
    BUDGET_OPTIMIZE = "BUDGET_OPTIMIZE"
    RAIN_REPLAN = "RAIN_REPLAN"
    GENERAL_GUIDANCE = "GENERAL_GUIDANCE"


class AgentStop(BaseModel):
    order: int
    title: str
    category: str
    timeWindow: str
    note: str
    optional: bool = False
    latitude: float | None = None
    longitude: float | None = None


class AgentDayContext(BaseModel):
    dayNumber: int
    title: str
    summary: str
    walkKm: float
    stopCount: int
    nextStop: str | None = None
    optionalStops: list[str] = Field(default_factory=list)
    stops: list[AgentStop] = Field(default_factory=list)


class AgentTripContext(BaseModel):
    tripId: str
    destination: str
    budget: str
    pace: str
    interests: list[str] = Field(default_factory=list)
    startingArea: str | None = None


class SavedPlaceSignal(BaseModel):
    name: str
    city: str
    category: str
    priceLevel: str
    rating: float
    tags: str | None = None


class PlanningStrategyContext(BaseModel):
    title: str
    description: str
    signals: list[str] = Field(default_factory=list)


class UserAgentContext(BaseModel):
    userId: str
    travelStyle: str
    defaultPace: str
    defaultBudget: str
    foodDiscovery: str
    tasteSignals: list[str] = Field(default_factory=list)
    savedCategorySignals: list[str] = Field(default_factory=list)
    savedPlaces: list[SavedPlaceSignal] = Field(default_factory=list)
    planningStrategy: PlanningStrategyContext | None = None


class AgentMessageRequest(BaseModel):
    message: str
    trip: AgentTripContext
    day: AgentDayContext
    itineraryDays: list[AgentDayContext] = Field(default_factory=list)
    userProfile: UserAgentContext | None = None


class AgentActionPreview(BaseModel):
    intent: AgentIntent
    title: str
    message: str
    suggestedAction: str
    minutesSaved: int | None = None
    affectedStops: list[str] = Field(default_factory=list)
    routeSummary: str
    reasons: list[str] = Field(default_factory=list)
    requiresConfirmation: bool = True


class AgentMessageResponse(BaseModel):
    message: str
    intent: AgentIntent
    preview: AgentActionPreview
