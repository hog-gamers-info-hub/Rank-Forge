# Phase 0 — Project Foundation and Governance

* **v0.0.1 — Repository Setup:** Create the GitHub repository, local project structure, `.gitignore`, README, and base branches.
* **v0.0.2 — Development Environment:** Configure Android Studio, Java, Android SDK, Supabase CLI, Docker, and required tools.
* **v0.0.3 — Architecture Decisions:** Finalize Android architecture, backend strategy, OCR approach, persistence, and export architecture.
* **v0.0.4 — AI Development Workflow:** Define ChatGPT Work Mode, Codex responsibilities, approval rules, testing rules, and Git workflow.
* **v0.0.5 — Testing and Recovery Preparation:** Add the 12-slot test roster, testing plan, backup process, rollback process, and release controls.

# Phase 1 — Android Application Foundation

* **v0.1.0 — Android Project Creation:** Create the Kotlin, Jetpack Compose, Material 3 application with SDK configuration.
* **v0.1.1 — Application Architecture:** Add presentation, domain, data, and core layers with package boundaries.
* **v0.1.2 — Dependency Injection:** Configure Hilt modules, application setup, and injectable dependencies.
* **v0.1.3 — Navigation Foundation:** Add Navigation Compose and initial placeholder screens.
* **v0.1.4 — Theme and Shared UI:** Add colors, typography, spacing, reusable components, and standard screen states.
* **v0.1.5 — Baseline Testing:** Configure unit tests, Compose UI tests, lint, and build verification.

# Phase 2 — Tournament and Roster Management

* **v0.2.0 — Tournament Creation:** Add tournament name, date, organizer details, and tournament status.
* **v0.2.1 — Tournament List and Details:** Display created tournaments and open their management screens.
* **v0.2.2 — Twelve-Team Slot Structure:** Create exactly 12 fixed team slots for every tournament.
* **v0.2.3 — Manual Team Entry:** Allow team names and slot assignment.
* **v0.2.4 — Player Roster Entry:** Allow four to six players for each team.
* **v0.2.5 — Roster Validation:** Detect missing teams, invalid player counts, duplicate teams, and duplicate players.
* **v0.2.6 — Roster Review:** Add a complete 12-team review screen before tournament processing.

# Phase 3 — Manual Match Processing

* **v0.3.0 — Match Creation:** Add match number, date, map, and draft status with a maximum of 10 matches.
* **v0.3.1 — Manual Placement Entry:** Allow assignment of positions 1 through 12.
* **v0.3.2 — Manual Kill Entry:** Allow kill totals for every participating team.
* **v0.3.3 — Match Result Validation:** Detect duplicate teams, duplicate placements, missing rows, and invalid values.
* **v0.3.4 — Match Review Screen:** Display all 12 results for correction before finalization.
* **v0.3.5 — Draft and Finalized States:** Separate editable drafts from protected finalized matches.
* **v0.3.6 — Match Correction Workflow:** Allow controlled corrections while preserving previous result information.

# Phase 4 — Scoring and Tournament Standings

* **v0.4.0 — Position Points Engine:** Implement the approved points for placements 1 through 12.
* **v0.4.1 — Kill Points Engine:** Calculate one point for every kill.
* **v0.4.2 — Match Total Calculation:** Combine position points and kill points deterministically.
* **v0.4.3 — Cumulative Tournament Standings:** Aggregate results across up to 10 matches.
* **v0.4.4 — Tie-Break Rules:** Apply total points, first-place finishes, total kills, and latest-match placement.
* **v0.4.5 — Standings Interface:** Display rankings, match totals, cumulative totals, and tie-break data.
* **v0.4.6 — Scoring Verification:** Add exhaustive unit tests for placements, kills, totals, and ties.

# Phase 5 — Local Persistence and Offline Recovery

* **v0.5.0 — Room Database Foundation:** Configure Room, entities, DAOs, migrations, and database access.
* **v0.5.1 — Tournament Persistence:** Store and retrieve tournament records locally.
* **v0.5.2 — Roster Persistence:** Store teams, slots, and player rosters.
* **v0.5.3 — Match Persistence:** Store draft and finalized match results.
* **v0.5.4 — Standings Persistence:** Store or reliably regenerate cumulative standings.
* **v0.5.5 — App-Restart Recovery:** Restore unfinished tournaments and matches after app restart.
* **v0.5.6 — Offline Operations:** Allow the core tournament workflow without an internet connection.
* **v0.5.7 — Local Data Integrity:** Add transactions, constraints, duplicate prevention, and failure recovery.

