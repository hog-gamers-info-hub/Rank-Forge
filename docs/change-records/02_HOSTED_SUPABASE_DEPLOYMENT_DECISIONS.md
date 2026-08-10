# CR-002 — Hosted Supabase Deployment Decisions

## Status

**Decisions Approved — Deployment Pending**

This change record authorizes preparation for deployment of the existing, versioned Rank-Forge Supabase backend to the approved hosted Supabase project.

Actual hosted deployment must not begin until every precondition and dry-run gate in this document passes.

---

## 1. Purpose

Rank-Forge currently has a complete, versioned Supabase backend stored in GitHub under:

```text
supabase/migrations/
supabase/functions/
supabase/config.toml
```

The approved hosted Rank-Forge Supabase project is healthy but has not yet received the Rank-Forge application database migration chain.

The purpose of CR-002 is to deploy the existing repository-controlled backend to the correct hosted project while preserving:

* Supabase migration history;
* existing Auth users and identities;
* ownership-based RLS;
* Storage access controls;
* revision-safe cloud synchronization;
* finalized-match protection;
* correction audit history;
* export idempotency;
* existing Google Sign-In configuration.

---

## 2. Approved Hosted Project

The deployment target was confirmed directly from the connected Supabase project rather than from a remembered project reference.

Approved project:

```text
Project name: Rank-Forge
Project reference: jfllzadfduzktczzdvil
Region: ap-south-1
Status at audit: ACTIVE_HEALTHY
API URL: https://jfllzadfduzktczzdvil.supabase.co
Postgres major version: 17
```

No other Supabase project is approved for this deployment.

Before any write operation, the CLI link target must again be confirmed as:

```text
jfllzadfduzktczzdvil
```

If the linked project reference differs, deployment must stop.

---

## 3. Repository Baseline

The audited deployable-backend source baseline is:

```text
Branch: main
Audited backend commit: 28334448b0627e4e4eb7b0d91bc129036d6ebe37
```

The 13-migration chain, Storage configuration, Edge Function implementation, Auth integration, and Android backend configuration approved by this decision were audited against that exact commit.

The CR-002 decision documentation itself is expected to advance `main` when its documentation PR is merged. Therefore, the final deployment execution `HEAD` is not required to equal the audited backend commit above.

After the CR-002 documentation PR is merged, the live repository at:

```text
D:\Projects\Rank-Forge
```

must satisfy all of the following:

```text
current branch = main
working tree = clean
HEAD = origin/main
28334448b0627e4e4eb7b0d91bc129036d6ebe37 is an ancestor of HEAD
```

The diff from the audited backend baseline to the deployment execution `HEAD` must contain only the approved documentation changes:

docs/change-records/00_CHANGE_REGISTER.md
    deleted

docs/change-records/02_HOSTED_SUPABASE_DEPLOYMENT_DECISIONS.md
    added/updated

The synchronized post-merge `main` SHA must then be captured as the **CR-002 deployment execution baseline**.

If any other file has changed since the audited backend baseline, especially anything under `supabase/` or Android/backend configuration, deployment must stop and CR-002 must be re-audited before proceeding.


---

## 4. Approved Migration Chain

The approved deployment consists of the following 13 migration files in exact timestamp order:

```text
01  20260727163228_v0_6_1_core_backend_schema.sql
02  20260727165522_v0_6_1_1_schema_hardening.sql
03  20260727172504_v0_6_2_rls_ownership_policies.sql
04  20260728120000_v0_6_7_revision_safe_writes.sql
05  20260728150000_v0_6_8_protected_match_finalization.sql
06  20260728160000_v0_6_8_1_protected_corrections_audit.sql
07  20260729110000_v0_7_5_match_screenshot_storage.sql
08  20260729120000_v0_7_6_match_screenshot_metadata.sql
09  20260801120000_v0_10_7_export_retry_idempotency.sql
10  20260801140000_v0_10_8_export_verification.sql
11  20260802060704_v0_12_2_1_storage_policy_correlation_fix.sql
12  20260803061255_v0_6_9_revision_safe_roster_sync_replacement.sql
13  20260807110000_v0_7_10_match_result_screenshot_assets.sql
```

Historical migration files must not be:

* edited;
* renamed;
* reordered;
* squashed;
* deleted;
* manually marked as applied;
* copied individually into the Dashboard SQL Editor.

