# v0.8.9 — Cropped Roster Layout Definition Decisions

## 1. Status

Approved implementation decision gate for the Phase 8 roster OCR extension, v0.8.9. The Phase 7 roster screenshot extension is complete and closed, including manual crop preparation and ordered tournament-scoped set association.

This document authorizes only one supported cropped roster-panel layout definition and its normalized, project-owned region model. It does not authorize OCR, image preprocessing, parsing, validation, review, persistence, or synchronization.

## 2. Version scope

v0.8.9 defines the layout contract for a cropped roster panel that contains four visible roster slots. The layout is based on the privacy-controlled representative screenshot observations confirmed for this decision: screenshot positions 1, 2, and 3 respectively represent intended tournament slots 1–4, 5–8, and 9–12.

The contract applies only after the user has selected a roster screenshot and prepared its roster-panel crop through the approved v0.7.8 manual in-app crop workflow. It defines normalized regions inside that crop; it does not define or infer full-screenshot crop coordinates.

## 3. Canonical sources reviewed

This decision was reviewed against:

* `AGENTS.md` and `README.md`;
* `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`;
* `docs/project-workflow/36_PHASE_8_CLOSURE_AUDIT.md`;
* `docs/project-workflow/37_ROSTER_SCREENSHOT_OCR_ROADMAP_DECISIONS.md`;
* `docs/project-workflow/38_V0_7_7_ROSTER_SCREENSHOT_INTAKE_DECISIONS.md` through `docs/project-workflow/40_V0_7_9_ROSTER_SCREENSHOT_SET_ASSOCIATION_DECISIONS.md`;
* `docs/project-workflow/41_PHASE_7_ROSTER_SCREENSHOT_EXTENSION_CLOSURE_AUDIT.md`;
* the completed Phase 8 scoreboard layout and OCR decision documents, especially `docs/project-workflow/28_V0_8_1_FIXED_SCOREBOARD_LAYOUT_DECISIONS.md`;
* `docs/01_PRODUCT_REQUIREMENTS.md`, `docs/02_SYSTEM_ARCHITECTURE.md`, `docs/04_OCR_AND_TEAM_MATCHING.md`, `docs/06_ANDROID_APP.md`, `docs/09_TESTING_AND_ACCEPTANCE.md`, and `docs/11_SECURITY_AND_PRIVACY.md`;
* the current normalized OCR rectangle, scoreboard layout, validation, OCR-boundary, and focused test contracts; and
* the user-confirmed visual observations of three representative roster screenshots and manually verified expected structure. Those private screenshots and any real names are decision evidence only and are not repository assets or fixtures.

## 4. Relationship to Phase 7 roster screenshot extension

v0.7.7 through v0.7.9 remain the prerequisite image-lifecycle work. They own roster screenshot intake, private-original preservation, manual crop preparation, crop metadata, fixed screenshot positions 1–3, local set association, and local restoration.

v0.8.9 consumes only the approved cropped-panel input or its reproducible normalized crop metadata. It must not reinterpret the original image, bypass the manual crop, change the tournament-scoped roster screenshot set, or affect the independent match-result screenshot workflow.

## 5. Cropped-panel-only layout decision

The only supported v0.8.9 input is a prepared roster-panel crop. Its content boundary must contain the roster list or panel only.

The following full-screenshot content is outside the layout and must not become a roster-layout region: lobby settings, chat, account or header UI, invite controls, start controls, and other non-roster content. The implementation must not introduce 1600 x 720 coordinates, other full-screenshot coordinates, automatic full-image crop guessing, or a fallback that silently treats an uncropped screenshot as a supported panel.

All rectangles are normalized relative to the cropped panel: the origin is the crop's top-left corner, `x` and `y` range from `0.0` to `1.0`, and `width` and `height` are fractions of the crop dimensions. Pixel mapping, if needed later, is a conversion of this cropped-panel definition only.

## 6. Supported roster-panel structure decision

The one supported structure contains exactly four visible roster slots arranged as a two-by-two panel:

| Visible position | Panel location |
| --- | --- |
| 1 | Top left |
| 2 | Top right |
| 3 | Bottom left |
| 4 | Bottom right |

Each visible slot has a slot-content region, a slot-number region, and four ordered player-name row regions. These regions are geometry only; none is an OCR call, text value, team identity, player identity, or roster record.

## 7. Slot ordering and visible-position decision

Visible positions use the fixed reading order: top left, top right, bottom left, bottom right. That order is the only positional metadata defined by this version.

| Roster screenshot position | Intended tournament-slot range | Visible position mapping |
| --- | --- | --- |
| 1 | 1–4 | 1→1, 2→2, 3→3, 4→4 |
| 2 | 5–8 | 1→5, 2→6, 3→7, 4→8 |
| 3 | 9–12 | 1→9, 2→10, 3→11, 4→12 |

This table records deterministic layout metadata only. It does not associate OCR candidates to roster slots; that work remains deferred to v0.8.12.

## 8. Slot-region decision

The future v0.8.9 layout model must represent four slot-content regions by visible position, using immutable normalized rectangles that remain fully within the cropped-panel boundary. Each region identifies where a later version may limit roster-specific processing; it must not embed text, call ML Kit, preprocess pixels, parse values, or create candidate data.

The model must expose all four positions explicitly rather than relying on an ambiguous list order alone. A layout with fewer, more, duplicate, or unknown visible positions is not the supported layout.

## 9. Player-row-region decision

Each visible slot has exactly four approved, ordered player-name row regions. The rows are ordered from top to bottom within the slot-content region and use normalized cropped-panel coordinates.

The four regions reflect the only player-row visibility confirmed by the representative evidence. They define candidate text locations only for later work and must not parse a player name, normalize a player, match a roster, or infer absent rows.

