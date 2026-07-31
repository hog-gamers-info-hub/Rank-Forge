# v0.10.4 — Google Sheets Edge Function Decisions

## Purpose

v0.10.4 creates the secure server-side foundation for Phase 10 Google Sheets exports.

Google Sheets API calls must run through a Supabase Edge Function. Google credentials, private keys, OAuth access tokens, and service-account configuration must never be stored in the Android application, committed repository files, public responses, or logs.

This version verifies the Google Sheets connection only. Match-row export remains v0.10.5. Tournament-standings export remains v0.10.6.

## Canonical Function

Function name:

`google-sheets-export`

Function path:

`supabase/functions/google-sheets-export/index.ts`

Runtime:

- Supabase Edge Runtime
- Deno 2

## Canonical Operation

v0.10.4 supports only:

`verify_connection`

Request:

```json
{
  "operation": "verify_connection"
}
```

Successful response:

```json
{
  "ok": true,
  "operation": "verify_connection",
  "spreadsheet_access": "verified"
}
```

The response must not expose:

- Google access tokens
- Google private keys
- service-account email
- spreadsheet ID
- spreadsheet title
- worksheet names
- Supabase access tokens
- Google API response bodies
- credential values

## Scope

v0.10.4 includes:

- one authenticated Supabase Edge Function
- explicit JWT verification
- authenticated-user validation
- Google service-account JWT generation
- RS256 signing through Web Crypto
- Google OAuth token exchange
- Google Sheets-only OAuth scope
- configured-spreadsheet access verification
- request validation
- structured success and failure responses
- bounded upstream timeouts
- safe Google API error mapping
- secret-safe logging
- mocked Deno unit tests
- configuration documentation without secret values
- deployment and smoke-test instructions

## Out Of Scope

v0.10.4 does not implement:

- match-row insertion
- tournament-standings insertion
- worksheet creation
- worksheet formatting
- spreadsheet creation
- Google Drive API access
- Android export UI
- Android Google authentication
- Android Google SDK integration
- Android storage of Google credentials
- CSV schema changes
- Room export tables
- database migrations
- export history
- persistent retries
- idempotency
- duplicate-row prevention
- deployment without explicit approval
- production secret configuration through repository files

## Server-Side-Only Rule

All Google operations must remain server-side.

Decisions:

- Android must never call Google OAuth directly.
- Android must never call the Google Sheets API directly.
- Android must never receive a Google access token.
- Android must never contain a Google service-account email or private key.
- Android may call only the authenticated Supabase Edge Function.
- Google credentials must exist only as Supabase project secrets.
- Google access tokens must remain in Edge Function memory.
- Tokens must not be persisted in Room, Supabase tables, logs, or responses.

## Authentication

The function must require a valid Supabase authenticated-user bearer token.

Decisions:

- `verify_jwt` must be enabled.
- Missing authorization must return HTTP 401.
- Malformed authorization must return HTTP 401.
- Invalid or expired Supabase JWTs must return HTTP 401.
- Authentication must complete before contacting Google.
- Anonymous access is prohibited.
- The service-role key must not be used for ordinary user authentication.
- The authenticated user ID must not be returned unnecessarily.

## HTTP Contract

Allowed method:

- `POST`

Unsupported methods:

- HTTP 405
- error code `METHOD_NOT_ALLOWED`

Invalid JSON:

- HTTP 400
- error code `INVALID_JSON`

Missing or unsupported operation:

- HTTP 400
- error code `INVALID_OPERATION`

Successful connection verification:

- HTTP 200
- JSON response
- `spreadsheet_access` equals `verified`

## Required Secrets

Supabase project secrets:

- `GOOGLE_SHEETS_CLIENT_EMAIL`
- `GOOGLE_SHEETS_PRIVATE_KEY`
- `GOOGLE_SHEETS_SPREADSHEET_ID`

