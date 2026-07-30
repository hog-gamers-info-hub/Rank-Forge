# v0.9.6 - OCR Review Interface Decisions

## 1. Title and Status

**Phase:** 9 - Team Matching and Manual Correction
**Version:** v0.9.6 - OCR Review Interface
**Status:** Approved documentation decision gate; no implementation is authorized by this document alone.

Phase 8 is complete and closed. Phase 9 v0.9.0 Text Normalization, v0.9.1 Player Similarity Matching, v0.9.2 Team Candidate Scoring, v0.9.3 Top-Three Suggestions, v0.9.4 Confidence Thresholds, and v0.9.5 Assignment Safety Rules are implemented, verified, merged, and protected. This document defines only the next OCR review presentation boundary that a later approved v0.9.6 implementation may add.

## 2. Decision Summary

v0.9.6 will add a read-only Android review interface for one match's already-computed OCR, matching, confidence, and assignment-safety evidence.

The interface must:

* show the fixed 12 scoreboard rows from the match screenshot review evidence;
* preserve and display parsed placement, player-name, and kill evidence from Phase 8;
* show top-three team suggestions from v0.9.3 when available;
* show confidence-tier evidence from v0.9.4 when available;
* show assignment-safety status and reasons from v0.9.5 when available;
* make warning and blocking states visible to the operator; and
* remain read-only and advisory.

It must not run OCR, parse text, normalize names, score players, score teams, generate suggestions, classify confidence, evaluate safety, edit values, assign teams, persist review state, finalize matches, change scoring, change navigation outside the approved route boundary, or alter existing manual match workflows.

## 3. Repository Context

Phase 8 provides the OCR evidence boundary. `OcrFailureAnalyzer` returns `OcrFailureAnalysisResult`, ordered `OcrReviewRow` values, and field-level `OcrReviewField` markers for placement, player name, and kill fields. Approved statuses are `ACCEPTED`, `MISSING`, `INVALID`, `AMBIGUOUS`, `DUPLICATE`, `UNSUPPORTED`, and `UNCERTAIN`. Approved severities are `BLOCKING`, `WARNING`, and `INFORMATIONAL`.

Phase 8 parsing outputs remain distinct:

* `ParsedPlacementRow` preserves expected placement, panel, row index, parse status, detected placement value, and placement OCR evidence.
* `ParsedPlayerNameRow` preserves expected placement, panel, row index, parse status, detected player name, failure, and player-name OCR evidence.
* `ParsedKillRow` preserves expected placement, panel, row index, parse status, detected kill value, failure, and kill OCR evidence.

Phase 9 v0.9.0 through v0.9.5 provide comparison and assignment-safety evidence only. They do not mutate OCR data, write assignments, persist review records, or finalize matches.

The current Android presentation layer uses type-safe serializable navigation destinations under `presentation.navigation`, route-level composables, ViewModels, immutable UI-state data classes, string resources, Material 3 components, and stable Compose test tags.

## 4. Scope

The v0.9.6 scope is one read-only OCR review screen for one match.

The future implementation may add:

* `MatchOcrReviewDestination`;
* `MatchOcrReviewRoute`;
* `MatchOcrReviewScreen`;
* `MatchOcrReviewViewModel`;
* `MatchOcrReviewUiState`;
* row and field UI-state models needed to render review evidence;
* string resources;
* stable test-tag constants; and
* focused ViewModel and Compose UI tests.

The future implementation must not add manual field correction, editable inputs, persistence, migrations, Supabase changes, scoring changes, match finalization behavior, actual assignment writes, OCR execution, parser execution, matching execution, or roster OCR review behavior.

## 5. Terminology and Data Boundaries

The following values remain distinct:

1. original screenshot reference or metadata;
2. raw OCR text and hierarchy;
3. parsed OCR placement, player-name, and kill values;
4. Phase 8 review markers and severities;
5. normalized comparison-only player-name values;
6. player similarity assessments;
7. candidate team scores;
8. top-three advisory suggestions;
9. confidence-tier assessments;
10. assignment-safety assessments;
11. proposed team slot evidence;
12. later operator correction;
13. later confirmed match result values; and
14. later finalized match result values.

