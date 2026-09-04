# Phase 3 — Personalization Event Foundation Validation

## Phase 3 validation result
PASS for the event-foundation scope. No new production behavior or Phase 4 work.
The approved Phase 2 changes were still uncommitted at the start of this phase.
Phase 3 adds two test files and this report; it does not change those approved files.

## Event invariants verified
New events produced by actual favorite, itinerary and Explore operations are
checked for authenticated ownership, canonical Place ID/name/category,
identityVerified=true, the expected source/context/action, and the exact expected
SHA-256 event key. Deliberately distinct Place and ItineraryStop IDs prevent an
accidental equality from hiding the old identity defect.

## Save lifecycle result
PASS. Save/retry/remove/retry/save creates SAVED, UNSAVED, SAVED, with the second
SAVED assigned a new SavedPlace lifecycle ID. Event keys are distinct. Rollback
after a save, failure inserting feedback, and failure inserting UNSAVED all leave
favorite state and event history consistent.

## Concurrency result
PASS. Existing simultaneous-save coverage and new simultaneous-remove,
save/remove race, simultaneous negative feedback and simultaneous-DONE tests use
separate threads, authenticated contexts and database transactions. A start barrier
releases concurrent callers together. Six save/remove races validate either
legitimate final state and the corresponding event history. Futures must complete
successfully: duplicate-key exceptions cannot be hidden as successful retries.
Per-user database locking and unique keys remain unchanged. These are bounded
concurrency tests on H2, not proof over every PostgreSQL interleaving.

## Itinerary transition result
PASS. DONE and SKIPPED retries, resets to PLANNED, and re-entry do not multiply
the same action for a stop. Same-status DONE retry preserves its completion time.
The current product accepts DONE → SKIPPED and SKIPPED → DONE. Both actions may
therefore exist once for the same stop, while its status reflects the latest
committed transition. This semantic ambiguity is reported for future scoring
design; Phase 3 does not invent terminal-state rules.

## Canonical Place identity result
PASS for visit, skip, explicit add and explicit remove. placeId is Place.id /
stop.placeId; contextId is the stop ID for itinerary events. Removing and adding
again produces a new stop context. Historical null and unknown place IDs support
existing status/removal operations without emitting trusted feedback.

## Invalid / synthetic identity result
PASS. Unknown/fabricated/preview IDs, stop IDs, starter/seed/planned-fallback
providers, missing provider/fetch metadata, invalid provider syntax, missing or
out-of-range coordinates, (0,0), and blank canonical name/city yield no trusted
evidence. Public feedback uses the existing 404 response. Unverified favorite
snapshots remain operable without events, as approved in Phase 2.

## Metadata tampering result
PASS. Null, garbage, changed capitalization and false category/name cannot
override canonical metadata at the feedback service. Existing public @NotBlank
validation rejects null name/category with 400; it does not create an event.
Nonblank tampered HTTP metadata is accepted but canonicalized server-side.

## User isolation result
PASS. User A saves Place X, visits Place Y and gives feedback on Place Z; User B's
history remains independent. B cannot change A's trip or remove A's favorite.
Both users can save the same Place and emit the same explicit action independently.
Identical Explore event keys are permitted across different users. A request
containing another user's userId still records only for the JWT-authenticated user.
New API tests run the real security filter chain, clearing pre-authenticated test
state first. An unauthenticated request is rejected with the current 403 behavior.

## Negative feedback idempotency result
PASS. NOT_INTERESTED, TOO_EXPENSIVE, TOO_FAR and ALREADY_VISITED each deduplicate
by user/place/action even when reason text changes. Different actions remain
separate. All existing weights are asserted unchanged. Public attempts to inject
SAVED, UNSAVED, ADDED_TO_TRIP, REMOVED_FROM_TRIP, VISITED, SKIPPED, REMOVED or
REPLACED are rejected with 400.

