# Positive rain simulator scenario

Prepared as a **development-only fixture**. The two integration bugs are now fixed: weather Apply follows the existing object-body contract, and explicit stored timeline slots are authoritative. Weather thresholds, eligibility rules, fixture forecast, normal Expo entry point and normal database remain unchanged. See [integration fix report](INTEGRATION_FIX_REPORT.md).

The existing unit fixture used invented test names. This launcher instead replays two real OpenStreetMap records, also present in the earlier live Edirne itinerary. Weather is explicitly synthetic and local to this disposable process.

## What runs

- `scripts/weather-simulator.sh` explicitly invokes Spring Boot's existing `test-run` goal.
- `backend/src/test/java/com/journy/backend/weather/simulator/WeatherSimulator.java` creates an in-memory H2 database and a local HTTP forecast stub. It forces a dedicated profile and loopback binding; it never connects to the normal PostgreSQL/H2 database. It refuses an occupied port rather than stopping another process.
- The **normal OpenMeteoWeatherProvider, parser, cache, WeatherRiskEvaluator, WeatherAdjustmentService, authenticated controllers and persistence path** run unchanged. The stub returns observations only for this fixture's coordinates and date; all other queries fail unavailable. No `available=true` override exists.
- The existing configured thresholds remain **60% OR 1 mm/hour**. Startup checks that these real decisions yield an eligible preview; it fails if the fixture is no longer eligible.
- `mobile/dev/weather-simulator` is a separate Expo project importing the **unchanged real Plan and Assistant screens and API client**. A visible DEV ONLY banner distinguishes it. It checks the backend fixture marker before logging into the fixture account. It bypasses only normal onboarding navigation, which otherwise forces trip setup and cannot open this preloaded trip directly.
- Fixture classes/resources were checked to be absent from the packaged production JAR. The normal mobile entry point never imports this harness.

## Exact scenario

The trip is **tomorrow relative to launcher startup, in Europe/Istanbul**. The printed date and the yellow simulator banner are authoritative. The verified run used **2026-09-05**.

| Stop | Real identity | Before | Proposed after |
|---|---|---|---|
| Balkan Savaşı Şehitleri Anıtı | OSM node 1801477226; WALKING; 41.6592439, 26.5377829 | 14:00 | 16:00 |
| Edirne Müzesi | OSM node 2181137651; CULTURE; 41.6788940, 26.5607674; `tourism=museum` | 16:00 | 14:00 |

Rain: **14:00–15:00, 90% probability, 2 mm/hour**, code 63. At **16:00: 0%, 0 mm/hour**. All 24 hourly values are supplied for the exact requested trip date. The memorial visit is 60 minutes; the museum visit is 120 minutes. These are explicit scenario scheduling durations, not claims about required real-world visiting times.

The museum is recognized from recorded `tourism=museum`, not a guessed name or an invented indoor tag. Provider IDs, names and coordinates are preserved from the records. The two-stop walking estimate is calculated once from their coordinates and retained after reversal; no savings are advertised.

