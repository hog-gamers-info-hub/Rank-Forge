# v0.8.1 — Fixed Scoreboard Layout Definition Decisions

## 1. Status

Approved implementation decision gate for Phase 8 v0.8.1. Phase 7 is complete and closed, and v0.8.0 ML Kit Integration is complete and merged into `main`.

This document authorizes only the fixed supported scoreboard-layout definition and its coordinate data structures. It does not authorize OCR execution, image transformation, parsing, persistence, review UI, or result processing.

## 2. Version scope

v0.8.1 defines one supported landscape Free Fire / Free Fire MAX match-result scoreboard layout using the two-panel result-table structure observed in the reference screenshot. The implementation scope is limited to immutable coordinate and row/placement definitions that later approved work can consume.

The 1600 x 720 reference screenshot is the calibration basis. Its coordinate data is represented as normalized ratios so compatible landscape images do not need to be exactly 1600 x 720 pixels.

## 3. Canonical sources reviewed

This decision was reviewed against:

* `AGENTS.md` and `README.md`;
* `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`;
* `docs/project-workflow/26_PHASE_7_CLOSURE_AUDIT.md`;
* `docs/project-workflow/27_V0_8_0_ML_KIT_INTEGRATION_DECISIONS.md`;
* `docs/project-workflow/19_V0_7_0_PHOTO_PICKER_INTEGRATION_DECISIONS.md` through `docs/project-workflow/25_V0_7_6_SCREENSHOT_METADATA_DECISIONS.md`;
* `docs/02_SYSTEM_ARCHITECTURE.md`, `docs/04_OCR_AND_TEAM_MATCHING.md`, and `docs/06_ANDROID_APP.md`;
* `docs/09_TESTING_AND_ACCEPTANCE.md` and `docs/11_SECURITY_AND_PRIVACY.md`; and
* the applicable `docs/ai/` workflow, coding, security, testing, Git, and approval documents.

## 4. Reference screenshot observations

The visual calibration reference is a 1600 x 720 landscape Free Fire / Free Fire MAX match-result screenshot. It presents two visible result panels:

* the left panel visibly contains placements 1 through 5;
* the right panel visibly contains placements 6 through 11; and
* the lower final-row area is constrained by the bottom controls, so placement 12 is only an expected model row and is partially unavailable in this reference.

Each placement row shows a placement number, player names, elimination numbers, and repeated `Eliminations` labels. The Free Fire logo, header area, gameplay background, bottom numeric overlay, and BACK button are not scoreboard OCR content and are excluded by the approved layout zones.

The reference contains real player names. It is a calibration reference only and must not be committed as a repository screenshot, fixture, or OCR-accuracy test input in v0.8.1.

## 5. Supported layout decision

The only supported layout in v0.8.1 is a landscape Free Fire / Free Fire MAX match-result scoreboard that matches the calibrated two-panel result-table structure.

An image is layout-compatible only when it is landscape and has an aspect ratio within five percent of the 1600:720 calibration ratio (`2.2222`). The approved inclusive range is `2.11` through `2.33` width divided by height. Exact 1600 x 720 resolution is not required when normalized coordinates can be mapped safely.

v0.8.1 defines no second layout, device-specific pixel table, or alternate panel arrangement.

## 6. Coordinate system decision

All layout rectangles use normalized `Float` or `Double` values and immutable data models.

* The origin is the image top-left corner.
* `x` ranges from `0.0` at the left edge to `1.0` at the right edge.
* `y` ranges from `0.0` at the top edge to `1.0` at the bottom edge.
* `width` is a ratio of image width and `height` is a ratio of image height.

A later implementation may convert an approved normalized rectangle to a bitmap pixel rectangle at runtime using the current image dimensions. That conversion is coordinate mapping only; v0.8.1 does not crop, scale, enhance, or otherwise preprocess an image.

Exclusion zones take precedence over every content zone where boundaries touch or overlap. In particular, the bottom-controls exclusion supersedes the final one percent of the overall and panel content height in the calibration guidance.

## 7. Approved layout zones

The following normalized rectangles are the approved decision-level starting coordinates for the 1600 x 720 calibration basis.

| Zone | x | y | width | height | Purpose |
| --- | ---: | ---: | ---: | ---: | --- |
| Overall scoreboard content | 0.13 | 0.22 | 0.73 | 0.65 | Logical boundary for both result panels; exclusion zones still take precedence. |
| Left panel content | 0.13 | 0.22 | 0.40 | 0.65 | Logical panel for placements 1 through 5. |
| Right panel content | 0.54 | 0.22 | 0.32 | 0.65 | Logical panel for placements 6 through 12. |
| Top/logo exclusion | 0.00 | 0.00 | 1.00 | 0.20 | Excludes Free Fire logo/header content. |
| Bottom controls/BACK-button exclusion | 0.00 | 0.86 | 1.00 | 0.14 | Excludes the bottom controls and BACK button. |
| Bottom-left numeric overlay exclusion | 0.00 | 0.94 | 0.35 | 0.06 | Excludes the bottom numeric overlay. |
| Right-side non-scoreboard background exclusion | 0.86 | 0.00 | 0.14 | 1.00 | Excludes gameplay/background content outside the right panel. |

