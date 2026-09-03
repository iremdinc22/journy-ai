# Phase 8 runtime acceptance investigation — 2026-09-03

READY FOR MANUAL RETEST: YES. Backend acceptance passed on a genuinely new trip; simulator visual acceptance remains for manual retest. Phase 9 was not started.

## Runtime request flow

TripSetupScreen submits its tripDraft to LoadingPlanScreen. LoadingPlanScreen calls tripApi.create: POST /api/trips (60-second timeout), then stores the returned trip in session. TripController calls transactional TripService.createTrip, which resolves the destination, saves a new Trip, and calls ItineraryGenerationService.generateIfMissing. The generator calls PlaceProviderService.loadCityPlaces(destination, null, true, 20) for the tested two-day balanced trip. Provider loading suspends the trip transaction, resolves Sarajevo, checks provider-only fresh/stale cache, calls OSM, upserts provider rows with providerFetchedAt, and reloads them. The planner applies verification/category/budget filters and persists itinerary days/stops. GET /api/trips/{id}/itinerary maps those persisted stops through ItineraryMapper. The Plan screen calls that GET for the session trip and consumes the returned days and stop titles.

Actual resolved destination: Sarajevo, latitude 43.8570713, longitude 18.4126147. No requested/resolved locality mismatch.

Reproduction used the real localhost:8080 API and PostgreSQL development database with a separate acceptance account, not a mock provider or injected place data. The simulator itself was not operated during this investigation.

## Root cause and actual fallback reason

Before changes, the development database contained ZERO Sarajevo places, including ZERO seed/legacy rows. Existing runtime logs repeatedly showed provider refresh candidates=0. A new instrumented request (trip_15e40531-bc12-4c0c-8a5d-3d319ef14c09) reproduced loaded=0, verified=0, categoryMatch=0, budgetMatch=0, legacyCandidates=0, fallbackStops=8. Its API response contained Old Town Walk, Neighborhood Bakery Pause, etc., with placeId=null and source=planned_fallback.

Two upstream failures were confirmed:

1. SimpleClientHttpRequestFactory's Java connection attempted an unreachable IPv4 Overpass address and returned Connection refused. The adapter silently converted this exception to an empty list. curl -4 failed too; curl using IPv6 and Java with system address ordering succeeded against the same hostname. Switching the OSM transport to Apache HttpClient allowed alternate DNS address attempts.
2. Once connected, the broad Overpass around query returned runtime timeout remarks or hit the client read timeout. Even a direct 20-second radius query returned zero elements with a timeout remark. An equivalent bounding-box query returned 20 named Sarajevo elements in 7.6 seconds. The final backend request received 20 elements in about 3.3 seconds.

The divergence from unit coverage was real HTTP address selection and execution of the broad discovery query; prior provider tests directly exercised query generation/parsing, while planner tests supplied candidates. Candidate verification, timestamp persistence, category/budget filtering, and response identity did not cause this reproduction.

## Cache and existing itinerary interaction

Provider cache queries exclude seed/starter/planned_fallback and require providerFetchedAt. Cache sufficiency could not suppress refresh because both fresh and usable counts were zero. Upsert correctly populated all timestamps once discovery succeeded.

generateIfMissing preserves an existing itinerary. Two older Sarajevo trips had null stop sources; the September 3 19:37 UTC trip had 12 planned_fallback stops and zero linked places. Thus old plans do remain old, but old data alone does not explain the failure: a new trip also reproduced it. No historical trips/itineraries were deleted or globally regenerated.

## Final new-trip request and filter counts

POST /api/trips: destination=Sarajevo, dates=2026-09-10 to 2026-09-12, travelerType=SOLO, budget=BALANCED, pace=BALANCED, interests=WALKING/CULTURE/COFFEE. The application's existing dayCount is 2 for these dates.

Trip: trip_7a39124e-b528-460b-99d3-2cd657958d2c.

loaded=20; verified=20; categoryMatch=20; budgetMatch=20; verifiedPath=true; legacyPath=false; plannedFallback=false; fallbackStops=0; persisted provider stops=8.

The development database now has 20 Sarajevo rows, all provider=osm and all providerFetchedAt non-null. No planner filter or ranking change was necessary. Existing LEAN behavior continues to admit Free/Lean prices and reject Mid; this final live request used BALANCED.

