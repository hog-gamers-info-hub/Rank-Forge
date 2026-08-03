Rank-Forge v0.6.9 — Revision-Safe Roster Sync Replacement

1. Purpose and decision status

This document defines the implementation contract for the Phase 6 roster-OCR extension:

v0.6.9 — Revision-Safe Roster Sync Replacement

The version adds safe synchronization of an explicitly confirmed local roster replacement to Supabase.

This version follows:

v0.5.8 — Atomic Confirmed Roster Replacement

The local Room replacement is therefore already complete before v0.6.9 begins.

Decision:

Ready for implementation after this document is reviewed and merged.

This decision document does not itself modify Android runtime behavior, Room data, Supabase schema, RLS, migrations, sync queue state, or production infrastructure.

2. Canonical responsibility

v0.6.9 owns cloud synchronization of a replacement roster that has already been explicitly confirmed locally.

It must provide:

safe Supabase roster replacement;

stale-player deletion;

optimistic revision protection;

ownership and RLS enforcement;

transaction rollback;

retry-safe synchronization;

persistent sync-queue integration;

restoration compatibility;

protection against roster replacement after match processing has begun.

It does not perform OCR review or decide whether an OCR candidate roster is acceptable.

Those responsibilities remain in the later Phase 9 roster OCR review/correction extension.

3. Local source of truth

Room remains the local source of truth.

v0.5.8 performs the authoritative local confirmed-roster replacement first.

v0.6.9 then synchronizes the resulting current local tournament roster snapshot to Supabase.

A failed cloud operation must never undo, revert, partially rewrite, or corrupt the already-committed local roster.

Cloud synchronization is therefore:

confirmed local replacement
        ↓
current Room snapshot
        ↓
revision-safe cloud replacement

The cloud operation must not become the authority for whether the roster replacement is accepted locally.

4. Explicit synchronization only

Roster replacement synchronization must occur only through the dedicated roster-replacement synchronization operation.

It must not silently execute because of:

app startup;

authentication restoration;

ordinary tournament loading;

OCR extraction;

OCR validation;

screenshot selection;

tournament viewing;

roster viewing.

Persistent retry infrastructure may retry a previously recorded roster-replacement operation according to the existing Phase 6 foreground recovery rules.

5. Complete replacement boundary

Cloud replacement is a complete roster snapshot operation.

The input must represent the full fixed tournament roster:

exactly 12 tournament slots;

one team-slot record for each slot 1..12;

the current confirmed team name for each slot;

the complete current player list for every slot;

valid tournament identity;

deterministic existing cloud identities.

The operation must not perform a patch-style partial roster update.

A missing slot or structurally incomplete snapshot is a validation failure and must cause zero cloud mutation.

6. Existing identity rules are preserved

v0.6.9 must reuse the deterministic cloud identities already established by Phase 6.

Team-slot identity remains based on:

(tournament UUID, slot number)

Player identity remains based on:

(tournament UUID, slot number, roster position)

No new random identifiers may be introduced for existing tournament roster records.

No Room cloud-ID mapping table is required.

Repeated synchronization of the same confirmed roster must produce the same cloud IDs.

7. Dedicated cloud operation

v0.6.9 must not use the generic historical tournament snapshot write as the complete roster-replacement implementation.

A dedicated Supabase transactional RPC is required.

Approved operation name:

replace_tournament_roster_snapshot

The exact SQL parameter names may follow repository conventions, but the semantic inputs must include:

tournament identity;

complete 12-slot roster payload;

expected tournament cloud revision.

The function must execute as one database transaction.

No Edge Function is required.

8. Revision-safe write rule

The tournament aggregate revision remains the concurrency token.

The Android client must read the current locally known base cloud revision and provide it as the expected revision.

The RPC must:

lock/read the tournament row;

verify that it exists;

verify ownership through the caller/RLS boundary;

compare the expected revision with the current cloud revision;

reject stale writes without mutating roster data.

A revision mismatch must return a controlled conflict result.

The client must never respond to a stale-write conflict by blindly overwriting the newer cloud state.

9. Match-safety rule

Roster replacement is allowed only while the tournament has no match records.

