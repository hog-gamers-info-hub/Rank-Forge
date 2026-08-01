# v0.10.7 — Export Retry and Idempotency Decisions

## Purpose

v0.10.7 makes the existing Google Sheets match and standings export operations safe to replay after failures without blindly appending duplicate rows.

The version applies to the existing authenticated Supabase Edge Function:

`google-sheets-export`

Existing operations that must remain compatible:

- `verify_connection`
- `export_match`
- `export_standings`

v0.10.7 does not add a new public export operation.

The canonical Phase 10 roadmap requirement is:

> Handle failed exports without duplicate rows.

The implementation must preserve the approved v0.10.5 match-export schema, the approved v0.10.6 standings-export schema, existing ownership checks, existing finalized-data validation, existing Google worksheet/header contracts, and existing success-response contracts.

## Architectural Decision

Google Sheets values append does not provide a native request idempotency key that Rank Forge can rely on.

Therefore v0.10.7 uses a persistent Supabase export-operation ledger plus deterministic server-computed request fingerprinting.

The ledger prevents:

- two concurrent identical requests from both reaching Google
- replay of an already successful identical export
- blind retry after a Google write whose outcome is uncertain
- stale pre-write workers from completing after another worker has reclaimed the operation

The ledger does not attempt to prove whether an uncertain Google append actually wrote rows.

That verification is deferred to v0.10.8.

## Canonical Scope

v0.10.7 includes:

- deterministic server-side export request fingerprinting
- one additive Supabase migration
- a persistent export-operation ledger
- RLS and ownership protection for the ledger
- narrow authenticated RPCs for operation state transitions
- atomic claim behavior
- operation leases
- attempt counting
- concurrent duplicate suppression
- safe replay of already successful exports
- safe reclaim of interrupted pre-write work
- explicit write-start state before a Google append
- explicit uncertain-outcome state after ambiguous writes
- retryable failure state for failures known to occur before a write
- no duplicate Google append on identical successful replay
- no blind retry of uncertain writes
- deterministic database tests
- deterministic mocked Edge Function tests
- preservation of the existing Google Sheets schemas and ranges

## Out Of Scope

v0.10.7 does not implement:

- Android export UI
- Android network-client integration
- new Android request fields
- client-generated idempotency keys
- client-generated retry tokens
- Google Drive API access
- Google worksheet creation
- Google worksheet formatting
- changes to match export columns
- changes to standings export columns
- row-level Google verification
- duplicate-row scanning in Google Sheets
- uncertain-write reconciliation by reading sheet rows
- automatic deletion of export-operation records
- production deployment
- production secret changes
- scoring changes
- standings changes
- tie-break changes
- OCR changes
- Room changes
- cloud-sync redesign
- unrelated refactoring

Post-export row verification and resolution of uncertain outcomes remain v0.10.8 scope.

## Request Contract Decision

The existing public request contracts remain unchanged.

### Match export

The request remains exactly:

- `operation`
- `tournament_id`
- `match_id`
- `rows`

No new top-level field is added.

### Standings export

The request remains exactly:

- `operation`
- `tournament_id`
- `rows`

No new top-level field is added.

The Edge Function must continue rejecting unknown top-level fields.

In particular, clients must not send:

- `idempotency_key`
- `retry_token`
- `operation_id`
- `attempt`
- `force`
- `force_retry`

Retry safety is transparent to callers and is computed server-side.

## Why The Client Does Not Supply An Idempotency Key

Android export-flow integration is deferred to v0.11.6.

Adding a client idempotency field in v0.10.7 would unnecessarily change the approved server contract before Android integration exists.

The canonical export payload already contains all information required to distinguish one logical export snapshot from another.

Therefore v0.10.7 derives identity from:

- authenticated owner
- export operation
- stable target identifiers
- exact canonical validated export payload

## Canonical Payload Fingerprint

The Edge Function must compute a SHA-256 fingerprint after structural payload parsing.