The chain must be deployed through the Supabase CLI so that normal migration history is preserved.

---

## 5. Hosted Baseline Before Deployment

The read-only audit established the following hosted state before CR-002 deployment:

```text
Rank-Forge application migrations recorded remotely: 0
public Rank-Forge application tables: 0
Rank-Forge Storage buckets: 0
deployed Edge Functions: 0
```

The Supabase project itself is not globally empty.

Auth is already active and contains both email and Google identities.

Existing Auth users and identities are production data and must be preserved.

No database reset, project recreation, destructive rollback, or Auth clearing is approved.

---

## 6. Expected Database Result

After all 13 migrations have applied successfully, the expected Rank-Forge application tables are:

```text
public.tournaments
public.tournament_team_slots
public.players
public.matches
public.match_results
public.match_correction_audit_entries
public.match_screenshot_metadata
public.export_operations
public.match_result_screenshot_assets
```

Expected extension dependency:

```text
pgcrypto
```

The migrations use existing Supabase-managed schemas and facilities:

```text
auth
storage
public
```

No additional custom application schema is required.

---

## 7. Expected Backend Functions

The approved migration chain establishes the following application RPC functions:

```text
write_tournament_snapshot
write_match_snapshot
finalize_match_snapshot
correct_finalized_match_snapshot
claim_export_operation
mark_export_operation_write_started
complete_export_operation_success
mark_export_operation_retryable_failure
mark_export_operation_outcome_uncertain
resolve_export_operation_verified_success
replace_tournament_roster_snapshot
```

The repository's approved security design must remain unchanged.

Revision-safe ordinary writes use ownership-scoped authorization.

Protected finalization and correction paths retain their narrowly scoped server-authoritative behavior.

No service-role bypass is approved for Android clients.

---

## 8. RLS and Authorization Boundary

All exposed Rank-Forge application tables must have Row Level Security enabled after deployment.

Expected authorization principles:

* application data is owner-scoped;
* authenticated user A must not read or mutate authenticated user B's data;
* anonymous users must not obtain Rank-Forge application data;
* finalized matches must not become directly mutable;
* correction operations must preserve their protected audit path;
* export operation state must remain owner-scoped;
* screenshot metadata and screenshot assets must remain owner/correlation scoped.

The existence of SQL table grants does not replace RLS authorization.

Verification must test effective access using separate authenticated identities rather than inspecting policy names alone.

No policy may be weakened merely to make deployment or testing easier.

---

## 9. Storage Configuration

Bucket creation is already versioned in the repository migration history.

No manual Dashboard bucket creation is approved.

Expected private buckets:

```text
match-screenshots
ocr-screenshots
```

Approved MIME types:

```text
image/png
image/jpeg
image/webp
```

Both buckets must remain private.

Expected `match-screenshots` Storage policies:

```text
match_screenshots_insert_owner
match_screenshots_select_owner
match_screenshots_update_owner
```

Expected `ocr-screenshots` Storage policies:

```text
ocr_screenshots_insert_owner
ocr_screenshots_select_owner
ocr_screenshots_update_owner
```

No client Storage DELETE policy is approved.

Storage authorization must correlate:

* authenticated owner;
* user path;
* tournament;
* match;
* expected screenshot location;
* approved filename/extension.

The v0.12.2.1 Storage correlation fix must remain part of the migration history and must not be bypassed.

---

## 10. Match Result Screenshot Assets

The final migration adds the dedicated match-result screenshot asset model.

Each match can persist separate authoritative screenshot roles:

```text
MATCH_RESULT_UPPER
MATCH_RESULT_LOWER
```

Expected identity boundary:

```text
primary key = (match_id, screenshot_role)
```

The associated Storage bucket is:

```text
ocr-screenshots
```

The storage path contract must remain owner/tournament/match/role correlated.

No deployment change may merge the two screenshot roles or weaken their independent identity.

---

## 11. Edge Function Deployment

The repository contains one deployable Edge Function:

```text
google-sheets-export
```

Shared files under:

```text
supabase/functions/_shared/
```

are dependencies and are not deployed as independent functions.

The approved function configuration is:

```text
verify_jwt = true
```

JWT verification must not be disabled.

The function also validates the requesting Supabase user using the supplied bearer token.

No service-role key is required by the approved implementation.

---

## 12. Required Edge Function Environment

