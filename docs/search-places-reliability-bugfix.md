# Phase 4 acceptance bugfix — Search Places runtime reliability

## Root cause
Manual log: Overpass GatewayTimeout → provider failure → 503. This establishes an
upstream failure, not that a usable cached result was discarded. The first request
may still return 503 when no verified matching cache exists, as permitted by the
requested contract.

Inspection and pre-fix tests confirmed that warm matching cache was already
returned before discovery, and cache was already re-read after a provider failure.
12 of the initial 13 regression cases passed without an application change.

A separate, directly demonstrated reliability defect was found in the same
runtime path: simultaneous identical cold searches each called the provider and
then concurrently inserted the same canonical Place. The pre-fix API test failed
with a Place primary-key violation for osm_relation_3376582. There is no evidence
that this concurrent race caused the particular manual 504; it is an additional
verified defect, not an inferred explanation of that log.

## Current failure path
Authenticated endpoint → PlaceSearchService input checks → existing destination
resolution → verified/matching geographic cache lookup → query-aware discovery
when empty → canonical upsert → cache re-read → unchanged search ordering/DTO.
Provider failure with no verified matching result maps to 503. Unrelated cache
does not convert that failure into success.

## Fix implemented
Only PlaceProviderService production code changed.
- Keep cache-first return and post-failure matching-cache fallback.
- Recheck matching cache after winning the discovery gate.
- Share one in-flight discovery between identical concurrent searches.
- Key includes normalized locality, resolved latitude/longitude, normalized query
  object and request limit. Geographically distinct destinations cannot share it.
- Remove the in-flight entry on success or failure; it is not a persistent cache.
- Preserve original 503 semantics for waiting callers. A later explicit retry can
  start fresh discovery.
- Add cache_hit/shared_discovery/cache_fallback logs without logging search text.

No retry/backoff loop, new storage, schema change or provider transport change was
introduced. The existing Place repository remains the only result cache.

## Cache fallback rule
A usable result must be persisted, pass PlannerPlaceContract, belong to the resolved
locality and geographic boundary, and match the existing query/type predicate.
Exact/prefix/substring name behavior and deterministic ranking are unchanged.
Stale but still verified matching records remain usable, as in Phase 4.
No arbitrary same-city Place qualifies just because the provider failed.

Name/category/coordinates and exact Place.id are preserved. Museum/gallery/park
cache matching still requires the existing relevant name or search-type metadata;
a generic CULTURE row with no matching type evidence is not assumed to be a museum.
No taxonomy or type predicate was changed.

## Provider call rule
Any nonempty verified matching cache remains sufficient under the existing Phase 4
contract. Return it without an OSM call. On a miss, concurrent identical requests
share the current discovery result/failure within this backend instance. After a
failure, cache is re-read before deciding between verified results and 503.
Provider success still uses the existing canonical upsert path.

The mobile explicit submit/clear flow is unchanged. Repeated retry taps or multiple
clients can reach the backend concurrently; these now share identical in-flight
work. No new mobile request/retry mechanism was added.

## Files changed
Relative to the start of this acceptance bugfix:
- backend/src/main/java/com/journy/backend/explore/provider/PlaceProviderService.java
- backend/src/test/java/com/journy/backend/explore/search/PlaceSearchReliabilityIntegrationTest.java
- backend/src/test/java/com/journy/backend/explore/search/SearchCacheLiveSmoke.java
- docs/search-places-reliability-bugfix.md

The earlier Phase 4 implementation was still present as uncommitted changes.
A start-of-bugfix hash snapshot distinguishes it from this patch: all other
backend/src/main and mobile files match their starting hashes.

## Tests added
14 integration cases using authenticated API requests and isolated H2:
1. Stale verified Selimiye cache with an unavailable provider: 200, exact identity,
   name, category and coordinates; zero provider calls.
