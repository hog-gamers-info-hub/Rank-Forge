# Rank-Forge - Testing and Acceptance

## 1. Document Purpose

This document defines the approved testing and acceptance plan for Rank-Forge.

It aligns testing expectations with the approved product scope, roadmap sequencing, OCR boundaries, manual roster workflow, Android requirements, Supabase backend requirements, export requirements, security requirements, and release gates.

This document is a testing and acceptance authority only. It does not create tests, fixtures, Android code, Supabase migrations, Edge Functions, CI workflows, screenshots, schemas, storage resources, or configuration.

## 2. Testing Principles

The approved testing principles are:

* Tests verify approved canonical requirements only.
* Tests must distinguish planned functionality from implemented functionality.
* Deterministic domain logic must be unit tested independently from UI, OCR, storage, and networking.
* Manual and OCR-assisted match processing must converge into the same validated result model.
* Tournament rosters are manually entered and maintained through structured application input.
* Roster import, roster-screenshot OCR, roster-image import, and automatic roster extraction are outside MVP scope unless separately approved and added to the roadmap.
* OCR confidence must not affect tournament scoring.
* Finalized data must not be silently overwritten.
* Repeated synchronization and export attempts must not create duplicates.
* Critical and high-severity defects block release.
* Evidence must be reproducible and traceable to commit or release context.

## 3. Current Testing Status

Current verified status is limited to repository evidence:

* Phase 0 documentation and governance are being aligned.
* Android implementation has not started in any tracked Android project files because no tracked Android project has been verified in the repository.
* Production Room schema has not been verified in tracked repository files.
* Production Supabase schema has not been verified in tracked repository files.
* OCR implementation has not been verified in tracked repository files.
* Synchronization implementation has not been verified in tracked repository files.
* Export implementation has not been verified in tracked repository files.
* Synthetic roster fixture requirements exist for planned testing.
* Real genuine Free Fire MAX screenshot acceptance remains deferred until approved samples exist.

This document does not claim that a test suite currently exists or currently passes unless verified by tracked repository evidence.

## 4. Test Data and Evidence

Synthetic roster fixture requirements:

* Exactly 12 team slots
* One unique team name per slot
* Four to six players per team
* No duplicate player names within the same team
* OCR-challenging names for normalization and matching tests

Current repository evidence includes the synthetic roster fixture at [test-data/rosters/teams.csv](/C:/Projects/Rank-Forge/test-data/rosters/teams.csv).

Genuine screenshot test data requirements:

* Clear screenshots
* Compressed screenshots
* Similar player names
* Symbols and mixed characters
* Low but usable quality
* All 12 placements
* Manually verified ground truth for every screenshot
* Screenshot quality documentation
* Reproducible expected-result files

Required evidence boundaries:

* Fake screenshots are not OCR acceptance evidence.
* Private or sensitive screenshots must not be committed publicly.
* Real screenshot acceptance remains deferred until approved samples exist.

## 5. Unit Test Requirements

Required scoring unit tests:

* Every placement from 1 through 12
* Position-point calculation
* Kill-point calculation
* Match-total calculation
* Tournament-total calculation
* Zero kills
* High kill totals without assuming an unapproved maximum
* Exclusion of draft matches
* Deterministic repeat calculation

Required validation unit tests:

* Exactly 12 result rows
* Fewer than 12 rows
* More than 12 rows
* Duplicate teams
* Duplicate placements
* Missing teams
* Missing placements
* Invalid placement
* Negative kills
* Non-numeric kills
* Unresolved OCR-assisted values
* Unmatched teams
* Incomplete finalization blocking

Required matching and normalization unit tests:

* Exact player-name matches
* Case differences
* Extra whitespace
* Symbols and punctuation
* Zero and letter `O` confusion
* One and letter `I` confusion
* Incorrect OCR characters
* Similar player names across teams
* Missing names
* Duplicate candidates
* Unmatched teams
* Damerau-Levenshtein comparison behavior
* Approved confidence tiers
* Automatic-assignment conditions
* Unique assignment rules
* Top-three candidate ordering

This document does not define new scoring formulas, matching formulas, or confidence calculation details.

## 6. Integration Test Requirements

Required integration tests must cover:

* Manual tournament creation
* Manual 12-team roster entry and validation
* Manual roster review
* Match creation
* Manual result persistence
* Match review and finalization
* Correction persistence
* Finalized-result retrieval
* Standings recalculation
* Room persistence and restart recovery
* Supabase synchronization
* Offline queue behavior
* Duplicate synchronization prevention
* Conflict detection
* Finalized-data protection
* CSV export
* Google Sheets export through the approved backend flow

Roster import is not part of the approved MVP integration scope.

