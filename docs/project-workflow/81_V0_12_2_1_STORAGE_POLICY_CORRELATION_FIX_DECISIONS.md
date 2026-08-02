# Phase 12 v0.12.2.1 — Screenshot Storage Policy Correlation Fix Decisions

## Status

**Blocking production-fix patch required before v0.12.2 Backend Tests can be completed.**

## Version

**Phase 12 — Quality Assurance and Security Validation**

**v0.12.2.1 — Screenshot Storage Policy Correlation Fix**

---

## 1. Purpose

Fix the production Supabase Storage RLS defect discovered while implementing v0.12.2 backend runtime tests.

The defect prevents a legitimate tournament owner from inserting an otherwise valid match screenshot object into the private `match-screenshots` bucket.

This patch exists only because the v0.12.2 runtime test exposed an actual production authorization-policy defect.

After this patch is merged, work returns immediately to:

`v0.12.2 — Backend Tests`

---

## 2. Discovery

The defect was discovered by the new runtime owner-success Storage test in:

`supabase/tests/07_v0_7_5_match_screenshot_storage.sql`

Expected behavior:

- authenticated tournament owner
- correct owner-scoped object path
- owned tournament
- owned match
- allowed screenshot filename
- private `match-screenshots` bucket

should permit the Storage object operation.

Actual behavior:

```text
ERROR: new row violates row-level security policy for table "objects"
```

The test fixture was verified to represent an intended owner-success operation.

Therefore the failure is not a test-fixture error.

---

## 3. Root Cause

The existing v0.7.5 Storage policies call expressions such as:

```sql
(storage.foldername(name))[4]
(storage.foldername(name))[6]
```

inside a nested ownership `exists (...)` query containing:

```sql
from public.tournaments as tournament_row
join public.matches as match_row ...
```

The `tournaments` relation also contains a `name` column.

Inside that nested SQL scope, the unqualified identifier `name` is not safely tied to the outer `storage.objects` row.

As a result, the parent ownership check can evaluate the tournament row's `name` instead of the Storage object's path.

That causes valid owner operations to fail RLS.

---

## 4. Affected Policies

The same correlation pattern exists in all three current owner Storage policies:

- `match_screenshots_insert_owner`
- `match_screenshots_select_owner`
- `match_screenshots_update_owner`

Therefore the patch must correct all three policies.

Do not fix only INSERT.

The SELECT and UPDATE policies contain the same unsafe nested path references and must be corrected in the same patch.

---

## 5. Core Decision

The original migration:

`supabase/migrations/20260729110000_v0_7_5_match_screenshot_storage.sql`

must **not** be edited.

It has already been part of the canonical migration history.

The fix must be delivered through exactly one **new Supabase migration**.

The migration must be created using the repository-pinned Supabase CLI rather than manually inventing a timestamped filename.

Use:

```powershell
npx --yes supabase@2.109.1 migration new v0_12_2_1_storage_policy_correlation_fix
```

If the environment requires `npx.cmd`, the equivalent command is acceptable.

The generated migration filename becomes the canonical production file for this patch.

---

## 6. Required Production Fix

The new migration must recreate the affected Storage policies so that every object-path lookup inside nested ownership checks explicitly references the **outer `storage.objects` object key**.

The implementation must eliminate ambiguous/unqualified references such as:

```sql
storage.foldername(name)
```

inside nested parent-ownership queries.

The fixed expression must explicitly bind to the Storage object's `name` column.

The precise SQL syntax must be validated against the local Supabase/PostgreSQL environment.

The required semantic contract is:

> Path parsing must always use the current `storage.objects.name` value and must never resolve to `public.tournaments.name`, `public.matches` columns, or another inner-query relation.

---

## 7. Policy Behavior Must Remain Otherwise Unchanged

The patch must preserve the existing Storage contract.

The bucket remains:

`match-screenshots`

The bucket remains private.

Allowed MIME types remain unchanged.

The path contract remains:

```text
users/{userId}/tournaments/{tournamentId}/matches/{matchId}/original.{png|jpg|webp}
```

Existing requirements remain unchanged:

- bucket ID must be `match-screenshots`
- first folder must be `users`
- second folder must equal authenticated user ID
- third folder must be `tournaments`
- fourth folder must equal the owned tournament ID
- fifth folder must be `matches`
- sixth folder must equal a match belonging to that tournament
- filename must be:
  - `original.png`
  - `original.jpg`
  - `original.webp`

- `owner_id` must remain either null or equal to authenticated user ID according to the current policy
- tournament ownership must still be verified
- match membership in that tournament must still be verified

Do not loosen any authorization requirement.

---

## 8. Security Boundary

This patch must not broaden Storage access.

The fix is specifically:

**valid owner operations currently denied → valid owner operations allowed**

It must not change:

- cross-account denial
- anonymous denial
- owner-path enforcement
- tournament ownership enforcement
- match ownership/association enforcement
- filename restrictions
- bucket privacy
- MIME restrictions

Cross-account and anonymous operations must remain denied.

---

## 9. Regression Test Strategy

The blocking defect must receive permanent regression coverage.

To avoid conflicting with the currently stashed v0.12.2 modifications to:

`supabase/tests/07_v0_7_5_match_screenshot_storage.sql`