The fingerprint input must be an explicitly constructed canonical representation with fixed field ordering.

It must not hash arbitrary raw request bytes.

The fingerprint must include:

### `export_match`

- operation
- tournament ID
- match ID
- all 12 rows
- all 20 canonical match fields in canonical order

### `export_standings`

- operation
- tournament ID
- all 12 rows
- all 20 canonical standings fields in canonical order

Strings must be fingerprinted exactly as accepted by the canonical parsers.

The fingerprint process must not:

- trim names
- normalize Unicode
- alter punctuation
- reorder rows
- reorder canonical row fields
- remove empty player-name strings
- include bearer tokens
- include Supabase configuration
- include Google configuration
- include spreadsheet ID
- include service-account data
- include environment values

Fingerprint format:

- SHA-256
- lowercase hexadecimal
- exactly 64 characters

## Logical Idempotency Identity

A logical export is unique under:

- authenticated `owner_id`
- `operation_type`
- `tournament_id`
- nullable `match_id`
- `payload_fingerprint`

The database must enforce uniqueness for the logical request.

For match exports:

- `match_id` is required

For standings exports:

- `match_id` is null

The payload fingerprint itself includes the target identifiers, but explicit target columns are retained for auditability, constraints, and safe response reconstruction.

## Re-Export Policy

An identical successful payload must not append again.

A replay of the exact same successful logical export returns the existing canonical success response without another Google write.

A materially changed payload produces a different fingerprint and is a different logical export.

Examples that may legitimately create a new logical export:

- a protected finalized match correction changes official match rows
- a later standings snapshot includes an additional finalized match
- official team/roster/export values change and the new payload passes current official validation

There is no `force` override in v0.10.7.

## Required Supabase Migration

v0.10.7 requires exactly one new additive migration for export idempotency.

No already-applied migration may be edited.

Suggested migration name pattern:

`*_v0_10_7_export_retry_idempotency.sql`

The migration must be reviewable and reversible through a future corrective migration.

No destructive migration is allowed.

## Canonical Export Operation Table

Create:

`public.export_operations`

Required columns:

- `id uuid primary key default gen_random_uuid()`
- `owner_id uuid not null references auth.users(id) on delete cascade`
- `operation_type text not null`
- `tournament_id uuid not null references public.tournaments(id) on delete cascade`
- `match_id uuid null references public.matches(id) on delete cascade`
- `payload_fingerprint text not null`
- `state text not null`
- `lease_token uuid null`
- `lease_expires_at timestamptz null`
- `attempt_count integer not null default 1`
- `failure_code text null`
- `rows_written integer null`
- `exported_match_count integer null`
- `created_at timestamptz not null default now()`
- `updated_at timestamptz not null default now()`
- `completed_at timestamptz null`

Required operation types:

- `export_match`
- `export_standings`

Required states:

- `in_progress`
- `write_started`
- `succeeded`
- `retryable_failure`
- `outcome_uncertain`

Required checks:

- fingerprint is exactly 64 lowercase hexadecimal characters
- `attempt_count >= 1`
- `rows_written` is null or exactly 12
- `exported_match_count` is null or between 1 and 10
- `export_match` requires non-null `match_id`
- `export_standings` requires null `match_id`
- succeeded operations require `rows_written = 12`
- succeeded standings operations require non-null `exported_match_count`
- succeeded match operations require null `exported_match_count`
- active states require a non-null lease token and lease expiry
- terminal states must not retain an active lease

The implementation may strengthen these checks when doing so preserves the frozen state model.

## Uniqueness

Create one unique constraint or unique index covering the logical operation identity.

Canonical uniqueness must prevent two identical logical exports for the same owner from creating separate operation rows.

The implementation must not rely only on an application-side pre-check.

Concurrency protection must be enforced atomically by PostgreSQL.

## RLS And Authorization

Enable RLS on `public.export_operations`.

Ownership is defined by:

`owner_id = auth.uid()`