v0.9.6 consumes these values for display only. It must not convert displayed evidence into assigned, corrected, persisted, confirmed, or finalized match state.

## 6. Review Scope

The screen must review exactly one match at a time for one tournament context.

The review surface must render all 12 expected fixed scoreboard rows. Rows must be ordered by fixed scoreboard order using the existing row evidence from Phase 8. If matching, confidence, or safety evidence is missing for a row, the row remains visible and shows the missing downstream evidence as review-required or unavailable instead of being dropped.

The review is limited to:

* placement OCR evidence;
* player-name OCR evidence;
* kill OCR evidence;
* row-level OCR review markers;
* top-three team suggestion evidence;
* confidence tier and reason evidence;
* assignment-safety status, proposed team slot, and safety reasons; and
* summary counts derived from already-computed evidence.

The review must not include roster OCR review, team-name OCR extraction, fifth/sixth player extraction, standings comparison, scoring recommendations, finalization approval, or correction editing.

## 7. Route and Screen Boundary

The proposed navigation destination is:

```kotlin
@Serializable
data class MatchOcrReviewDestination(
    val tournamentId: String,
    val matchId: String,
)
```

The proposed route and screen names are:

```kotlin
MatchOcrReviewRoute
MatchOcrReviewScreen
MatchOcrReviewViewModel
MatchOcrReviewUiState
```

The route must follow the current `RankForgeNavHost` pattern: accept `tournamentId` and `matchId`, obtain or receive a `MatchOcrReviewViewModel`, load the match OCR review state, collect immutable UI state with lifecycle awareness, and pass callback lambdas into the screen.

The route may provide only read-only navigation callbacks:

* back to the parent match or tournament details surface; and
* optional navigation to a later approved correction screen only after that later version explicitly authorizes correction behavior.

If v0.9.6 is implemented before v0.9.7 manual field correction exists, the only operator action must be leaving the screen. Do not wire correction, confirmation, assignment, finalization, retry OCR, screenshot replacement, or persistence actions into the v0.9.6 OCR review route.

## 8. Input Data Contract

The ViewModel input must identify the review target by:

* `tournamentId`;
* `matchId`; and
* already-associated screenshot or OCR processing context when available.

The UI-state mapping layer may consume already-computed values from approved boundaries:

* `OcrFailureAnalysisResult`;
* ordered `OcrReviewRow` values and their `OcrReviewField` values;
* parsed placement rows;
* parsed player-name rows;
* parsed kill rows;
* `TopTeamCandidateSuggestions`;
* `TeamMatchConfidenceAssessment`; and
* `TeamAssignmentSafetyResult`.

All matching and safety evidence must be supplied to the review interface as existing data. v0.9.6 must not call OCR recognizers, preprocessors, raw extractors, parsers, `PlayerNameComparisonNormalizer`, `PlayerNameSimilarityMatcher`, `TeamCandidateScorer`, `TopTeamCandidateSuggestionProvider`, `TeamMatchConfidenceTierClassifier`, or `TeamAssignmentSafetyEvaluator` from the composable screen.

If the ViewModel later orchestrates loading precomputed evidence, it must preserve the already-approved computation boundaries and expose only immutable UI state to Compose. Any orchestration that creates new OCR/matching data remains separate implementation scope and is not authorized by this document alone.

## 9. Row Content Contract

Each of the 12 row UI-state items must include:

* stable row index in `0..11`;
* expected placement id from the fixed scoreboard layout;
* panel identifier when available;
* parsed placement display value or unavailable marker;
* placement review status, severity, and reason;
* parsed player-name display value or unavailable marker;
* player-name review status, severity, and reason;
* parsed kill display value or unavailable marker;
* kill review status, severity, and reason;
* top-three suggestion list, preserving rank order when available;
* rank-1 proposed team slot when available;
* rank-1 confidence score when available;
* contributing match count and player-match evidence summary when available;
* confidence tier and reason when available;
* assignment-safety status and reasons when available; and
* row-level display severity derived from the most severe available row evidence.

