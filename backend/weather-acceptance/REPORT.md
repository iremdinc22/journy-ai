# Weather reliability acceptance report

## Current weather flow

Trip.destinationLookupQuery() → existing DestinationResolutionService → resolved latitude/longitude → Open-Meteo WeatherProvider → validated destination-local hours → WeatherRiskEvaluator → WeatherAdjustmentService → Plan/Assistant eligibility → explicit preview-token Apply. Trip creation and itinerary generation do not depend on weather. Analysis was completed before edits in [ANALYSIS.md](ANALYSIS.md).

## Root cause

The previous backend selected outdoor-heavy days before evaluating weather. Dry, unavailable and out-of-range forecasts all fell through to a fabricated hash-based rain window, generic indoor replacement, and claimed walking reduction. Assistant independently displayed a rain card from stop categories; Java/Python and offline previews invented weather changes without forecasts.

## Files changed

- `ai-agent/app/agents/travel_agent.py`
- `ai-agent/app/agents/weather_agent.py`
- `backend/src/main/java/com/journy/backend/agent/service/AgentService.java`
- `backend/src/main/java/com/journy/backend/ai/service/AiService.java`
- `backend/src/main/java/com/journy/backend/itinerary/controller/ItineraryController.java`
- `backend/src/main/java/com/journy/backend/itinerary/dto/WeatherAdjustmentResponse.java`
- `backend/src/main/java/com/journy/backend/itinerary/service/ItineraryService.java`
- `backend/src/main/java/com/journy/backend/weather/WeatherForecastService.java`
- `backend/src/main/resources/application.yaml`
- `mobile/src/api/journyApi.ts`
- `mobile/src/api/types.ts`
- `mobile/src/screens/AssistantScreen.tsx`
- `mobile/src/screens/ItineraryScreen.tsx`
- `ai-agent/tests/test_weather_reliability.py`
- `backend/src/main/java/com/journy/backend/weather/OpenMeteoWeatherProvider.java`
- `backend/src/main/java/com/journy/backend/weather/WeatherAdjustmentService.java`
- `backend/src/main/java/com/journy/backend/weather/WeatherConfiguration.java`
- `backend/src/main/java/com/journy/backend/weather/WeatherForecast.java`
- `backend/src/main/java/com/journy/backend/weather/WeatherProvider.java`
- `backend/src/main/java/com/journy/backend/weather/WeatherRiskEvaluator.java`
- `backend/src/test/java/com/journy/backend/weather/WeatherApplyIntegrationTest.java`
- `backend/src/test/java/com/journy/backend/weather/WeatherReliabilityTest.java`
- `mobile/src/utils/weatherAdjustment.ts`
- `mobile/tests/weatherAdjustment.test.cjs`

Evidence files: `ANALYSIS.md`, `LIVE_MATRIX.json`, `live_smoke.py`, this report and `SYNTHETIC_AUDIT.md` in `backend/weather-acceptance/`.

## Weather provider

