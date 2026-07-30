# v0.9.1 - Player Similarity Matching Decisions

## 1. Title and Status

**Phase:** 9 - Team Matching and Manual Correction
**Version:** v0.9.1 - Player Similarity Matching
**Status:** Approved documentation decision gate; no implementation is authorized by this document alone.

Phase 8 is complete and closed. Phase 9 v0.9.0 Text Normalization is implemented, verified, merged, and protected. This document defines the next narrow comparison boundary that a later approved v0.9.1 implementation may add.

## 2. Decision Summary

v0.9.1 will add a pure, deterministic, Android-independent domain capability that compares one OCR-detected player-name value with one manually maintained roster player-name value.

The capability must:

* normalize both input values through `PlayerNameComparisonNormalizer.normalize(...)`;
* calculate unrestricted Damerau-Levenshtein distance over Unicode code points;
* convert that distance into a deterministic integer player similarity score;
* return immutable comparison evidence for later team-candidate scoring and operator review; and
* preserve original OCR and roster display values outside the comparison result.

It must not assign a team, select a winning roster player, rank candidates, aggregate team confidence, apply thresholds, enforce global assignment safety, mutate raw OCR evidence, mutate roster display names, persist comparison output, or introduce UI behavior.

## 3. Repository Context

The approved v0.9.0 normalizer lives in `com.hoggamers.rankforge.domain.matching` as `PlayerNameComparisonNormalizer`. It is a pure Kotlin object with no Android dependency. It normalizes case, whitespace, punctuation, symbols, and only the approved comparison confusion groups `0/o -> 0` and `1/i/l -> 1`.

The existing `RosterNameNormalizer` in `domain/tournament/RosterValidation.kt` remains trim-only and is used for roster validation and duplicate checks. v0.9.1 must not modify or reinterpret that behavior.

Phase 8 player-name parsing returns `ParsedPlayerNameRow.detectedName`, `PlayerNameParseStatus`, optional failure information, and `PlayerNameOcrEvidence`. That parser preserves observed text and evidence in memory and does not normalize, match, correct, persist, score, or assign.

Roster players are represented by `RosterPlayer`, including `tournamentId`, `slotNumber`, and `displayName`. v0.9.1 consumes only a supplied roster display-name string and must not add roster IDs, team slots, or persistence state to the pairwise comparison contract.

Existing domain code uses immutable data classes, enums, sealed result models, focused objects/classes, and synthetic unit tests. The v0.9.1 contract follows those conventions while remaining documentation-only in this task.

## 4. Scope

The v0.9.1 scope is pairwise player-name similarity evidence only.

The future implementation may add:

* one comparison entry point for one detected name and one roster name;
* immutable result data that records normalized values, distance, maximum length, score, and classification;
* an internal unrestricted Damerau-Levenshtein implementation; and
* focused synthetic unit tests.

The future implementation must not add roster-wide search, team ranking, confidence thresholds, automatic assignment, manual correction, review UI, persistence, migrations, scoring, navigation, external dependencies, or finalization behavior.

## 5. Terminology and Data Boundaries

The following values remain distinct:

1. raw OCR text and hierarchy;
2. parsed OCR player-name candidate;
3. normalized detected comparison value;
4. normalized roster comparison value;
5. pairwise similarity assessment;
6. later roster-player match selection;
7. later team candidate and confidence information;
8. operator correction;
9. assigned team value;
10. confirmed result value; and
11. finalized match result.

v0.9.1 produces comparison evidence only. It must not write the normalized or scored output back to raw OCR, parsed OCR, roster display, corrected, assigned, confirmed, or finalized data.

## 6. Pairwise Comparison Boundary

v0.9.1 compares exactly one detected player value against exactly one roster player value.

It must not:

* search a complete roster;
* rank teams;
* rank multiple roster players;
* choose a winner between equal candidates;
* enforce one-to-one assignment across multiple detected players;
* resolve duplicate-player conflicts;
* resolve duplicate-team conflicts; or
* use roster order, team slot, database order, OCR confidence, or UI state as a tie-breaker.

Roster-wide search and candidate aggregation remain deferred to v0.9.2. Assignment safety remains deferred to v0.9.5.

## 7. Proposed API and Result Contract

The intended production package is:

```kotlin
package com.hoggamers.rankforge.domain.matching
```

The proposed comparator is:

```kotlin
object PlayerNameSimilarityMatcher {
    fun compare(
        detectedName: String?,
        rosterName: String?,
    ): PlayerNameSimilarityAssessment
}
```

The proposed immutable result is:

```kotlin
data class PlayerNameSimilarityAssessment(
    val normalizedDetectedName: String?,
    val normalizedRosterName: String?,
    val distance: Int?,
    val maximumLength: Int,
    val similarityScore: Int,
    val comparisonType: PlayerNameComparisonType,
)
```

The proposed comparison classification is:

```kotlin
enum class PlayerNameComparisonType {
    INVALID_INPUT,
    EXACT,
    NORMALIZED_EXACT,
    FUZZY,
}
```

These fields intentionally exclude IDs, team slots, team confidence, assignment state, UI state, persistence references, thresholds, ranks, mutable state, and raw evidence objects.

## 8. Input and Normalization Rules

The comparator accepts original detected and roster strings. Callers must not be required to normalize values before calling it.

For every call, the comparator must internally call:

```kotlin
PlayerNameComparisonNormalizer.normalize(...)
```

for both inputs.

Rules:

* original input strings remain unchanged;
* normalization output is comparison evidence only;
* the matcher must not perform additional Unicode normalization beyond the v0.9.0 normalizer;
* the matcher must not remove diacritics, transliterate, or apply locale-aware linguistic comparison;
* `null`, empty, whitespace-only, punctuation-only, symbol-only, emoji-only, or otherwise normalization-invalid values produce `INVALID_INPUT`; and
* invalid input must never become an exact, normalized-exact, fuzzy, automatic, assignable, or confirmed match.

## 9. Comparison Classification

Classification must be derived in this order:

1. `INVALID_INPUT` when either normalized value is absent.
2. `EXACT` when both original input strings are non-null, exactly equal before normalization, and both normalized values are valid.
3. `NORMALIZED_EXACT` when original strings differ but both valid normalized values are equal.
4. `FUZZY` when both normalized values are valid and different.

`EXACT` and `NORMALIZED_EXACT` must both produce:

```text
distance = 0
similarityScore = 100
```

Normalized equality must not be reported as raw equality. Empty or otherwise invalid original strings that are equal to each other still produce `INVALID_INPUT`, not `EXACT`.

## 10. Damerau-Levenshtein Definition

v0.9.1 uses unrestricted Damerau-Levenshtein distance, not the restricted Optimal String Alignment variant.

The distance must support:

* insertion;
* deletion;
* substitution;
* adjacent transposition; and
* repeated and overlapping edit sequences permitted by unrestricted Damerau-Levenshtein semantics.

Unit costs are fixed:

```text
insertion = 1
deletion = 1
substitution = 1
transposition = 1
```

The implementation must not introduce fractional costs, weighted substitutions, phonetic matching, transliteration, token reordering, substring matching, Jaro-Winkler, Soundex, heuristic fallback algorithms, or a third-party string-similarity dependency.

## 11. Unicode Code-Point Rules

Distance must be calculated over Unicode code points, not UTF-16 `Char` units.

Requirements:

* supplementary characters retained by v0.9.0 normalization must not be split into surrogate halves;
* code-point length is used for `maximumLength`;
* v0.9.0 NFC normalization remains authoritative;
* the matcher must not perform its own additional normalization;
* the matcher must not remove diacritics or transliterate;
* the matcher must not depend on Android APIs; and
* the matcher must not depend on the device default locale.

