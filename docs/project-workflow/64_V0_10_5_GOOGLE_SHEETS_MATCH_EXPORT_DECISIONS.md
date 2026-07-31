# v0.10.5 — Google Sheets Match Export Decisions

## Purpose

v0.10.5 extends the authenticated `google-sheets-export` Supabase Edge Function created in v0.10.4 so it can send one finalized match to the configured Google spreadsheet.

This version implements the server-side match-export operation only. Android workflow integration remains deferred to Phase 11 v0.11.6.

## Canonical Function

Function name:

`google-sheets-export`

Function path:

`supabase/functions/google-sheets-export/index.ts`

Existing v0.10.4 operation:

`verify_connection`

New v0.10.5 operation:

`export_match`

The existing `verify_connection` behavior must remain compatible.

## Canonical Scope

v0.10.5 includes:

- authenticated `export_match` operation
- exact Phase 10 match-result schema support
- server-side tournament ownership verification through Supabase RLS
- server-side finalized-match verification
- structural and semantic validation of 12 match rows
- exact match-result column mapping
- verification of the target worksheet header
- one Google Sheets append request for exactly 12 rows
- safe Google API response validation
- bounded timeouts
- typed error responses
- deterministic mocked Deno tests
- secret-safe logging
- no automatic retry

## Out Of Scope

v0.10.5 does not implement:

- Android export UI
- Android network client integration
- Compose screens or buttons
- tournament standings export
- worksheet creation
- spreadsheet creation
- worksheet formatting
- Google Drive API access
- export-history tables
- database migrations
- persistent retry
- idempotency
- duplicate-export prevention
- post-export row verification
- production deployment
- production secret changes
- CSV schema changes
- scoring-rule changes
- OCR changes
- Room changes
- cloud-sync redesign

## Source Of Truth

The stable schema is defined by:

`docs/project-workflow/59_V0_10_0_EXPORT_DATA_MODEL_DECISIONS.md`

The logical match-result schema must remain:

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

Required constants:

- `export_schema_version`: `phase_10_v1`
- `export_type`: `match_result`

The Edge Function must not rename, reorder, add, or remove official export columns.

## Request Contract

Canonical request:

```json
{
  "operation": "export_match",
  "tournament_id": "uuid",
  "match_id": "uuid",
  "rows": [
    {
      "export_schema_version": "phase_10_v1",
      "export_type": "match_result",
      "tournament_id": "uuid",
      "tournament_name": "Tournament Name",
      "match_id": "uuid",
      "match_label": "Match 1",
      "match_finalized_at": "",
      "row_number": 1,
      "placement": 1,
      "team_slot": 4,
      "team_name": "Team Name",
      "player_1_name": "Player One",
      "player_2_name": "Player Two",
      "player_3_name": "Player Three",
      "player_4_name": "Player Four",
      "placement_points": 12,
      "kills": 8,
      "kill_points": 8,
      "total_points": 20,
      "correction_status": "original_finalized"
    }
  ]
}
```

The request must contain exactly four top-level fields:

- `operation`
- `tournament_id`
- `match_id`
- `rows`

Unknown top-level fields must be rejected.

## Row Contract

`rows` must contain exactly 12 objects.

Every row must contain exactly the 20 approved fields.

Unknown row fields must be rejected.

Required data types:

- identifiers and names: strings
- `match_finalized_at`: string
- `row_number`: integer
- `placement`: integer
- `team_slot`: integer
- point and kill fields: integers
- `correction_status`: string

Allowed correction statuses:

- `original_finalized`
- `corrected_finalized`

Player names may preserve Unicode and punctuation.

Rows must not contain:

- raw OCR data
- screenshot metadata
- storage paths
- auth identifiers
- access tokens
- internal revision values
- sync queue data
- private correction evidence

## Structural Validation

The Edge Function must reject the payload unless:

- exactly 12 rows are present
- row numbers are exactly 1 through 12
- placements are exactly 1 through 12
- rows are ordered by placement ascending
- `row_number` equals the placement-ordered row position
- team slots are unique
- every team slot is between 1 and 12
- every row uses `phase_10_v1`
- every row uses `match_result`
- every row uses the top-level `tournament_id`
- every row uses the top-level `match_id`
- all tournament names are identical
- all match labels are identical
- all correction statuses are valid
- kills are non-negative
- placement points match the approved placement table
- kill points equal kills
- total points equal placement points plus kill points

Approved placement points:

- 1: 12
- 2: 9
- 3: 8
- 4: 7
- 5: 6
- 6: 5
- 7: 4
- 8: 3
- 9: 2
- 10: 1
- 11: 0
- 12: 0

The function must fail closed. It must not repair, reorder, normalize, or silently replace invalid values.

## Supabase Ownership And Finalization Validation

Authentication must complete before Supabase data verification and before every Google request.

