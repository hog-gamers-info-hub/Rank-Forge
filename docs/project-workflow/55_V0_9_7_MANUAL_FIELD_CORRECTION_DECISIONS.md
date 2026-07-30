# Phase 9 v0.9.7 Manual Field Correction Decisions

## 1. Title and Status

This document records the approved decision gate for Phase 9 v0.9.7 Manual Field Correction.

Status: documentation decision only. It creates no production code, tests, persistence schema, navigation entry point, roadmap update, build configuration, Supabase change, or Room migration.

v0.9.7 depends on the merged v0.9.6 OCR Review Interface and the existing manual match-result validation rules. It is not a finalization, scoring, persistence, OCR, parsing, matching, synchronization, export, or roster-management version.

## 2. Decision Summary

v0.9.7 is the manual correction layer for the v0.9.6 OCR review interface.

The correction workflow is approved as an in-review draft workflow for already-present OCR review evidence. It may let the user correct:

- scoreboard-row placement draft values;
- scoreboard-row kill draft values;
- assigned team-slot draft values.

Detected player-name OCR evidence remains view-only in v0.9.7. The current repository supports safe display of detected player-name evidence and matching suggestions, but it does not yet provide an approved scoreboard player-name correction draft model that can preserve original and corrected name evidence without conflating roster correction with match-result correction.

v0.9.7 must not:

- rerun OCR;
- parse screenshots;
- recompute player similarity;
- recompute team candidate scoring;
- recompute top-three suggestions;
- recompute confidence tiers;
- recompute assignment safety automatically from edits;
- persist corrected data as canonical original/corrected evidence;
- finalize matches;
- update standings;
- upload or synchronize corrections;
- change roster records.

Safe finalization remains v0.9.8. Original/corrected data preservation remains v0.9.9.

## 3. Repository Context

The repository already separates the relevant concerns:

- Phase 8 OCR parsing and failure handling preserve typed evidence and review markers without UI, persistence, scoring, finalization, or correction behavior.
- v0.9.0 through v0.9.5 provide comparison normalization, player similarity, candidate scoring, top-three suggestions, confidence tiers, and assignment safety as advisory evidence.
- v0.9.6 adds `MatchOcrReviewDestination`, `MatchOcrReviewRoute`, `MatchOcrReviewScreen`, `MatchOcrReviewViewModel`, `MatchOcrReviewUiState`, and `MatchOcrReviewRowUiState` for read-only display of one match's OCR review evidence.
- Existing manual match validation is represented by `ValidateMatchResultUseCase`, `MatchResultRowInput`, and `MatchResultValidationError`.
- Existing finalized-match correction is represented separately by `MatchCorrectionDestination`, `MatchCorrectionRoute`, `MatchCorrectionViewModel`, `MatchCorrectionUiState`, and correction-history persistence. That workflow is for correcting finalized match data and must not be reused as v0.9.7 OCR correction persistence.

No reviewed repository evidence conflicts with the v0.9.7 decision boundary.

## 4. Scope

In scope for v0.9.7:

- one match scoreboard OCR review result at a time;
- all 12 fixed scoreboard rows;
- editable draft placement values;
- editable draft kill values;
- editable draft assigned team-slot values;
- validation and severity derived from the draft values;
- dirty tracking and reset behavior;
- continued display of original OCR, matching, confidence, suggestion, and safety evidence.

Out of scope for v0.9.7:

- multiple-match editing;
- roster editing;
- OCR execution;
- screenshot parsing;
- matching recomputation;
- confidence or safety recomputation;
- persistence of canonical corrected evidence;
- finalization;
- scoring;
- standings;
- synchronization;
- exports.

## 5. Terminology and Data Boundaries

Original OCR evidence means the raw or parsed OCR-derived display evidence produced by approved Phase 8 and later review models.

Parsed OCR value means the numeric or text value derived from OCR before manual correction.

Suggestion evidence means the v0.9.2 and v0.9.3 team-candidate scoring and top-three suggestion evidence.

Confidence evidence means the v0.9.4 confidence assessment evidence.

Safety evidence means the v0.9.5 assignment safety result.

Correction draft means the v0.9.7 user-editable placement, kill, or assigned team-slot value held for review before safe finalization exists.

Finalized match result means the later confirmed result that can affect scoring and standings. v0.9.7 does not produce this result.

