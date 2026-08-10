# CR-002 — Hosted Supabase Deployment Closure

## Status

**CLOSED — Hosted Supabase Deployment Verified**

Closure date:

```text
2026-08-10
```

CR-002 successfully deployed and verified the existing Rank-Forge Supabase backend against the approved hosted project.

No historical migration was edited, renamed, reordered, squashed, repaired, or manually marked as applied.

No hosted database reset was performed.

Existing hosted Auth users and identities were preserved.

---

## 1. Purpose

CR-002 existed to move the repository-controlled Rank-Forge Supabase backend from the local development environment to the approved hosted Supabase project and prove that the deployed system operates correctly through normal authenticated application boundaries.

The deployment covered:

- database migrations;
- Row Level Security;
- ownership authorization;
- Storage buckets and Storage RLS;
- Supabase Auth interoperability;
- Android hosted configuration;
- cloud tournament restoration;
- cloud match restoration;
- Edge Function deployment;
- Google Sheets integration;
- export idempotency and verification.

---

## 2. Approved Hosted Project

Deployment target:

```text
Project name: Rank-Forge
Project reference: jfllzadfduzktczzdvil
Region: ap-south-1
API URL: https://jfllzadfduzktczzdvil.supabase.co
Postgres major version: 17
```

The project was confirmed healthy during CR-002 execution.

No other Supabase project was used.

---

## 3. Repository Baselines

Audited deployable-backend source baseline:

```text
28334448b0627e4e4eb7b0d91bc129036d6ebe37
```

Deployment execution baseline after the CR-002 decisions documentation merge:

```text
8eb07cf682333de0efa26d54f8b6cb3fb2798ca2
```

The audited backend baseline remained an ancestor of the deployment execution baseline.

The only changes between those baselines were the approved CR-002 documentation changes.

No deployable Supabase backend source, Android backend configuration, or migration file changed between the backend audit and deployment execution.

---

## 4. Migration Deployment Result

The approved 13-migration chain was deployed through the Supabase CLI in exact timestamp order.

The deployed migration chain is:

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

Verification after deployment confirmed:

```text
Local migration count  : 13
Remote migration count : 13
Local/remote history   : matched
```

No migration repair command was required.

---

## 5. Hosted Database Verification

The deployed application schema was verified directly.

Expected application tables were present:

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

Verified result:

```text
Application tables    : 9
RLS-enabled tables    : 9
Application policies  : 30
```

The required PostgreSQL extension was present:

```text
pgcrypto
```

No unexpected public application trigger was introduced.

---

## 6. RPC Verification

The expected application RPC set was present:

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

Verified count:

```text
11 RPC functions
```

Runtime verification exercised normal authenticated RPC behavior, including:

- tournament snapshot upload;
- match snapshot creation;
- protected match finalization;
- export operation state transitions.

---

## 7. RLS and Ownership Verification

Application ownership isolation was tested using two distinct real hosted Auth identities.

For a tournament owned by the primary test identity:

```text
Owner SELECT       : allowed
Owner UPDATE       : allowed
Other-user SELECT  : blocked
Other-user UPDATE  : blocked
```

The cross-account test was performed transactionally and rolled back.

No persistent unauthorized mutation occurred.

This verifies that the deployed application-table RLS boundary isolates different authenticated users.

---

## 8. Storage Deployment Verification

The expected private Storage buckets were created through migration history:

```text
match-screenshots
ocr-screenshots
```

Both were verified as private.

Approved MIME types remained:

```text
image/png
image/jpeg
image/webp
```

Expected Storage policies were present:

```text
match_screenshots_insert_owner
match_screenshots_select_owner
match_screenshots_update_owner

ocr_screenshots_insert_owner
ocr_screenshots_select_owner
ocr_screenshots_update_owner
```

Verified Storage policy count:

```text
6
```

Client DELETE policy count:

```text
0
```

Runtime Storage verification confirmed:

- authenticated owner upload succeeds;
- authenticated owner private download succeeds;
- exact downloaded bytes match uploaded bytes;
- same-path upsert persists replacement content;
- cross-account Storage metadata SELECT is blocked;
- forged user-namespace writes are blocked;
- tournament/match/path correlation remains enforced;
- filename and MIME restrictions remain valid.

Final controlled Storage evidence:

```text
match-screenshots objects : 1
ocr-screenshots objects   : 1

unauthorized/misaligned objects : 0
```

The final match-screenshot test object was restored to a valid PNG after a temporary test-quality issue involving arbitrary replacement bytes.

---

## 9. Edge Function Deployment

The repository Edge Function:

```text
google-sheets-export
```

was deployed successfully.

Hosted deployment verification:

```text
Status     : ACTIVE
Version    : 1
verify_jwt : true
```

Shared files under:

```text
supabase/functions/_shared/
```

remained dependencies and were not deployed as separate functions.

Unauthenticated invocation was tested and correctly rejected:

```text
HTTP 401
```

Authenticated:

```text
verify_connection
```

returned successful spreadsheet-access verification.

This proved:

```text
Rank Forge user
    -> Supabase Auth
    -> hosted Edge Function
    -> Google service account
    -> target spreadsheet metadata
```

---

## 10. Google Service Account Configuration

The existing Rank-Forge Google Sheets service account was reused.

A valid service-account JSON key was temporarily downloaded locally to configure hosted secrets.

Hosted secret names configured:

```text
GOOGLE_SHEETS_CLIENT_EMAIL
GOOGLE_SHEETS_PRIVATE_KEY
GOOGLE_SHEETS_SPREADSHEET_ID
```

Secret values were not committed to Git.

Secret values were not placed into Android source.

Secret values were not written into this closure record.

After successful hosted Google Sheets write verification, the downloaded local service-account JSON file was removed.

Deleting the local JSON file does not revoke the Google Cloud service-account key.

The active key remains required by the hosted Supabase Edge Function until an intentional key rotation is performed.

---

## 11. Android Hosted Configuration Verification

Android was configured to use the hosted Supabase project through the approved client-safe configuration keys:

```text
RANK_FORGE_SUPABASE_URL
RANK_FORGE_SUPABASE_PUBLISHABLE_KEY
```

The hosted URL was verified.

No service-role key, database password, OAuth client secret, service-account private key, or Edge Function secret was placed in Android configuration.

The local configuration backup was stored outside the repository.

The repository remained clean.

A debug APK was built and installed on the physical Android test device.

The application launched without crashing.

---

## 12. Hosted Auth Verification

Physical-device hosted authentication was verified.

Test sequence:

```text
signed out
-> sign in to hosted Supabase
-> force-stop app
-> relaunch app
-> session restored
```

Result:

```text
Hosted sign-in          : PASS
Hosted session restore  : PASS
Post-restart auth state : PASS
```

Existing Google Sign-In configuration remained intact.

Approved Android callback remained:

```text
com.hoggamers.rankforge://auth-callback
```

---

## 13. Tournament Cloud Upload and Restoration

The Rank-Forge architecture remains local-first.

Tournament creation stores local data first.

Cloud upload is an explicit authenticated operation.

Controlled tournament upload was verified through the application.

Initial upload using duplicate/default team names correctly exposed a validation mismatch and failed atomically.

After assigning 12 unique team names, upload succeeded.

Hosted verification confirmed:

```text
Tournament count       : 1
Team slots             : 12
Distinct team names    : 12
Slot range             : 1-12
```

The cloud tournament was then restored through the Android application.

Hosted API logs confirmed authenticated reads for:

- tournament;
- tournament team slots;
- players.

The restored tournament persisted in local Room after an Android process restart.

All 12 team slots remained present.

---

## 14. Match Cloud Restoration

A controlled hosted draft match was created for CR-002 verification.

Android match restoration was then exercised through the normal application workflow.

Hosted API logs confirmed authenticated reads for:

```text
tournaments
matches
match_results
```

The controlled match became visible locally.

After process restart, the restored match remained present in Room.

Result:

```text
Android hosted match restoration : PASS
Room restart persistence          : PASS
```

A minor UI observation was recorded: the expected restoration success status text was not visibly shown during one test, although the match itself restored successfully and the hosted API logs confirmed the read path.

This was not a backend deployment blocker.

---

## 15. Controlled Match Finalization

The controlled hosted draft match was finalized through the authenticated protected finalization RPC.

Before finalization:

```text
Tournament revision : 2
Match status         : draft
Match results        : 0
```

A deterministic 12-result payload was prepared:

