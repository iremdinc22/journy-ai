from __future__ import annotations

from app.agents.context_analyzer import DayAnalysis
from app.schemas.agent import AgentActionPreview, AgentIntent, AgentMessageRequest


class WeatherAgent:
    def build_rain_replan_preview(
        self,
        request: AgentMessageRequest,
        analysis: DayAnalysis,
    ) -> AgentActionPreview:
        # Agent context has no validated forecast. Only Java's weather endpoint can propose changes.
        return AgentActionPreview(
            intent=AgentIntent.GENERAL_GUIDANCE,
            title="Weather forecast required",
            message="Check Plan for a destination/date-specific weather preview. No change has been applied.",
            suggestedAction="Check forecast in Plan",
            minutesSaved=None,
            affectedStops=[],
            routeSummary="Existing itinerary retained.",
            reasons=["No validated forecast in agent context"],
            requiresConfirmation=False,
        )
