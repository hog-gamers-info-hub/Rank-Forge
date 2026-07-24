# Rank-Forge — Scoring and Processing Rules

## 1. Document Purpose

This document defines the authoritative MVP rules for manual and OCR-assisted match-result processing, validation, deterministic scoring, standings aggregation, tie-breaking, correction, finalization, duplicate prevention, and scoring verification.

It is a documentation authority only. It does not create scoring code, Android screens, tests, database changes, migrations, or configuration.

This document follows the approved authority hierarchy. OCR extraction and matching, database persistence, testing strategy, workflow governance, and roadmap sequencing remain governed by their respective canonical authorities.

## 2. Scope and Boundaries

This document covers match-result processing, scoring, standings, tie-breaks, correction, and finalization.

Approved MVP boundaries:

* A tournament contains exactly 12 team slots.
* A tournament supports a maximum of 10 matches.
* Every finalized match contains exactly 12 unique team-result rows.
* Manual match entry and OCR-assisted entry use the same validation and scoring rules.
* OCR extraction and team matching are governed by `docs/04_OCR_AND_TEAM_MATCHING.md`.
* Database persistence is governed by `docs/03_DATABASE_DESIGN.md`.
* Draft or unresolved results are not official standings inputs.
* Scoring is deterministic and independent from OCR confidence.

This document does not add penalties, bonuses, alternate scoring modes, additional tie-breakers, or alternate tournament formats.

## 3. Processing Principles

The approved processing principles are:

* Confirmed result values are the scoring inputs.
* Identical valid inputs must always produce identical outputs.
* Scoring must remain independent from UI, OCR, storage, and networking.
* Placement points and kill points are calculated separately.
* Derived totals must remain reproducible from canonical inputs.
* Derived values must not become independently editable sources of truth.
* Validation occurs before finalization.
* Errors and uncertain values must remain explicit.
* Finalized data must not be silently overwritten.
* Repeated processing must not count the same result more than once.

## 4. Match Identity and Limits

Each match supports the approved high-level identity information:

* Parent tournament
* Match number
* Match date
* Map
* Draft or finalized status

Rules:

* A tournament supports zero to 10 matches.
* Match numbers must be unique within a tournament.
* A result must remain associated with one tournament and one match.
* Match identity conflicts must be resolved before finalization.
* The precise physical database representation remains governed by the database document.

This document does not define map lists, round formats, game modes, stage formats, or scheduling rules.

## 5. Result Input Sources

There are two approved result-input paths:

1. Manual entry
2. OCR-assisted scoreboard processing

Manual entry:

* The operator assigns teams, placements, and kill values.
* Manual data remains subject to complete validation.

OCR-assisted processing:

* Confirmed values may originate from supported genuine Free Fire MAX scoreboard OCR.
* OCR values must be reviewed and corrected where required.
* OCR confidence and matching confidence do not change scoring points.
* Raw OCR evidence remains separate from confirmed scoring inputs.
* OCR output cannot finalize or score an official match automatically.

Both paths must produce the same confirmed match-result structure before finalization.

## 6. Match Result Structure

Each result row must represent:

* One tournament team
* One placement
* One non-negative kill value
* Review or confirmation state
* Source classification where required
* Current confirmed values
* Previous or raw values where applicable

A finalized match requires:

* Exactly 12 result rows
* Exactly 12 unique tournament teams
* Exactly one result for each team
* Exactly 12 unique placements covering positions 1 through 12
* Valid kill values
* No unresolved OCR, matching, correction, or duplicate conflict

This document does not define Kotlin classes, database columns, APIs, or screen fields.

## 7. Result Validation Rules

Finalization must be blocked when:

* Fewer than 12 result rows exist.
* More than 12 result rows exist.
* A tournament team is missing.
* A tournament team appears more than once.
* A placement is missing.
* A placement appears more than once.
* A placement is outside 1 through 12.
* A kill value is missing.
* A kill value is negative.
* A kill value is non-numeric.
* An OCR-assisted value remains uncertain or unconfirmed.
* A team assignment remains unmatched.
* A duplicate screenshot conflict remains unresolved.
* Required correction data was not persisted.
* Any required validation error remains unresolved.

Validation rules apply equally to manual and OCR-assisted processing.

This document does not define an unapproved maximum kill value.