The cloud RPC must reject replacement when any row exists in:

public.matches

for the target tournament.

This applies regardless of match state.

Therefore replacement is blocked when the tournament has:

a draft match;

a finalized match;

multiple matches of either state.

The rule deliberately mirrors the v0.5.8 zero-match replacement policy.

v0.6.9 must not delete, rewrite, detach, remap, or reinterpret existing matches to make a roster replacement possible.

10. Team-slot replacement behavior

The RPC must validate the complete twelve-slot payload before mutation.

The existing twelve deterministic tournament team-slot records are then updated to the confirmed replacement values.

The operation must not:

create duplicate slot numbers;

move a slot to another tournament;

alter tournament ownership;

alter deterministic slot identity;

delete match-linked team slots;

change scoring or standings data.

All slot updates belong to the same transaction as player replacement.

11. Stale-player deletion

Generic upsert-only behavior is insufficient for v0.6.9.

The cloud roster after success must exactly reflect the confirmed local player snapshot.

Therefore the transactional replacement must:

validate the complete incoming player payload;

upsert the incoming deterministic player rows;

remove old roster-player rows for the tournament that are absent from the new confirmed snapshot.

This stale-row deletion is required.

Example:

Existing cloud slot:

1: Player A
2: Player B
3: Player C
4: Player D

Replacement:

1: Player A
2: Player X
3: Player C

After successful replacement, the cloud roster must contain only:

1: Player A
2: Player X
3: Player C

The old position-4 player must not survive as stale cloud data.

Deletion must be tournament/slot scoped and must not affect players belonging to another tournament.

12. Atomicity and rollback

The following must form one Supabase transaction:

revision validation;

match-existence validation;

twelve-slot validation/update;

player validation;

player upsert;

stale-player deletion;

tournament revision advancement.

If any step fails:

no partial team-slot replacement may remain;

no partial player replacement may remain;

no stale-player cleanup may be partially committed;

the tournament revision must not advance.

The previous committed cloud roster remains intact.

13. Revision advancement

A successful replacement advances the tournament aggregate cloud revision exactly once.

The client then records the returned/new cloud revision as the tournament's confirmed base cloud revision using the existing local revision-state mechanism.

The replacement must not independently increment the tournament revision once per:

slot;

player;

stale-player deletion.

The externally visible tournament aggregate revision advances one logical step for one successful roster-replacement operation.

14. Ownership and RLS

The existing authenticated owner hierarchy remains authoritative.

The roster replacement must be restricted to the tournament owner.

The implementation must not use:

service-role credentials;

ownership bypasses;

SECURITY DEFINER merely to avoid RLS;

caller-supplied arbitrary owner reassignment;

anonymous writes.

Cross-account replacement attempts must fail.

Existing RLS must remain active.

Any new RPC grants must be limited to the required authenticated execution boundary.

15. Persistent sync queue

Roster replacement is a distinct synchronization operation and must not masquerade as generic tournament upload.

Add a dedicated operation identity:

ROSTER_REPLACEMENT

to the existing persistent sync queue operation model.

Its stable operation identity remains tournament-scoped.

Conceptually:

ROSTER_REPLACEMENT|<tournamentId>

The existing unresolved-entry deduplication rule must apply.

Repeated failures for the same unresolved roster-replacement operation must not create unlimited duplicate queue entries.

No Room schema migration is expected solely for the new operation value because the existing queue stores operation type as a string.

16. Retry behavior

The roster-replacement operation must have a dedicated retry action.

The existing queue retry dispatcher must route:

ROSTER_REPLACEMENT

to that dedicated action.

It must never route a roster-replacement queue entry through generic tournament upload.

A retry reconstructs the replacement payload from the current authoritative local Room state.

The retry must preserve:

deterministic team-slot IDs;

deterministic player IDs;

expected cloud revision handling;

stale-player deletion behavior;

match-safety enforcement.

No blind automatic conflict overwrite is allowed.

17. Queue outcome mapping

The operation must map results consistently with existing Phase 6 queue semantics.

Expected categories include:

success → completed;

authentication unavailable → blocked authentication;

network failure → blocked network;