Code-point comparison is deterministic. It is not a locale-aware linguistic comparison and must not claim to understand pronunciation, language-specific equivalence, or display grapheme clusters.

## 12. Similarity Score Formula

For valid fuzzy comparisons:

```text
maximumLength =
    max(normalizedDetectedCodePointLength, normalizedRosterCodePointLength)

similarityScore =
    ((maximumLength - distance) * 100) / maximumLength
```

The calculation uses integer division with truncation toward zero.

The final score must be clamped to:

```text
0..100
```

Rules:

* no floating-point calculation;
* no rounding to nearest integer;
* no threshold classification in v0.9.1;
* no team-confidence interpretation;
* `distance = 0` produces `100`;
* completely different one-character values produce `0`; and
* repeated equivalent calls produce the same score.

For invalid input, `similarityScore` is always `0`.

## 13. OCR-Confusion Handling

v0.9.1 receives confusion-aware behavior only through the approved v0.9.0 normalizer:

```text
0 / o -> 0
1 / i / l -> 1
```

Names differing only by those approved confusion groups become `NORMALIZED_EXACT` with `distance = 0` and `similarityScore = 100`.

v0.9.1 must not add new confusion mappings, weighted substitutions, or pair-specific similarity boosts.

Specifically, it must not introduce:

* `5/S`;
* `8/B`;
* `2/Z`;
* `6/G`;
* `rn/m`;
* `vv/w`; or
* any other speculative pair.

Additional confusion behavior requires representative OCR evidence and a separately approved decision.

## 14. Invalid Input Behavior

Invalid input is any comparison where either normalized value is absent after calling the v0.9.0 normalizer.

Invalid input produces:

```text
distance = null
similarityScore = 0
comparisonType = INVALID_INPUT
```

`maximumLength` is:

* `0` when both normalized values are absent;
* otherwise the available normalized value's Unicode code-point length.

Invalid input must never become a valid match, exact match, normalized-exact match, fuzzy match, candidate winner, automatic assignment, or confirmed result.

## 15. Tie and Threshold Boundaries

v0.9.1 returns similarity evidence, not a boolean match decision.

It must not add:

* `isMatch`;
* `isAutomatic`;
* `requiresConfirmation`;
* `confidenceTier`;
* minimum accepted score;
* automatic rejection threshold;
* candidate rank;
* candidate lead;
* team confidence; or
* tie-break metadata.

Equal pairwise similarity scores must remain equal. v0.9.1 must not break ties using roster order, team slot, alphabetical order, shorter name, OCR confidence, random selection, database order, first encountered value, or any other rule.

The `90`, `75`, three-of-four-player, and 10-point-lead rules belong to later Phase 9 versions. A score of `100` does not itself authorize team assignment.

## 16. Complexity and Implementation Constraints

The later implementation should use deterministic bounded in-memory computation appropriate for short player names.

Required constraints:

* no Android framework dependency;
* no network access;
* no database access;
* no persistence;
* no logging of OCR or roster names;
* no mutable global state;
* thread-safe pure comparison;
* no recursion that risks stack growth;
* no external string-similarity dependency unless separately approved; and
* no third-party dependency for this version.

Prefer an internal iterative implementation using primitive arrays or equivalent bounded structures. Inputs must be handled safely without crashes for unusual Unicode that survives normalization.

## 17. Synthetic Unit-Test Matrix

Later implementation tests must use only synthetic names and values. They must not include genuine player names, real OCR payloads, screenshots, private paths, or personal data.