Top-three row content must show at most three suggestions. Suggestion rows must make rank, candidate team slot, confidence score, contributing match count, and coverage score visible. Lower-ranked suggestions remain evidence only and must not become assignment choices in v0.9.6.

The row UI state must not contain editable text fields, corrected values, committed team assignment fields, finalized result fields, score-calculation fields, or persistence DTOs.

## 10. Warning and Blocking Semantics

The screen must clearly distinguish blocking, warning, and informational evidence.

Blocking OCR evidence includes:

* unsupported layout or input;
* preprocessing failure;
* raw extraction failure;
* empty OCR output that leaves required fields missing;
* missing required placement, player-name, or kill fields;
* invalid required placement, player-name, or kill fields;
* ambiguous required placement, player-name, or kill fields; and
* duplicate required placement or kill fields.

Blocking matching or safety evidence includes:

* no usable team suggestion for a row;
* `MANUAL_REQUIRED` confidence tier;
* `MANUAL_REQUIRED` assignment-safety status;
* malformed or unavailable confidence/safety evidence needed to explain the row; and
* any row evidence that prevents later automatic acceptance.

Warning evidence includes:

* `UNCERTAIN` OCR fields with warning severity;
* `CONFIRMATION_REQUIRED` confidence tier;
* `REVIEW_REQUIRED` assignment-safety status;
* insufficient player-match count;
* insufficient candidate lead;
* duplicate team candidate conflicts; and
* lower-ranked suggestions that make the row ambiguous enough for operator review.

Informational evidence includes accepted fields, available OCR metadata, and safe automatic assignment evidence. Informational evidence must remain visible enough to explain why a row appears safe.

v0.9.6 warning and blocking labels are display semantics only. They do not finalize, assign, reject, correct, persist, or score match data. Later finalization gates remain responsible for enforcement.

## 11. Read-Only Interaction Contract

v0.9.6 must be read-only.

Allowed interactions:

* open the review screen for one match;
* scroll through all rows;
* expand or collapse details if needed for readability;
* inspect suggestion, confidence, safety, and OCR-field details; and
* navigate back.

Disallowed interactions:

* editing placement, player name, kill, team, roster, or score values;
* choosing or confirming a team suggestion;
* accepting an automatic assignment;
* writing draft match rows;
* starting finalization;
* changing screenshot links;
* rerunning OCR;
* uploading evidence;
* synchronizing review evidence;
* clearing warnings or blockers; and
* persisting review-state acknowledgements.

If a future implementation needs a button leading to manual correction, that button must be deferred until the v0.9.7 correction contract is approved.

## 12. UI State Contract

The screen state must be immutable and deterministic. The recommended shape is a single `MatchOcrReviewUiState` with explicit high-level states or equivalent fields:

* loading;
* ready;
* empty or no OCR evidence;
* match not found;
* OCR evidence unavailable;
* error.

The ready state must include:

* `tournamentId`;
* `matchId`;
* optional match number or display label;
* exactly 12 row UI states;
* blocking row count;
* warning row count;
* safe row count;
* manual-required row count;
* review-required row count; and
* whether any evidence required for later automatic acceptance is unavailable.

Derived counts must be computed from already-loaded evidence. The composable must render state and invoke callbacks only. It must not perform domain evaluation, persistence, OCR processing, matching, scoring, or finalization logic.

## 13. Evidence Preservation

The UI-state mapper must preserve original display values separately from normalized comparison values and scored evidence.

Rules:

* raw OCR text remains raw OCR evidence;
* parsed OCR display values remain parsed evidence;
* normalized player-name values are not shown as replacements for original OCR or roster display names;
* candidate scores and player-match evidence remain explanatory evidence;
* confidence tiers remain advisory labels;
* assignment-safety statuses remain safety labels;
* proposed team slots remain proposed evidence only; and
* no displayed value becomes corrected or confirmed because it appeared on the review screen.

