# v0.8.4 — Placement Parsing Decisions

## 1. Status

Approved implementation decision gate for Phase 8 v0.8.4. Phase 7 is complete and closed, and v0.8.0 through v0.8.3 are complete and merged.

This document authorizes only placement parsing after review and merge. It does not authorize player-name or kill parsing, team matching, review UI, persistence, scoring, finalization, or later Phase 8 work.

## 2. Version scope

v0.8.4 parses candidate placement values `1` through `12` from raw v0.8.3 OCR evidence using the fixed v0.8.1 layout placement-number zones. It returns deterministic, typed row outcomes for the expected layout rows and retains the evidence needed by later approved work.

It consumes raw OCR results and layout metadata only. It does not run ML Kit, preprocess images, mutate images or matches, or create persisted data.

## 3. Canonical sources reviewed

This decision was reviewed against:

* `AGENTS.md`, `README.md`, and `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`;
* `docs/project-workflow/26_PHASE_7_CLOSURE_AUDIT.md`;
* `docs/project-workflow/27_V0_8_0_ML_KIT_INTEGRATION_DECISIONS.md` through `docs/project-workflow/30_V0_8_3_RAW_TEXT_EXTRACTION_DECISIONS.md`;
* `docs/02_SYSTEM_ARCHITECTURE.md`, `docs/04_OCR_AND_TEAM_MATCHING.md`, `docs/05_SCORING_AND_PROCESSING_RULES.md`, and `docs/06_ANDROID_APP.md`;
* `docs/09_TESTING_AND_ACCEPTANCE.md`, `docs/11_SECURITY_AND_PRIVACY.md`, and `docs/ai/00_AI_WORKFLOW.md`; and
* the current v0.8.1 layout, v0.8.2 preprocessing, and v0.8.3 raw extraction models.

## 4. Relationship to v0.8.1 fixed layout

The v0.8.1 Free Fire MAX two-panel layout is the only placement-layout authority. Parsing must use its panel, row, placement-number-zone, and row-order definitions; it must not create new coordinates, layouts, or field zones.

The left-panel rows remain placements 1–5 and the right-panel rows remain expected placements 6–12, including the placement-12 constrained-reference limitation. Parser output ordering follows this fixed panel/row order, never arbitrary OCR return order.

## 5. Relationship to v0.8.3 raw extraction

The parser accepts ordered v0.8.3 raw extraction results and their source preprocessing-candidate metadata. It uses raw block, line, and element text and geometry where available, retaining safe source references or copied raw metadata in its evidence model.

The parser must not invoke ML Kit, alter raw OCR hierarchy, select preprocessing candidates based on text, or require geometry that ML Kit did not provide.

## 6. Placement parsing boundary decision

Placement parsing must be a project-owned domain boundary under `app/src/main/java/com/hoggamers/rankforge/domain/ocr/parsing/`. It may use focused types such as `PlacementParser`, `PlacementParsingInput`, `PlacementParsingResult`, `ParsedPlacementRow`, `PlacementParseStatus`, `PlacementParseFailure`, and `PlacementOcrEvidence`.

The parser is pure and deterministic: no Android, ML Kit, UI, ViewModel, Room, Supabase, screenshot-storage, scoring, standings, correction, or finalization dependency is approved. It returns typed outcomes rather than throwing for expected malformed OCR evidence.

## 7. Geometry-aware placement detection decision

When raw geometry exists, the parser must prefer OCR entities whose geometry intersects the v0.8.1 placement-number zone for the expected row. It may inspect block, line, and element hierarchy, preserving the chosen entity’s raw text, geometry, language, confidence state, extraction result, source candidate, panel, row, and zone references as evidence.

Missing geometry is safe: the parser must not crash or fabricate a zone match. A geometry-free entity may be considered only by a narrowly deterministic fallback associated with its fixed expected row; it must not be used to scan or infer values from player-name, elimination, or other zones.

## 8. Text handling decision

Text handling is limited to placement-number tokens. The parser may trim surrounding whitespace and apply minimal OCR-safe cleanup only for obvious numeric tokens, such as removing a single trailing punctuation mark or resolving an unambiguous `O`/`o` to `0` inside an otherwise numeric token.

It accepts numeric values only from `1` through `12`. It must not apply broad Phase 9 normalization, fuzzy matching, name handling, contextual scoring, or auto-correction. Malformed, multi-value, or non-numeric tokens are ignored or represented as invalid evidence without guessing a placement.

## 9. Parser output model decision

The parser returns one ordered `ParsedPlacementRow` outcome for each expected placement row 1–12. Each row retains its expected placement ID, panel ID, row index, placement-number-zone reference, source extraction/candidate context, and zero or more `PlacementOcrEvidence` entries.

