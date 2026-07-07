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
    break_after_stop: AgentStop | None
    break_before_stop: AgentStop | None
    weather_sensitive_stop: AgentStop | None
    estimated_minutes_saved: int
    route_summary: str
    signals: list[str]


@dataclass(frozen=True)
class TripAnalysis:
    day_count: int
    total_stops: int
    average_walk_km: float
    busiest_day_number: int | None
    lightest_day_number: int | None
    food_gap_day_numbers: list[int]
    outdoor_heavy_day_numbers: list[int]
    balance_summary: str
    signals: list[str]


class TripContextAnalyzer:
    FOOD_CATEGORIES = {"FOOD", "COFFEE"}
    INDOOR_CATEGORIES = {"MUSEUM", "CULTURE", "FOOD", "COFFEE", "SHOPPING"}
    OUTDOOR_CATEGORIES = {"WALKING", "FREE", "LANDMARK", "PARK"}
    ANCHOR_CATEGORIES = {"MUSEUM", "CULTURE", "LANDMARK"}

    def analyze_trip(self, trip: AgentTripContext, days: list[AgentDayContext]) -> TripAnalysis:
        if not days:
            return TripAnalysis(
                day_count=0,
                total_stops=0,
                average_walk_km=0,
                busiest_day_number=None,
                lightest_day_number=None,
                food_gap_day_numbers=[],
                outdoor_heavy_day_numbers=[],
                balance_summary="No itinerary days are available yet.",
                signals=["Trip context is not complete"],
            )

        analyses = [(day, self.analyze_day(trip, day)) for day in days]
        total_stops = sum(day.stopCount for day in days)
        average_walk_km = round(sum(day.walkKm for day in days) / len(days), 1)
        busiest = max(days, key=lambda day: (day.walkKm, day.stopCount))
        lightest = min(days, key=lambda day: (day.walkKm, day.stopCount))
        food_gap_days = [day.dayNumber for day, analysis in analyses if analysis.food_break_count == 0]
        outdoor_heavy_days = [day.dayNumber for day, analysis in analyses if analysis.outdoor_stop_count >= 2]

        signals = [
            f"{len(days)} planned days in {trip.destination}",
            f"Average daily walk is {average_walk_km} km",
            f"Busiest day is Day {busiest.dayNumber}",
            f"Lightest day is Day {lightest.dayNumber}",
        ]
        if food_gap_days:
            signals.append(f"Days without a food or coffee break: {', '.join(map(str, food_gap_days))}")
        if outdoor_heavy_days:
            signals.append(f"Outdoor-heavy days: {', '.join(map(str, outdoor_heavy_days))}")

        return TripAnalysis(
            day_count=len(days),
            total_stops=total_stops,
            average_walk_km=average_walk_km,
            busiest_day_number=busiest.dayNumber,
            lightest_day_number=lightest.dayNumber,
            food_gap_day_numbers=food_gap_days,
            outdoor_heavy_day_numbers=outdoor_heavy_days,
            balance_summary=(
                f"{trip.destination} has {len(days)} days, {total_stops} stops and "
                f"{average_walk_km} km average walking per day. "
                f"Day {busiest.dayNumber} carries the most pressure; Day {lightest.dayNumber} is the lightest."
            ),
            signals=signals,
        )

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
        break_after_stop, break_before_stop = self._find_break_window(day.stops)
        weather_sensitive_stop = self._find_weather_sensitive_stop(day.stops)
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
            break_after_stop=break_after_stop,
            break_before_stop=break_before_stop,
            weather_sensitive_stop=weather_sensitive_stop,
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

    def _find_break_window(self, stops: list[AgentStop]) -> tuple[AgentStop | None, AgentStop | None]:
        if not stops:
            return None, None
        if len(stops) == 1:
            return stops[0], None

        for index, stop in enumerate(stops[:-1]):
            next_stop = stops[index + 1]
            category = stop.category.upper()
            next_category = next_stop.category.upper()
            if category in self.ANCHOR_CATEGORIES or next_category in self.ANCHOR_CATEGORIES:
                return stop, next_stop

        midpoint = max(0, min(len(stops) - 2, len(stops) // 2 - 1))
        return stops[midpoint], stops[midpoint + 1]

    def _find_weather_sensitive_stop(self, stops: list[AgentStop]) -> AgentStop | None:
        outdoor = [stop for stop in stops if stop.category.upper() in self.OUTDOOR_CATEGORIES]
        return outdoor[-1] if outdoor else self._find_flexible_stop(stops)

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
