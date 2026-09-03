# Phase 9 — Planner validation and dynamic day titles

Implementation and backend runtime regression are complete. READY FOR PHASE 10: NO — Phase 9 simulator visual acceptance remains pending. Phase 10 was not started.

## Current title generation / root cause of generic titles

Previously, ItineraryGenerationService.dynamicThemeFor called cityDayTheme, which returned hardcoded city themes or generic category/interest combinations. Its generic return was non-null, preventing the subsequent place-anchor branch from being used. These titles were persisted and passed through the mapper. Mobile then applied localizeDynamicText fragment substitutions to the English titles, causing mixed-language output. No LLM was involved.

## Planner validation rules

New provider-backed generation requires a nonblank Place ID, name, city and provider; providerFetchedAt; a category; finite latitude/longitude within valid bounds; and a provider other than seed/starter/planned_fallback. The invalid (0,0) pair is rejected. Individual zero latitude or longitude remains valid.

Candidates must match the trip's already-resolved destination, ignoring case and surrounding whitespace. Existing category/budget rules remain in effect. Before selection, the accepted provider identities are copied into an immutable snapshot. Before saveAll, every provider stop must match a snapshot entry's ID, source, name, category, latitude and longitude. Invalid internal output fails validation instead of being silently persisted as verified. This validation applies to newly generated itineraries only.

## Duplicate protection

A LinkedHashMap keeps the first ranked candidate per Place ID before daily selection. The itinerary-wide used-ID set prevents reuse across days. A replaced daily selection releases its unused ID. Final validation also rejects repeated non-null Place IDs across all days. This is deterministic deduplication, not a new ranking engine.

## Destination isolation

Mixed Sarajevo/Tallinn candidate pools are explicitly tested in both directions. A real JPA/H2 cache integration test commits provider rows for Amsterdam and Copenhagen into an isolated database and generates both destinations through the real provider-cache service and generator. Each itinerary consumes only its own city's rows.

## Unknown candidate behavior

An internal D selection outside accepted A/B/C cannot receive provider provenance: final validation throws before persistence. A verified-looking repository D absent from the provider result cannot re-enter through the legacy repository path. A malformed provider response is excluded before selection. Existing unverified repository fallback remains separately labelled repository; planned fallback has no Place ID.

## Coordinate integrity result

Verified stops retain exact entity coordinates; no offsets, replacement centers or rounding are applied. Tests reject altered coordinates and validate every selected stop against its source Place. Live PostgreSQL verification for the new Sarajevo trip found 12/12 exact matches of name, coordinates, destination and populated fetch timestamp.

## Candidate exhaustion result

A three-day itinerary with only two unique verified candidates uses those two exactly once, then creates ten planned_fallback stops with null placeId. Fallback is retained as required for Phase 9.

## Legacy compatibility result

An existing itinerary with null placeId/source stays unchanged under generateIfMissing and is readable through ItineraryMapper. Empty titleTranslations preserves the previous client rendering path, including the day-detail legacy prefix cleanup. No historical-row migration was introduced.

## New day title strategy

DayTitleGenerator reads provider-backed stops only. It counts existing categories and selects up to two dominant categories, breaking ties by actual visit order. A short real Place name from the leading category anchors the title. Long anchors fall back to a category route title; weak/unknown categories use a neutral title. Titles are deterministic, short, and derived from selected composition. A proper-name anchor supplies local context without city-specific hardcodes or invented POIs. The generator does not infer art from a cafe named Art or nightlife from a venue name.

Newly generated provider days persist the English composition title. On API mapping, whole English and Turkish titles are exposed through additive titleTranslations metadata. Place entities, stop titles, provider identity and coordinates are untouched by title generation. All-fallback/legacy day generation retains existing behavior.

## Language consistency strategy

The mobile app's existing language context selects titleTranslations.en or titleTranslations.tr directly. These complete titles bypass fragment translation in the Plan screen, day-detail screen, and existing day selector in PlaceDetail. Proper names retain their original spelling. Old payloads without titleTranslations use existing rendering. No new locale system or stop translation behavior was introduced.

## Title fallback strategy

If a short anchor cannot fit, use the verified category composition, such as “Kültür ve Kahve Rotası.” If no supported verified category exists, use “Şehir Keşfi” / “City Discovery.” No invented POI, “Day 1,” or city-specific title is used by this title generator.

## Tests added

- Five generator tests: mixed destination isolation and exact identity; duplicate IDs across days and exhaustion; malformed provider data; unknown repository candidate bypass; legacy mapping/read preservation.
- Three final contract tests: unknown IDs and name/source/category/coordinate corruption; immutable snapshots; cross-day duplicates and wrong destination snapshots.
- Six title tests: museum/cafe composition; food/social composition using existing categories; weak-category fallback; determinism and visit order; arbitrary destinations/long anchors; mapper/stop identity preservation.
- One isolated JPA cache integration test: committed fresh cache, two destinations, no discovery calls, persisted identity and localized response metadata.
- Two mobile tests: selecting whole translations without fragment mixing or identity mutation, and old-payload compatibility.

