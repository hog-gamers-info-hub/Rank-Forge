# Rank-Forge — Android Application

## 1. Document Purpose

This document defines the authoritative Rank-Forge MVP Android client requirements, architecture boundaries, navigation responsibilities, screen workflows, UI states, offline behavior, security controls, accessibility requirements, and Android verification expectations.

It defines the approved Android direction and boundaries; the v0.1.0 Android foundation is now implemented in the repository.

This document follows the approved authority hierarchy. Product documents define what the Android application must support, architecture documents define boundaries, and the roadmap governs when Android capabilities are implemented.

## 2. Scope and Current Status

Rank-Forge is planned as a native Android application for tournament organizers and authorized operators.

Approved scope and status boundaries:

* Android implementation began in Phase 1 with v0.1.0.
* The v0.1.0 Android foundation is verified in tracked Android files.
* This document defines approved Android behavior and implementation boundaries beyond the verified v0.1.0 foundation.
* Public spectator, player-login, payment, subscription, advertising, and unrelated user experiences are outside approved MVP scope.

Current verified repository status:

* The v0.1.0 Android Gradle project, Kotlin Android source, minimal Compose screen, manifest, and Android resources have been verified.

## 3. Approved Android Technology Stack

The approved high-level Android stack is:

* Kotlin
* Native Android
* Jetpack Compose
* Material 3
* MVVM with layered architecture
* Navigation Compose
* Hilt dependency injection according to roadmap sequencing
* Kotlin Coroutines
* `StateFlow`
* Room for approved local persistence
* Supabase client integration for authentication and backend synchronization
* Google ML Kit bundled Latin Text Recognition v2 for scoreboard OCR
* Android Photo Picker for approved scoreboard screenshot selection
* CSV export
* Secure Google Sheets export through the approved Supabase backend flow

Approved platform constraints:

* Minimum supported Android API is API 26.
* Testing must also cover the current configured target API when implementation exists.
* Exact SDK, library, Kotlin, Compose, Gradle, and plugin versions remain deferred until Phase 1 implementation.

This document does not define dependency versions or additional libraries.

## 4. Android Architectural Boundaries

The approved conceptual Android layers are:

* Presentation
* Domain
* Data
* Local persistence
* Remote backend
* OCR and image-processing boundary
* Synchronization boundary
* Export boundary

Required dependency direction:

* Composables render state and emit user actions.
* ViewModels coordinate approved use cases.
* Domain logic contains validation, scoring, matching, and workflow rules.
* Repository abstractions coordinate local and remote data sources.
* Data implementations must not leak infrastructure details into the domain layer.
* Composables must not contain database, networking, OCR, matching, scoring, synchronization, or export business logic.
* Deterministic scoring must remain independent from Android framework classes.
* OCR extraction must remain separate from matching, correction, scoring, and finalization.
* Room must not be treated as the permanent source of truth.
* Supabase remains the permanent backend data authority.

This document does not define exact packages, modules, classes, interfaces, or filenames.

## 5. Application State and ViewModel Rules

Approved Android state and ViewModel rules:

* Every screen must expose explicit loading, empty, success, and error states where relevant.
* Screen state should be immutable where practical.
* ViewModels own screen state and process user actions.
* Long-running operations must expose progress and completion or failure states.
* UI state must survive configuration changes through approved ViewModel and persistence mechanisms.
* Draft workflows must recover after expected app restarts.
* One-time events must not be repeatedly triggered after recomposition.
* Blocking database, file, OCR, image-processing, synchronization, or networking work must not run on the main thread.
* Validation results and unresolved warnings must remain visible.
* Finalization state must be explicit and protected.

This document does not prescribe a specific one-time-event library or state-container implementation.

## 6. Navigation and Workflow Structure

The approved conceptual navigation destinations are:

* Authentication
* Tournament list
* Tournament creation
* Tournament details
* Team and roster entry
* Roster review
* Match list or tournament-match management
* Match creation
* Manual result entry
* Scoreboard screenshot selection
* OCR processing state
* OCR and team-matching review
* Match review
* Finalized match details
* Tournament standings
* Export actions and status

Navigation requirements:

* Users must not access tournament workflows without the required authenticated session once authentication is implemented.
* Navigation must preserve the active tournament and match context.
* Back navigation must not silently discard unsaved drafts.
* Finalized results must not reopen as freely editable drafts.
* Invalid or incomplete workflows must not navigate directly into finalization.
* Navigation implementation follows roadmap sequencing.
* Placeholder screens may exist during Phase 1, but later functionality must not be implemented ahead of its roadmap phase.

This document does not define route strings, deep links, tab layouts, bottom navigation, drawer structure, or exact screen hierarchy.

