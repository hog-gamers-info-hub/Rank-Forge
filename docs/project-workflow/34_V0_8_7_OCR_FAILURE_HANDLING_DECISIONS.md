# v0.8.7 — OCR Failure Handling Decisions

## 1. Status

Approved implementation decision gate for Phase 8, v0.8.7 only. Phase 8 versions v0.8.0 through v0.8.6 are complete and merged. v0.8.7 may begin only within the OCR failure-handling scope recorded here.

## 2. Version scope

v0.8.7 adds OCR failure handling only. It aggregates missing, malformed, duplicate, ambiguous, invalid, unsupported, and uncertain outcomes from v0.8.2 through v0.8.6 into deterministic in-memory review markers.

The output identifies fields requiring later manual review. It does not add manual review UI, run OCR or preprocessing, parse new values, match players or teams, calculate scores, mutate match state, persist data, synchronize data, upload data, or evaluate real screenshots.

## 3. Canonical sources reviewed

- `AGENTS.md` and `README.md`
- `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`
- `docs/project-workflow/27_V0_8_0_ML_KIT_INTEGRATION_DECISIONS.md` through `docs/project-workflow/33_V0_8_6_KILL_PARSING_DECISIONS.md`
- `docs/04_OCR_AND_TEAM_MATCHING.md`, `docs/05_SCORING_AND_PROCESSING_RULES.md`, and `docs/06_ANDROID_APP.md`
- `docs/09_TESTING_AND_ACCEPTANCE.md`, `docs/11_SECURITY_AND_PRIVACY.md`, and applicable `docs/ai/` workflow, security, coding, and testing documents
- Current v0.8.1 layout, v0.8.2 preprocessing, v0.8.3 raw extraction, and v0.8.4 through v0.8.6 parsing boundaries

## 4. Relationship to v0.8.2 preprocessing

Failure handling may consume v0.8.2 preprocessing results and typed failures where applicable. Unsupported layout or input, unreadable input, invalid crop, resource, and preprocessing failures must become blocking review output without reprocessing an image.

v0.8.7 does not invoke or modify preprocessing, alter candidates, define coordinates, or change original screenshot ownership or storage behavior.

## 5. Relationship to v0.8.3 raw extraction

Failure handling may consume v0.8.3 raw extraction outcomes, including empty and failed extraction results and their source-candidate context. Empty or failed extraction must yield deterministic missing or failure markers without invoking ML Kit or changing raw evidence.

## 6. Relationship to v0.8.4 placement parsing

v0.8.7 consumes existing placement parser outcomes only. A detected placement becomes an accepted field marker; missing, ambiguous, duplicate, or invalid placement outcomes become typed review markers. It must not reparse, change, or resolve placement values.

## 7. Relationship to v0.8.5 player-name parsing

v0.8.7 consumes existing player-name parser outcomes only. A detected player name becomes an accepted field marker; missing, ambiguous, or invalid player-name outcomes become typed review markers. It must not normalize names, roster-match players, team-match rows, or alter player-name parsing.

## 8. Relationship to v0.8.6 kill parsing

v0.8.7 consumes existing kill parser outcomes only. A detected kill value becomes an accepted field marker; missing, ambiguous, duplicate, or invalid kill outcomes become typed review markers. It must not reparse or correct kill values, calculate kill points, or apply a value to match state.

## 9. OCR failure handling boundary decision

OCR failure handling must be a pure, project-owned domain boundary under `app/src/main/java/com/hoggamers/rankforge/domain/ocr/review/`. The preferred focused abstractions are:

- `OcrFailureAnalyzer`
- `OcrFailureAnalysisInput`
- `OcrFailureAnalysisResult`
- `OcrReviewRow`
- `OcrReviewField`
- `OcrReviewFieldType`
- `OcrReviewStatus`
- `OcrReviewSeverity`
- `OcrReviewReason`
- `OcrReviewEvidence`

The boundary must remain independent of Android, ML Kit, UI, ViewModel, Room, Supabase, screenshot storage, matching, scoring, standings, correction, and finalization types. It may later be consumed by approved Phase 9 review UI or integration work without replacement.

## 10. Failure and review marker model decision

The analyzer accepts applicable v0.8.2 preprocessing results, v0.8.3 raw extraction outcomes, and v0.8.4 through v0.8.6 parser outcomes. It returns a deterministic summary with one ordered review row for every expected fixed-layout row and field-level markers for placement, player name, and kill values.

Each `OcrReviewField` must retain its field type, panel and row context, review status, severity, reason, manual-review-required state, and safe source evidence reference or copied metadata where available. The approved statuses are `ACCEPTED`, `MISSING`, `INVALID`, `AMBIGUOUS`, `DUPLICATE`, `UNSUPPORTED`, and `UNCERTAIN`.

