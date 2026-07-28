# Rank-Forge v0.6.8 - Protected match finalization

## Purpose

v0.6.8 moves cloud finalization of an already synchronized draft match into one server transaction. The server, not a client-side status update, is the authority that makes the cloud match finalized.

## Transactional RPC

The `finalize_match_snapshot` RPC locks the owner tournament row, validates the expected revision, verifies ownership, and locks the requested match. It accepts only a cloud draft match with exactly twelve valid results for that tournament, replaces its draft result rows atomically, sets `finalized_at` and `finalized_by`, marks the match finalized, and advances the tournament revision once.

The RPC is narrowly scoped `security definer`: it performs an explicit `auth.uid()` ownership check, exposes only deterministic outcomes, revokes public execution, and grants execution to `authenticated`.

## Revision and idempotency rules

* Missing or non-positive revisions fail without mutation.
* A stale expected revision returns `stale_write` and the current revision.
* A finalized match returns `already_finalized` without changing results or revision. This is the idempotent retry result.
* Any retry rechecks the server revision and match status; it never force-overwrites a newer final result.

## Finalized-data protection

`write_match_snapshot` rejects any write that targets an existing finalized match. Therefore draft sync and later generic match writes cannot overwrite protected cloud results. Existing v0.6.7.1 resolution remains draft-only; finalized or indeterminate conflicts remain blocked.

## Android and queue behavior

Local finalization remains local-first for offline/no-session operation. Cloud finalization is performed by the existing `FINALIZED_MATCH_SYNC` path, which now invokes the protected RPC one finalized match at a time and carries the returned revision to the next match. Network and authentication failures retain their established retryable queue classifications; validation and conflict failures remain non-retryable. The existing operation identity and v0.6.6 active-entry de-duplication are unchanged, and completed entries remain retained.

## Limitations and deferred work

This version does not add a Room migration, background processing, automatic merge, finalized correction approval, audit history, a queue-management screen, or production Supabase changes. Protected finalized corrections and audit history remain v0.6.8.1 work.
