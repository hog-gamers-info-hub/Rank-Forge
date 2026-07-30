# v0.9.3 - Top-Three Suggestions Decisions

## 1. Title and Status

**Phase:** 9 - Team Matching and Manual Correction
**Version:** v0.9.3 - Top-Three Suggestions
**Status:** Approved documentation decision gate; no implementation is authorized by this document alone.

Phase 8 is complete and closed. Phase 9 v0.9.0 Text Normalization, v0.9.1 Player Similarity Matching, and v0.9.2 Team Candidate Scoring are implemented, verified, merged, and protected. This document defines only the next narrow suggestion-selection boundary that a later approved v0.9.3 implementation may add.

## 2. Decision Summary

v0.9.3 will add a pure, deterministic, Android-independent domain capability that produces the three strongest advisory team suggestions for one parsed OCR result row.

The capability must:

* accept detected player-name values from one parsed OCR result row;
* accept the available candidate roster teams for one tournament;
* score each supplied candidate team through `TeamCandidateScorer`;
* order candidate scores deterministically;
* return at most three strongest candidate suggestions;
* preserve team-candidate evidence for later operator review; and
* remain advisory only.

It must not assign a team, auto-select a winner, apply automatic or confirmation-required tiers, apply manual tiers, enforce candidate lead rules, enforce one team per match across multiple OCR rows, resolve duplicate team assignment, render UI, persist suggestion state, modify raw OCR values, modify roster display names, change scoring, or change match finalization.

## 3. Repository Context

The approved v0.9.0 normalizer is `PlayerNameComparisonNormalizer` in `com.hoggamers.rankforge.domain.matching`. It produces comparison-only values and must not alter raw OCR text, parsed OCR text, or roster display names.

The approved v0.9.1 matcher is `PlayerNameSimilarityMatcher` in the same package. It compares one detected player-name value to one roster player-name value and returns immutable `PlayerNameSimilarityAssessment` evidence. It does not rank teams, apply thresholds, assign teams, persist data, or expose UI state.

The approved v0.9.2 scorer is `TeamCandidateScorer`. It scores one OCR result row against one candidate team, validates the candidate team slot using the fixed `TeamSlot.SLOT_NUMBERS` convention, selects non-duplicated contributing player matches, applies the contribution floor of `75`, and returns immutable `TeamCandidateScore` evidence. It does not rank teams, return top-three suggestions, calculate candidate lead, apply confidence tiers, assign teams, persist data, or expose UI state.

`TeamSlot` and `RosterPlayer` validate fixed team-slot numbers with `require(...)`, producing `IllegalArgumentException` for structurally invalid slot values. The same exception behavior remains appropriate for invalid candidate slots surfaced through `TeamCandidateScorer`.

Phase 8 player-name parsing returns one `ParsedPlayerNameRow` per fixed scoreboard row. A row exposes `detectedName`, parse status, optional failure, and `PlayerNameOcrEvidence`. Parsed OCR values and raw OCR evidence remain in-memory processing evidence and are not confirmed match results.

## 4. Scope

The v0.9.3 scope is one-row, roster-candidate suggestion selection.

The future implementation may add:

* `TopTeamCandidateSuggestionProvider`;
* `TeamCandidateRosterInput`;
* `TopTeamCandidateSuggestions`;
* `TopTeamCandidateSuggestion`;
* deterministic candidate sorting and top-three selection; and
* focused synthetic unit tests.

The future implementation must not add confidence tiers, automatic assignment, confirmation-required behavior, manual-tier interpretation, candidate lead calculation, match-wide assignment safety, duplicate-team resolution across OCR rows, review UI, correction UI, persistence, migrations, scoring changes, navigation, or finalization behavior.

## 5. Terminology and Data Boundaries

The following values remain distinct:

1. raw OCR text and hierarchy;
2. parsed OCR player-name candidate;
3. normalized detected comparison value;
4. normalized roster comparison value;
5. pairwise player similarity assessment;
6. one candidate team's scored evidence;
7. top-three advisory suggestion;
8. later threshold interpretation;
9. later team assignment decision;
10. operator correction;
11. confirmed result value; and
12. finalized match result.

