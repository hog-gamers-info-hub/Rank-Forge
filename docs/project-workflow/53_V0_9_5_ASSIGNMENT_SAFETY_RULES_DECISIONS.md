# v0.9.5 - Assignment Safety Rules Decisions

## 1. Title and Status

**Phase:** 9 - Team Matching and Manual Correction
**Version:** v0.9.5 - Assignment Safety Rules
**Status:** Approved documentation decision gate; no implementation is authorized by this document alone.

Phase 8 is complete and closed. Phase 9 v0.9.0 Text Normalization, v0.9.1 Player Similarity Matching, v0.9.2 Team Candidate Scoring, v0.9.3 Top-Three Suggestions, and v0.9.4 Confidence Thresholds are implemented, verified, merged, and protected. This document defines only the next assignment-safety boundary that a later approved v0.9.5 implementation may add.

## 2. Decision Summary

v0.9.5 will add a pure, deterministic, Android-independent domain capability that evaluates whether v0.9.4 confidence assessments for one match are safe for automatic team assignment.

The capability must:

* accept confidence assessments for OCR result rows from one match;
* inspect preserved v0.9.3 suggestion evidence through each v0.9.4 assessment;
* require an automatic confidence tier before any safe automatic assignment result;
* enforce minimum matched-player contribution count;
* enforce candidate lead over the second suggestion when a second suggestion exists;
* enforce unique team assignment across OCR rows for the same match;
* downgrade unsafe automatic candidates to review-required safety outcomes;
* preserve all input confidence and suggestion evidence; and
* return deterministic safety evidence for later OCR review and finalization workflows.

The capability must not perform OCR, parse screenshots, normalize names, score players, score teams, generate top-three suggestions, classify confidence thresholds, edit detected names, edit roster names, render UI, persist assignments, finalize matches, or mutate match result records.

## 3. Repository Context

The approved v0.9.4 classifier returns `TeamMatchConfidenceAssessment` values containing a tier, an optional rank-1 selected suggestion, the original `TopTeamCandidateSuggestions`, and a threshold reason. v0.9.4 validates malformed suggestion structures and remains advisory only.

The approved v0.9.3 suggestion result preserves at most three ranked `TopTeamCandidateSuggestion` values. Each suggestion preserves the v0.9.2 `TeamCandidateScore`, including candidate team slot, confidence score, contributing match count, coverage, average matched-player score, and contributing player-match evidence.

The approved v0.9.2 scorer records `contributingMatchCount` as actual selected contributing player matches and caps coverage contribution at four. The canonical automatic-assignment rules require at least three contributing player matches for safe automatic handling.

Current fixed team-slot conventions use `TeamSlot.SLOT_NUMBERS` for team slots `1..12` and `require(...)` / `IllegalArgumentException` for structurally invalid slot values. No repository convention currently defines a separate match-wide OCR result-row index range, so v0.9.5 uses zero-based row indexes `0..11` for row-ordering evidence.

Phase 8 parsing and failure-handling outputs keep raw OCR, parsed values, typed failures, and review markers distinct. They do not normalize, match, assign, persist, score, or finalize. v0.9.5 consumes only already-computed confidence assessments and does not call Phase 8 parsers or failure analyzers.

## 4. Scope

The v0.9.5 scope is match-wide assignment-safety evidence for one match's OCR row confidence assessments.

The future implementation may add:

* `TeamAssignmentSafetyEvaluator`;
* `RowTeamMatchConfidenceAssessment`;
* `TeamAssignmentSafetyResult`;
* `RowTeamAssignmentSafetyResult`;
* `TeamAssignmentSafetyStatus`;
* `TeamAssignmentSafetyReason`; and
* focused synthetic unit tests.

The future implementation must not add OCR extraction, parsing, normalization, player similarity matching, team candidate scoring, top-three suggestion generation, confidence-tier classification, review UI, manual correction UI, persistence, migrations, scoring changes, navigation, actual assignment writes, or finalization behavior.

## 5. Terminology and Data Boundaries

The following values remain distinct:

1. raw OCR text and hierarchy;
2. parsed OCR row and player-name candidates;
3. normalized comparison values;
4. pairwise player similarity assessments;
5. one candidate team's scored evidence;
6. top-three advisory suggestions;
7. tier-classified confidence assessment;
8. assignment-safety assessment;
9. later assigned team value;
10. operator correction;
11. confirmed result value; and
12. finalized match result.

v0.9.5 produces safety evidence only. It must not write its output back to raw OCR, parsed OCR, normalized, pairwise-compared, candidate-scored, suggested, tier-classified, assigned, corrected, confirmed, or finalized state.

## 6. Match-Wide Boundary

Unlike v0.9.0 through v0.9.4, v0.9.5 operates on a collection of row confidence assessments because unique team assignment requires cross-row comparison.

`rowAssessments` represents OCR row confidence assessments for one match only. The evaluator supports up to 12 OCR rows.

The evaluator must not:

* process multiple matches;
* process tournament standings;
* finalize match results;
* write draft or finalized match data;
* apply placement or kill scoring; or
* change correction workflows.

The evaluator may return fewer than 12 row results when fewer than 12 row assessments are supplied. Row-count completeness remains an OCR review/finalization concern for later versions.

## 7. Proposed API and Result Contract

The intended production package is:

```kotlin
package com.hoggamers.rankforge.domain.matching
```

The proposed evaluator is:

```kotlin
object TeamAssignmentSafetyEvaluator {
    fun evaluate(
        rowAssessments: List<RowTeamMatchConfidenceAssessment>,
    ): TeamAssignmentSafetyResult
}
```

The row input wrapper is:

```kotlin
data class RowTeamMatchConfidenceAssessment(
    val rowIndex: Int,
    val confidenceAssessment: TeamMatchConfidenceAssessment,
)
```

The match-wide result is:

```kotlin
data class TeamAssignmentSafetyResult(
    val rowCount: Int,
    val safeAssignmentCount: Int,
    val rowResults: List<RowTeamAssignmentSafetyResult>,
)
```

Each row result is:

```kotlin
data class RowTeamAssignmentSafetyResult(
    val rowIndex: Int,
    val confidenceAssessment: TeamMatchConfidenceAssessment,
    val safetyStatus: TeamAssignmentSafetyStatus,
    val proposedTeamSlot: Int?,
    val reasons: Set<TeamAssignmentSafetyReason>,
)
```

The safety status enum is:

```kotlin
enum class TeamAssignmentSafetyStatus {
    SAFE_AUTOMATIC_ASSIGNMENT,
    REVIEW_REQUIRED,
    MANUAL_REQUIRED,
}
```

The safety reason enum is:

```kotlin
enum class TeamAssignmentSafetyReason {
    NO_SUGGESTION,
    NOT_AUTOMATIC_TIER,
    INSUFFICIENT_PLAYER_MATCH_COUNT,
    INSUFFICIENT_CANDIDATE_LEAD,
    DUPLICATE_TEAM_CANDIDATE,
    MALFORMED_CONFIDENCE_ASSESSMENT,
}
```

These types intentionally exclude UI state, Room entities, Supabase DTOs, persistence IDs, mutable state, finalization state, correction state, tournament scoring fields, placement fields, kill fields, and committed assignment fields.

## 8. Input Rules

Input behavior must be deterministic:

* `rowAssessments` represents one match only.
* each row input has a stable `rowIndex`;
* `rowIndex` must be unique within the input;
* `rowIndex` must be in `0..11`;
* empty input is valid and returns `rowCount = 0`, `safeAssignmentCount = 0`, and no row results;
* more than 12 rows is structurally invalid and throws `IllegalArgumentException`;
* duplicate row indexes throw `IllegalArgumentException`;
* out-of-range row indexes throw `IllegalArgumentException`;
* the evaluator must not invoke v0.9.0, v0.9.1, v0.9.2, v0.9.3, v0.9.4, OCR, parser, scorer, suggestion-provider, or classifier APIs;
* the evaluator must use only already-computed confidence and suggestion evidence supplied in each assessment; and
* the evaluator must not mutate supplied assessments or nested evidence.

`rowCount` is the supplied row-assessment count after structural validation. `rowResults` must be sorted by ascending `rowIndex` regardless of input list order.

## 9. Automatic Eligibility Rule