The table must not allow one user to read another user's operation records.

Direct mutation by normal authenticated clients is prohibited.

Required access model:

- authenticated owners may read only their own operation records if direct SELECT is exposed
- no direct client INSERT policy
- no direct client UPDATE policy
- no direct client DELETE policy
- all state-changing operations go through narrow database functions
- Edge Function calls use the caller bearer token and anon key
- service-role credentials are not used

The implementation must not accept caller-supplied `owner_id`.

Database functions must derive the owner from `auth.uid()`.

## Narrow RPC Policy

The migration must provide narrowly scoped authenticated RPC behavior for:

1. claiming or replaying an export operation
2. marking that the Google write is about to start
3. marking confirmed success
4. marking a definitive retryable failure
5. marking an uncertain outcome

Exact function names may follow repository naming conventions, but responsibilities must remain separate and narrow.

Each privileged RPC must:

- derive owner from `auth.uid()`
- reject unauthenticated callers
- use an empty or explicitly safe `search_path`
- verify operation ownership
- validate current state
- validate lease token where a worker transition is required
- expose no secrets
- return only fields required by the Edge Function
- grant execution only to the required authenticated role
- revoke unnecessary public/anon execution

No RPC may accept a service-role credential or owner ID as authority.

## Lease Decision

Lease duration:

`90 seconds`

The lease protects an active worker.

The database generates a new `lease_token` when an operation is initially claimed or safely reclaimed.

A worker must present the current lease token for state-changing transitions.

This prevents a stale worker from finalizing an operation after another invocation has reclaimed it.

## Claim State Machine

### New logical export

Create one row:

- state: `in_progress`
- attempt count: 1
- fresh lease token
- lease expiry: current database time + 90 seconds

Return ownership of the attempt to the caller.

### Existing `succeeded`

Do not acquire a new lease.

Do not contact Google.

Return replay metadata needed to construct the original canonical success response.

### Existing active `in_progress` with unexpired lease

Do not contact Google.

Return an in-progress result.

The Edge Function returns `EXPORT_IN_PROGRESS`.

### Existing expired `in_progress`

This state is safe to reclaim because Google write-start has not been recorded.

Atomically:

- increment attempt count
- issue new lease token
- set new 90-second lease
- clear safe prior failure metadata
- remain `in_progress`

Only one concurrent caller may win the reclaim.

### Existing `retryable_failure`

This state is safe to reclaim because the prior attempt is known not to have an unresolved Google write.

Atomically:

- increment attempt count
- issue new lease token
- set new lease
- clear prior safe failure metadata
- transition to `in_progress`

### Existing active `write_started`

Do not contact Google from the competing request.

Return an in-progress result while the lease is valid.

### Existing expired `write_started`

Do not reclaim.

Atomically classify it as:

`outcome_uncertain`

Clear the active lease.

Return uncertain outcome.

### Existing `outcome_uncertain`

Do not reclaim.

Do not contact Google.

Return uncertain outcome.

## Official Validation Before Claim

The existing official Supabase validation remains mandatory.

Canonical call order for export requests:

1. structural payload parsing
2. authentication
3. official Supabase reads and semantic verification
4. canonical fingerprint calculation
5. atomic export-operation claim
6. Google OAuth
7. exact worksheet header verification
8. mark `write_started`
9. one Google append
10. finalize operation state
11. return response

The operation ledger must not make invalid or stale application data exportable.

An idempotent replay must still pass the current official data validation before the ledger is consulted.

This preserves v0.10.5 and v0.10.6 finalized-data guarantees.

## Write-Start Boundary

Immediately before the Google append request, the worker must transition:

`in_progress -> write_started`

The transition must require:

- operation ID
- current lease token
- current state exactly `in_progress`
- owner match

The database transition must succeed before any Google append is sent.

If the transition does not succeed, Google must not be contacted for the append.

This durable boundary is mandatory.

It prevents an expired pre-write lease from being incorrectly retried after a Google write may have begun.

