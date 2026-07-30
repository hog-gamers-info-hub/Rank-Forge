# v0.9.2 - Team Candidate Scoring Decisions

## 1. Title and Status

**Phase:** 9 - Team Matching and Manual Correction
**Version:** v0.9.2 - Team Candidate Scoring
**Status:** Approved documentation decision gate; no implementation is authorized by this document alone.

Phase 8 is complete and closed. Phase 9 v0.9.0 Text Normalization and v0.9.1 Player Similarity Matching are implemented, verified, merged, and protected. This document defines the next narrow team-candidate scoring boundary that a later approved v0.9.2 implementation may add.

## 2. Decision Summary

v0.9.2 will add a pure, deterministic, Android-independent domain capability that scores one OCR result row against one candidate roster team by using v0.9.1 pairwise player similarity assessments.

The capability must:

* accept detected player-name values from one parsed OCR result row;
* accept roster-player display names from one manually maintained candidate team slot;
* compare detected players to roster players through `PlayerNameSimilarityMatcher.compare(...)`;
* select non-duplicated contributing player matches for that one candidate team;
* calculate a deterministic advisory team candidate confidence score;
* preserve contributing-player evidence for later review; and
* return immutable candidate evidence only.

It must not rank all teams, select top-three candidates, choose a winning team, assign a team, apply confidence tiers, enforce match-wide team uniqueness, resolve conflicts across OCR rows, modify raw OCR values, modify roster display names, change tournament scoring, or change finalization.

## 3. Repository Context

The approved v0.9.0 normalizer is `PlayerNameComparisonNormalizer` in `com.hoggamers.rankforge.domain.matching`. It returns comparison-only normalized player-name values or `null` for unusable input.

The approved v0.9.1 pairwise matcher is `PlayerNameSimilarityMatcher` in the same package. Its `PlayerNameSimilarityAssessment` records normalized detected and roster names, nullable distance, maximum code-point length, similarity score, and `PlayerNameComparisonType`. It does not rank candidates, apply thresholds, assign teams, persist data, or use UI state.

`TeamSlot` and `RosterPlayer` use `require(slotNumber in TeamSlot.SLOT_NUMBERS)` for fixed slot validation. That existing convention supports using `IllegalArgumentException` for structurally invalid candidate team slots in v0.9.2.

`RosterNameNormalizer` remains trim-only for roster validation. v0.9.2 must not modify it or reinterpret roster duplicate validation.

Phase 8 player-name parsing returns `ParsedPlayerNameRow.detectedName`, typed parse status, optional failure, and `PlayerNameOcrEvidence`. The parser preserves observed text and evidence in memory and does not normalize, match, score, correct, persist, or assign.

## 4. Scope

The v0.9.2 scope is one-row, one-candidate-team confidence evidence.

The future implementation may add:

* `TeamCandidateScorer`;
* `TeamCandidateScore`;
* `TeamCandidatePlayerMatch`;
* a deterministic internal pairwise matrix and contribution-selection routine; and
* focused synthetic unit tests.

The future implementation must not add roster-wide search, top-three suggestions, cross-team ranking, candidate lead calculation, confidence tiers, automatic assignment, match-wide assignment safety, review UI, manual correction UI, persistence, migrations, scoring changes, navigation, or finalization behavior.

## 5. Terminology and Data Boundaries

The following values remain distinct:

1. raw OCR text and hierarchy;
2. parsed OCR player-name candidate;
3. normalized detected comparison value;
4. normalized roster comparison value;
5. pairwise player similarity assessment;
6. one candidate team's scored evidence;
7. later ranked team suggestions;
8. later team assignment decision;
9. operator correction;
10. confirmed result value; and
11. finalized match result.

v0.9.2 produces advisory team-candidate evidence only. It must not write its confidence score or contributing matches back to raw OCR, parsed OCR, roster display, assigned, corrected, confirmed, or finalized data.

## 6. Single Candidate Team Boundary

v0.9.2 scores exactly one OCR row against exactly one candidate team.

It may be reused later by v0.9.3 to score multiple teams and choose top-three suggestions, but v0.9.2 itself must not expose that behavior.

It must not:

* search every roster team;
* sort candidates across teams;
* limit candidates to three;
* calculate candidate lead;
* compare the leading candidate to the second candidate;
* choose a winning team; or
* enforce unique team assignment across a match.

Those responsibilities remain deferred to v0.9.3, v0.9.4, and v0.9.5.

