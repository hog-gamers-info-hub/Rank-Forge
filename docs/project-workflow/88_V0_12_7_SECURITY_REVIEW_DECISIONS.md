# Phase 12 v0.12.7 — Security Review Decisions

## Status

**Approved for audit and verification.**

## Version

**Phase 12 — Quality Assurance and Security Validation**

**v0.12.7 — Security Review**

Canonical scope:

> Verify credentials, RLS, storage policies, backend authorization, and repository hygiene.

---

## 1. Purpose

v0.12.7 performs a focused security review of the completed Rank-Forge implementation.

The review covers five required areas:

1. credential and secret handling
2. PostgreSQL Row Level Security
3. Supabase Storage authorization
4. backend/Edge Function authorization
5. Git repository hygiene

The default outcome is verification and documentation.

No production implementation change is authorized unless the review proves a concrete security defect.

---

## 2. Security Review Classification

Planned implementation classification:

```text
Audit / verification first
```

Production Android changes planned:

```text
None
```

Supabase migration changes planned:

```text
None
```

RLS policy changes planned:

```text
None
```

Storage policy changes planned:

```text
None
```

Edge Function changes planned:

```text
None
```

Repository configuration changes planned:

```text
None unless hygiene audit identifies a tracked-secret or ignore-rule defect
```

If a genuine defect is discovered, stop v0.12.7 implementation and create a separately scoped blocking security patch.

---

## 3. Existing Security Foundation

Earlier versions already implemented and tested:

- Supabase authentication
- session restoration and safe logout behavior
- core backend schema
- ownership-based RLS
- cross-account RLS tests
- authenticated tournament and roster synchronization
- authenticated match synchronization/restoration
- local persistent sync queue
- duplicate-prevention and idempotency behavior
- revision-based conflict detection
- finalized match protection
- protected corrections and audit history
- authenticated screenshot Storage
- screenshot metadata authorization
- authenticated Google Sheets export
- export retry/idempotency protection
- backend authorization regression tests

Phase 12 v0.12.2 completed the backend test suite.

The complete local pgTAP suite passed:

```text
11 files
338 tests
0 failures
```

Storage runtime coverage passed:

```text
21/21
```

A Storage RLS correlation defect discovered during v0.12.2 was fixed separately by v0.12.2.1 and regression-tested.

v0.12.7 must verify that these protections remain present and that repository/configuration hygiene has not undermined them.

---

## 4. Connected Supabase Deployment State

The currently connected Rank-Forge Supabase project is healthy, but the application backend has not been deployed to it.

Current read-only inspection found:

```text
Applied application migrations: 0
public application tables: 0
deployed Edge Functions: 0
```

This is not automatically classified as a security defect.

It means the repository/local migration chain remains the current backend implementation source of truth.

### Decision

v0.12.7 must **not deploy production migrations or Edge Functions merely to perform security review**.

Live production RLS and backend authorization cannot be claimed as tested until the backend is intentionally deployed in a later deployment workflow.

v0.12.7 may verify:

- repository migrations
- local Supabase database state
- local pgTAP authorization behavior
- source-level Edge Function authorization
- connected-project configuration/advisor state

Do not convert this QA version into production deployment.

---

## 5. Credential Handling Review

### Android client

The Android application may contain only credentials intended for public-client use.

Current configuration uses:

```text
RANK_FORGE_SUPABASE_URL
RANK_FORGE_SUPABASE_PUBLISHABLE_KEY
```

from Gradle properties or ignored `local.properties`, with safe placeholder fallbacks.

The resulting Android BuildConfig contains:

```text
SUPABASE_URL
SUPABASE_PUBLISHABLE_KEY
```

A Supabase publishable key is not treated as a server secret.

The Android application must never contain:

- Supabase secret keys
- service-role keys
- database passwords
- Google private keys
- Google service-account JSON
- private signing credentials
- webhook secrets
- OAuth client secrets intended only for servers

### Edge Functions

Server-only Google configuration must remain environment-based:

```text
GOOGLE_SHEETS_CLIENT_EMAIL
GOOGLE_SHEETS_PRIVATE_KEY
GOOGLE_SHEETS_SPREADSHEET_ID
```

Supabase server configuration must remain environment-based.

No real credential value may be committed.

---

