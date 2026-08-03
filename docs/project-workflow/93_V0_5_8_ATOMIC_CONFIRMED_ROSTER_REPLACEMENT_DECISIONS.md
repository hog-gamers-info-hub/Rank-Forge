# v0.5.8 — Atomic Confirmed Roster Replacement Decisions

## 1. Version

**v0.5.8 — Atomic Confirmed Roster Replacement**

Owner phase: **Phase 5 — Local Persistence**

This is a roster-OCR roadmap extension version required before the Phase 9 roster OCR review/correction workflow may persist a reviewed roster candidate.

## 2. Purpose

v0.5.8 adds one safe local operation for replacing the complete tournament roster after explicit operator confirmation.

The operation must replace the full 12-slot roster atomically and leave the tournament in the confirmed state.

It exists so later roster OCR review/correction can hand a complete reviewed candidate to one safe persistence boundary instead of writing individual team slots incrementally.

## 3. Current State

The current local roster workflow persists roster players one team slot at a time.

Current `saveRoster(...)` behavior:

- replaces one slot at a time;
- deletes that slot's previous players before inserting the new list;
- changes a confirmed tournament back to DRAFT when roster data is edited;
- performs the slot update inside a Room transaction.

Tournament confirmation is currently a separate operation performed after validation.

This is correct for manual incremental roster editing but is not sufficient for confirmed roster OCR replacement because a later OCR workflow must never expose or persist a partially replaced 12-team roster.

## 4. Frozen Replacement Boundary

v0.5.8 must provide one local confirmed-roster replacement boundary that accepts the complete reviewed roster candidate for all fixed tournament slots 1 through 12.

The replacement candidate must include:

- the exact tournament ID;
- exactly the fixed slots 1 through 12;
- the reviewed team name for every slot;
- the reviewed player list for every slot.

The operation must not accept partial replacement.

The operation must not silently fill missing slots from the previously persisted roster.

The operation must not infer missing OCR values.

## 5. Explicit Confirmation Requirement

The complete roster candidate may be persisted only after an explicit confirmation action from the calling workflow.

v0.5.8 provides the persistence boundary; it must not automatically invoke itself because OCR extraction or validation completed.

A later Phase 9 roster OCR review/correction workflow owns the user-facing confirmation action.

Manual roster entry remains supported independently.

## 6. Validation Contract

The replacement candidate must pass the existing canonical tournament roster validation rules before destructive database work begins.

Existing `RosterValidator` behavior must be reused.

v0.5.8 must not introduce:

- new team-name validation rules;
- new player-name normalization rules;
- fuzzy player matching;
- OCR correction rules;
- a new cross-team duplicate-player policy;
- different minimum or maximum roster-size rules.

If the candidate is invalid, the operation must reject it without modifying:

- team-slot names;
- roster players;
- tournament status;
- local revision metadata;
- the legacy local-state mirror.

## 7. Tournament Existence

Replacement requires the target tournament to exist locally.

A missing tournament must produce a controlled rejection result.

No tournament may be created implicitly by the replacement operation.

## 8. Match-Safety Policy

v0.5.8 adopts the strict safe policy required by the roster OCR roadmap:

> A confirmed roster may be replaced only while the tournament has zero created matches.

If any match already exists for the tournament, regardless of whether that match is DRAFT or FINALIZED, roster replacement must be rejected.

This protects:

- historical team identity;
- finalized match integrity;
- draft match team references;
- scoring and standings interpretation;
- correction history;
- OCR evidence associated with match team slots.

v0.5.8 does not introduce roster revision semantics for tournaments that already contain matches.

Any future requirement to replace a roster after match creation requires a separate canonical product and safety decision.

## 9. Tournament Status

A valid replacement may operate on an existing DRAFT or CONFIRMED tournament when the zero-match rule is satisfied.

After a successful complete replacement, the tournament must be:

**CONFIRMED**

This allows the same safe boundary to support:

- initial confirmed roster installation; and
- explicit replacement of an existing confirmed roster before any match exists.

A confirmed roster must never be silently overwritten.

## 10. Atomic Room Transaction

The complete local replacement must occur within one Room database transaction.

The transaction must encompass all persistence required for the replacement, including:

1. replacing all 12 team-slot names;
2. replacing all roster-player rows belonging to all 12 slots;
3. persisting the resulting CONFIRMED tournament status;
4. updating the legacy local-state mirror consistently;
5. advancing local revision state exactly once for the successful logical replacement.

No intermediate partial roster may become the committed database state.

Existing Room tables and persistence structures must be extended rather than duplicated.

## 11. Old-Roster Preservation

The previously confirmed roster remains authoritative until the replacement transaction successfully commits.

If validation or an eligibility check fails, the old roster must remain unchanged.

If the Room transaction fails before commit, Room rollback semantics must preserve the previously committed roster.

In-memory repository state must not be switched to the replacement candidate until the database transaction succeeds.

## 12. Local Revision Semantics

One successful complete replacement is one logical local mutation.

Therefore:

- local revision must advance once after successful replacement;
- it must not advance once per team slot;
- rejected replacements must not advance it;
- failed transactions must not advance the committed revision.

The existing revision mechanism must be reused.

v0.5.8 does not define Supabase revision behavior; that belongs to v0.6.9.

## 13. Team-Slot Persistence

The operation must reuse the existing fixed tournament slot identities 1 through 12.

It must update their reviewed team names rather than creating a parallel OCR-specific team table.

Existing slot identity must remain stable.

No team-slot renumbering is allowed.

## 14. Roster-Player Persistence

The operation must reuse the existing `roster_players` storage model.

All replacement players must belong to:

- the requested tournament ID; and
- their requested fixed slot.