The Edge Function depends on Supabase-provided runtime configuration plus three user-managed Google Sheets values.

Expected Supabase runtime values:

```text
SUPABASE_URL
SUPABASE_ANON_KEY
```

Expected user-managed secret names:

```text
GOOGLE_SHEETS_CLIENT_EMAIL
GOOGLE_SHEETS_PRIVATE_KEY
GOOGLE_SHEETS_SPREADSHEET_ID
```

Before function deployment, only the existence of these secret names may be inspected.

Their values must not be printed into:

* terminal transcripts;
* Git;
* documentation;
* screenshots;
* Android source;
* ChatGPT output;
* PR descriptions;
* logs.

Secrets must be configured only through Supabase's approved secret-management mechanism.

---

## 13. Google Sheets External Requirements

The `google-sheets-export` function uses a Google service account and the Google Sheets API.

Required OAuth scope:

```text
https://www.googleapis.com/auth/spreadsheets
```

The configured service account must have access to the target spreadsheet.

The configured spreadsheet must contain the repository-required worksheet structure, including:

```text
Match Results
Tournament Standings
```

The existing header contracts must match exactly before export is considered operational.

The function must not silently create or reinterpret an incompatible spreadsheet schema.

The approved non-destructive connection operation is:

```text
verify_connection
```

A successful connection check is required before live export verification.

---

## 14. Hosted Auth Configuration

Database migrations do not configure Google OAuth provider settings.

Existing hosted Supabase Auth configuration must be preserved.

CR-001 already established the Google Sign-In architecture.

CR-002 verification must confirm that hosted Auth still supports:

```text
email/password
Google Sign-In
```

Google Sign-In must continue to use the approved Android callback:

```text
com.hoggamers.rankforge://auth-callback
```

Required hosted Google Auth dependencies include:

* Google provider enabled;
* correct Google Web Client ID;
* Google Web Client Secret configured;
* Supabase OAuth callback registered in Google;
* Android callback registered in Supabase Redirect URLs;
* expected Site URL/redirect behavior.

No OAuth secret should be replaced unless an actual configuration defect is identified and separately approved.

---

## 15. Android Hosted Configuration

Android must not contain:

* Supabase service-role keys;
* database passwords;
* Google OAuth client secrets;
* Google service-account private keys;
* Edge Function private secrets.

The Android build resolves hosted Supabase configuration through approved environment/build configuration.

Before hosted beta verification, the resolved Supabase URL must be confirmed as:

```text
https://jfllzadfduzktczzdvil.supabase.co
```

The application must not be accidentally pointed at:

* `127.0.0.1`;
* localhost;
* a stale local Supabase environment;
* another Supabase project.

Only a client-safe publishable/anon key may be supplied to Android.

---

## 16. Security and Data Boundaries

The following are strict CR-002 boundaries.

### Secrets

Never expose:

```text
Supabase database password
Supabase service-role key
Google OAuth Client Secret
Google service-account private key
Edge Function secrets
```

### Existing hosted data

Existing Supabase Auth users and identities must be preserved.

No operation may intentionally delete or reset:

```text
auth.users
auth.identities
hosted project configuration
existing provider configuration
```

### Migration history

GitHub migration history is authoritative.

Applied migrations are immutable history.

Any correction after production deployment must normally be implemented as a new forward migration.

### Application data

No migration or verification command may bypass RLS using elevated credentials merely to demonstrate that the Android workflow works.

End-to-end authorization must be verified through normal authenticated user behavior.

---

## 17. Preconditions

Every item below must pass before `supabase db push`.

### Repository

* [ ] local path is `D:\Projects\Rank-Forge`;
* [ ] current branch is `main`;
* [ ] working tree is clean;
* [ ] `git pull --ff-only origin main` succeeds;
* [ ] local `HEAD` equals `origin/main`;
* [ ] audited backend baseline remains `28334448b0627e4e4eb7b0d91bc129036d6ebe37`;
* [ ] the audited backend baseline is an ancestor of current `HEAD`;
* [ ] the diff from the audited backend baseline to current `HEAD` contains only the two CR-002 documentation files;
* [ ] current synchronized `HEAD` is recorded as the **CR-002 deployment execution baseline**.

the diff from the audited backend baseline to current HEAD contains only the approved CR-002 documentation delta: removal of `00_CHANGE_REGISTER.md` and addition/update of `02_HOSTED_SUPABASE_DEPLOYMENT_DECISIONS.md`