## 10. Slot-number-region decision

Each visible slot has one slot-number region within its slot-content region. It is a normalized region for later raw-evidence handling only; v0.8.9 does not recognize, parse, validate, or use a displayed number as data.

The slot-number region must not be used to override the fixed screenshot-position plus visible-position mapping in section 7. That mapping is the layout contract for this version.

## 11. Layout compatibility and rejection decision

A cropped-panel input is layout-compatible only when it is identified as a prepared roster crop for screenshot position 1, 2, or 3; has positive usable dimensions; and can support the complete four-slot, slot-number, and four-player-row region structure within its normalized bounds.

The layout boundary must fail safely when required geometry is missing, invalid, out of bounds, duplicated, incomplete, or too small to map a required region safely. A full screenshot, an unknown screenshot position, or an unsupported panel arrangement must be rejected as unsupported rather than accepted by guessing, OCR, or inferred crop coordinates.

v0.8.9 defines no production rejection UI or manual-review flow. Unsupported inputs remain manual roster-entry cases until the approved later review phase.

## 12. Four-player visible-row evidence decision

The representative evidence supports four visible player-name rows per roster slot. Therefore, exactly four row regions per slot are part of the supported v0.8.9 layout model and synthetic verification contract.

This is evidence of visible geometry only. It is not an OCR-accuracy claim and does not assert that every observed row contains a valid player name.

## 13. Five/six-player visibility limitation decision

The representative evidence does not establish a fifth or sixth visible player row. Five- and six-player visibility is unsupported by v0.8.9 and must not be guessed, synthesized, or represented as an approved region.

Where a roster needs five or six players, it remains a manual-entry or manually corrected case until privacy-approved representative evidence and a later explicit layout decision establish supported visibility. Parsing, validation, review, and confirmation remain separately deferred.

## 14. Privacy and fixture decision

Real roster screenshots, real player names, raw OCR payloads, local paths, signed URLs, and manually verified ground truth must not be committed to the repository without explicit privacy approval.

Automated verification must use synthetic dimensions, normalized rectangles, and synthetic names where text labels are needed. Private representative screenshots may be evaluated locally only under the documented privacy rules; they are not test fixtures and must not be required for v0.8.9 tests.

## 15. Explicit exclusions

v0.8.9 must not add or change:

* OCR, ML Kit use, image preprocessing, player-name parsing, OCR-candidate-to-roster-slot association, OCR validation, or review UI;
* confirmed-roster persistence, Room schema or migration work, Supabase or backend work, upload, or synchronization;
* scoring, standings, match finalization, correction workflows, or manual roster-entry behavior;
* real screenshot or real-player-name fixtures, broad image refactoring, or automatic full-screenshot crop guessing; or
* the completed match-result scoreboard layout, match screenshot OCR behavior, or Phase 8 v0.8.0 through v0.8.8 implementation.

## 16. Testing and verification expectations

Future v0.8.9 implementation tests must use only synthetic inputs and verify:

* normalized cropped-panel bounds and safe normalized-to-pixel mapping where provided;
* exactly four slot regions with the approved top-left, top-right, bottom-left, bottom-right ordering;
* compatibility of screenshot-position metadata with the 1–4, 5–8, and 9–12 intended-slot ranges;
* one slot-number region and four ordered player-row regions for each slot;
* safe rejection of invalid crop dimensions, unknown screenshot positions, missing regions, out-of-bounds regions, and unsupported panel structures; and
* regression protection that the existing scoreboard layout remains independent and unchanged.

The tests must not require ML Kit, OCR accuracy, network access, Room migrations, Supabase, real screenshots, real names, or external files.

## 17. Risks and mitigations

| Risk | Approved mitigation |
| --- | --- |
| Full-screen game UI contaminates later roster processing. | Require a v0.7.8 prepared crop and define regions only within that crop. |
| A crop is silently treated as compatible despite missing geometry. | Require complete, bounded normalized regions and reject invalid or unsupported structures safely. |
| Slot order is inferred differently by later versions. | Fix the top-left, top-right, bottom-left, bottom-right order and screenshot-position mapping in one project-owned contract. |
| Four visible rows are mistaken for proof of all roster sizes. | Limit the supported geometry to four evidenced rows and keep five/six-player visibility manual or deferred. |
| Private screenshots or names enter source control. | Use synthetic tests only and retain representative evidence outside the repository under privacy controls. |
| Roster work changes match-result OCR. | Keep this as a roster-specific cropped-panel model and require scoreboard regression protection. |

## 18. Acceptance criteria for implementation

v0.8.9 implementation is acceptable only when:

* it provides immutable project-owned normalized layout structures for one cropped roster-panel layout;
* it represents exactly four explicit visible slot positions in the approved order;
* every slot exposes bounded slot-content, slot-number, and exactly four ordered player-row regions;
* screenshot positions 1–3 map only to the intended 1–4, 5–8, and 9–12 slot ranges;
* invalid dimensions, missing or invalid regions, unsupported panel structures, and uncropped input are rejected without guessing; and
* focused synthetic tests cover the layout and rejection boundary without adding OCR, preprocessing, parsing, persistence, UI review, backend, scoring, or real fixture behavior.

## 19. Next implementation action

After this decision document is reviewed and merged, and with explicit user approval, the next v0.8.9 implementation task is limited to the normalized cropped roster-panel layout data structures, compatibility boundary, and focused synthetic tests described here.

v0.8.10 raw OCR extraction, v0.8.11 roster team and player parsing, v0.8.12 candidate slot association, v0.8.13 OCR validation, Phase 9 review and correction, and the Phase 5/6 confirmed-roster persistence work remain deferred. Five- and six-player visible-row support also remains deferred pending new approved representative evidence.