## Google Append Policy

Each invocation may perform at most one Google append request.

v0.10.7 must not introduce an automatic loop that repeats the Sheets append request.

The existing requirements remain:

- Google Sheets values append API
- exactly 12 rows
- exactly 20 cells per row
- `RAW`
- `INSERT_ROWS`
- `majorDimension=ROWS`
- existing canonical worksheet/range per operation

## Safe Retry Definition

In v0.10.7, "retry" means replaying the same canonical export request in a later invocation.

It does not mean automatically repeating a Google append inside one invocation.

An invocation may safely retry/reclaim only when the persistent operation state proves that no ambiguous Google write exists.

## Retryable Failures

Failures that occur before `write_started` are duplicate-safe.

They may transition the operation to:

`retryable_failure`

Examples:

- Google OAuth failure
- Google header-read failure
- header-schema mismatch
- pre-append upstream timeout
- pre-append network failure
- other failures occurring after claim but before the durable write-start transition

An explicit Google append rejection that is known to have performed no write may also be classified as `retryable_failure`.

For v0.10.7, the explicitly recognized duplicate-safe append rejection codes are:

- `GOOGLE_SHEETS_ACCESS_DENIED`
- `GOOGLE_SHEETS_NOT_FOUND`
- `GOOGLE_API_RATE_LIMITED`

No Google append is automatically retried in the same invocation.

## Uncertain Outcomes

After state is `write_started`, the following must be treated as potentially having written rows unless the implementation has a frozen definitive no-write mapping:

- network failure
- request timeout
- Google 5xx
- malformed or contradictory successful append response
- failure to parse a successful append response
- inability to persist confirmed success after Google has acknowledged the append
- process interruption
- worker crash
- lease expiry while still in `write_started`

These transition to or are later classified as:

`outcome_uncertain`

An uncertain logical export must not be appended again by v0.10.7.

The response must instruct the caller not to blindly retry.

v0.10.8 owns sheet-row verification and reconciliation of uncertain operations.

## Confirmed Success

Success requires both:

1. Google confirms exactly 12 updated rows
2. the operation ledger is finalized as `succeeded`

On success store:

- `state = succeeded`
- `rows_written = 12`
- `exported_match_count` for standings exports
- `completed_at`
- no active lease
- no failure code

For match exports:

- `exported_match_count` remains null

If Google confirms success but the operation cannot be finalized durably, the request must fail closed as an uncertain outcome and must not permit a second append.

## Successful Replay Response

A replay of an operation already stored as `succeeded` returns the existing canonical response.

### Match

```json
{
  "ok": true,
  "operation": "export_match",
  "tournament_id": "uuid",
  "match_id": "uuid",
  "rows_written": 12
}
```

### Standings

```json
{
  "ok": true,
  "operation": "export_standings",
  "tournament_id": "uuid",
  "exported_match_count": 3,
  "rows_written": 12
}
```

No `replayed`, `operation_id`, fingerprint, lease, attempt count, database state, or internal metadata is added to the public success response.

This preserves the v0.10.5 and v0.10.6 public success contracts.

## New Error Contract

Add safe typed errors:

### `EXPORT_IN_PROGRESS`

Status:

`409`

Meaning:

An identical logical export is currently being processed.

Safe message:

`An identical export is already in progress.`

### `EXPORT_OUTCOME_UNCERTAIN`

Status:

`409`

Meaning:

A previous append may already have written rows and v0.10.7 cannot safely retry it.

Safe message:

`The previous export outcome is uncertain and cannot be retried safely.`

### `EXPORT_IDEMPOTENCY_FAILURE`

Status:

`502`

Meaning:

The operation ledger or required idempotency state transition could not be completed safely.

Safe message:

`The export operation state could not be updated safely.`

Existing errors remain compatible.

Raw SQL, PostgREST, Google, and Supabase response bodies must not be returned.

## Failure-Code Persistence

`failure_code` may store only an approved safe internal error code.

