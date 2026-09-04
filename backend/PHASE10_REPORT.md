# Phase 10 — Verified places or controlled insufficient data

READY FOR COMBINED MANUAL REGRESSION: YES. Backend/API runtime verification and automated tests passed. Combined simulator visual acceptance remains to be performed. No Phase 11 work was started.

## Current fallback paths: audit and outcome

Before this phase, these paths could create or persist invented itinerary stops:

1. Trip creation and explicit regeneration called ItineraryGenerationService. Missing candidates used the legacy repository path; missing daily slots called fillWithPlannedStops → plannedStop → hardcoded cityStopNames. These produced generic names and substituted/offset coordinates.
2. AI apply actions added a generic food break or replaced a stop with a better-fit, indoor, or budget placeholder; these used null identity and fabricated/copied coordinates.
3. Agent apply actions likewise added or renamed stops into generic food/replacement/budget/indoor placeholders, including changes that retained an old stop's identity while changing its name.
4. Manual addPlaceToDay trusted request presentation/coordinate fields, accepted arbitrary IDs without resolving a Place, and supplied city-center coordinates when absent. Explore starter entries could thereby become itinerary stops.
5. DatabaseSeeder.seedItinerary created generic sample itinerary rows on a fresh installation.

All five synthetic entry paths are removed or blocked. The only remaining production ItineraryStop constructors are verified generator output and a manual addition resolved to a verified database Place. Mapper/GET paths still read historical rows; they do not generate or revalidate them. generateIfMissing returns existing persisted days unchanged.

## Minimum viable itinerary rule

Chosen behavior: B, all-or-nothing. The minimum viable itinerary is the existing requested schedule, not a silently shortened schedule:

required distinct verified candidates = Trip.dayCount() × stopsPerDay(trip).

Existing stopsPerDay rules: RELAXED=3, BALANCED=4, FULL=5; LEAN reduces counts above 3 by one. No new threshold setting or scattered constants were introduced. Destination, verification, category and budget filters run before deduplication and supply counting. Both sufficient data and final generated identities are checked before persistence. Regeneration constructs and validates its replacement before deleting existing days.

This choice preserves the current API/mobile assumption that successful generation supplies every requested day at the selected pace, without introducing a new partial-plan state.

## Insufficient data contract

HTTP 422 with the existing ApiErrorResponse shape:

- error: INSUFFICIENT_DESTINATION_DATA
- message: We couldn't find enough reliable places for this destination yet. Please try again or choose a shorter trip.
- details: requiredPlaces=N, availablePlaces=M

InsufficientDestinationDataException is a runtime application exception handled by GlobalExceptionHandler. TripService's existing transactions roll back failed creation/current-trip changes. A failed regeneration preserves its old rows even before transaction rollback because deletion is deferred. No fake HTTP 200 itinerary is returned.

## Partial data behavior

Partial unique supply below the full requirement deterministically fails with 422. No duplication, invented slots, empty days, or invented day titles are persisted. The Phase 9 contract now rejects every newly generated non-provider stop, rather than allowing planned/repository stops through final validation.

## Provider failure behavior

Provider/cache architecture is unchanged:

- Sufficient fresh cache is consumed without discovery.
- Stale usable provider cache is retained when refresh fails; it succeeds only if it meets the planner's full schedule requirement.
- No usable cache plus provider failure returns controlled insufficient data.
- Same-city seed/starter rows and other-city places cannot rescue insufficient verified supply.

## Historical compatibility

Existing source=null/placeId=null and source=planned_fallback rows remain readable through the mapper and are preserved by generateIfMissing. Failed regeneration preserves historical data and titles. No historical rows were deleted or rewritten. On fresh installations the seeder no longer creates demo itinerary rows or advertises their former fabricated stop statistics; its Explore Place/destination seed data remains unchanged.

## Mobile failure handling

ApiError retains the backend error code. LoadingPlan's existing catch/finally path chooses a whole Turkish/English insufficient-data message, stops loading, and displays the existing Retry and Edit setup controls. It navigates to the Plan only after successful trip creation. Authentication, validation, and network error handling remain intact. TripSetup suggestions and Explore fallback UI were not changed.

## Planned fallback removal

Removed plannedStop, fillWithPlannedStops, plannedStopName, cityStopNames, generic day-theme helpers, legacy planner repository fallback, and invented-coordinate helpers. Phase 9 DayTitleGenerator remains the sole new-generation title mechanism.

AI/agent add/replace/rain/budget mutations had no verified selection implementation. They now fail with the controlled error before changing a day; their synthetic mutation helpers are removed. Existing read-only suggestions and lighter-day removal actions remain. No replacement engine was added.

Manual addition now resolves request.placeId to a verified database Place for the trip destination, takes name/category/provider/coordinates from that entity, and prevents duplicate IDs across the whole itinerary. Unknown, unverified and wrong-destination entries fail with 422 instead of becoming synthetic stops.

## Remaining generic stop occurrences

Every remaining requested-pattern match is listed with file/line and classification in PHASE10_REMAINING_OCCURRENCES.txt, including prior phase evidence.

Production-source matches are limited to:

- PlannerPlaceContract's planned_fallback rejection string.
- ExploreService's Prague “Old Town - river - cafes” display label.
- DatabaseSeeder destination labels/addresses and the explicitly preserved Explore seed Place names: Central Espresso Bar, Old Town Morning Loop, Vinohrady Coffee Break, Lokal Dinner Window (Old Town address), Kreuzberg Coffee Break, and Karakoy Coffee Break.