## 6. Repository Secret Scan

The review must inspect tracked files for sensitive filenames and credential material without printing secret values.

Expected forbidden tracked-file classes include:

```text
.env
.env.*
local.properties
*.jks
*.keystore
google-services.json
service-account*.json
credentials*.json
supabase/.env
supabase/functions/.env
```

The audit must also inspect tracked content for likely private-key or secret-key material.

If suspicious content is found:

1. do not print the value
2. record only file path and line number where possible
3. determine whether the content is a real secret, placeholder, test fixture, or documentation example
4. treat any genuine committed secret as a blocking defect
5. rotate exposed secrets before considering the issue closed

---

## 7. `.gitignore` Review

The existing `.gitignore` must continue to protect:

- Android Studio local files
- `local.properties`
- environment files
- signing stores
- Google configuration/service-account files
- Supabase local environment files
- Supabase temporary directories
- build output
- logs
- temporary files
- Node modules

The review must verify that these patterns are active.

Do not modify `.gitignore` unless a concrete missing sensitive-file class is identified.

---

## 8. Core RLS Review

The five original core cloud tables use the tournament owner as the authorization root.

Required ownership invariant:

```text
tournaments.owner_id = auth.uid()
```

Child ownership must continue to resolve through the tournament hierarchy.

Core tables:

```text
tournaments
tournament_team_slots
players
matches
match_results
```

The review must verify:

- RLS enabled
- authenticated-only policies where required
- SELECT ownership restrictions
- INSERT ownership checks
- UPDATE `USING`
- UPDATE `WITH CHECK`
- DELETE ownership restrictions
- no broad `USING (true)` authorization
- no ownership transfer
- no cross-owner reparenting
- match-result match/team references resolve to the same owned tournament

---

## 9. Deprecated RLS Pattern Review

Current migrations must be scanned for deprecated or unsafe authorization constructs.

Specifically verify absence of authorization based on:

```text
auth.role()
raw_user_meta_data
user_metadata
```

Authorization must not depend on user-editable JWT metadata.

Existing ownership must continue to use `auth.uid()` and relational ownership.

---

## 10. Public Schema RLS Review

Every application table exposed through the Supabase Data API must have appropriate RLS.

The security audit must enumerate application relations in `public` and verify:

```text
RLS enabled
```

for every exposed application table.

If a public application table exists without RLS, classify it as a blocking security defect.

System-managed Supabase schemas are outside the application table audit except where Rank-Forge defines explicit Storage policies.

---

## 11. Data API Grant Review

RLS and PostgreSQL grants are separate security layers.

The review must inspect table grants for:

```text
anon
authenticated
service_role
```

The review must verify that application access is intentional.

Do not assume that the presence of an RLS policy automatically grants Data API access.

Do not add broad grants merely to make a failing test pass.

Any grant modification requires a separately approved backend patch.

---

## 12. Cross-Account Authorization Review

Existing cross-account pgTAP coverage must continue to prove that owner A cannot access owner B data.

Required denied operations include:

- SELECT
- INSERT through another owner's parents
- UPDATE
- DELETE
- ownership transfer
- cross-owner child reparenting
- cross-tournament invalid match-result relationships

Anonymous access must remain denied where ownership-authenticated access is required.

---

## 13. Storage Security Review

Rank-Forge screenshot Storage must remain RLS-controlled.

The review must verify screenshot object behavior for:

```text
INSERT
SELECT
UPDATE
```

because replacement/upsert behavior requires all three permissions.

Authorization must preserve:

- approved screenshot bucket
- authenticated user
- owned tournament
- owned match
- approved deterministic object path
- same-owner replacement
- cross-account denial
- anonymous denial

The v0.12.2.1 policy-correlation fix must remain present.

Storage policy predicates must explicitly correlate object-path checks against the intended `storage.objects` row.

No broad authenticated bucket access is acceptable.

---

## 14. Screenshot Metadata Review

Screenshot metadata authorization must continue to enforce ownership through the tournament/match hierarchy.

Verify:

- owner access
- cross-account denial
- anonymous denial
- valid foreign-key relationships
- cascade behavior
- no metadata operation bypasses Storage ownership assumptions

---

## 15. Backend Authorization Review

Every authenticated backend/Edge Function operation must authenticate the caller before protected data access.

