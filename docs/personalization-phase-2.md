# Personalization Phase 2 — event foundation

## Current event model changes
The existing TasteFeedback entity, repository and service remain the only feedback store.
New rows contain identityVerified=true, source, contextId and eventKey.
No parallel favorite or analytics state is introduced.

## Canonical Place identity contract
New feedback placeId is the ID of a persisted Place that passes the existing
PlannerPlaceContract. Name and category are resolved from that record, never from
request snapshots or itinerary display fields. ItineraryStop.id is only contextId;
ItineraryStop.placeId supplies canonical identity. Verification is evaluated when
the event is written; there is no external provider request during feedback recording.

## Event semantics
| Action | Committed action/context | Weight |
| --- | --- | --- |
| SAVED | New favorite, SavedPlace.id | 3 (unchanged) |
| UNSAVED | Removed favorite, same SavedPlace.id | -2 (previous REMOVED weight) |
| ADDED_TO_TRIP | Explicitly added verified stop, ItineraryStop.id | 0 (scoring deferred) |
| REMOVED_FROM_TRIP | Removed stop, ItineraryStop.id | -2 (previous REMOVED weight) |
| VISITED | Transition to DONE, ItineraryStop.id | 4 (unchanged) |
| SKIPPED | Transition to SKIPPED, ItineraryStop.id | -2 (unchanged) |
| NOT_INTERESTED | Explicit Explore feedback, Place.id | -3 (unchanged) |
| TOO_EXPENSIVE / TOO_FAR | Explicit Explore feedback, Place.id | -2 (unchanged) |
| ALREADY_VISITED | Explicit Explore feedback, Place.id | 4 (unchanged) |

REMOVED and REPLACED remain readable with their original weights. No new producer
writes either. VIEWED/SEARCHED are not added. Automatic itinerary generation is not
instrumented as an explicit user add. Profile and Explore scoring code is unchanged.

## Files changed
Paths below are repository-relative.

- backend/src/main/java/com/journy/backend/feedback/model/TasteFeedback.java
- backend/src/main/java/com/journy/backend/feedback/model/TasteFeedbackAction.java
- backend/src/main/java/com/journy/backend/feedback/repository/TasteFeedbackRepository.java
- backend/src/main/java/com/journy/backend/feedback/service/TasteFeedbackService.java
- backend/src/main/java/com/journy/backend/security/CurrentUserService.java
- backend/src/main/java/com/journy/backend/user/repository/UserAccountRepository.java
- backend/src/main/java/com/journy/backend/savedplace/service/SavedPlaceService.java
- backend/src/main/java/com/journy/backend/itinerary/service/ItineraryService.java
- backend/src/main/resources/db/migration/V5__feedback_event_identity.sql
- backend/src/test/java/com/journy/backend/feedback/PersonalizationEventIntegrationTest.java
- docs/personalization-phase-2.md

## Schema changes
Four nullable columns: identity_verified boolean; source, context_id, event_key
varchar(255). Unique index/constraint on (user_id, event_key).
Null event keys permit historical rows to coexist without deduplication or rewriting.
The action check permits the three additive enum values and all old values.

## Migration notes
V5 is a new manual PostgreSQL migration; old migrations are unchanged. No
Flyway/Liquibase runner exists. Development uses Hibernate ddl-auto=update;
production uses validate, which does not apply migrations. Before deployment,
apply V5 transactionally using the normal database deployment process, then deploy
the backend. Inspect any manually named action constraints in the deployed schema:
V5 expands the Hibernate-default taste_feedback_action_check. Hibernate update
must not be assumed to expand an existing enum check; apply V5 to existing
development databases too. No production database was changed by this task.

## Save / unsave behavior
SavedPlaceService still owns favorites. It locks the authenticated user's row
before checking state, then saves/deletes and records feedback in the same
transaction. Repeat save/remove calls emit nothing. Saving again after unsaving
creates a new favorite lifecycle and can create a new SAVED event. Existing
unverified snapshot favorites remain supported, but neither save nor removal
creates trusted evidence for them.