v0.9.3 consumes detected player-name values and supplied candidate roster teams. It returns advisory suggestion evidence only. It must not write suggestion output back to raw OCR, parsed OCR, normalized, pairwise-compared, candidate-scored, thresholded, assigned, corrected, confirmed, or finalized state.

## 6. One OCR Row Boundary

v0.9.3 operates on exactly one OCR result row at a time.

It may evaluate multiple candidate teams for that one row, but it must not:

* process all 12 OCR rows together;
* enforce match-wide unique team assignment;
* detect candidate conflicts across rows;
* resolve duplicate assignments; or
* finalize a match.

Those responsibilities remain deferred to v0.9.5, v0.9.6, v0.9.7, and v0.9.8.

## 7. Proposed API and Result Contract

The intended production package is:

```kotlin
package com.hoggamers.rankforge.domain.matching
```

The proposed suggestion provider is:

```kotlin
object TopTeamCandidateSuggestionProvider {
    fun suggestTopThree(
        detectedPlayerNames: List<String?>,
        candidateTeams: List<TeamCandidateRosterInput>,
    ): TopTeamCandidateSuggestions
}
```

The proposed candidate input is:

```kotlin
data class TeamCandidateRosterInput(
    val teamSlot: Int,
    val rosterPlayerNames: List<String?>,
)
```

The proposed immutable result is:

```kotlin
data class TopTeamCandidateSuggestions(
    val detectedPlayerCount: Int,
    val evaluatedCandidateCount: Int,
    val suggestions: List<TopTeamCandidateSuggestion>,
)
```

The proposed suggestion item is:

```kotlin
data class TopTeamCandidateSuggestion(
    val rank: Int,
    val teamCandidateScore: TeamCandidateScore,
)
```

These types intentionally exclude team assignment state, confidence tiers, candidate lead, UI state, Room entities, Supabase DTOs, mutable state, match row IDs, finalized-result fields, scoring fields, persistence references, and corrected-value fields.

## 8. Input Rules

`detectedPlayerNames` represents one parsed OCR result row only. `candidateTeams` represents the candidate roster teams available for comparison in one tournament context.

Rules:

* each candidate team contains one candidate team slot and its roster player names;
* callers are not required to pre-score candidate teams;
* callers are not required to normalize or precompare names;
* original input lists and strings remain unchanged;
* empty candidate team input is valid and returns no suggestions;
* candidate teams with empty roster-player lists are evaluated and normally produce zero confidence;
* invalid player names are handled by v0.9.1 and v0.9.2;
* invalid team slots must surface the same `IllegalArgumentException` behavior from `TeamCandidateScorer`; and
* invalid team slots must not be silently skipped or converted into zero-confidence suggestions.

## 9. Candidate Evaluation Rules

The provider must evaluate each supplied candidate team using:

```kotlin
TeamCandidateScorer.score(
    detectedPlayerNames = detectedPlayerNames,
    candidateTeamSlot = candidate.teamSlot,
    rosterPlayerNames = candidate.rosterPlayerNames,
)
```

Rules:

* do not add extra string normalization;
* do not add new player similarity logic;
* do not add new OCR-confusion mappings;
* do not recalculate team confidence outside `TeamCandidateScorer`;
* do not mutate `TeamCandidateScore`;
* do not filter out a candidate only because confidence is low;
* do not infer team names from OCR;
* do not use OCR confidence, placement, kills, standings, or previous matches; and
* do not persist evaluated candidate scores.

## 10. Top-Three Selection Rule

v0.9.3 returns:

```text
min(3, evaluatedCandidateCount)
```

suggestions after deterministic ordering.

If fewer than three teams are available, return fewer than three suggestions. If no candidate teams are available, return an empty suggestions list.

The returned list must contain only selected suggestion items. The complete evaluated-candidate list remains internal unless a later approved version explicitly exposes broader review evidence.

## 11. Candidate Ordering and Ranking

Candidate scores must be ordered by:

1. higher `confidenceScore`;
2. higher `contributingMatchCount`;
3. higher `averageMatchedPlayerScore`;
4. higher `coverageScore`;
5. lower `candidateTeamSlot`.

Rationale:

* confidence remains primary;
* contributing matches and average player strength preserve scoring evidence;
* coverage provides deterministic secondary evidence;
* lower slot is only a stable final tie-breaker, not semantic proof of correctness.

Under the approved v0.9.2 formula, `coverageScore` is derived from `contributingMatchCount` capped at four. That makes `coverageScore` a required defensive ordering field, but it is normally redundant after `contributingMatchCount` for current `TeamCandidateScorer` outputs. v0.9.3 must still include it in the comparator to preserve the explicit ordering contract.

Do not order candidates by:

* team name;
* roster order as semantic evidence;
* OCR confidence;
* placement;
* kills;
* previous matches;
* tournament standings;
* random order;
* database order; or
* alphabetical order.

Ranks are assigned after sorting.

Rules:

* returned suggestions have sequential ranks starting at `1`;
* ranks reflect presentation order only;
* ranks are not assignment decisions;
* equal scores still receive deterministic sequential ranks because the returned list must be stable; and
* tie evidence is not resolved into assignment authority.

## 12. Low-Confidence and Zero-Confidence Suggestions

Low-confidence candidates may still appear in top-three suggestions if they are among the strongest available.

Zero-confidence candidates may appear only when there are fewer than three stronger non-zero candidates and enough teams are supplied. A zero-confidence suggestion does not authorize a match.

v0.9.4 and v0.9.5 decide tiers and safety gates later. v0.9.3 must not add thresholds or hide low-confidence teams unless a later repository authority explicitly changes this decision.

## 13. Duplicate and Invalid Candidate Input

Duplicate candidate team slots are structurally invalid for a single tournament candidate set.

Rules:

* throw `IllegalArgumentException` when the same `teamSlot` appears more than once;
* do not silently merge duplicate inputs;
* do not silently deduplicate duplicate inputs;
* do not pick one duplicate input;
* do not treat duplicate candidate input as duplicate assignment across OCR rows; and
* do not resolve duplicate team assignment in v0.9.3.

Rationale:

* silently merging duplicate team inputs could hide roster-state corruption;
* v0.9.3 must produce deterministic suggestions from valid candidate sets only; and
* duplicate team assignment across OCR rows remains later scope.

Invalid candidate team slots must propagate the `IllegalArgumentException` surfaced by `TeamCandidateScorer`. The provider must not swallow that exception and must not convert invalid slots into empty suggestions.

## 14. Result Evidence Boundary

Each suggestion preserves the full `TeamCandidateScore` returned by v0.9.2.

The result exposes:

* detected player count;
* evaluated candidate count;
* rank;
* candidate team slot through `TeamCandidateScore.candidateTeamSlot`;
* confidence score;
* contributing-player evidence;
* v0.9.1 pairwise assessment evidence inside `TeamCandidatePlayerMatch`; and
* deterministic suggestion order.

The result must not expose the full unsorted evaluated candidate list. Returning only the selected top-three suggestions keeps v0.9.3 small and stable.

## 15. Score Interpretation

v0.9.3 suggestions are advisory evidence only.

Do not add:

* `isMatch`;
* `isAutomatic`;
* `requiresConfirmation`;
* `confidenceTier`;
* `AUTO`;
* `CONFIRMATION_REQUIRED`;
* `MANUAL`;
* candidate lead;
* assignment result;
* conflict result; or
* final selected team.

The canonical `90`, `75`, three-of-four-player, and 10-point-lead assignment rules belong to v0.9.4 and v0.9.5. A rank-1 suggestion does not authorize assignment.

## 16. Complexity and Implementation Constraints

Implementation must be deterministic and bounded for one row and the fixed 12-team tournament structure.

Required constraints:

* no Android framework dependency;
* no network access;
* no database access;
* no persistence;
* no logging of OCR or roster names;
* no mutable global state;
* thread-safe pure suggestion generation;
* no external matching dependency;
* no external scoring dependency;
* no recursion that risks stack growth; and
* no third-party dependency.

The expected maximum candidate set is 12 teams, each with four to six roster names. Larger supplied lists must remain deterministic but must not broaden v0.9.3 into match-wide assignment orchestration.

## 17. Synthetic Unit-Test Matrix