For Google Sheets export, verify the existing flow remains:

```text
Authorization header
-> parse Bearer token
-> validate Supabase user
-> perform official data reads using caller token
-> existing RLS applies
-> validate official data
-> perform export
```

The backend must not replace caller authorization with a service-role bypass.

---

## 16. Service-Role / Secret-Key Review

Search production source for:

```text
SUPABASE_SERVICE_ROLE_KEY
service_role
SUPABASE_SECRET_KEY
sb_secret_
```

Any occurrence must be classified.

Allowed examples may include:

- comments explicitly forbidding service-role use
- tests verifying service-role absence
- documentation discussing prohibited use

A real server secret committed to the repository is a blocking defect.

A service-role key embedded into Android/client code is a blocking defect.

---

## 17. Google Credential Review

Google Sheets export credentials must remain server-only.

Verify:

- private key comes from environment
- client email comes from environment
- spreadsheet ID comes from environment
- no private key is embedded in TypeScript
- no service-account JSON is tracked
- no raw Google access token is logged
- no private-key material is logged
- no credential appears in response payloads

The Google service-account scope must remain limited to the Sheets API required by Rank-Forge.

---

## 18. Error and Logging Review

Security-sensitive backend failures must expose stable application error codes rather than upstream raw responses or secret values.

Review production logging for accidental output of:

- bearer tokens
- Supabase keys
- Google access tokens
- Google private keys
- authorization headers
- complete credential JSON
- sensitive upstream response bodies

Any production logging of credentials is a blocking defect.

---

## 19. Backend Privileged-Code Review

Inspect database migrations for:

```text
SECURITY DEFINER
```

If present, every occurrence must be reviewed individually for:

- genuine need
- safe schema placement
- restricted execution privileges
- explicit caller authorization
- safe `search_path`
- resistance to RLS bypass

Do not introduce new `SECURITY DEFINER` functions during v0.12.7.

Also inspect application views.

If an application view is available to `anon` or `authenticated`, verify that it cannot unintentionally bypass underlying RLS.

---

## 20. Security Advisor Review

Run the Supabase Security Advisor against the connected project.

Current known advisor result:

```text
WARN — Leaked Password Protection Disabled
```

No other advisor warning was returned during the initial v0.12.7 review.

### Decision

Leaked Password Protection must be handled explicitly.

If the project plan supports the feature:

```text
Enable it manually through Supabase Auth security settings.
```

If the project plan does not support the feature:

```text
Document it as a plan-based security hardening deferral.
```

Do not silently mark the advisor as fully clean while the warning remains.

This setting change, if performed, is an environment configuration action and not an Android/backend code change.

---

## 21. Authentication Password Review

In addition to the advisor finding, manually review current Auth password settings.

Record:

- minimum password length
- required character policy
- leaked-password protection state
- password-change reauthentication configuration if relevant

Do not change unrelated authentication behavior during this version without an explicit decision.

---

## 22. Current Supabase Platform Compatibility

The security review must account for current Supabase behavior rather than relying only on the Phase 6 assumptions.

Review current platform guidance relevant to:

- RLS
- Data API grants
- Storage RLS
- publishable vs secret keys
- Edge Function secrets
- deprecated RLS authorization functions

No migration should be changed solely because a newer recommendation exists unless the current implementation is actually unsafe or incompatible.

---

## 23. Local Supabase Verification

The repository migration chain must be tested from a clean local database.

Required:

```powershell
npx.cmd supabase --version
npx.cmd supabase db reset
npx.cmd supabase test db
```

Expected baseline from v0.12.2:

```text
11 pgTAP files
338 tests
0 failures
```

If the test count has legitimately increased since v0.12.2, use the current count while requiring:

```text
0 failures
```

The v0.12.2.1 screenshot Storage regression must remain passing.

---

## 24. Static RLS/Authorization Scans

Use read-only repository scans to flag suspicious constructs.

The review should inspect migrations and backend source for:

```text
auth.role()
SECURITY DEFINER
service_role
SUPABASE_SERVICE_ROLE_KEY
SUPABASE_SECRET_KEY
sb_secret_
BEGIN PRIVATE KEY
user_metadata
raw_user_meta_data
```

Matches are findings to classify, not automatic failures.

Do not print real credential values.