## Event key result
PASS. Tests independently calculate the expected hash for generated events.
Different favorite/stop contexts and different explicit actions produce different
keys. Separator-containing, similar-prefix and Unicode canonical IDs round-trip
and deduplicate without collisions in the tested examples. Empty public identity
is rejected; there is no allowed empty production event context.
The encoding uses explicit lengths for source/context, with action appended;
hashing does not rely on an ambiguous separator-only representation.
Database insertion of a duplicate (user_id, event_key) fails with uniqueness
violation, while identical material for different users and multiple historical
null keys are permitted.

## Transaction rollback result
PASS. Real, test-only database constraints cause feedback insert failures during
save, unsave and DONE. No partial favorite, itinerary status or event survives.
An exception after flushing a removal rolls back both the stop deletion and
feedback. A day-summary constraint failure after an attempted add also rolls back
the new stop and feedback. Failures are propagated, never swallowed.

## Historical compatibility result
PASS. Multiple historical rows with null verification/source/context/key remain
readable. REMOVED and REPLACED retain their stored values. ProfileService.me and
ExploreService.places execute against legacy evidence without crashing or
rewriting it. Reading history does not fabricate canonical identity.

## Latest-80 observation
Confirmed unchanged: 80 neutral ADDED_TO_TRIP events from explicit additions across
separate trips displace an older NOT_INTERESTED event from the latest-80 query.
The old event still exists in the database. Input for Phase 8: neutral observations
consume the same window as weighted/negative evidence and may consequently remove
older evidence from current scoring/hiding inputs. No query/limit/weight changes.

## Migration V5 validation
Static review: four nullable columns are additive; historical rows are not updated
or deleted. The action constraint is widened by replacing its existing default-
named check, retaining all old enum values. The unique index has the same
(user_id, event_key) columns/name as the entity's uniqueness declaration.
PostgreSQL's default distinct-null uniqueness permits multiple historical null
keys. V1–V4 and V5 itself are unchanged during Phase 3.

A disposable H2 PostgreSQL-mode migration test starts from a legacy table, runs
the actual V5 SQL twice, verifies old rows remain null/unmodified, accepts every
old/new enum value, rejects an unknown action, permits user-scoped identical keys,
and rejects a same-user duplicate.

PostgreSQL execution: NOT PERFORMED. The project compose file and running
journy-postgres container use the persistent developer volume; no disposable
project PostgreSQL environment was identified. No developer/production data was
touched. H2 compatibility is not claimed as PostgreSQL execution evidence.
V5 still requires manual deployment; no Flyway/Liquibase was added. Existing
non-default action constraint names remain a deployment inspection requirement.

## Public API regression
PASS with JWT authentication and the real filter chain:
- Save and unsave: 200; repeated requests remain successful without extra events.
- Explicit feedback: 200 with canonical metadata; repeats preserve event count.
- Itinerary add: 200; duplicate add retains one stop; unverified ID returns 422.
- Itinerary status: 200; repeated DONE creates no second visit.
- Itinerary removal: 200; retry after removal returns existing 404 behavior.
- Foreign trip add/status/remove: 404 with no event for the caller.
- Malformed JSON, empty required payloads and invalid status enum: 400.
- Unknown feedback Place: 404; injected state actions: 400.
- Missing authentication: existing 403 rejection.
DTOs and response contracts were not redesigned.

## Defects found
No Phase 2 event-foundation corruption defect was demonstrated.

An existing itinerary summary-length limit was exposed: summaryWithAddedPlace
appends text without bounding it to itinerary_days.summary's 500 characters.
Repeated additions to one day (or adding to an already full summary) fail with a
database length error. The original latest-80 fixture exposed this unrelated
limit. A separate regression now explicitly verifies that this failure rolls back
the attempted stop and event. This existing itinerary issue is not an event-
foundation blocker, but it should be addressed in separately authorized itinerary
work.