Later implementation tests must use only synthetic names and values. They must not include real player names, screenshots, OCR payloads, private paths, or personal data.

| Case | Detected inputs | Candidate teams | Expected returned ranks and team slots |
| --- | --- | --- | --- |
| No candidate teams returns empty suggestions | `["Unit7", "Nova", "Rin", "Kai"]` | `[]` | no suggestions; `evaluatedCandidateCount = 0` |
| One candidate returns one suggestion with rank 1 | `["Unit7", "Nova", "Rin", "Kai"]` | slot `1`: `["Unit7", "Nova", "Rin", "Kai"]` | rank `1` -> slot `1` |
| Two candidates return two suggestions | `["Unit7", "Nova", "Rin", "Kai"]` | slot `1`: four exact; slot `2`: three exact and one missing | rank `1` -> slot `1`; rank `2` -> slot `2` |
| Three candidates return three suggestions | `["Unit7", "Nova", "Rin", "Kai"]` | slot `1`: four exact; slot `2`: three exact; slot `3`: two exact | ranks `1..3` -> slots `[1, 2, 3]` |
| More than three candidates returns exactly three suggestions | `["Unit7", "Nova", "Rin", "Kai"]` | slot `1`: four exact; slot `2`: three exact; slot `3`: two exact; slot `4`: one exact | ranks `1..3` -> slots `[1, 2, 3]`; slot `4` omitted |
| Candidates ordered by higher confidence | `["Unit7", "Nova", "Rin", "Kai"]` | slot `2`: three exact, confidence `92`; slot `1`: four exact, confidence `100` | rank `1` -> slot `1`; rank `2` -> slot `2` |
| Tie broken by higher contributing match count | synthetic names producing slot `2`: confidence `77`, `contributingMatchCount = 2`; slot `1`: confidence `77`, `contributingMatchCount = 1` | both candidates otherwise valid | rank `1` -> slot `2`; rank `2` -> slot `1` |
| Tie broken by higher average matched player score | synthetic names producing equal confidence and equal contributing count, with slot `2` average greater than slot `1` by integer-truncation tie | both candidates otherwise valid | rank `1` -> slot `2`; rank `2` -> slot `1` |
| Coverage comparator is retained after average | construct or factor the ordering test so candidate scores are equal through average and contribution fields while coverage differs, if implementation exposes an ordering seam; otherwise assert the field is present and ordered after average in the comparator because current v0.9.2 scorer derives coverage from contributing count | current scorer outputs make isolated coverage tie-break redundant | expected ordering uses higher `coverageScore` before lower slot whenever such score evidence exists |
| Final tie broken by lower team slot | `["Unit7", "Nova", "Rin", "Kai"]` | slot `2`: four exact; slot `1`: four exact | rank `1` -> slot `1`; rank `2` -> slot `2` |
| Rank assignment remains sequential | any three selected candidates after sorting | three returned suggestions | ranks exactly `[1, 2, 3]` with no gaps |
| Low-confidence candidates may appear when among top three | `["abcd"]` | slot `1`: one contribution at score `75`; slot `2`: zero confidence | rank `1` -> slot `1`; rank `2` -> slot `2`; low confidence is not hidden |
| Zero-confidence candidates may appear only when needed | `["Unit7"]` | slot `1`: exact one-player match; slot `2`: empty roster; slot `3`: no matching roster names | rank `1` -> slot `1`; ranks `2` and `3` may be zero-confidence slots ordered by lower slot |
| Duplicate candidate team slot throws | any detected list | slot `1` supplied twice with any roster lists | throws `IllegalArgumentException` before returning suggestions |
| Invalid candidate slot propagates | any detected list | slot `0` or `13` with any roster list | throws `IllegalArgumentException` from candidate scoring behavior |
| Candidate with empty roster is evaluated | `["Unit7", "Nova", "Rin", "Kai"]` | slot `1`: empty roster | rank `1` -> slot `1`; `confidenceScore = 0`; `evaluatedCandidateCount = 1` |
| Detected list remains unchanged | mutable copy of `["Unit7", "Nova", "Rin", "Kai"]` | one or more matching candidate teams | input detected list contents unchanged after call |
| Candidate roster lists remain unchanged | any detected list | mutable roster lists in candidate inputs | candidate roster list contents unchanged after call |
| Deterministic repeated calls | same detected list | same candidate teams | identical `TopTeamCandidateSuggestions` on every call |
| No thresholds, assignment, or candidate lead fields exist | any valid inputs | enough candidate teams for suggestions | result types expose no tier, threshold, lead, assignment, UI, persistence, or finalization fields |
| No speculative OCR confusion mappings beyond v0.9.0/v0.9.1 | `["5S", "Nova", "Rin", "Kai"]` | slot `1`: `["55", "Nova", "Rin", "Kai"]`; slot `2`: lower evidence | slot `1` ranks only from approved v0.9.0/v0.9.1 behavior; `5S` vs `55` does not become normalized exact |

