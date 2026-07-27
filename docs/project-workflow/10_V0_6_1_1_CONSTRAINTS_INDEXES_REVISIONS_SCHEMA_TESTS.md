# v0.6.1.1 - Constraints, indexes, revisions, and schema tests

## 1. Purpose

This decision gate defines the exact database-hardening work approved for `v0.6.1.1` after the core schema introduced in `v0.6.1`.

It authorizes one follow-up migration and schema tests only. It does not authorize policy, synchronization, Android, or production work.

## 2. Governing baseline

The baseline is `supabase/migrations/20260727163228_v0_6_1_core_backend_schema.sql`. It creates only `tournaments`, `tournament_team_slots`, `players`, `matches`, and `match_results`, with RLS enabled and no policies.

The existing migration is applied history and must not be edited, deleted, reordered, or rewritten. `supabase/tests/` currently has no schema tests beyond its placeholder file.

## 3. Approved future implementation scope

`v0.6.1.1` may:

* Add one new versioned Supabase migration.
* Add the constraints, indexes, and `revision` columns in this document.
* Add database/schema tests under `supabase/tests/`.
* Append implementation and verification evidence to this document.

It must not add tables, policies, functions, triggers, RPCs, storage, sync, OCR, export, audit, idempotency, Android, Room, Gradle, or production changes.

## 4. Approved uniqueness constraints

The new migration must add these declarative unique constraints:

| Table | Columns | Reason |
| --- | --- | --- |
| `tournament_team_slots` | `(tournament_id, slot_number)` | One slot number per tournament. |
| `tournament_team_slots` | `(tournament_id, team_name)` | One entered team name per tournament; nullable draft team names remain allowed. |
| `players` | `(team_slot_id, normalized_name)` | Prevents duplicate normalized player names within one team while allowing the same name on different teams. |
| `matches` | `(tournament_id, match_number)` | One match number per tournament. |
| `match_results` | `(match_id, team_slot_id)` | A tournament team appears at most once in a match. |
| `match_results` | `(match_id, placement)` | A non-null placement appears at most once in a match; PostgreSQL permits multiple draft `NULL` placements. |

No global player-name, global team-name, case-insensitive, screenshot, export, idempotency, or audit uniqueness constraint is approved.

The current table shape cannot declaratively prove that a result's team slot belongs to the result's match tournament without a redundant tournament key or a trigger. That relationship-hardening decision is deferred; it must not be solved by a trigger in `v0.6.1.1`.

## 5. Approved indexes

The unique constraints above create their required backing indexes. The migration may add only these additional indexes:

| Table | Columns | Reason |
| --- | --- | --- |
| `tournaments` | `(owner_id)` | Supports the authenticated owner's tournament-list traversal. |
| `match_results` | `(team_slot_id)` | Supports the uncovered team-slot foreign key and team-result lookup. |

Do not add redundant indexes for foreign keys already covered by the leading columns of the approved unique indexes. Do not add status, text-search, partial, expression, analytics, or speculative production-performance indexes.

## 6. Approved revision boundary

Add `revision integer not null default 1` with `check (revision > 0)` to every core table:

* `tournaments`
* `tournament_team_slots`
* `players`
* `matches`
* `match_results`

No trigger may update `revision` or `updated_at`. The field establishes a stable positive version value only; conditional compare-and-increment writes, conflict detection, merge behavior, revision history, audit history, and stale-write protection remain deferred to `v0.6.7` and `v0.6.7.1`.

## 7. Required schema tests

Schema tests under `supabase/tests/` must run against a freshly migrated local database and verify:

1. The follow-up migration applies without editing the v0.6.1 migration.
2. All five tables retain UUID keys, timestamps, required foreign keys, and RLS enabled.
3. No RLS policy, trigger, function, RPC, or excluded table is introduced.
4. Each approved unique constraint accepts valid rows and rejects its duplicate case.
5. Existing status, slot-number, match-number, placement, and non-negative-kill checks still reject invalid values.
6. Approved parent-delete behavior and foreign-key rejection behavior remain intact.
7. Every `revision` column defaults to `1` and rejects zero or negative values.
8. The two explicit indexes exist, and unique-constraint backing indexes cover the remaining approved access paths.

Use the repository's supported Supabase database-test workflow, such as pgTAP when available. Tests must exercise schema behavior; RLS authorization scenarios are not substitutes for these tests and are not included here.

## 8. Deferred integrity and workflow rules

The following remain deferred because they require transactional workflow rules, policy design, sync design, or later data models:

* Exact twelve-slot and four-to-six-player completion enforcement.
* Maximum-ten-match and exactly-twelve-finalized-result enforcement.
* Controlled status transitions and finalized-data protection.
* Automatic `updated_at` updates.
* Cross-table proof that a result team slot belongs to the same tournament as its match.
* Revision compare-and-increment semantics, conflict detection, merge strategy, and history.
* Screenshot, OCR, correction, export, storage, audit, sync, idempotency, and standings objects.

## 9. RLS and authorization boundary

RLS stays enabled with no policies in `v0.6.1.1`. Ownership policies remain out of scope until `v0.6.2` because they must derive child access through the tournament owner and require an approved `SELECT`, `INSERT`, `UPDATE`, and `DELETE` policy design. Broad `authenticated` or `anon` policies are not an acceptable interim substitute.

Cross-account authorization tests remain out of scope until `v0.6.2.1` because they depend on the v0.6.2 ownership policies and controlled multiple-user fixtures. This version verifies only the structural fact that RLS remains enabled and that no policies were added.

## 10. Verification and safety requirements

Future implementation must run and report:

```powershell
supabase --version
supabase migration list
supabase db reset
supabase test db
git diff --check
git status --short
git diff --name-status
git diff --stat
```

If Docker or local Supabase blocks reset or database tests, the exact command failure must be recorded. No success may be claimed for blocked checks. No production Supabase command, destructive SQL, secret, local configuration, or generated artifact is approved.

## 11. Implementation readiness decision

Ready to implement `v0.6.1.1` after this decision gate is reviewed, committed, merged, and the next branch is confirmed clean.

Required future implementation branch:

```text
feature/v0.6.1.1-schema-hardening-tests
```
