from __future__ import annotations

import json
from typing import Any

from openai import OpenAI
from pydantic import ValidationError

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

    def decide(self, request: AgentMessageRequest) -> AgentMessageResponse:
        if self.client:
            response = self._decide_with_openai(request)
            if response:
                return response
        return self._decide_with_rules(request)

    def _decide_with_openai(self, request: AgentMessageRequest) -> AgentMessageResponse | None:
        try:
            completion = self.client.chat.completions.create(
                model=settings.openai_model,
                response_format={"type": "json_object"},
                messages=[
                    {
                        "role": "system",
                        "content": (
                            "You are Journy's travel planning agent. "
                            "Return only valid JSON matching this shape: "
                            "{message:string,intent:string,preview:{intent:string,title:string,message:string,"
                            "suggestedAction:string,minutesSaved:number|null,affectedStops:string[],"
                            "routeSummary:string,reasons:string[],requiresConfirmation:boolean}}. "
                            "Never claim you changed the plan. Produce a preview only."
                        ),
                    },
                    {
                        "role": "user",
                        "content": json.dumps(self._prompt_payload(request), ensure_ascii=False),
                    },
                ],
                temperature=0.2,
            )
            content = completion.choices[0].message.content or "{}"
            return AgentMessageResponse.model_validate_json(content)
        except (ValidationError, json.JSONDecodeError, Exception):
            return None

    def _decide_with_rules(self, request: AgentMessageRequest) -> AgentMessageResponse:
        intent = self._detect_intent(request.message)
        preview = self._preview_for(intent, request)
        return AgentMessageResponse(
            message=self._message_for(intent, request),
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

    def _preview_for(self, intent: AgentIntent, request: AgentMessageRequest) -> AgentActionPreview:
        day = request.day
        trip = request.trip
        affected = self._affected_stops(intent, request)

        data = {
            AgentIntent.MAKE_DAY_LIGHTER: {
                "title": f"Make Day {day.dayNumber} lighter",
                "message": "I can remove pressure from the most flexible part of the day while keeping the main anchors.",
                "action": "Remove optional final stop",
                "minutes": max(14, round(day.walkKm * 3.4)),
                "summary": f"{trip.destination} Day {day.dayNumber} becomes calmer with fewer transitions.",
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
                "summary": f"{trip.destination} Day {day.dayNumber} has {day.stopCount} stops and {day.walkKm} km of walking.",
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
            reasons=self._reasons_for(intent, request, affected),
            requiresConfirmation=intent != AgentIntent.GENERAL_GUIDANCE,
        )

    def _message_for(self, intent: AgentIntent, request: AgentMessageRequest) -> str:
        if intent == AgentIntent.GENERAL_GUIDANCE:
            return "I can read your current route and create a safe preview before changing it."
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

    def _reasons_for(self, intent: AgentIntent, request: AgentMessageRequest, affected: list[str]) -> list[str]:
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
            "The change keeps stop order in mind",
            "Journy applies it only after your confirmation",
        ]

    def _prompt_payload(self, request: AgentMessageRequest) -> dict[str, Any]:
        return {
            "userMessage": request.message,
            "trip": request.trip.model_dump(),
            "day": request.day.model_dump(),
            "supportedIntents": [intent.value for intent in AgentIntent],
        }

    def _contains(self, text: str, *values: str) -> bool:
        return any(value in text for value in values)
