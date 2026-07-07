from __future__ import annotations

import json
from typing import Any

from openai import OpenAI
from pydantic import ValidationError

from app.agents.context_analyzer import DayAnalysis, TripContextAnalyzer
from app.agents.food_agent import FoodAgent
from app.agents.pace_agent import PaceAgent
from app.agents.weather_agent import WeatherAgent
from app.core.settings import settings
from app.schemas.agent import (
    AgentActionPreview,
    AgentIntent,
    AgentMessageRequest,
    AgentMessageResponse,
)


class TravelAgent:
    def __init__(self) -> None:
        self.client = OpenAI(api_key=settings.openai_api_key) if settings.openai_api_key else None
        self.context_analyzer = TripContextAnalyzer()
        self.pace_agent = PaceAgent()
        self.food_agent = FoodAgent()
        self.weather_agent = WeatherAgent()

    def decide(self, request: AgentMessageRequest) -> AgentMessageResponse:
        analysis = self.context_analyzer.analyze_day(request.trip, request.day)
        if self.client:
            response = self._decide_with_openai(request, analysis)
            if response:
                return response
        return self._decide_with_rules(request, analysis)

    def _decide_with_openai(
        self,
        request: AgentMessageRequest,
        analysis: DayAnalysis,
    ) -> AgentMessageResponse | None:
        try:
            completion = self.client.chat.completions.create(
                model=settings.openai_model,
                response_format={"type": "json_object"},
                messages=[
                    {
                        "role": "system",
                        "content": (
                            "You are Journy's travel planning agent. "
                            "Analyze the route context before deciding. "
                            "If the user asks for an easier/lighter/less tiring day, use MAKE_DAY_LIGHTER. "
                            "If the user asks for food, dinner, coffee or a cafe, use ADD_FOOD_STOP. "
                            "If the user mentions rain or weather, use RAIN_REPLAN. "
                            "Prefer safe previews that preserve anchor stops and explain why. "
                            "Return only valid JSON matching this shape: "
                            "{message:string,intent:string,preview:{intent:string,title:string,message:string,"
                            "suggestedAction:string,minutesSaved:number|null,affectedStops:string[],"
                            "routeSummary:string,reasons:string[],requiresConfirmation:boolean}}. "
                            "Never claim you changed the plan. Produce a preview only."
                        ),
                    },
                    {
                        "role": "user",
                        "content": json.dumps(self._prompt_payload(request, analysis), ensure_ascii=False),
                    },
                ],
                temperature=0.2,
            )
            content = completion.choices[0].message.content or "{}"
            response = AgentMessageResponse.model_validate_json(content)
            return self._with_tool_preview(response, request, analysis)
        except (ValidationError, json.JSONDecodeError, Exception):
            return None

    def _with_tool_preview(
        self,
        response: AgentMessageResponse,
        request: AgentMessageRequest,
        analysis: DayAnalysis,
    ) -> AgentMessageResponse:
        if response.intent in {
            AgentIntent.MAKE_DAY_LIGHTER,
            AgentIntent.ADD_FOOD_STOP,
            AgentIntent.RAIN_REPLAN,
        }:
            return AgentMessageResponse(
                message=response.message,
                intent=response.intent,
                preview=self._preview_for(response.intent, request, analysis),
            )
        return response

    def _decide_with_rules(
        self,
        request: AgentMessageRequest,
        analysis: DayAnalysis,
    ) -> AgentMessageResponse:
        intent = self._detect_intent(request.message)
        preview = self._preview_for(intent, request, analysis)
        return AgentMessageResponse(
            message=self._message_for(intent, request, analysis),
            intent=intent,
            preview=preview,
        )

    def _detect_intent(self, message: str) -> AgentIntent:
        text = message.lower()
        if self._contains(text, "budget", "cheap", "cheaper", "save money", "ucuz", "bütçe", "tasarruf", "euro"):
            return AgentIntent.BUDGET_OPTIMIZE
        if self._contains(text, "rain", "weather", "rainy", "yağmur", "hava"):
            return AgentIntent.RAIN_REPLAN
        if self._contains(text, "coffee", "cafe", "food", "dinner", "restaurant", "kahve", "yemek", "akşam"):
            return AgentIntent.ADD_FOOD_STOP
        if self._contains(text, "replace", "swap", "change stop", "değiştir", "yerine"):
            return AgentIntent.REPLACE_STOP
        if self._contains(text, "light", "lighter", "easy", "short", "slow", "less walking", "hafif", "yorul", "kolay", "az yür"):
            return AgentIntent.MAKE_DAY_LIGHTER
        return AgentIntent.GENERAL_GUIDANCE

    def _preview_for(
        self,
        intent: AgentIntent,
        request: AgentMessageRequest,
        analysis: DayAnalysis,
    ) -> AgentActionPreview:
        if intent == AgentIntent.MAKE_DAY_LIGHTER:
            return self.pace_agent.build_lighter_day_preview(request, analysis)
        if intent == AgentIntent.ADD_FOOD_STOP:
            return self.food_agent.build_food_break_preview(request, analysis)
        if intent == AgentIntent.RAIN_REPLAN:
            return self.weather_agent.build_rain_replan_preview(request, analysis)

        day = request.day
        trip = request.trip
        affected = self._affected_stops(intent, request)

        data = {
            AgentIntent.MAKE_DAY_LIGHTER: {
                "title": f"Make Day {day.dayNumber} lighter",
                "message": "I can remove pressure from the most flexible part of the day while keeping the main anchors.",
                "action": "Remove optional final stop",
                "minutes": analysis.estimated_minutes_saved,
                "summary": analysis.route_summary,
            },
            AgentIntent.ADD_FOOD_STOP: {
                "title": "Add a local food break",
                "message": "I can place a food or coffee stop near the current route without rebuilding the whole day.",
                "action": "Add food stop near route",
                "minutes": None,
                "summary": f"{trip.destination} Day {day.dayNumber} keeps the same route shape with a better break window.",
            },
            AgentIntent.REPLACE_STOP: {
                "title": "Replace one flexible stop",
                "message": "I can swap the weakest-fit stop for another option in the same route window.",
                "action": "Replace stop in same area",
                "minutes": 10,
                "summary": f"{trip.destination} Day {day.dayNumber} stays walkable while matching preferences better.",
            },
            AgentIntent.BUDGET_OPTIMIZE: {
                "title": f"Optimize Day {day.dayNumber} for budget",
                "message": "I can reduce expensive food pressure and add a lower-cost local alternative.",
                "action": "Replace flexible stop with budget-friendly option",
                "minutes": 8,
                "summary": f"{trip.destination} Day {day.dayNumber} becomes easier on budget without losing the main route.",
            },
            AgentIntent.RAIN_REPLAN: {
                "title": f"Rebuild Day {day.dayNumber} around rain",
                "message": "I can swap weather-sensitive outdoor time for an indoor culture or cafe window.",
                "action": "Replace outdoor stop with indoor-friendly option",
                "minutes": 12,
                "summary": f"{trip.destination} Day {day.dayNumber} keeps its rhythm with less weather risk.",
            },
            AgentIntent.GENERAL_GUIDANCE: {
                "title": "I can adjust this day",
                "message": "Tell me if you want the day lighter, cheaper, food-focused or weather-ready.",
                "action": "Ask for a route adjustment",
                "minutes": None,
                "summary": analysis.route_summary,
            },
        }[intent]

        return AgentActionPreview(
            intent=intent,
            title=data["title"],
            message=data["message"],
            suggestedAction=data["action"],
            minutesSaved=data["minutes"],
            affectedStops=affected,
            routeSummary=data["summary"],
            reasons=self._reasons_for(intent, request, affected, analysis),
            requiresConfirmation=intent != AgentIntent.GENERAL_GUIDANCE,
        )

    def _message_for(
        self,
        intent: AgentIntent,
        request: AgentMessageRequest,
        analysis: DayAnalysis,
    ) -> str:
        if intent == AgentIntent.GENERAL_GUIDANCE:
            return (
                f"I checked Day {request.day.dayNumber}: "
                f"{analysis.route_summary} Tell me if you want it lighter, cheaper, food-focused or weather-ready."
            )
        if intent == AgentIntent.MAKE_DAY_LIGHTER:
            return (
                f"I analyzed Day {request.day.dayNumber}. "
                f"The route pressure is {analysis.route_pressure}, so I prepared a lighter-day preview."
            )
        if intent == AgentIntent.ADD_FOOD_STOP:
            return (
                f"I checked Day {request.day.dayNumber} for a better break window. "
                "I prepared a food or coffee preview that keeps the route shape intact."
            )
        if intent == AgentIntent.RAIN_REPLAN:
            return (
                f"I checked Day {request.day.dayNumber} for weather-sensitive stops. "
                "I prepared a rain-ready preview before changing the plan."
            )
        return f"I checked Day {request.day.dayNumber}. I can prepare this change as a preview before applying it."

    def _affected_stops(self, intent: AgentIntent, request: AgentMessageRequest) -> list[str]:
        stops = request.day.stops
        if not stops:
            return []
        if intent == AgentIntent.MAKE_DAY_LIGHTER:
            return [stops[-1].title]
        if intent == AgentIntent.ADD_FOOD_STOP:
            food = [stop.title for stop in stops if stop.category.upper() in {"FOOD", "COFFEE"}]
            return food[:1] or [stops[min(1, len(stops) - 1)].title]
        if intent == AgentIntent.RAIN_REPLAN:
            outdoor = [stop.title for stop in stops if stop.category.upper() in {"WALKING", "FREE"}]
            return outdoor[:2]
        return [stops[min(1, len(stops) - 1)].title]

    def _reasons_for(
        self,
        intent: AgentIntent,
        request: AgentMessageRequest,
        affected: list[str],
        analysis: DayAnalysis,
    ) -> list[str]:
        trip = request.trip
        day = request.day
        anchor = affected[0] if affected else "the flexible route window"
        if intent == AgentIntent.MAKE_DAY_LIGHTER:
            return [
                f"{anchor} is the easiest part to adjust",
                f"Day {day.dayNumber} is currently {day.walkKm} km of walking",
                f"The core {trip.destination} anchors stay in place",
            ]
        if intent == AgentIntent.BUDGET_OPTIMIZE:
            return [
                f"Your trip budget mode is {trip.budget.lower()}",
                "Food and flexible stops are the easiest places to optimize",
                "The route can stay close to the existing cluster",
            ]
        if intent == AgentIntent.RAIN_REPLAN:
            return [
                "Outdoor stops are the most weather-sensitive",
                "Indoor culture and cafe windows preserve the experience",
                "Keeping the same area avoids extra transfers",
            ]
        return [
            f"This matches your {trip.pace.lower()} pace",
            analysis.route_summary,
            "Journy applies it only after your confirmation",
        ]

    def _prompt_payload(self, request: AgentMessageRequest, analysis: DayAnalysis) -> dict[str, Any]:
        return {
            "userMessage": request.message,
            "trip": request.trip.model_dump(),
            "day": request.day.model_dump(),
            "contextAnalysis": {
                "walkPressure": analysis.walk_pressure,
                "stopPressure": analysis.stop_pressure,
                "routePressure": analysis.route_pressure,
                "foodBreakCount": analysis.food_break_count,
                "outdoorStopCount": analysis.outdoor_stop_count,
                "indoorStopCount": analysis.indoor_stop_count,
                "anchorStopCount": analysis.anchor_stop_count,
                "flexibleStop": analysis.flexible_stop.model_dump() if analysis.flexible_stop else None,
                "heaviestStop": analysis.heaviest_stop.model_dump() if analysis.heaviest_stop else None,
                "breakAfterStop": analysis.break_after_stop.model_dump() if analysis.break_after_stop else None,
                "breakBeforeStop": analysis.break_before_stop.model_dump() if analysis.break_before_stop else None,
                "weatherSensitiveStop": (
                    analysis.weather_sensitive_stop.model_dump() if analysis.weather_sensitive_stop else None
                ),
                "estimatedMinutesSaved": analysis.estimated_minutes_saved,
                "routeSummary": analysis.route_summary,
                "signals": analysis.signals,
            },
            "supportedIntents": [intent.value for intent in AgentIntent],
        }

    def _contains(self, text: str, *values: str) -> bool:
        return any(value in text for value in values)
