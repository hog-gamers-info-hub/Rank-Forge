# v0.9.4 - Confidence Thresholds Decisions

## 1. Title and Status

**Phase:** 9 - Team Matching and Manual Correction
**Version:** v0.9.4 - Confidence Thresholds
**Status:** Approved documentation decision gate; no implementation is authorized by this document alone.

Phase 8 is complete and closed. Phase 9 v0.9.0 Text Normalization, v0.9.1 Player Similarity Matching, v0.9.2 Team Candidate Scoring, and v0.9.3 Top-Three Suggestions are implemented, verified, merged, and protected. This document defines only the next narrow confidence-tier classification boundary that a later approved v0.9.4 implementation may add.

## 2. Decision Summary

v0.9.4 will add a pure, deterministic, Android-independent domain capability that classifies the strongest available v0.9.3 suggestion for one OCR result row into one advisory confidence tier:

* automatic candidate;
* confirmation-required candidate; or
* manual matching required.

The capability must:

* accept the v0.9.3 top-three suggestion result for one OCR row;
* inspect the highest-ranked suggestion;
* classify that suggestion by deterministic confidence thresholds;
* preserve the top-three suggestion evidence; and
* return advisory tier evidence for later review and safety-rule evaluation.

It must not assign a team, finalize a match, enforce one team per match, compare candidates across OCR rows, calculate candidate lead, enforce three-of-four matched-player safety, enforce unique team assignment, resolve conflicts, render UI, persist state, modify raw OCR values, modify roster display names, or change scoring.

## 3. Repository Context

The approved v0.9.0 normalizer is `PlayerNameComparisonNormalizer` in `com.hoggamers.rankforge.domain.matching`. It produces comparison-only values and must not alter raw OCR text, parsed OCR text, or roster display names.

The approved v0.9.1 matcher is `PlayerNameSimilarityMatcher` in the same package. It compares one detected player-name value to one roster player-name value and returns immutable `PlayerNameSimilarityAssessment` evidence. It does not apply thresholds, assign teams, persist data, or expose UI state.

The approved v0.9.2 scorer is `TeamCandidateScorer`. It scores one OCR result row against one candidate team, validates the candidate team slot, selects non-duplicated contributing player matches, applies the contribution floor of `75`, and returns immutable `TeamCandidateScore` evidence. It does not apply final confidence tiers, calculate candidate lead, assign teams, persist data, or expose UI state.

The approved v0.9.3 suggestion provider is `TopTeamCandidateSuggestionProvider`. It evaluates supplied candidate teams for one OCR result row, orders them deterministically, returns at most three `TopTeamCandidateSuggestion` values, assigns sequential presentation ranks starting at `1`, and preserves `TeamCandidateScore` evidence. It does not apply confidence thresholds, calculate candidate lead, assign teams, persist data, or expose UI state.

Phase 8 player-name parsing returns one `ParsedPlayerNameRow` per fixed scoreboard row. A row exposes `detectedName`, parse status, optional failure, and `PlayerNameOcrEvidence`. Parsed OCR values and raw OCR evidence remain in-memory processing evidence and are not confirmed match results.

The canonical OCR and matching documents define `90-100` as automatic-assignment tier evidence, `75-89` as suggestion requiring operator confirmation, and below `75` as manual or unmatched. They also require additional automatic-assignment conditions later, including candidate lead, player-match safety, and unique-team safety. v0.9.4 records the threshold classification only; it does not authorize assignment.

## 4. Scope

The v0.9.4 scope is one-row confidence-tier classification for a v0.9.3 suggestion result.

The future implementation may add:

* `TeamMatchConfidenceTierClassifier`;
* `TeamMatchConfidenceTier`;
* `TeamMatchConfidenceAssessment`;
* `TeamMatchConfidenceReason`;
* structural validation for supplied top-three suggestion results; and
* focused synthetic unit tests.

The future implementation must not add candidate lead calculation, assignment-safety checks, automatic assignment, confirmation UI, manual correction UI, match-wide duplicate-team handling, persistence, migrations, scoring changes, navigation, or finalization behavior.

## 5. Terminology and Data Boundaries

The following values remain distinct:

1. raw OCR text and hierarchy;
2. parsed OCR player-name candidate;
3. normalized detected comparison value;
4. normalized roster comparison value;
5. pairwise player similarity assessment;
6. one candidate team's scored evidence;
7. top-three advisory suggestion;
8. tier-classified suggestion evidence;
9. later safety-checked suggestion evidence;
10. later assigned team value;
11. operator correction;
12. confirmed result value; and
13. finalized match result.