It must not store:

- raw error messages
- request bodies
- Google response bodies
- Supabase response bodies
- tokens
- spreadsheet IDs
- tournament names
- team names
- player names
- private keys
- environment values

## Operation Data Minimization

The ledger must not persist exported row payloads.

The ledger may persist only operation metadata required for idempotency and safe replay.

Do not persist:

- row JSON
- player names
- team names
- tournament names
- scores
- OCR data
- spreadsheet content
- Google token data
- Supabase bearer tokens

The SHA-256 payload fingerprint is sufficient for logical request identity.

## Retention Decision

v0.10.7 does not automatically delete export-operation records.

Successful and uncertain records must remain durable so that later identical requests cannot silently create duplicates.

No cron cleanup is introduced.

No retention job is introduced.

A future version may introduce archival or cleanup only with a policy that preserves duplicate-prevention guarantees.

## No Google Duplicate Scan

v0.10.7 must not scan existing Google rows to guess whether an export already exists.

Reasons:

- v0.10.8 owns post-export verification
- row scanning adds race conditions
- row content may legitimately repeat across different snapshots
- the stable schemas do not contain an export-operation ID column
- v0.10.7 must not change the 20-column export schemas

## No Schema Column Change

The match and standings worksheet schemas remain exactly 20 columns.

No idempotency identifier is written into Google Sheets.

No hidden 21st column is allowed.

No formulas, notes, metadata columns, or marker rows are added.

## Concurrency Rules

At most one active lease may exist for one logical export.

Two simultaneous identical requests must result in:

- one claimed worker
- one `EXPORT_IN_PROGRESS` response or successful replay depending on timing
- at most one Google append

Database uniqueness and atomic claim behavior must enforce this.

The implementation must not rely on process-local mutexes or Edge Function memory.

## Cross-Invocation Safety

The design must remain correct when:

- requests run on different Edge Function instances
- the original instance terminates unexpectedly
- requests arrive simultaneously
- a caller repeats the same payload after a timeout
- a caller repeats the same payload after confirmed success

No in-memory state may be required for correctness.

## Database Function Safety

Required database tests must prove:

- unauthenticated RPC calls fail
- owner A cannot claim or transition owner B's operation
- owner identity is derived from `auth.uid()`
- duplicate logical operation creation is atomic
- one concurrent logical operation exists
- active lease cannot be stolen
- expired pre-write lease can be reclaimed
- stale lease token cannot transition the reclaimed row
- active write-start cannot be reclaimed
- expired write-start becomes uncertain
- succeeded state cannot be reopened
- uncertain state cannot be reopened
- invalid state transitions fail
- `rows_written` success invariant is enforced
- match/standings target constraints are enforced
- fingerprint format constraint is enforced

## Edge Function Testing

All tests must use mocks/injected network behavior.

No test may contact production Supabase or Google.

Required coverage includes:

1. existing `verify_connection` tests remain passing
2. existing `export_match` tests remain passing
3. existing `export_standings` tests remain passing
4. identical successful match replay performs no second Google append
5. identical successful standings replay performs no second Google append
6. two identical concurrent claims permit only one active worker
7. active duplicate request returns `EXPORT_IN_PROGRESS`
8. expired `in_progress` operation is safely reclaimed
9. reclaimed attempt receives a different lease token
10. stale lease token cannot mark write started
11. stale lease token cannot finalize success
12. `retryable_failure` can be reclaimed
13. `write_started` is persisted before append
14. append is never sent when write-start persistence fails
15. Google append executes at most once per invocation
16. explicit safe 403 append rejection records retryable failure
17. explicit safe 404 append rejection records retryable failure
18. explicit safe 429 append rejection records retryable failure
19. append timeout records uncertain outcome
20. append network failure records uncertain outcome
21. append 5xx records uncertain outcome
22. malformed successful append response records uncertain outcome
23. wrong updated-row count records uncertain outcome
24. success stores exactly 12 written rows
25. standings success stores official exported match count
26. match success stores no standings match count
27. successful replay preserves the existing public response shape
28. uncertain replay performs no Google request
29. uncertain replay returns `EXPORT_OUTCOME_UNCERTAIN`
30. active replay performs no Google request
31. payload fingerprint changes when a canonical row changes
32. payload fingerprint is stable for identical parsed payloads
33. fingerprint excludes authorization and Google configuration
34. match and standings fingerprints cannot collide through operation omission
35. invalid official data is rejected before claim
36. Google is not contacted when claim fails
37. no service-role key is used
38. no Drive API request occurs
39. no hidden schema column is written
40. no automatic Google append retry loop exists
41. no raw payload or secrets appear in operation failure responses