Recorded provider evidence: [OSM memorial](https://api.openstreetmap.org/api/0.6/node/1801477226.json), [OSM museum](https://api.openstreetmap.org/api/0.6/node/2181137651.json). Snapshot under `backend/src/test/resources/weather-simulator/osm-places.json`, retrieved September 4, 2026. © OpenStreetMap contributors, ODbL 1.0. Runtime does not require internet access to these providers.

## Start the simulator scenario

1. In the terminal running your **normal backend**, stop it with **Ctrl+C** so port 8080 is free. Do not change its configuration or database. You can leave PostgreSQL running. Do not use `scripts/dev.sh` for this fixture.
2. In terminal A:

   ```bash
   cd /Users/iremdinc/Journy
   bash scripts/weather-simulator.sh
   ```

   Wait for `DEV ONLY WEATHER SIMULATOR READY` and note the printed date. This starts the isolated backend on **127.0.0.1:8080**. If the port is occupied, the script explains and stops.
3. In terminal B:

   ```bash
   cd /Users/iremdinc/Journy/mobile/dev/weather-simulator
   ../../node_modules/.bin/expo start --localhost --port 8083 --clear
   ```

4. Press **i** in terminal B to open the iOS simulator. Open the separate **Journy · DEV Rain Fixture** experience, not the normal Journy experience. No dependency installation or normal mobile configuration change is needed with the current workspace dependencies.
5. The harness checks the fixture marker, signs into its disposable account and opens Plan automatically. Expect the yellow **DEV ONLY · Synthetic rain · Real recorded OSM places** banner. If connection fails, wait for terminal A's ready message, then tap **Retry fixture connection**.

No normal login or trip creation is necessary. Disposable credentials, if needed for API inspection: `weather.simulator@example.test` / `WeatherDemo123!`.

## Manual checks and expected results

1. **Plan card:** it should appear because the real backend response has `available=true` and `weatherStatus=AVAILABLE` (the API field is `available`, not literally `weatherAdjustmentAvailable`).
2. **Forecast:** card title should say rain risk on the date printed by the launcher, **14:00–15:00**. The fixture timezone is Europe/Istanbul.
3. **Places:** confirm both names above already exist in the itinerary; no third/replacement place is proposed.
4. **Preview:** tap **Preview changes**. It must list the two exact changes in the table. Toggling the preview must not change either stored stop. The read-only verifier below confirms this independently.
5. **Distance:** before/after counts are both 2 and the walking estimate is identical. There must be no claimed saved kilometres or minutes.
6. **Assistant:** switch to Assistant before applying. Its weather action should appear because it fetches the same backend eligibility for day 1. Asking about weather should describe these existing stops and direct application through Plan; it should not invent another place or saved time.
7. **Apply:** return to Plan and tap Apply explicitly. Expect success without HTTP 403. Museum becomes the first stop at **14:00**, memorial the second at **16:00**. The weather card disappears.
8. **Day Detail:** tap the day card after Apply. Confirm museum **14:00** and memorial **16:00**, matching Plan and the stored slots. Return to Plan, then Assistant; its weather action should disappear after refetch. Stop names, IDs, coordinates and count must stay the same.

## Resolved integration issues

Weather Apply now supplies `{ previewId }` as an object to the shared client, which serializes exactly once. The mapper now honors every valid stored slot rather than deriving later starts. Missing/invalid legacy slots retain derived timing; travel rows remain visible. Both Plan and Day Detail consume this same timeline.

The real production Plan handler and API client have passed a fixture HTTP integration check, including successful Apply and exact before/after timeline slots. This is not a claim of native simulator interaction. The old [POSITIVE_SIMULATOR_API.json](POSITIVE_SIMULATOR_API.json) records historical failures; current evidence is [INTEGRATION_FIXTURE_RESULT.json](INTEGRATION_FIXTURE_RESULT.json).

## API verification and reset

From the repository root, inspect the running fixture without applying:

```bash
python3 backend/weather-acceptance/verify_simulator.py
```

It refuses a non-fixture backend, checks that preview retrieval does not mutate the itinerary, and prints eligibility and exact changes.

For a separate **fixture-only API Apply test** (this changes the disposable trip):

```bash
python3 backend/weather-acceptance/verify_simulator.py --apply
```

It sends a deliberately malformed negative-control request, then a valid object request, checks persistence/identity/no-savings, and asserts zero timeline discrepancies. The current mobile request is tested separately by `mobile/dev/weather-simulator/verify-apply.cjs` against a fresh fixture on port 8082. Run this only after your before/preview checks. Refocus the screens afterwards to fetch updated state.

To reset: **Ctrl+C terminal A**, rerun `bash scripts/weather-simulator.sh`, then press **r** in terminal B to reload the harness and sign into the new in-memory scenario. Restart after a date rollover too. No deletion/reset endpoint is added.

To return to normal: stop both fixture terminals, restart your usual development command, and reopen normal Journy. Sign back into your usual account if needed; never create the fixture in the normal database.

## Verification performed

- Real Plan Apply handler plus shared API client completed a successful request against the disposable backend, without modifying request bodies or responses in the test.
- Preview left the itinerary unchanged; only two stored stops exchanged order/time; all identity fields and walking estimate stayed unchanged.
- Timeline exactly matched stored slots before and after Apply. Eligibility became false afterward.
- Backend compile and all 123 tests pass; all 18 mobile tests and TypeScript pass; both Python weather tests pass.
- The separate iOS harness bundles successfully and now includes the real Day Detail screen.
- No native simulator visual checks were claimed as performed.

**WEATHER POSITIVE UI FLOW READY FOR MANUAL ACCEPTANCE: YES.** Perform the real simulator-button and visual checklist above to complete manual acceptance.
