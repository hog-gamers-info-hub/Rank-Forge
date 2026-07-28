# Rank-Forge v0.6.7 - Revision-based conflict detection

## 1. Purpose

This decision document defines and records the implemented v0.6.7 revision-based conflict-detection design. It protects cloud writes from stale local state and preserves local/cloud data when restoration detects divergence.

## 2. Current blocker

v0.6.7 cannot be safely implemented using only the current schema and code:

* Supabase revision columns default to positive integers, but cloud writes are not protected by compare-and-increment behavior.
* The v0.6.1.1 decisions explicitly deferred revision-update triggers and stale-write protection.
* Kotlin cloud DTOs, restoration snapshots, local Room entities, and domain models do not carry persisted cloud or base revision values.
* Cloud writes currently use unconditional PostgREST upserts.

Consequently, the app has neither a reliable expected/base revision to send nor an authoritative conditional-write mechanism to enforce it. Safe stale-write detection and local/cloud divergence detection require approved Supabase and Room schema work.

## 3. Approved design requirement

The v0.6.7 implementation may include the following prerequisite work:

* A versioned Supabase migration that provides revision-safe conditional write support.
* A Room schema/version migration that persists the base and cloud revision values needed for local comparison and retry.
* Kotlin model and cloud DTO changes that carry revision data through reads, writes, restoration, and local persistence.
* Repository and use-case changes that compare the expected/base revision with the current cloud revision.
* Queue and retry handling that records stale-write and divergence outcomes deterministically and non-destructively.

These changes are implemented together. Revision conflict detection is not a client-only pre-check: the Supabase function performs the authoritative comparison while holding the tournament row lock.

## 4. Supabase revision requirements

Supabase must provide a revision contract for every v0.6.7-protected cloud record:

* A cloud read returns the current tournament revision with the record data.
* `write_tournament_snapshot` and `write_match_snapshot` atomically verify the expected tournament revision while locking the owner-visible tournament row, then advance that revision only when it matches.
* A write that finds an advanced cloud revision must return a deterministic stale-write conflict and must not overwrite cloud data.
* A missing or indeterminate revision must fail safely and non-destructively; it must not fall back to an unconditional upsert.
* The versioned Supabase migration adds narrowly scoped, `security invoker` RPCs and grants execution only to `authenticated`. Existing RLS and ownership protections remain in force.

## 5. Local Room/base-revision requirements

Room must persist the revision values required to make a meaningful comparison after local edits, process restart, restoration, and retry:

* Room database version 5 adds `sync_revisions`, keyed by tournament ID, with `local_revision` and nullable `base_cloud_revision` fields.
* Carry revision values through domain models, cloud DTOs, restoration snapshots, and local entities without substituting timestamps or inferred values.
* Update the persisted base/cloud revision only from a confirmed cloud read or successful conditional write.
* Migration 4 to 5 preserves existing data and leaves legacy rows without a revision record. Such rows are treated as missing revision metadata and cannot write until an explicit safe restore establishes a base revision.

The local representation must preserve enough identity and revision context to distinguish a locally changed record from a record whose cloud version has advanced.

## 6. Conditional write requirements

Cloud writes must not overwrite newer cloud data.

* A new local tournament uses the explicit create expectation `0`; persisted server revisions remain positive. Known cloud records use their positive base revision.
* When the expected/base revision matches the current cloud revision, the write may proceed and advances the tournament revision.
* When the cloud revision has advanced, the write must be blocked and return a stale-write conflict.
* When the revision is missing, unreadable, or otherwise indeterminate, the action must fail safely and non-destructively.
* The client must not treat a local revision comparison as sufficient protection without the cloud-side conditional check.

## 7. Restoration/divergence detection requirements

Restore and cloud-read flows should detect local/cloud divergence when both local and cloud revision data are available.

* A detected divergence preserves both local and cloud data for a later decision. Restoration returns a deterministic conflict before any local replacement occurs.
* v0.6.7 must not auto-merge divergent data.
* v0.6.7 must not delete local data or overwrite cloud data to resolve divergence.
* v0.6.7 does not add conflict-resolution UI.

If revision data is unavailable or indeterminate, the flow must preserve data and report a safe non-destructive outcome rather than infer that either side is current.

## 8. Queue/retry/idempotency compatibility

Revision conflicts must integrate with the existing persistent sync queue without changing the v0.6.6 duplicate-prevention guarantees:

* Stale-write and divergence outcomes use `FAILED_CONFLICT` with stable failure metadata (`STALE_WRITE_CONFLICT`, `LOCAL_CLOUD_DIVERGENCE`, or `MISSING_REVISION`) and do not create duplicate active queue entries.
* A retry repeats the same conditional RPC write, so the cloud revision is re-checked before every write attempt.
* Conflicts are non-retryable unless a later version adds explicit resolution. While no dedicated persistent conflict status exists, the implementation must record a stable non-retryable failure outcome and deterministic conflict metadata using the existing queue contract.
* v0.6.6 operation-identity duplicate prevention must remain intact.
* Completed queue entries remain retained; v0.6.7 does not add a cleanup or deletion policy.

## 9. Explicit non-goals

v0.6.7 does not include:

* Conflict-resolution UI.
* A manual merge workflow.
* Automatic merge.
* Destructive overwrite of local or cloud data.
* Protected finalized-data audit behavior.
* Background retry.
* Cleanup or deletion of queue entries.

## 10. Deferred items

The following remain deferred beyond v0.6.7:

* User-facing conflict resolution.
* Protected finalized-data correction workflow.
* Audit trail.
* Server-side exactly-once guarantees beyond the approved revision checks.
* Cleanup of historical duplicate rows.
* A full multi-device merge policy.

## 11. Implementation approval status

Implemented in the v0.6.7 branch. Tournament/roster upload, draft-match sync, and finalized-match sync use revision-safe RPC writes. Tournament and match restoration carry the tournament revision and block local replacement when local/cloud divergence is detected. Existing legacy local records with no revision metadata safely return a missing-revision conflict for writes until restored; no automatic reconciliation is performed.