For each logical row, v0.8.1 defines the following repeated field zones relative to that row's panel rectangle:

| Field zone | Relative x | Relative y | Relative width | Relative height | Purpose |
| --- | ---: | ---: | ---: | ---: | --- |
| Placement-number zone | 0.00 | 0.00 | 0.12 | 1.00 | Placement number only. |
| Player-name zone | 0.12 | 0.00 | 0.58 | 1.00 | Player-name text only; no normalization or parsing is approved. |
| Elimination-value zone | 0.70 | 0.00 | 0.16 | 1.00 | Elimination number only. |
| Repeated-label exclusion | 0.86 | 0.00 | 0.14 | 1.00 | Excludes repeated `Eliminations` labels from the value zone. |

These are zone definitions only. They are not crop instructions, OCR calls, field extraction rules, or evidence that any text in a zone is accurate.

## 8. Row and placement mapping decision

The left panel has five vertically stacked logical rows, with equal row-height ratios within the left-panel content rectangle:

| Panel | Row index | Placement ID |
| --- | ---: | ---: |
| Left | 0 | 1 |
| Left | 1 | 2 |
| Left | 2 | 3 |
| Left | 3 | 4 |
| Left | 4 | 5 |

The right panel has seven expected vertically stacked logical rows, with equal row-height ratios within the right-panel content rectangle:

| Panel | Row index | Placement ID | Reference visibility |
| --- | ---: | ---: | --- |
| Right | 0 | 6 | Visible |
| Right | 1 | 7 | Visible |
| Right | 2 | 8 | Visible |
| Right | 3 | 9 | Visible |
| Right | 4 | 10 | Visible |
| Right | 5 | 11 | Visible |
| Right | 6 | 12 | Constrained/partially unavailable beneath the lower controls |

Placement 12 remains part of the fixed scoreboard model but is not fully visible in the calibration reference. It must not be treated as OCR-accuracy evidence or inferred as detected data in v0.8.1.

## 9. Unsupported layout handling

Portrait images, images outside the approved aspect-ratio range, images without the calibrated two-panel structure, and layouts whose required zones are unavailable are unsupported in v0.8.1.

v0.8.1 does not define a runtime failure UI or manual-review workflow. Unsupported layouts are out of scope and must be handled by later manual-review or failure handling in the roadmap's existing v0.8.7.

## 10. Explicit exclusions

v0.8.1 must not:

* run OCR or modify the v0.8.0 ML Kit integration beyond conceptually depending on its existing boundary later;
* crop, scale, rotate, enhance, compress, mutate, or preprocess images;
* parse placements, player names, eliminations, kills, teams, totals, standings, or confidence;
* persist OCR blocks, lines, recognized text, parsed fields, layout results, or review state;
* add manual OCR review UI, multiple layouts, public sharing, export behavior, or real screenshot fixtures;
* modify finalized-match protection, scoring, standings, correction workflows, Room persistence, Supabase synchronization, or screenshot storage behavior; or
* add Room schema changes, Supabase changes, migrations, Android permissions, or screenshot/image files to the repository.

## 11. Testing and verification expectations

Future v0.8.1 implementation tests must verify:

* normalized-to-pixel rectangle conversion;
* landscape and approved aspect-ratio acceptance and rejection;
* five left-panel and seven expected right-panel row definitions;
* placement IDs mapping to the correct panel and row index; and
* all approved exclusion zones are present and take precedence where they meet content zones.

Tests must not run OCR, require OCR accuracy, use the reference screenshot, or add real player names to fixtures. Real screenshot accuracy evaluation remains deferred to the roadmap's existing v0.8.8.

## 12. Risks and mitigations

| Risk | Approved mitigation |
| --- | --- |
| Coordinate behavior is tied only to 1600 x 720 pixels. | Store normalized rectangles and map them from the current bitmap dimensions at runtime. |
| Non-scoreboard artwork or controls contaminate later OCR input. | Define and give precedence to logo/header, background, bottom-control, numeric-overlay, and BACK-button exclusions. |
| Placement 12 is assumed visible from incomplete evidence. | Retain it as an expected right-panel model row and document the reference visibility constraint. |
| Layout definitions are mistaken for OCR or parsing behavior. | Limit this version to immutable zones and row/placement mapping; defer processing to the existing roadmap versions. |

## 13. Acceptance criteria for implementation

v0.8.1 implementation is acceptable only when:

* immutable normalized coordinate structures define the approved content, panel, field, and exclusion zones;
* the 1600:720 calibration ratio and approved `2.11` to `2.33` landscape acceptance range are enforced or exposed through a testable boundary;
* the left panel maps placements 1 through 5 and the right panel maps expected placements 6 through 12;
* placement 12 retains its documented constrained-reference limitation;
* normalized-to-pixel mapping and layout/row validation have focused unit tests; and
* no OCR, preprocessing, parsing, persistence, UI review, scoring, storage, synchronization, or finalized-match behavior is added.

## 14. Next implementation action

After this decision document is reviewed and merged, the next approved implementation task is limited to the immutable normalized layout-zone and row/placement data structures plus their focused unit tests. It must not begin image preprocessing, OCR execution, parsing, review UI, or real screenshot evaluation.
