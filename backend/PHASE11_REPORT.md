# Phase 11 — Final Regression and Generalization Acceptance

## FINAL PIPELINE STATUS

**UNIVERSAL DESTINATION PIPELINE ACCEPTED: YES.**

The seven required destinations passed both live development smoke checks and an isolated, internet-independent integration matrix. Two additional same-name destinations exposed a real correctness defect; the corrected pipeline passes both cases, including regeneration. Unseen destinations no longer require manual city seeding. Fake itinerary generation has been removed from the new production flow, including a remaining mobile preview fallback found in this phase.

Acceptance uses the explicitly permitted **prepared simulator checklist** option. Native simulator visual acceptance has **not** been performed; it is not represented as a passing test.

Validation date: 2026-09-04, Europe/Istanbul. Work stopped at Phase 11. Existing Phase 10 changes were preserved; only the defects described below required production changes in Phase 11.

## TEST MATRIX BY DESTINATION

Live requests used October 10–12, 2026, SOLO / BALANCED / BALANCED, CULTURE, COFFEE, LOCAL_FOOD, WALKING. Every successful itinerary contained eight distinct provider-backed stops and zero planned fallbacks.

| Input → resolved locality | Initial provider cache | First live path | Raw OSM elements | Accepted / planner-verified / persisted provider rows | Max cached distance from resolved center | Stop count / fallback |
|---|---:|---|---:|---:|---:|---:|
| Las Vegas → Las Vegas | 0 | Nominatim → discovery | 20 | 17 / 17 / 17 | 4.441 km | 8 / 0 |
| Tallinn → Tallinn | 0 | Nominatim → discovery | 20 | 20 / 20 / 20 | 2.185 km | 8 / 0 |
| Bologna → Bologna | 0 | Nominatim → discovery | 20 | 20 / 20 / 20 | 4.353 km | 8 / 0 |
| Bruges → Brugge | 0 | Nominatim → discovery | 20 | 19 / 19 / 19 | 1.486 km | 8 / 0 |
| Antalya → Antalya | 0 | Known coordinates → discovery | 20 | 20 / 20 / 20 | 2.645 km | 8 / 0 |
| Sarajevo → Sarajevo | 20 | Nominatim → fresh cache | Not called | 20 cached / 20 / 20 | 3.227 km | 8 / 0 |
| Copenhagen → Copenhagen | 20 | Known coordinates → fresh cache | Not called | 20 cached / 20 / 20 | 3.980 km | 8 / 0 |

All persisted provider rows have providerFetchedAt and valid coordinates. “Raw elements” is the provider response count before validation; it is not claimed to be verified supply. Sarajevo and Copenhagen reused existing provider cache, not manually added data. Antalya already had a known coordinate mapping but no Place rows in the initial development cache.

| Destination | Example exact provider names | Example Turkish day title |
|---|---|---|
| Las Vegas | Highland Valley Park; Salon De Belleza; Sinatra | Highland Valley Park: Yürüyüş ve Kültür |
| Tallinn | Piiskopi vaateplatvorm; Maiasmokk; Lastepargi kivi | Piiskopi vaateplatvorm: Yürüyüş ve Kahve |
| Bologna | Bagni di Mario; Bar Crystal; Trattoria da Vito | Bagni di Mario: Yürüyüş ve Kahve |
| Brugge | Simon Stevin; Le Pain Quotidien; Breydel de Coninc | Simon Stevin: Yürüyüş ve Kahve |
| Antalya | Atatürk Heykeli; Starbucks; Hasanağa Restaurant | Atatürk Heykeli: Yürüyüş ve Kahve |
| Sarajevo | Zmajevac; Art; Konak | Zmajevac: Yürüyüş ve Kahve |
| Copenhagen | Ølbaren; Café Moccador; Juma | Ølbaren: Yürüyüş ve Kahve |

These are provider names and metadata, not a claim of editorial quality or independently audited venue operations. No ranking or metadata redesign was made.

Live evidence: `phase11/live/*.json`, `live-provider.log`, `live-places.csv`, `live-cache-summary.json`. After fixes, a restarted development backend passed the full seven-city matrix again using fresh cache: `phase11/live-after-fix/*.json` and `live-after-fix-provider.log`.

## LAS VEGAS ACCEPTANCE RESULT

PASS. Initial read-only database inspection found zero Las Vegas Place rows. The live request resolved to 36.1674263, -115.1484131, discovered OSM places, and persisted 17 usable candidates. All eight selected stops retained exact Place IDs, names, provider sources and coordinates. Titles derive from those stops. No seed insertion script or Las Vegas production special case was added.

