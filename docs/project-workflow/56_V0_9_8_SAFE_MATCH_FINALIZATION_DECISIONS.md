# Phase 9 v0.9.8 Safe Match Finalization Decisions

## 1. Title and Status

This document records the approved decision gate for Phase 9 v0.9.8 Safe Match Finalization.

Status: documentation decision only. It creates no production code, tests, persistence schema, navigation entry point, roadmap update, build configuration, Supabase change, Room migration, synchronization behavior, export behavior, or pull request.

v0.9.8 depends on the merged v0.9.6 OCR Review Interface, the merged v0.9.7 Manual Field Correction draft state, and the existing manual match validation and finalization rules.

## 2. Decision Summary

v0.9.8 is the safety gate that allows a corrected OCR review result to become an existing finalized match result only when all validation and finalized-data protection rules pass.

The approved outcome is narrow:

- corrected OCR review values may be mapped into the existing match-result/finalized-match model;
- existing validation and finalization paths must remain the authority;
- existing scoring engines compute placement points, kill points, totals, and standings from finalized match data;
- warnings require explicit operator confirmation before finalization;
- blockers prevent finalization;
- original OCR evidence and corrected-data history remain v0.9.9 scope.

v0.9.8 must not rerun OCR, parse screenshots, recompute matching, recompute suggestions, recompute confidence, recompute assignment safety, edit roster records, create original/corrected evidence history, add Supabase changes, sync, upload, export, or share.

## 3. Repository Context

The current repository already contains the core manual result finalization path:

- `ValidateMatchResultUseCase` validates 12 team-result rows, required placements, duplicate placements, required kills, invalid kills, duplicate teams, and missing team rows.
- `FinalizeMatchUseCase` accepts `FinalizeMatchInput(matchId, rows)` and delegates validation before calling `TournamentRepository.finalizeDraftMatch(...)`.
- `TournamentRepository.finalizeDraftMatch(...)` is the approved repository boundary for converting a draft match into a finalized match.
- `RoomTournamentRepository.finalizeDraftMatch(...)` persists finalized placement and kill rows in a transaction, changes match status from `DRAFT` to `FINALIZED`, clears draft values, and rejects missing, non-draft, or invalid data.
- `MatchReviewViewModel` and `MatchReviewScreen` already expose a confirmation-based manual finalization pattern for draft matches.
- Phase 4 scoring and standings derive from finalized matches only.
- Phase 5 local persistence already stores draft and finalized match results, correction history, and standings inputs without requiring a new schema for ordinary finalized match placements and kills.
- v0.9.7 provides `MatchOcrReviewCorrectionDraft`, row correction drafts, validation blockers/warnings, dirty state, reset behavior, and editable in-memory UI state.

The existing finalized-match correction workflow is for post-finalization corrections and must remain separate from OCR review finalization. v0.9.8 must not reuse post-finalization correction history as OCR original/corrected evidence history.

No reviewed repository evidence requires a Room migration for v0.9.8 as long as the implementation writes only the existing finalized match result model.

## 4. Scope

In scope:

- one match OCR review correction draft at a time;
- finalization readiness from the current v0.9.7 correction draft;
- validation of exactly 12 corrected rows;
- mapping assigned team slots, corrected placements, and corrected kills into existing finalization input rows;
- blocker and warning display before finalization;
- explicit confirmation before finalization;
- success and deterministic failure states;
- use of existing validation, repository, finalization, scoring, and standings boundaries.

Out of scope:

- OCR execution or retry;
- screenshot parsing;
- matching, suggestion, confidence, or safety recomputation;
- roster correction;
- original/corrected OCR evidence history;
- new scoring rules;
- direct standings mutation;
- Supabase changes;
- synchronization or upload;
- export or public sharing.

## 5. Terminology and Data Boundaries

Correction draft means the v0.9.7 in-memory draft containing corrected placement, kills, and assigned team-slot values for one OCR review result.

Finalization source means the current correction draft plus existing match, tournament, roster/team-slot, and validation state.

Finalized match result means the existing `Match` model with `MatchStatus.FINALIZED`, finalized `MatchPlacement` rows, and finalized `MatchKill` rows.