Automatically supplied Supabase values may include:

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`

Decisions:

- Secret values must never be committed.
- Service-account JSON must never be committed.
- `.env` files containing secrets must never be committed.
- Private keys must never be placed in Android resources.
- Private keys must never be placed in `local.properties`.
- Private keys must never be included in documentation.
- Private keys must never be logged.
- Missing configuration must fail closed.
- Escaped `\n` sequences in the private key may be converted to real line breaks in memory.
- No other silent private-key rewriting is allowed.

## Google Service-Account JWT

Required JWT header:

- `alg`: `RS256`
- `typ`: `JWT`

Required claims:

- `iss`: configured service-account email
- `scope`: approved Google Sheets scope
- `aud`: Google OAuth token endpoint
- `iat`: current Unix timestamp
- `exp`: no more than 3600 seconds after `iat`

Approved OAuth scope:

`https://www.googleapis.com/auth/spreadsheets`

Approved audience:

`https://oauth2.googleapis.com/token`

Decisions:

- Google Drive scope must not be requested.
- Broad Google Cloud scopes must not be requested.
- Signing must use Web Crypto.
- Private-key import failures must be handled safely.
- JWT signing failures must return typed failures.
- JWT assertions must never be logged or returned.

## Google OAuth Token Exchange

Token endpoint:

`https://oauth2.googleapis.com/token`

Request requirements:

- method `POST`
- content type `application/x-www-form-urlencoded`
- grant type `urn:ietf:params:oauth:grant-type:jwt-bearer`
- signed JWT assertion

Successful token responses must contain:

- access token
- compatible bearer token type

Malformed OAuth responses must fail closed.

Raw Google OAuth responses must not be returned to Android.

## Spreadsheet Access Verification

After obtaining a Google access token, the function must verify access to the configured spreadsheet using minimal Google Sheets metadata retrieval.

Decisions:

- The spreadsheet must already exist.
- The spreadsheet must be shared with the service-account email.
- v0.10.4 must not create a spreadsheet.
- v0.10.4 must not create worksheets.
- v0.10.4 must not write cells.
- v0.10.4 must not clear cells.
- v0.10.4 must not alter formatting.
- Spreadsheet metadata must not be included in the client response.
- Successful metadata retrieval is sufficient to report verified access.

## Timeouts

Every external request must have a bounded timeout.

Required timeout boundaries:

- Supabase authenticated-user validation
- Google OAuth token exchange
- Google Sheets metadata request

Decisions:

- Requests must not wait indefinitely.
- Timeout values must be constants.
- Timeouts must return a typed failure.
- Tests must verify timeout mapping without real network requests.

## Error Contract

Error response:

```json
{
  "ok": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Safe client-facing message"
  }
}
```

Approved error codes:

- `METHOD_NOT_ALLOWED`
- `INVALID_JSON`
- `INVALID_OPERATION`
- `UNAUTHORIZED`
- `SUPABASE_AUTH_FAILURE`
- `GOOGLE_CONFIG_MISSING`
- `GOOGLE_CREDENTIAL_INVALID`
- `GOOGLE_JWT_SIGNING_FAILURE`
- `GOOGLE_TOKEN_FAILURE`
- `GOOGLE_TOKEN_RESPONSE_INVALID`
- `GOOGLE_SHEETS_ACCESS_DENIED`
- `GOOGLE_SHEETS_NOT_FOUND`
- `GOOGLE_API_RATE_LIMITED`
- `UPSTREAM_TIMEOUT`
- `GOOGLE_API_FAILURE`
- `INTERNAL_ERROR`

Decisions:

- Raw exception messages must not be returned.
- Raw Google response bodies must not be returned.
- Stack traces must not be returned.
- Secret values must not be returned.
- Client-facing messages must remain generic and actionable.

## HTTP Status Mapping

- 400: invalid request
- 401: authentication failure
- 403: spreadsheet access denied
- 404: spreadsheet not found
- 405: unsupported method
- 429: upstream rate limit
- 500: configuration or internal failure
- 502: Google OAuth or Sheets failure
- 504: upstream timeout

