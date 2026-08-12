from __future__ import annotations

import json
from typing import Any

from openai import OpenAI
from pydantic import ValidationError

from app.agents.context_analyzer import DayAnalysis, TripAnalysis, TripContextAnalyzer
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
        itinerary_days = request.itineraryDays or [request.day]
        trip_analysis = self.context_analyzer.analyze_trip(request.trip, itinerary_days)
        detected_intent = self._detect_intent(request.message)
        if detected_intent != AgentIntent.GENERAL_GUIDANCE:
            return self._decide_with_rules(request, analysis, trip_analysis, detected_intent)
        if self.client:
            response = self._decide_with_openai(request, analysis, trip_analysis)
            if response:
                return response
        return self._decide_with_rules(request, analysis, trip_analysis)

    def _decide_with_openai(
        self,
        request: AgentMessageRequest,
        analysis: DayAnalysis,
        trip_analysis: TripAnalysis,
    ) -> AgentMessageResponse | None:
        response_language = self._response_language(request)
        try:
            completion = self.client.chat.completions.create(
                model=settings.openai_model,
                response_format={"type": "json_object"},
                messages=[
                    {
                        "role": "system",
                        "content": (
                            "You are Journy's travel planning agent. "
                            "Analyze the active day and the full multi-day trip context before deciding. "
                            "Use userProfile tasteSignals, savedCategorySignals and savedPlaces to personalize reasons. "
                            "Use tripAnalysis to notice the busiest day, missing food breaks and weather-heavy days. "
                            f"Respond in {response_language}. "
                            "All user-facing strings inside message and preview must use that language. "
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
                        "content": json.dumps(self._prompt_payload(request, analysis, trip_analysis), ensure_ascii=False),
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
        trip_analysis: TripAnalysis,
        forced_intent: AgentIntent | None = None,
    ) -> AgentMessageResponse:
        intent = forced_intent or self._detect_intent(request.message)
        preview = self._preview_for(intent, request, analysis)
        return AgentMessageResponse(
            message=self._message_for(intent, request, analysis, trip_analysis),
            intent=intent,
            preview=preview,
        )

    def _detect_intent(self, message: str) -> AgentIntent:
        text = message.lower()
        if self._contains(text, "budget", "cheap", "cheaper", "save money", "ucuz", "bütçe", "tasarruf", "euro"):
            return AgentIntent.BUDGET_OPTIMIZE
        if self._contains(text, "rain", "weather", "rainy", "indoor", "inside", "covered", "rain-ready", "yağmur", "hava", "kapalı"):
            return AgentIntent.RAIN_REPLAN
        if self._contains(text, "coffee", "cafe", "food", "dinner", "restaurant", "kahve", "yemek", "akşam"):
            return AgentIntent.ADD_FOOD_STOP
        if self._contains(text, "replace", "swap", "change stop", "değiştir", "yerine"):
            return AgentIntent.REPLACE_STOP
        if self._contains(text, "light", "lighter", "easy", "short", "slow", "less walking", "tired", "finish earlier", "hafif", "yorul", "yorgun", "kolay", "az yür", "erken bit"):
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
                "title": self._text(request, f"Make Day {day.dayNumber} lighter", f"{day.dayNumber}. günü hafiflet"),
                "message": self._text(request, "I can remove pressure from the most flexible part of the day while keeping the main anchors.", "Ana durakları koruyup günün en esnek kısmındaki baskıyı azaltabilirim."),
                "action": self._text(request, "Remove optional final stop", "Opsiyonel son durağı kaldır"),
                "minutes": analysis.estimated_minutes_saved,
                "summary": analysis.route_summary,
            },
            AgentIntent.ADD_FOOD_STOP: {
                "title": self._text(request, "Add a local food break", "Yerel yemek molası ekle"),
                "message": self._text(request, "I can place a food or coffee stop near the current route without rebuilding the whole day.", "Tüm günü yeniden kurmadan mevcut rotanın yakınına yemek veya kahve durağı ekleyebilirim."),
                "action": self._text(request, "Add food stop near route", "Rotaya yakın yemek durağı ekle"),
                "minutes": None,
                "summary": self._text(request, f"{trip.destination} Day {day.dayNumber} keeps the same route shape with a better break window.", f"{trip.destination} {day.dayNumber}. gün aynı rota şeklini korur ve daha iyi mola aralığı kazanır."),
            },
            AgentIntent.REPLACE_STOP: {
                "title": self._text(request, "Replace one flexible stop", "Esnek bir durağı değiştir"),
                "message": self._text(request, "I can swap the weakest-fit stop for another option in the same route window.", "En zayıf uyumlu durağı aynı rota aralığında başka bir seçenekle değiştirebilirim."),
                "action": self._text(request, "Replace stop in same area", "Aynı bölgede durağı değiştir"),
                "minutes": 10,
                "summary": self._text(request, f"{trip.destination} Day {day.dayNumber} stays walkable while matching preferences better.", f"{trip.destination} {day.dayNumber}. gün yürünebilir kalır ve tercihlerine daha iyi uyar."),
            },
            AgentIntent.BUDGET_OPTIMIZE: {
                "title": self._text(request, f"Optimize Day {day.dayNumber} for budget", f"{day.dayNumber}. günü bütçeye göre düzenle"),
                "message": self._text(request, "I can reduce expensive food pressure and add a lower-cost local alternative.", "Pahalı yemek baskısını azaltıp daha ekonomik yerel bir alternatif ekleyebilirim."),
                "action": self._text(request, "Replace flexible stop with budget-friendly option", "Esnek durağı bütçe dostu seçenekle değiştir"),
                "minutes": 8,
                "summary": self._text(request, f"{trip.destination} Day {day.dayNumber} becomes easier on budget without losing the main route.", f"{trip.destination} {day.dayNumber}. gün ana rotayı kaybetmeden bütçeye daha uygun hale gelir."),
            },
            AgentIntent.RAIN_REPLAN: {
                "title": self._text(request, f"Rebuild Day {day.dayNumber} around rain", f"{day.dayNumber}. günü yağmura göre düzenle"),
                "message": self._text(request, "I can swap weather-sensitive outdoor time for an indoor culture or cafe window.", "Hava durumuna hassas açık hava zamanını kapalı kültür veya kafe aralığıyla değiştirebilirim."),
                "action": self._text(request, "Replace outdoor stop with indoor-friendly option", "Açık hava durağını kapalı mekana uygun seçenekle değiştir"),
                "minutes": 12,
                "summary": self._text(request, f"{trip.destination} Day {day.dayNumber} keeps its rhythm with less weather risk.", f"{trip.destination} {day.dayNumber}. gün daha az hava riskiyle ritmini korur."),
            },
            AgentIntent.GENERAL_GUIDANCE: {
                "title": self._text(request, "I can adjust this day", "Bu günü düzenleyebilirim"),
                "message": self._text(request, "Tell me if you want the day lighter, cheaper, food-focused or weather-ready.", "Günü daha hafif, ekonomik, yemek odaklı veya hava durumuna hazır yapmak istersen söyle."),
                "action": self._text(request, "Ask for a route adjustment", "Rota düzenlemesi iste"),
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
        trip_analysis: TripAnalysis,
    ) -> str:
        if intent == AgentIntent.GENERAL_GUIDANCE:
            if self._is_turkish(request):
                profile_note = self._profile_note(request)
                return (
                    f"{request.day.dayNumber}. günü kontrol ettim: "
                    f"{analysis.route_summary} Seyahatin geneline bakınca {trip_analysis.balance_summary} "
                    f"{profile_note} Daha hafif, ekonomik, yemek odaklı veya hava durumuna hazır bir düzenleme isteyebilirsin."
                )
            profile_note = self._profile_note(request)
            return (
                f"I checked Day {request.day.dayNumber}: "
                f"{analysis.route_summary} Across the trip, {trip_analysis.balance_summary} "
                f"{profile_note} Tell me if you want it lighter, cheaper, food-focused or weather-ready."
            )
        if intent == AgentIntent.MAKE_DAY_LIGHTER:
            if self._is_turkish(request):
                busiest_note = " Bu seyahatin en yoğun günü olduğu için baskıyı azaltmak mantıklı." if trip_analysis.busiest_day_number == request.day.dayNumber else ""
                optional_note = f" Önce opsiyonel durak {request.day.optionalStops[0]} ile başlayabilirim." if request.day.optionalStops else ""
                return (
                    f"{request.day.dayNumber}. günü analiz ettim. "
                    f"Rota baskısı {analysis.route_pressure}; bu yüzden daha hafif bir gün önizlemesi hazırladım."
                    f"{busiest_note}{optional_note} {self._profile_note(request)}"
                )
            busiest_note = ""
            if trip_analysis.busiest_day_number == request.day.dayNumber:
                busiest_note = " This is also the busiest day in the trip, so reducing pressure makes sense."
            optional_note = ""
            if request.day.optionalStops:
                optional_note = f" I can start with optional stop {request.day.optionalStops[0]}."
            return (
                f"I analyzed Day {request.day.dayNumber}. "
                f"The route pressure is {analysis.route_pressure}, so I prepared a lighter-day preview."
                f"{busiest_note}{optional_note} {self._profile_note(request)}"
            )
        if intent == AgentIntent.ADD_FOOD_STOP:
            if self._is_turkish(request):
                food_note = " Bu günde henüz yemek veya kahve molası yok." if request.day.dayNumber in trip_analysis.food_gap_day_numbers else ""
                return (
                    f"{request.day.dayNumber}. gün için daha iyi bir mola aralığı kontrol ettim. "
                    "Rotanın şeklini bozmayan yemek veya kahve önizlemesi hazırladım."
                    f"{food_note} {self._saved_place_note(request)}"
                )
            food_note = ""
            if request.day.dayNumber in trip_analysis.food_gap_day_numbers:
                food_note = " This day has no food or coffee break yet."
            return (
                f"I checked Day {request.day.dayNumber} for a better break window. "
                "I prepared a food or coffee preview that keeps the route shape intact."
                f"{food_note} {self._saved_place_note(request)}"
            )
        if intent == AgentIntent.RAIN_REPLAN:
            if self._is_turkish(request):
                weather_note = " Bu gün açık hava ağırlıklı, bu yüzden yağmur yedeği işe yarar." if request.day.dayNumber in trip_analysis.outdoor_heavy_day_numbers else ""
                return (
                    f"{request.day.dayNumber}. günü hava durumuna hassas duraklar için kontrol ettim. "
                    "Planı değiştirmeden önce yağmura hazır bir önizleme hazırladım."
                    f"{weather_note}"
                )
            weather_note = ""
            if request.day.dayNumber in trip_analysis.outdoor_heavy_day_numbers:
                weather_note = " This day is outdoor-heavy, so a rain backup is useful."
            return (
                f"I checked Day {request.day.dayNumber} for weather-sensitive stops. "
                "I prepared a rain-ready preview before changing the plan."
                f"{weather_note}"
            )
        if self._is_turkish(request):
            return f"{request.day.dayNumber}. günü kontrol ettim. Uygulamadan önce bu değişikliği önizleme olarak hazırlayabilirim. {self._profile_note(request)}"
        return f"I checked Day {request.day.dayNumber}. I can prepare this change as a preview before applying it. {self._profile_note(request)}"

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
                self._profile_reason(request),
                f"The core {trip.destination} anchors stay in place",
            ]
        if intent == AgentIntent.BUDGET_OPTIMIZE:
            return [
                f"Your trip budget mode is {trip.budget.lower()}",
                self._profile_reason(request),
                "Food and flexible stops are the easiest places to optimize",
                "The route can stay close to the existing cluster",
            ]
        if intent == AgentIntent.RAIN_REPLAN:
            return [
                "Outdoor stops are the most weather-sensitive",
                self._profile_reason(request),
                "Indoor culture and cafe windows preserve the experience",
                "Keeping the same area avoids extra transfers",
            ]
        return [
            f"This matches your {trip.pace.lower()} pace",
            self._saved_place_reason(request),
            analysis.route_summary,
            "Journy applies it only after your confirmation",
        ][:3]

    def _prompt_payload(
        self,
        request: AgentMessageRequest,
        analysis: DayAnalysis,
        trip_analysis: TripAnalysis,
    ) -> dict[str, Any]:
        return {
            "userMessage": request.message,
            "language": request.language,
            "trip": request.trip.model_dump(),
            "day": request.day.model_dump(),
            "itineraryDays": [day.model_dump() for day in request.itineraryDays],
            "userProfile": request.userProfile.model_dump() if request.userProfile else None,
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
            "tripAnalysis": {
                "dayCount": trip_analysis.day_count,
                "totalStops": trip_analysis.total_stops,
                "averageWalkKm": trip_analysis.average_walk_km,
                "busiestDayNumber": trip_analysis.busiest_day_number,
                "lightestDayNumber": trip_analysis.lightest_day_number,
                "foodGapDayNumbers": trip_analysis.food_gap_day_numbers,
                "outdoorHeavyDayNumbers": trip_analysis.outdoor_heavy_day_numbers,
                "balanceSummary": trip_analysis.balance_summary,
                "signals": trip_analysis.signals,
            },
            "supportedIntents": [intent.value for intent in AgentIntent],
        }

    def _profile_note(self, request: AgentMessageRequest) -> str:
        profile = request.userProfile
        if not profile or not profile.tasteSignals:
            if self._is_turkish(request):
                return "Öneriyi mevcut TripSetup seçimlerinle uyumlu tutacağım."
            return "I will keep the recommendation aligned with your current TripSetup choices."
        if self._is_turkish(request):
            return f"Ayrıca {profile.tasteSignals[0].lower()} zevk sinyalini kullanıyorum."
        return f"I am also using your {profile.tasteSignals[0].lower()} taste signal."

    def _saved_place_note(self, request: AgentMessageRequest) -> str:
        profile = request.userProfile
        if not profile or not profile.savedCategorySignals:
            return self._profile_note(request)
        if self._is_turkish(request):
            return f"Kayıtlı yerlerin {profile.savedCategorySignals[0].lower()} tarafına yakın; bunu dikkate aldım."
        return f"Your saved places lean toward {profile.savedCategorySignals[0].lower()}, so I kept that in mind."

    def _profile_reason(self, request: AgentMessageRequest) -> str:
        profile = request.userProfile
        if not profile or not profile.tasteSignals:
            if self._is_turkish(request):
                return "TripSetup tercihlerini takip eder"
            return "It follows your TripSetup preferences"
        if self._is_turkish(request):
            return f"{profile.tasteSignals[0].lower()} tercihine uyuyor"
        return f"It matches your {profile.tasteSignals[0].lower()} preference"

    def _saved_place_reason(self, request: AgentMessageRequest) -> str:
        profile = request.userProfile
        if not profile or not profile.savedCategorySignals:
            return self._profile_reason(request)
        if self._is_turkish(request):
            return f"Kayıtlı yerler {profile.savedCategorySignals[0].lower()} öneriyor"
        return f"Saved places suggest {profile.savedCategorySignals[0].lower()}"

    def _contains(self, text: str, *values: str) -> bool:
        return any(value in text for value in values)

    def _is_turkish(self, request: AgentMessageRequest) -> bool:
        return (request.language or "").lower() == "tr"

    def _response_language(self, request: AgentMessageRequest) -> str:
        return "Turkish" if self._is_turkish(request) else "English"

    def _text(self, request: AgentMessageRequest, english: str, turkish: str) -> str:
        return turkish if self._is_turkish(request) else english
