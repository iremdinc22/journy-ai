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
        turkish = (request.language or "").lower() == "tr"
        if analysis.route_pressure == "low":
            title = f"{request.day.dayNumber}. günü rahat tut" if turkish else f"Keep Day {request.day.dayNumber} relaxed"
            message = "Bu gün zaten hafif. Rotayı koruyup son durağı opsiyonel yap." if turkish else "This day is already light. Keep the route and mark the last stop optional."
            action = "Son durağı opsiyonel yap" if turkish else "Mark final stop as optional"
        else:
            title = f"{request.day.dayNumber}. günü hafiflet" if turkish else f"Lighten Day {request.day.dayNumber}"
            stop_name = flexible_stop.title if flexible_stop else ("en esnek durak" if turkish else "the most flexible stop")
            message = f"{stop_name} durağını çıkar veya hafiflet. Ana durakları koru." if turkish else f"Remove or soften {stop_name}. Keep the main stops."
            action = "Esnek durağı kaldır" if turkish else "Remove flexible stop"

        reasons = [
            *analysis.signals[:1],
            "Ana duraklar korunur" if turkish else "Main stops stay",
        ]
        if flexible_stop:
            reasons.append(f"{flexible_stop.title} esnek" if turkish else f"{flexible_stop.title} is flexible")

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
