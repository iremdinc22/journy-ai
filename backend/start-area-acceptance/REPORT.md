# Verified trip start-area suggestions — acceptance report

## CURRENT START-SUGGESTION FLOW

Before: TripSetup → Explore For you → discard coordinates/identity → combine names with defaultStartSuggestions, cityStartSuggestions and fallbackStartSuggestions → string chips → startingArea string in Trip. Typing did not perform a place search. Full pre-change analysis is in ANALYSIS.md.

After: TripSetup → /api/start-areas?destination=…&query=… → DestinationResolutionService → existing PlaceProvider / OSM adapter → geographic validation, identity deduplication and deterministic priority → verified chips → server-verified selection → nullable Trip identity fields. The dedicated endpoint never consumes Explore starter data.

## ROOT CAUSE

Frontend fallback filling treated static names and destination + city center/main station/old town/museum area as geographic options. Valid provider identities were reduced to strings. There was no dedicated search or persistence contract. The destination picker also dropped the disambiguating lookup query; an additive response field now preserves that context for the new flow.

## FILES CHANGED

- `backend/src/main/java/com/journy/backend/destination/dto/DestinationResponse.java`
- `backend/src/main/java/com/journy/backend/destination/mapper/DestinationMapper.java`
- `backend/src/main/java/com/journy/backend/destination/service/DestinationService.java`
- `backend/src/main/java/com/journy/backend/explore/provider/OsmOverpassPlaceProvider.java`
- `backend/src/main/java/com/journy/backend/explore/provider/PlaceProvider.java`
- `backend/src/main/java/com/journy/backend/trip/dto/CreateTripRequest.java`
- `backend/src/main/java/com/journy/backend/trip/dto/TripResponse.java`
- `backend/src/main/java/com/journy/backend/trip/mapper/TripMapper.java`
- `backend/src/main/java/com/journy/backend/trip/model/Trip.java`
- `backend/src/main/java/com/journy/backend/trip/service/TripService.java`
- `backend/src/test/java/com/journy/backend/controller/ItineraryControllerIntegrationTest.java`
- `backend/src/test/java/com/journy/backend/controller/ProfileControllerIntegrationTest.java`
- `backend/src/test/java/com/journy/backend/destination/DestinationResolutionServiceTest.java`
- `mobile/src/api/journyApi.ts`
- `mobile/src/api/types.ts`
- `mobile/src/i18n/translations.ts`
- `mobile/src/screens/TripSetupScreen.tsx`
- `backend/src/main/java/com/journy/backend/startarea/StartAreaController.java`
- `backend/src/main/java/com/journy/backend/startarea/StartAreaSuggestion.java`
- `backend/src/main/java/com/journy/backend/startarea/StartAreaSuggestionService.java`
- `backend/src/main/resources/db/migration/V4__add_verified_trip_start_area.sql`
- `backend/src/test/java/com/journy/backend/startarea/StartAreaIntegrationTest.java`
- `backend/src/test/java/com/journy/backend/startarea/StartAreaSuggestionServiceTest.java`
- `mobile/src/utils/startAreaSuggestions.ts`
- `mobile/tests/startAreaSuggestions.test.cjs`

Reports, test logs, sanitized live payloads and the development-only smoke runner are under backend/start-area-acceptance/.

## PROVIDER / SEARCH STRATEGY

No new external provider. PlaceProvider adds a default-empty searchStartAreas operation, implemented by the existing OsmOverpassPlaceProvider with its existing HTTP transport, address failover and bounding-box helper. Itinerary search(), queries, categories and parsing remain unchanged.

Default discovery requests named railway stations, bus stations, neighborhoods/quarters/suburbs/boroughs, squares and explicit attraction/museum/monument/castle tags. Nodes, ways and relations are supported. Name search is escaped as a literal regex and JSON-encoded before bounded Overpass lookup. A second normalized-name check rejects unrelated search results even if the provider ignores the name filter. No LLM-generated names or fake ratings are used.

At default configuration the search and validation radius is 9 km. Up to 60 provider elements are considered; at most six suggestion chips are returned. A bounded in-memory cache holds up to 256 entries keyed by resolved provider identity, exact latitude/longitude and search text. Valid results expire after five minutes; empty/error results after 15 seconds. This is separate from the itinerary Place cache and does not seed Place rows.

## SUGGESTION MODEL