Reuses Open-Meteo through a small WeatherProvider interface. Requests hourly precipitation probability, precipitation in millimetres, and weather code. Connect timeout 2 seconds; read timeout 6 seconds. No credentials or new provider introduced. Provider capabilities and date/time parameters were checked against [official Open-Meteo documentation](https://open-meteo.com/en/docs).

## Forecast model

WeatherForecast contains status/reason/source, resolved query coordinates, timezone, requested start/end dates and hourly local timestamps with nullable probability, amount and code. Forecast observations are not generated or defaulted to zero. Temperature and summaries are omitted because this decision does not need them. Missing precipitation observations invalidate the response; weather codes remain optional and never trigger intervention by themselves.

## Destination coordinate strategy

Uses the existing resolver and the persisted disambiguated destination query. No default city, device location, or Place-cache coordinates. Structured weather_request/weather_response logs include coordinates, dates, timezone, availability and observation count. Unresolved destinations return unavailable.

## Date / timezone strategy

Trip start is inclusive; Journy trip end is exclusive, so the provider end date is endDate − 1 day. Exact dates are sent; timezone=auto returns destination-local hourly timestamps. Provider timezone is validated with ZoneId and used to check the current local day and schedule. The supported window is local today through today + 15 days. A broad UTC guard rejects clearly impossible dates before the request without guessing timezone. Complete hourly coverage is required for the requested range. Past scheduled visits cannot trigger swaps.

## Rain threshold strategy

Configurable `journy.weather.rain-probability-threshold: 60` percent OR `journy.weather.rain-amount-threshold-mm: 1.0` mm/hour. Equal-to-threshold counts. Both values must be finite and positive; probability cannot exceed 100. A rainy weather code with zero precipitation and low probability does not trigger risk.

## Weather-sensitive stop strategy

Only verified current Place-backed stops with matching name, category, source and coordinates are eligible. WALKING or explicit outdoor/park/garden/viewpoint metadata is sensitive. An alternative requires explicit indoor/museum/gallery metadata and must not also be marked sensitive. FOOD/COFFEE/CULTURE labels or a place name alone do not prove shelter. Unknown exposure remains unknown.

## Adjustment availability rule

All must hold: reliable forecast; future PLANNED visit with a parseable local start time; risk overlapping that outdoor visit; an adjacent verified sheltered stop; a lower-risk slot long enough for the outdoor visit; no known next-slot overlap or date crossing. Only the two existing stops exchange time/order. The read-only preview lists stop IDs, Place IDs, exact names and from/to times. No new place, title rewrite, auto-apply or claimed distance savings. Apply uses a dedicated owned-trip endpoint and recomputes the preview; stale/mismatched tokens return 409. Legacy generic rain-apply endpoints remain blocked.

## Provider failure behavior

Disabled, timeout, HTTP error, invalid timezone, malformed data, absent/null precipitation, incomplete coverage or resolution failure returns weatherStatus=UNAVAILABLE and available=false with no forecast values invented. Plan loads its itinerary before awaiting weather. Assistant offline weather requests provide neutral guidance with no Apply action or saved-minutes claim.

## Out-of-horizon behavior

Unavailable, false, empty observations. No substitution with today’s weather. Requests spanning unsupported past/future dates are conservatively unavailable as a whole.

## Cache strategy

Separate in-memory weather cache keyed by resolved latitude/longitude, exact date range and timezone mode. Configurable 15-minute success TTL; unavailable responses expire after 30 seconds. Maximum 256 entries with simple clearing. No Redis and no Place-cache reuse. Apply can use observations within that freshness window and rechecks the current local schedule and identities.

## Synthetic weather fallbacks removed

Hash rain windows; category-only backend eligibility; generic Indoor Culture Window; invented walking reductions; Assistant category-only rain card and unconditional rain quick prompt; offline rain swaps/savings; ungrounded Java AI rain recommendations; Python WeatherAgent and rule/LLM rain previews. RightNow no longer describes missing data as clear weather. Remaining strings and classifications are in [SYNTHETIC_AUDIT.md](SYNTHETIC_AUDIT.md).

## Tests added

12 backend weather cases cover zero/dry/below-threshold, threshold equality and amount risk, all-indoor/unknown exposure/missing identity, unavailable, exact coordinates/dates in an HTTP request, HTTP failure, horizon/disabled provider, destination isolation/cache reuse, Tokyo versus Los Angeles local-date mapping, incomplete data, rain outside visits/no dry slot, read-only preview, explicit identity-preserving swap, stale rejection, persistence and ownership.

Five mobile cases exercise the shared visibility predicate, actual Assistant eligibility expression, and actual Plan loading callback with delayed forecasts during an Edirne → Las Vegas switch. Two Python cases reject both rule-based and LLM-originated rain claims when agent context lacks a forecast.

## Test results

- Backend: 120 tests, 0 failures/errors in latest reports (full regression plus focused weather rerun after correcting required database fixture fields).
- Mobile: 15 tests passing; TypeScript check passes.
- Python: 2 unittest cases passing.
- git diff --check passes.
- Existing 108 backend and 10 mobile cases remain green. Protected destination, start-area, generator, title, insufficient-data, Explore, NIGHTLIFE, ranking and optimizer implementations were not changed.

## Live weather matrix

Local backend, real Open-Meteo responses, queried 2026-09-04 for 2026-09-05. All four trip and weather HTTP responses were 200; each returned 24 validated local hours. Full hourly values, generated itineraries and no-mutation comparisons are saved in [LIVE_MATRIX.json](LIVE_MATRIX.json). No weather adjustment was applied.

| Destination | Resolved coordinates | Timezone | Probability range | Amount range mm/h | Codes | Adjustment |
|---|---|---|---|---|---|---|
| Edirne | 41.6771, 26.5557 | Europe/Istanbul | 0–0% | 0–0 | 0 | false |
| Las Vegas | 36.1674263, -115.1484131 | America/Los_Angeles | 0–1% | 0–0 | 0 | false |
| Sarajevo | 43.8570713, 18.4126147 | Europe/Sarajevo | 0–0% | 0–0 | 0, 1, 2, 3 | false |
| Tallinn | 59.437242, 24.7572693 | Europe/Tallinn | 7–26% | 0–9.7 | 3, 51, 55, 65 | false |

Edirne, Las Vegas and Sarajevo are below both thresholds throughout the day. Tallinn exceeds the amount threshold at 16:00–17:00; its walking stop is at 09:30, so the forecast does not justify moving that visit. All decisions report NO_MEANINGFUL_WEATHER_CHANGE. These are differing real forecasts, not four identical rain defaults. All four GET previews left the full itinerary unchanged. A positive rainy preview is demonstrated deterministically, not claimed as a live event.

## Manual retest checklist

NOT EXECUTED: `xcrun simctl list devices booted` returned no booted devices. Native app interaction is not available in this session. Automated expression/callback tests are not a substitute for visual simulator acceptance.

- [ ] Dry: open Edirne or Las Vegas for 2026-09-05 (recheck current forecast); Plan renders normally with no weather card/button; Assistant has no rain action.
- [ ] Rain: use a development forecast fixture with rain during a verified WALKING visit, a verified explicitly indoor adjacent stop, and a dry later slot. Confirm the displayed date/time/names and preview; verify nothing changes before tapping Apply, then only the listed stops exchange times/order. Do not modify production weather data to force this case.
- [ ] Unavailable: choose dates beyond the forecast horizon; Plan remains usable with no rain banner or offline rain Apply.
- [ ] Destination switch: open Edirne, then Las Vegas; return to Plan and Assistant. Confirm no old city’s weather remains, including while the new request loads.
- [ ] Stale preview: alter a listed stop or let the risk window pass before Apply; refresh after the conflict rather than applying an outdated proposal.

## Known limitations

- Existing Place providers often omit indoor/outdoor metadata. Unknown stops are deliberately not assumed sheltered, so legitimate rainy days can have no eligible preview. No external Place strategy was changed.
- Reorders adjacent existing slots only; it is not a route optimizer. Opening-hour feasibility and recalculated route distances are not available here. The existing walk estimate is retained, with no savings claimed.
- Uses persisted visit duration when known; otherwise a documented 60-minute planning window. Forecasts are hourly, not minute-level certainty.
- Incomplete ranges and DST days with missing/repeated local hours are conservatively unavailable. Ongoing trips whose start is before today are unavailable rather than partially forecast.
- Only the earliest eligible day’s preview is returned. Assistant may show it only for its current displayed day.
- New weather explanations are currently English. Existing localized UI labels remain available.
- Live observations are time-sensitive and do not guarantee the same forecast during manual retesting.
- Manual simulator acceptance remains outstanding.

## Final status

**WEATHER RELIABILITY ACCEPTED: NO — pending manual simulator acceptance.**

Implementation and automated/live backend checks pass: weather adjustment is now based on destination/date-specific forecast data and is no longer universally shown. The exact remaining blocker is unperformed visual verification of the dry, targeted-rain, unavailable and destination-switch flows on a running simulator. No subsequent feature was started.
