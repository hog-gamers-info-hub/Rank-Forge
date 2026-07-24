# Rank-Forge — Workflow and Platform Responsibilities

## 1. Purpose

This document defines the responsibility, authority and restrictions of every platform used in the Rank-Forge project.

Each platform must be used only for its approved role. GitHub remains the central source of truth for project code and documentation.

---

## 2. ChatGPT Work Mode

### Responsibilities

ChatGPT Work Mode is responsible for:

* Product planning and requirement definition
* System and software architecture
* Project phases, versions and implementation order
* Database, OCR, scoring and export design
* Preparing approved Codex implementation tasks
* Reviewing Codex implementation reports
* Root-cause analysis and debugging guidance
* Acceptance criteria, testing and rollback planning
* Maintaining consistency between decisions and documentation

### Restrictions

ChatGPT Work Mode must not:

* Treat recommendations as approved requirements
* Add unapproved features
* Provide Codex implementation prompts unless explicitly requested
* Mark work complete without verification
* Expose secrets or credentials
* Recommend destructive actions without warnings and rollback steps

---

## 3. Codex

### Responsibilities

Codex is responsible for:

* Inspecting the existing repository
* Implementing explicitly approved tasks
* Following repository architecture and coding rules
* Adding or updating relevant tests
* Running required verification commands
* Reviewing the final Git diff
* Reporting exact changes, results, risks and blockers

### Restrictions

Codex must not:

* Expand the approved scope
* Perform unrelated refactoring
* Add unapproved dependencies
* Modify restricted files
* Commit, push or merge unless explicitly instructed
* Continue automatically to another phase or version
* Claim completion when verification failed or was skipped

Detailed Codex rules are defined in:

```text
AGENTS.md
docs/ai/02_CODEX_ROLE.md
```

---

## 4. Android Studio

### Responsibilities

Android Studio is the primary Android development environment.

It will be used for:

* Creating and managing the Android project
* Kotlin and Jetpack Compose development
* Gradle dependency and build management
* Emulator testing
* Physical-device debugging
* Logcat inspection
* Android linting
* Build variants and signing configuration
* APK and Android App Bundle generation
* Performance and memory inspection

### Restrictions

* Do not store secrets directly in Kotlin files or Android resources.
* Do not commit local Android Studio settings unnecessarily.
* Do not manually modify generated build output.
* Do not add Android permissions without an approved requirement.
* Do not use the emulator as the only release-verification environment.

---

## 5. Visual Studio Code

### Responsibilities

VS Code may be used for:

* Repository and documentation management
* Markdown editing
* Git commands
* Supabase files and SQL migrations
* Edge Function development
* Scripts and configuration files
* Reviewing project-wide diffs

### Restrictions

* VS Code does not replace Android Studio for Android-specific build, emulator or profiling work.
* Do not run destructive terminal commands without reviewing the current directory and command impact.
* Do not edit generated Android files unnecessarily.

---

## 6. GitHub

### Responsibilities

GitHub is the source of truth for:

* Application code
* Supabase migrations
* Edge Functions
* Tests
* Documentation
* Project history
* Branches and pull requests
* Releases and tags
* CI verification

The `main` branch must contain only stable and verified work.

### Restrictions

* Do not develop directly on `main`.
* Do not force-push shared branches without explicit approval.
* Do not commit secrets, private credentials or local environment files.
* Do not commit generated build output.
* Do not rewrite shared Git history.
* Do not merge work with failed mandatory verification.

---

## 7. Supabase

### Responsibilities

Supabase is the primary backend and data source of truth.

It will provide:

* User authentication
* PostgreSQL database
* Row-Level Security
* Screenshot and export storage
* Database functions
* Edge Functions
* Secure integration with Google Sheets
* Future secure OpenAI Vision integration
* Backend logs and operational monitoring

### Restrictions

* Never expose service-role or secret keys in the Android app.
* Every exposed table must use RLS.
* Authorization must enforce record ownership.
* Database schema changes must use versioned migrations.
* Do not edit or delete applied production migrations.
* Do not run destructive SQL without backup, approval and rollback planning.
* Do not use privileged functions merely to bypass RLS.
* Production data must not be manually modified without explicit approval.

---

## 8. Google ML Kit

### Responsibilities

Google ML Kit is the approved MVP OCR technology.

It will be used for:

* On-device text recognition
* OCR processing for supported genuine Free Fire MAX scoreboard screenshots
* Player-name extraction from supported scoreboard screenshots
* Kill-value extraction from supported scoreboard screenshots

### Restrictions

* Tournament roster management is manual in the MVP.
* Teams and players must be entered through structured application input.
* ML Kit OCR applies only to genuine Free Fire MAX scoreboard screenshots.
* Roster-screenshot OCR and roster-image import are outside MVP scope unless separately approved and assigned to a roadmap version.
* ML Kit output must never be treated as automatically correct.
* Original OCR values must be preserved.
* Low-confidence results must require review.
* Scoreboard OCR, roster management, team matching, and scoring must remain separate responsibilities.
* Unsupported screenshot layouts must be flagged instead of silently processed.

