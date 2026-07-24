# Rank-Forge - Google Sheets and CSV Export

## 1. Document Purpose

This document defines the authoritative MVP requirements for exporting finalized Rank-Forge results to CSV and Google Sheets. It establishes export eligibility, output boundaries, data-source rules, idempotency expectations, validation requirements, backend security boundaries, privacy limits, and verification expectations without introducing implementation-specific details that have not yet been approved.

## 2. Scope and Boundaries

This file covers finalized-result export to CSV and Google Sheets.

CSV export is an approved MVP output.

Google Sheets export is an approved MVP output through a secure backend flow.

Exports do not define official scoring. Scoring is governed by [docs/05_SCORING_AND_PROCESSING_RULES.md](docs/05_SCORING_AND_PROCESSING_RULES.md).

Exports do not define database truth. Supabase is the permanent backend authority.

Exports do not define local state. Room supports local and offline responsibilities only.

Draft, invalid, incomplete, unresolved, or unconfirmed data must not be exported.

Export implementation occurs according to the roadmap, primarily in Phase 10 and later integration phases.

This document explicitly excludes Google Sheets as a database of record.

This document explicitly excludes direct Android storage of Google credentials.

This document explicitly excludes export of raw OCR evidence as official results.

This document explicitly excludes export of draft or unresolved results.

This document explicitly excludes payment, subscription, spectator, or player-account export workflows.

## 3. Export Principles

Export only finalized data.

Export only current confirmed result values.

Exported totals must match canonical scoring.

Exported standings must match canonical tie-break ordering.

Export output must be reproducible from finalized application data.

Export retries must be idempotent.

Failed exports must not appear successful.

CSV output must be UTF-8.

Google Sheets export must use backend-only privileged credentials.

Exports are user-facing outputs, not alternate canonical data stores.

Export validation must happen before output generation.

## 4. Export Eligibility

Export is allowed only when the match or tournament data is finalized.

Every exported match must have exactly 12 valid unique team-result rows.

Every finalized result must have a valid team, placement, kill value, position points, kill points, and match total.

Required corrections must be persisted before export.

Required OCR-assisted values must be confirmed before export.

No unresolved duplicate screenshot, duplicate team, duplicate placement, unmatched team, or validation conflict may remain.

Standings must be calculated from finalized matches only.

Current export data must match the latest authorized finalized data.

Export must be blocked when data is draft.

Export must be blocked when data is incomplete.

Export must be blocked when data is invalid.

Export must be blocked when data is unresolved.

Export must be blocked when finalization failed.

Export must be blocked when standings recalculation failed.

Export must be blocked when required current finalized data cannot be loaded.

Export must be blocked when export idempotency cannot be established where required.

## 5. Export Data Sources

Export data comes from confirmed finalized match results and reproducible standings.

Position points, kill points, match totals, cumulative totals, and tie-break fields are derived from canonical scoring inputs.

Raw OCR output is not an official export source.

Corrected confirmed values are exportable only after validation and finalization.

Cached or materialized standings may be exported only if they match reproducible finalized standings.

Supabase is the backend authority for synchronized finalized data.

Local data may support offline export only if it is confirmed, finalized, current, and safe under the approved offline model.

## 6. Match CSV Export

A match CSV export must represent one finalized match and include all 12 team results.

Approved logical field groups are:

* Tournament identity or display context
* Match identity or display context
* Team slot or stable team reference
* Team name
* Confirmed placement
* Position points
* Confirmed kill value
* Kill points
* Match total
* Finalization or export context where approved

All 12 teams must be present.

Each team must appear once.

Each placement from 1 through 12 must appear once.

Rows must follow a stable approved order.

Match totals must equal position points plus kill points.

Exported values must match finalized application values.

Draft or unresolved rows must not be included.

UTF-8 must preserve special characters in team and player names where exported.

Exact column names and final column order remain deferred to Phase 10 export data model approval.

## 7. Tournament CSV Export

A tournament CSV export must represent cumulative standings across finalized matches.

Approved logical field groups are:

* Tournament identity or display context
* Team slot or stable team reference
* Team name
* Final rank
* Total position points
* Total kill points
* Total points
* Number of first-place finishes
* Latest-match placement where needed for tie-breaking
* Matches included in standings
* Export context where approved

