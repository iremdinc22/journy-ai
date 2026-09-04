# Synthetic weather audit

Searched backend production Java, mobile production TypeScript, and Python agent code for rain, weather, hardcoded availability, forecast mocks/defaults, rain windows, indoor replacements, rain-ready claims and saved-distance values. Reviewed TripSetup, creation and generation for weather dependencies before edits.

| Occurrence/group | Classification | Disposition |
|---|---|---|
| ItineraryService hash-derived 14:00/15:00 rain window | Production synthetic weather | Removed |
| ItineraryService outdoor category → available=true without forecast | Production synthetic eligibility | Removed |
| Indoor Culture Window replacement and weatherWalkReduction | Production invented alternative/savings | Removed |
| Old WeatherForecastService default numeric zero and shifted rain times | Production provider fallback | Replaced with strict parsing and exact observed hours |
| RightNow “Weather clear enough” on absent forecast | Production false fallback | Replaced with “No verified rain alert” |
| Assistant outdoor category/name predicate → rain card | Production synthetic eligibility | Removed; requires backend preview |
| Assistant default Rain backup and unconditional Indoor plan prompt | Production UI placeholder presented as action | Removed/gated by backend eligibility |
| Assistant offline rain swap, 12 minutes saved, 0.4 km reduction | Production fabricated preview | Removed; neutral unavailable guidance with no confirmation |
| AgentService rainPreview and AiService weather chat/suggestion | Production invented recommendation/savings | Authoritative weather preview in AgentService; neutral Plan guidance in legacy AI API |
| Python WeatherAgent and TravelAgent weather dictionary/message/reasons | Production forecast-free rain advice | Removed; no-confirmation guidance; LLM rain intent also gated |
| WeatherForecast/WeatherProvider normalized values and thresholds | Production real forecast | Retained, guarded; no mock/default rain |
| Open-Meteo enabled=true | Production provider feature flag | Retained; enabling a provider is not rain eligibility |
| ItineraryService RightNow Rain risk text | Production observed forecast display | Retained only when real matching-date observations exceed thresholds |
| WeatherAdjustmentService available=true and Rain risk title | Production validated decision | Requires observed overlap, verified stops and a lower-risk slot |
| RAIN_REPLAN enums and English/Turkish intent keywords | Production routing | Retained; detecting a weather question does not prove rain |
| Assistant Weather Agent icon/labels and localized weather strings | UI labels/translation compatibility | Retained; UI cannot render intervention without backend eligibility |
| Generic initial assistance copy mentioning rain-ready planning | UI capability copy | Not a forecast or availability state; retained |
| Old localizedDynamicText translation entries describing historical weather previews | UI translation compatibility | Retained; not a weather generator or source of available=true |
| Test fixture rain/probabilities/dates/Place names | Test fixture | Deterministic tests only; never selected by production code |
| LIVE_MATRIX.json forecast and generated-place records | Development evidence | Actual provider responses; never consumed by production |
| live_smoke.py destinations | Development smoke matrix | Four cities requested by the user; not production branches |

No destination-specific weather hardcoding, production mock forecast, default rain=true, or synthetic replacement Place is introduced. Non-weather existing demo suggestions were outside this task and were not redesigned.
