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
            message = (
                "This day is already light for your selected pace. "
                "I would keep the core route and only mark the last stop as optional."
            )
            action = "Mark final stop as optional"
        else:
            title = f"Lighten Day {request.day.dayNumber}"
            stop_name = flexible_stop.title if flexible_stop else "the most flexible stop"
            message = (
                f"I would soften the day by adjusting {stop_name}, "
                "while keeping the main anchor stops in place."
            )
            action = "Remove or make the flexible stop optional"

        reasons = [
            *analysis.signals[:3],
            "Core anchor stops stay in the plan",
        ]
        if flexible_stop:
            reasons.append(f"{flexible_stop.title} is the safest flexible stop to adjust")

        return AgentActionPreview(
            intent=AgentIntent.MAKE_DAY_LIGHTER,
            title=title,
            message=message,
            suggestedAction=action,
            minutesSaved=analysis.estimated_minutes_saved,
            affectedStops=affected,
            routeSummary=analysis.route_summary,
            reasons=reasons,
            requiresConfirmation=True,
        )
