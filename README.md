# Rank-Forge

## Overview

Rank-Forge is a planned native Android application for Free Fire MAX tournament organizers and authorized operators.

Its approved MVP direction is to support tournament management, roster management, match-result processing, deterministic scoring and standings, scoreboard screenshot OCR assistance, manual correction, offline recovery, backend synchronization, and finalized-result export.

The project is in the Phase 1 Android foundation phase, with the v0.1.0 foundation now tracked in the repository.

## Current Repository Status

This repository contains canonical project documentation, workflow and governance material, and the v0.1.0 Android foundation.

README is a high-level entry point. Canonical details live under `docs/`.

Android implementation has begun with v0.1.0 according to the approved roadmap.

Current tracked repository facts:

* The tracked v0.1.0 Android Gradle project and Kotlin application source provide the initial Compose foundation screen.
* No tracked Room schema implementation or Android workflow implementation has been verified.
* Supabase scaffolding is present, including `supabase/config.toml` and placeholder directories, but no tracked migration set, Edge Function implementation, or backend synchronization implementation has been verified.
* No tracked ML Kit OCR pipeline, scoreboard parser, team-matching implementation, CSV export implementation, Google Sheets export implementation, or test suite implementation has been verified.

## Approved MVP Scope

The approved MVP scope includes:

* Native Android client
* Tournament creation and management
* Exactly 12 fixed team slots per tournament
* Manual structured team and player roster entry
* Four to six players per team
* Maximum 10 matches per tournament
* Manual match result entry
* Scoreboard screenshot intake for genuine supported Free Fire MAX scoreboards
* On-device Google ML Kit OCR for scoreboard text extraction when implemented
* Team matching against manually maintained rosters
* Operator review, correction, and confirmation
* Deterministic scoring and standings
* Room-backed local persistence and app-restart recovery when implemented
* Supabase authentication, backend authority, synchronization, RLS, and storage when implemented
* CSV and Google Sheets export for finalized results when implemented

## Explicit MVP Exclusions

The approved MVP excludes:

* Roster-screenshot OCR
* Roster-image import
* Automatic roster extraction
* Public spectator access
* Player-login accounts
* Team-owner accounts
* Payments or subscriptions
* Advertising or monetization features
* Unapproved collaboration or organization features
* Export of draft, invalid, unresolved, or unconfirmed results
* Google Sheets as a source of truth

## Technology Direction

The approved technology direction is:

* Kotlin
* Native Android
* Jetpack Compose
* Material 3
* MVVM layered architecture
* Navigation Compose
* Hilt according to roadmap sequencing
* Kotlin Coroutines and `StateFlow`
* Room
* Supabase
* Google ML Kit bundled Latin Text Recognition v2
* Android Photo Picker
* CSV export
* Secure Google Sheets export through Supabase backend flow

## Documentation Map

Canonical project documents:

* [Project Overview](docs/00_PROJECT_OVERVIEW.md)
* [Product Requirements](docs/01_PRODUCT_REQUIREMENTS.md)
* [System Architecture](docs/02_SYSTEM_ARCHITECTURE.md)
* [Database Design](docs/03_DATABASE_DESIGN.md)
* [OCR and Team Matching](docs/04_OCR_AND_TEAM_MATCHING.md)
* [Scoring and Processing Rules](docs/05_SCORING_AND_PROCESSING_RULES.md)
* [Android Application](docs/06_ANDROID_APP.md)
* [Supabase Backend](docs/07_SUPABASE_BACKEND.md)
* [Google Sheets and CSV Export](docs/08_GOOGLE_SHEETS_AND_CSV.md)
* [Testing and Acceptance](docs/09_TESTING_AND_ACCEPTANCE.md)
* [Workflow and Platform Roles](docs/10_WORKFLOW_AND_PLATFORM_ROLES.md)
* [Security and Privacy](docs/11_SECURITY_AND_PRIVACY.md)
* [Phase and Version Roadmap](docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md)
* [AI Governance Documents](docs/ai/)

## Roadmap

The approved roadmap is summarized at a high level as:

* Phase 0: Documentation and governance foundation
* Phase 1: Android foundation
* Phase 2: Tournament and roster foundation
* Phase 3: Manual match processing
* Phase 4: Scoring and standings
* Phase 5: Room and offline recovery
* Phase 6: Supabase authentication, backend, and synchronization
* Phase 7: Screenshot intake and storage
* Phase 8: OCR extraction and parsing
* Phase 9: Team matching, review, correction, and finalization
* Phase 10: CSV and Google Sheets export
* Phase 11: Full workflow integration
* Phase 12: QA, security, and regression validation
* Phase 13: Controlled beta
* Phase 14: Production release

## Development Workflow

Development workflow is governed by the approved project instructions:

* GitHub is the source of truth.
* Work is performed one approved task at a time.
* Codex implements only explicitly approved tasks.
* ChatGPT assists with planning, review, documentation, and prompt preparation.
* Files outside the approved task scope must not be modified.
* Branch creation is not part of the current Phase 0 manual-save workflow unless explicitly approved.
* Implementation must follow `AGENTS.md` and `docs/ai/`.

## Security and Privacy Notes

Key approved security and privacy boundaries are:

* Android must not contain Supabase service-role keys or Google privileged credentials.
* Supabase RLS must be ownership-based.
* Authentication alone is not sufficient authorization.
* Private screenshots must not be committed publicly.
* Secrets and local environment files must not be committed.
* Only finalized results may be exported.
* Destructive operations require explicit approval, backup, and rollback planning.

## Testing and Acceptance

Release requires passing the relevant unit, integration, UI, Room, Supabase, OCR, export, security, recovery, and device tests when those implementations exist.

Fake screenshots are not OCR acceptance evidence.

Genuine screenshot OCR acceptance is deferred until approved real screenshots and manually verified ground truth exist.

Critical and high-severity defects block release.

The v0.1.0 Gradle test, lint, and debug build checks have passed; broader test coverage remains deferred to later roadmap versions.

## Contribution and Changelog Status

`CONTRIBUTING.md` and `CHANGELOG.md` are intentionally deferred until their required content is clearly defined.

They are not populated by this task.

## Source of Truth

README is a high-level entry point only.

Canonical requirements are in `docs/`.

Workflow and agent behavior are governed by `AGENTS.md` and `docs/ai/`.

Phase sequencing is governed by [docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md](docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md).

If information conflicts, use the approved source-of-truth precedence documented in the canonical workflow files.