## Logging

Allowed log data:

- operation name
- high-level success or failure category
- upstream HTTP status
- non-secret correlation identifier
- elapsed duration

Prohibited log data:

- authorization header
- Supabase JWT
- Google access token
- Google JWT assertion
- private key
- service-account email
- spreadsheet ID
- Google raw response body
- environment-variable values

## Function Configuration

`supabase/config.toml` must explicitly include:

```toml
[functions.google-sheets-export]
verify_jwt = true
```

Existing unrelated configuration must remain unchanged.

## Shared Modules

Implementation may add narrowly scoped modules under:

`supabase/functions/_shared/`

Possible responsibilities:

- JSON response helpers
- request validation
- authenticated-user validation
- PEM private-key parsing
- base64url encoding
- Google JWT creation
- OAuth token exchange
- Sheets access verification
- typed error mapping

Unrelated frameworks or broad refactors are prohibited.

## Testing

Tests must not contact:

- production Supabase
- production Google OAuth
- production Google Sheets
- a real spreadsheet
- a real service account

Tests must use mocked dependencies or mocked `fetch`.

Required coverage:

- POST accepted
- unsupported method rejected
- invalid JSON rejected
- missing operation rejected
- unsupported operation rejected
- missing authorization rejected
- invalid authenticated-user response rejected
- missing Google configuration rejected
- escaped private-key newline normalization
- malformed private key rejected
- JWT header and claims
- Sheets-only OAuth scope
- token request form body
- malformed OAuth response rejected
- OAuth denial mapped safely
- spreadsheet access success
- Sheets 403 mapped to access denied
- Sheets 404 mapped to not found
- Sheets 429 mapped to rate limited
- Sheets 5xx mapped to upstream failure
- upstream timeout mapped safely
- successful response exposes no metadata or secrets
- errors expose no raw upstream body
- no Google write request is made
- no Drive API request is made
- no secret appears in responses or captured logs

## Verification

Implementation verification should include:

- focused Deno unit tests
- Deno type checking
- Supabase configuration validation
- secret-pattern scan
- `git diff --check`

Android Gradle tests are not required unless Android files are modified unexpectedly.

A real Google connection test requires separate operational setup. Secrets must not be pasted into chat.

## Deployment Safety

Decisions:

- Do not deploy during code generation.
- Do not set production secrets during code generation.
- Do not modify production Supabase configuration without approval.
- Review and merge the implementation PR before deployment.
- Deploy only the approved function.
- Use only the Rank-Forge Supabase project.
- Record deployment and smoke-test results.
- Do not access unrelated projects.

## Later Versions

### v0.10.5 — Google Sheets Match Export

Will write finalized match rows using the approved `match_result` schema.

### v0.10.6 — Google Sheets Standings Export

Will write cumulative standings using the approved `tournament_standings` schema.

### v0.10.7 — Export Retry and Idempotency

Will add retry-safe operation identity and duplicate prevention.

## Acceptance Criteria

v0.10.4 is complete when:

- the authenticated `google-sheets-export` function exists
- JWT verification is explicitly enabled
- only POST is accepted
- only `verify_connection` is accepted
- unauthenticated requests fail before contacting Google
- credentials are loaded only from Supabase secrets
- a Google service-account JWT can be generated securely
- only the approved Sheets OAuth scope is used
- Google OAuth token exchange is implemented
- configured-spreadsheet access can be verified
- the function performs no spreadsheet writes
- the function performs no Drive API requests
- responses and logs expose no secrets
- typed errors and HTTP mappings exist
- upstream requests have bounded timeouts
- mocked tests pass
- type checking passes
- secret scanning passes
- `git diff --check` passes
- no Android files are changed
- no database migrations are added
- no CSV schema is changed
- no production deployment occurs without approval