the patch should create one new focused pgTAP regression file rather than modify that stashed file on this branch.

Create:

`supabase/tests/09_v0_12_2_1_match_screenshot_storage_policy_regression.sql`

The regression test should directly prove the corrected owner contract.

Minimum required coverage:

1. authenticated owner can insert a correctly scoped screenshot object
2. authenticated owner can select/read the correctly scoped object under the Storage SELECT policy
3. authenticated owner can perform an allowed update that exercises both UPDATE `USING` and `WITH CHECK`

Use a valid existing-style test fixture containing:

- auth user
- owned tournament
- owned match
- valid Storage object path

The test must use real local Storage RLS behavior.

Do not merely inspect policy definitions as text.

---

## 10. Negative Coverage

Do not duplicate the full v0.12.2 Storage RLS suite in this patch.

Cross-account and anonymous runtime coverage remains part of:

`v0.12.2 — Backend Tests`

The v0.12.2 work is already safely stashed and will resume after this patch.

The v0.12.2 file:

`supabase/tests/07_v0_7_5_match_screenshot_storage.sql`

must remain untouched on the v0.12.2.1 branch.

---

## 11. Approved Implementation Boundary

### Production file

Exactly one new migration generated by:

```text
supabase migration new v0_12_2_1_storage_policy_correlation_fix
```

The resulting timestamped migration file is approved.

### New test file

```text
supabase/tests/09_v0_12_2_1_match_screenshot_storage_policy_regression.sql
```

### Existing files to modify

```text
None.
```

### Existing migrations to modify

```text
None.
```

---

## 12. Out of Scope

Do not modify:

- `20260729110000_v0_7_5_match_screenshot_storage.sql`
- other Supabase migrations
- screenshot metadata schema
- Android screenshot handling
- Room
- synchronization RPCs
- finalization RPCs
- correction RPCs
- export backend
- Edge Functions
- authentication behavior
- bucket structure
- MIME allow-list
- other Storage buckets
- Gradle
- CI
- production secrets
- remote Supabase configuration

Do not perform unrelated Storage policy cleanup.

---

## 13. No Remote Supabase Changes

Implementation and verification must remain local.

Do not:

- link a remote project
- run remote SQL
- push migrations
- deploy functions
- modify production database state
- modify secrets
- test using production data

---

## 14. Local Verification

After implementation, required local verification is:

```powershell
npx --yes supabase@2.109.1 db reset
npx --yes supabase@2.109.1 test db supabase/tests/09_v0_12_2_1_match_screenshot_storage_policy_regression.sql
npx --yes supabase@2.109.1 test db
git diff --check
```

Docker Desktop/local Supabase services must be available.

The complete pgTAP suite must pass with the patch branch's tests.

The stashed v0.12.2 test work is not part of this branch and is not expected to run here.

---

## 15. Production Acceptance Criteria

The patch is accepted when:

1. a new migration is created through Supabase CLI
2. INSERT owner policy uses the outer Storage object key unambiguously
3. SELECT owner policy uses the outer Storage object key unambiguously
4. UPDATE `USING` uses the outer Storage object key unambiguously
5. UPDATE `WITH CHECK` uses the outer Storage object key unambiguously
6. all existing authorization restrictions remain unchanged
7. legitimate owner INSERT succeeds
8. legitimate owner SELECT succeeds
9. legitimate owner UPDATE succeeds
10. focused regression pgTAP passes
11. complete existing pgTAP suite passes
12. `git diff --check` passes
13. no existing migration is modified
14. no unrelated production file is modified

---

## 16. Relationship to v0.12.2

The current v0.12.2 backend-test work is stored safely in:

```text
stash@{0}: wip v0.12.2 backend tests before storage policy fix
```

Do not apply or drop that stash while working on v0.12.2.1.

After this patch is:

1. implemented
2. verified locally
3. committed
4. merged through its own PR
5. and `main` is synchronized

return to:

`feature/v0.12.2-backend-tests`

Update that branch with the new `main`, then restore the saved v0.12.2 test work.

The Storage runtime test that originally exposed this defect must then be rerun.

The v0.12.2 backend-test version remains incomplete until its full local pgTAP suite passes.

---

## 17. Completion Criteria

v0.12.2.1 is complete when:

1. this decision document is merged
2. implementation begins from synchronized `main`
3. exactly one new migration is added
4. exactly one new focused regression pgTAP file is added
5. all three affected Storage policies are corrected
6. no existing migration is edited
7. owner INSERT/SELECT/UPDATE regression tests pass
8. local database reset succeeds
9. complete patch-branch pgTAP suite passes
10. `git diff --check` passes
11. implementation is merged through a PR
12. local `main` is synchronized
13. working tree is clean
14. work returns immediately to v0.12.2

---

## 18. Decision Summary

Approved patch:

```text
v0.12.2.1 — Screenshot Storage Policy Correlation Fix

1 new Supabase migration
1 new focused pgTAP regression file
0 existing files modified

Fix INSERT policy correlation
Fix SELECT policy correlation
Fix UPDATE USING correlation
Fix UPDATE WITH CHECK correlation

Preserve all existing authorization rules
No remote deployment
No unrelated backend changes
```

After this decision document is merged, implementation may proceed on:

`fix/v0.12.2.1-storage-policy-correlation`