`StartAreaSuggestion { id, name, type, latitude, longitude, source, providerPlaceId }`. Coordinates are nullable in the input model so missing values can be rejected; every returned suggestion has valid coordinates. Types come from explicit OSM tags: transit_station, neighborhood, district, square, landmark, or null when uncertain. A named hotel is not relabelled as a hotel-heavy area. IDs are stable from provider identity; missing provider IDs use normalized name and rounded coordinates.

## GEOGRAPHIC VALIDATION STRATEGY

Reject missing/blank names, invalid or missing coordinates, non-finite values, out-of-range latitude/longitude, 0,0, synthetic seed/starter sources, and candidates more than 9 km from the resolved destination. The adapter validates its requested radius too. Distance uses the same great-circle calculation pattern as the existing pipeline. Cache keys include geography, so identical locality labels cannot share pools.

This establishes proximity, not administrative-boundary membership: nearby suburban stations can appear within the radius. No fabricated coordinate substitution occurs.

## RANKING / PRIORITY STRATEGY

Exact user-search name matches first; then transit hubs, neighborhoods/districts, squares, landmarks, other verified results. Ties prefer distance, stronger provider identity, name and stable ID. The selected verified option stays first in the UI. No popularity data is invented.

## DEDUPLICATION STRATEGY

Primary key: source/provider + providerPlaceId. Fallback: source/provider + normalized name + coordinates rounded to four decimal places. Distinct identities remain separate internally. The live Ljubljana check found adjacent transit records with identical labels; the display list now keeps only the highest-priority/nearest result per normalized name and type. It does not merge or rewrite provider identities. A regression test covers this case.

## SYNTHETIC FALLBACKS REMOVED

Removed the frontend defaultStartSuggestions, cityStartSuggestions, fallbackStartSuggestions, name-only Explore suggestion extraction, and their template-filling helpers. No Edirne/Sarajevo/Ljubljana city-center, main-station or old-town labels are generated. A provider-returned exact name remains eligible; for example OSM names actual stations simply “Edirne” and “Ljubljana.”

Every remaining production match for city center/main station/old town/hotel area is listed in text-audit.txt. Remaining matches are instructional setup copy; legacy/localized text; destination description/image metadata; historical or Explore seed descriptions; and existing Explore/Place-detail address fallbacks. Those unrelated paths were intentionally preserved. None feeds the new start-area endpoint or chip list.

## NO-SUGGESTION BEHAVIOR

Backend returns []. The frontend adds no local chips, retains the search field, and allows creation without a selected starting area. A previously selected verified option can remain visible; it is not synthetic filler. Empty input causes no start-area verification request during creation. Provider failures cannot produce invented suggestions.

## USER SEARCH BEHAVIOR

400 ms debounce, destination-scoped requests, stale-response cleanup and clearing of old chips/selection on destination changes. Editing the search text clears its resolved selection. Unselected typed text is not submitted or treated as a resolved start in the planning preview. English/Turkish helper text explains that the user may select a match or continue without a start.

Live Baščaršija/Sarajevo returned local matches. Live Shinjuku/Edirne returned none. A tampered or cross-destination selected identity is not trusted: TripService retrieves the canonical candidate from the server discovery pool before saving, ignoring client-provided coordinates. Legacy clients submitting new string-only start text must obtain an exact normalized provider-name match or receive HTTP 400; they may leave the optional field empty.

## UNSEEN DESTINATION TEST RESULT

PASS in isolated H2/local-HTTP tests. No start-area rows or provider-backed Place rows are seeded for area discovery. The actual resolver, Nominatim adapter, OSM adapter, service and API run against local fixture HTTP responses. Discovery is independent of the supported/seeded city lists. Live tests are kept out of CI.

## LJUBLJANA RESULT

PASS automated and live/API. First discovery required no manual seeding. Final live output has six distinct chip labels with provider identities and coordinates; selection persisted exactly and trip creation succeeded. The initial repeated-label observation was fixed and retested live.

## SECOND UNSEEN DESTINATION RESULT

Ghent: deterministic discovery PASS, including real-named fixture candidates and no seeded area data. Live resolution returned Gent, Belgium. Overpass returned HTTP 429 during start-area discovery, so Journy returned [] and then reused that short-lived empty cache. Synthetic count stayed zero and a trip without a start area still succeeded. This is a graceful no-data result, not a claim of successful live candidate discovery.

## SAME-NAME DESTINATION RESULT

PASS for Portland Maine and Portland Oregon in the same isolated context, both resolving to locality Portland with different coordinates/provider identities. Their candidate pools remain separate. An additional API test asserts that selecting the destination result retains lookupQuery=Portland Oregon before requesting its starting areas. No Portland production special case was introduced.