## Sarajevo DB place sample

All rows below have city=Sarajevo and provider=osm. Times are UTC on 2026-09-03.

| id | name | providerPlaceId | providerFetchedAt | category | latitude | longitude |
|---|---|---|---|---|---|---|
| osm_node_375762263 | Zmajevac | node/375762263 | 20:02:24.218695 | WALKING | 43.8669491 | 18.4418938 |
| osm_node_492137265 | Art | node/492137265 | 20:02:24.241452 | COFFEE | 43.867595 | 18.4122772 |
| osm_node_524782368 | Golf club | node/524782368 | 20:02:24.244994 | FOOD | 43.885238 | 18.4093188 |
| osm_node_704412254 | Konak | node/704412254 | 20:02:24.253224 | CULTURE | 43.8563325 | 18.4307435 |
| osm_node_747043184 | Muzej Alije Izetbegovića | node/747043184 | 20:02:24.254876 | CULTURE | 43.8631599 | 18.4366199 |

## Actual API stop sources

GET /api/trips/trip_7a39124e-b528-460b-99d3-2cd657958d2c/itinerary returned HTTP 200. Complete response is in PHASE8_RUNTIME_ACCEPTANCE.json.

| title | placeId | source |
|---|---|---|
| Zmajevac | osm_node_375762263 | provider:osm |
| Art | osm_node_492137265 | provider:osm |
| Konak | osm_node_704412254 | provider:osm |
| Golf club | osm_node_524782368 | provider:osm |
| Muzej Alije Izetbegovića | osm_node_747043184 | provider:osm |
| Coccinelle | osm_node_938872352 | provider:osm |
| Velika Bašta | osm_node_699104717 | provider:osm |
| Tito | osm_node_674049031 | provider:osm |

No frontend change was made: the original generic names were present in the backend API response itself, and there was no evidence of a frontend mapping bug.

## Files changed and fix made

Existing Phase 4–8 working-tree edits were preserved. This investigation changed:

- pom.xml: add Spring Boot-managed httpclient5 dependency.
- src/main/java/com/journy/backend/explore/provider/OsmOverpassPlaceProvider.java: alternate-address-capable HTTP transport; named bounding-box queries with circular distance validation and polar/dateline radius-query fallback; query/read timeout headroom (20/23 seconds); request/response failure diagnostics.
- src/main/java/com/journy/backend/explore/provider/PlaceProviderService.java: requested/resolved destination and coordinate diagnostics.
- src/main/java/com/journy/backend/itinerary/service/ItineraryGenerationService.java: existing-itinerary, stage counts, candidate metadata, selected path, and fallback count/reason diagnostics.
- src/test/java/com/journy/backend/explore/provider/OsmOverpassPlaceProviderTest.java: bounds assertions and an actual local HTTP regression test excluding bounding-box corners outside the circular radius.
- PHASE8_RUNTIME_ACCEPTANCE.md and PHASE8_RUNTIME_ACCEPTANCE.json: this evidence and captured final API response.

plannedStop remains. No Phase 9 work or new ranking engine.

## Test results

Compile: PASS. git diff --check: PASS.

| Test | Passed |
|---|---:|
| PlaceProviderServiceTest | 27 |
| DestinationResolutionServiceTest | 18 |
| OsmOverpassPlaceProviderTest | 3 |
| ItineraryGenerationServiceUnitTest | 5 |
| ItineraryGenerationServiceTest | 1 |
| ItineraryControllerIntegrationTest | 1 |

55 tests passed, zero failures/errors. Initial integration execution hit the sandbox's Mockito agent-attachment restriction; rerun outside the sandbox passed. Final seeded H2 integration tests disabled external OSM via a command-line property; the provider unit/HTTP tests instantiate their own enabled provider. Live PostgreSQL/API acceptance separately used real OSM and passed. The added radius regression test also passed after the full suite.

## Manual retest

Backend is running on port 8080 with the fix; diagnostics are in .dev-logs/phase8-backend.log. In the simulator create a NEW Sarajevo trip. Existing historical itineraries intentionally retain their old stops. Expect real names such as Zmajevac, Art, Konak, and Muzej Alije Izetbegovića; supply may vary with budget and duration. This confirms backend readiness for manual retest, not completed simulator visual acceptance. External provider outages can still trigger Phase 8 fallback.
