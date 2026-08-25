# Lobby Player Dual-OCR Mapping Architecture

Status: Design locked for implementation
Scope: Lobby team crops → player-row mapping → ML Kit + PP-OCRv6 consensus
Engines: Google ML Kit Text Recognition + PP-OCRv6 Small

## 1. Purpose

This document defines the architecture for extracting lobby player names from the already-generated individual team crops.

The preceding team-crop phase is authoritative for producing the compact crop and for associating the crop with the actual OCR-derived team slot number. Each crop contains the slot number at the left, exactly four vertically ordered player positions, two above the slot-number vertical center and two below it, and a compact horizontal player-name area.

The next phase must:

1. use ML Kit to understand the structure of each team crop;
2. establish a slot-number vertical anchor;
3. map text bounding boxes to Row 1, Row 2, Row 3, and Row 4;
4. create four isolated player-row crops;
5. run ML Kit and PP-OCRv6 Small on the exact same row image;
6. combine both candidates with deterministic consensus rules;
7. preserve detailed evidence from both engines;
8. output player data through the existing lobby-player review/data contract; and
9. leave result OCR, scoring, matching, and cloud behavior unchanged.

This is a design decision only. It does not authorize implementation or changes to production source, tests, persistence, or runtime configuration.

## 2. Locked high-level pipeline

```text
Confirmed lobby screenshot
        ↓
existing OCR-guided team cropping
        ↓
individual team crop
        ↓
ML Kit structure pass on team crop
        ↓
find/confirm slot anchor
        ↓
map ML Kit text geometry to four player rows
        ↓
create Row 1 / Row 2 / Row 3 / Row 4 image crops
        ↓
for EACH row crop:
        ├── ML Kit OCR
        └── PP-OCRv6 Small OCR
        ↓
dual-engine consensus
        ↓
resolved player text + complete evidence
        ↓
existing lobby-player review/data flow
```

PP-OCRv6 must not normally run on the full lobby screenshot or the full four-team lobby panel. PP is primarily a player-row OCR engine. The only structural PP use allowed here is the slot-number fallback, and that fallback operates on the smallest practical slot-anchor region.

## 3. OCR-engine responsibilities

### 3.1 ML Kit

ML Kit has two distinct jobs.

#### Job A — team structure pass

Run ML Kit once on the complete team crop to obtain text blocks, lines, elements, bounding boxes, slot-number evidence when possible, and player-name geometry. This pass is structural. Its returned text may be preserved as evidence but is not automatically the final player-name candidate.

#### Job B — row-level OCR candidate

After an isolated player-row image is created, run ML Kit again on that exact row crop. The result is the row's `mlCandidate`.

### 3.2 PP-OCRv6 Small

PP has two permitted responsibilities.

#### Job A — row-level second candidate

Run PP-OCRv6 on the exact same isolated player-row image supplied to ML Kit. The result is the row's `ppCandidate`.

#### Job B — slot-anchor fallback

If the ML Kit structure pass cannot provide usable slot-number geometry, PP may run on a small left/center slot-anchor region from the team crop. PP must not process the full lobby screenshot or four-team panel for this fallback.

## 4. Team identity versus row-mapping anchor

The established team-crop phase already provides the authoritative team identity:

```text
teamSlotNumber = teamCrop.detectedSlotNumber
```

The new structure pass must maintain a separate geometric concept:

```text
slotAnchor = geometry used to divide the team image into upper/lower rows
```

If a new ML or PP structural pass reads a different digit from `teamSlotNumber`:

- retain the conflicting OCR text and geometry as evidence;
- never silently overwrite `teamSlotNumber`;
- use the geometry for row mapping when its bounding box clearly represents the slot-number object; and
- expose the conflict through diagnostic/evidence status where needed.

No screenshot-index or visible-position fallback may manufacture a team slot number.

## 5. Locked slot-anchor priority

Use this exact priority and record the selected source explicitly:

1. `ML_KIT_SLOT`
2. `PP_OCR_SLOT`
3. `TEAM_CROP_CENTER_FALLBACK`

### 5.1 ML Kit slot anchor

From the structure pass, search the appropriate slot-number region, accept only canonical values `1..12`, require usable bounding-box geometry, and select the slot-number bounding box.

For a selected box:

```text
slotAnchorX = (bbox.left + bbox.right) / 2
slotAnchorY = (bbox.top + bbox.bottom) / 2
```

`slotAnchorY` is the value used for row mapping.

### 5.2 PP slot fallback

When ML Kit cannot provide usable slot geometry:

1. create or reuse a small left-middle slot-anchor crop;
2. run PP-OCRv6 Small on that region;
3. accept canonical values `1..12` only;
4. transform the PP bounding box back into team-crop coordinates; and
5. calculate `PP_OCR_SLOT.slotAnchorY` from the transformed box.

Preserve PP text, PP bounds, the coordinate transform, and agreement or conflict with the authoritative `teamSlotNumber`. PP must not replace that identity automatically.

### 5.3 Team-crop center fallback

If neither engine provides usable slot geometry, player-row mapping must continue. The established crop contract guarantees that the slot number is vertically centered:

```text
slotAnchorY = teamCropHeight / 2
slotAnchorSource = TEAM_CROP_CENTER_FALLBACK
```

This is a geometry fallback, not fabricated OCR. Do not invent slot text or a slot bounding box. The existing authoritative `teamSlotNumber` remains associated because it came from the verified team-crop phase.

## 6. Locked player-row geometry

There are exactly four vertical player positions:

```text
Row 1 = upper first row
Row 2 = upper second row
-------- SLOT VERTICAL CENTER --------
Row 3 = lower first row
Row 4 = lower second row
```

Two rows are above `slotAnchorY` and two are below it. Player order must never be derived from OCR return order or text content. It is derived from bounding-box Y geometry.

## 7. Row-band calculation

Given:

```text
top = 0
bottom = teamCropHeight
anchor = slotAnchorY
```

Calculate:

```text
upperHeight = anchor - top
lowerHeight = bottom - anchor
upperSplit = top + upperHeight / 2
lowerSplit = anchor + lowerHeight / 2
```

The bands are:

```text
Row 1: [top, upperSplit)
Row 2: [upperSplit, anchor)
Row 3: [anchor, lowerSplit)
Row 4: [lowerSplit, bottom]
```

Upper and lower heights should normally match because of the crop geometry, but mapping must use the actual anchor and crop bounds. This preserves deterministic behavior under one-pixel rounding.

## 8. Mapping ML geometry to rows

Every candidate ML text object must be expressed in team-crop coordinates. For each bounding box:

```text
centerY = (bbox.top + bbox.bottom) / 2
```

Assign it to the row band containing `centerY`. Recognition order is irrelevant.

### 8.1 Exclude slot-number content

Exclude the selected ML slot geometry, selected PP fallback geometry where relevant, and obvious slot-number-only content in the slot gutter. Use horizontal geometry as a safety filter because the slot number is separated from the player-name region.

### 8.2 Multiple fragments in one row

When ML splits one name into several elements or lines assigned to the same row:

1. sort fragments left-to-right by bounding-box X;
2. preserve every raw fragment and geometry record;
3. create the union bounding box;
4. build structural row text only as evidence; and
5. do not treat structural concatenation as the final player candidate.

The complete isolated row is reprocessed by both engines.

### 8.3 Missing structural text

A missing ML structural line must not eliminate a row. The fixed four-row geometry still creates Row 1..Row 4 crops, allowing row-level ML and PP to attempt recognition independently.

## 9. Row-crop generation

For every structurally valid team crop, create exactly four row regions: Row 1, Row 2, Row 3, and Row 4. A row image may exist even when structural ML text is empty; no player text is fabricated.

### 9.1 Vertical crop