## 7. Proposed API and Result Contract

The intended production package is:

```kotlin
package com.hoggamers.rankforge.domain.matching
```

The proposed scorer is:

```kotlin
object TeamCandidateScorer {
    fun score(
        detectedPlayerNames: List<String?>,
        candidateTeamSlot: Int,
        rosterPlayerNames: List<String?>,
    ): TeamCandidateScore
}
```

The proposed immutable result is:

```kotlin
data class TeamCandidateScore(
    val candidateTeamSlot: Int,
    val confidenceScore: Int,
    val detectedPlayerCount: Int,
    val validDetectedPlayerCount: Int,
    val rosterPlayerCount: Int,
    val contributingMatchCount: Int,
    val averageMatchedPlayerScore: Int,
    val coverageScore: Int,
    val playerMatches: List<TeamCandidatePlayerMatch>,
)
```

The proposed contributing evidence is:

```kotlin
data class TeamCandidatePlayerMatch(
    val detectedPlayerIndex: Int,
    val rosterPlayerIndex: Int,
    val detectedOriginalName: String?,
    val rosterOriginalName: String?,
    val similarityAssessment: PlayerNameSimilarityAssessment,
    val contributesToScore: Boolean,
)
```

`detectedPlayerIndex` and `rosterPlayerIndex` are zero-based positions from the supplied lists. These fields intentionally exclude team names, persistent IDs, candidate ranks, top-three state, assignment state, threshold tiers, UI state, Room entities, Supabase DTOs, and mutable state.

## 8. Input and Candidate Slot Rules

`detectedPlayerNames` represents one OCR result row only. `rosterPlayerNames` represents one candidate team only. `candidateTeamSlot` identifies the candidate team slot for evidence; it is not an assignment result.

Rules:

* the scorer accepts nullable original strings;
* callers must not be required to normalize or precompare strings;
* each detected/roster pair is compared internally through `PlayerNameSimilarityMatcher.compare(...)`;
* original input strings and lists remain unchanged;
* empty detected or roster lists are valid and produce zero confidence;
* null, blank, punctuation-only, symbol-only, emoji-only, or otherwise invalid player names are handled by v0.9.1 and do not contribute; and
* ordinary invalid names must not throw.

`candidateTeamSlot` must be in `1..12`, matching `TeamSlot.SLOT_NUMBERS`. Because existing slot-like domain models use `require(...)` and throw `IllegalArgumentException` for invalid slots, v0.9.2 must also throw `IllegalArgumentException` for an invalid `candidateTeamSlot`. This is structural argument validation, not candidate scoring.

## 9. Pairwise Similarity Matrix

v0.9.2 must evaluate each detected player against each roster player for the candidate team using:

```kotlin
PlayerNameSimilarityMatcher.compare(...)
```

The pairwise matrix is internal implementation evidence.

Rules:

* pairwise invalid comparisons may be retained internally but do not contribute;
* pairwise comparison output must not be mutated;
* the scorer must not add extra string normalization;
* the scorer must not add a new similarity algorithm;
* the scorer must not add new OCR-confusion mappings; and
* v0.9.1 similarity scores are the only pairwise player-score source.

## 10. Contribution Selection Rules

The scorer must select deterministic non-duplicated contributing matches from the pairwise matrix.

Rules:

* one detected player index can contribute at most once;
* one roster player index can contribute at most once;
* only valid v0.9.1 comparisons may contribute;
* only comparisons at or above the contribution floor may contribute;
* candidate contributing matches are considered by highest similarity score first;
* ties are broken by lower Damerau-Levenshtein distance, then lower detected player index, then lower roster player index; and
* selected alternatives do not imply final assignment.

The selection algorithm must not use OCR confidence, team slot priority, random choice, database order, alphabetical order, or roster order as semantic match-quality evidence. List indexes are used only as deterministic tie-breakers after score and distance.

## 11. Contribution Floor

The approved minimum pairwise player similarity score required for a pair to contribute is:

```text
minimumContributingPlayerSimilarityScore = 75
```

Rationale:

* canonical confidence-tier documentation treats below `75` as manual or unmatched territory at the later team-confidence interpretation boundary;
* low-similarity pairs should not inflate a candidate team score; and
* v0.9.2 still does not apply final confidence tiers, assignment thresholds, or automatic matching.

This value is a contribution floor for candidate scoring only. It is not a final match threshold, not a confidence tier, and not an automatic-assignment rule.

