from __future__ import annotations

from dataclasses import dataclass

from app.schemas.agent import AgentDayContext, AgentStop, AgentTripContext


@dataclass(frozen=True)
class DayAnalysis:
    walk_pressure: str
    stop_pressure: str
    route_pressure: str
    food_break_count: int
    outdoor_stop_count: int
    indoor_stop_count: int
    anchor_stop_count: int
    flexible_stop: AgentStop | None
    heaviest_stop: AgentStop | None
    estimated_minutes_saved: int
    route_summary: str
    signals: list[str]


class TripContextAnalyzer:
    FOOD_CATEGORIES = {"FOOD", "COFFEE"}
    INDOOR_CATEGORIES = {"MUSEUM", "CULTURE", "FOOD", "COFFEE", "SHOPPING"}
    OUTDOOR_CATEGORIES = {"WALKING", "FREE", "LANDMARK", "PARK"}
    ANCHOR_CATEGORIES = {"MUSEUM", "CULTURE", "LANDMARK"}

    def analyze_day(self, trip: AgentTripContext, day: AgentDayContext) -> DayAnalysis:
        walk_pressure = self._walk_pressure(trip.pace, day.walkKm)
        stop_pressure = self._stop_pressure(trip.pace, day.stopCount)
        route_pressure = self._route_pressure(walk_pressure, stop_pressure)
        flexible_stop = self._find_flexible_stop(day.stops)
        heaviest_stop = self._find_heaviest_stop(day.stops)
        food_break_count = self._count_categories(day.stops, self.FOOD_CATEGORIES)
        outdoor_stop_count = self._count_categories(day.stops, self.OUTDOOR_CATEGORIES)
        indoor_stop_count = self._count_categories(day.stops, self.INDOOR_CATEGORIES)
        anchor_stop_count = self._count_categories(day.stops, self.ANCHOR_CATEGORIES)
        estimated_minutes_saved = self._estimate_minutes_saved(day, route_pressure, flexible_stop)

        return DayAnalysis(
            walk_pressure=walk_pressure,
            stop_pressure=stop_pressure,
            route_pressure=route_pressure,
            food_break_count=food_break_count,
            outdoor_stop_count=outdoor_stop_count,
            indoor_stop_count=indoor_stop_count,
            anchor_stop_count=anchor_stop_count,
            flexible_stop=flexible_stop,
            heaviest_stop=heaviest_stop,
            estimated_minutes_saved=estimated_minutes_saved,
            route_summary=self._route_summary(trip, day, route_pressure, anchor_stop_count, food_break_count),
            signals=self._signals(trip, day, route_pressure, food_break_count, outdoor_stop_count),
        )

    def _walk_pressure(self, pace: str, walk_km: float) -> str:
        pace_key = pace.upper()
        if pace_key == "RELAXED":
            if walk_km > 4.2:
                return "high"
            if walk_km > 2.8:
                return "medium"
            return "low"
        if pace_key == "FULL":
            if walk_km > 8.0:
                return "high"
            if walk_km > 5.5:
                return "medium"
            return "low"
        if walk_km > 6.2:
            return "high"
        if walk_km > 4.0:
            return "medium"
        return "low"

    def _stop_pressure(self, pace: str, stop_count: int) -> str:
        pace_key = pace.upper()
        if pace_key == "RELAXED":
            if stop_count >= 5:
                return "high"
            if stop_count >= 4:
                return "medium"
            return "low"
        if pace_key == "FULL":
            if stop_count >= 8:
                return "high"
            if stop_count >= 6:
                return "medium"
            return "low"
        if stop_count >= 6:
            return "high"
        if stop_count >= 4:
            return "medium"
        return "low"

    def _route_pressure(self, walk_pressure: str, stop_pressure: str) -> str:
        if "high" in {walk_pressure, stop_pressure}:
            return "high"
        if "medium" in {walk_pressure, stop_pressure}:
            return "medium"
        return "low"

    def _find_flexible_stop(self, stops: list[AgentStop]) -> AgentStop | None:
        if not stops:
            return None
        optional = [
            stop
            for stop in stops
            if stop.category.upper() not in self.ANCHOR_CATEGORIES
            and stop.category.upper() not in self.FOOD_CATEGORIES
        ]
        return optional[-1] if optional else stops[-1]

    def _find_heaviest_stop(self, stops: list[AgentStop]) -> AgentStop | None:
        anchors = [stop for stop in stops if stop.category.upper() in self.ANCHOR_CATEGORIES]
        return anchors[-1] if anchors else self._find_flexible_stop(stops)

    def _count_categories(self, stops: list[AgentStop], categories: set[str]) -> int:
        return len([stop for stop in stops if stop.category.upper() in categories])

    def _estimate_minutes_saved(
        self,
        day: AgentDayContext,
        route_pressure: str,
        flexible_stop: AgentStop | None,
    ) -> int:
        base = max(10, round(day.walkKm * 4))
        if route_pressure == "high":
            base += 10
        if flexible_stop and flexible_stop.category.upper() in self.OUTDOOR_CATEGORIES:
            base += 5
        return min(base, 42)

    def _route_summary(
        self,
        trip: AgentTripContext,
        day: AgentDayContext,
        route_pressure: str,
        anchor_count: int,
        food_count: int,
    ) -> str:
        pressure = {
            "high": "busy",
            "medium": "balanced but adjustable",
            "low": "already light",
        }[route_pressure]
        return (
            f"{trip.destination} Day {day.dayNumber} is {pressure}: "
            f"{day.stopCount} stops, {day.walkKm} km walking, "
            f"{anchor_count} anchor stops and {food_count} food or coffee breaks."
        )

    def _signals(
        self,
        trip: AgentTripContext,
        day: AgentDayContext,
        route_pressure: str,
        food_count: int,
        outdoor_count: int,
    ) -> list[str]:
        signals = [
            f"Trip pace is {trip.pace.lower()}",
            f"Day has {day.stopCount} stops and {day.walkKm} km walking",
            f"Route pressure is {route_pressure}",
        ]
        if trip.startingArea:
            signals.append(f"Starts around {trip.startingArea}")
        if food_count == 0:
            signals.append("No food or coffee break is currently scheduled")
        if outdoor_count >= 2:
            signals.append("Several stops are weather-sensitive")
        return signals