Use the corresponding row band as the safe primary vertical crop. Optional padding is allowed only when it remains within neighboring row boundaries and cannot absorb meaningful adjacent-row text. Prefer no overlap or minimal controlled overlap.

### 9.2 Horizontal crop

The row crop contains the player-name region, not unnecessary slot gutter/background.

When usable slot geometry exists, keep the crop to the right of the slot number using the slot bbox or established gutter boundary and add only a small relative safety margin. When slot geometry is unavailable, reuse an existing team-layout/player-area boundary. Do not invent a new arbitrary fraction when a project-owned layout constant exists.

Use the useful right edge of the already compact team crop. No second aggressive right-side truncation is required.

## 10. Same-pixels rule

This is a hard invariant:

```text
rowBitmap
    ├── ML Kit
    └── PP-OCRv6
```

Both engines receive the exact same bitmap pixels. Do not create engine-specific crops. Do not resize one engine's input differently unless one deterministic preprocessing step is explicitly shared by both. The evidence must represent two interpretations of the same visual row.

## 11. Row OCR execution

For each row in order:

1. create or copy the isolated row bitmap;
2. run ML Kit row OCR;
3. run PP-OCRv6 row OCR;
4. retain both raw results;
5. resolve consensus; and
6. release temporary bitmap memory only after both engines complete.

Cancellation must propagate normally. A failure in one engine must not discard a usable result from the other.

## 12. Candidate extraction

Each engine produces one deterministic candidate string while preserving:

- raw engine text;
- block, line, and element evidence where available;
- useful bounding boxes; and
- empty/failure status.

Prefer coherent recognized line text. If text is split, sort fragments left-to-right and collapse structural whitespace safely. Do not perform destructive character substitutions.

## 13. Safe comparison normalization

Only comparison normalization is permitted:

- Unicode normalization consistent with project conventions;
- trim leading/trailing whitespace;
- collapse repeated whitespace; and
- case-insensitive comparison for similarity/equality classification where appropriate.

Do not silently normalize `0 ↔ O`, `1 ↔ I`, `1 ↔ l`, `S ↔ $`, `B ↔ 8`, or similar OCR-sensitive pairs. Preserve original ML and PP strings.

## 14. Locked dual-engine consensus

Inputs are `A = ML Kit result` and `B = PP-OCRv6 result`. The resolver is deterministic. Conceptual statuses are:

```text
BOTH_EMPTY
AGREED
ML_ONLY
PP_ONLY
SIMILAR_PP_SELECTED
DISAGREEMENT_PP_SELECTED
```

Names may adapt to repository conventions, but semantics are locked.

### 14.1 Both empty

If both candidates are empty:

```text
resolvedText = null/empty
status = BOTH_EMPTY
```

This is the only basic text-consensus case in which neither engine provides a candidate.

### 14.2 Exact agreement

For equal non-empty candidates:

```text
resolvedText = agreed text
status = AGREED
```

Preserve both engine strings.

### 14.3 ML empty, PP non-empty

```text
resolvedText = PP result
status = PP_ONLY
```

Do not reject PP because ML missed the row.

### 14.4 PP empty, ML non-empty

```text
resolvedText = ML result
status = ML_ONLY
```

Do not discard ML because PP failed or returned empty.

### 14.5 Both non-empty and similar

```text
resolvedText = PP result
status = SIMILAR_PP_SELECTED
```

Preserve ML as evidence. Similarity classification should reuse an existing project Damerau-Levenshtein/similarity implementation where appropriate and use a clearly named threshold. The threshold classifies evidence; it does not alter the winner rule.

### 14.6 Both non-empty and very different

This rule is locked:

```text
resolvedText = PP result
status = DISAGREEMENT_PP_SELECTED
```

Do not force manual review solely because ML and PP disagree. Do not choose ML because it looks more human-readable. Do not create a guessed hybrid string. Whenever both candidates are non-empty, exact agreement is accepted, but PP is selected for both similar and strongly different disagreements.

