# v0.10.6 — Google Sheets Standings Export Decisions

## Purpose

v0.10.6 extends the authenticated `google-sheets-export` Supabase Edge Function so it can export cumulative tournament standings to the configured Google spreadsheet.

This version implements the server-side standings-export operation only.

Android workflow integration remains deferred to Phase 11 v0.11.6.

The implementation must preserve the stable Phase 10 tournament-standings schema, existing scoring behavior, existing standings order, existing tie-break behavior, existing `verify_connection` behavior, and existing `export_match` behavior.

## Canonical Function

Function name:

`google-sheets-export`

Function path:

`supabase/functions/google-sheets-export/index.ts`

Existing operations:

- `verify_connection`
- `export_match`

New v0.10.6 operation:

- `export_standings`

The existing operations must remain compatible.

## Canonical Scope

v0.10.6 includes:

- authenticated `export_standings` operation
- exact Phase 10 tournament-standings schema support
- structural and semantic validation of exactly 12 standings rows
- server-side tournament ownership verification through existing Supabase RLS
- server-side discovery of the tournament's visible matches
- finalized-match-only inclusion
- server-side verification of every finalized match and confirmed result row
- server-side cumulative standings reconstruction
- exact reuse of the approved position-points table
- exact reuse of the approved one-kill-one-point rule
- exact reuse of the approved match-total formula
- exact reuse of the approved standings ordering
- exact reuse of the approved tie-break ordering
- official roster and team-name verification
- exact standings worksheet-header verification
- one Google Sheets append request for exactly 12 rows
- safe Google append-response validation
- bounded timeouts
- typed safe error responses
- deterministic mocked Deno tests
- secret-safe behavior
- no automatic retry
- no idempotency implementation

## Out Of Scope

v0.10.6 does not implement:

- Android export UI
- Android network-client integration
- Android navigation
- Compose screens or buttons
- Android file save or share behavior
- match CSV changes
- tournament CSV changes
- match Google Sheets schema changes
- worksheet creation
- worksheet renaming
- spreadsheet creation
- worksheet formatting
- Google Drive API access
- export-history tables
- database migrations
- RLS changes
- persistent retry
- idempotency
- duplicate-export prevention
- duplicate-row scanning
- post-export Google row verification
- production deployment
- production secret changes
- scoring-rule changes
- standings-rule changes
- tie-break-rule changes
- OCR changes
- Room changes
- cloud-sync redesign
- unrelated refactoring

## Source Of Truth

The stable export schema is defined by:

`docs/project-workflow/59_V0_10_0_EXPORT_DATA_MODEL_DECISIONS.md`

The existing Android tournament CSV behavior is defined by:

`docs/project-workflow/61_V0_10_2_TOURNAMENT_CSV_EXPORT_DECISIONS.md`

The existing implementation reference is:

`app/src/main/java/com/hoggamers/rankforge/domain/export/TournamentCsvExporter.kt`

Existing finalized Supabase tournament, match, result, team-slot, and player records are the server-side source of truth.

The Edge Function must not trust client-supplied totals, order, ranks, team names, player membership, match count, tie status, or scoring calculations without reconstructing and verifying them from official records visible through the caller's RLS context.

The Edge Function must not use raw OCR tables, OCR evidence, screenshot metadata, correction evidence, storage objects, or local Android state as the server-side export source.

## Official Schema

The logical standings schema must remain exactly:

1. `export_schema_version`
2. `export_type`
3. `tournament_id`
4. `tournament_name`
5. `exported_match_count`
6. `standings_rank`
7. `team_slot`
8. `team_name`
9. `player_1_name`
10. `player_2_name`
11. `player_3_name`
12. `player_4_name`
13. `matches_played`
14. `total_position_points`
15. `total_kills`
16. `total_kill_points`
17. `total_points`
18. `best_placement`
19. `first_place_count`
20. `tie_break_status`

Required constants:

- `export_schema_version`: `phase_10_v1`
- `export_type`: `tournament_standings`

The Edge Function must not rename, reorder, add, or remove official export columns.

## Request Contract

Canonical request:

```json
{
  "operation": "export_standings",
  "tournament_id": "11111111-1111-4111-8111-111111111111",
  "rows": [
    {
      "export_schema_version": "phase_10_v1",
      "export_type": "tournament_standings",
      "tournament_id": "11111111-1111-4111-8111-111111111111",
      "tournament_name": "Championship",
      "exported_match_count": 3,
      "standings_rank": 1,
      "team_slot": 4,
      "team_name": "Team Name",
      "player_1_name": "Player One",
      "player_2_name": "Player Two",
      "player_3_name": "Player Three",
      "player_4_name": "Player Four",
      "matches_played": 3,
      "total_position_points": 29,
      "total_kills": 24,
      "total_kill_points": 24,
      "total_points": 53,
      "best_placement": 1,
      "first_place_count": 2,
      "tie_break_status": "unique_order"
    }
  ]
}
```

The request must contain exactly three top-level fields:

- `operation`
- `tournament_id`
- `rows`

Unknown top-level fields must be rejected.

The function must not accept:

- `match_id`
- an idempotency key
- a retry token
- a worksheet name
- a spreadsheet ID
- a service-account identity
- caller-supplied Google configuration
- raw OCR data
- private evidence
- internal revision metadata

## Top-Level Validation

The top-level request is valid only when:

- the value is a JSON object
- `operation` is exactly `export_standings`
- `tournament_id` is a canonical UUID string
- `rows` is an array
- no unknown top-level fields exist

Invalid top-level input must fail with `INVALID_STANDINGS_EXPORT_PAYLOAD`.

## Row Contract

`rows` must contain exactly 12 objects.

Every row must contain exactly the 20 approved standings fields.

Unknown row fields must be rejected.

Required types:

- identifiers and names: strings
- count, rank, slot, placement, kill, and point fields: integers
- `tie_break_status`: string

Names must preserve Unicode, punctuation, symbols, and whitespace exactly as supplied.

The function must not normalize, trim, reorder, repair, or silently replace row values.

Player-name fields may be empty strings.

Rows must not contain:

- raw OCR data
- screenshot metadata
- storage paths
- auth identifiers
- access tokens
- refresh tokens
- service-account data
- internal revisions
- sync queue payloads
- correction evidence
- debug metadata

## Allowed Tie-Break Status Values

Structurally allowed values:

- `unique_order`
- `tie_break_applied`
- `unresolved_tie`
- `resolved_by_existing_order`

Official verification must compare the supplied value with the status reconstructed from current approved standings behavior.

The current official behavior normally produces:

- `unique_order`
- `tie_break_applied`
- `unresolved_tie`

`resolved_by_existing_order` remains schema-compatible for a future approved engine output but must not be accepted as an official match when the current server-side reconstruction produces a different value.

## Structural Validation

The Edge Function must reject the payload unless all of the following are true:

- exactly 12 rows are present
- every row has exactly the approved 20 fields
- `standings_rank` values are exactly 1 through 12
- rows are ordered by `standings_rank` ascending
- `standings_rank` equals the row's one-based array position
- team slots are unique
- team slots are exactly 1 through 12
- every row uses `phase_10_v1`
- every row uses `tournament_standings`
- every row uses the top-level `tournament_id`
- all tournament names are identical
- `exported_match_count` is identical across all rows
- `exported_match_count` is between 1 and 10
- `matches_played` is identical to `exported_match_count` for every row
- total position points are non-negative integers
- total kills are non-negative integers
- total kill points are non-negative integers
- total kill points equal total kills
- total points are non-negative integers
- total points equal total position points plus total kill points
- best placement is between 1 and 12
- first-place count is between 0 and `exported_match_count`
- every tie-break status is structurally allowed
- non-empty player names are not duplicated within the same row

The function must fail closed.

It must not:

- repair scores
- repair match counts
- repair ranks
- repair team slots
- reorder rows
- substitute team names
- substitute player names
- recalculate the client payload silently
- change tie-break status silently

## Supabase Authentication And Ownership

Authentication must complete before:

- tournament lookup
- match lookup
- result lookup
- team-slot lookup
- roster-player lookup
- Google OAuth
- Google Sheets header read
- Google Sheets append

The function must use:

- the caller's Supabase bearer token
- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`

The service-role key must not be used.

All Supabase reads must execute in the authenticated caller's RLS context.

RLS-hidden and nonexistent tournament data must map to the same safe not-found response.

The function must not reveal whether another account owns a hidden tournament.

## Required Official Supabase Reads

The implementation must read, through caller RLS:

1. the requested tournament
2. all visible matches belonging to that tournament
3. all result rows belonging to finalized matches included in the export
4. all 12 team slots belonging to that tournament
5. official roster players belonging to those team slots

The implementation may extend the existing narrow shared Supabase reader.

It must not introduce a broad data-access framework.

Required deterministic query ordering:

- matches: `match_number` ascending, then `id` ascending
- match results: `match_id` ascending, then `placement` ascending with nulls last
- team slots: `slot_number` ascending
- players: deterministic order supported by the existing schema and query contract

No server-side read may use the service-role key.

## Finalized-Match Inclusion Policy

Only finalized matches contribute to official standings.

Decisions:

- draft matches are excluded
- at least one finalized match is required
- no more than 10 finalized matches may be included
- client `exported_match_count` must equal the official finalized-match count
- finalized matches are ordered by `match_number` ascending and then `id` ascending
- the final match in that order supplies `latestMatchPlacement` for tie-breaking
- duplicate official match IDs must fail safely
- duplicate official match numbers must fail safely because tournament match numbers are expected to be unique
- a finalized match with invalid official data must block the entire standings export
- invalid finalized data must not be silently excluded
- draft data must not affect official totals or order

When no finalized match exists, the function must fail with `NO_FINALIZED_MATCHES`.

## Official Finalized-Match Validation

Every included finalized match must satisfy all of the following:

- belongs to the requested tournament
- status is exactly `finalized`
- has exactly 12 official result rows
- every result row belongs to that match
- every result row has `review_status` exactly `confirmed`
- placements are integers 1 through 12
- placements 1 through 12 exist exactly once
- team-slot references are unique within the match
- every team-slot reference resolves to one of the tournament's 12 official slots
- all 12 official slots appear exactly once
- kills are non-negative integers
- no missing placement exists
- no duplicate placement exists
- no duplicate team exists
- no extra result row exists
- no unresolved or draft result contributes

Any violation must fail with `STANDINGS_EXPORT_DATA_MISMATCH` unless the failure is an upstream read/parse failure, which uses `SUPABASE_DATA_FAILURE`.

## Official Team-Slot Validation

The tournament must expose exactly 12 official team slots.

Required official slot rules:

- slot numbers are exactly 1 through 12
- slot IDs are unique
- slot numbers are unique
- every slot belongs to the requested tournament
- every team name is a non-empty string
- the official team name must match the payload row for that slot exactly

Missing, duplicate, out-of-range, hidden, malformed, or mismatched team-slot data must block export.

## Official Player Validation

For each standings row:

- each non-empty exported player name must belong to the official roster for that row's team slot
- a non-empty exported player name must not be duplicated within the row
- player names must match official roster spelling and Unicode exactly
- an exported player from another team slot must be rejected
- an unknown player must be rejected
- empty player fields are allowed
- the Edge Function must not invent missing player names
- the Edge Function must not require exactly four non-empty players because the stable schema permits empty remaining columns
- the Edge Function does not enforce a server-side display order that the current Supabase schema cannot authoritatively reconstruct
- raw OCR player candidates must not be used as official roster data

## Official Scoring Rules

The Edge Function must reconstruct standings using the approved Phase 4 rules.

### Position points

Approved placement points:

- placement 1: 12
- placement 2: 9
- placement 3: 8
- placement 4: 7
- placement 5: 6
- placement 6: 5
- placement 7: 4
- placement 8: 3
- placement 9: 2
- placement 10: 1
- placement 11: 0
- placement 12: 0

### Kill points

- one kill equals one kill point
- negative kills are invalid

### Match total

- match total equals placement points plus kill points

### Cumulative fields

For each team slot:

- `matches_played` = official finalized-match count
- `total_position_points` = sum of official placement points
- `total_kills` = sum of official kills
- `total_kill_points` = sum of official kill points
- `total_points` = total position points plus total kill points
- `best_placement` = lowest numeric official placement
- `first_place_count` = number of official placement-1 finishes
- `latest_match_placement` = placement in the latest included match under the approved match order

The Edge Function must not use a different scoring table or alternate calculation path.

## Official Standings Order

The server must reconstruct the official standings order using these criteria:

1. total points descending
2. first-place count descending
3. total kills descending
4. latest-match placement ascending
5. team slot ascending for deterministic display order

`standings_rank` is the one-based row position in this order.

The server must compare every supplied row with the reconstructed official row at the same rank.

The export must fail when the supplied row order differs from the official order.

The Edge Function must not create export-only ranking behavior.

## Official Tie-Break Status

The server must reconstruct tie-break status consistently with the current tournament CSV exporter.

For each row:

- `unresolved_tie` when another team has the same complete tie key:
  - total points
  - first-place count
  - total kills
  - latest-match placement
- otherwise `tie_break_applied` when more than one team has the same total points and the existing secondary criteria establish the official order
- otherwise `unique_order`

The deterministic team-slot fallback does not erase `unresolved_tie`.

The supplied `tie_break_status` must exactly match the reconstructed official value.

## Payload Versus Official Data Comparison

After structural validation and official reconstruction, every payload row must exactly match its official row for:

- tournament ID
- tournament name
- exported match count
- standings rank
- team slot
- team name
- matches played
- total position points
- total kills
- total kill points
- total points
- best placement
- first-place count
- tie-break status

Player names are verified by official team-roster membership and within-row uniqueness as defined above.

Any mismatch must fail with `STANDINGS_EXPORT_DATA_MISMATCH`.

The function must not write to Google when official verification fails.

## Target Worksheet

Canonical worksheet name:

`Tournament Standings`

Canonical header range:

`Tournament Standings!A1:T1`

Canonical append range:

`Tournament Standings!A:T`

The spreadsheet and worksheet must already exist.

v0.10.6 must not:

- create the worksheet
- rename the worksheet
- create the spreadsheet
- format the worksheet
- repair the header

## Header Verification

Before appending rows, the function must perform one read-only Google Sheets values request for:

`Tournament Standings!A1:T1`

The returned header must contain exactly one row with exactly the 20 approved column names in the approved order.

The function must reject:

- missing header
- empty header
- reordered header
- renamed header
- duplicated header cell
- missing header cell
- additional header cell
- malformed successful response
- non-array header values

Header mismatch must fail with `GOOGLE_STANDINGS_SHEET_SCHEMA_MISMATCH`.

The function must not write or repair the header automatically.

## Google Sheets Write

After authentication, official Supabase verification, Google OAuth, and header verification succeed, the function must perform exactly one append request.

Required behavior:

- Google Sheets v4 values append API
- method `POST`
- append range `Tournament Standings!A:T`
- `valueInputOption=RAW`
- `insertDataOption=INSERT_ROWS`
- `majorDimension=ROWS`
- exactly 12 row arrays
- exactly 20 cells per row
- exact approved column order

The function must not:

- use `USER_ENTERED`
- send formulas
- use `batchUpdate`
- clear cells
- update existing cells
- delete rows
- create sheets
- rename sheets
- call the Drive API
- perform more than one write request
- automatically retry

## Append Response Validation

A successful Google response must confirm:

- an object response exists
- `updates` exists as an object
- `updates.updatedRows` is exactly `12`

Malformed, missing, non-integer, contradictory, or wrong-row-count success responses must fail with:

`GOOGLE_STANDINGS_EXPORT_RESPONSE_INVALID`

The function must not expose:

- updated range
- spreadsheet ID
- worksheet name
- Google response metadata
- access token
- raw Google response body
- exported names
- exported totals

## Success Response

Canonical success response:

```json
{
  "ok": true,
  "operation": "export_standings",
  "tournament_id": "11111111-1111-4111-8111-111111111111",
  "exported_match_count": 3,
  "rows_written": 12
}
```

The success response must not include:

- tournament name
- team names
- player names
- scores
- worksheet metadata
- spreadsheet ID
- Google credentials
- access tokens
- raw upstream data

## Error Contract

Existing v0.10.4 and v0.10.5 error behavior must remain compatible.

New v0.10.6 error codes:

- `INVALID_STANDINGS_EXPORT_PAYLOAD`
- `NO_FINALIZED_MATCHES`
- `STANDINGS_EXPORT_DATA_MISMATCH`
- `GOOGLE_STANDINGS_SHEET_SCHEMA_MISMATCH`
- `GOOGLE_STANDINGS_EXPORT_FAILURE`
- `GOOGLE_STANDINGS_EXPORT_RESPONSE_INVALID`

Existing reusable error codes:

- `METHOD_NOT_ALLOWED`
- `INVALID_JSON`
- `INVALID_OPERATION`
- `UNAUTHORIZED`
- `SUPABASE_AUTH_FAILURE`
- `TOURNAMENT_NOT_FOUND_OR_FORBIDDEN`
- `SUPABASE_DATA_FAILURE`
- `GOOGLE_CONFIG_MISSING`
- `GOOGLE_CREDENTIAL_INVALID`
- `GOOGLE_JWT_SIGNING_FAILURE`
- `GOOGLE_TOKEN_FAILURE`
- `GOOGLE_TOKEN_RESPONSE_INVALID`
- `GOOGLE_SHEETS_ACCESS_DENIED`
- `GOOGLE_SHEETS_NOT_FOUND`
- `GOOGLE_API_RATE_LIMITED`
- `UPSTREAM_TIMEOUT`
- `INTERNAL_ERROR`

Approved status mapping:

- `INVALID_STANDINGS_EXPORT_PAYLOAD`: 400
- `UNAUTHORIZED`: 401
- `GOOGLE_SHEETS_ACCESS_DENIED`: 403
- `TOURNAMENT_NOT_FOUND_OR_FORBIDDEN`: 404
- `GOOGLE_SHEETS_NOT_FOUND`: 404
- `NO_FINALIZED_MATCHES`: 409
- `STANDINGS_EXPORT_DATA_MISMATCH`: 409
- `GOOGLE_STANDINGS_SHEET_SCHEMA_MISMATCH`: 409
- `GOOGLE_API_RATE_LIMITED`: 429
- configuration and credential failures: 500
- Supabase data, Google OAuth, and Google standings export failures: 502
- invalid Google standings append response: 502
- upstream timeout: 504

Raw Supabase and Google response bodies must never be returned.

## Required Client-Safe Messages

Approved client-safe meaning:

- invalid payload: the standings export payload is invalid
- no finalized matches: the tournament has no finalized matches to export
- data mismatch: the standings export data does not match finalized records
- worksheet mismatch: the Tournament Standings worksheet header is invalid
- Google export failure: Google Sheets could not export the standings
- invalid append response: Google Sheets returned an invalid standings export response

Implementation wording may follow the existing concise error-message style but must preserve this meaning and must not expose sensitive data.

## Timeouts

Bounded timeouts are required for:

- Supabase user validation
- tournament lookup
- tournament match lookup
- finalized match-result lookup
- team-slot lookup
- roster-player lookup
- Google OAuth token exchange
- Google standings-header read
- Google standings append

Timeout values must use named constants.

Every timeout controller must be cleaned up.

The implementation may reuse existing 10-second defaults unless a separately justified value is approved during implementation review.

## Retry And Duplicate Limitation

v0.10.6 must perform one append attempt only.

It must not automatically retry a failed or timed-out append.

A timeout or network failure after Google receives the request may leave the final write outcome uncertain.

Until v0.10.7 is implemented:

- duplicate prevention is not guaranteed
- callers must not blindly retry an uncertain export
- no export operation table is created
- no idempotency key is accepted
- no duplicate-row scan is performed
- no existing Google rows are inspected for duplicate exports
- no cleanup or compensating delete is attempted

This limitation must be included in the implementation PR and completion report.

## Logging And Secret Safety

Allowed logging, when logging is added:

- non-secret correlation ID
- operation name
- high-level validation result
- upstream HTTP status
- elapsed duration
- row count
- finalized-match count

Prohibited logging:

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
- scores
- raw request payload
- raw Supabase response
- raw Google response
- environment values

v0.10.6 does not require new logging.

## Shared-Module Policy

Implementation may extend the existing narrow modules under:

`supabase/functions/_shared/`

Expected responsibilities may include:

- typed standings export errors
- standings payload parsing
- standings row serialization
- tournament-level Supabase reads
- official standings reconstruction
- payload-versus-official comparison
- standings worksheet-header verification
- standings append and response validation

The implementation must not:

- introduce a broad framework
- replace the existing match-export modules
- rewrite existing v0.10.5 behavior
- merge unrelated responsibilities
- change Android files
- create database tables
- redesign authentication

## Expected Implementation Boundary

The implementation is expected to modify only a narrow set of Edge Function files.

Likely modified files:

- `supabase/functions/_shared/errors.ts`
- `supabase/functions/_shared/supabaseData.ts`
- `supabase/functions/google-sheets-export/index.ts`

Likely new files:

- a standings payload/parser module
- an official standings reconstruction/comparison module
- a Google standings header/append module
- focused standings parser tests
- focused official standings tests
- focused Google standings tests
- end-to-end `export_standings` operation tests
- additional Supabase data-reader tests where required

The exact implementation file boundary must be declared after the decision PR is merged and after current source files are re-read.

## Testing Policy

All tests must use synthetic data and injected or mocked network behavior.

No test may contact:

- production Supabase
- production Google OAuth
- production Google Sheets
- a real spreadsheet
- a real service account
- Google Drive

Existing tests for `verify_connection` and `export_match` must remain passing.

## Required Test Coverage

Required coverage includes:

1. existing `verify_connection` behavior remains passing
2. existing `export_match` behavior remains passing
3. valid `export_standings` request succeeds
4. exact three-field top-level request contract
5. unsupported top-level field rejection
6. missing rows rejection
7. row count other than 12 rejection
8. unknown row-field rejection
9. exact 20-column order
10. exact `phase_10_v1` constant
11. exact `tournament_standings` constant
12. invalid UUID rejection
13. duplicate standings rank rejection
14. missing standings rank rejection
15. standings row-order mismatch rejection
16. duplicate team-slot rejection
17. invalid team-slot rejection
18. missing team-slot rejection
19. inconsistent tournament ID rejection
20. inconsistent tournament name rejection
21. inconsistent exported-match count rejection
22. exported-match count below 1 rejection
23. exported-match count above 10 rejection
24. matches-played mismatch rejection
25. negative total-position-points rejection
26. negative kills rejection
27. total-kill-points mismatch rejection
28. total-points mismatch rejection
29. invalid best placement rejection
30. invalid first-place count rejection
31. invalid tie-break status rejection
32. duplicate non-empty player name rejection
33. Unicode names are preserved
34. unauthenticated request contacts neither Supabase data nor Google
35. RLS-hidden tournament maps safely
36. no-finalized-match rejection
37. draft matches are excluded
38. official finalized-match count mismatch rejection
39. more than 10 finalized matches rejection
40. duplicate official match ID rejection
41. duplicate official match number rejection
42. finalized match with result count other than 12 rejection
43. unconfirmed official result rejection
44. duplicate official placement rejection
45. missing official placement rejection
46. duplicate official team-slot rejection
47. missing official team-slot rejection
48. negative official kill rejection
49. official tournament-name mismatch rejection
50. official team-name mismatch rejection
51. invalid roster-player membership rejection
52. cross-team player rejection
53. empty player fields accepted
54. exact official position-points totals
55. exact official kill totals
56. exact official kill-point totals
57. exact official cumulative totals
58. exact official best placement
59. exact official first-place count
60. exact latest-match placement tie-break input
61. exact standings ordering
62. unique-order tie status
63. tie-break-applied status
64. unresolved-tie status
65. payload rank mismatch rejection
66. payload total mismatch rejection
67. payload tie-status mismatch rejection
68. Google is not contacted when Supabase validation fails
69. OAuth occurs only after official standings validation
70. missing standings worksheet header rejection
71. reordered standings worksheet header rejection
72. additional standings worksheet header rejection
73. valid exact standings header accepted
74. header request is read-only
75. append uses `RAW`
76. append uses `INSERT_ROWS`
77. append uses `majorDimension=ROWS`
78. append contains exactly 12 rows
79. every append row contains exactly 20 cells
80. one append request only
81. append response with wrong updated-row count rejection
82. malformed successful append response rejection
83. Sheets 403 maps safely
84. Sheets 404 maps safely
85. Sheets 429 maps safely
86. Sheets 5xx maps safely
87. upstream timeout maps safely
88. no Drive API request
89. no formatting request
90. no clear request
91. no automatic retry
92. no secret or raw upstream body in responses
93. exact success response
94. success response contains no names, scores, worksheet metadata, or credentials
95. call order is authentication, Supabase reads, OAuth, header read, append

The implementation may add more focused cases where needed.

## Verification

Required local checks:

- Deno formatting
- Deno formatting check
- Deno lint
- Deno type checking
- focused standings tests
- complete `google-sheets-export` Deno test suite
- static scan for Google Drive scopes and endpoints
- static scan for `USER_ENTERED`
- static scan for `batchUpdate`
- static scan for clear, update, delete, or sheet-creation operations
- static scan confirming one standings append implementation
- static scan for service-role usage
- static scan for embedded private-key material
- static scan for sensitive logging
- static scan for retry or backoff behavior
- `git diff --check`
- final changed-file boundary review
- staged-diff review

Android Gradle tests are not required unless Android files are unexpectedly modified.

No connected-device test is required.

## Deployment Safety

Implementation and verification must not:

- deploy the Edge Function
- set Supabase secrets
- change production secrets
- contact production Supabase
- contact production Google OAuth
- contact production Google Sheets
- modify a real spreadsheet
- access a real service account

Any later live connection or write test requires a separately approved operational step.

## Manual-First Implementation Policy

v0.10.6 is classified as a moderate manual implementation.

The expected implementation method is:

1. manually add standings errors
2. manually add payload parsing and structural tests
3. manually extend tournament-level Supabase reads
4. manually add official standings reconstruction and comparison
5. manually add Google standings header and append helpers
6. manually integrate `export_standings` into the router
7. manually add mocked end-to-end tests
8. run the final audit
9. commit once
10. open one implementation PR

Codex is not required unless an unexpected repository-wide dependency makes the approved manual boundary unsafe.

Codex must not be used to redesign scoring, standings, tie-breaks, RLS, or the request contract.

## Later-Version Handoff

### v0.10.7 — Export Retry and Idempotency

v0.10.7 adds retry-safe operation identity and duplicate prevention for match and standings exports without changing official export columns.

### v0.10.8 — Export Verification

v0.10.8 compares exported rows, order, totals, and Google results against finalized application data.

### v0.11.6 — Export Flow

v0.11.6 connects Android application workflows to the approved CSV and Google Sheets operations.

## Acceptance Criteria

v0.10.6 is complete when:

- `export_standings` exists
- `verify_connection` remains compatible
- `export_match` remains compatible
- authentication occurs before Supabase data or Google access
- caller ownership is enforced through existing RLS
- exactly 12 payload rows are required
- exact Phase 10 standings columns and order are preserved
- only finalized matches contribute
- draft matches are excluded
- no-finalized-match export fails safely
- official finalized-match data is validated
- official cumulative standings are reconstructed server-side
- official scoring totals are verified
- official order and tie status are verified
- official team names are verified
- exported player names are verified against official team rosters
- the `Tournament Standings` header is verified exactly
- one RAW append writes exactly 12 rows
- append response confirms exactly 12 updated rows
- no Drive API or formatting operation is used
- no automatic retry occurs
- no idempotency behavior is added
- success and error responses expose no secrets or row data
- deterministic mocked Deno tests pass
- formatting, lint, type checking, tests, scans, and diff checks pass
- no Android files are changed
- no Gradle files are changed
- no database migration is added
- no RLS policy is changed
- no deployment occurs
- no secret is created or changed
- one implementation commit is created
- one implementation PR is merged
- local and remote `main` are synchronized
- the implementation branch is removed