| Case | Detected input | Roster input | Expected type | Expected normalized detected | Expected normalized roster | Expected distance | Expected maximum length | Expected score |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Identical original strings | `Unit7` | `Unit7` | `EXACT` | `un1t7` | `un1t7` | `0` | `5` | `100` |
| Case-only difference | `Unit7` | `unit7` | `NORMALIZED_EXACT` | `un1t7` | `un1t7` | `0` | `5` | `100` |
| Whitespace-only difference | `Unit 7` | `Unit   7` | `NORMALIZED_EXACT` | `un1t 7` | `un1t 7` | `0` | `6` | `100` |
| Punctuation-only difference | `Unit-7` | `Unit_7` | `NORMALIZED_EXACT` | `un1t 7` | `un1t 7` | `0` | `6` | `100` |
| `0/O` normalized equality | `O0o` | `000` | `NORMALIZED_EXACT` | `000` | `000` | `0` | `3` | `100` |
| `1/I/l` normalized equality | `1Il` | `111` | `NORMALIZED_EXACT` | `111` | `111` | `0` | `3` | `100` |
| One substitution | `abcd` | `abxd` | `FUZZY` | `abcd` | `abxd` | `1` | `4` | `75` |
| One insertion | `abcd` | `abcde` | `FUZZY` | `abcd` | `abcde` | `1` | `5` | `80` |
| One deletion | `abcde` | `abcd` | `FUZZY` | `abcde` | `abcd` | `1` | `5` | `80` |
| One adjacent transposition | `abcd` | `acbd` | `FUZZY` | `abcd` | `acbd` | `1` | `4` | `75` |
| Unrestricted DL differs from OSA | `CA` | `ABC` | `FUZZY` | `ca` | `abc` | `2` | `3` | `33` |
| Multiple edits | `abcd` | `abxy` | `FUZZY` | `abcd` | `abxy` | `2` | `4` | `50` |
| Different lengths | `abc` | `abcdef` | `FUZZY` | `abc` | `abcdef` | `3` | `6` | `50` |
| Completely different one-character values | `a` | `b` | `FUZZY` | `a` | `b` | `1` | `1` | `0` |
| Composed/decomposed Unicode equivalence | `Café` | `Café` | `NORMALIZED_EXACT` | `café` | `café` | `0` | `4` | `100` |
| Supplementary code-point safety | `a𐐨b` | `a𐐨c` | `FUZZY` | `a𐐨b` | `a𐐨c` | `1` | `3` | `66` |
| Null detected value | `null` | `Alpha` | `INVALID_INPUT` | `null` | `a1pha` | `null` | `5` | `0` |
| Null roster value | `Alpha` | `null` | `INVALID_INPUT` | `a1pha` | `null` | `null` | `5` | `0` |
| Both null | `null` | `null` | `INVALID_INPUT` | `null` | `null` | `null` | `0` | `0` |
| Empty input | `` | `Alpha` | `INVALID_INPUT` | `null` | `a1pha` | `null` | `5` | `0` |
| Punctuation-only input | `--__...` | `Alpha` | `INVALID_INPUT` | `null` | `a1pha` | `null` | `5` | `0` |
| Invalid versus valid input | `★★☆` | `Alpha` | `INVALID_INPUT` | `null` | `a1pha` | `null` | `5` | `0` |
| Symmetric distance | `abcd` and `abxd` in both directions | same pair reversed | `FUZZY` | same normalized pair reversed | same normalized pair reversed | `1` both ways | `4` both ways | `75` both ways |
| Score symmetry | `abc` and `abcdef` in both directions | same pair reversed | `FUZZY` | same normalized pair reversed | same normalized pair reversed | `3` both ways | `6` both ways | `50` both ways |
| Deterministic repeated calls | `abcd` | `acbd` | `FUZZY` | `abcd` | `acbd` | `1` on every call | `4` on every call | `75` on every call |
| Input string preservation | ` Alpha-Beta ` | `Alpha Beta` | `NORMALIZED_EXACT` | `a1pha beta` | `a1pha beta` | `0` | `10` | `100` |
| No speculative confusion mappings | `5S` | `55` | `FUZZY` | `5s` | `55` | `1` | `2` | `50` |
| Equal scores remain equal without tie-breaking | `abcd` vs `abxd`; `wxyz` vs `wxyq` | two independent pairwise calls | `FUZZY` for both | pair-specific | pair-specific | `1` for both | `4` for both | `75` for both |

