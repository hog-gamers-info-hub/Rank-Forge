# Rank-Forge — Project Overview

## 1. Product Summary

Rank-Forge is an Android tournament-management and result-processing application planned for Free Fire MAX tournaments.

At a high level, the approved product direction is intended to support tournament and team-roster management, manual match-result entry, deterministic scoring and standings, Free Fire MAX scoreboard screenshot intake, ML Kit scoreboard OCR, team matching with manual correction, offline and local recovery, Supabase synchronization, and CSV and Google Sheets export.

This overview is the high-level canonical summary of the project. Detailed product requirements, architecture, database design, OCR behavior, scoring rules, security controls, and implementation specifics are governed by their respective canonical documents and the approved roadmap.

## 2. Purpose and Problem Statement

Rank-Forge is intended to help tournament organizers process Free Fire MAX tournament results more consistently and with less manual reconciliation across rosters, match results, and final standings.

The approved product direction combines manual operator workflows with planned OCR-assisted scoreboard processing so that tournament results can be reviewed, corrected, finalized, stored, and exported in a controlled way. The product is intended to reduce scoring mistakes, duplicate handling issues, and release-time confusion while keeping approval, verification, and correction steps explicit.

## 3. Primary Users

The approved high-level user roles are:

* Tournament organizers
* Authorized tournament operators or administrators

No public-user, spectator, player-login, payment, subscription, or other unapproved roles are established by this overview.

## 4. MVP Capability Overview

The approved MVP product direction is planned to cover:

* Tournament creation and tournament roster management
* Manual team and player roster entry
* Manual match-result entry and review
* Deterministic scoring and cumulative standings
* Scoreboard screenshot intake for supported Free Fire MAX scoreboards
* Google ML Kit OCR for supported scoreboard screenshots
* Team matching and operator correction workflows
* Local persistence and offline recovery
* Supabase-backed authentication, storage, backend data, and synchronization
* Finalized-result export to CSV and Google Sheets

These capabilities are roadmap-controlled. Their inclusion here describes approved product direction, not verified implementation status in the current repository.

## 5. MVP Scope Boundaries

The current approved MVP boundaries are:

* Tournament rosters are entered and maintained manually.
* ML Kit OCR applies only to genuine supported Free Fire MAX scoreboard screenshots.
* Roster-screenshot OCR and roster-image import are outside MVP scope unless separately approved and scheduled in the roadmap.
* OCR output requires validation and correction before finalization.
* Only finalized results may be exported.
* The roadmap controls when each capability is implemented.

Manual roster management and scoreboard OCR are separate responsibilities. This overview does not authorize roster-image intake, automatic finalization, or any roadmap capability ahead of its approved phase and version.

## 6. Approved Technical Direction

The approved high-level technical direction is:

* Native Android
* Kotlin
* Jetpack Compose and Material 3
* MVVM with layered architecture
* Kotlin Coroutines and `StateFlow`
* Google ML Kit for MVP scoreboard OCR
* Room for approved local and offline persistence
* Supabase for authentication, backend data, storage, Row Level Security, and synchronization
* Google Sheets and CSV for approved exports
* GitHub as the source of truth for code and repository documentation

This overview does not define detailed schemas, APIs, packages, permissions, or OCR and matching algorithms.

## 7. Current Repository Status

The current tracked repository state verifies Phase 0 foundation work rather than application implementation.

Verified repository facts:

* Canonical Phase 0 product and governance documents from `docs/00_PROJECT_OVERVIEW.md` through `docs/11_SECURITY_AND_PRIVACY.md` are populated in the repository.
* Approved AI governance documents under `docs/ai/00_AI_WORKFLOW.md` through `docs/ai/07_PROMPT_AND_APPROVAL_PROCESS.md` are populated or otherwise present as approved governance artifacts.
* `README.md` is populated as the high-level repository entry point.
* The approved phase-and-version roadmap is present at `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`.
* `CHANGELOG.md` and `CONTRIBUTING.md` remain intentionally deferred until their required content is clearly defined.
* Phase 0 closure still requires a fresh closure audit after blocker corrections.
* Supabase repository scaffolding is present, including `supabase/config.toml` and placeholder directories for migrations, functions, and tests.
* Synthetic roster fixture data is present at `test-data/rosters/teams.csv`.
* Repository tooling includes `package.json` with a Supabase CLI development dependency and script entry.
* Recent Git history verifies ongoing Phase 0 governance-alignment work.