Player order must remain deterministic through existing roster-position semantics.

No OCR-specific confirmed-player table may be introduced.

## 15. DAO Strategy

A new Room schema or migration is not currently required because the existing tournament, team-slot, roster-player, match, state, and revision tables can represent the required result.

The implementation may extend existing DAO/repository operations only where required for the atomic transaction.

If implementation review discovers that a schema or migration change is actually necessary, implementation must stop and return to the decision gate before making that change.

## 16. Result Contract

The replacement boundary must return a deterministic typed result.

At minimum the caller must be able to distinguish:

- successful replacement;
- tournament not found;
- invalid roster candidate;
- replacement blocked because one or more matches already exist.

Unexpected persistence failures must not be converted into false success.

Cancellation must retain normal coroutine cancellation behavior.

Exact Kotlin type names are deferred to implementation design.

## 17. Manual Roster Workflow Preservation

The existing manual roster workflow remains valid.

v0.5.8 must not require manual entry to use the new all-12 replacement operation.

Existing behavior for:

- per-slot editing;
- team-name editing;
- roster validation;
- ordinary roster confirmation

must remain unchanged unless a narrowly required shared contract change is explicitly approved during implementation review.

## 18. OCR Boundary

v0.5.8 does not execute OCR.

It does not:

- read roster screenshots;
- crop images;
- preprocess images;
- call ML Kit;
- parse OCR text;
- associate OCR slots;
- correct OCR candidates;
- display OCR review UI.

It accepts only an already-reviewed complete roster candidate from a later caller.

## 19. Cloud Boundary

v0.5.8 is local-only.

It does not add or change:

- Supabase tables;
- Supabase migrations;
- RLS policies;
- cloud roster replacement;
- stale-player deletion in Supabase;
- synchronization conflict handling;
- sync queue operations;
- cloud revision reconciliation;
- backend restoration behavior.

Those requirements belong to **v0.6.9 — Revision-Safe Roster Sync Replacement**.

## 20. Finalized-Match Protection

No finalized match data may be changed by v0.5.8.

Because replacement is blocked whenever any match exists, finalized-match integrity is protected by construction.

No match placements, kills, corrections, OCR evidence, scoring records, or standings data may be rewritten as part of roster replacement.

## 21. Testing Requirements

v0.5.8 implementation must add focused automated coverage for at least:

- successful complete 12-slot replacement;
- replacement updates all reviewed team names;
- replacement updates all reviewed player lists;
- successful replacement leaves tournament CONFIRMED;
- replacement survives Room database close and reopen;
- existing roster remains unchanged when candidate validation fails;
- replacement is rejected for a missing tournament;
- replacement is rejected when a DRAFT match exists;
- replacement is rejected when a FINALIZED match exists;
- blocked replacement does not mutate roster or team names;
- blocked replacement does not change tournament status;
- blocked replacement does not advance local revision;
- successful replacement advances local revision exactly once;
- existing manual roster workflow remains operational.

A transaction-failure rollback test should be added if a realistic deterministic Room failure can be exercised without adding a production-only failure-injection mechanism.

Do not add artificial production failure hooks solely to manufacture a rollback test.

## 22. Verification Requirements

Implementation verification must include the relevant:

- JVM unit tests;
- Room/instrumentation tests;
- Android build;
- Android test build;
- connected instrumentation tests where affected;
- lint;
- `git diff --check`.

No private roster screenshots or genuine player-name fixtures are required for v0.5.8.

Synthetic test data must be used.

## 23. Privacy and Security

v0.5.8 must not commit:

- genuine roster screenshots;
- real player names from private acceptance evidence;
- raw OCR payloads;
- private image paths;
- signed URLs;
- Supabase credentials;
- service-role credentials;
- other secrets.

The version operates only on already-reviewed domain roster values.

## 24. Explicitly Out of Scope

The following are out of scope for v0.5.8:

- v0.6.9 cloud roster replacement;
- roster OCR review/correction UI;
- real roster OCR acceptance testing;
- OCR extraction or parsing changes;
- player fuzzy matching;
- match-result OCR behavior;
- scoring;
- standings;
- match finalization changes;
- protected correction changes;
- CSV export;
- Google Sheets export;
- authentication;
- RLS changes;
- Storage changes;
- screenshot lifecycle changes;
- new cross-team duplicate-player policy;
- replacing rosters after any match has been created.

## 25. Acceptance Criteria

v0.5.8 is complete when:

1. one explicit operation can accept a complete valid 12-slot reviewed roster candidate;
2. the operation rejects incomplete or invalid candidates before persistence;
3. the operation rejects replacement when any tournament match exists;
4. all team names and roster players are replaced atomically;
5. the previous roster remains committed unless the complete transaction succeeds;
6. successful replacement leaves the tournament CONFIRMED;
7. local revision advances exactly once on successful replacement;
8. rejected operations leave revision and persisted roster unchanged;
9. the replacement survives database close/reopen;
10. existing manual roster behavior remains functional;
11. no Room schema change is made unless separately re-approved;
12. no Supabase, OCR, scoring, standings, or match behavior changes are introduced;
13. required focused and regression tests pass.

## 26. Implementation Boundary Gate

This document does not itself authorize arbitrary source-file changes.

After this decision document is merged into `main`, perform one final implementation-boundary review against the merged source.

Freeze the exact implementation and test file list before coding.

Any required file outside that approved list requires stopping implementation and returning to the decision gate.

## 27. Next Canonical Step

After v0.5.8 implementation is merged and verified, proceed to:

**v0.6.9 — Revision-Safe Roster Sync Replacement**

Do not begin the Phase 9 roster OCR review/correction workflow until both v0.5.8 and v0.6.9 are complete.