## 8. Placement Points

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

Rules:

* Only integer placements 1 through 12 are valid.
* Position points are derived only from the confirmed placement.
* Position points are not manually editable.
* OCR confidence does not modify position points.
* No placement bonus or penalty exists in the approved MVP.

## 9. Kill Points

Approved kill-point rules:

* Each confirmed kill equals one point.
* `kill points = confirmed kill value`
* Kill values must be non-negative whole numbers.
* Kill points are deterministic.
* Kill points are not modified by placement or OCR confidence.
* No kill multiplier, cap, bonus, or penalty is approved.

This document does not define separate player-kill versus team-kill scoring behavior beyond the confirmed match-result kill value.

## 10. Match Total Calculation

The approved match-total formula is:

`match total = position points + kill points`

Requirements:

* Match total uses confirmed placement and kill values.
* Match total must be reproducible.
* Match total is a derived value.
* Match total must not be independently edited.
* Every finalized result row has one calculated match total.
* Reprocessing the same confirmed values must produce the same total.

Examples:

* Placement 1 with 0 kills: `12 + 0 = 12`
* Placement 2 with 5 kills: `9 + 5 = 14`
* Placement 12 with 8 kills: `0 + 8 = 8`

These examples illustrate the approved formula only and do not add further requirements.

## 11. Match Review and Finalization

The approved processing sequence is:

1. Create or select a match draft.
2. Enter manual results or process a supported scoreboard screenshot.
3. Resolve team assignments.
4. Review all 12 result rows.
5. Correct placements, teams, player names, and kill values where required.
6. Validate team uniqueness, placement uniqueness, row count, and values.
7. Calculate position points, kill points, and match totals.
8. Confirm all uncertain results.
9. Persist confirmed values and required history.
10. Finalize the match.
11. Recalculate tournament standings.
12. Make finalized data eligible for export.

Rules:

* Draft and finalized states remain distinct.
* Scoring calculations may be derived during review, but only finalized results contribute to official standings.
* Finalization is a controlled state transition.
* Finalization requires complete valid data.
* OCR output cannot directly trigger finalization.
* Finalized matches require stronger overwrite protection than drafts.

## 12. Tournament Standings

Official tournament standings aggregate finalized matches only.

A tournament supports aggregation across up to 10 matches.

For every team, standings must derive:

* Total position points
* Total kill points
* Total points
* Number of first-place finishes
* Latest-match placement required for tie-breaking
* Matches included in the calculation

Definitions:

`total position points = sum of finalized match position points`

`total kill points = sum of finalized match kill points`

`total points = total position points + total kill points`

Requirements:

* Draft matches are excluded.
* Invalidated or unresolved data is excluded from official standings.
* Every finalized match is counted once.
* Cached or persisted standings must remain reproducible from finalized match results.
* Recalculation must not create duplicate totals.
* Team standings must use the same stable tournament-team identity across matches.

This document does not add averages, win rates, survival points, bonus points, penalty points, or unrelated statistics.

## 13. Tie-Break Rules

The approved tie-break order is:

1. Total points
2. Number of first-place finishes
3. Total kills
4. Placement in the latest match

Rules:

* Higher total points ranks first.
* If total points are equal, more first-place finishes ranks first.
* If still equal, more total kills ranks first.
* If still equal, the better placement in the latest match ranks first.
* Tie-break criteria must be evaluated sequentially.
* Later criteria must not be used when an earlier criterion already resolves the tie.
* Ordering must be deterministic and reproducible.

This document does not add head-to-head results, best single-match score, average placement, earlier-match placement, manual organizer selection, or alphabetical ordering as an approved competitive tie-break.

If all approved tie-break fields remain equal, the result remains an unresolved complete tie that requires an explicit later product decision.

## 14. Corrections and Recalculation

Approved correction and recalculation rules:

* Draft results may be corrected before finalization.
* Corrections must preserve required previous and original information.
* OCR corrections must not overwrite raw OCR evidence.
* Finalized-result corrections require explicit authorization.
* Finalized-result corrections must be auditable.
* A corrected finalized result requires affected scoring and standings to be recalculated.
* Recalculation must replace the affected derived result rather than count both revisions.
* Exported data must use the current authorized finalized result.
* Stale local data must not restore an older result silently.

The exact role or approval workflow for post-finalization correction remains deferred.