The final implementation may add focused tests beyond this list.

## Local Verification

Required checks:

- migration formatting/review
- local Supabase database reset or approved isolated migration application
- database tests for schema, RLS, RPCs, transitions, and concurrency invariants
- Deno formatting
- Deno lint
- Deno type checking
- focused idempotency tests
- complete `google-sheets-export` Deno test suite
- static scan for service-role usage
- static scan for Google Drive scopes/endpoints
- static scan proving match and standings exports remain 20 columns
- static scan proving no automatic repeated Sheets append
- static scan for secret/private-key material
- `git diff --check`
- exact changed-file review

Android Gradle or device tests are not required unless Android files are unexpectedly modified.

## Deployment Safety

Implementation must not:

- deploy the Edge Function
- apply migrations to production
- set Supabase secrets
- modify production RLS directly
- contact production Supabase
- contact production Google OAuth
- contact a real spreadsheet
- perform a real Google append

All database verification must be local or isolated according to the repository workflow.

## Expected Implementation Boundaries

Likely implementation areas:

- one new Supabase migration
- database tests for the migration/RPC state machine
- a narrow shared export-idempotency/fingerprint module
- narrow Supabase RPC client support
- `google-sheets-export/index.ts` orchestration updates
- typed errors
- focused Deno tests

The implementation must not modify Android files.

The implementation must not modify existing stable match or standings export columns.

## Compatibility

v0.10.7 must preserve:

- `verify_connection`
- `export_match`
- `export_standings`
- match request field count
- standings request field count
- both 20-column schemas
- match worksheet and ranges
- standings worksheet and ranges
- RAW value input
- INSERT_ROWS append
- 12-row write size
- finalized-data verification
- caller RLS ownership
- no service-role use
- no Drive API use
- public success-response shapes

## v0.10.8 Handoff

v0.10.8 — Export Verification owns:

- reading exported Google rows
- comparing exported row ordering and totals
- determining whether an uncertain write actually succeeded
- reconciliation of `outcome_uncertain` operations
- any approved state transition from uncertain after verification
- post-export verification reporting

v0.10.7 must leave `outcome_uncertain` fail-closed rather than guessing.

## Acceptance Criteria

v0.10.7 is complete when:

- one additive migration creates the persistent idempotency ledger
- RLS is enabled and cross-account access is blocked
- authenticated RPCs atomically enforce claim and state transitions
- export requests remain schema-compatible with v0.10.5 and v0.10.6
- identical requests use a deterministic SHA-256 fingerprint
- one logical export has only one persistent operation row
- simultaneous identical requests cannot both append
- successful replay performs no Google write
- expired pre-write work can be safely reclaimed
- stale workers cannot finalize reclaimed operations
- write-start is durably recorded before Google append
- uncertain writes cannot be blindly retried
- safe pre-write failures can be retried by replaying the same request
- no automatic Google append retry loop exists
- no Google schema column is added
- no Google duplicate-row scan is added
- no service-role key is used
- no Android code changes
- deterministic local database tests pass
- deterministic mocked Deno tests pass
- formatting, lint, type checking, database checks, and `git diff --check` pass
- no production deployment, migration, secret change, or real Google write occurs