`OcrReviewReason` must preserve the originating typed outcome where safe, including preprocessing failure, raw extraction failure or emptiness, parser failure, missing geometry, malformed text, unsupported layout, or unresolved ambiguity. The analyzer must preserve valid fields during partial success rather than converting an entire row to failure because another field requires review.

## 11. Severity and blocking decision

The approved severities are `BLOCKING`, `WARNING`, and `INFORMATIONAL`.

- `BLOCKING` applies to unsupported layout or input, preprocessing failure, raw extraction failure, and missing, invalid, ambiguous, or duplicate required placement, player-name, or kill fields. A blocking marker must prevent later automatic acceptance of the affected parsed OCR data until a later approved review workflow resolves it.
- `WARNING` applies to uncertain or incomplete evidence that does not itself establish a valid field and must remain visible for downstream review.
- `INFORMATIONAL` applies only to diagnostic metadata that does not change field validity or manual-review requirements.

v0.8.7 does not automatically accept, reject, correct, finalize, or mutate any parsed OCR data.

## 12. Manual-review marker decision

Every missing, invalid, ambiguous, duplicate, unsupported, or uncertain field must be marked as requiring manual review. Empty OCR output must create missing/manual-review markers rather than throw. Unsupported layout and preprocessing failures must create blocking manual-review markers.

The marker is a domain model only. v0.8.7 must not create a screen, navigation path, composable, ViewModel behavior, correction flow, or operator action.

## 13. Persistence and storage decision

Failure summaries, review markers, evidence references, and causes remain in memory only in v0.8.7. This version must not write Room data, alter Room schemas, create migrations, change Supabase data or synchronization, upload OCR, parsed, or failure data, change screenshot storage, or add Android permissions.

Finalized-match protection, correction workflows, scoring, standings, totals, MVP calculations, and match finalization remain unchanged.

## 14. Explicit exclusions

v0.8.7 must not implement:

- Manual OCR review UI, correction UI, review actions, navigation, or user interaction.
- Direct ML Kit calls, image preprocessing, candidate mutation, raw OCR persistence, new placement/name/kill parsing, or real-screenshot evaluation.
- Player or team matching, confidence scoring or thresholds, score calculation, standings, totals, MVP, winner positions, match mutation, or finalization.
- Room persistence, Supabase schema or synchronization changes, storage uploads, export, network behavior, or any v0.8.8 or later scope.

## 15. Testing and verification expectations

Implementation tests must use synthetic data only. Focused unit tests should verify:

- Missing placement, player-name, and kill fields create manual-review markers.
- Invalid or malformed, ambiguous, and duplicate parser outcomes create typed review markers.
- Unsupported layout or preprocessing failure creates blocking review output.
- Empty raw OCR output creates missing/manual-review markers without a crash.
- Valid fields remain accepted while failed fields in the same row remain marked for review.
- Review rows follow fixed layout order and equivalent inputs produce equivalent summaries.
- No scoring, standings, matching, finalization, persistence, or UI behavior is introduced.

Tests must not use real player names, screenshots, ML Kit, OCR-accuracy assertions, network services, Google Play Services, Supabase, Room migrations, or external files. Real screenshot evaluation remains deferred to v0.8.8.

## 16. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| A parser failure is silently lost while other fields succeed. | Preserve field-level statuses, reasons, severity, and manual-review markers independently. |
| Unsupported input is mistaken for successful OCR. | Emit a blocking unsupported/preprocessing marker without invoking a fallback OCR workflow. |
| Failure handling becomes review UI or correction logic. | Limit v0.8.7 to pure in-memory domain models and aggregation. |
| Valid fields are discarded because one row field is unresolved. | Preserve accepted field markers while marking only the affected fields for review. |

## 17. Acceptance criteria for implementation

v0.8.7 implementation is acceptable only when it provides a pure, testable failure analyzer that:

- Consumes applicable v0.8.2 through v0.8.6 outcomes without reprocessing or reparsing them.
- Returns deterministic row-ordered placement, player-name, and kill review fields with safe evidence and cause context.
- Marks missing, invalid, ambiguous, duplicate, unsupported, and uncertain fields for manual review.
- Applies blocking, warning, and informational severity consistently, with blocking markers preventing later automatic acceptance of affected data.
- Preserves valid fields during partial success.
- Introduces no UI, ML Kit, preprocessing, matching, scoring, finalization, persistence, synchronization, upload, storage, or real-evaluation behavior.

## 18. Next implementation action

Implement the minimal domain failure-analyzer and review-marker models with synthetic focused unit tests for v0.8.7. Defer manual review UI, correction, matching, scoring, persistence, and real screenshot evaluation to their approved later work.
