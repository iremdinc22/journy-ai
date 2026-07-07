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
        after_stop = analysis.break_after_stop
        before_stop = analysis.break_before_stop
        break_type = self._break_type(trip.interests)
        route_window = self._route_window(after_stop.title if after_stop else None, before_stop.title if before_stop else None)

        if analysis.route_pressure == "high":
            message = (
                f"I would add a short {break_type} pause inside the existing route window, "
                "without turning the day into a heavier route."
            )
            action = f"Add compact {break_type} break"
            minutes = 6
        else:
            message = (
                f"I found a clean {break_type} window that improves the rhythm of the day "
                "and keeps the route walkable."
            )
            action = f"Add local {break_type} stop"
            minutes = 8

        reasons = [
            f"Best break window: {route_window}",
            f"Food or coffee breaks currently scheduled: {analysis.food_break_count}",
            f"Matches interests: {', '.join(trip.interests[:3]) if trip.interests else 'local discovery'}",
        ]
        if trip.budget.upper() in {"LEAN", "LOW", "BUDGET"}:
            reasons.append("Keep it casual and budget-aware")
        else:
            reasons.append("Choose a local pick close to the existing route")

        return AgentActionPreview(
            intent=AgentIntent.ADD_FOOD_STOP,
            title=f"Add a {break_type} break",
            message=message,
            suggestedAction=action,
            minutesSaved=None,
            affectedStops=[label for label in [after_stop.title if after_stop else None, before_stop.title if before_stop else None] if label],
            routeSummary=f"{trip.destination} Day {day.dayNumber} gains a better break window around {route_window}.",
            reasons=[*reasons, f"Expected walking impact: about +{minutes} min"],
            requiresConfirmation=True,
        )

    def _break_type(self, interests: list[str]) -> str:
        normalized = {interest.upper() for interest in interests}
        if "COFFEE" in normalized:
            return "coffee"
        if "LOCAL_FOOD" in normalized or "FOOD" in normalized:
            return "local food"
        return "food"

    def _route_window(self, after_stop: str | None, before_stop: str | None) -> str:
        if after_stop and before_stop:
            return f"{after_stop} -> {before_stop}"
        if after_stop:
            return f"after {after_stop}"
        if before_stop:
            return f"before {before_stop}"
        return "the middle of the day"
