# Weather positive-flow integration fixes

## Bug 1 root cause

`tripApi.applyWeatherAdjustment` supplied a pre-serialized string, while `apiRequest` serialized every supplied body. The resulting top-level JSON string could not bind to ApplyWeatherRequest. The shared client already expected objects; all other API body-bearing callers conformed. Its refresh-token helper uses raw fetch and correctly serializes once independently. Session storage serialization is unrelated.

## Bug 1 fix / API serialization contract

Weather Apply now passes `body: { previewId }`. The shared serializer remains unchanged. `RequestOptions.body` is narrowed from `unknown` to `object`, catching accidental string bodies at compile time. No other endpoints or network behavior were rewritten.

Focused tests execute the real `journyApi.ts` and `client.ts`, inspecting the outgoing fetch body: `{"previewId":"abc"}`, with an object as its parsed value. They verify that the accepted response is returned and representative login/agent messages preserve their body shapes.

## Bug 2 root cause / analysis

`ItineraryStop.timeWindow` persists the scheduled slot. The stop response copies it unchanged. Previously, ItineraryMapper read only the first stop's slot, then unconditionally derived every later start using visit duration, travel and buffer. The implementation had no legacy-only guard; no evidence established that intentionally replacing explicit later slots was a product rule.

Plan and Day Detail both prefer `day.timeline`; their legacy UI fallbacks use `stop.timeWindow`. Detail map labels already used the stored field, explaining the visual disagreement. Repository search found no backend consumer of the response's derived timeline timestamps and no existing test requiring explicit slots to be overwritten.

## Authoritative time rule / Bug 2 fix

For each stop, a valid persisted `H:mm`/`HH:mm` slot (hours 0–23, minutes 0–59) determines the timeline start. Only missing or unusable legacy slots use the existing cursor fallback. The mapper does not mutate persistence or contain weather-specific conditions.

Travel rows, their estimates, durations and opening-hour checks remain. Subsequent fallback timing derives from the actual preceding start. Bounds validation prevents malformed legacy values such as 24:00 or 16:75 being treated as explicit valid slots.

## Legacy time behavior / Plan–Day Detail consistency

Missing/empty/non-time/out-of-range values remain readable through the established pace/duration/travel fallback. Explicit later slots remain authoritative even after a legacy first stop. Both screens consume the corrected server timeline; no production screen redesign was needed. Reopen Day Detail from the updated Plan after applying to inspect the updated day response.

## Files changed in this fix

Production:

- `mobile/src/api/journyApi.ts` — structured weather Apply body.
- `mobile/src/api/client.ts` — object-body type contract; serialization behavior unchanged.
- `backend/src/main/java/com/journy/backend/itinerary/mapper/ItineraryMapper.java` — explicit start for each stop and valid clock bounds.

Tests/dev/evidence:

- `backend/src/test/java/com/journy/backend/itinerary/ItineraryMapperTimingTest.java`
- `mobile/tests/weatherSerialization.test.cjs`
- `mobile/dev/weather-simulator/index.tsx` — register the existing Day Detail screen in the dev harness.
- `mobile/dev/weather-simulator/verify-apply.cjs` — execute the real Plan handler and shared transport against the isolated fixture.
- `backend/weather-acceptance/verify_simulator.py` — retain malformed-body negative control; assert no timeline discrepancies.
- `backend/weather-acceptance/INTEGRATION_FIXTURE_RESULT.json`
- `backend/weather-acceptance/POSITIVE_SIMULATOR.md` and this report.

Other uncommitted weather/fixture work predates this fix. Weather provider, evaluator, adjustment service, thresholds, fixture observations/identities, generator and protected pipelines were not changed in this task.

## Tests added / results

| Check | Result |
|---|---|
| Backend compile | PASS |
| Full backend suite | 123 tests; 0 failures, 0 errors, 0 skipped |
| Mobile suite | 18 tests; all pass |
| TypeScript | PASS |
| Python weather-agent suite | 2 tests; all pass |
| iOS dev fixture bundle | PASS, including Day Detail |
| Actual Plan handler + shared API client against fixture | PASS |
| git diff --check | PASS |

