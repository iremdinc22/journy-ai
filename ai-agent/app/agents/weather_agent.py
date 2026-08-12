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
        turkish = (request.language or "").lower() == "tr"
        risky_stop = analysis.weather_sensitive_stop
        affected = [risky_stop.title] if risky_stop else []

        if risky_stop:
            message = f"{risky_stop.title} durağını yakındaki kapalı mekana uygun bir seçenekle değiştir." if turkish else f"Swap {risky_stop.title} for an indoor-friendly stop nearby."
            action = "Açık hava durağını değiştir" if turkish else "Swap outdoor stop"
        else:
            message = "Bu gün çoğunlukla kapalı mekana uygun. Bir kapalı mola aralığı ekle." if turkish else "This day is mostly indoor-friendly. Add one covered break."
            action = "Kapalı mola aralığı ekle" if turkish else "Add covered buffer window"

        reasons = [
            f"Açık hava durakları: {analysis.outdoor_stop_count}" if turkish else f"Outdoor stops: {analysis.outdoor_stop_count}",
            f"Kapalı mekan durakları: {analysis.indoor_stop_count}" if turkish else f"Indoor stops: {analysis.indoor_stop_count}",
            "Aynı bölge" if turkish else "Same area",
        ]
        if risky_stop:
            reasons.append(f"{risky_stop.title} hava durumuna hassas" if turkish else f"{risky_stop.title} is weather-sensitive")

        return AgentActionPreview(
            intent=AgentIntent.RAIN_REPLAN,
            title=f"{day.dayNumber}. günü yağmura hazırla" if turkish else f"Rain-proof Day {day.dayNumber}",
            message=message,
            suggestedAction=action,
            minutesSaved=analysis.estimated_minutes_saved // 2,
            affectedStops=affected,
            routeSummary=f"{day.dayNumber}. gün yağmura hazır hale gelir." if turkish else f"Day {day.dayNumber} becomes rain-ready.",
            reasons=reasons[:2],
            requiresConfirmation=True,
        )