## UNSEEDED DESTINATION RESULTS

Las Vegas, Tallinn, Bologna, Brugge and Antalya started with zero provider rows in development; discovery populated each cache. No unrelated development data was deleted. Sarajevo and Copenhagen retained their preexisting cache.

The isolated H2 matrix starts each required destination with zero provider cache, including Sarajevo and Copenhagen. A loopback HTTP server supplies explicit test fixtures to the real Nominatim and OSM adapters. Resolution, bounded queries, validation, persistence, planner, API mapping and Explore all run normally. Each response includes duplicate, unnamed and geographically distant elements; only 12 unique valid fixture places persist. Fixture venues are test-only and are not presented as live provider evidence.

## FIRST/SECOND REQUEST CACHE RESULTS

PASS. Live Las Vegas: first request 3.36 s, cache miss → discovery → 17 persisted; second 0.79 s, fresh hit, no Overpass call. Live Tallinn: first 6.08 s → 20 persisted; second 0.30 s, fresh hit, no Overpass call. Times are observations, not performance guarantees.

The deterministic matrix asserts exactly one OSM request per geographic destination across two trip creations, explicit regeneration of the second trip, and Explore. Other destinations' row names, coordinates and freshness timestamps remain unchanged.

Same-name coverage uses **Portland Maine** and **Portland Oregon** as test inputs resolving to the same locality “Portland,” with distinct coordinates and provider identities. Both pass without sharing Place pools. Cache filtering now uses the resolved geographic boundary as well as locality. The original request is persisted internally so regeneration does not lose regional disambiguation.

## PROVIDER FAILURE RESULTS

PASS. Existing deterministic provider tests cover fresh cache reuse, stale refresh/upsert without duplicate growth, stale usable cache after provider failure, and failure with no cache. Generator tests distinguish sufficient stale verified supply from insufficient supply. Other-city and seed rows cannot rescue an empty verified pool.

HTTP 503 and actual read-timeout transport tests return no candidates. They do not manufacture Place rows or successful itineraries. Planner exhaustion remains controlled failure.

## INSUFFICIENT DATA RESULT

PASS. Three API integration tests cover failed-creation rollback/current-trip preservation, failed regeneration preserving existing itinerary JSON and titles, and rejection of synthetic AI/agent mutations or arbitrary manual additions. Supply is deterministic and does not depend on internet failure.

Final live overlong Sarajevo request (October 10–30): HTTP 422, `INSUFFICIENT_DESTINATION_DATA`, `requiredPlaces=80`, `availablePlaces=20`. The current trip ID before and after is identical. Evidence: `phase11/live-insufficient.json`.

Mobile tests verify actual API error parsing/rejection and localized message selection. LoadingPlan's catch/finally and Retry/Edit handlers remain intact. Their native touch behavior is included in the unperformed simulator checklist below.

## STOP IDENTITY RESULTS

PASS. SQL joined all 18 live acceptance trips, before and after fixes: **144 stops, 144 exact Place identity matches**, including destination, name, provider source, latitude, longitude and populated provider timestamp. Evidence: `phase11/live-identity.sql` and `live-identity.csv`.

The nine-case isolated matrix also checks every persisted stop against its exact Place row. There are no coordinate deltas or title-driven mutations of names or IDs.

## DUPLICATE RESULTS

PASS. Every live itinerary has eight stops and eight distinct Place IDs. Deterministic tests reject duplicate supply, prohibit reuse across days, and fail on exhaustion rather than padding. Duplicate OSM elements do not increase usable pool size.

## DAY TITLE RESULTS

PASS. Existing six backend title tests and two mobile title tests remain green. Real provider names stay verbatim; connective/category text is localized as a whole. Turkish output does not use mixed English template fragments. Long names, neutral composition, legacy titles, stable ordering and identity preservation remain covered.

## HISTORICAL COMPATIBILITY

PASS for backend and mobile data-selection tests. Null Place IDs, null sources and planned_fallback historical rows remain readable. Existing days are retained by generateIfMissing. Failed regeneration leaves them intact. The mobile regression explicitly preserves loaded historical objects unchanged.

No historical stop rows were migrated, deleted or rewritten. The new nullable `destination_query` column does not backfill old trips; those retain their previous locality lookup. Native display of an old trip remains on the manual checklist.

## EXPLORE REGRESSION