## Itinerary event behavior
Only explicit add, remove and status flows are instrumented. Canonical identity is
resolved from stop.placeId. Duplicate add emits no event. Same-status retry returns
the current day without changing timestamps. A removed stop retry retains the
existing 404 behavior and emits nothing. Failed/rolled-back mutations leave no
event. Generation, selection, routing and weather behavior are unchanged.

## Negative feedback validation
The existing request/response shape is preserved, including client name/category
fields, but stored identity/name/category come from Place. The public endpoint
accepts only NOT_INTERESTED, TOO_EXPENSIVE, TOO_FAR and ALREADY_VISITED. It returns
the existing event on duplicate feedback. State-action injection returns the
existing structured 400 error; unknown/unverified Place identity returns 404.

## Idempotency strategy
The same event means the same authenticated user, source, context and action.
eventKey is SHA-256 of length-prefixed source/context plus action; user ownership
is a separate component of the database unique constraint.

- SAVED_PLACE context is a favorite lifecycle's SavedPlace.id.
- ITINERARY context is ItineraryStop.id: each action counts at most once per stop,
  including DONE → PLANNED → DONE. A newly created stop has new context.
- EXPLORE context is canonical Place.id: each explicit action counts once per
  user/place, independent of repeat requests or changed reason text.

A pessimistic database lock on the authenticated user serializes these writers
before mutable state is read. The unique constraint is an additional guard.
Internal transition recording requires the state owner's existing transaction.
No distributed service or client idempotency token is required.

## User isolation
All new ownership comes from CurrentUserService, including internal event writes.
No public user ID is accepted. Existing owned-trip checks still run. Repository
history queries remain scoped by authenticated user email; uniqueness is per user.

## Synthetic / invalid Place behavior
Unknown, preview and itinerary-stop IDs cannot be resolved as verified canonical
Places. Starter/seed/planned-fallback providers and incomplete provider records
fail the existing verification contract. Public feedback rejects them. Historical
itinerary and favorite operations remain safe and skip the evidence.

## Historical compatibility
Existing rows and generic REMOVED actions are neither rewritten nor reverified.
The new fields stay null on those rows. Existing historical scoring reads remain
unchanged in this phase. Null/missing/unverified canonical stop identity never
receives a fabricated replacement ID.

## Tests added
PersonalizationEventIntegrationTest covers 16 cases: save/retry, unsave/retry/new
lifecycle, DONE/retry/reset, SKIPPED/retry, remove/retry, add/retry/failure, canonical
negative/retry, four unchanged negative weights, invalid/synthetic IDs, historical
null stop, user isolation/foreign trip rejection, concurrent double save,
transaction rollback, historical-row reads, negative HTTP contract, saved HTTP
contract. The focused MVC checks omit security filters and supply authenticated
SecurityContext explicitly; the full suite includes the existing authentication
regressions.

## Known limitations
Idempotency is tied to state/context, not an offline request log. An old save replay
after a later successful unsave is indistinguishable from an intentional new save.
Explicit negative feedback has no reset/revision flow in this phase.
Per-user writes serialize briefly. No PostgreSQL deployment was exercised; tests
use H2 PostgreSQL mode. New neutral add events still occupy the existing latest-80
history window; that limit/formula is intentionally unchanged. Historical evidence
continues to participate in existing scoring until a separately approved scoring
phase. No mobile code changed, so mobile tests/TypeScript are not required.

## Test results
Backend compile passed. The initial sandboxed test attempt failed because Mockito
could not attach its agent. The permitted rerun passed all 14 initial focused tests.
After adding rollback and historical-row coverage, the full compile/test run passed
all 16 focused cases.

## Full regression result
`cd backend && ./mvnw -q compile test`: exit 0.
23 test suites, 139 tests, 0 failures, 0 errors, 0 skipped.
Includes existing itinerary, Explore/provider, profile, auth, destination, start-area
and weather regressions. `git diff --check` passed.
Mobile tests and TypeScript were not run because no mobile files changed.

## Ready for Phase 3: YES
Phase 2 implementation and backend regression are complete. No Phase 3 work started.
Production deployment still requires the manual schema step described above.