Warning means a non-blocking issue that still requires explicit operator acknowledgement before finalization, such as changed OCR values or weak evidence.

Blocker means a validation, state, or data-integrity issue that prevents finalization.

v0.9.8 must keep original OCR evidence, parsed OCR evidence, suggestions, confidence/safety evidence, correction draft values, finalized match values, and later original/corrected evidence history distinct.

## 6. Finalization Source

The finalization source is the current v0.9.7 correction draft for one match OCR review.

Required inputs:

- tournament id;
- match id;
- 12 row correction draft values;
- original OCR and review evidence for display;
- correction validation state;
- existing match draft/finalized state;
- existing tournament and team-slot roster state;
- existing match validation rules.

When a correction draft is present, finalization must use the correction draft values rather than raw OCR values.

When no correction draft is present, finalization from OCR review is blocked or unavailable. The existing manual match review finalization path remains separate and unchanged.

## 7. Finalization Preconditions

A match can be finalized from OCR review only when all of these are true:

- tournament exists;
- match exists;
- match belongs to the tournament context being reviewed;
- match is still draft/editable;
- match is not already finalized;
- OCR review context is available;
- correction draft is available;
- exactly 12 scoreboard rows are present;
- every row has a valid assigned team slot in `1..12`;
- assigned team slots are unique across `1..12`;
- every required team slot exists in the tournament roster/team-slot model;
- every row has a valid placement in `1..12`;
- placements are unique across `1..12`;
- every row has valid kills as an integer greater than or equal to zero;
- correction draft status is not blocked;
- no malformed correction draft is present;
- warning acknowledgement has been captured when warnings exist;
- finalized-data protection rules allow finalization;
- existing match validation passes;
- repository finalization succeeds.

Warnings alone must not permanently block finalization when all blockers are resolved. However, warnings are unresolved until explicitly acknowledged in the finalization confirmation flow.

## 8. Blocking Conditions

Finalization must be blocked for:

- missing OCR review result;
- missing correction draft;
- fewer than 12 rows;
- more than 12 rows;
- missing placement;
- invalid placement;
- duplicate placement;
- missing kills;
- invalid kills;
- negative kills;
- missing team slot;
- invalid team slot;
- duplicate team slot;
- missing tournament;
- missing match;
- match already finalized;
- tournament roster or team-slot data unavailable when required;
- team slot in the draft missing from the tournament team-slot model;
- finalized-data protection failure;
- repository write or finalization failure;
- malformed draft;
- unexpected exception before a safe finalization result is produced.

Each blocking condition must produce deterministic user-visible feedback. Sensitive internal exception details must not be displayed.

## 9. Warning and Confirmation Rules

Warnings must be displayed before finalization, including:

- placement changed from OCR value;
- kills changed from OCR value;
- assigned team slot changed from suggestion;
- row originally required manual review;
- weak confidence or safety evidence;
- any row that required manual correction.

Finalization confirmation rules:

- no-blocker/no-warning finalization may proceed after the normal final confirmation;
- no-blocker warning finalization requires explicit confirmation summarizing the warning count and changed rows;
- blocker finalization is disabled and must show blocker reasons;
- unacknowledged warnings are unresolved and block the final action until the warning confirmation is completed;
- acknowledged warnings do not change scoring and do not bypass validation.

The confirmation must not silently accept uncertain OCR evidence. It records only operator acknowledgement for the current in-memory finalization action. Canonical evidence history remains v0.9.9 scope.

## 10. Finalization Output Mapping

For each corrected row:

- assigned team slot identifies the tournament team;
- corrected placement becomes the `MatchPlacement.position` for that team slot;
- corrected kills becomes the `MatchKill.kills` for that team slot;
- the mapped row becomes a `MatchResultRowInput(teamSlotNumber, placement, kills)` or equivalent existing finalization input;
- existing validation confirms the complete 12-row result;
- existing finalization use case/repository writes the finalized match result.

Existing scoring engines compute:

- placement points through `PositionPointsEngine`;
- kill points through `KillPointsEngine`;
- match totals through `MatchTotalEngine`;
- cumulative standings through the existing standings engines.

v0.9.8 must not create a separate scoring formula and must not directly mutate standings outside the existing finalized-match flow.

