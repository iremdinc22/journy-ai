from enum import Enum

from pydantic import BaseModel, Field, field_validator


class AgentIntent(str, Enum):
    MAKE_DAY_LIGHTER = "MAKE_DAY_LIGHTER"
    ADD_FOOD_STOP = "ADD_FOOD_STOP"
    REPLACE_STOP = "REPLACE_STOP"
    BUDGET_OPTIMIZE = "BUDGET_OPTIMIZE"
    RAIN_REPLAN = "RAIN_REPLAN"
    GENERAL_GUIDANCE = "GENERAL_GUIDANCE"


class AgentStop(BaseModel):
    order: int = 1
    title: str = "Route stop"
    category: str = "WALKING"
    timeWindow: str = "10:00"
    note: str = "Flexible route stop."
    optional: bool = False
    latitude: float | None = None
    longitude: float | None = None

    @field_validator("title", "category", "timeWindow", "note", mode="before")
    @classmethod
    def default_text(cls, value: str | None) -> str:
        return value or ""


class AgentDayContext(BaseModel):
    dayNumber: int = 1
    title: str = "Trip day"
    summary: str = "Flexible day route."
    walkKm: float = 0
    stopCount: int = 0
    nextStop: str | None = None
    optionalStops: list[str] = Field(default_factory=list)
    stops: list[AgentStop] = Field(default_factory=list)

    @field_validator("title", "summary", mode="before")
    @classmethod
    def default_text(cls, value: str | None) -> str:
        return value or ""

    @field_validator("optionalStops", "stops", mode="before")
    @classmethod
    def default_list(cls, value):
        return value or []


class AgentTripContext(BaseModel):
    tripId: str = ""
    destination: str = "Your trip"
    budget: str = "BALANCED"
    pace: str = "BALANCED"
    interests: list[str] = Field(default_factory=list)
    startingArea: str | None = None

    @field_validator("tripId", "destination", "budget", "pace", mode="before")
    @classmethod
    def default_text(cls, value: str | None) -> str:
        return value or ""

    @field_validator("interests", mode="before")
    @classmethod
    def default_list(cls, value):
        return value or []


class SavedPlaceSignal(BaseModel):
    name: str | None = ""
    city: str | None = ""
    category: str | None = "WALKING"
    priceLevel: str | None = "Mid"
    rating: float | None = 0
    tags: str | None = None

    @field_validator("name", "city", "category", "priceLevel", mode="before")
    @classmethod
    def default_text(cls, value: str | None) -> str:
        return value or ""


class PlanningStrategyContext(BaseModel):
    title: str = "Journy plan"
    description: str = "Adaptive route strategy."
    signals: list[str] = Field(default_factory=list)

    @field_validator("title", "description", mode="before")
    @classmethod
    def default_text(cls, value: str | None) -> str:
        return value or ""

    @field_validator("signals", mode="before")
    @classmethod
    def default_list(cls, value):
        return value or []


class UserAgentContext(BaseModel):
    userId: str = ""
    travelStyle: str | None = "Balanced traveler"
    defaultPace: str | None = "BALANCED"
    defaultBudget: str | None = "BALANCED"
    foodDiscovery: str | None = "LOCAL_FIRST"
    tasteSignals: list[str] = Field(default_factory=list)
    savedCategorySignals: list[str] = Field(default_factory=list)
    savedPlaces: list[SavedPlaceSignal] = Field(default_factory=list)
    planningStrategy: PlanningStrategyContext | None = None

    @field_validator("userId", "travelStyle", "defaultPace", "defaultBudget", "foodDiscovery", mode="before")
    @classmethod
    def default_text(cls, value: str | None) -> str:
        return value or ""

    @field_validator("tasteSignals", "savedCategorySignals", "savedPlaces", mode="before")
    @classmethod
    def default_list(cls, value):
        return value or []


class AgentMessageRequest(BaseModel):
    message: str = ""
    language: str = "en"
    trip: AgentTripContext = Field(default_factory=AgentTripContext)
    day: AgentDayContext = Field(default_factory=AgentDayContext)
    itineraryDays: list[AgentDayContext] = Field(default_factory=list)
    userProfile: UserAgentContext | None = None

    @field_validator("message", "language", mode="before")
    @classmethod
    def default_message(cls, value: str | None) -> str:
        return value or ""

    @field_validator("itineraryDays", mode="before")
    @classmethod
    def default_list(cls, value):
        return value or []


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