## 12. Confidence Score Formula

The formula uses integer-only arithmetic.

Definitions:

```text
detectedPlayerCount =
    detectedPlayerNames.size

validDetectedPlayerCount =
    count of detected names whose PlayerNameComparisonNormalizer.normalize(...)
    result is non-null

rosterPlayerCount =
    rosterPlayerNames.size

contributingMatchCount =
    count of selected non-duplicated contributing matches

coverageContributionCount =
    min(contributingMatchCount, 4)

requiredDetectedPlayerCount =
    4
```

`requiredDetectedPlayerCount` is fixed at `4` for v0.9.2 to preserve the supported scoreboard assumption of four visible player names. `contributingMatchCount` still records the actual selected contributing match count. `coverageContributionCount` is used only for the coverage component so extra detected names beyond four may be evaluated and returned as evidence if selected, but they must not increase coverage above `100` unless a later approved OCR parser supports more visible players.

The score fields are:

```text
averageMatchedPlayerScore =
    if contributingMatchCount == 0:
        0
    else:
        sum(contributing player similarity scores) / contributingMatchCount

coverageScore =
    (coverageContributionCount * 100) / requiredDetectedPlayerCount

confidenceScore =
    (averageMatchedPlayerScore * 70 + coverageScore * 30) / 100
```

Clamp final `confidenceScore` to `0..100`.

Rules:

* no floating-point arithmetic;
* no rounding to nearest;
* no kill values;
* no placement values;
* no OCR confidence;
* no team-name similarity;
* no previous-match data;
* no standings data;
* no roster slot weighting; and
* no UI confirmation state.

## 13. Four-Player and Missing-Name Rules

v0.9.2 confidence must respect the supported scoreboard row expectation of four visible player names.

Rules:

* `detectedPlayerCount` is the raw detected input list size;
* `validDetectedPlayerCount` counts detected list entries that the approved v0.9.0 normalizer, as used by v0.9.1, can normalize to a usable detected value;
* missing, invalid, malformed, punctuation-only, symbol-only, or null detected names reduce coverage because they cannot contribute;
* `contributingMatchCount` counts only selected non-duplicated matches at or above the contribution floor;
* extra detected names beyond four are accepted as evidence and may appear in returned contributing matches if selected;
* extra detected names beyond four do not increase the coverage component above four contributing matches;
* extra detected names must not create assignment authority, thresholds, top-three ranking, candidate lead behavior, or any other later-version behavior;
* roster teams may contain four to six roster players; and
* rows with fewer than four valid detected names may still produce advisory evidence but should receive lower confidence.

Missing detected players must not be treated as successful matches.

## 14. Duplicate and Ambiguity Handling

Duplicate detected names are allowed as raw evidence, but each detected index can contribute at most once. Duplicate roster names are allowed as input evidence, but each roster index can contribute at most once.

Rules:

* identical pair scores across multiple roster players must not be collapsed;
* ambiguous alternatives must not cause assignment in v0.9.2;
* selected contribution evidence must preserve the exact detected and roster indexes used;
* non-selected equal alternatives remain unresolved for later review only if a later version chooses to expose them; and
* v0.9.2 must not implement global duplicate-player policy, team uniqueness policy, or cross-row conflict resolution.

## 15. Result Evidence and Ordering

`playerMatches` must include only selected contributing matches. The complete pairwise matrix remains an internal implementation detail.

Returned `playerMatches` must be sorted by:

1. lower detected player index;
2. lower roster player index.

Every returned match has `contributesToScore = true`. Keeping only contributing matches makes the result small and stable. A later review-focused version may add broader evaluated-pair evidence if explicitly approved.

## 16. Complexity and Implementation Constraints

Implementation must be deterministic and bounded for short player lists: one OCR row and one roster team.

Required constraints:

* no Android framework dependency;
* no network access;
* no database access;
* no persistence;
* no logging of OCR or roster names;
* no mutable global state;
* thread-safe pure scoring;
* no recursion that risks stack growth;
* no external matching dependency;
* no external scoring dependency; and
* no third-party dependency.

The expected input size is small: four visible detected names and four to six roster players. Larger lists may be handled deterministically, but they must not broaden the public behavior into roster-wide search.

## 17. Synthetic Unit-Test Matrix

Later implementation tests must use only synthetic names and values. They must not include real player names, screenshots, OCR payloads, private paths, or personal data.