## 11. Draft vs Finalized Protection

Before finalization, OCR correction state is draft/review-only.

After successful finalization, the match becomes finalized through the existing finalized state mechanism.

Finalized matches must be protected by existing finalized-data protection rules:

- repeated finalization after success must be blocked or idempotently rejected;
- finalized OCR-derived match data must not remain editable through the OCR correction UI;
- later changes must go through the existing protected finalized-match correction workflow;
- direct OCR review edits must not mutate finalized results.

v0.9.8 must not weaken existing finalized/draft state separation.

## 12. Persistence Boundary

Allowed:

- writing the finalized match result through the existing match persistence/finalization path;
- updating existing match state from draft to finalized through the approved repository/use-case flow;
- clearing existing draft match values when the existing repository finalization path already does so.

Not allowed:

- new original/corrected OCR evidence history persistence;
- new audit/history table;
- new Room migration unless repository evidence proves the existing finalized match model cannot represent the result;
- Supabase schema changes;
- synchronization payload changes;
- storage upload changes;
- direct raw SQL or manual database writes;
- bypassing repository/use-case boundaries.

If existing models cannot safely persist OCR-derived finalized match results without a schema change, the implementation must stop and report the conflict rather than add migrations silently.

## 13. UI Boundary

Allowed v0.9.8 UI additions:

- finalization readiness summary;
- finalize action only when safe;
- disabled finalize state when blocked;
- blocker count and reason display;
- warning count and confirmation dialog;
- changed-row summary for warning confirmation;
- success state after finalization;
- deterministic error state after finalization failure;
- back/navigation behavior after success.

Not allowed:

- export or share controls;
- cloud sync controls;
- OCR retry controls;
- roster editing;
- raw evidence editing;
- v0.9.9 preservation/history UI;
- post-finalization correction UI changes outside the existing protected correction workflow.

Correction inputs must be hidden or disabled after successful finalization if the same UI remains visible.

## 14. ViewModel and Use-Case Boundary

The ViewModel may expose a finalize action from ready correction state.

The ViewModel must:

- expose immutable UI state;
- derive button enabled/disabled state from draft validity and finalization state;
- call an approved domain/use-case/repository boundary;
- display deterministic success and failure states;
- avoid scoring formulas;
- avoid direct repository writes when a use case exists;
- avoid OCR, parsing, matching, suggestion, confidence, and safety recomputation.

The use-case boundary must:

- validate all finalization preconditions;
- map correction draft rows to existing finalization input rows;
- delegate ordinary match validation to existing validation rules;
- call the approved repository finalization path;
- return deterministic success/failure results;
- avoid partial mutation on failure;
- block or idempotently reject repeated finalization after success.

Where current repository conventions already provide a `FinalizeMatchUseCase`, v0.9.8 implementation should extend or wrap that boundary rather than duplicate finalization behavior in the ViewModel.

## 15. Error Handling

Deterministic error states must cover:

- tournament not found;
- match not found;
- OCR review evidence unavailable;
- correction draft missing;
- correction draft invalid;
- row count invalid;
- roster or team-slot state unavailable;
- match already finalized;
- match not draft/editable;
- repository finalization rejection;
- write failure;
- unexpected exception.

The UI must not crash. User-visible messages should identify the actionable category without exposing stack traces, database internals, private OCR text, screenshot paths, tokens, or backend details.

## 16. Security and Data Integrity

Finalization must not bypass finalized-data protection.

Only the local authorized operator workflow is in scope for v0.9.8. Cloud authorization, Supabase RLS, storage policies, synchronization, and backend conflict handling remain outside this version.

v0.9.8 must avoid partial writes. Finalization must either produce one complete finalized match result or leave existing valid data unchanged.

Original evidence must remain available in memory/UI during review, but it must not be persisted as canonical original/corrected history until v0.9.9.

Raw OCR text should not be exposed beyond the existing review UI, and should not be logged.

## 17. Testing Strategy

Required JVM tests:

- finalization blocked when no correction draft exists;
- finalization blocked when draft has blockers;
- finalization allowed when draft is valid;
- warnings require explicit confirmation;
- duplicate placement blocks;
- duplicate team slot blocks;
- invalid kills blocks;
- missing row blocks;
- already finalized match blocks;
- missing tournament and missing match are handled deterministically;
- successful mapping from corrected rows to existing match result model;
- scoring uses existing scoring engines rather than new formulas;
- repeated finalization after success is blocked or idempotently rejected;
- repository failure returns deterministic error;
- no original/corrected evidence history persistence is added.

Required Compose/ViewModel tests:

- finalize button disabled when blocked;
- blocker labels visible;
- warning confirmation visible;
- finalize callback/action fires only when allowed;
- success state visible;
- no save, export, sync, upload, or roster-edit controls are shown;
- finalized state hides or disables correction inputs if repository UI supports finalized review.

Connected-device tests are required for the implementation PR because v0.9.8 changes finalization UI behavior.

Tests must use synthetic sanitized data only. Real screenshots, real player names, private OCR payloads, ML Kit execution, network calls, Supabase calls, Room migrations, Docker, and device assets are not required for this decision document.

## 18. Compatibility Requirements

v0.9.8 must not modify or break:

- Phase 8 OCR extraction and parsing models;
- v0.9.0 text normalization;
- v0.9.1 similarity matching;
- v0.9.2 candidate scoring;
- v0.9.3 top-three suggestions;
- v0.9.4 confidence thresholds;
- v0.9.5 assignment safety;
- v0.9.6 read-only review display;
- v0.9.7 manual correction draft/edit behavior;
- existing manual match creation;
- existing manual placement entry;
- existing manual kill entry;
- existing match review;
- existing match finalization;
- existing match correction workflow;
- scoring and standings;
- roster management;
- Room persistence;
- authentication;
- cloud synchronization;
- finalized-data protection.

## 19. Out of Scope

The following are explicitly out of scope:

- OCR retry orchestration;
- OCR execution;
- OCR parsing;
- player-name correction;
- roster correction;
- recomputing suggestions from corrected values;
- recomputing confidence;
- recomputing assignment safety;
- new scoring rules;
- direct standings mutation;
- original/corrected evidence history persistence;
- Room migrations unless explicitly proven unavoidable;
- Supabase changes;
- synchronization or upload;
- exports;
- public sharing.

The non-numbered roadmap item `v0.9.x - Roster OCR Review and Correction` remains separate future scope.

## 20. Acceptance Criteria

A later v0.9.8 implementation is accepted only when:

- finalization is unavailable without a valid correction draft;
- all blockers prevent finalization;
- warnings require explicit confirmation;
- exactly 12 corrected rows are required;
- placements are valid and unique;
- kills are valid and non-negative;
- assigned team slots are valid and unique;
- corrected rows map safely into the existing match finalization model;
- existing scoring pipeline is used;
- existing standings derive from finalized match state;
- finalized-data protection remains enforced;
- repeated finalization after success is blocked or idempotently rejected;
- no original/corrected evidence history persistence is added;
- no Room or Supabase schema change is added unless explicitly justified by repository evidence;
- comprehensive unit and UI tests pass;
- connected-device verification passes;
- existing behavior remains unchanged.

## 21. Deferred Decisions

Deferred to v0.9.9:

- canonical original/corrected OCR evidence preservation;
- persisted OCR evidence history;
- audit display of original OCR values beside confirmed corrected values;
- any canonical history model linking raw OCR, parsed OCR, correction draft, and finalized values.

Deferred to later approved versions:

- roster OCR review and correction;
- cloud synchronization of OCR-derived finalization evidence;
- export of finalized OCR-assisted match results;
- public sharing;
- exact backend authorization behavior for OCR-derived finalization.

## 22. Implementation Handoff

The next implementation task may add only the v0.9.8 safe finalization behavior described here.

Implementation should start from the v0.9.7 correction draft, map valid corrected rows into the existing match finalization model, use the existing validation/finalization/repository/scoring/standings boundaries, add warning confirmation and blocker feedback, and keep evidence-history persistence, schema changes, OCR, matching recomputation, synchronization, export, and roster behavior out of scope.

If implementation evidence shows the existing finalized match model cannot safely store OCR-derived finalized results without a schema change, stop and resolve the decision before coding migrations.
