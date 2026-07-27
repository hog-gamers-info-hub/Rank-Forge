# v0.6.2 - RLS policies and ownership rules

## 1. Purpose

This decision gate defines the ownership-based row-level security model for the five core Supabase tables. It authorizes later policy implementation only; it does not create a migration, policy, test, or production change.

## 2. Governing baseline

`v0.6.1` created `tournaments`, `tournament_team_slots`, `players`, `matches`, and `match_results`. `v0.6.1.1` added constraints, indexes, revisions, and schema tests. RLS is already enabled on all five tables and no policies currently exist.

Existing migrations are immutable history. Phase 6 remains local-first: this version must not upload, claim, hide, modify, or delete Room tournament data.

## 3. Approved ownership root

The sole Phase 6 ownership root is:

```text
public.tournaments.owner_id = auth.uid()
```

One authenticated user owns one tournament record. Tournament ownership cannot be transferred in v0.6.2: an insert or updated tournament row must keep `owner_id = auth.uid()`.

No collaboration, administrator bypass, public sharing, anonymous access, or role-based ownership model is approved.

## 4. Child ownership derivation

Every child policy must use an inline `EXISTS` ownership predicate; no helper function is approved.

| Table | Required ownership path |
| --- | --- |
| `tournament_team_slots` | `tournament_team_slots.tournament_id -> tournaments.id -> tournaments.owner_id = auth.uid()` |
| `players` | `players.team_slot_id -> tournament_team_slots.tournament_id -> tournaments.owner_id = auth.uid()` |
| `matches` | `matches.tournament_id -> tournaments.id -> tournaments.owner_id = auth.uid()` |
| `match_results` | `match_results.match_id -> matches.tournament_id -> tournaments.owner_id = auth.uid()` **and** `match_results.team_slot_id -> tournament_team_slots.tournament_id`, with both parent paths resolving to the same tournament. |

For `match_results`, the approved predicate must join `matches`, `tournament_team_slots`, and `tournaments`, require `tournament_team_slots.tournament_id = matches.tournament_id`, and require that tournament owner to equal `auth.uid()`. Checking the two parents independently is insufficient because it would permit a user to join a match and team slot from two different tournaments they own.

## 5. Policy shape and roles

Later implementation must create four separate policies per table: `SELECT`, `INSERT`, `UPDATE`, and `DELETE`. Policies may target `authenticated` only when each policy also contains the table-specific ownership predicate in this document.

`TO authenticated` is role gating, not authorization. A standalone `TO authenticated` policy, a permissive `USING (true)`, a permissive `WITH CHECK (true)`, or any `anon` policy is forbidden.

## 6. Approved operation behavior

| Table | SELECT | INSERT | UPDATE | DELETE |
| --- | --- | --- | --- | --- |
| `tournaments` | Rows where `owner_id = auth.uid()` | Only a new row with `owner_id = auth.uid()` | Only an owned current row; the proposed row must still have `owner_id = auth.uid()` | Only an owned row |
| `tournament_team_slots` | Rows whose tournament is owned | Only when the proposed `tournament_id` resolves to an owned tournament | Only an owned current row; the proposed `tournament_id` must still resolve to an owned tournament | Only when its current tournament is owned |
| `players` | Rows whose team slot's tournament is owned | Only when the proposed `team_slot_id` resolves through an owned team slot and tournament | Only an owned current row; the proposed `team_slot_id` must still resolve through an owned hierarchy | Only when its current team slot hierarchy is owned |
| `matches` | Rows whose tournament is owned | Only when the proposed `tournament_id` resolves to an owned tournament | Only an owned current row; the proposed `tournament_id` must still resolve to an owned tournament | Only when its current tournament is owned |
| `match_results` | Rows whose match and team slot resolve to the same owned tournament | Only when proposed `match_id` and `team_slot_id` resolve to the same owned tournament | Only an owned current row; proposed `match_id` and `team_slot_id` must still resolve to the same owned tournament | Only when both current parent paths resolve to the same owned tournament |

The policies authorize only ownership. They do not add finalized-data protection, revision compare-and-increment behavior, status-transition checks, or synchronization semantics.

## 7. Required UPDATE safeguards

Every update policy must contain both clauses:

* `USING`: validates the currently stored row through its current ownership path before it can be selected for update.
* `WITH CHECK`: validates the proposed row through the same table-specific ownership path after changed foreign keys or `owner_id` values are applied.