## Fixes made
No application fixes. The latest-80 test uses separate trips so it measures the
history query rather than the unrelated day-summary capacity. Test-only injected
constraints are scoped to each test user and removed afterward.

## Files changed
Phase 3 only:
- backend/src/test/java/com/journy/backend/feedback/PersonalizationAdversarialIntegrationTest.java
- backend/src/test/java/com/journy/backend/feedback/FeedbackMigrationValidationTest.java
- docs/personalization-phase-3.md

## Tests added
23 adversarial integration tests and one disposable migration compatibility test.
The 16 existing Phase 2 event tests remain unchanged.

## Test matrix
| Requirement | Coverage |
| --- | --- |
| A–C save/unsave/re-save lifecycle | Existing and new full lifecycle tests |
| D concurrent save | Existing separate-thread save test |
| E–F concurrent remove and save/remove race | New barrier-based race tests |
| G–I status retries/reset/re-entry | Existing retries plus both terminal actions and concurrent DONE |
| J–K identity distinction/historical null | Invariant checks, explicit add/remove and historical operations |
| L–M invalid/synthetic/unverified | Existing invalid IDs plus canonical contract corruption cases |
| N metadata tampering | Null/garbage/case variants and HTTP canonicalization |
| O–P isolation/same action across users | Three-place scenario, same keys, foreign operations, JWT user injection |
| Q–R negative retries/multiple actions | Every supported explicit action, changed reasons, unchanged weights |
| S–T keys/encoding | Exact independent hash assertions, context changes, separators/prefixes/Unicode |
| U rollback | Save/unsave/status constraints, removal after flush, add summary constraint |
| V–W history/old actions | Profile/Explore reads and migration compatibility |
| X latest-80 | 80 neutral committed adds displace an older negative event |
| Y public API | Authenticated successful/repeated/invalid/foreign/malformed requests |

## Scoring freeze confirmation
All backend/src/main and mobile file hashes match the snapshot taken at Phase 3
start. No production files were added. The already approved Phase 2 working-tree
diff is unchanged. TasteFeedback weights, ProfileMapper, mobile percentages,
Explore formula/weights, category mappings, candidate limits and history queries
are untouched.

## Manual simulator test required: NO
Only tests and documentation were added. There is no mobile or product-visible
behavior change and no production defect fix requiring UI acceptance.

## Known limitations
- PostgreSQL migration/locking behavior was not executed against a disposable
  PostgreSQL instance. H2 concurrency and migration evidence is explicitly bounded.
- The existing 500-character itinerary summary overflow remains outside this scope.
- DONE and SKIPPED can each contribute once for the same stop under current rules.
- Neutral events consume the existing latest-80 window.
- Approved lifecycle idempotency cannot distinguish a stale save replay after a
  completed unsave from an intentional new save. Explicit negative feedback has
  no reset/revision flow.

## Test results
- Backend compile: `./mvnw -q compile`, exit 0.
- Focused suite: `./mvnw -q '-Dtest=Personalization*IntegrationTest,FeedbackMigrationValidationTest' test`,
  40 tests passed, no failures/errors/skips.
- Mockito tests used the previously established permitted test-agent procedure.
- Initial test runs exposed the summary overflow described above; a test-only
  compilation issue and an overly broad injected test constraint were corrected.
  No application change was used to make tests pass.
- `git diff --check`: passed.
- Mobile/TypeScript: not run; no mobile changes.

## Full regression result
`./mvnw -q test`: exit 0.
25 suites, 163 tests, 0 failures, 0 errors, 0 skipped.
Includes existing authentication, itinerary, Explore/provider, profile, destination,
start-area and weather coverage as well as all event tests.

## Ready for Phase 4: YES
No event-foundation validation blocker was found. PostgreSQL execution remains an
explicit deployment-validation limitation, not a claim of completed deployment.
The existing itinerary summary overflow is reported for separately scoped work.
Phase 3 is complete; Phase 4/Search Places has not been started.