v0.9.4 consumes a `TopTeamCandidateSuggestions` value for one row and returns advisory confidence-tier evidence only. It must not write its output back to raw OCR, parsed OCR, normalized, pairwise-compared, candidate-scored, suggested, safety-checked, assigned, corrected, confirmed, or finalized state.

## 6. One OCR Row Boundary

v0.9.4 operates on exactly one OCR result row at a time.

It may classify the strongest suggestion from a v0.9.3 result for that row, but it must not:

* process all 12 OCR rows together;
* enforce match-wide unique team assignment;
* detect duplicate team suggestions across rows;
* resolve candidate conflicts across rows; or
* finalize match results.

Those responsibilities remain deferred to v0.9.5, v0.9.6, v0.9.7, and v0.9.8.

## 7. Proposed API and Result Contract

The intended production package is:

```kotlin
package com.hoggamers.rankforge.domain.matching
```

The proposed classifier is:

```kotlin
object TeamMatchConfidenceTierClassifier {
    fun classify(
        suggestions: TopTeamCandidateSuggestions,
    ): TeamMatchConfidenceAssessment
}
```

The proposed tier enum is:

```kotlin
enum class TeamMatchConfidenceTier {
    AUTOMATIC_CANDIDATE,
    CONFIRMATION_REQUIRED,
    MANUAL_REQUIRED,
}
```

The proposed immutable result is:

```kotlin
data class TeamMatchConfidenceAssessment(
    val tier: TeamMatchConfidenceTier,
    val selectedSuggestion: TopTeamCandidateSuggestion?,
    val suggestions: TopTeamCandidateSuggestions,
    val reason: TeamMatchConfidenceReason,
)
```

The proposed reason enum is:

```kotlin
enum class TeamMatchConfidenceReason {
    NO_SUGGESTIONS,
    BELOW_CONFIRMATION_THRESHOLD,
    MEETS_CONFIRMATION_THRESHOLD,
    MEETS_AUTOMATIC_THRESHOLD,
}
```

These types intentionally exclude assignment state, candidate lead, conflict state, UI state, Room entities, Supabase DTOs, mutable state, match row IDs, finalized-result fields, scoring fields, persistence references, corrected-value fields, and safe-to-assign fields.

## 8. Input Rules

The input must be a `TopTeamCandidateSuggestions` object for one OCR result row.

Rules:

* callers must not be required to pre-sort suggestions;
* callers must not be required to recalculate scores;
* the classifier relies on the `rank == 1` suggestion from the supplied v0.9.3 result;
* if no suggestions exist, return `MANUAL_REQUIRED`;
* the classifier must not evaluate candidate teams itself;
* the classifier must not call `TeamCandidateScorer`;
* the classifier must not call `TopTeamCandidateSuggestionProvider`;
* the classifier must not normalize or compare player names;
* the classifier must not mutate the supplied suggestions or nested evidence; and
* malformed suggestion structures must throw `IllegalArgumentException` rather than being repaired.

## 9. Threshold Values

The approved thresholds are:

```text
automaticCandidateThreshold = 90
confirmationRequiredThreshold = 75
```

Interpretation:

* `confidenceScore >= 90` produces `AUTOMATIC_CANDIDATE`;
* `confidenceScore >= 75 && confidenceScore < 90` produces `CONFIRMATION_REQUIRED`;
* `confidenceScore < 75` produces `MANUAL_REQUIRED`; and
* no suggestions produces `MANUAL_REQUIRED`.

The lower bounds are inclusive. The implementation must use integer comparison only. It must not use floating-point arithmetic, rounding, dynamic thresholds, OCR confidence, candidate lead, player-count safety checks, UI state, or any later-version assignment criteria.

## 10. Tier Semantics

### AUTOMATIC_CANDIDATE

`AUTOMATIC_CANDIDATE` means the strongest suggestion has enough confidence to be considered for automatic handling later.

It does not assign a team by itself. It still requires v0.9.5 safety rules before any automatic assignment can happen.

### CONFIRMATION_REQUIRED

`CONFIRMATION_REQUIRED` means the strongest suggestion is plausible but must be explicitly reviewed or confirmed later.

It does not assign a team. It does not bypass manual correction or review workflows.

### MANUAL_REQUIRED

`MANUAL_REQUIRED` means no suggestion exists, or the strongest suggestion is below the confirmation threshold.

It requires manual matching or review later. It must preserve all suggestion evidence that exists.

## 11. Selected Suggestion and Malformed Input Rules

`selectedSuggestion` is the first and only suggestion with `rank == 1`.

Rules:

* no suggestions is valid and returns `MANUAL_REQUIRED` with `selectedSuggestion = null`;
* suggestions containing exactly one rank-1 value use that value as `selectedSuggestion`;
* suggestions with no rank-1 value are malformed and throw `IllegalArgumentException`;
* suggestions with more than one rank-1 value are malformed and throw `IllegalArgumentException`;
* duplicate rank values are malformed and throw `IllegalArgumentException`;
* ranks must be sequential starting at `1`;
* suggestion count must be at most `3`;
* `evaluatedCandidateCount` must be greater than or equal to `suggestions.size`; and
* malformed suggestion input must not be silently normalized, re-sorted, repaired, or derived from list order.

Rationale:

* v0.9.3 guarantees at most three sequential ranks starting at `1`;
* malformed rank data indicates a structural domain error; and
* silently deriving from list order could hide corruption.

Lower-ranked higher-confidence suggestions are also malformed for v0.9.4's input contract if the supplied ranks are not consistent with v0.9.3 ordering. A later implementation should reject such evidence through the same structural validation rather than re-sorting or reclassifying it. If a lightweight implementation cannot independently prove score-order consistency without duplicating v0.9.3 sorting logic, tests must at minimum verify that malformed rank structures are rejected and that the classifier never re-sorts as part of classification.

## 12. Candidate Lead and Safety Deferrals

The following remain explicitly deferred to v0.9.5 Assignment Safety Rules and later review/finalization versions:

* candidate lead calculation;
* second-candidate score comparison;
* 10-point lead requirement;
* three-of-four matched-player requirement;
* one-team-per-match uniqueness;
* duplicate team assignment safety;
* cross-row conflict detection;
* required-detected-value safety;
* automatic assignment authorization; and
* final assignment decisions.

v0.9.4 must not include `candidateLead`, `secondCandidateScore`, `safeToAssign`, `assignmentAllowed`, `isMatch`, conflict results, or equivalent fields.

## 13. Score Interpretation

v0.9.4 tiers are advisory classification evidence only.

Do not add:

* final selected team;
* team assignment result;
* conflict result;
* match finalization state;
* correction state;
* persistence state;
* UI display state;
* scoring state; or
* tournament standings state.

An `AUTOMATIC_CANDIDATE` assessment does not authorize assignment without later v0.9.5 safety rules.

## 14. Complexity and Implementation Constraints

Implementation must be deterministic and bounded for one row and at most three suggestions.

Required constraints:

* no Android framework dependency;
* no network access;
* no database access;
* no persistence;
* no logging of OCR or roster names;
* no mutable global state;
* thread-safe pure classification;
* no external dependency;
* no recursion;
* no candidate-team scoring;
* no top-three suggestion generation; and
* no third-party dependency.

## 15. Synthetic Unit-Test Matrix

Later implementation tests must use only synthetic names and values. They must not include real player names, screenshots, OCR payloads, private paths, or personal data.

| Case | Supplied suggestions | Expected tier | Expected reason |
| --- | --- | --- | --- |
| No suggestions | `TopTeamCandidateSuggestions(..., suggestions = [])` | `MANUAL_REQUIRED` | `NO_SUGGESTIONS` |
| Confidence `0` | rank `1`, confidence `0` | `MANUAL_REQUIRED` | `BELOW_CONFIRMATION_THRESHOLD` |
| Confidence `74` | rank `1`, confidence `74` | `MANUAL_REQUIRED` | `BELOW_CONFIRMATION_THRESHOLD` |
| Confidence `75` | rank `1`, confidence `75` | `CONFIRMATION_REQUIRED` | `MEETS_CONFIRMATION_THRESHOLD` |
| Confidence `89` | rank `1`, confidence `89` | `CONFIRMATION_REQUIRED` | `MEETS_CONFIRMATION_THRESHOLD` |
| Confidence `90` | rank `1`, confidence `90` | `AUTOMATIC_CANDIDATE` | `MEETS_AUTOMATIC_THRESHOLD` |
| Confidence `100` | rank `1`, confidence `100` | `AUTOMATIC_CANDIDATE` | `MEETS_AUTOMATIC_THRESHOLD` |
| Selected suggestion is rank 1 | ranks `1`, `2`, and `3` with valid descending evidence | tier from rank-1 confidence | reason from rank-1 confidence |
| Lower-ranked higher-confidence malformed input | rank `1` confidence lower than rank `2` in a way inconsistent with v0.9.3 ordering | throws `IllegalArgumentException` | n/a |
| Duplicate rank-1 suggestions | two suggestions with `rank = 1` | throws `IllegalArgumentException` | n/a |
| Missing rank 1 | suggestions start at rank `2` | throws `IllegalArgumentException` | n/a |
| Duplicate rank values | ranks `[1, 2, 2]` | throws `IllegalArgumentException` | n/a |
| Non-sequential ranks | ranks `[1, 3]` | throws `IllegalArgumentException` | n/a |
| More than three suggestions | four suggestion items | throws `IllegalArgumentException` | n/a |
| Evaluated count below suggestion count | `evaluatedCandidateCount < suggestions.size` | throws `IllegalArgumentException` | n/a |
| Suggestions evidence object preserved | any valid suggestions object | assessment contains same suggestions object | reason from selected confidence |
| Deterministic repeated calls | same valid input repeated | same assessment every call | same reason every call |
| No assignment, safety, lead, UI, persistence, scoring, or finalization fields exist | inspect public v0.9.4 result types | no out-of-scope fields | n/a |