---

## 9. Room Database

### Responsibilities

Room provides local Android storage for:

* Offline tournament drafts
* Cached tournament data
* Pending synchronization operations
* Temporary OCR results
* Recovery after app closure or connectivity loss

### Restrictions

* Room is not the permanent source of truth.
* Confirmed server data must not be overwritten by stale local data.
* Synchronization conflicts must be detected and handled explicitly.
* Sensitive data must not be stored locally without necessity.
* Local schema changes must use Room migrations.

---

## 10. Google Cloud Console

### Responsibilities

Google Cloud Console will be used for:

* Enabling the Google Sheets API
* Configuring approved Google credentials
* Managing service accounts or OAuth configuration
* Controlling API access
* Reviewing quotas and usage

### Restrictions

* Google credentials must not be embedded in the Android application.
* Service-account credentials must remain in secure backend secrets.
* Access must follow least-privilege principles.
* Unused APIs and credentials must be disabled or removed.
* Credentials must never be committed to GitHub.

---

## 11. Google Sheets

### Responsibilities

Google Sheets will provide:

* Live tournament result output
* Match-wise result tables
* Cumulative standings
* Final rankings
* Shareable tournament result sheets

Updates must be performed through a secure Supabase Edge Function.

### Restrictions

* Draft or unconfirmed OCR results must not be exported.
* Repeated synchronization must not duplicate rows.
* Manual sheet edits must not silently overwrite confirmed Supabase data.
* Google Sheets must not become the primary database.
* Failed exports must be retryable and logged.

---

## 12. CSV Export

### Responsibilities

CSV will provide:

* Offline tournament export
* Match-wise results
* Overall standings
* Final tournament reports
* Backup and manual sharing

### Rules

* Use UTF-8 encoding.
* Export only finalized data.
* Preserve team and player-name symbols where supported.
* Use stable and documented column names.
* Prevent missing or duplicated teams.

---

## 13. Google Play Console

### Responsibilities

Google Play Console will be used for:

* Internal testing
* Closed and open testing
* Production publishing
* App signing
* Release management
* Store listing
* Policy and privacy declarations
* Crash and Android-vitals review

### Restrictions

* Do not upload unverified builds.
* Do not publish directly to production without staged testing.
* Signing credentials must remain secure.
* Release notes must match the actual changes.
* Required privacy and data-safety disclosures must be accurate.

---

## 14. Crash Reporting and Monitoring

An approved monitoring platform may later be used for:

* Crash reporting
* Non-fatal error reporting
* Application performance monitoring
* Release health
* OCR and synchronization failure monitoring

Monitoring must not collect unnecessary player, screenshot or personal data.

No monitoring service should be added until its privacy impact and operational need are approved.

---

## 15. Physical Android Devices

Physical devices are required for verifying:

* Screenshot selection
* Camera capture
* ML Kit OCR performance
* Local storage
* Offline behavior
* Network reconnection
* Google Sheets export
* Performance on real hardware
* Different screen sizes and Android versions

The emulator may support development but must not be the only release-testing environment.

---

## 16. Platform Source-of-Truth Order

When project information conflicts, follow this order:

1. Current explicit user instructions and approved user decisions govern project intent, scope approvals, corrections, and exceptions.
2. The current explicitly approved implementation task governs immediate execution boundaries, including file scope, constraints, and required verification.
3. `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md` governs phase boundaries, version sequencing, dependencies, and implementation order.
4. Approved canonical product documents govern requirements within their respective domains.
5. `AGENTS.md` and approved `docs/ai` workflow documents govern ChatGPT and Codex execution behavior, approval gates, implementation restrictions, testing expectations, and repository workflow.
6. Verified repository implementation and deployed configuration represent actual current state, but do not automatically redefine approved requirements or future scope.
7. Earlier discussions, drafts, assumptions, and inferred requirements rank last.

The immediate approved implementation task controls immediate file scope, execution boundaries, constraints, and verification only. It cannot rewrite product scope, roadmap sequencing, canonical requirements, security rules, or this governance hierarchy.

### Material-Conflict Rule

If a material conflict appears between sources:

1. Stop the dependent work.
2. Identify the conflicting sources.
3. Do not resolve the conflict by assumption, inference, or convenience.
4. Require an explicit user decision or approved documentation correction before continuing.

The immediate approved implementation task controls execution boundaries only; it cannot rewrite product scope, roadmap sequencing, canonical requirements, or governance rules.

---

## 17. Change Workflow

The standard workflow is:

1. Product requirement is discussed and approved.
2. ChatGPT defines architecture, scope and acceptance criteria.
3. Work is assigned to one project phase and version.
4. ChatGPT prepares an implementation task when requested.
5. Codex inspects and implements the approved scope.
6. Android Studio, Supabase and other relevant platforms are used for verification.
7. Codex reports exact implementation and test results.
8. ChatGPT reviews the result.
9. The user confirms completion.
10. Changes are committed and pushed to GitHub when explicitly approved.
11. Documentation and current project status are updated.

No platform may automatically skip approval, verification or documentation steps.