Original OCR evidence, parsed OCR values, suggestion/confidence/safety evidence, correction drafts, validation state, and finalized match results must remain distinct.

## 6. Correction Scope

The correction interface applies to exactly one match scoreboard OCR review result at a time.

It must support 12 fixed rows. Each row may carry:

- original OCR placement evidence;
- corrected placement draft value;
- original OCR kill evidence;
- corrected kill draft value;
- original OCR player-name evidence for display;
- suggested team-slot evidence;
- corrected assigned team-slot draft value;
- warning labels derived from draft validity and original evidence;
- blocker labels derived from draft validity;
- dirty state.

The interface must not edit multiple matches together. It must not change roster team names or player names. Manual player-name and roster correction remain separate from scoreboard-result correction unless a later approved version defines a safe model.

## 7. UI and State Boundary

The repository-aligned implementation boundary for a later v0.9.7 code task is:

- extend `MatchOcrReviewScreen` only if correction behavior can be added without breaking v0.9.6 read-only display;
- extend `MatchOcrReviewViewModel` with correction draft state and reducer-style actions only if a safe evidence source is available;
- extend `MatchOcrReviewUiState.Ready` with correction-enabled fields while preserving the existing loading, empty, error, and ready states;
- extend `MatchOcrReviewRowUiState` with editable correction display state for placement, kills, and assigned team slot;
- introduce a UI/state-only `MatchOcrReviewCorrectionDraft` model if needed;
- introduce a UI/state-only `MatchOcrReviewRowCorrectionDraft` model if needed.

Approved action names for the later implementation are:

- `onPlacementChanged(rowIndex, value)`;
- `onKillsChanged(rowIndex, value)`;
- `onAssignedTeamSlotChanged(rowIndex, value)`;
- `onResetRowCorrection(rowIndex)`;
- `onResetAllCorrections()`.

The existing `MatchOcrReviewDestination` and `MatchOcrReviewRoute` should remain the preferred route boundary. A separate route is not approved unless implementation evidence shows that the existing route cannot safely host correction mode without changing v0.9.6 behavior.

## 8. Data Contract

v0.9.7 must keep a clear separation between:

- original OCR text and evidence;
- parsed OCR values;
- suggestion, confidence, and safety evidence;
- manual correction draft values;
- validation result for the correction draft;
- later finalized match result.

v0.9.7 must not collapse original and corrected data into one canonical field.

v0.9.7 may keep correction draft state in ViewModel and UI state only. It must not add Room entities, DAO methods, migrations, Supabase tables, Supabase RPCs, synchronization payloads, or cloud upload behavior.

Existing finalized-match correction history is not a v0.9.7 OCR correction-draft storage mechanism.

## 9. Placement Correction Rules

Corrected placement must follow existing match validation rules:

- placement must be numeric;
- placement must be an integer;
- placement must be in `1..12`;
- each placement may be used by only one row;
- all 12 rows must have valid placements before the correction draft can be considered complete;
- missing placements are blockers;
- invalid placements are blockers;
- duplicate placements are blockers.

v0.9.7 must not change the scoring table. It must not assign position points.

## 10. Kill Correction Rules

Corrected kills must follow existing kill validation rules:

- kills must be numeric;
- kills must be an integer;
- kills must be zero or greater;
- blank kills are blockers;
- negative kills are blockers;
- non-numeric kills are blockers.

v0.9.7 must not compute kill points.

## 11. Team-Slot Correction Rules

Corrected assigned team slot must follow the fixed 12-slot tournament model:

- team slot must be one of `1..12`;
- each team slot may be assigned to at most one scoreboard row;
- duplicate assigned team slots are blockers;
- missing assigned team slots are blockers when finalization requires assignment;
- suggestions remain evidence only;
- a user-selected assignment overrides the suggestion for draft display only;
- no automatic assignment is written by v0.9.7.

The team-slot correction draft maps one scoreboard row to one tournament team slot for review only. It must not edit roster team names, roster player names, player similarity inputs, or team-candidate evidence.

## 12. Draft Validity and Severity

The correction draft has one of three statuses:

- valid;
- has warnings;
- blocked.

Blocking conditions include:

- missing placement;
- invalid placement;
- duplicate placement;
- missing kills;
- invalid kills;
- missing team slot;
- invalid team slot;
- duplicate team slot;
- malformed row correction draft.

Warnings may include:

- user changed an OCR-derived placement;
- user changed OCR-derived kills;
- user changed the suggested team slot;
- row originally required manual review;
- confidence evidence was weak before correction;
- safety evidence was weak before correction.

Warnings do not block by themselves. Any blocker makes the draft blocked even if warnings are also present.

## 13. Dirty State Rules

A row is dirty when any correction draft value differs from its original parsed or suggested display value.

The screen is dirty when any row is dirty.

Reset row restores that row's draft values to the original parsed placement, original parsed kills, and original suggested or proposed team slot where available. If an original value is unavailable, reset restores the field to blank or no selection rather than inventing a value.

Reset all restores every row using the same rule.

If current navigation conventions support dirty-back confirmation, v0.9.7 may reuse the same pattern. If not, dirty-back confirmation remains an implementation decision or deferral and must not be improvised into persistence or finalization behavior.

## 14. Read/Edit Interaction Contract

Allowed v0.9.7 interactions:

- edit placement draft;
- edit kills draft;
- choose assigned team-slot draft;
- reset one row;
- reset all rows;
- navigate back;
- inspect original OCR, matching, confidence, suggestion, and safety evidence while editing.

Disallowed v0.9.7 interactions:

- save canonical corrected data;
- finalize match;
- update standings;
- run OCR retry;
- change screenshot;
- change roster;
- synchronize or upload corrections;
- edit original OCR evidence;
- erase original OCR evidence.

v0.9.7 may add an informational state explaining that corrections are not finalizable yet. It should not add a save or finalize button that implies persistence or official result authority.

## 15. Validation Message Contract

Field-level and row-level validation messages must identify:

- missing placement;
- invalid placement range;
- duplicate placement;
- missing kills;
- invalid kills;
- negative kills;
- missing team slot;
- invalid team slot;
- duplicate team slot.

Messages should be concise, deterministic, and associated with the affected row or field. They must not expose raw OCR text unnecessarily.

Existing validation-message conventions and string-resource usage should be reused in a later implementation. Hard-coded production UI strings are not approved.

## 16. Evidence Preservation

Manual corrections must preserve:

- original OCR display and evidence;
- original parsed value;
- suggestion evidence;
- confidence evidence;
- safety evidence;
- correction draft value;
- validation state.

Original evidence must remain inspectable while editing. A correction draft must never erase, replace, or silently reinterpret the original OCR evidence.

v0.9.7 must not persist original/corrected evidence as canonical history. That preservation model is v0.9.9 scope.

## 17. Empty and Error State Rules

Opening correction mode with no OCR evidence must show an empty or unavailable review state and must not create editable fabricated rows.

Opening correction mode with no rows must show an empty state.

Opening correction mode with fewer than 12 rows must show an error or blocking review state and must not crash.

Opening correction mode with more than 12 rows must show an error or blocking review state and must not silently drop rows.

Missing suggestions must leave placement and kill correction possible, but the team-slot draft must require manual selection before it can be complete.

Missing confidence or safety evidence must remain visible as unavailable evidence and may create warning or blocker labels. It must not trigger recomputation.

Malformed correction draft state must block completion and must not be coerced into a valid placement, kill, or team slot.

## 18. Accessibility and Test Tags

Editable placement fields must have accessible labels.

Editable kill fields must have accessible labels.

Team-slot selectors must have accessible labels.

Icons, if used, must have content descriptions when they convey state or actions.

Validation severity must not rely on color alone.

Stable test tags required for a later implementation:

- correction mode root: `match_ocr_review_correction_root`;
- placement input by row: `match_ocr_review_row_{rowIndex}_placement_input`;
- kills input by row: `match_ocr_review_row_{rowIndex}_kills_input`;
- team-slot input or selector by row: `match_ocr_review_row_{rowIndex}_team_slot_input`;
- row dirty marker: `match_ocr_review_row_{rowIndex}_dirty`;
- row blocker label: `match_ocr_review_row_{rowIndex}_blocker`;
- row warning label: `match_ocr_review_row_{rowIndex}_warning`;
- reset row action: `match_ocr_review_row_{rowIndex}_reset`;
- reset all action: `match_ocr_review_reset_all`.

The existing v0.9.6 read-only review test tags must remain stable unless a later implementation has an explicit compatibility reason to extend them.

## 19. Testing Strategy

A later v0.9.7 implementation must include:

- ViewModel or reducer tests for placement correction;
- ViewModel or reducer tests for kill correction;
- ViewModel or reducer tests for team-slot correction;
- duplicate placement validation tests;
- duplicate team-slot validation tests;
- invalid placement range tests;
- invalid kill validation tests;
- dirty row tracking tests;
- dirty screen tracking tests;
- reset row tests;
- reset all tests;
- blocker and warning count tests;
- tests proving no finalization, save, persistence, upload, synchronization, scoring, or standings action is exposed;
- Compose tests for correction inputs;
- Compose tests for validation labels;
- Compose tests for reset actions;
- navigation dirty-back tests only if dirty-back confirmation is implemented.

Connected-device tests are required for the implementation PR because v0.9.7 changes editable Compose UI behavior.

Tests must use synthetic sanitized data. Real screenshots, private OCR output, and real player names must not be committed without explicit privacy approval.

## 20. Compatibility Requirements

v0.9.7 must not modify or break:

- Phase 8 OCR extraction and parsing models;
- v0.9.0 text normalization;
- v0.9.1 similarity matching;
- v0.9.2 candidate scoring;
- v0.9.3 top-three suggestions;
- v0.9.4 confidence thresholds;
- v0.9.5 assignment safety;
- v0.9.6 read-only review display;
- existing manual match processing;
- match finalization;
- finalized-match correction workflow;
- scoring and standings;
- roster management;
- Room persistence;
- authentication;
- cloud synchronization;
- finalized-data protection.

## 21. Security and Privacy

v0.9.7 must not log raw OCR text, private screenshots, player names, secrets, tokens, Supabase keys, or private file paths.

The UI may display OCR evidence already approved for the local review workflow, but validation messages should not repeat raw OCR text unnecessarily.

No upload, synchronization, export, screenshot storage, Room schema, or Supabase schema behavior is approved by this version.

Automated tests must use synthetic sanitized fixtures unless a later explicit privacy approval allows representative local-only real-data evaluation.

## 22. Out of Scope

The following are explicitly out of scope:

- OCR retry orchestration;
- OCR execution;
- OCR parsing;
- player-name correction;
- roster correction;
- recomputing suggestions from corrected values;
- recomputing confidence;
- recomputing assignment safety;
- saving canonical corrected data;
- original/corrected data persistence history;
- Room migrations;
- Supabase changes;
- match finalization;
- scoring and standings changes;
- exports;
- public sharing.

The non-numbered roadmap item `v0.9.x - Roster OCR Review and Correction` remains separate future scope.

## 23. Acceptance Criteria

A later v0.9.7 implementation is accepted only when:

- it adds manual correction draft behavior for placement, kills, and assigned team slot;
- it validates placement, kills, and team-slot correction drafts;
- it detects missing, invalid, and duplicate placements;
- it detects missing, invalid, and duplicate team slots;
- it detects invalid kills;
- it tracks dirty row and screen state;
- it allows reset row and reset all;
- it preserves original OCR, matching, confidence, suggestion, and safety evidence;
- it does not save canonical corrected data;
- it does not finalize matches;
- it does not change scoring or standings;
- comprehensive unit and UI tests pass;
- connected-device verification passes;
- existing behavior remains unchanged.

## 24. Deferred Decisions

Deferred to v0.9.8:

- safe match finalization from reviewed and corrected OCR-assisted values;
- exact transition from correction draft to confirmed match-result input.

Deferred to v0.9.9:

- canonical original/corrected OCR data preservation;
- persisted correction history for OCR-assisted original and corrected values.

Deferred to a later `v0.9.x` roster OCR review and correction item:

- roster OCR review;
- roster player-name correction;
- roster team-name correction;
- confirmed roster replacement from OCR evidence.

Deferred unless a later implementation task explicitly approves it:

- dirty-back confirmation if the existing route cannot safely support it;
- a separate correction route;
- persisted draft recovery across process death.

## 25. Implementation Handoff

The next implementation task may add only the v0.9.7 manual correction draft behavior described here.

Implementation should begin from the existing v0.9.6 OCR review UI/state boundary, reuse existing validation concepts where applicable, preserve all original OCR/matching/safety evidence, expose only draft edit/reset behavior, and keep finalization, persistence, scoring, standings, synchronization, and roster changes out of scope.

If implementation evidence conflicts with this document, stop and resolve the decision document before coding.
