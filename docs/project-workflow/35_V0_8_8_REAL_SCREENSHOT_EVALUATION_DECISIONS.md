# v0.8.8 — Real Screenshot Evaluation Decisions

## 1. Status

Approved implementation decision gate for Phase 8, v0.8.8 only. Phase 8 versions v0.8.0 through v0.8.7 are complete and merged. v0.8.8 may begin only within the real screenshot evaluation scope recorded here.

## 2. Version scope

v0.8.8 adds real screenshot evaluation only. It compares Phase 8 OCR pipeline output against manually verified expected data for explicitly approved genuine scoreboard screenshots.

Evaluation is local and test-only. It must not finalize matches, mutate tournament, match, roster, scoring, standings, correction, Room, Supabase, storage, or sync state; add production UI; calculate official standings; write match results; or perform Phase 9 matching or broad text normalization.

## 3. Canonical sources reviewed

- `AGENTS.md` and `README.md`
- `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`
- `docs/project-workflow/27_V0_8_0_ML_KIT_INTEGRATION_DECISIONS.md` through `docs/project-workflow/34_V0_8_7_OCR_FAILURE_HANDLING_DECISIONS.md`
- `docs/04_OCR_AND_TEAM_MATCHING.md`, `docs/06_ANDROID_APP.md`, `docs/09_TESTING_AND_ACCEPTANCE.md`, and `docs/11_SECURITY_AND_PRIVACY.md`
- Applicable Phase 7 screenshot, Android, testing, security, and `docs/ai/` workflow documents
- Current v0.8.1 layout, v0.8.2 preprocessing, v0.8.3 extraction, v0.8.4 through v0.8.6 parsers, and v0.8.7 review-marker analyzer

## 4. Relationship to completed Phase 8 OCR pipeline

Evaluation must exercise the Phase 8 OCR chain conceptually: the v0.8.1 fixed layout, v0.8.2 preprocessing, v0.8.3 raw extraction, v0.8.4 placement parsing, v0.8.5 player-name parsing, v0.8.6 kill parsing, and v0.8.7 failure/review markers.

The evaluation boundary observes and compares those outputs only. It must not alter ML Kit configuration, layout zones, preprocessing behavior, extraction, parser behavior, review-marker behavior, or any production workflow.

## 5. Evaluation boundary decision

Implementation belongs in test-only code, preferably under `app/src/test/java/com/hoggamers/rankforge/ocr/evaluation/` or an established equivalent test package. The preferred focused abstractions are:

- `RealScreenshotEvaluationCase`
- `ExpectedScoreboardRow`
- `RealScreenshotEvaluationResult`
- `OcrEvaluationMetric`
- `OcrEvaluationMismatch`
- `OcrEvaluationFixturePolicy`

The boundary must compare supplied expected data with parser and review-marker output deterministically. It must not introduce production entry points, UI, navigation, persistence, network access, or automatic correction.

## 6. Genuine screenshot fixture and privacy decision

No genuine screenshot, real player-name fixture, or uploaded conversation attachment is approved for repository inclusion in v0.8.8. The real Free Fire scoreboard screenshot uploaded in the ChatGPT conversation may be used only as a decision-discussion reference unless the user explicitly approves a later copy into the repository.

If genuine screenshots are required for manual implementation evaluation, all of the following are required first:

1. Explicit user approval for the specific evaluation use.
2. Privacy review of the image and expected-data handling.
3. Local-only storage outside version control or in an explicitly approved git-ignored test-input location.
4. Sanitized case identifiers and expected fixtures that do not expose real player names in source code, comments, documentation, test output, or committed files.
5. A documented statement of whether test data is committed; the default is intentionally excluded.

No screenshot, raw OCR payload, storage URL, private path, or real player name may appear in evaluation reports committed to the repository.

## 7. Manually verified expected-data decision

Every genuine evaluation case must have manually verified expected data before comparison. Expected data must identify:

- Placements 1 through 12 where visible and verifiable.
- Player-name text per row only where sufficiently visible and manually verified.
- Kill or elimination values per row only where sufficiently visible and manually verified.
- Explicit expected missing, ambiguous, invalid, or constrained fields.
- The visibility limitation for row 12 when controls or cropping partially obscure it.

Expected data is ground truth for comparison only. It must not be supplied back to a parser, used to auto-correct OCR, or converted into tournament or match data.

## 8. Evaluation comparison decision

For each expected fixed-layout row, evaluation must compare:

- Parsed placement outcome with the expected placement value where visible.
- Parsed player-name outcome with expected text where visible and manually verified.
- Parsed kill outcome with the expected kill value where visible and manually verified.
- v0.8.7 review markers with expected missing, ambiguous, invalid, or constrained fields.

