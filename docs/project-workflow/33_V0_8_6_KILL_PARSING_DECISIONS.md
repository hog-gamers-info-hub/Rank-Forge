# v0.8.6 — Kill Parsing Decisions

## 1. Status

Approved implementation decision gate for Phase 8, v0.8.6 only. Phase 8 versions v0.8.0 through v0.8.5 are complete and merged. v0.8.6 may begin only within the kill-parsing scope recorded here.

## 2. Version scope

v0.8.6 adds kill parsing only. It extracts and validates candidate kill or elimination values for each fixed scoreboard row from raw OCR evidence and returns typed in-memory outcomes.

This version does not parse placements or player names, match players or teams, calculate points or standings, finalize matches, mutate match state, add review UI, persist data, synchronize data, upload data, or evaluate real screenshots.

## 3. Canonical sources reviewed

- `AGENTS.md` and `README.md`
- `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`
- `docs/project-workflow/27_V0_8_0_ML_KIT_INTEGRATION_DECISIONS.md` through `docs/project-workflow/32_V0_8_5_PLAYER_NAME_PARSING_DECISIONS.md`
- `docs/04_OCR_AND_TEAM_MATCHING.md`, `docs/05_SCORING_AND_PROCESSING_RULES.md`, and `docs/06_ANDROID_APP.md`
- `docs/09_TESTING_AND_ACCEPTANCE.md`, `docs/11_SECURITY_AND_PRIVACY.md`, and applicable `docs/ai/` workflow, coding, security, and testing documents
- Current v0.8.1 layout, v0.8.3 raw extraction, v0.8.4 placement parsing, and v0.8.5 player-name parsing boundaries

## 4. Relationship to v0.8.1 fixed layout

The v0.8.1 Free Fire MAX two-panel layout remains the sole layout authority. Kill parsing consumes each fixed row's `ELIMINATION_VALUE` field zone and emits rows in the fixed layout order: left-panel rows 1 through 5, then right-panel rows 6 through 12.

Placement-number, player-name, repeated-label, and other exclusion zones are not kill-value sources. v0.8.6 does not add, calibrate, or modify layout coordinates.

## 5. Relationship to v0.8.3 raw extraction

The parser accepts v0.8.3 raw extraction results, including raw block, line, and element text and safely available geometry. It preserves source raw evidence or copied metadata needed to explain an in-memory row outcome.

v0.8.6 does not call ML Kit directly, modify the ML Kit boundary, preprocess images, mutate preprocessing candidates, select candidates based on OCR content, or require real screenshot accuracy.

## 6. Relationship to v0.8.4 placement parsing

v0.8.4 placement parsing may provide fixed-row context only. Kill parsing must not reparse placements, infer kills from placement values, or change placement parser behavior or outcomes.

## 7. Relationship to v0.8.5 player-name parsing

v0.8.5 player-name parsing may provide fixed-row context only. Kill parsing must not parse player names, normalize names, roster-match players, team-match rows, or change player-name parser behavior or outcomes.

## 8. Kill parsing boundary decision

Kill parsing must be a pure, project-owned domain boundary under `app/src/main/java/com/hoggamers/rankforge/domain/ocr/parsing/`. The preferred focused abstractions are:

- `KillParser`
- `KillParsingInput`
- `KillParsingResult`
- `ParsedKillRow`
- `KillParseStatus`
- `KillParseFailure`
- `KillOcrEvidence`

The boundary must remain independent of Android, ML Kit, UI, ViewModel, Room, Supabase, screenshot-storage, scoring, standings, correction, and finalization types. It must support later review handling without replacing v0.8.6 parsing.

## 9. Geometry-aware kill detection decision

The parser must prefer raw OCR entities whose available geometry intersects the expected row's fixed `ELIMINATION_VALUE` zone. It may use the raw block, line, and element hierarchy to preserve supporting evidence.

Evidence with missing or insufficient geometry must not be guessed into a kill row. Empty extraction output, missing geometry, and unusable evidence must yield typed non-success outcomes without a crash or fabricated value. Text in placement-number, player-name, repeated-label, or other non-elimination zones must not be parsed as a kill value.

## 10. Text and numeric validation decision

The parser may trim surrounding whitespace and accept only a complete non-negative integer token. Negative values, decimal values, malformed tokens, and numeric overflow are invalid and must not be auto-corrected. Repeated `Eliminations` labels are ignored through their fixed exclusion geometry.

v0.8.6 defines no game-specific maximum displayed kill value. A value is valid only when it is representable by the approved parser numeric type as a non-negative integer; values outside that range are `INVALID`. A later approved version may establish a game-specific upper bound. Broad Phase 9 normalization, character substitution, contextual correction, and confidence-based confirmation are not approved.