## 7. Authentication Workflow

The planned Phase 6 Android authentication responsibilities are:

* Sign-up
* Login
* Logout
* Session restoration
* Authentication error handling
* Protected access to authorized tournament data

Requirements:

* Authentication state must be observable.
* Expired or invalid sessions require explicit handling.
* Logout must clear or protect local authenticated state appropriately.
* Offline access to previously approved local tournament data must not imply authenticated backend access.
* Authentication alone is insufficient authorization; tournament ownership remains enforced by Supabase RLS.
* Service-role credentials must never be stored in the Android application.

This document does not define social login, phone authentication, password-recovery flows, account deletion, administrator accounts, or collaboration behavior.

## 8. Tournament Management Workflow

The approved Android tournament workflow covers:

* Viewing tournaments
* Creating a tournament
* Opening tournament details
* Viewing tournament status
* Continuing unfinished tournament work

Approved tournament creation data includes:

* Tournament name
* Tournament date
* Organizer details
* Tournament status

Requirements:

* Required values must be validated.
* Tournament identity must remain stable.
* Draft or unsynchronized tournaments must remain visibly distinguishable where required.
* Creating the same tournament repeatedly through retry must not create duplicate backend records.
* Tournament details provide access to approved roster, match, standings, and export workflows according to roadmap progress.

This document does not define tournament formats, public visibility, collaboration, brackets, prizes, registration, scheduling, or map lists.

## 9. Team and Roster Workflow

Approved team and roster workflow rules:

* Every tournament contains exactly 12 fixed team slots.
* Team and player rosters are entered manually through structured Android input.
* Each team contains four to six players.
* Team names must be unique within the tournament.
* Duplicate players and invalid player counts must be detected according to approved rules.
* Slot numbers remain fixed from 1 through 12.
* A complete roster-review workflow is required before tournament processing continues.
* Missing teams, incomplete teams, invalid player counts, duplicate team names, and approved duplicate-player conditions must remain visible.
* Roster drafts must survive expected app restarts.
* Manual input must remain usable without OCR.

Explicit MVP exclusions:

* Roster-screenshot OCR
* Roster-image import
* Automatic roster extraction
* Unapproved CSV roster import

This document does not define exact form controls or screen layouts.

## 10. Manual Match Processing Workflow

Approved manual match-processing rules:

* A tournament supports a maximum of 10 matches.
* Match creation includes match number, date, map, and draft status.
* Match numbers must be unique within the tournament.
* Manual result entry supports all 12 teams.
* Operators enter or assign placements 1 through 12.
* Operators enter non-negative whole-number kill values.
* Duplicate teams, duplicate placements, missing rows, invalid placements, and invalid kill values must be detected.
* A complete 12-row review is required before finalization.
* Draft and finalized states remain distinct.
* Controlled correction must preserve required previous information.
* Manual and OCR-assisted workflows must converge on the same confirmed result structure.

This document does not define an unapproved maximum kill value, map list, game mode, or match format.

## 11. Scoring and Standings Workflow

Approved Android scoring and standings behavior:

* Provisional scoring may be displayed during review.
* Position points, kill points, and match totals are derived values.
* Derived scoring values are not independently editable.
* Only finalized matches contribute to official standings.
* Standings aggregate up to 10 finalized matches.
* Standings must display or make available:
  * Position points
  * Kill points
  * Match totals
  * Cumulative totals
  * First-place finish count
  * Total kills
  * Latest-match placement where needed for tie-breaking
  * Final rank
* Tie-break ordering must follow the canonical scoring document.
* Recalculation after an authorized correction must not double-count results.
* A complete unresolved tie must not be resolved through an invented Android-only rule.

This document does not add alternate scoring behavior or unrelated analytics.

## 12. Screenshot Intake and OCR Workflow

The approved Android OCR flow is:

1. Select a genuine Free Fire MAX scoreboard screenshot through Android Photo Picker.
2. Associate the screenshot with a tournament and match.
3. Validate basic image usability.
4. Detect duplicate screenshot use through the approved content-hash boundary.
5. Preserve the original screenshot where permitted.
6. Display OCR preparation and processing state.
7. Run bundled on-device ML Kit OCR when implemented.
8. Preserve raw OCR evidence.
9. Parse candidate placements, player names, and kill values.
10. Display warnings, incomplete values, and failures.
11. Continue to team-matching and correction review.

Requirements:

* OCR is limited to supported genuine scoreboard screenshots.
* OCR must not create rosters.
* OCR output must never silently become confirmed result data.
* Empty, partial, malformed, duplicate, or unsupported screenshots require explicit handling.
* OCR processing must not block the main thread.
* App interruption must not silently finalize or discard confirmed work.
* Fake screenshots must not be treated as OCR acceptance evidence.

