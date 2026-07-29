# v0.8.5 — Player-Name Parsing Decisions

## 1. Status

Approved implementation decision gate for Phase 8, v0.8.5 only. Phase 8 versions v0.8.0 through v0.8.4 are complete and merged. v0.8.5 may begin only within the scope recorded here.

## 2. Version scope

v0.8.5 introduces player-name parsing only. It extracts candidate player-name text for each fixed scoreboard row from raw OCR evidence and reports typed parsing outcomes. It does not perform roster matching, team matching, kill parsing, scoring, standings, review UI, persistence, synchronization, upload, or any mutation.

## 3. Canonical sources reviewed

- `README.md`
- `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`
- `docs/project-workflow/27_V0_8_0_ML_KIT_INTEGRATION_DECISIONS.md`
- `docs/project-workflow/28_V0_8_1_FIXED_SCOREBOARD_LAYOUT_DECISIONS.md`
- `docs/project-workflow/29_V0_8_2_IMAGE_PREPROCESSING_DECISIONS.md`
- `docs/project-workflow/30_V0_8_3_RAW_TEXT_EXTRACTION_DECISIONS.md`
- `docs/project-workflow/31_V0_8_4_PLACEMENT_PARSING_DECISIONS.md`
- Current Android OCR, parsing, testing, security, and AI workflow documentation and implementation boundaries.

## 4. Relationship to the fixed scoreboard layout

The parser consumes the v0.8.1 fixed scoreboard layout and its `PLAYER_NAME` field zones. It produces results in the deterministic fixed-layout row order: left-panel rows 1 through 5, followed by right-panel rows 6 through 12.

Placement-number, elimination-value, and repeated-label zones are not player-name sources. v0.8.5 does not add, calibrate, or alter scoreboard coordinates.

## 5. Relationship to raw text extraction

The parser consumes v0.8.3 raw OCR extraction output, including its text hierarchy and available geometry. It preserves the raw evidence needed to explain a result in memory for the current processing flow.

v0.8.5 does not call ML Kit directly, change the ML Kit adapter, add preprocessing, or require real screenshot accuracy.

## 6. Relationship to placement parsing

v0.8.4 placement parsing may supply fixed-row context only. Player-name parsing must not reparse placements, infer a name from a placement value, or use a placement result to override player-name evidence.

## 7. Parser boundary decision

OCR execution remains behind project-owned abstractions. The preferred domain parsing boundary is:

- `PlayerNameParser`
- `PlayerNameParsingInput`
- `PlayerNameParsingResult`
- Per-row player-name result, status, failure, and evidence types

The boundary must be independent of ML Kit and Android framework types. It must allow later versions to add review handling without replacing the v0.8.5 parser.

## 8. Geometry and evidence detection decision

The parser must prefer raw OCR evidence whose available geometry intersects the fixed row's `PLAYER_NAME` zone. It may use the raw block, line, and element hierarchy to retain the supporting evidence.

Evidence without sufficient geometry to associate it safely with a player-name zone must not be guessed into a row. Missing geometry, empty extraction output, and unusable evidence must produce typed non-success outcomes without a crash or fabricated name.

## 9. Text handling decision

For detected player-name text, trim surrounding whitespace and preserve the original case, symbols, and internal text where practical. Do not apply Phase 9 normalization, case folding, punctuation removal, character substitution, fuzzy comparison, roster lookup, player matching, or team matching.

Obvious UI labels may be excluded only when their geometry places them outside player-name fields, including placement, elimination, and repeated-label zones. Text must not be removed or reclassified through speculative string heuristics.

## 10. Output decision

The result must include one player-name outcome for every fixed-layout row, in fixed-layout row order. Each row outcome must retain its panel and row context, relevant player-name zone reference, typed status, parsed text when safely detected, and supporting raw evidence.

The approved statuses are `DETECTED`, `MISSING`, `AMBIGUOUS`, and `INVALID`. The result is in-memory processing data only.

## 11. Missing, ambiguous, and invalid handling

Empty OCR output or no usable evidence in a row's player-name zone yields `MISSING`. Multiple distinct credible name candidates for one row yield `AMBIGUOUS`. Malformed, unusable, or insufficiently attributable evidence yields `INVALID` when it cannot safely become a detected name.

Low-evidence conditions must remain typed for later v0.8.7 review handling. The parser must not select a candidate silently, invent a name, or treat placement numbers, kill-like values, or UI labels as a player name.

## 12. Persistence and mutation decision

Parsed player names and raw evidence remain in memory only in v0.8.5. This version must not write Room data, alter Room schemas, create migrations, change Supabase data or synchronization, upload screenshots, modify screenshot storage, or mutate match records.

Finalized-match protection, correction workflows, scoring, standings, totals, MVP calculations, and match finalization remain unchanged.

## 13. Explicit exclusions

v0.8.5 must not implement:

- Kill, placement, team, total, standings, score, MVP, or match-result parsing.
- Player roster matching, player normalization, team matching, or confidence-based automatic confirmation.
- Image preprocessing, crop-coordinate definition, direct ML Kit integration, or real-screenshot evaluation.
- Review UI, correction UI, persistence, synchronization, storage uploads, or external network behavior.
- Any v0.8.6 or later scope. Phase 9 player and roster matching remains deferred.

## 14. Testing and verification expectations

Implementation tests must use synthetic data only. Focused unit tests should verify:

- Player-name detection from fixed player-name zones and deterministic left-panel then right-panel row order.
- Empty OCR output and missing geometry produce safe typed outcomes without guessing.
- Ambiguous overlapping evidence is represented as `AMBIGUOUS`.
- Placement numbers, kill-like values, and `Eliminations` labels are not parsed as player names.
- No roster matching, team matching, scoring, or standings calculation is introduced.

Tests must not use real player names, screenshots, ML Kit, OCR-accuracy assertions, network services, Google Play Services, Supabase, Room migrations, or external files. Real screenshot evaluation is deferred to v0.8.8.

## 15. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| OCR geometry is absent or unreliable. | Return typed `MISSING` or `INVALID` outcomes; do not guess a row assignment. |
| Nearby placement, elimination, or UI text is mistaken for a name. | Limit eligible evidence to fixed `PLAYER_NAME` zones and reject evidence from non-name zones by geometry. |
| More than one candidate appears in a row. | Preserve the evidence and return `AMBIGUOUS` for later review handling. |
| Parsing becomes coupled to future matching work. | Keep raw-preserving text handling and the parser boundary independent of Phase 9 matching. |

## 16. Acceptance criteria for implementation

Implementation is acceptable when it provides a project-owned, testable player-name parser that:

- Consumes v0.8.3 raw OCR evidence and v0.8.1 player-name zones, with v0.8.4 row context optional.
- Produces one typed result per fixed-layout row in deterministic order.
- Uses geometry for safe player-name association and preserves raw evidence.
- Handles missing, ambiguous, malformed, and low-evidence cases without crashes or fabricated names.
- Introduces no ML Kit, preprocessing, persistence, sync, upload, matching, scoring, standings, finalization, or review UI behavior.

## 17. Next implementation action

Implement the minimal domain player-name parser boundary and synthetic focused unit tests for v0.8.5. Defer kill parsing, normalization, player and team matching, review handling, persistence, and real screenshot evaluation to their approved later versions.