2. Empty cache + GatewayTimeout: 503, no persisted/fabricated result.
3. Unrelated Edirne cafe + Selimiye query + timeout: 503.
4. Matching canonical cache committed during failing discovery: 200 fallback.
5. Failure → successful recovery → canonical cache reuse; two provider calls total.
6–7. Edirne cache cannot leak to Tallinn or Sarajevo.
8. Same city label with a geographically distinct resolved center cannot leak.
9–10. Verified coffee/museum cache works with unavailable provider and zero calls.
11. Starter/seed/planned-fallback/unverified matches cannot become fallback.
12. Exact/prefix/substring/name/ID ordering remains deterministic.
13. Identical concurrent successes share one discovery and avoid duplicate insert.
14. Identical concurrent failures preserve 503; later retry recovers.

The opt-in live smoke launcher uses an ephemeral H2 database, random loopback
ports and a local proxy. It fetches real OSM Selimiye records first; only then may
the proxy inject 504 and restore those exact previously fetched canonical records
to emulate a concurrent cache commit. No fabricated places or normal database
writes are used.

## Test results
- Pre-fix: 13 cases, 12 passed; concurrent duplicate Place insert failed.
- Post-fix compile + focused search/provider/destination/personalization selection:
  115 tests passed, no failures/errors/skips.
- New reliability class: 14 passed.
- git diff --check passed.

## Full regression result
./mvnw -q test: exit 0.
27 suites, 188 tests, 0 failures, 0 errors, 0 skipped.
Includes existing Search Places, provider/OSM, destination, Explore,
personalization event, auth, itinerary, weather and start-area regressions.

## Destination isolation result
PASS. Locality and existing distance filter are retained on every cache read.
Tallinn/Sarajevo cannot receive Edirne records, even if the query names match.
The same locality label with distant coordinates is also rejected.

## Synthetic fallback audit
Search controller/service/provider path still returns canonical verified matches,
[] for a successful no-match discovery, or an explicit error.
No starter, preview, city-center, generic-museum or generic-restaurant generator
was added or called. Search ranking, aliases and filtering code are unchanged.
No TasteFeedback/SEARCHED/SEARCH_RESULT_OPENED/VIEWED writes were added.

## Mobile changes
None in this bugfix. Existing 503 → error UI and 200 → result UI remain.
Mobile tests/TypeScript were not rerun because mobile files are unchanged.
No simulator was used and no manual simulator acceptance is claimed.

## Known limitations
- An uncached first request can still fail with 503 when Overpass is unavailable.
  This patch cannot manufacture verified data or guarantee upstream availability.
- In-flight sharing is local to one backend instance and one exact request key.
  It is not distributed coordination or serialization of all overlapping queries.
- Current destination resolution and museum type-evidence requirements remain.
- Provider-success empty responses retain the existing empty-result behavior.

## Live/API retest result
PASS on the final controlled smoke run (exit 0), using live OSM followed by local
test-only fault injection:
- Live provider returned canonical Selimiye Camii, Place.id osm_relation_3376582,
  providerPlaceId relation/3376582; latitude 41.6780926, longitude 26.5592026.
- A matching cache commit during injected HTTP 504 returned HTTP 200, with the
  exact same serialized canonical results.
- A warm-cache request while the test provider remained unavailable returned 200
  with zero additional provider calls.
- Unrelated cache and empty cache each returned 503; the empty database remained
  empty, with no fabricated result.

Earlier smoke attempts encountered a test-proxy connection issue and a real
upstream 504; neither was counted as success. The proxy was adjusted to use the
application's existing Apache transport, without any production transport change.
The final retry used the same public provider after waiting. No automatic retry
logic was added to the application.

Evidence: docs/search-cache-live-smoke.json (additional report artifact).
No production/developer database or simulator was used.

## Ready for Phase 4 manual retest: YES
Restart the backend with this patch before retesting.
After one successful Selimiye discovery, repeat the query: logs should show
place_search cache_hit and no OSM request for that matching-cache path.
An uncached query may still report the existing error UI during an upstream outage;
this is explicitly permitted by the acceptance contract.

## Ready for Phase 5: NO
Phase 5 has not started. Await the user's Phase 4 manual retest.