## 15. Why similarity remains useful

Similarity supports diagnostics, confidence presentation, future evaluation, engine-comparison metrics, screenshot triage, and distinguishing one-character corrections from entirely different recognition. Similar and strongly different evidence classifications remain distinct even though both resolve to PP.

## 16. Proposed row evidence contract

Conceptually:

```text
LobbyPlayerRowOcrEvidence
    teamSlotNumber
    rowIndex: 1..4
    rowBoundsInTeamCrop
    slotAnchorSource
    slotAnchorY
    mlStructureEvidence
    mlRowCandidate
    ppRowCandidate
    resolvedText
    consensusStatus
    similarityScore (when both are non-empty)
    mlFailure (optional)
    ppFailure (optional)
    rowCropDimensions
    uncertainty/diagnostic metadata (optional)
```

Raw bitmap objects must not be persisted in domain data. Bitmap lifetime and evidence metadata are separate concerns.

## 17. Proposed team result contract

Conceptually:

```text
LobbyTeamPlayerOcrResult
    teamSlotNumber
    slotAnchorSource
    slotAnchorEvidence
    Row 1 result
    Row 2 result
    Row 3 result
    Row 4 result
    processingStatus
```

Rows remain explicitly ordered 1..4. Do not reorder alphabetically, by confidence, or by player text.

## 18. Slot-anchor and row-mapping examples

### Example A — ML detects slot

For team slot 5, ML detects slot `5` with `centerY = 180` in a 360-pixel crop:

```text
anchorY = 180
source = ML_KIT_SLOT
upperSplit = 90
lowerSplit = 270
```

Bands are `0..90`, `90..180`, `180..270`, and `270..360`. Text centers `45`, `134`, `223`, and `315` map to Rows 1, 2, 3, and 4 respectively.

### Example B — ML misses, PP detects

PP runs on the small slot region, detects `10`, and transforms its bbox into team coordinates with `centerY = 181`:

```text
anchorY = 181
source = PP_OCR_SLOT
```

The authoritative team slot identity is not changed.

### Example C — both engines miss

For a 360-pixel crop:

```text
anchorY = 180
source = TEAM_CROP_CENTER_FALLBACK
```

Rows still map from ML player-name geometry by Y. No slot OCR result is fabricated.

## 19. Player-row examples

| ML | PP | Resolved | Status |
| --- | --- | --- | --- |
| `NE.ZLUX` | `NE.ZLUX` | `NE.ZLUX` | `AGREED` |
| `VELOCITyHxT` | `VELOCITYHxT` | `VELOCITYHxT` | `SIMILAR_PP_SELECTED` |
| empty | `DARKxKING` | `DARKxKING` | `PP_ONLY` |
| `DARKxKING` | empty | `DARKxKING` | `ML_ONLY` |
| `SOKiNGBOYS` | `KiNGBDY$` | `KiNGBDY$` | `DISAGREEMENT_PP_SELECTED` |

The final row text is never a synthetic blend such as `SOKiNGBDY$`.

## 20. Multiple ML fragments in one row

If ML returns `NE.` and `ZLUX` in the same row:

- order fragments by left X;
- preserve both geometry records;
- union the structural bounds;
- retain `NE.ZLUX` as structural evidence;
- create one complete Row 1 crop; and
- use the row-level dual-engine result, not the structural concatenation, as the final candidate.

## 21. False and noise text

Use geometry before semantic guessing. Reject or filter candidates that belong to the slot gutter, fall outside all row bands, have invalid geometry, or duplicate the same visual text at multiple ML hierarchy levels. Prefer one consistent ML hierarchy, such as lines, and use elements only when needed. Never concatenate block, line, and element text simultaneously to create duplicates.

## 22. Row-mapping ambiguity

Text-consensus disagreement and row-mapping ambiguity are separate issues. PP selection under text disagreement does not excuse ambiguous geometry. Preserve genuine row-ownership ambiguity as evidence. Do not move a clearly mapped Row 2 name to Row 1 merely to fill every row; deterministic row bands should resolve normal cases.