## SCHEMA CHANGES

New additive migration: `backend/src/main/resources/db/migration/V4__add_verified_trip_start_area.sql`. Adds nullable starting_area_id, starting_area_type, starting_area_latitude, starting_area_longitude, starting_area_source and starting_area_provider_place_id. Existing starting_area remains the name/string column. Old migrations are unchanged; no drop, backfill or historical rewrite. Development used its existing Hibernate update mode; production must apply V4 through the existing migration procedure before deploying.

CreateTripRequest and TripResponse gain optional startingAreaSelection. DestinationResponse gains optional lookupQuery for the picker handoff. These are additive API fields.

## BACKWARD COMPATIBILITY

Existing string-only trips remain readable and map startingAreaSelection to null. Historical trips are not revalidated on read. New selected values store canonical name and identity; requests without a start stay optional. The existing itinerary verification, Place cache architecture, title generator, insufficient-data behavior, optimizer and Explore fallback are unchanged. The two unrelated controller test drafts now leave the optional starting area blank rather than relying on unverified station text.

## TESTS ADDED

- Nine integration cases: six destination fixtures (Edirne, Sarajevo, Ljubljana, Ghent and both Portlands), scoped/empty/failure/search handling, identity persistence/tampering/history, and the destination lookup-query handoff.
- Four service tests: fallback/provider identity deduplication and caching, same-label distinct-identity display suppression, invalid/distant/seed rejection, and provider failure/unresolvable/optional behavior.
- Four mobile tests: empty list/no required start, exact selection identity, invalid-coordinate exclusion, and actual screen handlers clearing previous destination/query selection. The latter also guards against the removed synthetic helper families.

## TEST RESULTS

**108 backend tests passed; 0 failures, 0 errors, 0 skipped. 10 mobile tests passed; 0 failures. Compile PASS. TypeScript PASS. git diff --check PASS.**

Commands: `./mvnw -q -DskipTests compile`; `./mvnw -q -Djourny.places.osm.enabled=false -Djourny.destinations.nominatim.enabled=false test`; `node --test tests/*.test.cjs`; `npx tsc --noEmit`. The dedicated integration context overrides the remote endpoints with loopback HTTP fixtures. The established elevated Mockito/local-HTTP test environment was used.

Full logs and machine-readable totals are in this directory. The initial focused run exposed a test-constructor compile mismatch after adding the service dependency; the test wiring was updated. No failing test was disabled. The complete preexisting itinerary suite passed.

## LIVE SMOKE RESULTS

Development-only run on 2026-09-04, Europe/Istanbul. Source: OpenStreetMap via Overpass. The first request used provider discovery; the immediate repeat used the separate start-area cache. Each successful trip selected the first suggestion, or omitted the start when none existed. Trips used October 10–11, RELAXED pace, BALANCED budget, and existing verified itinerary generation.

| Input → resolved locality | Raw elements | Accepted pool | Displayed | Max displayed distance | Trip HTTP | Synthetic |
|---|---:|---:|---:|---:|---:|---:|
| Edirne → Edirne | 48 | 46 | 6 | 7.237 km | 200 | 0 |
| Sarajevo → Sarajevo | 60 | 58 | 6 | 1.159 km | 200 | 0 |
| Ljubljana → Ljubljana | 60 | 60 | 6 | 8.894 km | 200 | 0 |
| Ghent → Gent | Unavailable (429) | 0 | 0 | N/A | 200 | 0 |

Query path: `/api/start-areas` → real geographic resolution → named-element Overpass bounding box → validation/dedup/priority → at most six chips. `provider.log` contains raw counts, resolved centers, cache hits and the Ghent failure. Sanitized response bodies are in `live/`; the final Ljubljana retest is in `live-final/`. `live-summary.json` contains coordinates, types, IDs and distances.

