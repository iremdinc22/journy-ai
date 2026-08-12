from __future__ import annotations

from app.agents.context_analyzer import DayAnalysis
from app.schemas.agent import AgentActionPreview, AgentIntent, AgentMessageRequest


class FoodAgent:
    def build_food_break_preview(
        self,
        request: AgentMessageRequest,
        analysis: DayAnalysis,
    ) -> AgentActionPreview:
        trip = request.trip
        day = request.day
        turkish = (request.language or "").lower() == "tr"
        after_stop = analysis.break_after_stop
        before_stop = analysis.break_before_stop
        break_type = self._break_type(trip.interests)
        route_window = self._route_window(after_stop.title if after_stop else None, before_stop.title if before_stop else None, turkish)

        if analysis.route_pressure == "high":
            message = f"Mevcut rota içine kısa bir {self._break_type_tr(break_type) if turkish else break_type} molası ekle." if turkish else f"Add a short {break_type} pause inside the current route."
            action = f"Kompakt {self._break_type_tr(break_type) if turkish else break_type} molası ekle" if turkish else f"Add compact {break_type} break"
            minutes = 6
        else:
            message = f"{route_window} civarına {self._break_type_tr(break_type)} durağı ekle." if turkish else f"Add a {break_type} stop around {route_window}."
            action = f"Yerel {self._break_type_tr(break_type)} durağı ekle" if turkish else f"Add local {break_type} stop"
            minutes = 8

        reasons = [
            f"En iyi aralık: {route_window}" if turkish else f"Best window: {route_window}",
            f"Mevcut molalar: {analysis.food_break_count}" if turkish else f"Current breaks: {analysis.food_break_count}",
            f"Uyum: {', '.join(trip.interests[:2]) if trip.interests else 'yerel keşif'}" if turkish else f"Matches: {', '.join(trip.interests[:2]) if trip.interests else 'local discovery'}",
        ]
        if trip.budget.upper() in {"LEAN", "LOW", "BUDGET"}:
            reasons.append("Bütçeye duyarlı" if turkish else "Budget-aware")
        else:
            reasons.append("Rotaya yakın" if turkish else "Near route")

        return AgentActionPreview(
            intent=AgentIntent.ADD_FOOD_STOP,
            title=f"{self._break_type_tr(break_type).capitalize()} molası ekle" if turkish else f"Add a {break_type} break",
            message=message,
            suggestedAction=action,
            minutesSaved=None,
            affectedStops=[label for label in [after_stop.title if after_stop else None, before_stop.title if before_stop else None] if label],
            routeSummary=f"{day.dayNumber}. gün daha iyi bir mola kazanır." if turkish else f"Day {day.dayNumber} gains a better break.",
            reasons=[*reasons[:2], f"+{minutes} dk yürüyüş" if turkish else f"+{minutes} min walk"],
            requiresConfirmation=True,
        )

    def _break_type(self, interests: list[str]) -> str:
        normalized = {interest.upper() for interest in interests}
        if "COFFEE" in normalized:
            return "coffee"
        if "LOCAL_FOOD" in normalized or "FOOD" in normalized:
            return "local food"
        return "food"

    def _route_window(self, after_stop: str | None, before_stop: str | None, turkish: bool = False) -> str:
        if after_stop and before_stop:
            return f"{after_stop} -> {before_stop}"
        if after_stop:
            return f"{after_stop} sonrası" if turkish else f"after {after_stop}"
        if before_stop:
            return f"{before_stop} öncesi" if turkish else f"before {before_stop}"
        return "günün ortası" if turkish else "the middle of the day"

    def _break_type_tr(self, value: str) -> str:
        if value == "coffee":
            return "kahve"
        if value == "local food":
            return "yerel yemek"
        return "yemek"