## 23. Four-row completeness

The data model always represents Row 1 through Row 4, while distinguishing an existing row position from usable OCR text. A row may resolve to `BOTH_EMPTY`. Never fabricate a player name or copy a neighboring name into an empty row.

## 24. Engine failure behavior

| Condition | Resolved result | Evidence |
| --- | --- | --- |
| ML fails, PP succeeds | PP / `PP_ONLY` | preserve ML failure |
| PP fails, ML succeeds | ML / `ML_ONLY` | preserve PP failure |
| both fail | empty / `BOTH_EMPTY` equivalent | preserve both failures |
| cancellation | no converted result | propagate cancellation |

Cancellation must not become an OCR-empty result.

## 25. Bitmap lifetime

For each row, create `rowBitmap`, run ML and PP against it, await both operations, and release it only afterward. If operations are concurrent, await both before release. If one fails, complete or safely cancel the other according to the coroutine contract before releasing shared memory. No use-after-recycle behavior is permitted.

## 26. Concurrency and performance contract

Do not launch all 48 rows with unrestricted ML and PP inference. Initial implementation should favor bounded or sequential processing:

```text
for each team:
    structure ML
    for Row 1..4:
        run ML + PP for that row
    continue next team
```

ML and PP for one row may run concurrently only if existing wrappers are safe with the same immutable bitmap. Avoid duplicate PP model initialization, OCR reruns caused by UI recomposition, and unbounded bitmap accumulation.

## 27. Existing downstream player data

After consensus, `resolvedText` flows into the existing lobby-player presentation/data path. Downstream code should not depend directly on PP-specific or ML-specific classes:

```text
dual OCR evidence
        ↓
resolved player text
        ↓
existing player parsing/review/matching boundary
```

Evidence remains separately available for review and diagnostics.

## 28. UI expectation

Keep the existing general lobby-player presentation:

```text
Team / actual slot
- Row 1 player
- Row 2 player
- Row 3 player
- Row 4 player
```

The current large team-crop preview remains intact. Do not redesign Match Review. Evidence details may later expose ML candidate, PP candidate, and consensus status, but a large diagnostics UI is not required for the first implementation.

## 29. Strict non-goals

This phase must not modify:

- existing team-crop geometry, including the 92% horizontal crop rule;
- slot-guided team crop generation or global team-crop ordering;
- result screenshot OCR or result parsing;
- scoring, standings, finalization, matching thresholds, or similarity algorithms except reuse for evidence classification;
- Supabase, screenshot upload/storage, tournament sync, cache, or persistence;
- PP model/runtime integration itself; or
- the old fixed lobby `PLAYER_ROW` architecture as the primary path.

## 30. Implementation component boundaries

Prefer separate responsibilities conceptually similar to:

```text
LobbyTeamStructureDetector
    ML team-crop geometry

LobbySlotAnchorResolver
    ML slot → PP slot fallback → crop-center fallback

LobbyPlayerRowMapper
    bounding-box Y → Row 1..4

LobbyPlayerRowCropper
    four isolated row images

LobbyPlayerDualOcrRunner
    ML + PP on same row bitmap

LobbyPlayerOcrConsensusResolver
    deterministic result selection

LobbyTeamPlayerOcrRunner
    orchestration for one team

Match-level lobby orchestration
    teams → existing UI state
```

Exact class names may follow repository conventions. These responsibilities must not be collapsed into `MatchOcrReviewViewModel`.

## 31. Core invariants