The interface must never hide original OCR evidence merely because a suggestion looks safe.

## 14. Empty and Error State Rules

Empty and error handling must be deterministic:

* no linked screenshot or OCR context: show an empty/no OCR evidence state with back navigation;
* OCR preprocessing or extraction failure: show blocking review state when a failure analysis result exists, otherwise show OCR evidence unavailable;
* parser output unavailable with raw extraction present: show warning or blocking rows according to existing `OcrFailureAnalysisResult`;
* fewer than 12 parsed rows: show all 12 rows and mark missing row evidence as blocking or unavailable;
* more than 12 row evidence items: treat as invalid evidence for display and show an error or blocking unavailable state rather than silently truncating;
* missing matching evidence for one row: keep that row visible and mark matching evidence unavailable;
* missing match or tournament: show not-found state;
* loading failure: show error state; and
* repeated loads of the same available evidence must produce stable UI state.

The screen must not crash, hide unresolved rows, or present unresolved rows as safe.

## 15. Accessibility and Test Tags

The screen must use project string resources for visible text and content descriptions. Do not hard-code user-facing copy in composables.

Warnings and blockers must not rely on color alone. They must include text labels and accessible descriptions for icons or status indicators.

Stable test tags must be provided for:

* OCR review screen root;
* loading state;
* empty state;
* error state;
* row list;
* each row by zero-based row index;
* each row placement field;
* each row player-name field;
* each row kill field;
* each row suggestions area;
* each row confidence tier;
* each row safety status;
* each row warning indicator; and
* each row blocking indicator.

Recommended tag names:

```text
match_ocr_review_screen
match_ocr_review_loading
match_ocr_review_empty
match_ocr_review_error
match_ocr_review_row_list
match_ocr_review_row_{rowIndex}
match_ocr_review_row_{rowIndex}_placement
match_ocr_review_row_{rowIndex}_player_name
match_ocr_review_row_{rowIndex}_kills
match_ocr_review_row_{rowIndex}_suggestions
match_ocr_review_row_{rowIndex}_confidence
match_ocr_review_row_{rowIndex}_safety
match_ocr_review_row_{rowIndex}_warning
match_ocr_review_row_{rowIndex}_blocking
```

## 16. Testing Strategy

Implementation tests must use synthetic data only. They must not include real player names, real screenshots, private paths, OCR payloads copied from real users, credentials, tokens, network calls, Supabase calls, Room migrations, ML Kit execution, or device image assets.

Focused ViewModel or UI-state mapper tests must verify:

* loading state;
* not-found state;
* empty/no OCR evidence state;
* error state;
* ready state contains exactly 12 ordered rows;
* accepted OCR fields map to informational display state;
* missing, invalid, ambiguous, duplicate, unsupported, and uncertain OCR fields map to visible review labels;
* top-three suggestions preserve rank order and show at most three items;
* confidence tiers map to visible row labels;
* assignment-safety statuses and reasons map to visible row labels;
* proposed team slot remains evidence, not committed assignment;
* warning and blocking counts are deterministic; and
* repeated mapping of equivalent evidence is idempotent.

Focused Compose tests must verify:

* loading, empty, error, and ready rendering;
* all 12 rows are discoverable by stable test tags;
* row fields display unavailable markers when evidence is missing;
* warning and blocking labels are visible as text, not only color;
* top-three suggestion rows display rank, slot, confidence, and contribution evidence;
* read-only behavior exposes no editable fields or assignment/finalization controls; and
* back navigation callback is invoked.

Connected-device verification remains appropriate for a later UI implementation PR because this is a Compose screen. This documentation-only decision does not authorize running connected tests now.

## 17. Compatibility Requirements

v0.9.6 must not modify or break:

* Phase 8 OCR preprocessing, extraction, parsing, failure analysis, or real-evaluation helpers;
* `PlayerNameComparisonNormalizer`;
* `PlayerNameSimilarityMatcher`;
* `TeamCandidateScorer`;
* `TopTeamCandidateSuggestionProvider`;
* `TeamMatchConfidenceTierClassifier`;
* `TeamAssignmentSafetyEvaluator`;
* existing `RosterNameNormalizer`;
* roster creation, editing, review, validation, and screenshot intake;
* tournament management;
* fixed 12-team-slot behavior;
* manual placement and kill entry;
* manual match review;
* match correction workflows;
* finalization protection;
* scoring and standings;
* Room persistence;
* Supabase synchronization;
* authentication; or
* cloud conflict resolution.

## 18. Security and Privacy

The review interface must not log raw OCR names, roster names, normalized names, pairwise scores, candidate scores, suggestion ranks, confidence tiers, safety results, screenshots, local file paths, private URIs, raw OCR payloads, credentials, tokens, or backend details.

The future implementation must not upload OCR review evidence, create analytics, add crash-report metadata containing names or screenshot details, persist OCR evidence, expose private screenshot paths in UI, or change screenshot storage behavior.

Documentation and tests must use synthetic names and synthetic evidence only.

## 19. Out of Scope

v0.9.6 excludes:

* OCR execution;
* image preprocessing;
* raw OCR extraction;
* placement parsing;
* player-name parsing;
* kill parsing;
* player-name normalization;
* player similarity matching;
* team candidate scoring;
* top-three suggestion generation;
* confidence-tier classification;
* assignment-safety evaluation;
* manual field correction;
* confirmation workflow;
* actual team assignment writes;
* draft match row writes;
* review-state persistence;
* Room migrations;
* Supabase changes;
* cloud synchronization changes;
* match finalization changes;
* scoring changes;
* roster OCR review and correction;
* team-name OCR extraction;
* fifth/sixth player OCR extraction;
* public sharing; and
* exports.

The non-numbered roadmap item `v0.9.x - Roster OCR Review and Correction` remains separate future scope.

## 20. Acceptance Criteria

A later v0.9.6 implementation is acceptable only when:

* it adds a read-only OCR review interface for one match;
* it follows the current type-safe destination, Route, ViewModel, UI-state, string-resource, and test-tag conventions;
* it renders all 12 fixed scoreboard rows;
* it preserves original OCR, parsed, suggestion, confidence, and safety evidence as display evidence only;
* it clearly distinguishes blocking, warning, and informational states;
* it handles loading, empty, not-found, unavailable-evidence, and error states deterministically;
* it exposes no editable values, assignment controls, finalization controls, OCR rerun controls, persistence acknowledgements, or scoring changes;
* focused synthetic ViewModel and Compose tests cover the documented states; and
* existing behavior remains unchanged.

## 21. Deferred Decisions

The following remain deferred to later approved versions:

* manual correction UI and editable field behavior;
* confirmation workflow for suggested teams;
* actual team assignment writes;
* persistence of OCR review state, raw OCR evidence, corrected values, confirmed values, candidate evidence, tier evidence, or safety evidence;
* safe OCR-assisted finalization;
* final validation against placement, kill, and complete 12-row result data;
* duplicate-placement correction;
* post-review confirmation state;
* roster OCR review and correction;
* team-name OCR extraction;
* fifth/sixth-player OCR extraction; and
* Phase 12 real OCR acceptance evaluation.

## 22. Implementation Handoff

After this decision document is reviewed, merged, and followed by explicit user approval, the implementation task may add only the read-only `MatchOcrReviewDestination`, route, screen, ViewModel, immutable UI-state models, string resources, test tags, and focused synthetic ViewModel/Compose tests needed to render existing OCR, suggestion, confidence, and safety evidence.

The implementation must not modify Phase 8 OCR processing, v0.9.0 through v0.9.5 matching and safety domain behavior, roster normalization, Room, Supabase, scoring, finalization, correction workflows, screenshot storage, existing manual match entry, or any unrelated UI.
