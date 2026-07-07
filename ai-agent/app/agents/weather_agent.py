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
            message = (
                f"I would protect the route from rain by swapping {risky_stop.title} "
                "for an indoor culture, cafe or covered local stop nearby."
            )
            action = "Swap weather-sensitive stop"
        else:
            message = (
                "This day is already mostly indoor-friendly. I would keep the route shape "
                "and add one covered break window as a buffer."
            )
            action = "Add covered buffer window"

        reasons = [
            f"Outdoor stops found: {analysis.outdoor_stop_count}",
            f"Indoor-friendly stops already planned: {analysis.indoor_stop_count}",
            "Rain changes should preserve the same neighborhood cluster",
        ]
        if risky_stop:
            reasons.append(f"{risky_stop.title} is the most weather-sensitive stop")

        return AgentActionPreview(
            intent=AgentIntent.RAIN_REPLAN,
            title=f"Rain-proof Day {day.dayNumber}",
            message=message,
            suggestedAction=action,
            minutesSaved=analysis.estimated_minutes_saved // 2,
            affectedStops=affected,
            routeSummary=f"{trip.destination} Day {day.dayNumber} becomes more indoor-friendly without rebuilding the whole day.",
            reasons=reasons,
            requiresConfirmation=True,
        )