Tests must also assert:

* `selectedSuggestion` is `null` only when no suggestions exist;
* a non-empty valid input preserves the rank-1 `TopTeamCandidateSuggestion`;
* nested `TeamCandidateScore` evidence remains available through `selectedSuggestion`;
* the original `TopTeamCandidateSuggestions` instance or value is preserved in the assessment;
* inclusive bounds at `75` and `90` are exact;
* the classifier does not call candidate scoring or suggestion generation; and
* no result includes candidate lead, assignment permission, conflict state, UI state, persistence state, scoring state, or finalization state.

## 16. Compatibility Requirements

v0.9.4 must not modify or break:

* `PlayerNameComparisonNormalizer`;
* `PlayerNameSimilarityMatcher`;
* `TeamCandidateScorer`;
* `TopTeamCandidateSuggestionProvider`;
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

## 17. Security and Privacy

Confidence-tier classification is local pure computation. It must not log raw OCR names, roster names, normalized names, pairwise scores, candidate scores, suggestion ranks, screenshots, private paths, raw OCR payloads, credentials, tokens, or backend details.

The future implementation must not upload tier evidence, create persistence records, add analytics, add crash-report metadata containing names, or change screenshot handling. Tests and documentation must use synthetic values only.

## 18. Out of Scope

v0.9.4 excludes:

* candidate lead calculation;
* second-candidate score calculation;
* 10-point lead enforcement;
* three-of-four player-match safety;
* automatic team assignment;
* confirmation UI;
* manual correction UI;
* unique-team assignment across a match;
* duplicate team resolution across OCR rows;
* cross-row conflict resolution;
* OCR review UI;
* persistence or migrations;
* cloud synchronization changes;
* raw/corrected evidence persistence;
* match finalization changes;
* roster OCR review and correction;
* team-name OCR extraction;
* fifth/sixth player OCR extraction;
* scoring changes;
* navigation;
* public sharing; and
* exports.

The non-numbered roadmap item `v0.9.x - Roster OCR Review and Correction` remains separate future scope.

## 19. Acceptance Criteria

A later v0.9.4 implementation is acceptable only when:

* it classifies v0.9.3 suggestions for exactly one OCR row;
* it uses the approved `90` and `75` thresholds;
* it treats bounds inclusively;
* it returns manual required when there are no suggestions;
* it preserves suggestion evidence;
* it validates malformed suggestion structures;
* it remains advisory and does not assign, safety-check, persist, display UI, score tournament points, or finalize matches;
* comprehensive synthetic unit tests pass; and
* existing behavior remains unchanged.

## 20. Deferred Decisions

The following remain deferred to later approved versions:

* candidate lead calculation;
* second-candidate comparison;
* 10-point lead enforcement;
* three-of-four matched-player requirement;
* unique-team assignment across one match;
* duplicate-team and cross-row conflict handling;
* automatic assignment authorization;
* review UI presentation of tier evidence;
* manual correction workflows;
* persistence of OCR review state, raw/corrected data, candidate evidence, tier evidence, or confirmed assignments;
* safe OCR-assisted finalization;
* roster OCR review and correction;
* team-name OCR extraction;
* fifth/sixth-player OCR extraction; and
* Phase 12 real OCR acceptance evaluation.

## 21. Implementation Handoff

After this decision document is reviewed, merged, and followed by explicit user approval, the implementation task may add only the proposed pure `TeamMatchConfidenceTierClassifier`, `TeamMatchConfidenceTier`, `TeamMatchConfidenceAssessment`, `TeamMatchConfidenceReason`, malformed-suggestion validation, and focused synthetic unit tests.

The implementation must not modify `PlayerNameComparisonNormalizer`, `PlayerNameSimilarityMatcher`, `TeamCandidateScorer`, `TopTeamCandidateSuggestionProvider`, `RosterNameNormalizer`, existing roster validation, Phase 8 OCR processing, persistence, UI, assignment safety, candidate lead, assignments, corrections, scoring, navigation, or finalization.