---

## 25. Safe Tracked-File Hygiene Check

Use `git ls-files` rather than scanning untracked developer files.

A tracked sensitive filename is a security finding.

Expected result:

```text
No tracked environment files
No tracked local.properties
No tracked signing stores
No tracked service-account JSON
No tracked credential JSON
```

---

## 26. Git State Requirements

Before and after security review:

```text
main == origin/main
working tree clean
```

The review must not accidentally modify:

- generated local Supabase files
- `.temp`
- Android local properties
- IDE files
- test database artifacts
- emulator files

---

## 27. Production Deployment Exclusion

v0.12.7 does not authorize:

- pushing migrations to the connected Supabase project
- deploying Edge Functions
- creating Storage buckets in production
- creating production tables
- changing production RLS
- rotating credentials without a confirmed exposure
- changing authentication providers
- enabling service-role usage
- production Google writes

Deployment remains a separate controlled activity.

---

## 28. Blocking Security Defects

The following findings require a separately scoped patch before v0.12.7 can close:

- committed real secret
- client-embedded service-role/secret key
- public application table without required RLS
- cross-account RLS bypass
- anonymous unauthorized data access
- unsafe Storage cross-account access
- Storage path authorization bypass
- service-role backend bypass where caller RLS is required
- unauthorized `SECURITY DEFINER` exposure
- sensitive credential logging
- backend authorization bypass
- regression failure in security pgTAP tests

Do not silently repair such findings inside the review branch.

---

## 29. Non-Blocking / Documentable Findings

Potentially documentable items include:

- live backend not yet deployed
- optional security features unavailable on the current Supabase plan
- non-secret publishable key exposure
- benign documentation/test references to security-key names
- security hardening recommendations that do not represent exploitable current behavior

Each must still be explicitly recorded.

---

## 30. Review Deliverable

The final v0.12.7 deliverable should be a dedicated security-review evidence document containing:

### Credentials

```text
PASS / FAIL
```

### RLS

```text
PASS / FAIL
```

### Storage

```text
PASS / FAIL
```

### Backend authorization

```text
PASS / FAIL
```

### Repository hygiene

```text
PASS / FAIL
```

### Supabase Security Advisor

```text
PASS / WARN / FAIL
```

### Production deployment state

```text
Not deployed / deployed and verified
```

### Blocking findings

```text
None
```

or an exact list.

### Documented deferrals

Explicitly list any accepted security-hardening deferral.

---

## 31. Codex Policy

Do not use Codex for the initial v0.12.7 review.

The review is primarily:

- read-only inspection
- command-based verification
- local Supabase testing
- repository scanning
- documentation

Use Codex only if:

1. a genuine code/security defect is identified, and
2. the required patch is complex enough to justify Codex.

Any patch must receive its own exact approved file boundary.

---

## 32. Acceptance Criteria

v0.12.7 is accepted when:

1. credential handling is reviewed
2. no genuine secret is tracked
3. Android contains no privileged Supabase key
4. Edge Function credentials remain environment-based
5. all application RLS policies are reviewed
6. cross-account authorization tests pass
7. Data API grants are reviewed
8. screenshot Storage policies are reviewed
9. Storage runtime security tests pass
10. screenshot metadata authorization passes
11. backend caller-token authorization is reviewed
12. no unintended service-role bypass exists
13. Google credentials remain server-only
14. privileged database code is reviewed
15. sensitive logging is reviewed
16. local database reset succeeds
17. complete pgTAP suite passes
18. repository hygiene scan passes
19. Supabase Security Advisor result is recorded
20. leaked-password warning is resolved or explicitly deferred
21. connected production deployment state is recorded accurately
22. no production deployment occurs as part of the review
23. no blocking security defect remains unresolved
24. security-review evidence document is merged
25. `main` is synchronized with `origin/main`

---

## 33. Final Decision

**v0.12.7 is approved as an audit-first security verification version.**

The intended workflow is:

```text
repository credential audit
-> local migration/RLS audit
-> cross-account authorization verification
-> Storage policy verification
-> backend authorization review
-> repository hygiene scan
-> Supabase Security Advisor review
-> classify findings
-> patch separately only if required
-> record final evidence
```

No production code or backend mutation is planned unless the review identifies a blocking defect.