Tests must also assert that:

* `normalize(normalize(value)) == normalize(value)` remains owned by v0.9.0, while v0.9.1 comparison itself is deterministic across repeated calls;
* `distance(a, b) == distance(b, a)` for valid inputs;
* `similarityScore(a, b) == similarityScore(b, a)` for valid inputs;
* null distance is not coerced to zero for invalid input; and
* equal scores are not converted into ordered or ranked outcomes.

## 18. Compatibility Requirements

v0.9.1 must not modify or break:

* `PlayerNameComparisonNormalizer`;
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

Player similarity matching is local pure computation. It must not log raw OCR names, roster names, normalized names, distances, scores, screenshots, private paths, raw OCR payloads, credentials, tokens, or backend details.

The future implementation must not upload comparison values, create persistence records, add analytics, add crash-report metadata containing names, or change screenshot handling. Tests and documentation must use synthetic values only.

## 20. Out of Scope

v0.9.1 excludes:

* roster-wide player search;
* team candidate scoring;
* candidate aggregation;
* top-three suggestions;
* confidence thresholds;
* automatic team assignment;
* confirmation tiers;
* candidate lead calculation;
* one-to-one multi-player assignment;
* duplicate player resolution;
* duplicate team resolution;
* OCR review UI;
* manual correction UI;
* persistence or migrations;
* cloud synchronization changes;
* raw/corrected evidence persistence;
* match finalization changes;
* roster OCR review and correction;
* scoring changes;
* navigation;
* public sharing; and
* exports.

The roadmap item `v0.9.x - Roster OCR Review and Correction` remains separate future scope.

## 21. Acceptance Criteria

A later v0.9.1 implementation is acceptable only when:

* it uses the approved v0.9.0 normalizer internally;
* it implements unrestricted Damerau-Levenshtein distance;
* it compares Unicode code points;
* it uses the exact integer score formula;
* it returns immutable comparison evidence;
* exact and normalized-exact cases are distinguishable;
* invalid input cannot produce a valid similarity match;
* approved OCR confusion groups are handled through normalization;
* no speculative confusion mapping is added;
* equal scores remain equal without tie-breaking;
* no threshold, ranking, assignment, persistence, UI, scoring, or finalization logic is introduced;
* comprehensive synthetic unit tests pass; and
* existing behavior remains unchanged.

## 22. Deferred Decisions

The following remain deferred to later approved versions:

* roster-wide comparison orchestration;
* player-to-roster candidate selection;
* team candidate score aggregation;
* top-three suggestion ordering;
* confidence tiers and threshold interpretation;
* candidate lead calculation;
* automatic assignment conditions;
* one-to-one multi-player assignment safety;
* duplicate-player and duplicate-team conflict handling;
* OCR review UI and manual correction workflows;
* persistence of OCR review state, raw/corrected data, and confirmed assignments;
* safe OCR-assisted finalization;
* roster OCR review and correction;
* Phase 12 real OCR acceptance evaluation; and
* any additional OCR-confusion mappings supported by representative evidence.

## 23. Implementation Handoff

After this decision document is reviewed, merged, and followed by explicit user approval, the implementation task may add only the proposed pure `PlayerNameSimilarityMatcher`, `PlayerNameSimilarityAssessment`, `PlayerNameComparisonType`, the unrestricted Damerau-Levenshtein helper needed by that matcher, and focused synthetic unit tests.

The implementation must not modify `PlayerNameComparisonNormalizer`, `RosterNameNormalizer`, existing roster validation, Phase 8 OCR processing, persistence, UI, team candidate scoring, thresholds, assignments, corrections, scoring, navigation, or finalization.