```text
12 distinct team slots
12 distinct placements
placements 1-12
0 kills for all teams
review_status = confirmed
```

Finalization succeeded:

```text
outcome  : success
revision : 3
```

Direct hosted verification confirmed:

```text
Tournament revision   : 3
Match status           : finalized
finalized_at           : present
finalized_by owner     : true
Match results          : 12
Distinct team slots    : 12
Distinct placements    : 12
Confirmed results      : 12
Zero-kill results      : 12
```

---

## 16. Google Sheets — Match Results Export

The target worksheet was configured exactly as required:

```text
Match Results
```

Header contract:

```text
A1:T1
20 exact repository-defined columns
```

The first export attempt returned:

```text
HTTP 502
```

Durable export state showed:

```text
state         : retryable_failure
attempt_count : 1
rows_written  : null
```

The failure occurred before Google append began.

Root cause was the spreadsheet worksheet/header configuration.

After correcting the worksheet name and exact header, the same idempotent export was retried once.

Final result:

```text
operation     : export_match
state         : succeeded
attempt_count : 2
rows_written  : 12
```

The Edge Function returned HTTP 200.

The function verified the appended Google range before recording success.

Manual spreadsheet verification confirmed:

```text
Header row      : row 1
Exported rows   : rows 2-13
Data rows       : 12
Placements      : 1-12
Duplicate block : none
```

Result:

```text
Google Sheets Match Results export : PASS
```

---

## 17. Google Sheets — Tournament Standings Export

The target worksheet was configured exactly as required:

```text
Tournament Standings
```

Header contract:

```text
A1:T1
20 exact repository-defined columns
```

A deterministic standings payload was generated from the single finalized controlled match.

The first function request was rejected:

```text
HTTP 401
```

No standings export operation was created.

The PowerShell Supabase access token had expired.

The session was refreshed using the existing refresh token.

Authenticated `/auth/v1/user` verification succeeded.

The standings export was then executed once.

Final result:

```text
operation            : export_standings
state                : succeeded
attempt_count        : 1
rows_written         : 12
exported_match_count : 1
```

The Edge Function returned HTTP 200.

Manual spreadsheet verification confirmed:

```text
Header row      : row 1
Exported rows   : rows 2-13
Standings rows  : 12
Ranks           : 1-12
Duplicate block : none
```

Result:

```text
Google Sheets Tournament Standings export : PASS
```

---

## 18. Export Idempotency Verification

Hosted export operation state after verification:

```text
export_match      : succeeded
export_standings  : succeeded
```

Total controlled export operations:

```text
2
```

Successful export operations:

```text
2
```

The Match Results failure/retry path demonstrated that retryable pre-write failures do not produce duplicate sheet rows.

The standings authentication failure demonstrated that requests rejected before authenticated execution do not create export-operation state.

No blind retry was performed after an ambiguous write state.

---

## 19. Final Hosted Evidence State

At closure, the controlled hosted application evidence state was:

```text
Tournaments          : 1
Matches              : 1
Match results        : 12
Export operations    : 2
Successful exports   : 2

match-screenshots objects : 1
ocr-screenshots objects   : 1
```

Controlled tournament:

```text
revision          : 3
finalized matches : 1
match results     : 12
```

These controlled fixtures are intentionally retained as CR-002 deployment evidence.

They are not being deleted as part of CR-002 closure.

Storage cleanup would require a separately approved privileged Storage operation because no authenticated client DELETE policy exists.

---

## 20. Security Advisor Findings

Post-deployment Security Advisor review identified warnings related to RPC function execution privileges.

Several SECURITY DEFINER functions are executable by authenticated users and some retain PUBLIC/anon executable grants inherited from the deployed design.

The protected functions explicitly validate:

```text
auth.uid()
```

and reject unauthenticated protected operations.

Runtime authorization tests showed that unauthorized cross-account application access remained blocked.

However, unnecessary function EXECUTE grants should still be reviewed as a defense-in-depth hardening item.

Historical migrations must not be edited to change these grants.

Any hardening must be delivered through a new forward migration.

Supabase also reported:

```text
Leaked Password Protection Disabled
```

This is a hosted Auth security-setting follow-up and was not changed during CR-002.

---

## 21. Resolved Execution Incidents

The following non-blocking execution issues occurred and were resolved during CR-002.

