# Rank-Forge v0.6.7.1 - Conflict resolution workflow

## 1. Purpose

This decision document defines the approved boundary for a foreground, explicit user workflow that resolves eligible draft-sync conflicts detected by v0.6.7. The workflow must preserve both sides until the user selects an approved action.

## 2. Current blocker

The current v0.6.7 restoration conflict contract is aggregate-only. It exposes tournament-level revision conflict metadata, but does not identify the affected match data or prove that the conflict is draft-only.

Resolving a restoration divergence with only that aggregate metadata could accidentally replace or alter finalized data. `DraftMatchCloudSync` stale-write conflicts are identifiable as draft-only, but `MatchCloudRestoration` conflicts are not yet safe to resolve through a user workflow.

## 3. Approved contract extension

v0.6.7.1 may add a minimal conflict-contract extension without changing the Supabase or Room schema unless a concrete later blocker requires separate approval:

* Conflict results may include operation scope.
* Conflict results may distinguish a draft conflict from a finalized or otherwise unsupported conflict.
* Draft conflicts may carry local draft snapshot data where available.
* Draft conflicts may carry cloud draft snapshot data where available.
* Draft conflicts may carry tournament ID, match ID where applicable, operation type, current cloud revision, and local/base revision.
* Unsupported or finalized conflicts must remain blocked and non-resolvable by this workflow.

## 4. Draft-only resolution scope

v0.6.7.1 may resolve only:

* Draft-match stale-write conflicts.
* Draft-match local/cloud divergence when both local and cloud data are confirmed draft and unfinalized.

Finalized-match conflicts are display-only or blocked. They are not resolvable in this version.

## 5. Required conflict snapshot data

The resolution workflow needs enough information to display and act safely:

* Tournament ID.
* Match ID where applicable.
* Operation type.
* Local draft placement, kills, and status where available.
* Cloud draft placement, kills, and status where available.
* Local/base revision.
* Current cloud revision.
* Conflict type.
* Resolvability classification: draft-resolvable or finalized/unsupported.

Missing or indeterminate data must result in a blocked, non-destructive conflict state.

## 6. Supported user actions

For an eligible draft-only conflict, the user may explicitly choose:

* Keep local draft: attempt a revision-safe cloud write using the current expected/base revision.
* Accept cloud draft: replace only local draft data after explicit user confirmation.
* Defer or cancel: leave local and cloud data unchanged.

If the cloud revision changes again during resolution, the workflow must return to a conflict state with updated metadata and must not force an overwrite.

## 7. Finalized-data exclusions

This version must never:

* Resolve finalized-match conflicts.
* Overwrite finalized results.
* Replace finalized local data.
* Modify finalized cloud data.
* Run protected finalized-data correction logic.
* Add finalized audit or correction workflows.

## 8. Queue/retry/idempotency compatibility

The existing v0.6.5 through v0.6.7 behavior remains in force:

* Resolution must not create duplicate active queue entries.
* Keep-local must use the existing revision-safe write behavior.
* Accept-cloud must update local draft data only after explicit user action.
* Conflicts remain non-retryable unless the user chooses a resolution action.
* v0.6.6 duplicate prevention remains intact.
* Completed entries remain retained.
* Queue metadata must be updated deterministically after a successful or failed resolution attempt.

## 9. Non-goals

v0.6.7.1 does not include:

* Automatic merge.
* Finalized conflict resolution.
* Protected finalized-data correction.
* Audit trail.
* Destructive overwrite without explicit user action.
* A general queue-management screen.
* Background work.
* Notifications.
* Supabase migration unless a concrete blocker receives separate approval.
* Room schema migration unless a concrete blocker receives separate approval.

## 10. Deferred items

The following remain deferred:

* Finalized conflict resolution.
* Finalized correction and audit workflow.
* Multi-device merge policy.
* User-visible conflict history.
* Cleanup or deletion of old queue entries.
* Automatic merge suggestions.
* Full server-side conflict resolution beyond existing revision-safe writes.

## 11. Implementation approval status

The v0.6.7.1 draft-only workflow direction and minimal conflict-contract extension are approved. Implementation may begin only after the contract carries sufficient scope and snapshot data to prove an action is draft-resolvable. Finalized and unsupported conflicts remain blocked.