This document does not define camera capture, crop coordinates, exact image preparation, resolutions, parser implementation, or matching formulas.

## 13. Review, Correction, and Finalization

The Android review experience must allow:

* Review of all 12 result rows
* Review of raw and parsed OCR evidence where applicable
* Review of warnings and confidence information
* Review of the top three team candidates
* Correction of player names
* Correction of kill values
* Correction of placement
* Selection of a suggested team
* Manual assignment of an unmatched team
* Resolution of duplicate team assignments
* Confirmation of uncertain results

Finalization must remain disabled or blocked while:

* Result-row count is not exactly 12.
* A team or placement is missing or duplicated.
* A placement or kill value is invalid.
* An OCR-assisted result remains unconfirmed.
* A team remains unmatched.
* A duplicate screenshot conflict remains unresolved.
* Required correction persistence fails.
* Any required validation error remains unresolved.

Finalization requirements:

* Explicit operator action
* Complete valid result data
* Persisted corrections
* Preserved raw evidence
* Confirmed deterministic scoring
* Controlled transition from draft to finalized
* Standings recalculation
* Finalized-only export eligibility

## 14. Local Persistence and App-Restart Recovery

Approved Room-backed Android responsibilities:

* Preserve tournament drafts.
* Preserve roster drafts.
* Preserve match drafts.
* Preserve temporary OCR review and correction state where required.
* Cache approved synchronized records for offline access.
* Preserve pending synchronization operations.
* Restore unfinished work after app restart.

Requirements:

* Multi-record updates use transactions.
* Local schema changes require Room migrations.
* Unsynchronized work must not be lost silently.
* Local, pending, synchronized, conflicted, failed, and finalized states must remain distinguishable.
* Stale local data must not overwrite newer or finalized backend data.
* Finalized records must not become freely editable because they are cached locally.

This document does not define Room entities, DAOs, database versions, converters, or conflict algorithms.

## 15. Offline and Synchronization Behavior

Approved Android offline and synchronization rules:

* Core tournament, roster, manual match, review, scoring, and draft workflows must support approved offline operation.
* Local changes may remain pending while offline.
* Pending work must retry when connectivity returns.
* Synchronization state and failure must be visible.
* Repeated retries must not create duplicate records.
* Conflicts require explicit handling.
* Stale local drafts must not silently replace newer or finalized backend data.
* Failed operations must remain recoverable.
* Finalized-state protection applies locally and remotely.

This document does not define retry intervals, WorkManager configuration, background scheduling, version fields, or merge algorithms.

## 16. Export Workflow

Approved Android export workflow rules:

* Export actions are available only for finalized data.
* Match CSV export contains all 12 teams and approved scoring data.
* Tournament CSV export contains cumulative standings.
* CSV uses UTF-8 and stable approved ordering.
* Google Sheets export is initiated by Android but executed through the secure Supabase backend flow.
* Google credentials must not exist in the Android application.
* Export status, success, failure, and retry must be visible.
* Repeated export attempts must not create duplicate destination rows.
* Exported values must match current finalized application data.

This document does not define exact columns, filenames, spreadsheet names, sharing UI, or destination-selection behavior.

## 17. UI, Accessibility, and Resource Requirements

Approved UI, accessibility, and resource requirements:

* Material 3 components and theme conventions
* Consistent typography, spacing, and reusable components
* Standard loading, empty, success, warning, and error presentation
* Clear visual distinction between draft, pending, conflicted, failed, and finalized states
* Clear indication of unresolved OCR or validation warnings
* Portrait-orientation verification
* Accessibility labels for meaningful controls and images
* Adequate touch targets
* Readable contrast
* Screen-reader-compatible labels where relevant
* User-facing strings stored in Android resources
* No hard-coded user-facing strings in composables
* Confirmation before destructive actions where approved
* Progress feedback for OCR, synchronization, and export operations

This document does not define exact colors, typography values, dimensions, animations, or visual layouts.

## 18. Security and Privacy Requirements

Approved Android security and privacy requirements:

* Android uses only approved public or client credentials.
* Service-role and Google credentials remain backend-only.
* Authorized tournament access is enforced by backend RLS.
* Private screenshots require controlled access.
* Sensitive screenshot or OCR content must not be logged unnecessarily.
* Secrets and local environment files must not be committed.
* Screenshot selection should use the approved least-access Android mechanism.
* No unnecessary Android permissions may be added.
* Network or background behavior requires approved requirements.
* Destructive operations require explicit confirmation and appropriate backend authorization.
* Local sensitive data must be minimized.

