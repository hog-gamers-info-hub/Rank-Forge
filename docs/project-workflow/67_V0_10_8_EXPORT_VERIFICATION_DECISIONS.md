# v0.10.8 — Export Verification Decisions

## Purpose

v0.10.8 verifies that Google Sheets exports actually contain the exact finalized Rank Forge data that was approved for export.

The canonical Phase 10 roadmap requirement is:

> Compare exported rows and totals against finalized application data.

This version closes the verification gap intentionally left by v0.10.7:

- successful Google appends must be read back and verified before the export operation is durably finalized as `succeeded`
- an `outcome_uncertain` export must be reconciled by reading Google Sheets instead of being blindly retried
- exported rows, ordering, identifiers, and scoring totals must match the same canonical application data already validated by the existing match and standings exporters

v0.10.8 applies only to the existing authenticated Supabase Edge Function:

`google-sheets-export`

Existing public operations remain:

- `verify_connection`
- `export_match`
- `export_standings`

v0.10.8 does not add an Android export UI or Android network integration.

---

## Architectural Decision

Verification is integrated transparently into the existing `export_match` and `export_standings` operations.

No new public verification operation is added.

The existing request schemas remain unchanged.

A caller verifies or reconciles an export by replaying the same canonical export request.

The Edge Function continues to:

1. structurally parse the request
2. authenticate the caller
3. read authoritative Supabase data under caller RLS
4. verify the supplied export payload against finalized application data
5. calculate the canonical payload fingerprint
6. consult the persistent export-operation ledger

After that point:

- a new claimed export proceeds to Google append and mandatory read-back verification
- an already `succeeded` identical export returns the existing success response without another Google request
- an active identical export returns `EXPORT_IN_PROGRESS`
- an `outcome_uncertain` identical export performs read-only Google reconciliation instead of appending

No invocation may blindly append an `outcome_uncertain` export.

---

## Canonical Scope

v0.10.8 includes:

- mandatory post-append Google Sheets read-back verification
- exact comparison of 12 exported rows against canonical export values
- exact row-order verification
- exact 20-column verification
- verification of identifiers, team/player names, placements, kills, points, totals, tie-break fields, and other canonical exported values
- safe reconciliation of v0.10.7 `outcome_uncertain` operations
- detection of an exact previously written export block
- detection of a confirmed currently absent uncertain export
- detection of partial, mismatched, duplicated, or ambiguous candidate data
- bounded Google Sheets scanning for uncertain-write reconciliation
- narrow authenticated database transitions for verified uncertain operations
- deterministic mocked Edge Function tests
- deterministic Supabase database/RLS/RPC tests
- preservation of existing match and standings request schemas
- preservation of existing successful public responses
- preservation of the approved 20-column worksheet schemas
- preservation of existing retry/idempotency guarantees

---

## Out Of Scope

v0.10.8 does not implement:

- Android export UI
- Android network-client integration
- new Android request fields
- a new public `verify_export` operation
- client-generated operation IDs
- client-generated idempotency keys
- client-generated retry tokens
- force-retry or force-export flags
- hidden tracking columns in Google Sheets
- a 21st export column
- worksheet creation
- worksheet renaming
- worksheet formatting
- Google Drive API access
- destructive Google Sheets cleanup
- automatic deletion of duplicate rows
- automatic repair of mismatched rows
- automatic rewriting of manually edited rows
- automatic append in the same invocation after uncertain verification reports absence
- unbounded worksheet scanning
- production deployment
- production secret changes
- scoring-rule changes
- standings-rule changes
- tie-break changes
- OCR changes
- Room changes
- Android workflow integration
- unrelated refactoring

v0.11.6 remains responsible for Android export-flow integration.

---

## Existing Public Request Contracts Remain Frozen

### Match export

The request remains exactly:

- `operation`
- `tournament_id`
- `match_id`
- `rows`

with:

`operation = "export_match"`

No additional top-level field is accepted.

### Standings export

The request remains exactly:

- `operation`
- `tournament_id`
- `rows`

with:

`operation = "export_standings"`

No additional top-level field is accepted.

Unknown top-level fields continue to be rejected.

The Edge Function must not accept:

- `verify`
- `verification_id`
- `operation_id`
- `idempotency_key`
- `retry_token`
- `force`
- `force_retry`
- `force_export`

---

## Source Of Truth

The authoritative comparison remains the same finalized Supabase application data already required by v0.10.5 and v0.10.6.

The verification process must not treat Google Sheets as the source of truth.

Canonical order is:

1. caller payload
2. authoritative finalized Supabase validation
3. canonical export representation
4. Google Sheets observation
5. exact comparison

If the caller payload no longer matches current finalized Supabase data, verification must stop before Google reconciliation exactly as existing export validation already requires.

An old/stale payload cannot be used to verify or resurrect an export operation.

---

## Canonical Match Verification Values

Match verification compares exactly 12 rows and exactly the existing 20 canonical match fields in the approved order:

1. `export_schema_version`
2. `export_type`
3. `tournament_id`
4. `tournament_name`
5. `match_id`
6. `match_label`
7. `match_finalized_at`
8. `row_number`
9. `placement`
10. `team_slot`
11. `team_name`
12. `player_1_name`
13. `player_2_name`
14. `player_3_name`
15. `player_4_name`
16. `placement_points`
17. `kills`
18. `kill_points`
19. `total_points`
20. `correction_status`

The expected Google values must use the same canonical conversion already used for append.

No verification-only normalization is allowed.

---

## Canonical Standings Verification Values

Standings verification compares exactly 12 rows and exactly the existing 20 canonical standings fields in the approved order.

The expected Google values must use the same canonical conversion already used for standings append.

Verification includes all cumulative totals and ranking/tie-break output already present in the frozen standings export schema.

No verification-only recalculation, normalization, reordering, or repair is allowed.

The official exported match count remains between 1 and 10 and must match the canonical standings snapshot.

---

## Exact Comparison Policy

Google values are compared cell-for-cell against the canonical values used for export.

Required properties:

- exactly 12 rows
- exactly 20 cells per row
- rows in exact canonical order
- no missing canonical cell
- no additional canonical cell
- strings compared exactly
- integer values compared by exact canonical numeric value
- Unicode preserved exactly
- punctuation preserved exactly
- whitespace preserved exactly
- empty exported strings remain empty
- no trimming
- no case folding
- no Unicode normalization
- no score coercion
- no silent repair

The verification helper may account only for representation differences that are inherent to the Google Sheets values API and are explicitly deterministic, such as a canonical integer being returned as the same numeric JSON value.

It must not treat `"12"` and `12` as interchangeable unless the existing Google API test contract proves that the values API deterministically returns one representation for RAW integer cells and that behavior is explicitly tested.

Fail closed on unexpected representations.

---

## Target Worksheets Remain Frozen

### Match export

Worksheet:

`Match Results`

Header:

`Match Results!A1:T1`

Append columns:

`Match Results!A:T`

### Standings export

Worksheet:

`Tournament Standings`

Header:

`Tournament Standings!A1:T1`

Append columns:

`Tournament Standings!A:T`

v0.10.8 must not rename or create worksheets.

---

## Normal Post-Append Verification

A new claimed export follows the existing v0.10.7 safety sequence through the Google append.

After Google reports a successful append, v0.10.8 must verify the written rows before the ledger becomes `succeeded`.

Canonical sequence:

1. structural payload parsing
2. authentication
3. authoritative Supabase reads
4. semantic validation against finalized data
5. canonical fingerprint
6. export-operation claim
7. Google OAuth
8. exact worksheet-header verification
9. durable `write_started`
10. exactly one Google append request
11. validate Google append acknowledgement
12. read back the exact appended 12-row range
13. compare all 12 × 20 cells exactly
14. only after exact verification, finalize the ledger as `succeeded`
15. return the existing canonical success response

There is still at most one Google append request per invocation.

---

## Google Append Range Decision

For successful append responses, v0.10.8 must use the Google-provided updated range only as an internal locator for read-back verification.

The append helper must validate the returned updated range before trusting it.

The returned range must:

- refer to the expected worksheet
- cover columns A through T only
- cover exactly 12 rows
- start below the header row
- be syntactically valid
- not refer to another spreadsheet or worksheet
- not be exposed in the public response

A malformed, contradictory, missing, or unexpected updated range after Google may have accepted the append is an uncertain outcome.