# Phase 6 — Authentication, Backend, and Cloud Sync

* **v0.6.0 — Supabase Authentication:** Add sign-up, login, logout, session restoration, and authentication errors.
* **v0.6.1 — Backend Database Schema:** Create version-controlled migrations for tournaments, teams, players, matches, and results.
* **v0.6.2 — Row Level Security:** Add ownership rules, access policies, grants, and authorization tests.
* **v0.6.3 — Tournament Cloud Storage:** Synchronize tournament and roster records with Supabase.
* **v0.6.4 — Match Cloud Storage:** Synchronize draft and finalized match results.
* **v0.6.5 — Offline Sync Queue:** Queue local changes and retry them when connectivity returns.
* **v0.6.6 — Idempotency and Duplicate Prevention:** Prevent repeated sync operations from creating duplicate records.
* **v0.6.7 — Conflict Resolution:** Define safe handling for competing local and server changes.
* **v0.6.8 — Finalized Data Protection:** Prevent unauthorized or silent overwriting of finalized results.

# Phase 7 — Screenshot Intake and Storage

* **v0.7.0 — Photo Picker Integration:** Select scoreboard screenshots through Android Photo Picker.
* **v0.7.1 — Image Validation:** Validate file type, resolution, orientation, and basic usability.
* **v0.7.2 — Match Screenshot Linking:** Associate each screenshot with its tournament and match.
* **v0.7.3 — Duplicate Screenshot Detection:** Generate image hashes and detect reused screenshots.
* **v0.7.4 — Local Image Preservation:** Preserve original images separately from processed versions.
* **v0.7.5 — Supabase Storage Integration:** Upload approved screenshots securely with controlled access.
* **v0.7.6 — Screenshot Metadata:** Store dimensions, hashes, processing status, timestamps, and storage references.

# Phase 8 — OCR Extraction and Parsing

* **v0.8.0 — ML Kit Integration:** Add the bundled Latin Text Recognition v2 model.
* **v0.8.1 — Fixed Scoreboard Layout Definition:** Define the supported Free Fire MAX screenshot layout and crop coordinates.
* **v0.8.2 — Image Preprocessing:** Add cropping, scaling, contrast adjustment, and enhancement retries.
* **v0.8.3 — Raw Text Extraction:** Extract and preserve raw OCR blocks, lines, and confidence-related metadata.
* **v0.8.4 — Placement Parsing:** Detect positions 1 through 12 from OCR output.
* **v0.8.5 — Player-Name Parsing:** Extract player names from each scoreboard row.
* **v0.8.6 — Kill Parsing:** Extract and validate player or team kill values.
* **v0.8.7 — OCR Failure Handling:** Mark missing, malformed, or uncertain fields for manual review.
* **v0.8.8 — Real Screenshot Evaluation:** Compare OCR results against manually verified genuine screenshots.

# Phase 9 — Team Matching and Manual Correction

* **v0.9.0 — Text Normalization:** Normalize case, whitespace, punctuation, symbols, and common OCR character confusion.
* **v0.9.1 — Player Similarity Matching:** Implement Damerau-Levenshtein and confusion-aware player comparison.
* **v0.9.2 — Team Candidate Scoring:** Calculate team confidence from matched roster players.
* **v0.9.3 — Top-Three Suggestions:** Present the three strongest team candidates for each result.
* **v0.9.4 — Confidence Thresholds:** Implement automatic, confirmation-required, and manual matching tiers.
* **v0.9.5 — Assignment Safety Rules:** Enforce player-match count, candidate lead, and unique team assignment rules.
* **v0.9.6 — OCR Review Interface:** Review all 12 placements with OCR text, kills, team matches, and warnings.
* **v0.9.7 — Manual Field Correction:** Edit player names, kills, placements, and assigned teams.
* **v0.9.8 — Safe Match Finalization:** Block finalization until every conflict and uncertain result is resolved.
* **v0.9.9 — Original and Corrected Data Preservation:** Retain raw OCR output alongside confirmed corrected results.

# Phase 10 — CSV and Google Sheets Export

