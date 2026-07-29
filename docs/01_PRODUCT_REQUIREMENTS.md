# Rank-Forge — Product Requirements

## 1. Document Purpose

This document defines the authoritative repository-level MVP product requirements for Rank-Forge.

It establishes approved product behavior and scope boundaries without defining implementation architecture, database schemas, APIs, classes, screen layouts, algorithms, or other technical details that belong in later canonical documents.

This document follows the approved authority hierarchy. Manual roster management, scoreboard OCR, and the separately staged roster screenshot OCR extension govern this document, and unsupported or conflicting statements must not be promoted into MVP requirements through inference.

## 2. Product Goals

The approved MVP product goals are:

* Manage Free Fire MAX tournaments.
* Maintain tournament teams and player rosters.
* Support the separately staged screenshot-first roster OCR workflow without removing manual roster entry.
* Process match results accurately.
* Calculate deterministic standings.
* Support controlled scoreboard OCR.
* Preserve review and correction before finalization.
* Support local recovery, backend synchronization, and finalized-result export.

This MVP does not establish business, revenue, spectator, payment, subscription, advertising, or unrelated platform goals.

## 3. Primary Users and Actors

The approved high-level actors are:

* Tournament organizer
* Authorized tournament operator or administrator

No spectator accounts, public-user roles, player accounts, team-owner accounts, subscription administrators, payment users, or other unapproved roles are established by this document.

## 4. MVP Functional Requirements

The MVP product must support the following high-level capabilities within approved roadmap sequencing:

* Create and manage Free Fire MAX tournaments.
* Maintain tournament teams and player rosters.
* Process up to the approved maximum number of matches per tournament.
* Record match results and calculate standings deterministically.
* Associate genuine supported Free Fire MAX scoreboard screenshots with tournament matches.
* Apply ML Kit OCR to supported scoreboard screenshots.
* Review, correct, confirm, finalize, store, synchronize, and export approved tournament results.

Tournament and roster requirements:

* Every tournament must use exactly 12 fixed team slots.
* Manual structured team and player roster entry must remain available for every tournament.
* The separately staged roster OCR workflow may produce candidates from three roster screenshots expected to cover the 12 fixed slots, with four visible team slots per screenshot and four to six player names per slot.
* Roster OCR candidates require review and explicit confirmation before they may replace roster data.
* Each team must contain four to six players.
* Team names must be unique within a tournament.
* Player duplication and invalid roster sizes must be detected.
* A complete roster review must be required before tournament processing proceeds.

Match-processing requirements:

* A tournament must support a maximum of 10 matches.
* Match information must include the approved high-level match identity fields defined by the roadmap: match number, date, map, and draft status.
* Manual match processing must support positions 1 through 12.
* Kill values must be entered for participating teams.
* Duplicate teams, duplicate placements, missing rows, and invalid values must be detected.
* All 12 result rows must be reviewed before finalization.
* Draft and finalized states must remain distinct.
* Finalized results must be protected from silent or unauthorized overwriting.
* Corrections must be controlled and preserve previous or original result information where required.

## 5. Core User Workflows

The approved MVP workflows are:

* A tournament organizer or authorized operator creates a tournament and prepares exactly 12 team slots.
* The operator manually enters and reviews team names and player rosters before tournament processing continues.
* Where the approved staged workflow is available, the operator may review and correct roster OCR candidates from privately preserved, manually cropped roster screenshots before confirmation.
* The operator creates match records within the approved match limit and enters or reviews match identity information.
* The operator processes match results through manual entry and, where applicable, supported scoreboard screenshot review.
* The operator reviews detected values, resolves conflicts, confirms uncertain OCR results, and makes required corrections before finalization.
* Finalized match results contribute to tournament standings.
* Finalized data is preserved, synchronized when applicable, and exported through approved flows only after finalization.

These workflows define product intent. They do not define screens, navigation, storage models, or implementation classes.

## 6. Validation and Data-Integrity Requirements

The MVP must enforce the following validation and integrity requirements:

* Exactly 12 team slots must be used for every tournament.
* Each team must contain four to six players.
* Team names must be unique within a tournament.
* Duplicate players and invalid roster sizes must be detected.
* Match placements must be unique from 1 through 12.
* Duplicate team assignment within a match must be detected.
* Missing rows and invalid result values must be detected before finalization.
* All 12 result rows must be reviewed before finalization.
* Deterministic scoring rules must be applied consistently.
* Finalized results must remain distinguishable from drafts.
* Finalized data must not be silently overwritten.

Scoring and standings requirements:

* Placement points must use the approved canonical scoring table.
* One kill equals one point.
* Match total equals placement points plus kill points.
* Tournament standings aggregate finalized match results.
* Tie-break order is:
  1. Total points
  2. Number of first-place finishes
  3. Total kills
  4. Placement in the latest match
* Scoring must be deterministic.
* Detailed scoring specification remains governed by `docs/05_SCORING_AND_PROCESSING_RULES.md` once populated.

Approved placement points:

* 1st: 12
* 2nd: 9
* 3rd: 8
* 4th: 7
* 5th: 6
* 6th: 5
* 7th: 4
* 8th: 3
* 9th: 2
* 10th: 1
* 11th: 0
* 12th: 0

## 7. OCR and Manual-Review Requirements

Scoreboard screenshot and OCR requirements:

* Screenshot processing applies only to genuine supported Free Fire MAX scoreboard screenshots.
* Screenshots must be associated with a tournament and match.
* Original screenshots and raw OCR output must be preserved where permitted.
* OCR may extract placements, player names, and kill values from supported scoreboard layouts.
* OCR extraction must remain separate from team matching and scoring.
* OCR output must never be treated as automatically correct.
* Missing, malformed, unsupported, or uncertain values must require review.
* Genuine screenshot acceptance remains deferred until approved real screenshot data exists.

Roster screenshot OCR requirements:

* Roster screenshot OCR follows the staged cross-phase roadmap extension only.
* Exactly three roster screenshots are expected to cover all 12 fixed slots, with four visible team slots per image.
* The original selected image must be preserved privately, and the operator must crop the roster panel in-app before OCR.
* OCR must use the cropped roster panel or reproducible crop metadata, not the full roster screenshot.
* Representative screenshots and manually verified expected data are prerequisites for layout coordinates and extraction-accuracy work.
* Roster OCR output is candidate data only until review, correction where needed, and explicit confirmation.
* Manual roster entry remains available for corrections, unsupported screenshots, incomplete data, and fallback.

Team matching and manual-correction requirements:

* Detected player names may be normalized and compared against the manually maintained roster.
* Team matching must preserve one detected-player-to-one-roster-player assignment.
* Team assignment must be unique within a match.
* Uncertain or unmatched results must require operator confirmation or manual assignment.
* The operator must be able to review and correct placements, kills, player names, and team assignments.
* Finalization must be blocked while required values, conflicts, or uncertain results remain unresolved.
* Original OCR data and corrected confirmed data must remain distinguishable.

This document does not define crop coordinates, screen resolutions, parsing expressions, OCR preprocessing algorithms, additional screenshot layouts, confidence thresholds, or matching algorithms.

## 8. Persistence and Synchronization Requirements

The approved MVP persistence and synchronization requirements are:

* Tournament, roster, match, and result drafts must survive expected app restarts.
* Core tournament workflows must support approved offline operation.
* Pending changes must synchronize when connectivity returns.
* Repeated synchronization must not create duplicate records.
* Synchronization conflicts must be detected and handled explicitly.
* Supabase is the permanent backend source of truth.
* Finalized data must not be silently overwritten by stale local data.
* Roster replacement after created or finalized matches requires an explicit safety policy before persistence work.
* Existing Room and Supabase roster persistence must be extended rather than duplicated.

This document does not define Room entities, Supabase tables, queues, RPCs, policies, or conflict-resolution algorithms.

## 9. Export Requirements

The approved MVP export requirements are:

* Only finalized results may be exported.
* CSV exports must use UTF-8.
* Match exports must include all 12 teams and approved scoring fields.
* Tournament exports must include cumulative standings.
* Google Sheets export must run through an approved secure backend flow.
* Retry behavior must not create duplicate rows.
* Exported totals and ordering must match finalized application data.

This document does not define exact export columns that have not yet been approved.

## 10. Security and Access Requirements

The approved high-level MVP security and access requirements are:

* Users may access only authorized tournament data.
* Supabase-exposed tables require Row Level Security.
* Privileged credentials must not be included in the Android application.
* Google credentials must remain in secure backend configuration.
* Private tournament screenshots must not be committed publicly without explicit approval.
* Destructive or production data operations require approval and rollback planning.

This document does not define retention periods, compliance certifications, account-recovery behavior, or policy SQL.

## 11. MVP Constraints

The MVP is constrained by the following approved boundaries:

* Native Android application.
* Minimum Android API and detailed device support remain governed by approved Android and testing documents.
* Exactly 12 team slots.
* Four to six players per team.
* Maximum 10 matches per tournament.
* Genuine screenshots are required for OCR acceptance evidence.
* Android implementation begins only according to the roadmap.
* Current repository status must not be represented as completed product implementation.

## 12. MVP Exclusions

The current approved MVP excludes:

* Silent automatic confirmation of uncertain OCR results
* Export of draft or unconfirmed results
* Public spectator accounts
* Player-login workflows
* Payments and subscriptions
* Features not approved and assigned to the roadmap

## 13. Acceptance Boundaries

The MVP is acceptable only when evidence confirms:

* Exactly 12 team slots work correctly.
* Four-to-six-player roster validation works.
* Up to 10 matches can be processed.
* Deterministic scoring and tie-break tests pass.
* Invalid or incomplete results cannot be finalized.
* Manual correction and conflict resolution work.
* Finalized data persists correctly.
* Duplicate synchronization and export rows are prevented.
* CSV export passes.
* Google Sheets export passes when implemented.
* Required unit, integration, UI, database, security, device, and recovery verification passes.
* No critical or high-severity defect remains unresolved.
* OCR acceptance is completed only after approved genuine screenshots are available.

These acceptance boundaries define MVP release evidence requirements. They do not mean the current repository already satisfies them.

## 14. Pending Decisions

The following matters remain pending later approval or later canonical documentation:

* Supported genuine scoreboard layout details
* Approved real screenshot acceptance dataset
* Export column details not yet approved in a populated canonical export document
* Detailed OCR parsing, preprocessing, normalization, and matching definitions
* Cross-team duplicate-player policy for roster validation
* Roster replacement eligibility after created or finalized matches
* Detailed Android, database, backend, and export technical specifications intentionally deferred to later canonical documents