The helper must not infer or guess an appended row range.

---

## Post-Append Read-Back Result

### Exact match

If the exact returned 12-row range contains exactly the canonical expected values:

- complete the export ledger as `succeeded`
- store `rows_written = 12`
- store the official `exported_match_count` for standings
- clear active lease/failure metadata according to the existing v0.10.7 success transition
- return the unchanged existing success response

### Mismatch

If Google acknowledges an append but the read-back rows do not exactly match the canonical expected values:

- do not append again
- do not mark success
- classify the export as `outcome_uncertain`
- return `EXPORT_VERIFICATION_CONFLICT`

### Verification read failure

If the post-append verification read fails, times out, is malformed, is truncated, or cannot be safely interpreted:

- do not append again
- do not mark success
- preserve or transition the operation to `outcome_uncertain`
- return `EXPORT_VERIFICATION_FAILURE`

The caller must not blindly retry an append.

A later replay performs uncertain-write reconciliation.

---

## Existing Successful Replay

If the ledger already contains:

`state = succeeded`

for the identical validated logical export:

- do not contact Google
- do not perform another verification scan
- do not append
- return the existing canonical success response

Reason:

Under v0.10.8, new success is only recorded after exact Google read-back verification.

No production deployment occurred before v0.10.8 implementation, so no production backfill of pre-v0.10.8 succeeded ledger rows is required.

No historical verification migration is required.

---

## Uncertain-Outcome Reconciliation

When the identical validated request maps to:

`state = outcome_uncertain`

the Edge Function must not append.

Instead it performs read-only reconciliation against the target worksheet.

Canonical uncertain-reconciliation sequence:

1. structural request parsing
2. authentication
3. authoritative Supabase reads
4. semantic validation
5. canonical fingerprint calculation
6. ledger claim/replay lookup returns `outcome_uncertain`
7. Google OAuth
8. exact worksheet-header verification
9. bounded read of the worksheet data region
10. identify candidate rows using stable canonical identifiers
11. compare candidates against the exact canonical 12-row block
12. resolve the ledger only when the observed result is unambiguous
13. return the appropriate response
14. never append during this reconciliation invocation

---

## Candidate Identification — Match Export

For an uncertain `export_match`, candidate rows are rows whose canonical identifying fields indicate the same match snapshot.

At minimum the candidate filter must use the existing canonical columns containing:

- `export_schema_version = phase_10_v1`
- `export_type = match_result`
- requested `tournament_id`
- requested `match_id`

The verifier must then evaluate candidate rows as a complete 12-row export block.

An exact match requires:

- exactly 12 relevant candidate rows forming one contiguous canonical block
- exact canonical row ordering
- exact 20-cell values for all 12 rows

The verifier must not declare success from identifiers alone.

---

## Candidate Identification — Standings Export

For an uncertain `export_standings`, candidate rows are rows whose canonical identifying fields indicate the same tournament standings snapshot.

At minimum the candidate filter must use the existing canonical columns containing:

- `export_schema_version = phase_10_v1`
- `export_type = tournament_standings`
- requested `tournament_id`
- canonical `exported_match_count`

The verifier must then evaluate candidate rows as a complete 12-row export block.

An exact match requires:

- exactly 12 relevant candidate rows forming one contiguous canonical block
- exact canonical standings order
- exact 20-cell values for all 12 rows

Identifiers alone are insufficient to mark success.

---

## Bounded Worksheet Scan

Uncertain reconciliation must not scan an open-ended range.

Canonical verification scan limit:

- inspect at most 50,000 data rows per worksheet
- header row is not part of the data-row limit
- columns are limited to A:T

The implementation must make truncation detectable.

A scan must not treat "no matching rows within a truncated result" as proof that the export is absent.

The implementation must request enough information to determine whether the configured verification boundary was exceeded.

If populated data exists beyond the supported verification boundary, return:

`EXPORT_VERIFICATION_RANGE_EXCEEDED`

and keep the export blocked.

The verifier must not append after this result.

---

## Uncertain Reconciliation Outcomes

### 1. Exactly one exact canonical block exists

If exactly one complete 12-row canonical export block exists and there are no conflicting candidate rows for that logical export:

- resolve the uncertain ledger operation to `succeeded`
- store `rows_written = 12`
- store official `exported_match_count` for standings
- clear failure metadata
- return the existing canonical success response
- do not append

### 2. No candidate rows exist

If the bounded complete worksheet observation proves there are zero candidate rows for the logical export:

- resolve the uncertain operation to `retryable_failure`
- use failure code `EXPORT_VERIFICATION_NOT_FOUND`
- do not append in the same invocation
- return `EXPORT_VERIFICATION_NOT_FOUND`

A later replay may claim the now-retryable operation and perform a normal export attempt.

This preserves the rule of no blind same-invocation append after uncertain reconciliation.

### 3. Partial or mismatched candidate data exists

Examples:

- fewer than 12 candidate rows
- more than 12 non-duplicate candidate rows
- non-contiguous candidate rows where one canonical block cannot be proven
- wrong row order
- wrong team/player values
- wrong placement or kill values
- wrong points or totals
- wrong standings values
- missing cells
- extra/malformed values

Result:

- keep the operation blocked as `outcome_uncertain`
- do not append
- return `EXPORT_VERIFICATION_CONFLICT`

### 4. Multiple exact blocks exist

If more than one exact matching 12-row block exists:

- duplicate export data already exists
- do not delete anything
- do not append
- keep the operation blocked as `outcome_uncertain`
- return `EXPORT_VERIFICATION_CONFLICT`

v0.10.8 detects duplicates but does not destructively repair them.

### 5. Google read cannot be completed safely

Examples:

- timeout
- network failure
- Google 5xx
- malformed values response
- unsupported cell representation
- range-boundary overflow

Result:

- do not append
- keep the operation blocked
- return the applicable verification failure
- never convert an incomplete observation into "not found"

---

## Database Reconciliation Decision

v0.10.8 requires one additive Supabase migration only if current v0.10.7 RPCs cannot express the required uncertain-state resolutions.

The implementation must not edit the already-merged v0.10.7 migration.

The migration may add narrowly scoped authenticated RPCs for these exact responsibilities:

1. resolve an owned `outcome_uncertain` operation as verified `succeeded`
2. resolve an owned `outcome_uncertain` operation as verified currently absent and therefore `retryable_failure`

No broad ledger mutation RPC is allowed.

No caller-supplied `owner_id` is allowed.

Every reconciliation RPC must:

- derive ownership from `auth.uid()`
- reject unauthenticated callers
- verify operation ownership
- require current state exactly `outcome_uncertain`
- validate operation type
- validate success metadata
- use an empty or explicitly safe `search_path`
- expose no secrets or row data
- revoke unnecessary `public` and `anon` execution
- grant only the required authenticated execution
- update only the caller-owned export operation

Direct authenticated table INSERT/UPDATE/DELETE remains prohibited.

The Edge Function continues to use:

- caller bearer token
- Supabase anon key

No service-role credential is introduced.

---

## Reconciliation-To-Success Rules

An uncertain operation may be resolved to `succeeded` only after the Edge Function has observed exactly one exact canonical Google block.

Required stored success metadata remains:

- `state = succeeded`
- `rows_written = 12`
- standings: official `exported_match_count` between 1 and 10
- match: `exported_match_count = null`
- `completed_at` set
- no active lease
- no failure code

No verification row values, names, scores, spreadsheet IDs, or Google ranges are stored in the ledger.

---

## Reconciliation-To-Retryable Rules

An uncertain operation may be resolved to `retryable_failure` only after a complete bounded worksheet observation proves zero candidate rows exist.

Required stored result:

- `state = retryable_failure`
- `failure_code = EXPORT_VERIFICATION_NOT_FOUND`
- no active lease
- no rows-written metadata
- no exported-match-count metadata
- no `completed_at` success timestamp

A later invocation may reclaim it through the existing v0.10.7 retry path.

The reconciliation invocation itself must not append.

---

## New Error Contract

Add these public errors:

### `EXPORT_VERIFICATION_NOT_FOUND`

Suggested status:

`409`

Message:

`No matching exported rows were found. The export can be retried safely.`

Meaning:

- uncertain reconciliation completed over the supported bounded sheet region
- zero candidate rows exist
- ledger has been moved to retryable state
- this invocation did not append