The function must use:

- the caller's Supabase bearer token
- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`

The service-role key must not be used.

The function must query Supabase through the authenticated user's RLS context.

Required server-side checks:

- the tournament exists and is visible to the authenticated user
- the match exists and is visible to the authenticated user
- the match belongs to the requested tournament
- the match status is `finalized`
- the official tournament name matches the payload
- the official match number matches the payload's stable label `Match <number>`
- the finalized match contains exactly 12 confirmed result rows
- official placements match payload placements by team slot
- official kills match payload kills by team slot
- official team names match payload team names by team slot
- each non-empty exported player name belongs to the official roster for that team slot
- the four exported player names within a row are not duplicated

RLS-denied or nonexistent tournament/match data must not reveal whether another user's record exists.

The function must not use raw OCR tables or evidence as an export source.

## Finalized Timestamp Policy

The existing approved match exporter may provide an empty `match_finalized_at` value because the current Android domain model does not expose a finalized timestamp.

v0.10.5 decisions:

- an empty string is allowed
- a non-empty value must be a valid RFC 3339 timestamp
- when non-empty, it must match the official Supabase `matches.finalized_at` value after timestamp normalization
- the Edge Function must not invent, replace, or silently mutate this field
- a future version may require a non-empty value only through a separately approved schema-compatible decision

## Target Worksheet

The canonical worksheet name is:

`Match Results`

The canonical header range is:

`Match Results!A1:T1`

The canonical append range is:

`Match Results!A:T`

The spreadsheet and worksheet must already exist.

v0.10.5 must not create or rename worksheets.

## Header Verification

Before appending rows, the function must read the worksheet header row.

The header must exactly match the 20 approved column names in the approved order.

Missing, reordered, renamed, duplicated, or additional header cells must block export.

The function must not write or repair the header automatically.

Header verification must use a read-only Google Sheets values request.

## Google Sheets Write

After authentication, Supabase validation, Google OAuth, and header verification succeed, the function must perform one append request.

Required endpoint behavior:

- Google Sheets v4 values append API
- method `POST`
- `valueInputOption=RAW`
- `insertDataOption=INSERT_ROWS`
- `majorDimension=ROWS`
- exactly 12 row arrays
- exactly 20 cells per row

The append request must preserve the approved column order.

The function must not:

- use `USER_ENTERED`
- send formulas
- use batch formatting
- clear cells
- update existing cells
- delete rows
- create sheets
- call the Drive API
- perform more than one write request

## Append Response Validation

A successful Google response must be validated.

The response must confirm that exactly 12 rows were updated.

The function must not expose:

- updated range
- spreadsheet ID
- worksheet name
- Google response metadata
- access token
- raw Google response body

Malformed or contradictory success responses must fail safely.

## Success Response

Canonical response:

```json
{
  "ok": true,
  "operation": "export_match",
  "tournament_id": "uuid",
  "match_id": "uuid",
  "rows_written": 12
}
```

The response must not include exported names, scores, worksheet metadata, or Google credentials.

## Error Contract

Existing v0.10.4 error behavior must remain compatible.

New approved error codes:

- `INVALID_MATCH_EXPORT_PAYLOAD`
- `TOURNAMENT_NOT_FOUND_OR_FORBIDDEN`
- `MATCH_NOT_FOUND_OR_FORBIDDEN`
- `MATCH_NOT_FINALIZED`
- `MATCH_EXPORT_DATA_MISMATCH`
- `GOOGLE_SHEET_SCHEMA_MISMATCH`
- `GOOGLE_MATCH_EXPORT_FAILURE`
- `GOOGLE_MATCH_EXPORT_RESPONSE_INVALID`
- `SUPABASE_DATA_FAILURE`

Suggested status mapping:

- 400: invalid payload
- 401: unauthenticated
- 404: tournament or match not found in the caller's RLS context
- 409: match not finalized, payload mismatch, or worksheet schema mismatch
- 429: Google rate limit
- 500: server configuration or internal failure
- 502: Supabase data failure or Google export failure
- 504: upstream timeout

Raw Supabase and Google response bodies must never be returned.

## Timeouts

Bounded timeouts are required for:

- Supabase user validation
- tournament lookup
- match lookup
- official match-result lookup
- team-slot lookup
- roster-player lookup
- Google OAuth token exchange
- Google header read
- Google append request

Timeout values must use named constants.

Every timeout controller must be cleaned up.

## Retry And Duplicate Limitation

v0.10.5 must perform one append attempt only.

It must not automatically retry a failed or timed-out append.

A timeout or network failure after Google receives the request may leave the final write outcome uncertain.

Until v0.10.7 is implemented:

- the function does not guarantee duplicate prevention
- callers must not blindly retry an uncertain export
- no export operation table is created
- no idempotency key is accepted
- no duplicate-row scan is performed

This limitation must be documented in the completion report.

## Logging

Allowed:

- non-secret correlation ID
- operation name
- high-level validation result
- upstream HTTP status
- elapsed duration
- row count

Prohibited:

- authorization header
- Supabase bearer token
- Google access token
- Google assertion
- private key
- service-account email
- spreadsheet ID
- worksheet data
- tournament name
- team names
- player names
- raw request payload
- raw upstream response
- environment values

## Shared-Module Policy

The implementation may extend the existing narrow shared modules under:

`supabase/functions/_shared/`

Suggested responsibilities:

- typed error definitions
- Supabase REST reads under caller RLS
- canonical match-row validation
- Google header verification
- Google values append

The implementation must not introduce a broad framework or unrelated refactor.

## Testing

All tests must use injected or mocked network behavior.

No test may contact:

- production Supabase
- production Google OAuth
- production Google Sheets
- a real spreadsheet
- a real service account

Required test coverage:

1. existing `verify_connection` behavior remains passing
2. valid `export_match` request writes exactly 12 rows
3. exact 20-column order is used
4. unsupported top-level fields are rejected
5. missing rows are rejected
6. row count other than 12 is rejected
7. unknown row fields are rejected
8. duplicate placement is rejected
9. missing placement is rejected
10. duplicate team slot is rejected
11. invalid team slot is rejected
12. row order mismatch is rejected
13. scoring mismatch is rejected
14. invalid correction status is rejected
15. unauthenticated request contacts neither Supabase data nor Google
16. RLS-hidden tournament maps safely
17. RLS-hidden match maps safely
18. non-finalized match is rejected
19. tournament mismatch is rejected
20. official tournament name mismatch is rejected
21. official match label mismatch is rejected
22. official placement mismatch is rejected
23. official kill mismatch is rejected
24. official team-name mismatch is rejected
25. invalid roster-player name is rejected
26. duplicate exported player name is rejected
27. empty finalized timestamp is accepted
28. invalid non-empty finalized timestamp is rejected
29. non-matching finalized timestamp is rejected
30. missing worksheet header is rejected
31. reordered worksheet header is rejected
32. valid worksheet header is accepted
33. append uses `RAW`
34. append uses `INSERT_ROWS`
35. append writes one request only
36. append contains 12 rows and 20 cells per row
37. append response with wrong updated-row count is rejected
38. Sheets 403 is mapped safely
39. Sheets 404 is mapped safely
40. Sheets 429 is mapped safely
41. Sheets 5xx is mapped safely
42. upstream timeout is mapped safely
43. no Drive API request is performed
44. no formatting or clear request is performed
45. no secret or raw upstream body appears in responses
46. no automatic retry occurs
47. call order is authentication, Supabase reads, OAuth, header read, append
48. Google is not contacted when Supabase validation fails

## Verification

Required local checks:

- Deno formatting check
- Deno lint
- Deno type checking
- focused Deno tests
- complete Google Sheets Edge Function Deno tests
- static scan for Google Drive scopes or endpoints
- static scan for forbidden Sheets write operations
- static scan confirming one append operation
- static scan for private-key material
- `git diff --check`
- final changed-file review

Android Gradle tests are not required unless Android files are unexpectedly modified.

No production deployment or live Google write is allowed during implementation.

## Deployment Safety

Code generation and verification must not:

- deploy the function
- set project secrets
- contact production Supabase
- contact production Google APIs
- modify the live spreadsheet

A later explicit operational step may configure and smoke-test the function after review and merge.

## Later-Version Handoff

### v0.10.6 — Google Sheets Standings Export

Adds cumulative tournament standings export using the existing authenticated function and the approved `tournament_standings` schema.

### v0.10.7 — Export Retry and Idempotency

Adds retry-safe operation identity and duplicate prevention for match and standings exports.

### v0.10.8 — Export Verification

Compares exported rows, ordering, and totals against finalized application data and Google Sheets results.

### v0.11.6 — Export Flow

Connects the Android application workflow to approved CSV and Google Sheets export operations.

## Acceptance Criteria

v0.10.5 is complete when:

- `export_match` exists without breaking `verify_connection`
- authentication occurs before data or Google access
- caller ownership is enforced through Supabase RLS
- only finalized matches are eligible
- exactly 12 official rows are accepted
- exact Phase 10 match columns and order are preserved
- payload scores and identifiers are validated
- official Supabase match data is compared safely
- the `Match Results` header is verified exactly
- one RAW append writes exactly 12 rows
- no Drive API or formatting operation is used
- no automatic retry occurs
- success and error responses expose no secrets or row metadata
- deterministic mocked Deno tests pass
- formatting, lint, type checking, and tests pass
- no Android files are changed
- no migrations are added
- no CSV schema is changed
- no production action occurs