PASS. Every required live destination returned HTTP 200 with provider places before and after the fixes. The isolated matrix checks IDs, locality and cache reuse, including the two Portland inputs. Explore seed/starter display fallback is intentionally preserved; those entries cannot become newly generated verified itinerary stops.

## PROVIDER RELIABILITY

PASS. Tests verify:

- Apache HTTP transport reaches a loopback server through the second DNS address after the first address refuses connection. Only DNS is controlled; this is not a claim that every public IPv4/IPv6 endpoint was separately exercised live.
- Queries contain named-element filters and resolved-coordinate bounding boxes, with no `around:` path.
- Bounding-box corner candidates outside the circular radius are rejected.
- Unnamed, duplicate and distant elements are excluded before persistence.
- HTTP errors and read timeouts yield empty supply, followed by the existing controlled planner behavior.

Production timeouts remain connect 3 s/read 23 s and Overpass query timeout 20 s. The timeout test shortens only its local read timeout. No new retry/backoff architecture was introduced. Live checks are separate scripts; CI does not need external providers.

## FORBIDDEN FALLBACK AUDIT

**Production fake itinerary-stop generation path count: 0.** Only two backend ItineraryStop constructors remain: verified generation and canonical database-backed manual addition. The mobile Plan preview stop/coordinate generator was removed after a failing test reproduced it.

Every literal match in backend/mobile source and tests is recorded with file and line in `phase11/fallback-occurrences.txt`. Classification:

- `PlannerPlaceContract.java` — planned_fallback rejection guard; historical compatibility boundary, not generation.
- `DatabaseSeeder.java` — “Central Espresso Bar” in the preserved Explore seed dataset; UI/Explore data, excluded from verified itinerary generation.
- `localizedDynamicText.ts` — “Garden Route” translation; UI text for legacy content.
- `TripService.previewDays` — two references calculate a setup day count; UI preview metadata, not stop generation.
- `PlannerPlaceContractTest`, `ItineraryGenerationServiceUnitTest`, `DayTitleGeneratorTest`, `itineraryDayTitle.test.cjs`, `itineraryFailure.test.cjs` — negative or historical test fixtures.

No production plannedStop, Historic Center Stroll, Old Town Walk, Local Coffee Break, cityStopNames, or previewStopTitle remains. Broader inspection of constructors and coordinate-offset consumers found only the explicitly preserved Explore starter generation and map/layout calculations. Existing hardcoded Place lists in DatabaseSeeder.seedPlaces are Explore data; they are not a supported-city whitelist or itinerary stop source. Historical reports and captured payloads are evidence, not executable production paths.

## PRODUCTION CITY-SPECIFIC CODE AUDIT

PASS: zero new city-specific business logic. `phase11/production-baseline.json` captured production hashes before Phase 11 changes; `production-changed-files.txt` identifies the exact Phase 11 production changes despite the existing dirty Phase 10 working tree.

`phase11/city-occurrences.txt` lists every case-insensitive production match for the requested city names plus Portland. All matches are preexisting Antalya coordinate, country, image, descriptive UI or TripSetup mappings. No production Las Vegas, Tallinn, Bologna, Bruges, Sarajevo or Portland special case was added. Regression city names appear only in tests, smoke scripts and evidence.

## BACKEND TEST TOTAL

**95 passed; 0 failures, 0 errors, 0 skipped.** Full-suite command:

`./mvnw -q -Djourny.places.osm.enabled=false -Djourny.destinations.nominatim.enabled=false test`

The matrix overrides only its own endpoints with a loopback fixture server. The established elevated Mockito/local-server environment was used. Totals and per-suite counts: `phase11/test-totals.json`; final output: `phase11/backend-tests.log`.

Added coverage: nine destination integration cases and three OSM transport cases. Before fixes, the mobile unavailable-itinerary test and same-name destination test failed as intended. After the geographic fix, two older API tests failed because their fixture helper assigned Amsterdam coordinates to all cities; the helper now uses correct known-city coordinates. No remaining failure is suppressed or disabled. Diagnostic logs are retained.

## MOBILE TEST TOTAL

**6 passed; 0 failed.** `node --test tests/*.test.cjs`. Two new tests execute the Plan screen's actual day-selection expression to check missing-itinerary and historical-data behavior. Evidence: `phase11/mobile-tests.log` and `mobile-before-fix.log`.

## COMPILE RESULT

**PASS** — `./mvnw -q -DskipTests compile`. Evidence: `phase11/compile.log` (empty successful output). `git diff --check` also passed.

## TYPESCRIPT RESULT

