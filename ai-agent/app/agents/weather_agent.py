from __future__ import annotations

from app.agents.context_analyzer import DayAnalysis
from app.schemas.agent import AgentActionPreview, AgentIntent, AgentMessageRequest


class WeatherAgent:
    def build_rain_replan_preview(
        self,
        request: AgentMessageRequest,
        analysis: DayAnalysis,
    ) -> AgentActionPreview:
        trip = request.trip
        day = request.day
        risky_stop = analysis.weather_sensitive_stop
        affected = [risky_stop.title] if risky_stop else []

        if risky_stop:
            message = f"Swap {risky_stop.title} for an indoor-friendly stop nearby."
            action = "Swap outdoor stop"
        else:
            message = "This day is mostly indoor-friendly. Add one covered break."
            action = "Add covered buffer window"

        reasons = [
            f"Outdoor stops: {analysis.outdoor_stop_count}",
            f"Indoor stops: {analysis.indoor_stop_count}",
            "Same area",
        ]
        if risky_stop:
            reasons.append(f"{risky_stop.title} is weather-sensitive")

        return AgentActionPreview(
            intent=AgentIntent.RAIN_REPLAN,
            title=f"Rain-proof Day {day.dayNumber}",
            message=message,
            suggestedAction=action,
            minutesSaved=analysis.estimated_minutes_saved // 2,
            affectedStops=affected,
            routeSummary=f"Day {day.dayNumber} becomes rain-ready.",
            reasons=reasons[:2],
            requiresConfirmation=True,
        )