For `tournaments`, both checks require `owner_id = auth.uid()`. For each child table, `USING` applies the current parent path and `WITH CHECK` applies the proposed parent path. For `match_results`, both clauses require the same-tournament join across both parent references.

This prevents ownership transfer, reparenting to another owner's hierarchy, and a match-result update that changes either parent reference into an invalid or cross-tournament combination.

## 8. INSERT and DELETE boundaries

INSERT policies must use `WITH CHECK` only. A client may create a child row only when the row's proposed parent reference is already in the caller's owned tournament hierarchy; knowing a parent UUID is never sufficient.

DELETE policies must use the current-row ownership predicate in `USING`. Direct child deletion is allowed only within the caller's own hierarchy. An owner may delete an owned tournament; existing foreign-key cascades continue to handle its dependent rows. No delete policy grants access across an ownership boundary.

## 9. Forbidden authorization shortcuts

Broad `authenticated` access is forbidden because authentication establishes identity but does not establish ownership of a row. `anon` access is forbidden because no public tournament-data access model is approved.

`auth.role()` must not be used in these policies. It distinguishes session roles such as authenticated versus anonymous, but cannot prove that a tournament belongs to the caller. `auth.uid()` plus the approved parent hierarchy is the required authorization basis.

`SECURITY DEFINER`, RPCs, functions, and triggers remain out of scope. They can obscure or bypass the row policy boundary and are unnecessary for the direct ownership predicates approved here. The implementation must not add them as a shortcut for hierarchy checks.

## 10. Minimal v0.6.2 policy tests

Later implementation must add local pgTAP policy tests under `supabase/tests/` that verify:

1. RLS remains enabled and exactly the approved operation policies exist for all five tables.
2. Each policy targets `authenticated` and contains an ownership predicate; no `anon`, unconditional, `auth.role()`, `SECURITY DEFINER`, function, trigger, or RPC path is introduced.
3. One authenticated owner can create, select, update, and delete its own tournament hierarchy.
4. Child inserts succeed only when their proposed parent hierarchy is owned by that user.
5. A match result succeeds only when its match and team slot resolve to the same owned tournament.
6. Updates retain the required `USING` and `WITH CHECK` boundary, including owner preservation and child reparenting checks through policy-definition and single-owner behavior assertions.
7. An unauthenticated context cannot read or write the core tables.

These tests must run after a fresh local migration reset and must preserve the existing schema-hardening tests.

## 11. Deferred v0.6.2.1 cross-account tests

`v0.6.2.1` owns deliberate multi-user authorization testing. It must use at least two authenticated fixtures and separately prove cross-owner denial for `SELECT`, `INSERT`, `UPDATE`, and `DELETE` on every core table.

It must also cover attempted tournament-owner transfer, child reparenting to another owner's tournament or team slot, forged parent UUIDs, and match-result links that mix a match and team slot from different owners or different tournaments. It must test both `USING` and `WITH CHECK` denial behavior under a second account.

Those scenarios are deferred so v0.6.2 can establish the policy model and minimal single-owner/unauthenticated coverage without claiming completed cross-account security verification.

## 12. Future implementation scope and exclusions

The future `v0.6.2` implementation may add one new migration, ownership policies for the existing five tables, pgTAP RLS policy tests, and implementation evidence in this document.

It must not edit existing migrations or add Android, Room, Gradle, sync, WorkManager, OCR, storage, export, audit, idempotency, conflict handling, Edge Functions, RPCs, triggers, functions, new tables, or production Supabase changes.

## 13. Verification and safety requirements

Future implementation must inspect current Supabase CLI help and run/report:

```powershell
npx.cmd supabase --version
npx.cmd supabase migration list
npx.cmd supabase db reset
npx.cmd supabase test db
git diff --check
git status --short
git diff --name-status
git diff --stat
```

If Docker or local Supabase blocks reset or tests, the exact failure must be recorded and no blocked check may be reported as passed. No production command, credentials, local configuration, or destructive SQL is approved.

## 14. Implementation readiness decision

Ready to implement `v0.6.2` after this decision gate is reviewed, committed, merged, and the implementation branch is confirmed clean.

Required future implementation branch:

```text
feature/v0.6.2-rls-ownership-policies
```