1. Team slot identity comes from established OCR evidence, never screenshot index.
2. The slot number is exactly vertically centered in the team layout.
3. Two player rows are above slot center.
4. Two player rows are below slot center.
5. Row assignment uses OCR bounding-box Y geometry.
6. ML slot geometry is preferred.
7. PP slot geometry is fallback.
8. Team-crop center is the final anchor fallback.
9. Missing slot OCR does not prevent row mapping.
10. Four row positions remain deterministic.
11. ML and PP receive the same row pixels.
12. Both candidates remain evidence.
13. Non-empty agreement is accepted.
14. A single non-empty candidate is used.
15. Similar non-empty candidates resolve to PP.
16. Strongly different non-empty candidates resolve to PP.
17. Strong disagreement alone does not force manual review.
18. No hybrid string is synthesized.
19. Cancellation propagates.
20. Result OCR and downstream scoring remain unchanged.

## 32. Required implementation test matrix

The later implementation must include focused tests for:

### Slot anchor

- ML slot found;
- ML missing and PP slot found;
- both missing and crop-center fallback;
- PP bbox transformation into team coordinates; and
- anchor conflict without overwriting authoritative `teamSlotNumber`.

### Row mapping

- four clean ML lines map Row 1..4;
- shuffled OCR return order still maps by Y;
- two rows above and two below anchor;
- missing structural line does not shift other rows;
- multiple fragments merge deterministically;
- slot geometry is excluded;
- boundary and rounding cases are deterministic; and
- center fallback maps rows correctly.

### Row cropping

- exactly four row regions;
- valid bounds in one team-crop coordinate system;
- slot gutter excluded;
- same source pixels for both engines; and
- missing ML text still produces OCR-able row crops.

### Dual OCR

- identical bitmap dimensions and pixels;
- ML + PP success;
- ML failure with PP success;
- PP failure with ML success;
- both empty;
- cancellation; and
- release after both engines complete.

### Consensus

- exact agreement;
- ML-only;
- PP-only;
- similar → PP;
- strongly different → PP;
- raw evidence preserved;
- no destructive `0/O`, `1/I/l`, or `S/$` normalization; and
- no character blending.

### Orchestration

- PP is not run on the full screenshot or four-team panel;
- PP slot fallback is restricted to the small slot region;
- old fixed `PLAYER_ROW` is not primary;
- team slot association is retained;
- Row 1..4 order is retained; and
- UI recomposition does not rerun OCR.

### Downstream and UI

- resolved names appear under the correct team;
- rows remain ordered 1..4;
- empty rows remain explicitly empty;
- team-crop preview remains available; and
- result OCR remains unchanged.

## 33. Final locked flow

```text
TEAM CROP
   ↓
ML KIT STRUCTURE PASS
   ↓
SLOT ANCHOR
   ├── ML slot found → ML bbox centerY
   ├── ML missing → PP small-slot fallback
   └── both missing → exact team-crop vertical center
   ↓
FOUR VERTICAL ROW BANDS
   ↓
ML BOUNDING-BOX Y MAPPING
   ↓
ROW 1 / ROW 2 / ROW 3 / ROW 4
   ↓
FOUR ISOLATED PLAYER-ROW IMAGES
   ↓
FOR EACH ROW: ML Kit candidate + PP-OCRv6 candidate
   ↓
CONSENSUS
   ├── equal → accept agreement
   ├── ML empty → PP
   ├── PP empty → ML
   ├── similar → PP
   └── very different → PP
   ↓
RESOLVED PLAYER NAME + ML EVIDENCE + PP EVIDENCE + STATUS
   ↓
EXISTING LOBBY PLAYER REVIEW / DOWNSTREAM CONTRACT
```

## 34. Design decision summary

ML Kit is the structural/layout engine. PP-OCRv6 is the character-detail partner and deterministic winner whenever both engines return different non-empty player-name strings.

The fixed lobby geometry is a safety net: because the slot is exactly vertically centered, player rows remain recoverable even when neither engine recognizes the slot number in the new team-level pass.

The resulting hybrid architecture is:

```text
fixed visual geometry
+ ML Kit bounding-box structure
+ PP fallback structure
+ dual row OCR
+ deterministic consensus
```

It does not change existing result processing, scoring, matching, persistence, or cloud architecture.