### Supabase CLI

The installed CLI must be inspected before deployment:

```powershell
supabase --version
supabase db push --help
supabase migration list --help
supabase functions deploy --help
supabase secrets --help
```

No deployment flag may be assumed from memory.

### Hosted target

The authenticated CLI target must be confirmed as:

```text
jfllzadfduzktczzdvil
```

### Remote state

Before SQL deployment:

* [ ] remote migration list contains no unexpected migration;
* [ ] no unexpected Rank-Forge application tables have appeared;
* [ ] no unexpected conflicting bucket exists;
* [ ] no unexpected backend state invalidates the original audit.

### Edge Function prerequisites

Before function deployment:

* [ ] required Google secret names exist;
* [ ] no secret value is printed;
* [ ] service account has spreadsheet access;
* [ ] expected spreadsheet exists;
* [ ] expected worksheet headers exist.

---

## 18. Approved Deployment Procedure

### Step 1 — synchronize repository

Run from the local Rank-Forge repository:

```powershell
Set-Location D:\Projects\Rank-Forge

git switch main
git pull --ff-only origin main
git status --short
git rev-parse HEAD
git rev-parse origin/main
git merge-base --is-ancestor 28334448b0627e4e4eb7b0d91bc129036d6ebe37 HEAD
git diff --name-only 28334448b0627e4e4eb7b0d91bc129036d6ebe37..HEAD
```

Required result:

```text
branch = main
working tree = clean
HEAD = origin/main
audited backend baseline is an ancestor of HEAD
```

The diff from the audited backend baseline must contain exactly:

```text
docs/change-records/00_CHANGE_REGISTER.md
docs/change-records/02_HOSTED_SUPABASE_DEPLOYMENT_DECISIONS.md
```

The synchronized post-merge `HEAD` SHA must then be recorded as the **CR-002 deployment execution baseline**.

If any additional file appears in the diff, deployment must stop and CR-002 must be re-audited before proceeding.


### Step 2 — inspect CLI

```powershell
supabase --version
supabase db push --help
supabase migration list --help
supabase functions deploy --help
supabase secrets --help
```

Use only flags confirmed by the installed CLI.

### Step 3 — authenticate and link

```powershell
supabase login
supabase link --project-ref jfllzadfduzktczzdvil
```

After linking, confirm the exact project again before any write.

### Step 4 — inspect remote migration history

```powershell
supabase migration list
```

Expected pre-deployment result:

* repository contains the approved 13 migrations;
* remote contains no applied Rank-Forge application migration;
* no unexpected remote-only history exists.

If the result differs from the audit, stop.

### Step 5 — migration dry-run

```powershell
supabase db push --dry-run
```

The dry-run must list exactly these 13 migrations:

```text
20260727163228
20260727165522
20260727172504
20260728120000
20260728150000
20260728160000
20260729110000
20260729120000
20260801120000
20260801140000
20260802060704
20260803061255
20260807110000
```

No unexpected migration may appear.

No expected migration may be missing.

No history override is approved.

After the dry-run, stop and review the output before executing the real push.

### Step 6 — SQL migration deployment

Only after the dry-run is explicitly accepted:

```powershell
supabase db push
```

If any migration fails:

**stop immediately.**

Do not rerun blindly.

### Step 7 — verify migration history

```powershell
supabase migration list
```

All 13 expected timestamps must now be recorded remotely.

---

## 19. SQL Post-Deployment Verification

Immediately after successful SQL deployment verify:

### Schema

* [ ] all 9 Rank-Forge application tables exist;
* [ ] `pgcrypto` is available;
* [ ] expected PK constraints exist;
* [ ] expected UNIQUE constraints exist;
* [ ] expected foreign keys exist;
* [ ] expected CHECK constraints exist;
* [ ] expected indexes exist;
* [ ] expected 11 RPC functions exist;
* [ ] no unexpected application trigger was introduced.

### RLS

* [ ] RLS enabled on every exposed Rank-Forge application table;
* [ ] owner policies exist;
* [ ] authenticated cross-account reads fail;
* [ ] authenticated cross-account writes fail;
* [ ] anonymous application-data reads fail;
* [ ] finalized-match mutation protection remains intact.

### Storage