**PASS** — `npx tsc --noEmit`. Evidence: `phase11/typescript.log` (empty successful output).

## MANUAL SIMULATOR CHECKLIST

**Prepared, NOT performed.** Reload the latest mobile bundle, use the restarted development backend, sign in, and select Turkish. Use the live payloads above as the identity/title comparison reference.

### A. Sarajevo success

- [ ] Create October 10–12, BALANCED pace/budget, CULTURE/COFFEE/WALKING/LOCAL_FOOD.
- [ ] Plan shows eight real provider names across two days, no generic fillers, no duplicate Place IDs.
- [ ] Complete Turkish titles match the API, including Zmajevac / Muzej Alije Izetbegovića when selected.
- [ ] Open each Day Detail; its title matches the Plan card.
- [ ] Map pins correspond to the returned stop coordinates and real places; inspect multiple pins.
- [ ] Navigate between Plan, Day Detail and place details without a crash.

### B. Las Vegas or Tallinn success

- [ ] Repeat the same setup and checks. Compare against `phase11/live-after-fix/las-vegas.json` or `tallinn.json`.
- [ ] Confirm destination-specific real names, fully localized title composition, eight distinct places and geographically relevant map pins.
- [ ] Reopen/reload the itinerary; no generic preview route flashes while loading.

### C. Insufficient data and unavailable itinerary

- [ ] First retain a valid current trip, then request Sarajevo October 10–30 at BALANCED pace.
- [ ] LoadingPlan stops loading and displays the localized insufficient-data message; it does not navigate to a fake new plan.
- [ ] Retry responds and remains usable; Edit setup returns to editable dates/destination.
- [ ] Shorten to two days and retry successfully; the previous valid trip remained available before success.
- [ ] With itinerary loading unavailable on a newly mounted Plan screen, verify the existing error/retry UI and zero invented days or pins.

### D. Historical trip and Explore

- [ ] Open an existing historical trip with null identity/planned_fallback data; confirm readability and no crash.
- [ ] Open Explore for Sarajevo and the second city; provider places appear and reload works.
- [ ] Treat TripSetup city-center/station suggestions and Explore starters as the known separate limitations below.

## KNOWN REMAINING PRODUCT ISSUES

- Simulator visual/touch checks above are prepared, not completed.
- Explore starter fallback and TripSetup city-center/station suggestions remain by explicit scope instruction.
- AI/agent actions that formerly invented replacements remain controlled failures; read-only previews may still offer them. No replacement engine was added.
- The existing planner loads at most 30 candidates; long or heavily filtered schedules can legitimately return 422. No ranking, NIGHTLIFE, routing or metadata changes were made.
- Public provider availability and source metadata quality remain external dependencies. Verified here means traceable provider identity, valid geography and sufficient distinct supply.
- Historical trips cannot recover regional disambiguation that was never stored. Existing rows remain readable; new trips retain their original query.
- Production schema validation requires applying additive `V3__retain_trip_destination_query.sql` through the existing migration procedure before deploying these backend changes. Development applied the column via its existing Hibernate update mode. The migration system itself was not redesigned.

## PHASE 11 DEFECTS FOUND

1. **Synthetic mobile Plan fallback:** missing itinerary data generated generic preview stops and offset coordinates. Reproduced by a failing test (two invented days instead of zero).
2. **Same-locality geographic leakage:** the original region-qualified query was discarded and cache lookup accepted any row with the same locality. The second Portland case failed because it received the first Portland's pool.
3. **Test fixture defect:** a shared “verified” fixture helper used Amsterdam coordinates for Paris and Copenhagen, revealed by the corrected geographic cache guard.

## FIXES MADE

- Plan now displays only loaded itinerary days; the generic stop/coordinate helpers were removed and the existing error description was corrected in English and Turkish. No UI redesign.
- Trip retains nullable original destinationQuery; creation and regeneration use it for lookup while retaining the existing display locality and historical fallback behavior.
- Fresh, stale and refreshed cache results are filtered by the existing maximum geographic distance from the resolved destination.
- Added the nullable column migration using the existing SQL convention; no historical backfill or stop migration.
- Corrected geographically invalid test fixtures and added the regression coverage listed above.

## FINAL STATUS

**UNIVERSAL DESTINATION PIPELINE ACCEPTED: YES.** Unseen destinations no longer require manual city seeding; new production itinerary flow no longer fabricates stops. Native simulator acceptance remains explicitly unperformed, with the requested checklist prepared. Phase 11 ends here.