Tests must also assert:

* `detectedPlayerCount`;
* `evaluatedCandidateCount`;
* selected suggestion count is at most three;
* selected ranks are sequential from `1`;
* returned slots match sorted order;
* each suggestion preserves its `TeamCandidateScore`;
* no duplicate candidate team slots are accepted;
* empty candidate input returns an empty suggestion list; and
* no result includes threshold, assignment, candidate lead, UI, persistence, scoring, or finalization state.

## 18. Compatibility Requirements

v0.9.3 must not modify or break:

* `PlayerNameComparisonNormalizer`;
* `PlayerNameSimilarityMatcher`;
* `TeamCandidateScorer`;
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

Top-three suggestion generation is local pure computation. It must not log raw OCR names, roster names, normalized names, pairwise scores, candidate scores, suggestion ranks, screenshots, private paths, raw OCR payloads, credentials, tokens, or backend details.

The future implementation must not upload suggestion evidence, create persistence records, add analytics, add crash-report metadata containing names, or change screenshot handling. Tests and documentation must use synthetic values only.

## 20. Out of Scope

v0.9.3 excludes:

* confidence tiers;
* automatic team assignment;
* confirmation tiers;
* candidate lead calculation;
* 10-point lead enforcement;
* unique-team assignment across a match;
* duplicate team resolution across OCR rows;
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

The non-numbered roadmap item `v0.9.x - Roster OCR Review and Correction` remains separate future scope.

## 21. Acceptance Criteria

A later v0.9.3 implementation is acceptable only when:

* it uses v0.9.2 `TeamCandidateScorer` internally;
* it evaluates supplied candidate teams for exactly one OCR row;
* it returns at most three deterministic suggestions;
* it applies the approved candidate ordering rule;
* it assigns sequential presentation ranks;
* it preserves `TeamCandidateScore` evidence;
* it rejects duplicate candidate team slots deterministically;
* it remains advisory and does not assign, threshold, persist, display UI, score tournament points, or finalize matches;
* comprehensive synthetic unit tests pass; and
* existing behavior remains unchanged.

## 22. Deferred Decisions

The following remain deferred to later approved versions:

* confidence tier interpretation;
* automatic-assignment conditions;
* candidate lead calculation;
* 10-point lead enforcement;
* match-wide unique-team assignment safety;
* duplicate-team and cross-row conflict handling;
* review UI presentation of suggestions and ambiguity;
* manual correction workflows;
* persistence of OCR review state, raw/corrected data, candidate evidence, or confirmed assignments;
* safe OCR-assisted finalization;
* roster OCR review and correction;
* team-name OCR extraction;
* fifth/sixth-player OCR extraction; and
* Phase 12 real OCR acceptance evaluation.

## 23. Implementation Handoff

After this decision document is reviewed, merged, and followed by explicit user approval, the implementation task may add only the proposed pure `TopTeamCandidateSuggestionProvider`, `TeamCandidateRosterInput`, `TopTeamCandidateSuggestions`, `TopTeamCandidateSuggestion`, deterministic sorting/top-three selection, duplicate-candidate validation, and focused synthetic unit tests.

The implementation must not modify `PlayerNameComparisonNormalizer`, `PlayerNameSimilarityMatcher`, `TeamCandidateScorer`, `RosterNameNormalizer`, existing roster validation, Phase 8 OCR processing, persistence, UI, confidence tiers, assignments, corrections, scoring, navigation, or finalization.