| Destination | Exact name | Type | Latitude | Longitude | Distance |
|---|---|---|---:|---:|---:|
| Edirne | Edirne | transit_station | 41.6552066 | 26.5805143 | 3.19 km |
| Edirne | Edirne Şehirlerarası Otobüs Terminali | transit_station | 41.6319481 | 26.6184351 | 7.237 km |
| Edirne | Babademirtaş | district | 41.6802064 | 26.5546472 | 0.356 km |
| Edirne | Sabuni | district | 41.6736521 | 26.5571997 | 0.403 km |
| Edirne | Sarıcapaşa | district | 41.6756198 | 26.5609202 | 0.464 km |
| Edirne | Mithat Paşa | district | 41.6757901 | 26.5503475 | 0.468 km |
| Sarajevo | Sarajevo | transit_station | 43.8604591 | 18.3989497 | 1.159 km |
| Sarajevo | Skenderija | district | 43.8551592 | 18.4139241 | 0.237 km |
| Sarajevo | Mejtaš | district | 43.8612082 | 18.4194456 | 0.715 km |
| Sarajevo | Soukbunar | district | 43.8503081 | 18.4145842 | 0.768 km |
| Sarajevo | Ciglane | district | 43.8645049 | 18.408036 | 0.904 km |
| Sarajevo | Bjelave | district | 43.8637854 | 18.4195229 | 0.93 km |
| Ljubljana | Ljubljana | transit_station | 46.0582538 | 14.5105343 | 0.956 km |
| Ljubljana | Ljubljana Zalog | transit_station | 46.0595047 | 14.6148983 | 8.398 km |
| Ljubljana | Škofljica | transit_station | 45.9843722 | 14.5727108 | 8.894 km |
| Ljubljana | Trnovo | district | 46.041719 | 14.5038838 | 0.953 km |
| Ljubljana | Rožna dolina | neighborhood | 46.0496313 | 14.4844298 | 1.737 km |
| Ljubljana | Rakovnik | neighborhood | 46.0371062 | 14.5220167 | 1.849 km |

## MANUAL RETEST CHECKLIST

**NOT PERFORMED.** No iOS simulator was booted when checked, and available UI tools do not support native simulator interaction. Automated mobile logic tests and live API checks are not manual screen/touch acceptance.

Use the latest mobile bundle against the running development backend on port 8080. Start Expo from mobile if needed, open the app in the simulator, sign in and select Turkish or English.

### Edirne

- [ ] Open TripSetup and select Edirne. Verify no fabricated “Edirne city center/main station/old town” chips.
- [ ] Confirm the displayed chips match provider names (or are absent if discovery is unavailable). A station genuinely named “Edirne” is allowed.
- [ ] Select a suggestion and create a one-day relaxed trip. Confirm creation succeeds and the start-area selection is retained.

### Sarajevo

- [ ] Select Sarajevo; confirm the previous city’s chips and selected start clear.
- [ ] Search Baščaršija, select a returned local match, and create a trip. Confirm existing itinerary names/titles/maps still load normally.

### Ljubljana (unseen destination)

- [ ] Search/select Ljubljana without adding seeds. Verify real provider-backed options or no options, never template chips.
- [ ] Check there are no repeated name/type chip labels; select one and create a trip.
- [ ] Type a different area after selection; confirm the old selection clears and only new API matches appear.

### Zero-result / mismatch state

- [ ] In Edirne, search Shinjuku or a deliberately nonexistent local name. Confirm no fake chips appear and the search field stays usable.
- [ ] Continue without choosing a result. Trip creation remains allowed, and the typed search text is not saved as a start area.
- [ ] Check helper copy and no crashes/loading hangs on an empty/error result.

### Identity/history

- [ ] Reopen an older string-only trip; it must remain readable.
- [ ] Check both Portland destinations through qualified search and confirm chips stay geographically specific.

## KNOWN LIMITATIONS

- Required native simulator visual/touch acceptance remains unperformed; this is the final acceptance blocker.
- Overpass availability and quota can yield empty suggestions. Ghent demonstrated HTTP 429 safely. Empty results expire after 15 seconds; no retry/backoff architecture was added.
- The radius is a proximity rule, not a city-boundary polygon. Suburban transit hubs within 9 km may outrank closer districts, following the requested type priority.
- The provider scan is bounded and is not an exhaustive search engine. Typed lookup searches name tags, not arbitrary address strings or multilingual aliases. Unknown types remain null.
- A selection whose cache expired may need provider revalidation; if no reliable match can be recovered, creation with that selected start returns a clear 400 and the optional field can be cleared.
- This task persists the start location but does not implement route re-anchoring or change the existing optimizer.
- V4 must be applied before production deployment; no deployment or commit was performed.

## FINAL STATUS

**START AREA SUGGESTION PIPELINE ACCEPTED: NO — pending the required simulator retest.**

Implementation, deterministic tests and live API checks are complete. New destinations receive verified start-area options or an empty list, do not receive fabricated city-center/main-station/old-town suggestions, and do not require manual seeding. The exact remaining blocker is unperformed native simulator UI/selection/creation acceptance, not a failing automated test. No other feature was started.