Functionality not yet verified as implemented in the tracked repository:

* No tracked Android Gradle project or Kotlin application source has been verified.
* No tracked application screens, Android UI workflows, or Compose implementation have been verified.
* No tracked Room persistence implementation has been verified.
* No tracked Supabase migration set, Edge Function implementation, or backend synchronization logic has been verified.
* No tracked ML Kit OCR implementation, scoreboard parser, deterministic scoring implementation, or team-matching implementation has been verified.
* No tracked CSV or Google Sheets export implementation has been verified.

## 8. Roadmap Position

Rank-Forge is currently in Phase 0, which covers project foundation and governance.

Android implementation begins only with Phase 1. Phase boundaries, version sequencing, dependencies, and implementation order are controlled by `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`.

This task does not change the roadmap. The roadmap remains the authority for when approved capabilities move from planned scope into implementation. Phase 1 still requires explicit user approval even after Phase 0 blocker corrections.

## 9. Documentation Map

The current canonical documentation areas are:

* Product overview and requirements: `docs/00_PROJECT_OVERVIEW.md` and `docs/01_PRODUCT_REQUIREMENTS.md`
  Status: populated as the high-level overview and canonical product requirements baseline.
* Architecture and database: `docs/02_SYSTEM_ARCHITECTURE.md` and `docs/03_DATABASE_DESIGN.md`
  Status: populated as canonical architecture and database-planning documents.
* OCR and scoring: `docs/04_OCR_AND_TEAM_MATCHING.md` and `docs/05_SCORING_AND_PROCESSING_RULES.md`
  Status: populated as canonical OCR, matching, scoring, and processing-rule documents.
* Android and Supabase: `docs/06_ANDROID_APP.md` and `docs/07_SUPABASE_BACKEND.md`
  Status: populated as canonical Android and Supabase planning documents.
* Export: `docs/08_GOOGLE_SHEETS_AND_CSV.md`
  Status: populated as the canonical finalized-result export document.
* Testing: `docs/09_TESTING_AND_ACCEPTANCE.md`
  Status: populated and approved for current testing and acceptance planning.
* Workflow and platform roles: `docs/10_WORKFLOW_AND_PLATFORM_ROLES.md`
  Status: populated and approved for governance, authority, and platform responsibilities.
* Security and privacy: `docs/11_SECURITY_AND_PRIVACY.md`
  Status: populated and approved for canonical security and privacy requirements.
* AI-assisted development governance: `docs/ai/`
  Status: approved AI governance documents are populated or otherwise present after the recent blocker-resolution tasks.
* Phase-and-version roadmap: `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`
  Status: populated and authoritative for sequencing and implementation order.
* Deferred repository process documents: `CHANGELOG.md` and `CONTRIBUTING.md`
  Status: intentionally deferred until their required content is clearly defined.

## 10. Current Limitations and Pending Decisions

Current limitations and pending matters include:

* Planned: tournament-processing, OCR, synchronization, and export capabilities are approved product scope but are not yet verified as implemented in the tracked repository.
* Not yet implemented: Android application code, application screens, persistence logic, OCR processing, team matching, synchronization, and export flows are not currently verified in tracked source files.
* Documentation completion is not implementation completion: the populated canonical documents define approved direction, but tracked application and backend implementation still remains unverified.
* Planned: real Free Fire MAX screenshots for OCR acceptance are not yet available in the repository, and OCR acceptance testing remains deferred.
* Requires a fresh audit: Phase 0 closure still requires a fresh closure audit after blocker corrections.
* Requires explicit approval: Phase 1 Android implementation still requires explicit user approval.
* Requires an explicit decision: any capability outside the approved roadmap order, including roster-screenshot OCR or roster-image import, requires separate approval and scheduling.
* Requires an explicit decision: production backup commands and production restoration execution remain to be validated before the first production deployment.