Standings must include finalized matches only.

Draft matches must be excluded.

Every finalized match must be counted once.

Totals must match canonical scoring.

Ranking must follow the approved tie-break order:

1. Total points
2. Number of first-place finishes
3. Total kills
4. Placement in the latest match

A complete unresolved tie after all approved tie-break fields must not be resolved using an invented export-only rule.

Exported standings must be reproducible from finalized match data.

This document does not add averages, win rates, survival points, penalty points, bonus points, or analytics fields.

## 8. CSV Encoding and File Integrity

CSV output uses UTF-8.

CSV must preserve supported special characters.

Generated files must be readable by common spreadsheet tools.

Rows and totals must be validated before export completion is reported.

Empty, partial, corrupted, or failed files must not be reported as successful.

Exported file integrity must be verifiable.

File naming, save location, delimiter choice beyond standard CSV, and user destination behavior remain deferred.

## 9. Google Sheets Export

Google Sheets export sends finalized match results or cumulative standings to an approved spreadsheet destination.

Android may initiate the export request.

The secure Supabase backend flow performs privileged Google Sheets work.

Google credentials are never stored in Android.

Exported Sheets values must match finalized application values.

Exported ordering must match approved scoring and tie-break rules.

Export status must be visible to the operator.

Failed export attempts must not appear successful.

Repeated export attempts must not duplicate rows.

## 10. Google Sheets Backend Boundary

Google Sheets integration belongs behind Supabase Edge Functions or another approved secure backend boundary.

The backend validates authentication and authorization.

The backend confirms tournament ownership or approved access.

The backend validates that data is finalized and export-eligible.

The backend uses backend-only Google credentials.

The backend enforces idempotency.

The backend records export status where approved.

The backend returns explicit success or failure states.

The backend logs enough for troubleshooting without exposing secrets or private screenshot data.

## 11. Export Idempotency and Retry

Every export operation requiring retry must have a stable operation identity or approved idempotency boundary.

Retrying CSV generation must not create misleading duplicate official outputs.

Retrying Google Sheets export must not duplicate destination rows.

Repeated synchronization must not duplicate export records.

Failed exports must remain retryable where practical.

Ignored duplicate export attempts must be distinguishable from failed attempts.

The current finalized data version used for export must be clear enough to prevent stale exports where required.

Export retries must not overwrite newer finalized data with stale data.

The following decisions remain deferred:

* Exact idempotency key format
* Exact export operation schema
* Exact Google Sheets row matching strategy
* Exact retry intervals
* Exact conflict-resolution behavior for changed finalized data after export

## 12. Export Status and Audit Requirements

Export states are conceptually:

* Not exported
* Export pending
* Export in progress
* Export succeeded
* Export failed
* Export retry pending
* Export conflict or stale data detected where required

Audit or traceability may include:

* Export target type
* Tournament or match reference
* Export operation identity
* Actor where required
* Timestamp
* Status
* Failure reason
* Retry count where approved
* Current finalized data reference where required

Exact database columns, table names, log schemas, and UI labels are not defined here.

## 13. Validation Before Export

Export validation must confirm that the match is finalized.

Export validation must confirm that tournament standings are based only on finalized matches.

Export validation must confirm that every exported match contains exactly 12 valid result rows.

Export validation must confirm team uniqueness.

Export validation must confirm placement uniqueness.

Export validation must confirm valid kill values.

Export validation must confirm correct match totals.

Export validation must confirm correct tournament totals.

Export validation must confirm correct tie-break ordering.

Export validation must confirm OCR-assisted values are confirmed.

Export validation must confirm correction history requirements are satisfied.

Export validation must confirm the export destination is authorized where required.

Export validation must confirm an idempotency boundary is available where required.

Export must stop if validation fails.

## 14. Export Error Handling

Export error handling must explicitly cover draft data selected for export.

Export error handling must explicitly cover invalid or incomplete finalized data.

Export error handling must explicitly cover standings recalculation failure.

Export error handling must explicitly cover missing tournament or match.

Export error handling must explicitly cover missing team or result row.

Export error handling must explicitly cover duplicate team or placement.

Export error handling must explicitly cover invalid score total.

Export error handling must explicitly cover CSV generation failure.

Export error handling must explicitly cover file write failure.