* **v0.10.0 — Export Data Model:** Define stable export columns, ordering, identifiers, and finalized-result rules.
* **v0.10.1 — Match CSV Export:** Export one finalized match with all 12 teams and scoring fields.
* **v0.10.2 — Tournament CSV Export:** Export cumulative standings across all completed matches.
* **v0.10.3 — UTF-8 and File Validation:** Preserve special characters and verify generated file integrity.
* **v0.10.4 — Google Sheets Edge Function:** Create the secure server-side Sheets integration.
* **v0.10.5 — Google Sheets Match Export:** Send finalized match results to the configured spreadsheet.
* **v0.10.6 — Google Sheets Standings Export:** Export cumulative tournament standings.
* **v0.10.7 — Export Retry and Idempotency:** Handle failed exports without duplicate rows.
* **v0.10.8 — Export Verification:** Compare exported rows and totals against finalized application data.

# Phase 11 — Complete Workflow Integration

* **v0.11.0 — Tournament Setup Flow:** Connect tournament creation, roster entry, validation, and persistence.
* **v0.11.1 — Manual Match Flow:** Connect match creation, entry, review, scoring, and finalization.
* **v0.11.2 — Screenshot Processing Flow:** Connect screenshot import, OCR, parsing, and review.
* **v0.11.3 — Team Matching Flow:** Connect roster matching, confidence levels, suggestions, and corrections.
* **v0.11.4 — Finalization and Standings Flow:** Update tournament standings immediately after valid match finalization.
* **v0.11.5 — Cloud Synchronization Flow:** Integrate local persistence, offline queue, server synchronization, and recovery.
* **v0.11.6 — Export Flow:** Connect finalized results to CSV and Google Sheets.
* **v0.11.7 — Complete Error-State Handling:** Add consistent errors, retries, warnings, and recovery actions throughout the app.

# Phase 12 — Quality Assurance and Security Validation

* **v0.12.0 — Unit Test Completion:** Cover scoring, validation, normalization, matching, and confidence calculations.
* **v0.12.1 — Database Tests:** Test Room operations, migrations, transactions, and data-integrity constraints.
* **v0.12.2 — Backend Tests:** Test Supabase schema, RLS, authorization, synchronization, and idempotency.
* **v0.12.3 — Integration Tests:** Test complete roster, match, OCR, scoring, persistence, and export workflows.
* **v0.12.4 — Compose UI Tests:** Test navigation, data entry, correction, validation, and finalization screens.
* **v0.12.5 — Device Compatibility:** Test API 26, target API, emulators, and physical Android devices.
* **v0.12.6 — Offline and Recovery Testing:** Test connectivity loss, interrupted sync, app restart, and retry behavior.
* **v0.12.7 — Security Review:** Verify credentials, RLS, storage policies, backend authorization, and repository hygiene.
* **v0.12.8 — OCR Acceptance Testing:** Verify team-identification accuracy against the approved genuine screenshot set.
* **v0.12.9 — Regression Test Suite:** Add permanent tests for every important defect fixed.

# Phase 13 — Beta Testing and Production Readiness

* **v0.13.0 — Internal Alpha:** Test the complete application using controlled local tournament data.
* **v0.13.1 — Controlled Real-Tournament Beta:** Run the application with genuine tournament rosters and screenshots.
* **v0.13.2 — Beta Defect Resolution:** Fix functional, OCR, scoring, synchronization, export, and usability defects.
* **v0.13.3 — Performance Optimization:** Improve image processing, database operations, synchronization, and UI responsiveness.
* **v0.13.4 — Migration Rehearsal:** Test production migrations, backup procedures, and corrective migrations.
* **v0.13.5 — Release Configuration:** Prepare signing, build variants, environment configuration, and release metadata.
* **v0.13.6 — Production Operations Review:** Verify monitoring, incident response, backup, rollback, and release documentation.

# Phase 14 — MVP Production Release

* **v0.14.0 — Release Candidate 1:** Freeze features and run complete regression, security, device, and workflow testing.
* **v0.14.1 — Release Candidate Corrections:** Fix release-blocking defects and add regression coverage.
* **v0.14.2 — Final Release Candidate:** Validate signed build, production backend, backups, rollback, and smoke tests.
* **v0.14.3 — Production Deployment:** Deploy the approved database, Edge Functions, storage configuration, and release build.
* **v1.0.0 — Rank-Forge MVP:** Publish the approved production version with release evidence and operational monitoring.
* **v1.0.1 — Initial Stability Patch:** Resolve urgent post-release defects without introducing new features.