invalid local/cloud snapshot → failed validation;

authorization/RLS rejection → failed authorization;

stale revision → failed conflict;

prohibited existing match state → failed validation or another explicitly frozen non-retryable safety category;

unexpected failure → failed unknown.

The implementation must not classify revision conflicts as ordinary retryable network errors.

18. Idempotency

The operation must be idempotent at the application-data level.

Repeating a replacement for the same authoritative roster must not create:

duplicate tournaments;

duplicate slots;

duplicate players;

stale players;

extra logical roster versions.

Deterministic identity and transaction semantics provide record-level idempotency.

The existing sync-queue unresolved-operation deduplication remains responsible for preventing duplicate queued operations.

No new generic idempotency subsystem is required.

19. Restoration compatibility

Existing tournament/roster cloud restoration must remain compatible with successfully replaced cloud roster data.

After a successful replacement, a later authorized restoration should observe only the current cloud roster.

It must not restore stale deleted players.

No new cloud restoration workflow is required unless focused implementation testing proves that the existing restoration mapper cannot consume the replacement output correctly.

If such an incompatibility is discovered, implementation must stop rather than silently broaden v0.6.9.

20. Cloud tournament status

v0.6.9 does not introduce a new cloud tournament lifecycle.

Existing Phase 6 status mapping remains unchanged.

A locally confirmed roster does not automatically change the Supabase tournament into a new cloud lifecycle status.

Do not modify cloud tournament status semantics as part of roster replacement.

21. Interaction with v0.5.8

v0.5.8 and v0.6.9 have separate responsibilities.

v0.5.8

Owns:

explicit operator confirmation;

local validation;

zero-match safety;

complete 12-slot Room replacement;

local transaction atomicity;

local revision advancement.

v0.6.9

Owns:

cloud synchronization of that already-confirmed local state;

cloud revision comparison;

cloud zero-match protection;

cloud transaction atomicity;

cloud stale-player deletion;

persistent retry integration.

v0.6.9 must not duplicate the v0.5.8 local replacement transaction.

22. OCR boundary

No roster OCR execution belongs to v0.6.9.

This version must not modify:

roster screenshot intake;

crop preparation;

ML Kit execution;

roster raw OCR extraction;

roster candidate parsing;

slot association;

roster OCR validation;

OCR confidence rules;

OCR acceptance evaluation.

OCR candidates are not automatically persisted or synchronized.

Only an already-confirmed local roster can reach this synchronization boundary.

23. UI boundary

No new roster OCR review screen is part of v0.6.9.

No visual redesign is required.

If a presentation-layer trigger is required for an already-supported explicit synchronization action, it must remain narrowly scoped.

The core correctness of v0.6.9 belongs to:

domain contracts;

repository/use-case logic;

persistent queue routing;

Supabase transactional behavior.

Do not place synchronization logic inside Compose code.

24. Database migration rule

A new Supabase migration is required for the dedicated roster-replacement RPC and its execution grants.

Existing migrations must not be edited.

The migration must not introduce unrelated schema changes.

Expected database-object scope:

one dedicated roster-replacement RPC;

required execute privilege changes for that RPC;

no new application table;

no new column;

no new cloud roster identity model;

no new Storage object;

no Edge Function.

If implementation proves an additional database object is necessary, stop and review the scope before adding it.

25. Expected Android implementation areas

The implementation is expected to remain within the existing Phase 6 architecture.

Likely production areas are limited to:

dedicated roster cloud replacement domain contract/action;

roster cloud replacement repository/data source;

dedicated use case;

existing deterministic tournament/slot/player mapper reuse;

persistent sync operation enum/model;

queue retry dispatcher;

Hilt bindings/providers.

Existing generic tournament upload behavior must remain backward compatible.

Exact implementation filenames must be frozen before implementation begins.

No unrelated file may be modified without stopping for scope review.

26. Required automated coverage

Android/domain tests

Verify:

complete valid local roster produces the expected replacement payload;

deterministic team-slot identities are preserved;

deterministic player identities are preserved;

invalid/incomplete roster is rejected before cloud execution;

authentication failure is controlled;

authorization failure is controlled;