## 7. Android UI Test Requirements

Required Android UI tests must cover:

* Tournament creation
* Manual team entry
* Manual player entry
* Complete 12-team roster review
* Match creation
* Manual placement entry
* Manual kill entry
* Screenshot selection
* OCR processing state
* OCR review
* Reviewing all 12 result rows
* Editing player names
* Editing kill values
* Editing placements
* Selecting suggested team matches
* Manually selecting unmatched teams
* Resolving duplicate team assignments
* Blocking incomplete finalization
* Finalizing a valid match
* Viewing standings
* Export eligibility and export status

This document does not define screen layouts, route names, UI component names, or visual design details.

## 8. Device and Recovery Test Requirements

Required device and recovery testing must cover:

* Android API 26 minimum-supported device or emulator
* Current configured target API device or emulator
* At least one physical Android device
* Portrait orientation
* Offline mode
* Connectivity loss
* Interrupted synchronization
* App restart during unfinished tournament work
* App restart during unfinished match review
* App restart during pending export
* Low-memory recovery where practical

This document does not claim that these tests currently pass.

## 9. Room Persistence Test Requirements

Required Room persistence tests must cover:

* CRUD behavior
* Transactions
* Migrations
* Constraints
* Tournament draft persistence
* Roster draft persistence
* Match draft persistence
* OCR review state persistence where required
* Pending synchronization recovery
* App-restart recovery
* Finalized-state protection
* Unsynchronized draft preservation
* Prevention of stale local overwrite of newer or finalized backend data

This document does not define Room schema or entity details.

## 10. Supabase Backend Test Requirements

Required Supabase backend tests must cover:

* Migration application
* Foreign-key integrity
* Uniqueness constraints
* Controlled status transitions
* RLS enabled on exposed tables
* Owner access to own tournament hierarchy
* Cross-owner access denial
* Authentication-only access being insufficient
* `SELECT`, `INSERT`, `UPDATE`, and `DELETE` policies tested separately
* Storage access following tournament ownership
* Privileged function restrictions
* Synchronization idempotency
* Conflict handling
* Finalized-data protection
* Secure backend-only Google Sheets credential use

This document does not define SQL or policy text.

## 11. OCR and Screenshot Test Requirements

Required OCR and screenshot tests must cover:

* Genuine clear screenshots
* Compressed screenshots
* Similar player names
* Symbols and mixed characters
* Low but usable quality
* All 12 placements
* Missing fields
* Malformed fields
* Duplicate screenshot hash
* Unsupported layouts
* Empty OCR result
* Partial OCR result
* OCR processing failure

OCR acceptance requirements:

* Real screenshots require manually verified ground truth.
* OCR output is compared field by field.
* Player names, kills, placements, and team assignments are measured separately.
* Corrections are recorded.
* Screenshot quality is documented.
* Results must be reproducible.
* Target team-identification accuracy is at least 95 percent on the approved real screenshot test set.
* Scoring accuracy after operator confirmation is 100 percent.
* OCR acceptance remains deferred until approved genuine screenshots are available.

Fake screenshots are not accepted as OCR acceptance evidence.

## 12. Team-Matching and Manual-Correction Test Requirements

Automatic team assignment is allowed only when:

1. At least three of four detected players match the same roster team.
2. The team confidence score is at least `90`.
3. The leading candidate has at least a `10`-point advantage over the second candidate.
4. The candidate team has not already been assigned in the same match.
5. No unresolved duplicate-player or assignment conflict remains.
6. Required detected values are valid.

Approved confidence tiers:

* `90-100`: automatic-assignment tier
* `75-89`: suggestion requiring operator confirmation
* Below `75`: manual or unmatched

Required manual correction tests must verify:

* Review of all 12 placements
* Correction of player names
* Correction of kill values
* Correction of placement
* Top-three team suggestions
* Manual selection of unmatched teams
* Duplicate team assignment resolution
* Confirmation of uncertain results
* Preservation of raw OCR output after correction
* Blocking finalization while unresolved warnings remain

## 13. Scoring and Standings Test Requirements

The approved placement-points table is:

| Placement | Position points |
| --------: | --------------: |
|         1 |              12 |
|         2 |               9 |
|         3 |               8 |
|         4 |               7 |
|         5 |               6 |
|         6 |               5 |
|         7 |               4 |
|         8 |               3 |
|         9 |               2 |
|        10 |               1 |
|        11 |               0 |
|        12 |               0 |

Scoring rules under test must verify:

* One kill equals one point.
* `match total = position points + kill points`
* Tournament standings aggregate finalized matches only.
* Draft matches are excluded.
* Corrections recalculate standings without duplicate counting.

Tie-break tests must verify the approved order exactly:

1. Total points
2. Number of first-place finishes
3. Total kills
4. Placement in the latest match

Complete equality across all approved tie-break criteria must remain unresolved unless later approval defines further handling.

This document does not add extra tie-breakers.

## 14. Export Test Requirements

Only finalized results are export-eligible.

Required CSV export tests:

* Match CSV contains exactly 12 teams
* Tournament CSV contains cumulative standings
* UTF-8 character preservation
* Correct placements
* Correct position points
* Correct kill points
* Correct match totals
* Correct tournament totals
* Correct tie-break ordering
* Draft and unresolved results cannot export
* Empty or partial CSV is not reported as success

Required Google Sheets export tests:

* Export requires authorization
* Backend credentials are not exposed to Android
* Match export uses finalized match data only
* Tournament export uses finalized standings only
* Retry does not duplicate rows
* Invalid payload fails safely
* Backend failure is reported clearly
* Partial write handling is verified
* Exported totals match finalized data

This document does not define exact columns or sheet names.

## 15. Security and Privacy Test Requirements

Required security and privacy tests must verify:

* Users access only authorized tournaments
* Cross-owner data access is rejected
* RLS is enabled and ownership-based
* Android contains no service-role key
* Android contains no Google privileged credentials
* Secrets are not committed
* Private screenshots are not committed publicly
* Screenshot storage follows ownership access
* Export uses the approved backend flow
* Unauthorized writes are rejected
* Logs avoid secrets, tokens, private screenshot data, and sensitive OCR content
* Destructive operations require approval evidence where applicable

## 16. Acceptance Criteria

A release is acceptable only when evidence confirms:

* Exactly 12 team slots work correctly.
* Four-to-six-player roster validation works.
* Up to 10 matches can be processed.
* Manual roster entry and review work.
* Manual match processing works.
* OCR-assisted processing works only after approved genuine screenshot acceptance evidence exists.
* All scoring tests pass.
* Tie-break tests pass.
* Manual correction works.
* Invalid or unresolved results cannot be finalized.
* Finalized data persists correctly.
* Finalized data is not silently overwritten.
* Synchronization is idempotent.
* CSV export passes.
* Google Sheets export passes when implemented.
* Required unit, integration, UI, database, backend, security, device, recovery, and export tests pass.
* No critical or high-severity defect remains open.
* OCR acceptance passes after approved real screenshots become available.

This document does not claim that these criteria currently pass.

## 17. Release Evidence

Each release must record:

* Git commit or release tag
* Test environment
* Android API or device or emulator details
* Physical-device details where used
* Supabase environment used
* Commands executed
* Test results
* Failed-test details
* Screenshots or logs where relevant
* Known limitations
* Deferred tests
* Reviewer approval
* Release-blocking defects, if any

## 18. Defect Severity and Release Blocking

Approved defect categories are:

* Critical: data loss, security failure, incorrect finalized tournament result, unauthorized access, credential exposure, or unrecoverable corruption
* High: core workflow blocked, finalization invalid, export materially incorrect, synchronization corrupting or duplicating data, or OCR or matching failure that cannot be reviewed or corrected
* Medium: important feature partially affected with safe workaround
* Low: cosmetic, copy, or minor usability issue

Critical and high defects block release.

Every defect fix must include a regression test where practical.

## 19. Deferred Testing Decisions

The following testing decisions remain deferred:

* Exact real screenshot acceptance dataset
* Exact screenshot ground-truth file format
* Exact OCR measurement report format
* Exact CI workflow commands after Android project creation
* Exact emulator matrix beyond API 26 and target API
* Exact physical-device matrix
* Exact Supabase local-vs-remote test environment split
* Exact export column verification fixture
* Exact performance thresholds
* Exact low-memory test method
* Exact release-evidence template
* Exact defect-tracking format

These decisions must not be resolved in this document.

## 20. Roadmap Alignment

Roadmap alignment for testing and acceptance is:

* Phase 1 establishes baseline Android testing.
* Phase 2 validates tournament and manual roster workflows.
* Phase 3 validates manual match processing.
* Phase 4 validates scoring and standings.
* Phase 5 validates Room persistence, offline work, and recovery.
* Phase 6 validates Supabase authentication, RLS, synchronization, idempotency, conflict handling, and finalized-data protection.
* Phase 7 validates screenshot intake and storage.
* Phase 8 validates OCR extraction and real screenshot evaluation.
* Phase 9 validates matching, review, correction, and safe finalization.
* Phase 10 validates CSV and Google Sheets export.
* Phase 11 validates complete workflow integration.
* Phase 12 completes quality assurance and security validation.
* Phase 13 validates controlled real-tournament beta testing.
* Phase 14 validates release-candidate and production-release evidence.