Visible expected fields require exact comparison against their approved raw parsed value. Expected missing or constrained fields are correct only when the output retains an appropriate review marker; they must not be treated as valid detections. Mismatches must be reported, not silently normalized, corrected, suppressed, or used to weaken parser safety rules.

## 9. Metrics and reporting decision

Evaluation must report, at minimum:

- Row coverage for the fixed 12-row layout.
- Placement detection correctness for visible, verified placement fields.
- Player-name extraction correctness for visible, verified name fields.
- Kill extraction correctness for visible, verified kill fields.
- Missing, ambiguous, invalid, and constrained review-marker correctness.
- False-accept count for fields expected to require review.

Metrics must retain separate denominators for visible/verifiable fields and expected constrained or missing fields. A local evaluation report must use sanitized case identifiers and include: layout compatibility, visible and constrained row counts, per-field expected/observed status counts, correctness counts, false accepts, mismatch categories, and unresolved findings. It must not include screenshots, real player names, raw OCR text, storage identifiers, or private paths.

## 10. Position 12 visibility decision

Placement 12 must be evaluated explicitly because the calibrated reference layout identifies its right-panel row as constrained and potentially obscured by lower controls. An evaluator must mark each row-12 placement, name, and kill field as either visible and manually verified or constrained/missing before comparison.

When row 12 is constrained, a review marker is the expected safe outcome. Its absence is a false accept; its lack of detected data is not an extraction failure. Row 12 must remain in row coverage and fixed output ordering even when no field is visible.

## 11. Persistence and storage decision

Evaluation outputs remain local and test-only. v0.8.8 must not persist evaluation results, raw OCR, parsed values, screenshots, review markers, or expected data in Room, Supabase, screenshot storage, or synchronization state. It must not upload screenshots, OCR results, parsed data, or reports to Supabase.

The evaluation must not affect production app behavior, finalized-match protection, correction workflows, scoring, standings, exports, or match records.

## 12. Explicit exclusions

v0.8.8 must not implement:

- Production OCR workflow integration, production UI, navigation, review UI, or correction UI.
- Team matching, player/roster matching, Phase 9 normalization, confidence thresholds, score calculation, standings, totals, MVP, finalization, or match mutation.
- Room or Supabase schema changes, migrations, synchronization, storage upload, export, network behavior, Android permissions, or another layout.
- Automatic correction, test-data repository inclusion without explicit approval, or any work beyond the minimum test-only evaluation boundary.

## 13. Testing and verification expectations

Implementation tests must use sanitized synthetic fixtures to verify comparison logic, placement/player-name/kill mismatch reporting, expected constrained-field review markers, false-accept reporting, and fixed layout row ordering.

Tests must not require a committed genuine screenshot, real player names, ML Kit accuracy claims, network access, Supabase, Room migrations, or external files. If an explicitly approved manual genuine-screenshot evaluation is performed, its local-only steps are: prepare approved local input and manually verified sanitized expected data; run the test-only evaluator; compare its sanitized report with the expected statuses; record unresolved findings without changing parser rules or production data.

## 14. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Private screenshots or names enter version control. | Default to excluded local-only fixtures; require explicit approval and privacy review before any repository inclusion. |
| OCR safety rules are weakened to improve a metric. | Report mismatches and false accepts; never auto-correct or change parser behavior during evaluation. |
| Constrained row 12 is counted as an ordinary visible mismatch. | Record field visibility explicitly and require review-marker correctness for constrained fields. |
| Evaluation affects production results. | Keep code and outputs test-only, in memory or local-only, with no production state mutation or upload. |

## 15. Acceptance criteria for implementation

v0.8.8 implementation is acceptable only when it provides a deterministic, test-only comparison boundary that:

- Consumes Phase 8 pipeline outcomes and manually verified expected data without changing the pipeline.
- Reports per-row and per-field placement, player-name, kill, and review-marker mismatches.
- Produces the approved coverage, correctness, marker, and false-accept metrics with sanitized reporting.
- Handles row 12 visibility explicitly and preserves its fixed-layout position.
- Keeps genuine inputs and outputs private, local/test-only, and out of version control unless a later explicit approval and privacy policy permit otherwise.
- Introduces no production UI, matching, scoring, finalization, persistence, synchronization, upload, storage, or export behavior.

## 16. Next implementation action

Implement sanitized synthetic evaluation-comparison helpers and unit tests only. Do not use or copy a genuine screenshot until the user explicitly approves the fixture, privacy handling, and test-data policy; defer any unresolved OCR findings to approved Phase 8 follow-up or Phase 9/12 work.