Export error handling must explicitly cover Google Sheets backend request failure.

Export error handling must explicitly cover Google authentication failure.

Export error handling must explicitly cover Google authorization failure.

Export error handling must explicitly cover Google Sheets API failure.

Export error handling must explicitly cover network failure.

Export error handling must explicitly cover backend authorization failure.

Export error handling must explicitly cover duplicate export attempt.

Export error handling must explicitly cover stale finalized data.

Export error handling must explicitly cover partial Google Sheets write.

Errors must remain explicit.

Existing finalized data must not be corrupted.

Failed exports must not appear successful.

Retry must be available where practical.

Partial Google Sheets writes must not silently produce accepted duplicate or inconsistent results.

Exact user-facing messages and retry timing are not defined here.

## 15. Security and Credential Boundaries

Android must never store Google privileged credentials.

Android must never store Supabase service-role credentials.

Google Sheets credentials remain backend-only.

Service-role credentials remain backend-only.

Export requests require authenticated and authorized access where applicable.

The backend must verify tournament ownership before privileged export.

Secrets must not be committed.

Logs must not expose credentials, tokens, private screenshot data, or sensitive OCR content.

Exported files may contain tournament data and should be handled carefully by the operator.

Private screenshot files are not exported as official scoring output unless separately approved.

## 16. Privacy and Data-Minimization Requirements

Export only data required for approved match or standings output.

Do not export raw OCR blocks as official result columns.

Do not export private screenshot binaries.

Do not export internal synchronization metadata unless explicitly approved.

Do not export secrets, tokens, storage paths, or backend-only identifiers intended for internal use.

Operator-visible exported data should use stable display values and approved scoring outputs.

Sensitive operational logs must remain separate from user-facing exports.

## 17. Testing and Verification

Required CSV verification includes:

* Match CSV contains exactly 12 teams.
* Tournament CSV contains approved cumulative standings.
* UTF-8 preserves special characters.
* Position points, kill points, match totals, and tournament totals match finalized data.
* Draft and unresolved results cannot export.
* Duplicate teams and duplicate placements block export.
* Generated file integrity is verified.
* Empty or partial CSV is not reported as success.

Required Google Sheets verification includes:

* Export requires authorization.
* Backend credentials are not exposed to Android.
* Match export writes finalized match data only.
* Tournament export writes finalized standings only.
* Retry does not duplicate rows.
* Invalid payload fails safely.
* Backend failure is reported clearly.
* Partial write handling is verified.
* Exported totals match finalized data.

Required idempotency and recovery verification includes:

* Repeated export attempts do not duplicate rows.
* Failed exports can be retried.
* App restart during pending export does not falsely report success.
* Connectivity loss during export is recoverable.
* Stale finalized data is detected where required.

Required security verification includes:

* No Google credentials in Android.
* No service-role credentials in Android.
* No secrets committed.
* Export authorization respects tournament ownership.
* Logs avoid sensitive data.

This document does not claim that these tests currently pass.

## 18. Deferred Export Decisions

The following export decisions remain deferred:

* Exact CSV column names
* Exact CSV column order
* Exact file naming convention
* Exact Android save destination behavior
* Exact Android sharing behavior
* Exact spreadsheet destination model
* Exact sheet names or tab names
* Exact Google Sheets formatting
* Exact Edge Function names
* Exact request and response payloads
* Exact Google credential format
* Exact export operation table or schema
* Exact idempotency key format
* Exact duplicate-row detection strategy
* Exact retry schedule
* Exact stale-data detection mechanism
* Exact handling of complete unresolved ties in exported display
* Exact export audit-log schema
* Exact retention or deletion behavior for generated files

These decisions must not be resolved in this document.

## 19. Roadmap Alignment

Phase 4 defines deterministic scoring and standings that exports must match.

Phase 6 provides backend data authority, synchronization, idempotency, and finalized-data protection.

Phase 10 defines export data model, match CSV export, tournament CSV export, UTF-8 validation, Google Sheets Edge Function, match Sheets export, standings Sheets export, export retry, idempotency, and export verification.

Phase 11 integrates export flow with finalized results.

Phase 12 validates export, backend authorization, recovery, and regression behavior.

Phase 13 verifies export workflows with controlled real-tournament data.

Phase 14 includes approved production deployment and release evidence.