This document does not define encryption products, retention periods, compliance claims, biometric authentication, or certificate-pinning requirements.

## 19. Error and Recovery States

Required Android handling is defined for:

* Authentication failure
* Session expiry
* Invalid tournament data
* Invalid or incomplete roster
* Invalid match data
* Local-save failure
* App restart during unfinished work
* Screenshot selection failure
* Invalid or duplicate screenshot
* OCR failure
* Unsupported screenshot layout
* Low-confidence or unmatched team
* Duplicate team assignment
* Finalization failure
* Connectivity loss
* Synchronization conflict or failure
* Standings recalculation failure
* CSV export failure
* Google Sheets export failure

Requirements:

* Errors must remain explicit.
* Existing valid data must be preserved.
* Retry or correction must be available where practical.
* Failed operations must not appear successful.
* Partial failure must not silently finalize, overwrite, synchronize, or export invalid data.

This document does not define exact error messages or retry timing.

## 20. Android Testing Requirements

Required verification categories:

Unit and ViewModel tests:

* Screen-state transitions
* Validation coordination
* Error handling
* ViewModel actions
* Draft and finalization coordination
* No duplicate action processing

Compose UI tests:

* Navigation between approved workflows
* Tournament creation
* Manual roster entry
* Complete roster review
* Match creation
* Placement and kill entry
* Result review
* Finalization blocking
* Standings presentation
* Screenshot selection
* OCR review and correction
* Export eligibility and states

Persistence and recovery:

* Room reads and writes
* Transactions
* Migrations
* Draft recovery after restart
* Pending synchronization recovery
* Finalized-state protection

Device verification:

* Android API 26 device or emulator
* Current configured target API device or emulator
* At least one physical Android device
* Portrait orientation
* Offline operation
* Interrupted synchronization
* App restart during unfinished work
* Low-memory recovery where practical

Security verification:

* No privileged credentials in the APK or repository
* Authorized tournament access only
* No unnecessary permissions
* Controlled screenshot access
* Safe logging

Standard verification commands when the Android project exists:

```bash
gradlew.bat test
gradlew.bat lint
gradlew.bat assembleDebug
```

The v0.1.0 `test`, `lint`, and `assembleDebug` tasks have been verified successfully. Broader checks remain roadmap-controlled.

## 21. Android Constraints and Exclusions

Approved Android constraints:

* Native Android MVP client
* Minimum API 26
* Exactly 12 team slots
* Four to six players per team
* Maximum 10 matches
* Manual roster entry
* Scoreboard-only OCR
* Portrait-orientation testing
* Room for approved local responsibilities
* Supabase as permanent backend authority
* Finalized-only export
* Roadmap-controlled implementation
* No implementation claim without repository evidence

Explicit exclusions:

* Roster-screenshot OCR
* Roster-image import
* Automatic roster extraction
* Public spectator mode
* Player accounts
* Payments and subscriptions
* Unapproved camera workflow
* Unapproved collaboration
* Features outside the roadmap

## 22. Deferred Android Decisions

The following Android decisions remain deferred:

* Exact application ID
* Exact package structure
* Exact module structure
* Exact Gradle, Kotlin, Compose, SDK, and dependency versions
* Exact navigation graph and route names
* Exact screen layouts and design system values
* Exact Material theme tokens
* Exact ViewModel and use-case class structure
* Exact Room entity and DAO structure
* Exact Supabase client abstraction
* Exact background-work implementation
* Exact synchronization conflict UI
* Exact file-export destination behavior
* Exact screenshot layout and crop coordinates
* Exact OCR review presentation
* Exact post-finalization correction authorization UI
* Complete-tie presentation behavior
* Release signing and build-variant configuration

These decisions are intentionally not resolved here.

## 23. Roadmap Alignment

Approved roadmap alignment:

* Phase 1 creates the Android project, layers, Hilt, navigation, theme, shared UI, and baseline tests.
* Phase 2 implements tournament and manual roster workflows.
* Phase 3 implements manual match processing.
* Phase 4 implements scoring and standings presentation.
* Phase 5 implements Room, offline operation, and restart recovery.
* Phase 6 implements authentication, Supabase persistence, synchronization, and finalized-data protection.
* Phase 7 implements Photo Picker screenshot intake and storage.
* Phase 8 implements ML Kit OCR and parsing.
* Phase 9 implements matching, OCR review, correction, and safe finalization.
* Phase 10 implements CSV and Google Sheets export.
* Phase 11 integrates complete workflows and error states.
* Phase 12 completes Android, integration, device, recovery, and security testing.
* Phases 13 and 14 cover beta, release preparation, and production release.
