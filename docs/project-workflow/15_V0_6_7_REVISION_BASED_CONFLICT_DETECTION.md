# Rank-Forge v0.6.7 - Revision-based conflict detection

## 1. Purpose

This decision document defines the prerequisite design for v0.6.7 revision-based conflict detection. It records why the current implementation cannot safely detect stale writes or local/cloud divergence, and establishes the schema and write-contract direction required before implementation begins.

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

These changes must be scoped together. Revision conflict detection must not be added as a partial client-only check that can race with an unconditional cloud write.

## 4. Supabase revision requirements

Supabase must provide a revision contract for every v0.6.7-protected cloud record:

* A cloud read must return the current revision with the record data.
* A write must atomically verify the expected/base revision against the current cloud revision and advance the revision only when they match.
* A write that finds an advanced cloud revision must return a deterministic stale-write conflict and must not overwrite cloud data.
* A missing or indeterminate revision must fail safely and non-destructively; it must not fall back to an unconditional upsert.
* The required implementation may use a versioned Supabase migration, including narrowly scoped database support for conditional compare-and-increment writes. The implementation must preserve existing authorization and ownership protections.

## 5. Local Room/base-revision requirements

Room must persist the revision values required to make a meaningful comparison after local edits, process restart, restoration, and retry:

* Persist the cloud/base revision associated with each locally stored, synchronizable record or snapshot.
* Carry revision values through domain models, cloud DTOs, restoration snapshots, and local entities without substituting timestamps or inferred values.
* Update the persisted base/cloud revision only from a confirmed cloud read or successful conditional write.
* Add the required Room version migration before relying on these values in production.

The local representation must preserve enough identity and revision context to distinguish a locally changed record from a record whose cloud version has advanced.

## 6. Conditional write requirements

Cloud writes must not overwrite newer cloud data.

* When the expected/base revision matches the current cloud revision, the write may proceed and must advance the revision.
* When the cloud revision has advanced, the write must be blocked and return a stale-write conflict.
* When the revision is missing, unreadable, or otherwise indeterminate, the action must fail safely and non-destructively.
* The client must not treat a local revision comparison as sufficient protection without the cloud-side conditional check.

## 7. Restoration/divergence detection requirements

Restore and cloud-read flows should detect local/cloud divergence when both local and cloud revision data are available.

* A detected divergence must preserve both local and cloud data for a later decision.
* v0.6.7 must not auto-merge divergent data.
* v0.6.7 must not delete local data or overwrite cloud data to resolve divergence.
* v0.6.7 does not add conflict-resolution UI.

If revision data is unavailable or indeterminate, the flow must preserve data and report a safe non-destructive outcome rather than infer that either side is current.

## 8. Queue/retry/idempotency compatibility

Revision conflicts must integrate with the existing persistent sync queue without changing the v0.6.6 duplicate-prevention guarantees:

* Stale-write and divergence outcomes must not create duplicate active queue entries.
* A retry must re-read or conditionally re-check the cloud revision before attempting its write.
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

The revision-conflict design direction is approved, but the current implementation is blocked until a subsequent scoped task authorizes the required versioned Supabase migration and Room schema/version migration together with their Kotlin propagation. No client-only stale-write or divergence implementation is approved before those prerequisites exist.