* [ ] `match-screenshots` exists;
* [ ] `match-screenshots` is private;
* [ ] `ocr-screenshots` exists;
* [ ] `ocr-screenshots` is private;
* [ ] MIME restrictions are correct;
* [ ] all expected Storage policies exist;
* [ ] owner-correlated paths work;
* [ ] cross-user paths fail;
* [ ] wrong tournament/match correlation fails;
* [ ] malformed screenshot paths fail.

### Security Advisor

Run/review the hosted Security Advisor after deployment.

Critical findings block CR-002 completion.

Existing warning noted during audit:

```text
Leaked Password Protection Disabled
```

This warning must be recorded and reviewed.

It is not automatically a migration-chain deployment blocker, but it must not be silently ignored.

---

## 20. Edge Function Deployment Procedure

Edge Functions are deployed separately from database migrations.

Before deploying:

```powershell
supabase secrets list --project-ref jfllzadfduzktczzdvil
```

Verify only that the required secret names exist.

Do not output secret values.

Deploy:

```powershell
supabase functions deploy google-sheets-export --project-ref jfllzadfduzktczzdvil
```

The deployed function must retain:

```text
verify_jwt = true
```

After deployment, verify the hosted function inventory and configuration.

---

## 21. Edge Function Verification

The first function request must be authorized and non-destructive.

Verify:

```text
verify_connection
```

Expected behavior:

* valid authenticated request succeeds;
* invalid/missing JWT is rejected;
* Supabase user validation succeeds;
* Google service-account token exchange succeeds;
* spreadsheet access succeeds;
* no spreadsheet rows are modified by the connection check.

Then verify export behavior with approved test data.

Required coverage:

* match export authorization;
* standings export authorization;
* official cloud data validation;
* exact worksheet schema validation;
* successful 12-row append;
* retry/idempotent replay;
* in-progress protection;
* uncertain-outcome handling;
* verified reconciliation.

Do not perform uncontrolled repeated live exports during verification.

---

## 22. Android Hosted Verification

After SQL, Storage, Auth and Edge Function configuration are confirmed, run the approved Android beta verification against the hosted Rank-Forge project.

### Authentication

* [ ] email/password sign-up where applicable;
* [ ] email/password sign-in;
* [ ] Google Sign-In;
* [ ] Google cancellation/back;
* [ ] sign-out;
* [ ] session restoration;
* [ ] repeated Google Sign-In does not create duplicate users/identities;
* [ ] same verified-email account retains expected ownership access.

### Tournament and roster

* [ ] create/upload tournament;
* [ ] create/confirm roster;
* [ ] owner can restore own cloud data;
* [ ] second authenticated user cannot access it;
* [ ] revision-safe roster replacement works;
* [ ] roster replacement is blocked when match state makes replacement unsafe.

### Sync

* [ ] create local data offline;
* [ ] queue persists;
* [ ] reconnect;
* [ ] queued operation retries;
* [ ] no duplicate server data;
* [ ] revision conflict behavior remains deterministic.

### Screenshot backend

* [ ] screenshot metadata upload;
* [ ] Storage upload;
* [ ] restoration;
* [ ] unauthorized path rejected;
* [ ] `MATCH_RESULT_UPPER` upload;
* [ ] `MATCH_RESULT_LOWER` upload;
* [ ] independent role persistence;
* [ ] crop metadata persistence/restoration.

### Finalization

* [ ] valid finalization succeeds;
* [ ] direct finalized-data mutation is rejected;
* [ ] stale revision fails safely;
* [ ] repeated finalization does not corrupt results.

### Correction audit

* [ ] protected correction succeeds for owner;
* [ ] correction increments revision;
* [ ] audit row records previous and corrected values;
* [ ] unauthorized correction fails;
* [ ] finalized data remains protected outside correction RPC.

### Export

If Google Sheets export is configured:

* [ ] authenticated connection check;
* [ ] authorized match export;
* [ ] authorized standings export;
* [ ] duplicate retry does not duplicate spreadsheet rows;
* [ ] invalid ownership is rejected.

---

## 23. Failure Stop Conditions

Deployment must stop immediately if any of the following occurs:

* local repository is not clean;
* current branch is not `main`;
* `HEAD` differs from `origin/main`;
* current `main` differs from the approved baseline;
* CLI target project differs from `jfllzadfduzktczzdvil`;
* migration list contains an unexpected remote migration;
* dry-run does not show exactly the approved migration chain;
* migration order differs;
* an expected migration is omitted;
* an unknown migration is added;
* a migration fails;
* unexpected application tables or buckets appear before deployment;
* required Google secret names are missing;
* Edge Function JWT verification would need to be disabled;
* a critical Security Advisor issue is found;
* post-deployment RLS allows cross-account access;
* Storage correlation allows cross-account/wrong-resource access;
* protected finalized data becomes directly mutable.

A failure is evidence to investigate, not authorization to improvise.

---

## 24. Prohibited Deployment Actions

CR-002 does not authorize:

```text
supabase db reset against hosted production
supabase db push --include-all
manual migration repair
manual migration history insertion
manual SQL Editor execution of the migration chain
editing historical migration files
renaming migration timestamps
squashing migrations
dropping hosted application resources to make a retry work
deleting Auth users
resetting the hosted project
disabling RLS
disabling verify_jwt
placing secrets in Android/Git/docs
```

Any exceptional use of a migration-history repair or destructive recovery mechanism requires a separate audit and explicit user approval.

---

## 25. Rollback and Recovery Plan

Schema migrations are not assumed to be safely reversible.

The default CR-002 recovery strategy is:

```text
forward correction
```

rather than destructive rollback.

If `supabase db push` fails:

1. stop immediately;
2. preserve the complete sanitized error output;
3. record the migration timestamp that failed;
4. run a read-only migration-history check;
5. identify which migrations completed;
6. inspect the actual objects created by the failed migration;
7. determine the root cause;
8. do not modify any migration already recorded remotely;
9. document the corrective design;
10. obtain explicit approval;
11. implement a new forward corrective migration if required;
12. test locally;
13. resume deployment only after the hosted state is understood.

Do not assume that rerunning a failed command is safe.

Do not delete objects solely to force a migration to rerun.

Migration-history repair may only be considered if the migration ledger is demonstrably inconsistent with the actual database state and after explicit approval.

Existing Auth data must not be included in schema rollback operations.

---

## 26. Acceptance Criteria

CR-002 is complete only when all of the following are true:

### Repository

* exact approved baseline recorded;
* deployment executed from synchronized clean `main`;
* no source migration was modified to enable deployment.

### Database

* all 13 expected migrations are recorded remotely;
* all expected Rank-Forge tables/functions/indexes/constraints exist;
* no unexpected migration-history entry exists.

### Authorization

* RLS enabled across the application schema;
* owner access succeeds;
* cross-account access fails;
* anonymous application-data access fails.

### Storage

* both approved buckets exist and remain private;
* path/correlation policies pass;
* screenshot upload and restoration succeed;
* cross-owner Storage access fails.

### Auth

* email/password remains operational;
* Google Sign-In remains operational;
* account identity behavior remains correct;
* sign-out/session restoration work.

### Sync and data protection

* tournament/roster/match synchronization succeeds;
* offline queue recovery succeeds;
* revision conflicts remain safe;
* protected finalization remains enforced;
* correction audit remains enforced.

### Edge Function

If Google Sheets export is enabled for this hosted deployment:

* `google-sheets-export` is deployed;
* JWT verification remains enabled;
* required secret names are configured;
* `verify_connection` passes;
* authorized exports pass;
* idempotency/reconciliation behavior passes.

### Security

* Security Advisor reviewed after deployment;
* no unresolved critical finding;
* known warnings documented.

---

## 27. Documentation Completion

After all deployment and verification evidence passes:

1. update this decision record status from **Decisions Approved — Deployment Pending** to **Complete**;
2. record:

   * deployment execution baseline commit;
   * hosted project reference;
   * applied migration range;
   * Storage buckets;
   * deployed Edge Functions;
   * verification results;
   * Security Advisor result;
   * any approved deferral;
3. commit the completion documentation separately;
4. push the documentation branch;
5. open a documentation PR;
6. review and merge it;
7. return local `main` to a clean synchronized state.
---

## 28. Approved Decision

CR-002 is approved to proceed using the repository-controlled Supabase deployment workflow defined above.

The next execution stage is:

```text
synchronize clean main
→ verify audited-backend delta
→ capture deployment execution baseline
→ inspect installed Supabase CLI
→ confirm hosted project
→ inspect migration list
→ run db push --dry-run
→ stop and review dry-run
```

Actual `supabase db push` is permitted only after every precondition passes and the dry-run exactly matches the approved 13-migration chain.
