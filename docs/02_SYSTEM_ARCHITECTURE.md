# Rank-Forge — System Architecture

## 1. Document Purpose

This document defines the approved MVP system architecture for Rank-Forge at a repository-canonical level.

It describes the architectural responsibilities, boundaries, and dependency direction required to support the approved product requirements without introducing implementation code, database schemas, API payloads, class designs, screen layouts, or unresolved technical details.

This document follows the approved authority hierarchy. Product requirements define what the architecture must support, the roadmap defines when architectural areas are implemented, and verified repository state defines what is and is not currently implemented.

## 2. Architectural Goals

The approved architectural goals are:

* Correct deterministic result processing
* Clear separation of UI, domain, data, OCR, matching, scoring, synchronization, and export responsibilities
* Offline-capable tournament workflows
* Protection of finalized results
* Recoverability after app closure or connectivity loss
* Secure backend authority
* Testability and maintainability
* Minimal exposure of privileged credentials
* Idempotent synchronization and exports

This architecture does not add unrelated monetization, spectator, payment, subscription, web-platform, or unapproved scalability requirements.

## 3. System Context

Rank-Forge is architected as:

* A native Android client used by tournament organizers and authorized operators
* A Supabase backend providing authentication, permanent backend data, storage, Row Level Security, restricted backend functions, and synchronization authority
* An on-device Google ML Kit OCR capability for supported genuine Free Fire MAX scoreboard screenshots
* Room-based local persistence for approved drafts, cache, recovery, and pending synchronization
* CSV export and secure Google Sheets export

Architectural source-of-truth boundaries:

* GitHub is the source of truth for code and repository documentation.
* Supabase is the permanent backend data authority.
* Room is not the permanent source of truth.
* Google Sheets is an output destination, not the primary database.
* Android implementation has not started unless verified in tracked repository files.

## 4. High-Level Component Architecture

The approved logical architecture contains the following component boundaries:

* Presentation layer
* Domain layer
* Data layer
* Local persistence boundary
* Remote backend boundary
* Screenshot intake and storage boundary
* OCR extraction boundary
* Parsing and normalization boundary
* Team-matching boundary
* Scoring and standings boundary
* Review, correction, and finalization boundary
* Synchronization boundary
* Export boundary

Approved dependency direction:

* Presentation depends on domain abstractions.
* Domain logic must not depend on Android UI components.
* Data implementations satisfy domain-facing repository contracts.
* OCR, matching, scoring, synchronization, and export logic must not be placed inside composables.
* Infrastructure details must not leak into deterministic domain calculations.

This document does not define exact package names, class names, interfaces, file paths, or module names.

## 5. Android Application Architecture

The approved Android architectural direction is:

* Kotlin
* Jetpack Compose and Material 3
* MVVM with layered architecture
* ViewModels for screen state and actions
* Kotlin Coroutines and `StateFlow`
* Immutable UI state where practical
* Explicit loading, empty, success, and error states
* Navigation Compose according to roadmap sequencing
* Dependency injection according to the approved roadmap
* No blocking work on the main thread
* No business logic in composables

Conceptual Android responsibilities:

* Presentation renders state and sends user actions.
* ViewModels coordinate approved use cases.
* Domain use cases apply validation and workflow rules.
* Repository abstractions coordinate local and remote data sources.
* Background synchronization and OCR work must expose observable states and recoverable failures.

This document does not define screen designs, navigation routes, package structures, Gradle configuration, or implementation code.

## 6. Domain and Business-Logic Boundaries

Deterministic domain logic includes:

* Tournament and roster validation
* Match validation
* Placement and kill scoring
* Standings aggregation
* Tie-break ordering
* Finalization eligibility
* Duplicate prevention rules
* Team-assignment safety rules

Approved domain boundaries:

* Scoring is independent from UI, database, OCR, and networking.
* OCR output cannot directly finalize a match.
* Only reviewed and valid results may enter finalized scoring and export workflows.
* Detailed values remain governed by the scoring and OCR canonical documents when populated.

This document does not redefine detailed scoring values or matching algorithms beyond the approved architectural boundaries they must respect.

## 7. Local Persistence Architecture

Room's approved architectural role is to:

* Store approved offline drafts
* Cache tournament and roster data needed for offline operation
* Store pending synchronization operations
* Store temporary OCR and correction state where required
* Support recovery after app closure or connectivity loss

Local persistence boundaries:

* Room is not the permanent source of truth.
* Confirmed server data must not be silently overwritten by stale local data.
* Local schema changes require Room migrations.
* Sensitive data must be stored locally only when required.
* Finalized data requires stronger overwrite protection than editable drafts.
* Local operations requiring synchronization must carry sufficient identity and status for idempotent processing.

This document does not define entities, columns, DAOs, table names, queue schemas, encryption choices, or conflict-resolution algorithms.

## 8. Supabase Backend Architecture

Supabase is responsible for:

* Authentication
* PostgreSQL permanent backend storage
* Row Level Security
* Ownership-based authorization
* Screenshot and approved export-related storage
* Versioned database migrations
* Restricted database functions
* Edge Functions for privileged integrations
* Synchronization authority
* Operational logging where approved

Backend boundaries:

* Service-role and privileged credentials remain backend-only.
* Every exposed table requires RLS.
* Authentication alone is insufficient authorization.
* Database changes require versioned migrations.
* Existing production migrations must not be rewritten.
* Finalized results require protection from unauthorized or silent overwrite.

This document does not define tables, fields, SQL policies, RPC names, Edge Function names, storage buckets, or API payloads.

## 9. Screenshot and OCR Architecture

The approved scoreboard-only pipeline is:

1. Select a genuine Free Fire MAX scoreboard screenshot.
2. Validate basic image usability.
3. Associate the screenshot with a tournament and match.
4. Preserve the original image separately from processed variants where permitted.
5. Run approved image preparation when implemented.
6. Perform on-device ML Kit text recognition.
7. Preserve raw OCR blocks, lines, and related metadata.
8. Parse placements, player names, and kill values from the supported scoreboard layout.
9. Flag missing, malformed, unsupported, or uncertain values.
10. Pass reviewable values to team matching and manual correction.
11. Prevent finalization until required conflicts are resolved.

Required OCR boundaries:

* OCR applies only to supported genuine scoreboard screenshots.
* Tournament rosters remain manually entered structured data.
* Roster-screenshot OCR and roster-image import are outside MVP scope.
* OCR extraction, parsing, matching, scoring, correction, and finalization remain separate responsibilities.
* Raw OCR values and corrected values remain distinguishable.
* OCR acceptance remains deferred until approved genuine screenshot data exists.

This document does not define crop coordinates, image dimensions, preprocessing parameters, parser expressions, confidence thresholds, or supported layouts.

## 10. Team Matching and Result Processing

The conceptual result-processing flow is:

* Normalize detected player names.
* Compare detected players against the manually maintained roster.
* Produce team candidates and confidence information.
* Enforce one detected player to one roster-player assignment.
* Enforce unique team assignment within a match.
* Present uncertain and unmatched results for operator review.
* Preserve raw and corrected values separately.
* Validate all 12 result rows.
* Finalize only after conflicts and uncertainty are resolved.
* Send finalized results to scoring, persistence, synchronization, and export boundaries.

This architecture does not define exact fuzzy-matching algorithms, weights, thresholds, candidate data models, or UI layouts.

## 11. Synchronization Architecture

The approved synchronization model is offline-capable with Supabase authority:

* Local changes may be queued while offline.
* Pending operations retry after connectivity returns.
* Every synchronization operation must be idempotent.
* Repeated retries must not create duplicate records.
* Local and remote changes require explicit conflict detection.
* Stale local data must not overwrite newer or finalized backend data silently.
* Failed operations remain recoverable and observable.
* Finalized-state protection must be enforced at both client and backend boundaries.

This document does not define queue fields, timestamps, version columns, merge algorithms, background-work libraries, or retry schedules.

## 12. Export Architecture

The approved export architecture is:

* CSV export may be generated from finalized application data.
* CSV uses UTF-8 and stable approved ordering.
* Google Sheets integration runs through a secure Supabase Edge Function.
* Google credentials are never embedded in the Android application.
* Only finalized results may be exported.
* Repeated export attempts must not duplicate rows.
* Export output must match finalized scoring and standings.
* Google Sheets remains an output destination, not a source of tournament truth.

This document does not define unapproved export columns, spreadsheet templates, sheet names, credential formats, or API request structures.

## 13. Security Boundaries

Approved security boundaries are:

* The Android client uses only approved public or client credentials.
* Service-role and Google privileged credentials remain backend-only.
* Supabase authorization uses ownership-based RLS.
* Private screenshots require controlled access.
* Sensitive information must not be logged unnecessarily.
* Destructive operations require approval, backup, and rollback planning.
* Repository secrets and local environment files must not be committed.

This document does not make compliance claims or define retention periods.

## 14. Error Handling and Recovery

The architecture must provide explicit and recoverable handling for:

* Invalid rosters
* Invalid match data
* OCR failures
* Unsupported screenshot layouts
* Low-confidence or unmatched teams
* Local-save failure
* App restart
* Connectivity loss
* Synchronization failure
* Export failure
* Duplicate operations
* Finalization conflicts

Errors must be explicit, recoverable where practical, and must not silently corrupt or confirm data.

This document does not define user-interface wording or retry timing.

## 15. Testing and Verification Boundaries

The architecture must provide seams for:

* Pure deterministic unit testing for scoring, validation, normalization, matching, and duplicate rules
* Room persistence and migration tests
* Supabase schema, function, RLS, and authorization tests
* Synchronization and idempotency tests
* OCR fixture and genuine screenshot evaluation
* Export validation
* Compose UI and end-to-end workflow tests
* Emulator and physical-device verification

Genuine screenshot OCR acceptance remains deferred until approved real screenshots exist.

## 16. Architectural Constraints

The approved architectural constraints are:

* Native Android only for the MVP client
* Exactly 12 team slots
* Four to six players per team
* Maximum 10 matches per tournament
* Manual roster entry
* Scoreboard-only OCR
* Supabase permanent backend authority
* Room limited to approved local and offline responsibilities
* Finalized-only export
* Roadmap-controlled implementation order
* No current implementation claim without repository evidence

## 17. Deferred Technical Decisions

The following matters remain unresolved and require approval or later canonical documentation:

* Exact Android package and module structure
* Detailed dependency-injection modules
* Room entities and DAOs
* Supabase schema and RLS definitions
* Synchronization conflict-resolution mechanism
* Supported scoreboard layout and crop coordinates
* OCR preprocessing details
* Matching algorithm implementation details
* Export column definitions
* Storage-retention and deletion details
* Background-work scheduling details

## 18. Roadmap Alignment

The approved high-level roadmap alignment is:

* Phase 1: Android foundation
* Phases 2–4: tournament, match, scoring
* Phase 5: Room and offline recovery
* Phase 6: authentication, backend, and synchronization
* Phases 7–9: screenshot intake, OCR, matching, and correction
* Phase 10: exports
* Phases 11–14: integration, validation, beta, and release