| Case | Detected inputs | Roster inputs | Expected contributing matches | Average score | Coverage score | Confidence score |
| --- | --- | --- | --- | ---: | ---: | ---: |
| Four exact matches | `["Unit7", "Nova", "Rin", "Kai"]` | `["Unit7", "Nova", "Rin", "Kai"]` | `(0,0) (1,1) (2,2) (3,3)` | 100 | 100 | 100 |
| Three exact, one missing detected | `["Unit7", "Nova", "Rin", null]` | `["Unit7", "Nova", "Rin", "Kai"]` | `(0,0) (1,1) (2,2)` | 100 | 75 | 92 |
| Two exact, two missing detected | `["Unit7", "Nova", null, ""]` | `["Unit7", "Nova", "Rin", "Kai"]` | `(0,0) (1,1)` | 100 | 50 | 85 |
| No valid detected players | `[null, "", "--", "***"]` | `["Unit7", "Nova", "Rin", "Kai"]` | none | 0 | 0 | 0 |
| Empty detected list | `[]` | `["Unit7", "Nova", "Rin", "Kai"]` | none | 0 | 0 | 0 |
| Empty roster list | `["Unit7", "Nova", "Rin", "Kai"]` | `[]` | none | 0 | 0 | 0 |
| One invalid detected name | `["Unit7", "--", "Rin", "Kai"]` | `["Unit7", "Nova", "Rin", "Kai"]` | `(0,0) (2,2) (3,3)` | 100 | 75 | 92 |
| One invalid roster name | `["Unit7", "Nova", "Rin", "Kai"]` | `["Unit7", "--", "Rin", "Kai"]` | `(0,0) (2,2) (3,3)` | 100 | 75 | 92 |
| One fuzzy contributing match above floor | `["abcd", "Nova", "Rin", "Kai"]` | `["abxd", "Nova", "Rin", "Kai"]` | `(0,0) (1,1) (2,2) (3,3)` | 93 | 100 | 95 |
| One fuzzy pair below floor not contributing | `["ab", "Nova", "Rin", "Kai"]` | `["xy", "Nova", "Rin", "Kai"]` | `(1,1) (2,2) (3,3)` | 100 | 75 | 92 |
| Duplicate detected names compete for one roster player | `["Unit7", "Unit7", "Rin", "Kai"]` | `["Unit7", "Nova", "Rin", "Kai"]` | `(0,0) (2,2) (3,3)` | 100 | 75 | 92 |
| One detected player competes for duplicate roster names | `["Unit7", "Nova", "Rin", "Kai"]` | `["Unit7", "Unit7", "Rin", "Kai"]` | `(0,0) (2,2) (3,3)` | 100 | 75 | 92 |
| One roster player cannot contribute twice | `["Unit7", "Unit7", "Rin", "Kai"]` | `["Unit7"]` | `(0,0)` | 100 | 25 | 77 |
| One detected player cannot contribute twice | `["Unit7"]` | `["Unit7", "Unit7", "Rin", "Kai"]` | `(0,0)` | 100 | 25 | 77 |
| Tie-breaking by distance | `["abcdefghijkl"]` | `["abcxyzghijkl", "abcdefghijklWXYZ"]` | `(0,0)` because both candidate pairs score 75, then distance 3 beats distance 4 | 75 | 25 | 60 |
| Tie-breaking by detected index | `["Unit7", "Unit7"]` | `["Unit7"]` | `(0,0)` | 100 | 25 | 77 |
| Tie-breaking by roster index | `["Unit7"]` | `["Unit7", "Unit7"]` | `(0,0)` | 100 | 25 | 77 |
| Roster with six players | `["Unit7", "Nova", "Rin", "Kai"]` | `["BenchA", "Unit7", "BenchB", "Nova", "Rin", "Kai"]` | `(0,1) (1,3) (2,4) (3,5)` | 100 | 100 | 100 |
| More than four detected input names | `["Unit7", "Nova", "Rin", "Kai", "Extra"]` | `["Unit7", "Nova", "Rin", "Kai", "Extra"]` | `(0,0) (1,1) (2,2) (3,3) (4,4)`; `contributingMatchCount = 5`, `coverageContributionCount = 4` | 100 | 100 | 100 |
| Contribution floor is not a confidence tier | `["abcd"]` | `["abxd"]` | `(0,0)` | 75 | 25 | 60 |
| Confidence clamped to `0..100` | five exact detected values against five roster values | five selected matches may be returned as evidence, coverage uses only four, and final confidence remains clamped | 100 | 100 | 100 |
| Deterministic repeated calls | same four exact inputs repeated | same roster inputs repeated | same matches every call | 100 | 100 | 100 |
| Original inputs preserved | mutable copies of four synthetic strings | four matching roster strings | four exact matches | 100 | 100 | 100 |
| Invalid candidate slot behavior | valid player lists with slot `0` or `13` | valid player lists | throws `IllegalArgumentException` | n/a | n/a | n/a |
| No speculative confusion mappings | `["5S", "Nova", "Rin", "Kai"]` | `["55", "Nova", "Rin", "Kai"]` | `(1,1) (2,2) (3,3)`; `5S` vs `55` does not contribute because score is 50 | 100 | 75 | 92 |