Other matches are negative/historical test fixtures, assertions against generic titles, or prior phase reports/captured historical API data. There are no production plannedStop references, generic itinerary stop-name arrays, or synthetic stop constructors. Preserved Explore rows cannot enter either new generation or manual additions without passing verified Place validation.

## Tests added or updated

- Generator coverage for zero supply, insufficient unique supply, duplicate IDs, invalid candidates, legacy repository rejection, destination isolation, fresh cache, stale-cache recovery after provider failure, no-cache provider failure, and failed-regeneration preservation.
- Historical mapping test includes both null-source and planned_fallback rows.
- Final contract test rejects null, planned_fallback and repository sources for new stops.
- Three API integration tests verify failed-creation rollback/current-trip preservation, failed-regeneration preservation, and rejection of all eight synthetic AI/agent apply modes plus arbitrary manual additions without modifying the original itinerary.
- Manual-add integration verifies canonical database fields override untrusted request names and retain provider identity.
- Existing generation/profile/controller tests now provide sufficient verified fixtures rather than relying on the removed seeded fallback.
- Two mobile tests exercise actual API 422 parsing/rejection and localized-message selection, plus preservation of other error behavior.
- Phase 9 title/identity/cache tests and earlier provider/resolution suites remain in the full run.

## Test results

- ./mvnw -q -DskipTests compile: PASS.
- Requested focused backend suites: PASS.
- Full backend suite: 83 tests, zero failures/errors/skipped.
- TypeScript: npx tsc --noEmit PASS.
- Mobile: node --test tests/*.test.cjs, four passed.
- git diff --check: PASS.

Tests were run with elevated access for the already-known Mockito attachment/local HTTP restrictions. Full-suite command disables external OSM/Nominatim through test-only command-line properties; provider fixture/local HTTP tests still execute, and live API verification is separate. No Java/Mockito workaround was added to application architecture.

The first full run had one profile fixture expecting HTTP 200 from seed-only data; this was a stale test assumption under the intentionally changed contract. Replacing its seed-only dependency with verified fixtures produced the clean final run. No remaining application regression was observed.

## Live API/database validation

New successful trip: trip_7a42d2a5-1f30-48ad-91c9-98534841e387, Sarajevo, September 10–13, SOLO/BALANCED/BALANCED, WALKING/CULTURE/COFFEE.

- Fresh cache=20; loaded=20; verified=20; categoryMatch=20; budgetMatch=20.
- POST/itinerary GET HTTP 200.
- 12 stops, 12 unique Place IDs, all source=provider:osm, zero fallback.
- SQL join confirms 12/12 exact name/coordinate/destination matches against Place rows.
- Turkish titles preserved: “Zmajevac: Yürüyüş ve Kahve”; “Muzej Alije Izetbegovića: Kültür ve Kahve”; “Kafanica: Kahve ve Yürüyüş”.

An intentionally overlong September 10–30 Sarajevo request returned HTTP 422, requiredPlaces=80, availablePlaces=20. SQL confirmed no trip with that end date was persisted for the acceptance account and the successful three-day trip remained current. Successful payload: PHASE10_RUNTIME_ACCEPTANCE.json. Failure payload: PHASE10_INSUFFICIENT_RESPONSE.json. Runtime log: .dev-logs/phase10-backend.log.

## Files changed

Backend production:
- itinerary/service/ItineraryGenerationService.java
- itinerary/service/PlannerPlaceContract.java
- itinerary/service/ItineraryService.java
- common/exception/InsufficientDestinationDataException.java (new)
- common/exception/GlobalExceptionHandler.java
- ai/service/AiService.java
- agent/service/AgentService.java
- seed/DatabaseSeeder.java

Backend tests:
- itinerary/ItineraryGenerationServiceUnitTest.java
- itinerary/ItineraryGenerationServiceTest.java
- itinerary/PlannerPlaceContractTest.java
- controller/ItineraryControllerIntegrationTest.java
- controller/ProfileControllerIntegrationTest.java
- controller/InsufficientDataIntegrationTest.java (new)
- support/VerifiedPlaceFixtures.java (new, test-only)

Mobile:
- src/api/client.ts
- src/screens/LoadingPlanScreen.tsx
- src/utils/loadingPlanErrorMessage.ts (new)
- src/i18n/translations.ts (two error-message translations)
- tests/loadingPlanError.test.cjs (new)

Evidence: PHASE10_REPORT.md, PHASE10_RUNTIME_ACCEPTANCE.json, PHASE10_INSUFFICIENT_RESPONSE.json, PHASE10_REMAINING_OCCURRENCES.txt.

## Known limitations and combined manual regression

The existing provider-load limit is 30 candidates; requests needing more than that fail explicitly rather than silently truncating/filling. Shorter requests can also fail after budget/category filtering even when unrelated raw data exists. No discovery/cache/ranking redesign was performed.

Synthetic AI/agent replacement modes are intentionally unavailable until they have verified selection logic; read-only previews may still offer those actions, and applying them returns controlled failure. Adding unverified Explore starter entries likewise fails; Explore display fallback itself is unchanged. Historic titles/rows remain legacy-compatible. Simulator visual behavior was not operated in this turn.

Combined manual regression: reload the mobile bundle; create a three-day balanced Sarajevo trip and verify real stops and complete Turkish day titles. Then request a deliberately overlong trip and verify the clear insufficient-data message, no navigation to a fake new plan, and usable Retry/Edit setup buttons. The previously successful trip should remain current.

READY FOR COMBINED MANUAL REGRESSION: YES. Stop here; no Phase 11 implementation.
