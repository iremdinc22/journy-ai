# Phase 4 acceptance bugfix — Search Places empty/error state

## ROOT CAUSE

Backend zero-result behavior was correct. A successful OSM search with zero elements
returns an empty provider list, PlaceProviderService returns an empty list,
PlaceSearchService maps it unchanged, and Spring returns HTTP 200 with `[]`.

The mobile Search API did not use a search-specific timeout. It inherited the
shared client's 3.5-second timeout, while OSM name discovery can execute two bounded
radius requests and legitimately take up to about 46 seconds. The mobile request
was aborted and recorded as an error before the backend completed with `200 []`.
The client's existing base-URL fallback could issue another request after the
abort, explaining repeated zero-element logs and `shared_discovery`.

The old error was therefore not being replaced by the late successful response:
that response belonged to an already-aborted fetch and never reached ExploreScreen.
A subsequent slow successful-empty retry could suffer the same client timeout.

## BACKEND ZERO-RESULT CONTRACT

Unchanged and now explicit in tests:

- Successful provider response with zero elements: HTTP 200, JSON `[]`.
- Provider timeout/failure with no matching canonical cache: HTTP 503.
- No Place is persisted and no synthetic/preview card is returned in either case.

The provider strategy, cache fallback, shared in-flight discovery, canonical
identity, destination isolation, ranking, aliases and persistence were not changed.

## MOBILE STATE ROOT CAUSE

`exploreApi.search` called `apiRequest` without `timeoutMs`, selecting the client's
3,500 ms general timeout. ExploreScreen correctly treats rejected requests as
errors, so the premature client abort produced the error card even though the
server later logged a successful empty search.

The existing request-version checks were correct and remain intact. Focused tests
also prove a stale failed request cannot overwrite a newer successful response.

## FIX

- Search requests now use a 60,000 ms timeout, above the backend provider's bounded
  two-radius worst-case window. No retry/backoff was added.
- A current successful response explicitly calls `setError(false)` before committing
  its results. This makes the successful state transition explicit for both `[]`
  and nonempty results.
- Empty copy is exactly `No places found` / `Sonuç bulunamadı`.
- Existing 503 copy remains `Search could not be completed` / `Arama tamamlanamadı`
  and `Please try again.` / `Lütfen tekrar dene.`
- Search mode still uses `[]` rather than starter preview data, so empty searches
  cannot render synthetic cards.

## FILES CHANGED

Relative to the start of this final acceptance fix:

- `mobile/src/api/journyApi.ts`
- `mobile/src/screens/ExploreScreen.tsx`
- `mobile/src/i18n/translations.ts`
- `mobile/tests/placeSearch.test.cjs`
- `backend/src/test/java/com/journy/backend/explore/search/PlaceSearchIntegrationTest.java`
- `docs/search-places-empty-state-bugfix.md`

No backend production file changed in this fix.

## TESTS ADDED

Backend Search Places coverage now has separate cases for:

1. Provider success + zero elements → HTTP 200 `[]`, two-radius completion, no Place.
2. Provider timeout → HTTP 503, no Place.

Eight mobile checks were added for:

1. `200 []` → empty state and no preview fallback.
2. 503 → error state.
3. 503 → retry → `200 []` clears error.
4. 503 → retry → results clears error and renders results.
5. `[]` → new query results render normally.
6. Stale failed request cannot overwrite newer `[]` success.
7. Search API uses 60-second timeout without retry logic.
8. Exact Turkish and English empty-state copy.

## TEST RESULTS

- Focused backend Search Places: 26/26 passed.
- Focused mobile Search Places: 13/13 passed after correcting one test-only
  cross-VM array assertion; no application change was needed for that assertion.
- TypeScript: `npx tsc --noEmit` passed.
- `git diff --check` passed.

## FULL REGRESSION

- Backend: 27 suites, 189 tests, 0 failures, 0 errors, 0 skipped.
- Mobile: 31 tests, 0 failures, 0 skipped.
- No simulator acceptance is claimed for this fix.

## MANUAL RETEST STEPS

1. Restart/reload the Expo bundle so the updated Search API timeout is active.
2. With Edirne selected, search `Eiffel Tower` while the provider is responding.
3. Wait for the backend's zero-element radius searches to finish. Confirm the UI
   displays only `Sonuç bulunamadı`, without the error/retry card or preview cards.
4. During a real provider timeout, confirm the existing `Arama tamamlanamadı` and
   `Lütfen tekrar dene.` card remains.
5. After that failure, restore provider availability and retry `Eiffel Tower`.
   Confirm the error clears and `Sonuç bulunamadı` appears.
6. After a failure or empty result, search `Selimiye`. Confirm the error/empty state
   clears and canonical Selimiye results render.
7. Start a slow/failing search, then submit a newer successful search. Confirm the
   older failure never replaces the newer results.

## READY FOR PHASE 4 FINAL MANUAL ACCEPTANCE: YES

The reported response/UI-state defect is fixed and automated validation is green.

## READY FOR PHASE 5: NO

Phase 5 was not started. Await final Phase 4 manual acceptance.