## 11. Parser output model decision

The parser returns one `ParsedKillRow` outcome for every expected fixed-layout row in deterministic layout order. Each row retains its panel identity, row index, elimination-value zone reference, source evidence, and typed status.

`KillParseStatus` must distinguish at least `DETECTED`, `MISSING`, `AMBIGUOUS`, `DUPLICATE`, and `INVALID`. A detected result contains only a validated candidate kill value. It may represent a player-level kill value or a row-level team kill value as an in-memory candidate, but it must not apply that value to match state, scoring, standings, winner positions, or MVP calculations.

## 12. Missing, ambiguous, and invalid kill handling

Empty OCR output or no eligible elimination-zone evidence yields `MISSING`. Multiple distinct valid numeric candidates in one row yield `AMBIGUOUS`. Repeated competing numeric evidence that cannot safely be represented as one canonical field yields `DUPLICATE` with preserved evidence.

Negative, decimal, malformed, overflow, or otherwise out-of-range tokens associated with a row yield `INVALID`. These typed outcomes are retained for later v0.8.7 failure handling; the parser must not select a candidate silently, fabricate a value, or convert invalid text into a valid kill total.

## 13. Persistence and storage decision

Parsed kill values and raw evidence remain in memory only in v0.8.6. This version must not write Room data, alter Room schemas, create migrations, change Supabase data or synchronization, upload OCR or parsed data, change screenshot storage, or add Android permissions.

Finalized-match protection, correction workflows, scoring, standings, total points, MVP calculations, and match finalization remain unchanged.

## 14. Explicit exclusions

v0.8.6 must not implement:

- OCR execution, direct ML Kit calls, image preprocessing, candidate mutation, or real-screenshot evaluation.
- Placement parsing, player-name parsing, player normalization, roster matching, team matching, or confidence thresholds.
- Placement points, kill points, total points, standings, winner positions, MVP calculations, finalization, or match-state mutation.
- Manual OCR review UI, correction UI, Room persistence, Supabase schema or synchronization changes, storage uploads, export, or network behavior.
- Any v0.8.7 or later scope. Real screenshot evaluation remains deferred to v0.8.8.

## 15. Testing and verification expectations

Implementation tests must use synthetic data only. Focused unit tests should verify:

- Kill-value detection from fixed elimination-value zones, deterministic row order, and left/right panel mappings.
- Safe missing outcomes for empty OCR output and missing geometry.
- Typed ambiguous overlapping numeric evidence and typed duplicate evidence where applicable.
- Typed invalid negative, decimal, malformed, and overflow/out-of-range values without guessing.
- Placement numbers, player names, and repeated `Eliminations` labels outside elimination-value zones are not parsed as kills.
- No scoring, standings, MVP, finalization, roster matching, or team matching behavior is introduced.

Tests must not use real player names, screenshots, ML Kit, OCR-accuracy assertions, network services, Google Play Services, Supabase, Room migrations, or external files. Real screenshot evaluation is deferred to v0.8.8.

## 16. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Text from a nearby field is mistaken for a kill. | Limit eligible evidence to fixed `ELIMINATION_VALUE` zones and reject other zones by geometry. |
| Missing geometry leads to invented row values. | Return typed non-success outcomes and retain only safely associated evidence. |
| Malformed or implausible numeric OCR becomes match data. | Require complete non-negative integer tokens; preserve `INVALID`, `AMBIGUOUS`, or `DUPLICATE` outcomes without correction. |
| Kill parsing expands into scoring or finalization. | Keep outputs as in-memory candidates and leave scoring, standings, review, and match mutation unchanged. |

## 17. Acceptance criteria for implementation

v0.8.6 implementation is acceptable only when it provides a pure, testable kill parser that:

- Consumes v0.8.3 raw evidence and v0.8.1 elimination-value zones, with v0.8.4 and v0.8.5 row context optional.
- Returns one deterministic typed outcome for every fixed-layout row in panel/row order.
- Uses geometry for safe row association and preserves source evidence in memory.
- Accepts only complete non-negative integer values and types missing, ambiguous, duplicate, malformed, negative, decimal, and overflow/out-of-range cases without guessing.
- Introduces no ML Kit, preprocessing, placement/name parsing, matching, scoring, standings, finalization, review UI, persistence, synchronization, upload, storage, or real-evaluation behavior.

## 18. Next implementation action

Implement the minimal domain kill-parser boundary and synthetic focused unit tests for v0.8.6. Defer review and failure handling to v0.8.7 and real screenshot evaluation to v0.8.8; do not begin matching, scoring, persistence, or UI work.