## 15. Duplicate and Idempotency Rules

Required duplicate and idempotency rules:

* A team appears only once per match.
* A placement appears only once per match.
* A match number is unique within a tournament.
* A finalized result row is counted once.
* Repeated scoring execution must not duplicate totals.
* Repeated synchronization must not duplicate domain results.
* Repeated export attempts must not duplicate destination rows.
* Duplicate screenshot hashes require review.
* A correction revision replaces the current derived scoring contribution without deleting its history.
* Standings regeneration from canonical finalized inputs must produce the same result.

## 16. Error Handling

Explicit handling is required for:

* Invalid team count
* Invalid result-row count
* Missing team
* Duplicate team
* Missing placement
* Duplicate placement
* Placement outside 1 through 12
* Missing kill value
* Negative kill value
* Non-numeric kill value
* Unconfirmed OCR result
* Unmatched team
* Duplicate screenshot
* Failed local save
* Failed synchronization
* Failed finalization
* Failed standings recalculation
* Failed export

Error-handling requirements:

* Errors must remain visible.
* Partial or silent finalization must be avoided.
* Existing valid data must be preserved.
* Correction or retry must be permitted where practical.
* Official standings or export must be prevented when validity is uncertain.

This document does not define exact UI wording or retry timing.

## 17. Export Eligibility

Approved export-eligibility rules:

* Only finalized match results may be exported.
* Draft, invalid, incomplete, or unresolved results cannot be exported.
* Exported match totals must match finalized application totals.
* Tournament exports must use current cumulative finalized standings.
* Export ordering must follow the approved standings and tie-break rules.
* Repeated export attempts must not duplicate rows.
* CSV and Google Sheets are outputs and do not redefine official scoring data.

## 18. Testing and Verification

Required scoring unit tests:

* Every placement from 1 through 12
* Zero kills
* Positive kill totals
* High kill totals without assuming an unapproved maximum
* Position-point calculation
* Kill-point calculation
* Match-total calculation
* Tournament-total calculation
* Multiple finalized matches
* Exclusion of draft matches
* Deterministic repeat calculation

Required validation tests:

* Exactly 12 valid result rows
* Fewer than 12 rows
* More than 12 rows
* Duplicate teams
* Duplicate placements
* Missing teams
* Missing placements
* Invalid placement
* Negative kills
* Non-numeric kills
* Unresolved OCR values
* Unmatched teams
* Incomplete finalization blocking

Required tie-break tests:

* Equal total points
* Equal total points and first-place counts
* Equal total points, first-place counts, and total kills
* Latest-match placement resolution
* Complete equality across all approved tie-break criteria
* Multiple matches for the same tournament

Required correction and integrity tests:

* Draft correction
* Finalized authorized correction
* Preservation of previous values
* Standings recalculation
* Duplicate-count prevention
* Retry and idempotency
* App restart and persistence
* Synchronization without duplicate totals
* Export totals matching finalized data

Acceptance requirements:

* All scoring tests must pass.
* Tie-break tests must pass.
* Invalid results must not finalize.
* Scoring accuracy after operator confirmation must be `100%`.
* No critical or high-severity scoring defect may remain open.

This document does not claim that these acceptance requirements currently pass.

## 19. Deferred Decisions

The following matters remain unresolved:

* Exact authorization workflow for correcting finalized matches
* Physical representation of scoring snapshots
* Whether standings are persisted, cached, or always regenerated
* Exact scoring-rule version metadata
* Handling of a complete tie after all four approved tie-break rules
* Exact UI presentation of provisional calculations
* Exact invalidation or removal workflow for a finalized match

These decisions are intentionally not resolved here.

## 20. Roadmap Alignment

Approved roadmap alignment:

* Phase 3 establishes manual result entry, validation, review, draft/finalized states, and corrections.
* Phase 4 implements placement points, kill points, match totals, cumulative standings, tie-breaks, and scoring verification.
* Phase 5 persists match and standings data locally.
* Phase 6 synchronizes and protects finalized backend data.
* Phase 9 supplies confirmed OCR-assisted values after matching and correction.
* Phase 10 exports finalized scoring data.
* Phase 11 integrates processing, finalization, standings, synchronization, and export.
* Phase 12 completes scoring, database, integration, recovery, and regression testing.