network failure is controlled;

stale revision returns conflict;

success updates the known base cloud revision;

failure does not falsely advance cloud revision state.

Queue tests

Verify:

ROSTER_REPLACEMENT gets a stable tournament-scoped identity;

unresolved duplicate roster-replacement entries are deduplicated;

retry dispatcher invokes only the roster-replacement retry action;

success marks/resolves the queued operation;

authentication/network outcomes remain recoverable under existing rules;

revision conflict remains a conflict and is not blindly retried as a write.

Supabase pgTAP tests

Verify:

owner can replace their own match-free tournament roster;

cross-account replacement is rejected;

anonymous replacement is rejected;

stale expected revision is rejected;

missing/invalid revision is rejected;

incomplete twelve-slot snapshot is rejected;

duplicate/invalid slot structures are rejected;

existing draft match blocks replacement;

existing finalized match blocks replacement;

successful replacement updates all slot names;

incoming players are present;

stale players are deleted;

other tournaments are untouched;

revision advances exactly once;

failure rolls back the complete operation.

27. Required local verification

Implementation verification should include, as applicable:

Android focused JVM tests
full JVM regression suite
lintDebug
assembleDebug
assembleDebugAndroidTest
focused connected Android tests
full connected Android regression suite
Supabase local db reset
focused v0.6.9 pgTAP tests
full pgTAP regression suite
git diff --check
exact changed-file boundary review

Do not use Codex merely to run these verification commands.

Any unavailable environment or blocked verification must be reported accurately rather than marked as passed.

28. Explicit exclusions

v0.6.9 does not include:

local roster replacement implementation already owned by v0.5.8;

automatic OCR persistence;

roster OCR review/correction UI;

real roster screenshot evaluation;

scoreboard OCR changes;

roster OCR algorithm changes;

new matching algorithms;

scoring changes;

standings changes;

match finalization changes;

protected match correction changes;

CSV export changes;

Google Sheets export changes;

screenshot Storage changes;

Room schema migration solely for the queue operation;

WorkManager or background scheduling;

new cloud tournament lifecycle/status semantics;

production deployment;

production secrets;

service-role client usage;

destructive conflict resolution;

automatic roster overwrite when matches exist.

29. Later-version handoff

After v0.6.9 is implemented and merged, the remaining canonical roster-OCR prerequisite sequence is:

v0.9.x — Roster OCR Review and Correction
    ↓
v0.12.x — Real Roster OCR Acceptance Evaluation

The Phase 9 roster OCR review/correction extension will consume staged roster OCR candidates, expose invalid/uncertain fields, permit manual correction or abandonment, and require explicit confirmation before persistence.

The Phase 12 roster acceptance evaluation must remain separate and must use approved representative genuine roster screenshots with manually verified expected data.

30. Acceptance criteria

v0.6.9 is complete when all of the following are true:

a dedicated authenticated roster-replacement cloud operation exists;

the complete 12-slot confirmed local roster is the synchronization source;

deterministic existing cloud identities are reused;

the operation is protected by expected cloud revision;

stale writes do not mutate data;

any existing match blocks roster replacement;

stale cloud player rows are removed;

the complete cloud roster replacement is transactional;

failure preserves the previous cloud roster;

successful replacement advances the tournament aggregate revision exactly once;

successful Android handling records the new base cloud revision;

a dedicated ROSTER_REPLACEMENT persistent queue operation exists;

retry dispatch uses the dedicated roster-replacement action;

duplicate unresolved operations are not created;

RLS/ownership isolation remains enforced;

existing restoration remains compatible;

no OCR/UI/scoring/export scope is added;

focused and regression verification passes.

31. Closure condition

Merging this decision document does not complete v0.6.9.

The version is complete only after:

this decision PR is merged;

the implementation boundary is frozen;

the implementation is completed;

Android and Supabase verification passes;

the implementation PR is merged;

local main is synchronized with origin/main;

no unresolved v0.6.9 branch or blocker remains.

Phase 12 must not proceed to its roster OCR acceptance evaluation until v0.6.9 and the later Phase 9 roster OCR review/correction prerequisite are complete.