### `EXPORT_VERIFICATION_CONFLICT`

Suggested status:

`409`

Message:

`The exported rows could not be reconciled safely.`

Meaning:

- candidate data exists but is partial, mismatched, duplicated, or ambiguous
- no automatic write or cleanup occurred

### `EXPORT_VERIFICATION_FAILURE`

Suggested status:

`502`

Message:

`The exported rows could not be verified.`

Meaning:

- verification could not obtain or interpret a complete reliable Google observation
- operation remains blocked if a write may already have occurred

### `EXPORT_VERIFICATION_RANGE_EXCEEDED`

Suggested status:

`409`

Message:

`The worksheet exceeds the supported verification range.`

Meaning:

- absence cannot be proven safely within the supported bounded scan
- operation remains blocked

Existing errors remain compatible.

Raw Google/Supabase response bodies must never be returned.

---

## Existing Success Responses Remain Unchanged

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

No verification metadata is added.

Do not expose:

- Google range
- worksheet name
- spreadsheet ID
- operation ID
- payload fingerprint
- candidate counts
- internal state
- lease information
- database metadata

---

## Google API Policy

v0.10.8 continues using Google Sheets API only.

Allowed Google activity:

- OAuth token exchange
- existing exact header reads
- existing one append for a newly claimed export
- exact-range read-back after append
- bounded A:T data reads for uncertain reconciliation

Prohibited:

- Google Drive API
- worksheet creation
- spreadsheet creation
- formatting
- clearing
- deleting
- updating existing cells
- duplicate cleanup writes
- more than one append in one invocation

Verification itself is read-only.

---

## Logging And Data-Minimization Policy

Allowed high-level logging remains limited to non-secret operational metadata.

Do not log:

- bearer tokens
- Supabase anon key
- Google access tokens
- service-account assertions
- private keys
- spreadsheet ID
- tournament name
- team names
- player names
- full export rows
- raw Google response bodies
- raw Supabase response bodies

Verification errors must not echo observed worksheet data.

Database verification/reconciliation metadata remains minimal.

Do not persist Google row contents.

---

## Concurrency Policy

v0.10.7 remains the concurrency authority.

For normal exports:

- the existing lease and `write_started` rules remain unchanged

For uncertain reconciliation:

- no Google append is allowed
- database resolution must require current state exactly `outcome_uncertain`
- concurrent reconciliation attempts must be safe
- only the first valid terminal transition may succeed
- a stale reconciliation response must fail closed rather than overwrite a newer state

The implementation must not weaken the existing unique logical-export identity.

---

## Retry Policy

v0.10.8 does not introduce automatic append retry loops.

Safe retry remains a later replay of the same canonical export request.

Important distinction:

- verification may perform bounded read requests needed to obtain a complete observation
- verification must not perform another Google append
- when an uncertain export is proven absent, the current invocation returns `EXPORT_VERIFICATION_NOT_FOUND`
- only a later replay may reclaim and append

---

## Testing Requirements

All Edge Function tests must use injected/mocked network behavior.

No test may contact:

- production Supabase
- production Google OAuth
- production Google Sheets
- a real spreadsheet
- a real service account

Required coverage includes at least:

1. existing `verify_connection` remains compatible
2. existing request schemas remain unchanged
3. normal match append is read back before success
4. normal standings append is read back before success
5. exact match read-back succeeds
6. exact standings read-back succeeds
7. wrong read-back row count becomes verification conflict/uncertain
8. wrong read-back column count becomes verification conflict/uncertain
9. wrong match identifier becomes conflict
10. wrong placement becomes conflict
11. wrong kills becomes conflict
12. wrong match total becomes conflict
13. wrong standings total becomes conflict
14. wrong standings order becomes conflict
15. Unicode and punctuation are compared exactly
16. append updated range must target the expected worksheet
17. append updated range must cover A:T
18. append updated range must cover exactly 12 rows
19. malformed updated range fails closed as uncertain
20. post-append read timeout never causes a second append
21. post-append Google 5xx never causes a second append
22. ledger success is not completed before exact read-back verification
23. succeeded replay performs no Google request
24. active in-progress replay performs no Google request
25. uncertain match replay performs no append
26. uncertain standings replay performs no append
27. uncertain exact match block resolves to success
28. uncertain exact standings block resolves to success
29. uncertain successful reconciliation returns the existing success response
30. zero match candidate rows resolves to retryable failure
31. zero standings candidate rows resolves to retryable failure
32. zero-candidate reconciliation does not append in the same invocation
33. partial match candidate data remains blocked
34. partial standings candidate data remains blocked
35. mismatched candidate values remain blocked
36. multiple exact blocks remain blocked
37. non-contiguous ambiguous candidates remain blocked
38. range-limit overflow never resolves as not found
39. malformed scan response never resolves as not found
40. Google 403/404/429 during uncertain verification does not trigger append
41. no Drive API call occurs
42. no clear/update/delete/formatting request occurs
43. no hidden verification column is introduced
44. no 21st cell is added
45. no secret/raw worksheet data appears in public errors
46. official Supabase validation still occurs before Google verification
47. stale payload mismatch blocks Google reconciliation
48. match/standings fingerprint identity remains unchanged
49. existing v0.10.7 retry behavior remains valid for proven retryable states
50. no automatic same-invocation append occurs after `EXPORT_VERIFICATION_NOT_FOUND`

Database tests must cover:

- reconciliation RPC existence/signatures
- authenticated-only execution
- no anon/public execution
- no cross-owner reconciliation
- direct ledger mutations remain prohibited
- uncertain -> succeeded valid transition
- uncertain -> retryable valid transition
- succeeded metadata constraints
- standings exported-match-count validation
- match exported-match-count null requirement
- wrong current state rejected
- malformed failure metadata rejected
- unrelated operation rows remain unchanged

---

## Local Verification Requirements

Required local verification after implementation:

### Deno

- format check
- lint
- type checking
- focused verification tests
- complete `google-sheets-export` test suite

### Supabase

When a v0.10.8 migration is added:

- local `db reset`
- database lint
- focused pgTAP verification tests
- complete database test suite

### Static checks

- `git diff --check`
- exact changed-file boundary review
- no Android/Gradle changes
- no service-role usage
- no Google Drive endpoint/scope
- no worksheet clear/update/delete/formatting write
- exactly the approved append callsites
- no hidden 21st export column
- no production secret material

Android Gradle/device tests are not required unless Android files are unexpectedly modified.

---

## Deployment Safety

Implementation and verification must not:

- deploy the Edge Function
- push migrations to production
- change production secrets
- contact production Supabase
- contact production Google APIs
- write to a live spreadsheet
- inspect real private tournament sheet data

All verification during implementation uses local Supabase and mocked Google requests.

---

## Phase 10 Boundary

v0.10.8 is the final canonical Phase 10 feature version.

It completes the server-side export sequence:

- stable export data model
- match CSV
- tournament CSV
- UTF-8/file validation
- secure Google Sheets foundation
- match Sheets export
- standings Sheets export
- retry/idempotency
- post-export verification and uncertain-write reconciliation

Android workflow integration remains Phase 11 v0.11.6.

After v0.10.8 implementation is merged and verified, Phase 10 must receive its normal closure audit before Phase 11 begins.

---

## Acceptance Criteria

v0.10.8 is complete when:

- the existing match and standings request contracts remain unchanged
- authoritative finalized Supabase validation still occurs before Google verification
- every new successful Google export is read back before ledger success
- exact 12-row × 20-column comparison is enforced
- row ordering and all canonical values are verified
- append updated ranges are validated and used only internally
- a post-write verification failure never causes a second append
- an `outcome_uncertain` replay performs read-only Google reconciliation
- exactly one exact block resolves the ledger to success without append
- a complete observation with zero candidate rows resolves to retryable state without same-invocation append
- partial, mismatched, duplicate, or ambiguous candidate data remains blocked
- worksheet scanning is bounded and truncation cannot be mistaken for absence
- no hidden verification/tracking column is added
- no destructive Google reconciliation is performed
- existing success responses remain unchanged
- database reconciliation remains caller-owned and RLS-safe
- service-role credentials are not introduced
- Google Drive API is not used
- deterministic mocked Edge Function tests pass
- local Supabase tests pass when a migration is required
- formatting, lint, type checking, and `git diff --check` pass
- no Android files are changed
- no production deployment or secret change occurs