`PlacementParseStatus` must distinguish at least `DETECTED`, `MISSING`, `AMBIGUOUS`, `DUPLICATE`, and `INVALID`. A detected row contains an accepted numeric placement; other statuses retain evidence and must not claim a valid result. Output remains parser evidence, not confirmed match data.

## 10. Duplicate, missing, and invalid placement handling

Empty OCR output, no eligible placement-zone evidence, or no accepted token produces `MISSING`, not a guessed value. Multiple competing valid values for one expected row produce `AMBIGUOUS`. The same valid value detected for more than one expected row produces `DUPLICATE` for every affected row unless a later approved review workflow resolves it.

Values below 1, above 12, malformed numeric-like tokens, and tokens from invalid evidence produce `INVALID` where associated with an expected row. The parser must never convert an invalid or out-of-range value into a valid placement. These outcomes are typed parser states suitable for later v0.8.7 failure handling; v0.8.4 adds no failure UI.

## 11. Persistence and storage decision

Placement parsing is in-memory and domain-level only. It must preserve raw evidence references/metadata for later review while the result is in memory, but it must not persist raw evidence, parsed placements, statuses, or candidate images.

No Room entities, DAOs, migrations, schemas, Supabase schema, Storage uploads, OCR-data upload, synchronization, screenshot-storage behavior, or Android permission changes are approved.

## 12. Explicit exclusions

v0.8.4 must not parse player names, kills, teams, totals, standings, winner positions, or any non-placement scoreboard field. It must not run ML Kit, preprocess images, match teams, apply confidence thresholds, calculate scores, finalize matches, mutate match state, add review UI, export data, add another layout, or evaluate real screenshots.

It must not modify v0.8.0 ML Kit integration, v0.8.1 layout definitions, v0.8.2 preprocessing behavior, v0.8.3 raw extraction behavior, finalized protection, Room, Supabase, or Phase 7 screenshot handling. Real screenshot evaluation remains deferred to v0.8.8.

## 13. Testing and verification expectations

Future implementation unit tests must verify detection of placements 1–12 from raw text in approved placement zones; fixed layout row ordering; empty output to missing outcomes; safe missing geometry; duplicate and ambiguous outcomes; out-of-range and malformed-token invalid outcomes; and preservation of raw evidence/context.

Tests must verify player-name text is not parsed as a placement and kill-like text is not parsed unless it is in a placement-number zone. They must not execute ML Kit, require real screenshot accuracy, use uploaded screenshots or real player names, depend on network, Google Play Services, Supabase, Room migrations, or external files. No scoring or standings calculation is permitted in parser tests.

## 14. Risks and mitigations

| Risk | Approved mitigation |
| --- | --- |
| OCR text outside placement zones is mistaken for a placement. | Prefer geometry intersection with the fixed placement-number zone and prohibit cross-zone scanning. |
| Missing geometry leads to a fabricated location. | Use a narrow deterministic row fallback only; otherwise return missing or invalid evidence. |
| Duplicate or malformed values silently become match data. | Preserve typed duplicate, ambiguous, missing, and invalid outcomes with evidence; do not guess or auto-correct. |
| OCR order changes result order. | Emit all twelve outcomes in fixed v0.8.1 panel/row order. |
| Parsing expands into scoring or review work. | Keep the boundary pure and placement-only; defer review/failure handling to v0.8.7 and evaluation to v0.8.8. |

## 15. Acceptance criteria for implementation

v0.8.4 implementation is acceptable only when:

* a pure project-owned placement parser consumes v0.8.3 raw results and v0.8.1 layout definitions;
* it returns twelve deterministic row outcomes in fixed layout order;
* geometry-aware detection prefers placement-number zones and handles missing geometry safely;
* only narrowly cleaned numeric values 1–12 can be detected;
* evidence and source candidate/extraction context are retained in memory;
* missing, ambiguous, duplicate, and invalid/out-of-range cases are typed without guessing or auto-correction;
* focused tests cover valid, empty, missing-geometry, duplicate, invalid, zone-isolation, and ordering behavior; and
* no excluded ML Kit, preprocessing, name/kill parsing, matching, scoring, persistence, review, Room, Supabase, storage, permission, or real-evaluation behavior is introduced.

## 16. Next implementation action

After this decision document is reviewed and merged, the next approved implementation task is limited to the pure placement parsing models, fixed-layout/geometry-aware detection, typed outcomes, evidence retention, and focused unit tests described here. It must not begin player-name parsing, kill parsing, team matching, review UI, scoring, persistence, or real screenshot evaluation.