A row can be considered for `SAFE_AUTOMATIC_ASSIGNMENT` only when its confidence tier is:

```text
AUTOMATIC_CANDIDATE
```

Rows with:

```text
CONFIRMATION_REQUIRED
MANUAL_REQUIRED
```

must not be auto-assigned by v0.9.5.

Status mapping:

* `CONFIRMATION_REQUIRED` -> `REVIEW_REQUIRED` with `NOT_AUTOMATIC_TIER`;
* `MANUAL_REQUIRED` with a selected suggestion -> `MANUAL_REQUIRED` with `NOT_AUTOMATIC_TIER`;
* `MANUAL_REQUIRED` without a selected suggestion -> `MANUAL_REQUIRED` with `NO_SUGGESTION`.

## 10. Minimum Player-Match Count Rule

The minimum contributing player-match count required for safe automatic assignment is:

```text
minimumSafeContributingMatchCount = 3
```

A rank-1 suggestion must have:

```text
teamCandidateScore.contributingMatchCount >= 3
```

Otherwise the row is not safe for automatic assignment and records `INSUFFICIENT_PLAYER_MATCH_COUNT`.

This rule is independent from confidence tier. A high confidence score without enough contributing player evidence is not safe.

## 11. Candidate Lead Rule

Candidate lead is:

```text
candidateLead =
    rank1.confidenceScore - rank2.confidenceScore
```

where `rank2` is the second suggestion when available.

The minimum lead is:

```text
minimumSafeCandidateLead = 10
```

Rules:

* if rank 1 and rank 2 both exist, candidate lead must be `>= 10`;
* if only one suggestion exists and all other safety rules pass, candidate lead is considered satisfied because there is no competing suggestion evidence;
* if rank 2 exists with a higher confidence than rank 1, the supplied confidence assessment is structurally inconsistent with v0.9.4/v0.9.3 evidence and must not be repaired or re-sorted;
* structural malformed confidence input throws `IllegalArgumentException`;
* `MALFORMED_CONFIDENCE_ASSESSMENT` is reserved for a future non-throwing diagnostics mode; and
* lead must not be calculated from average score, coverage, team slot, rank number alone, placement, kills, standings, or OCR confidence.

When the lead is below 10, the row is not safe for automatic assignment and records `INSUFFICIENT_CANDIDATE_LEAD`.

## 12. Unique Team Assignment Rule

For all rows proposed as automatic candidates, the same candidate team slot must not be safely auto-assigned to more than one row.

Rules:

* first evaluate each row's automatic tier, selected suggestion, minimum player-match count, and candidate lead;
* collect the rank-1 candidate team slot only for rows that otherwise pass those checks;
* if a team slot appears in more than one otherwise-safe row, all rows sharing that duplicate team slot become `REVIEW_REQUIRED`;
* each duplicated row records `DUPLICATE_TEAM_CANDIDATE`;
* duplicate conflicts do not affect rows that were not otherwise safe automatic candidates;
* do not choose one winner among duplicate rows; and
* do not use placement, kills, row order, confidence score, candidate lead, roster slot, team slot priority, standings, or OCR confidence to break duplicate-team conflicts in v0.9.5.

## 13. Safety Status Rules

### SAFE_AUTOMATIC_ASSIGNMENT

Return `SAFE_AUTOMATIC_ASSIGNMENT` only when all are true:

* tier is `AUTOMATIC_CANDIDATE`;
* selected suggestion exists;
* contributing player-match count is at least `3`;
* candidate lead rule is satisfied;
* candidate team slot is unique among otherwise-safe automatic candidates; and
* no malformed safety input is detected.

Safe rows use an empty `reasons` set.

### REVIEW_REQUIRED

Return `REVIEW_REQUIRED` when:

* tier is `CONFIRMATION_REQUIRED`;
* tier is `AUTOMATIC_CANDIDATE` but one or more safety rules fail;
* a duplicate team candidate conflict exists; or
* a future non-throwing diagnostics mode reports malformed confidence evidence.

Review-required reasons must include every applicable safety reason from the approved reason enum.

### MANUAL_REQUIRED

Return `MANUAL_REQUIRED` when:

* tier is `MANUAL_REQUIRED`; or
* no selected suggestion exists.

Manual-required rows with no selected suggestion record `NO_SUGGESTION`. Manual-required rows with a selected suggestion record `NOT_AUTOMATIC_TIER`.

Structural malformed input throws `IllegalArgumentException`; it does not produce a row result in v0.9.5. `MALFORMED_CONFIDENCE_ASSESSMENT` is reserved only if a later caller needs non-throwing batch diagnostics.

## 14. Proposed Team Slot and Evidence Rules

`proposedTeamSlot` is the rank-1 candidate team slot when a selected suggestion exists.

Rules:

* `proposedTeamSlot` does not mean committed assignment;
* `proposedTeamSlot` is nullable when no selected suggestion exists;
* `proposedTeamSlot` may be present for review-required or manual-required rows when a selected suggestion exists;
* even `SAFE_AUTOMATIC_ASSIGNMENT` only means a later workflow may apply the assignment after its own approved persistence/finalization checks;
* each row result must preserve the original `rowIndex`;
* each row result must preserve the original `TeamMatchConfidenceAssessment`;
* selected suggestion evidence remains available through that preserved assessment;
* safety reasons must be deterministic; and
* the evaluator must not remove, filter, sort, or mutate nested confidence, suggestion, candidate-score, or player-match evidence.

## 15. Malformed Input Rules

The following are structural invalid cases:

* more than 12 rows;
* duplicate row indexes;
* row index outside `0..11`;
* confidence assessment with malformed suggestions if directly detectable from supplied assessment data;
* automatic or confirmation-required tier with no selected rank-1 suggestion;
* selected suggestion whose rank is not `1`;
* selected suggestion not present in the assessment's preserved suggestion list;
* selected suggestion conflicts with the assessment's preserved rank-1 suggestion; and
* rank-2 confidence higher than rank-1 confidence when both are directly available.

Decision:

* v0.9.5 validates only v0.9.5 input structure and minimal `TeamMatchConfidenceAssessment` consistency needed for safe evidence reads;
* v0.9.5 trusts v0.9.4 classifier outputs for full nested suggestion validity and must not duplicate the complete v0.9.4 malformed-suggestion validator;
* row count, duplicate index, out-of-range index, and directly detectable assessment-consistency failures throw `IllegalArgumentException`;
* malformed input is not silently repaired, re-sorted, filtered, or converted to safe evidence; and
* `MALFORMED_CONFIDENCE_ASSESSMENT` is reserved for future non-throwing batch diagnostics and is not expected in v0.9.5 throwing behavior.

## 16. Complexity and Implementation Constraints

Implementation must be deterministic and bounded for one match.

Required constraints:

* maximum expected row count is `12`;
* maximum expected suggestions per row is `3`;
* no network access;
* no database access;
* no persistence;
* no logging of OCR names, roster names, normalized names, scores, ranks, row evidence, screenshots, private paths, or raw OCR payloads;
* no mutable global state;
* thread-safe pure evaluation;
* no Android framework dependency;
* no external dependency;
* no recursion; and
* no mutation of caller-supplied lists, assessments, or nested evidence.

The intended complexity is bounded by the small one-match input size. Duplicate-team detection may use deterministic grouping by candidate team slot.

## 17. Synthetic Unit-Test Matrix

Later implementation tests must use only synthetic names and values. They must not include real player names, screenshots, OCR payloads, private paths, or personal data.