New backend tests cover exact explicit slots despite derived 15:55, retained travel information, a persisted order/time swap with identity checks, and missing/invalid historical slots. New mobile tests cover single serialization, successful response handling, representative caller compatibility, and both screens' actual timeline selection helpers.

## Fixture regression result

Reused the existing launcher, in-memory database, recorded OSM places and unchanged synthetic forecast. An isolated instance on port 8082 avoided interrupting the normal backend.

The integration verifier extracts and executes the production Plan Apply handler, imports the production API wrapper/client, and uses real HTTP. Its only network substitution is localhost port 8080 → 8082; it does not repair bodies or mock backend responses.

- Before: memorial 14:00; museum 16:00; `available=true`.
- Preview GET: no itinerary mutation.
- Actual wire body: a JSON object with one previewId; no double serialization.
- Apply: success through the real handler; no 403 or error alert.
- After: museum 14:00; memorial 16:00, in both stored slots and timeline.
- Every other stop field remains exact; no third place; walking estimate remains 3.4 km.
- Refetched weather eligibility is false, which hides the Assistant action on focus through its existing eligibility check.

Full evidence: [INTEGRATION_FIXTURE_RESULT.json](INTEGRATION_FIXTURE_RESULT.json). This runtime handler test is not a claim that a physical simulator button was tapped.

## Manual retest steps

1. Stop the normal backend in its terminal to free port 8080.
2. Terminal A:

   ```bash
   cd /Users/iremdinc/Journy
   bash scripts/weather-simulator.sh
   ```

3. Terminal B:

   ```bash
   cd /Users/iremdinc/Journy/mobile/dev/weather-simulator
   ../../node_modules/.bin/expo start --localhost --port 8083 --clear
   ```

4. Press **i** and open **Journy · DEV Rain Fixture**. It authenticates the disposable account automatically. Note tomorrow's date in the DEV ONLY banner; timezone Europe/Istanbul.
5. Confirm Plan shows the weather card and rain window **14:00–15:00**. Plan's initial slots must be memorial **14:00**, museum **16:00**. Open Day Detail and confirm those same times; return.
6. Open Preview. Confirm memorial **14:00 → 16:00**, museum **16:00 → 14:00**. Toggle preview without applying; the itinerary stays unchanged. Both counts are 2 and both walking estimates are 3.4 km.
7. Open Assistant before Apply; confirm its weather action appears. Return to Plan.
8. Tap the actual **Apply** button. Expect success, no 403/error alert, and no new place. Museum becomes first at **14:00**, memorial second at **16:00**. The weather card disappears.
9. Reopen Day Detail: the same two times and real places must match Plan. Return to Assistant: its weather action disappears after refetch.
10. Optionally inspect stored identities with `python3 backend/weather-acceptance/verify_simulator.py` (read-only) and compare to the documented OSM IDs/coordinates. Do not run its `--apply` option before testing the mobile button.

Reset by restarting terminal A, then pressing **r** in terminal B. This discards the fixture database only. Full scenario details and provenance: [POSITIVE_SIMULATOR.md](POSITIVE_SIMULATOR.md).

## Known limitations

- Native simulator interaction was not performed in this session. The real button's visual confirmation, Day Detail appearance and Assistant refresh still need the manual checklist; the actual handler/HTTP path and screen timing selectors are verified automatically.
- Explicit slots can reveal overlaps with estimated visit/travel durations. The fix intentionally displays the stored schedule without silently moving it or introducing route reoptimization. Travel estimates remain separate information.

## Final status

**WEATHER POSITIVE UI FLOW READY FOR MANUAL ACCEPTANCE: YES.**

Both identified integration blockers are fixed; required regressions and the production-handler fixture flow pass. Final native simulator acceptance is pending the visual retest above. No new weather feature or unrelated redesign was started.
