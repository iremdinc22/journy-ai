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
    latitude: float | None = None
    longitude: float | None = None


class AgentDayContext(BaseModel):
    dayNumber: int
    title: str
    summary: str
    walkKm: float
    stopCount: int
    stops: list[AgentStop] = Field(default_factory=list)


class AgentTripContext(BaseModel):
    tripId: str
    destination: str
    budget: str
    pace: str
    interests: list[str] = Field(default_factory=list)
    startingArea: str | None = None


class AgentMessageRequest(BaseModel):
    message: str
    trip: AgentTripContext
    day: AgentDayContext
    itineraryDays: list[AgentDayContext] = Field(default_factory=list)


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