| Case | Synthetic input shape | Expected safety status and evidence |
| --- | --- | --- |
| Empty row list | `[]` | `rowCount = 0`, `safeAssignmentCount = 0`, `rowResults = []` |
| One automatic row, 3 matches, no second suggestion, unique team | row `0`, rank 1 slot `1`, confidence `90`, contributing count `3`, no rank 2 | `SAFE_AUTOMATIC_ASSIGNMENT`, empty reasons, `proposedTeamSlot = 1` |
| One automatic row, 4 matches, lead exactly 10 | row `0`, rank 1 slot `1` confidence `90`, rank 2 slot `2` confidence `80`, contributing count `4` | `SAFE_AUTOMATIC_ASSIGNMENT`, empty reasons |
| Automatic row with lead 9 | row `0`, rank 1 confidence `90`, rank 2 confidence `81`, contributing count `4` | `REVIEW_REQUIRED`, reason `INSUFFICIENT_CANDIDATE_LEAD` |
| Automatic row with only 2 contributing matches | row `0`, rank 1 confidence `90`, contributing count `2`, lead satisfied | `REVIEW_REQUIRED`, reason `INSUFFICIENT_PLAYER_MATCH_COUNT` |
| Automatic row with low match count and low lead | row `0`, rank 1 confidence `90`, rank 2 confidence `85`, contributing count `2` | `REVIEW_REQUIRED`, reasons `INSUFFICIENT_PLAYER_MATCH_COUNT` and `INSUFFICIENT_CANDIDATE_LEAD` |
| Confirmation-required row | row `0`, tier `CONFIRMATION_REQUIRED`, selected suggestion slot `1` | `REVIEW_REQUIRED`, reason `NOT_AUTOMATIC_TIER`, `proposedTeamSlot = 1` |
| Manual-required row with selected suggestion | row `0`, tier `MANUAL_REQUIRED`, selected suggestion slot `1` | `MANUAL_REQUIRED`, reason `NOT_AUTOMATIC_TIER`, `proposedTeamSlot = 1` |
| No-suggestion row | row `0`, tier `MANUAL_REQUIRED`, `selectedSuggestion = null` | `MANUAL_REQUIRED`, reason `NO_SUGGESTION`, `proposedTeamSlot = null` |
| Duplicate otherwise-safe team slot across two rows | rows `0` and `1`, both otherwise safe with rank 1 slot `1` | both `REVIEW_REQUIRED`, both reason `DUPLICATE_TEAM_CANDIDATE`; no winner chosen |
| Duplicate team slot does not affect already manual row | row `0` otherwise safe slot `1`, row `1` manual with selected slot `1` | row `0` remains safe if unique among otherwise-safe rows; row `1` remains manual |
| Duplicate team slot does not choose a winner | two or more otherwise-safe rows share slot `1` with different confidence/lead/row order | every duplicated otherwise-safe row becomes `REVIEW_REQUIRED`; no row remains safe for that slot |
| Row results sorted by row index | input rows `[2, 0, 1]` | returned row indexes `[0, 1, 2]` |
| Safe assignment count counts only safe rows | one safe, one review-required, one manual-required | `safeAssignmentCount = 1` |
| `proposedTeamSlot` present for selected suggestion rows | selected rank-1 slot `3` | `proposedTeamSlot = 3` for safe, review, or manual rows with selected suggestion |
| `proposedTeamSlot` null for no-suggestion row | no selected suggestion | `proposedTeamSlot = null` |
| Duplicate row indexes | two inputs with row index `0` | throws `IllegalArgumentException` |
| More than 12 rows | 13 row inputs | throws `IllegalArgumentException` |
| Out-of-range row index | row index `-1` or `12` | throws `IllegalArgumentException` |
| Automatic tier missing selected suggestion | automatic confidence assessment with `selectedSuggestion = null` | throws `IllegalArgumentException` |
| Selected suggestion conflicts with preserved rank 1 | selected suggestion not equal to preserved rank-1 suggestion | throws `IllegalArgumentException` |
| Rank 2 higher than rank 1 | selected rank 1 confidence lower than rank 2 confidence in preserved suggestions | throws `IllegalArgumentException` |
| Evidence objects are preserved | reusable synthetic assessment object | row result contains the same assessment object/value and nested evidence remains available |
| Deterministic repeated calls | same row assessments repeated | identical result every call |
| No UI, persistence, finalization, scoring, correction, or actual assignment fields exist | inspect public v0.9.5 result types | no out-of-scope fields |

Tests must also assert that safe rows have empty reasons, unsafe automatic rows keep all applicable safety reasons, duplicate-team conflicts apply only after automatic/player-count/lead checks, and supplied confidence/suggestion evidence is not mutated.

## 18. Compatibility Requirements

v0.9.5 must not modify or break:

* `PlayerNameComparisonNormalizer`;
* `PlayerNameSimilarityMatcher`;
* `TeamCandidateScorer`;
* `TopTeamCandidateSuggestionProvider`;
* `TeamMatchConfidenceTierClassifier`;
* existing `RosterNameNormalizer`;
* roster creation, editing, and validation;
* tournament management;
* fixed 12-team-slot behavior;
* manual match processing;
* Phase 8 OCR extraction and parsing;
* screenshot preservation;
* placement and kill validation;
* scoring and standings;
* Room persistence;
* authentication;
* cloud synchronization;
* conflict resolution;
* finalized-data protection; or
* correction workflows.

## 19. Security and Privacy

Assignment-safety evaluation is local pure computation.

It must not log raw OCR names, roster names, normalized names, pairwise scores, candidate scores, suggestion ranks, safety results, screenshots, private paths, raw OCR payloads, credentials, tokens, or backend details.

The future implementation must not upload safety evidence, create persistence records, add analytics, add crash-report metadata containing names, or change screenshot handling. Tests and documentation must use synthetic values only.

## 20. Out of Scope

v0.9.5 excludes:

* running OCR;
* parsing OCR rows;
* player similarity scoring;
* team candidate scoring;
* top-three suggestion generation;
* confidence-tier classification;
* confirmation UI;
* OCR review UI;
* manual correction UI;
* persistence or migrations;
* cloud synchronization changes;
* raw/corrected evidence persistence;
* actual assignment writes;
* match finalization changes;
* roster OCR review and correction;
* team-name OCR extraction;
* fifth/sixth player OCR extraction;
* placement/kill scoring changes;
* navigation;
* public sharing; and
* exports.

The non-numbered roadmap item `v0.9.x - Roster OCR Review and Correction` remains separate future scope.

## 21. Acceptance Criteria

A later v0.9.5 implementation is acceptable only when:

* it evaluates assignment safety for one match's row assessments;
* it supports empty input and up to 12 rows;
* it requires automatic tier for safe automatic assignment;
* it enforces minimum three contributing player matches;
* it enforces 10-point candidate lead when a second suggestion exists;
* it treats one-suggestion automatic candidates as lead-satisfied;
* it prevents duplicate team auto-assignment across rows by downgrading all otherwise-safe duplicates to review required;
* it preserves confidence and suggestion evidence;
* it returns deterministic row results sorted by row index;
* it reports `safeAssignmentCount` from only safe rows;
* it does not assign, persist, display UI, score tournament points, correct fields, or finalize matches;
* comprehensive synthetic unit tests pass; and
* existing behavior remains unchanged.

## 22. Deferred Decisions

The following remain deferred to later approved versions:

* OCR review UI presentation of safety outcomes;
* manual correction workflows;
* actual team assignment writes;
* persistence of OCR review state, raw/corrected data, candidate evidence, tier evidence, safety evidence, or confirmed assignments;
* safe OCR-assisted finalization;
* final validation against placement, kill, and complete 12-row result data;
* duplicate-placement resolution;
* post-review confirmation state;
* roster OCR review and correction;
* team-name OCR extraction;
* fifth/sixth-player OCR extraction; and
* Phase 12 real OCR acceptance evaluation.

`MALFORMED_CONFIDENCE_ASSESSMENT` remains reserved for a future non-throwing diagnostics mode and does not require v0.9.5 to return malformed row results.

## 23. Implementation Handoff

After this decision document is reviewed, merged, and followed by explicit user approval, the implementation task may add only the proposed pure `TeamAssignmentSafetyEvaluator`, `RowTeamMatchConfidenceAssessment`, `TeamAssignmentSafetyResult`, `RowTeamAssignmentSafetyResult`, `TeamAssignmentSafetyStatus`, `TeamAssignmentSafetyReason`, minimal row-level structural validation, safety-rule evaluation, duplicate-team safety checks, and focused synthetic unit tests.

The implementation must not modify `PlayerNameComparisonNormalizer`, `PlayerNameSimilarityMatcher`, `TeamCandidateScorer`, `TopTeamCandidateSuggestionProvider`, `TeamMatchConfidenceTierClassifier`, `RosterNameNormalizer`, existing roster validation, Phase 8 OCR processing, persistence, UI, assignments, corrections, scoring, navigation, or finalization.
