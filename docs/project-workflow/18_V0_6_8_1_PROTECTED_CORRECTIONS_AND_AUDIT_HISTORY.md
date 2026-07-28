# Rank-Forge v0.6.8.1 - Protected corrections and audit history

## Purpose

v0.6.8.1 makes correction of a cloud-finalized match an explicit, server-authoritative operation. The server retains every prior finalized result before applying the replacement values.

## Approved correction path

`correct_finalized_match_snapshot` is the only approved cloud mutation path for finalized result values. It validates authentication, tournament ownership, expected revision, match membership, finalized status, and twelve complete result rows in one transaction. A stale, missing, unauthorized, invalid, or non-finalized request returns a deterministic outcome without mutation.

## Audit retention

Every changed result row creates an immutable `match_correction_audit_entries` record containing the tournament, match, result and slot identity; old and new placement/kills; prior and new tournament revision; actor; timestamp; and nullable reason. The current correction UI has no safe reason field, so correction reasons remain nullable and user-entered reasons are deferred rather than invented.

## Revision and idempotency

The RPC advances the tournament revision once after a successful correction. A repeated request with the same expected revision and unchanged data returns `already_corrected` without writing a duplicate audit record. A stale request returns the current revision without overwrite. Completed queue entries remain retained; a match-level persisted correction request is not added in this version because the existing queue identity only identifies an operation and tournament, not a correction payload or match identity.

## Android behavior

The correction flow calls the protected RPC before Room replaces a finalized result, then records the existing local before/after correction history and confirms the returned cloud revision. Authentication, network, authorization, validation, stale, and missing-revision results leave the finalized local result unchanged and keep the correction draft available. Draft synchronization and draft conflict resolution remain unable to alter finalized cloud data.

## Non-goals and deferred items

This version does not add automatic merge, a multi-device merge policy, a conflict-resolution UI for finalized matches, background retry, queue replay for correction payloads, cleanup/deletion of audit or queue data, or production Supabase changes.