## Test results

- ./mvnw -q -DskipTests compile: PASS.
- Full backend suite: 77 tests, zero failures, zero errors, zero skipped.
- Requested focused suites included: ItineraryGenerationServiceUnitTest (10), PlaceProviderServiceTest (27), OsmOverpassPlaceProviderTest (3), DestinationResolutionServiceTest (18), plus all new Phase 9 suites.
- npx tsc --noEmit: PASS.
- node --test tests/itineraryDayTitle.test.cjs: 2 passed.
- git diff --check: PASS.

The initial sandboxed focused run encountered the known Java 23/Mockito attachment restriction and localhost HTTP-test restrictions. The elevated full suite passed. Full-suite execution disables live OSM/Nominatim by command-line test properties; provider fixtures/local HTTP tests still run, and real development-DB/API regression is separate. No application architecture was changed to accommodate Mockito. The cache integration test has its own H2 database so committed fixtures do not leak to other test classes.

## Manual runtime regression and retest expectation

POST /api/trips through the live backend created trip_bea8ca95-e204-4b4e-9376-9169ac8073e0, Sarajevo, September 10–13, SOLO/BALANCED/BALANCED, WALKING/CULTURE/COFFEE. GET /api/trips/{id}/itinerary returned HTTP 200.

Runtime: fresh cache=20, loaded=20, verified=20, categoryMatch=20, budgetMatch=20. No Overpass refresh was needed. The three-day result contains 12 distinct Place IDs, 12 provider:osm stops, zero planned fallback, and 12 exact database matches.

Actual Turkish titles:

1. Zmajevac: Yürüyüş ve Kahve
2. Muzej Alije Izetbegovića: Kültür ve Kahve
3. Kafanica: Kahve ve Yürüyüş

Actual stops include Zmajevac, Art, Konak, Golf club, Muzej Alije Izetbegovića, Coccinelle, Velika Bašta, Tito, Kafanica, City Pub, Bijela tabija and SO.BA. All retain non-null placeId and source=provider:osm. See PHASE9_RUNTIME_ACCEPTANCE.json for the complete API response. Backend diagnostics are in .dev-logs/phase9-backend.log.

Reload the mobile bundle and create a new Sarajevo trip. With Turkish selected, expect complete Turkish category wording with original proper names; switch to English to see the corresponding complete English title. Confirm both the Plan cards and day detail. The simulator was not operated in this turn, so that visual acceptance remains pending.

## Files changed

Backend:

- src/main/java/com/journy/backend/itinerary/service/PlannerPlaceContract.java (new)
- src/main/java/com/journy/backend/itinerary/service/DayTitleGenerator.java (new)
- src/main/java/com/journy/backend/itinerary/service/ItineraryGenerationService.java
- src/main/java/com/journy/backend/itinerary/dto/ItineraryResponse.java
- src/main/java/com/journy/backend/itinerary/mapper/ItineraryMapper.java
- src/test/java/com/journy/backend/itinerary/ItineraryGenerationServiceUnitTest.java
- src/test/java/com/journy/backend/itinerary/PlannerPlaceContractTest.java (new)
- src/test/java/com/journy/backend/itinerary/DayTitleGeneratorTest.java (new)
- src/test/java/com/journy/backend/itinerary/PlannerCacheIntegrationTest.java (new)
- PHASE9_REPORT.md and PHASE9_RUNTIME_ACCEPTANCE.json (new evidence)

Mobile:

- src/api/types.ts
- src/utils/itineraryDayTitle.ts (new)
- src/screens/ItineraryScreen.tsx
- src/screens/DayRouteDetailScreen.tsx
- src/screens/PlaceDetailScreen.tsx (day-title display only)
- tests/itineraryDayTitle.test.cjs (new)

## Known limitations

Titles use existing coarse provider categories; inaccurate category metadata can yield a less descriptive theme. Proper names intentionally remain untranslated. Only the existing English/Turkish UI languages are covered. Historical all-null provenance and all-fallback titles keep their previous behavior. Pre-save contract violations fail generation and rely on the surrounding trip transaction to roll back, rather than fabricating a replacement verified stop. Destination matching assumes TripService's existing canonical locality resolution. This phase does not provide geographic-boundary inference for wrongly labelled upstream metadata.

Provider/cache architecture, external providers, Explore fallbacks, TripSetup starter suggestions, NIGHTLIFE, ranking, route optimization and unrelated UI were not modified. plannedStop remains. READY FOR PHASE 10: NO until Phase 9 manual simulator acceptance; no Phase 10 implementation performed.