Tests must also assert:

* `detectedPlayerCount`;
* `validDetectedPlayerCount`;
* `rosterPlayerCount`;
* `contributingMatchCount`;
* selected index ordering;
* `contributesToScore = true` for returned matches;
* no selected duplicate detected index;
* no selected duplicate roster index;
* input list contents are unchanged; and
* no result includes rank, lead, assignment, threshold, UI, persistence, scoring, or finalization state.

## 18. Compatibility Requirements

v0.9.2 must not modify or break:

* `PlayerNameComparisonNormalizer`;
* `PlayerNameSimilarityMatcher`;
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

Team candidate scoring is local pure computation. It must not log raw OCR names, roster names, normalized names, pairwise scores, candidate scores, screenshots, private paths, raw OCR payloads, credentials, tokens, or backend details.

The future implementation must not upload candidate evidence, create persistence records, add analytics, add crash-report metadata containing names, or change screenshot handling. Tests and documentation must use synthetic values only.

## 20. Out of Scope

v0.9.2 excludes:

* scoring all 12 teams in one public API;
* top-three suggestions;
* candidate ranking across teams;
* confidence tiers;
* automatic team assignment;
* confirmation tiers;
* candidate lead calculation;
* unique-team assignment across a match;
* duplicate team resolution;
* cross-row conflict resolution;
* OCR review UI;
* manual correction UI;
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

The roadmap item `v0.9.x - Roster OCR Review and Correction` remains separate future scope.

## 21. Acceptance Criteria

A later v0.9.2 implementation is acceptable only when:

* it uses v0.9.1 pairwise player similarity internally;
* it scores exactly one OCR row against exactly one candidate team;
* it validates candidate slots consistently with fixed 12-slot conventions;
* it prevents duplicate contribution from one detected index or one roster index;
* it uses the approved deterministic contribution-selection rule;
* it applies the approved contribution floor;
* it uses the approved integer confidence formula;
* it preserves contributing-player evidence;
* it returns only advisory evidence;
* it does not assign, rank, threshold, persist, display, score tournament points, or finalize matches;
* comprehensive synthetic unit tests pass; and
* existing behavior remains unchanged.

## 22. Deferred Decisions

The following remain deferred to later approved versions:

* roster-wide candidate scoring orchestration;
* top-three suggestion selection and ordering;
* candidate ranking across teams;
* confidence tier interpretation;
* candidate lead calculation;
* automatic assignment conditions;
* match-wide unique-team assignment safety;
* duplicate-team and cross-row conflict handling;
* review UI presentation of ambiguity and non-selected alternatives;
* manual correction workflows;
* persistence of OCR review state, raw/corrected data, candidate evidence, or confirmed assignments;
* safe OCR-assisted finalization;
* roster OCR review and correction;
* team-name OCR extraction;
* fifth/sixth player OCR extraction; and
* Phase 12 real OCR acceptance evaluation.

## 23. Implementation Handoff

After this decision document is reviewed, merged, and followed by explicit user approval, the implementation task may add only the proposed pure `TeamCandidateScorer`, `TeamCandidateScore`, `TeamCandidatePlayerMatch`, the minimal internal contribution-selection logic needed by that scorer, and focused synthetic unit tests.

The implementation must not modify `PlayerNameComparisonNormalizer`, `PlayerNameSimilarityMatcher`, `RosterNameNormalizer`, existing roster validation, Phase 8 OCR processing, persistence, UI, top-three suggestion behavior, confidence tiers, assignments, corrections, scoring, navigation, or finalization.