### 21.1 Duplicate/default team names

Initial tournament upload failed with a unique team-name constraint violation because the Android mapper allowed duplicate/default team names to reach the cloud RPC.

The RPC transaction rolled back atomically.

After assigning 12 unique team names, upload succeeded.

No partial hosted tournament state was created by the failed attempt.

### 21.2 Temporary DNS failure

One PowerShell REST request temporarily failed to resolve:

```text
jfllzadfduzktczzdvil.supabase.co
```

Follow-up checks confirmed:

```text
DNS resolution       : PASS
TCP 443 connectivity : PASS
```

The read operation then succeeded normally.

### 21.3 PSReadLine rendering failure

Windows PowerShell PSReadLine repeatedly raised:

```text
System.ArgumentOutOfRangeException
PSConsoleReadLine.ReallyRender
```

while entering a long standings/export payload.

This was a local terminal-rendering issue, not a Rank-Forge or Supabase defect.

PSReadLine was removed for that shell session and the payload was rebuilt cleanly.

### 21.4 Match Results worksheet mismatch

The first Match Results export failed before Google append because the target worksheet/header contract was not configured exactly.

After configuring:

```text
Match Results
A1:T1 exact header
```

the retry succeeded.

### 21.5 Expired access token

The first Tournament Standings request returned:

```text
401 Unauthorized
```

No export operation was created.

The hosted Supabase session was refreshed and authenticated successfully before retrying.

The subsequent export succeeded.

---

## 22. Deferred Follow-Ups

The following items are intentionally outside CR-002 closure and should be handled as separate changes.

### 22.1 Team-name validation alignment

Android currently allows tournament upload to reach the hosted unique constraint with duplicate/default blank team names.

A future change should align client/backend validation so invalid duplicate or blank team names are rejected before cloud upload.

This should not be fixed by weakening the database unique constraint.

### 22.2 RPC EXECUTE hardening

Review SECURITY DEFINER and RPC EXECUTE grants.

Where unnecessary PUBLIC or anon EXECUTE privileges exist, remove them through a new forward migration after dedicated security review and regression tests.

Historical migrations must remain unchanged.

### 22.3 Leaked-password protection

Review enabling Supabase Auth leaked-password protection through an approved hosted security configuration change.

### 22.4 Controlled fixture cleanup

The CR-002 hosted tournament, finalized match, match results, two Storage objects, export-operation records, and Google Sheet test rows are retained as deployment evidence.

If cleanup becomes desirable, perform it under a separate explicitly approved cleanup procedure.

Do not bypass Storage or RLS controls merely to remove test evidence.

### 22.5 Google service-account key lifecycle

The temporary local JSON file was removed.

The active key remains configured through hosted Supabase Secrets.

Future key rotation or deletion should be treated as a separate credential-lifecycle operation.

Unused older service-account keys, if any, should be identified before revocation.

---

## 23. Local Cleanup

Sensitive PowerShell variables used during hosted verification were removed after the tests were complete.

The temporary local service-account JSON file was removed.

No service-account private key or hosted bearer token remains intentionally stored in the repository.

Repository verification at cleanup confirmed:

```text
branch       : main
working tree : clean
HEAD         : 8eb07cf682333de0efa26d54f8b6cb3fb2798ca2
origin/main  : 8eb07cf682333de0efa26d54f8b6cb3fb2798ca2
```

The closure record itself is being added on a dedicated documentation branch after this clean execution state.

---

## 24. Closure Decision

CR-002 achieved its approved objective.

Verified hosted capabilities include:

- exact migration-history deployment;
- expected database schema;
- RLS ownership isolation;
- Storage authorization;
- authenticated Storage upload/download/upsert;
- hosted email/password Auth;
- hosted Google Sign-In compatibility;
- Android hosted session restoration;
- authenticated tournament cloud upload;
- tournament cloud restoration;
- match cloud restoration;
- protected match finalization;
- Edge Function deployment;
- authenticated Google service-account access;
- Match Results Google Sheets export;
- Tournament Standings Google Sheets export;
- durable export retry/idempotency behavior;
- physical-device restart persistence.

No unresolved CR-002 deployment blocker remains.

Deferred findings are documented and explicitly separated from this deployment closure.

**CR-002 — Hosted Supabase Deployment is formally CLOSED.**
