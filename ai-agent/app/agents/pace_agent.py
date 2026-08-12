from __future__ import annotations

from app.agents.context_analyzer import DayAnalysis
from app.schemas.agent import AgentActionPreview, AgentIntent, AgentMessageRequest


class PaceAgent:
    def build_lighter_day_preview(
        self,
        request: AgentMessageRequest,
        analysis: DayAnalysis,
    ) -> AgentActionPreview:
        flexible_stop = analysis.flexible_stop
        affected = [flexible_stop.title] if flexible_stop else []
        if analysis.route_pressure == "low":
            title = f"Keep Day {request.day.dayNumber} relaxed"
            message = "This day is already light. Keep the route and mark the last stop optional."
            action = "Mark final stop as optional"
        else:
            title = f"Lighten Day {request.day.dayNumber}"
            stop_name = flexible_stop.title if flexible_stop else "the most flexible stop"
            message = f"Remove or soften {stop_name}. Keep the main stops."
            action = "Remove flexible stop"

        reasons = [
            *analysis.signals[:1],
            "Main stops stay",
        ]
        if flexible_stop:
            reasons.append(f"{flexible_stop.title} is flexible")

        return AgentActionPreview(
            intent=AgentIntent.MAKE_DAY_LIGHTER,
            title=title,
            message=message,
            suggestedAction=action,
            minutesSaved=analysis.estimated_minutes_saved,
            affectedStops=affected,
            routeSummary=analysis.route_summary,
            reasons=reasons[:2],
            requiresConfirmation=True,
        )
