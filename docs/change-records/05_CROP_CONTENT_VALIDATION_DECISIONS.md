# CR-005 — Crop Content Validation Decisions

## Status

**Decisions recorded — implementation not started.**

CR-005 defines how Rank Forge will prevent incorrectly cropped lobby and match-result screenshots from becoming authoritative OCR inputs while preserving the existing OCR extraction, parsing, scoring, screenshot persistence, cloud upload, and review behavior.

Repository baseline for this decision record:

- Branch: `main`
- Baseline commit: `2e0f7b3e532b1d8c67e47cda151c648b705a2d76`
- Baseline date: 2026-08-18

No production Android code, Room schema, Supabase schema, Storage policy, OCR parser, scoring logic, or navigation behavior is changed by this decision record.

---

## 1. Purpose

The current OCR pipeline works correctly when the screenshot crop contains the expected Free Fire MAX panel. The remaining reliability risk is upstream: a user can confirm a crop that is geometrically legal but visually incorrect or incomplete.

CR-005 will add a semantic crop-content validation layer so that Rank Forge distinguishes between:

```text
user-confirmed crop
```

and:

```text
OCR-safe validated crop
```

The primary objective is:

> A crop must not become authoritative OCR input merely because its rectangle is valid and the user pressed Confirm.

---

## 2. Audited Current Baseline

The current effective screenshot flow is:

```text
Select screenshot
    ↓
Preserve original locally
    ↓
Open crop screen
    ↓
User adjusts crop rectangle
    ↓
OcrCropValidator
    ↓
Confirm Crop
    ↓
persistConfirmedCrop()
    ↓
Room crop metadata
    ↓
cloud/upload reconciliation
    ↓
OCR processing
```

The existing `OcrCropValidator` is a geometry validator. It verifies structural properties such as finite coordinates, legal bounds, positive area, source dimensions, and minimum normalized size. It does not prove that the selected rectangle contains the expected Free Fire MAX UI.

The current five-screenshot OCR preflight checks whether each screenshot exists, has a local file, has confirmed crop metadata, and is not currently processing. It does not semantically inspect the crop contents.

The affected normal match workflow contains five OCR-critical screenshots:

```text
Lobby Screenshot 1
Lobby Screenshot 2
Lobby Screenshot 3
Result Screenshot 1 / MATCH_RESULT_UPPER
Result Screenshot 2 / MATCH_RESULT_LOWER
```

---

## 3. Core Architecture Decision

CR-005 adds a second validation layer after crop geometry validation and before a crop is allowed to become authoritative.

The required contract is:

```text
Screenshot
    ↓
Crop editor
    ↓
Draft normalized crop
    ↓
Geometry validation        EXISTING
    ↓
Content/layout validation  NEW
    ↓
VALID only
    ↓
Persist confirmed crop
    ↓
Cloud reconciliation
    ↓
Existing OCR pipeline
```

The existing OCR pipeline remains downstream and should not be redesigned to compensate for arbitrary bad crops.

This is the central CR-005 invariant:

> Crop correctness is an input-contract responsibility, not an OCR-parser recovery responsibility.

---

## 4. Validation Responsibilities

### 4.1 Geometry validation

The existing `OcrCropValidator` remains responsible only for structural rectangle validity:

- finite normalized values;
- image bounds;
- valid left/top/right/bottom ordering;
- non-zero crop area;
- minimum supported crop dimensions;
- valid original image dimensions.

CR-005 must not overload this validator with screenshot-specific semantic logic.

### 4.2 Content validation

A new semantic validation layer will answer:

> Does this crop contain enough evidence of the expected screenshot role and layout to be safe for the existing OCR pipeline?

The new validator must understand screenshot type/role while sharing a common result contract.

---

## 5. Validation Result Contract

The semantic validator must return three top-level outcomes:

```text
VALID
INVALID
INDETERMINATE
```

### VALID

The crop contains sufficient independent structural/OCR evidence for the expected screenshot role.

Only `VALID` may proceed to authoritative crop persistence.

### INVALID

The available evidence indicates that the crop is wrong, incomplete, or does not contain the expected Free Fire MAX panel.

Examples include:

- required panel content is substantially clipped;
- expected layout distribution is absent;
- required structural regions are missing;
- unrelated game UI was selected;
- only a partial expected panel is visible.

### INDETERMINATE

The app could not reliably verify the crop due to a technical validation failure.

Examples include:

- bitmap decode failure;
- invalid prepared bitmap;
- ML Kit recognition failure;
- unexpected validation execution failure.

`INDETERMINATE` must not be silently converted into `INVALID`, because inability to validate is not proof that the user selected the wrong crop.

---

## 6. Validation Evidence Decision

CR-005 will use deterministic on-device evidence already available in the app architecture.

Primary evidence source:

```text
cropped bitmap
    ↓
ML Kit text recognition
    ↓
recognized text + geometry
    ↓
role-specific evidence evaluator
```

The validator must use independent raw recognition/layout evidence rather than final parsed rows or team-matching results.

The validator must not require exact player-name recognition.

The validator must not use tournament roster matching as a crop-correctness signal.

---

## 7. Why Final OCR Output Is Not Sufficient Validation

Result Screenshot 1 / `MATCH_RESULT_UPPER` uses canonical template ownership for positions 1 through 10. The result extractor can therefore produce expected row structure even when placement OCR evidence is incomplete.

For that reason, CR-005 explicitly rejects rules such as:

```text
10 rows returned = valid crop
```

or:

```text
parser produced output = valid crop
```

Crop-content validation must happen using independent evidence before trusting downstream extraction results.

---

## 8. Match Result Upper Validation Decision

`MATCH_RESULT_UPPER` is responsible for positions 1 through 10 in the existing OCR aggregation contract.

The semantic evaluator for this role must assess evidence that the expected upper-result panel is present across the crop, using signals such as:

- placement-anchor observations where available;
- player-text observations in expected row bands;
- kill-column observations where available;
- horizontal/vertical distribution consistent with the canonical upper layout;
- evidence across both expected panel areas rather than concentrated in one unrelated region.

The evaluator must not require every field to be recognized correctly.

A correct crop with ordinary OCR misses must remain eligible if the overall structure is strongly supported.

---

## 9. Match Result Lower Validation Decision

`MATCH_RESULT_LOWER` is authoritative for positions 11 and 12.

The semantic evaluator for this role must verify evidence consistent with the lower-result visual rows and expected placement/player/kill structure.

The existing ownership invariant remains unchanged:

```text
MATCH_RESULT_UPPER → positions 1–10 only
MATCH_RESULT_LOWER → positions 11–12 only
```

Any partial visibility of position 11 in the upper screenshot must never compete with or override the lower screenshot.

CR-005 must not change that aggregation boundary.

---

## 10. Lobby Screenshot Validation Decision

The three lobby screenshots represent deterministic tournament-slot ranges:

```text
Lobby Screenshot 1 → Slots 1–4
Lobby Screenshot 2 → Slots 5–8
Lobby Screenshot 3 → Slots 9–12
```

The existing cropped roster-panel model defines four visible slots arranged in a 2×2 layout. Each slot contains:

- one slot-number region;
- four player-row regions.

The lobby crop evaluator must validate the presence/distribution of this expected four-slot structure.

The validator must verify structural evidence, not exact roster identity.

A player-name OCR error must not make an otherwise correct lobby crop invalid.

---

## 11. Confirm-Crop Gate Decision

For newly created or edited crops, validation must happen before persistence.

New required flow:

```text
User taps Confirm Crop
    ↓
Check editable/draft match state
    ↓
Check local screenshot availability
    ↓
Geometry validation
    ↓
Semantic content validation
    ↓
VALID?
 ├─ No → remain on crop screen
 └─ Yes
      ↓
      persistConfirmedCrop()
      ↓
      schedule upload/reconciliation
      ↓
      normal navigation callback
```

### INVALID behavior

For `INVALID`:

- do not persist crop metadata;
- do not schedule upload/reconciliation for the attempted crop;
- do not launch downstream OCR from the attempted crop;
- do not invoke the successful confirmation/navigation callback;
- keep the user on the crop screen with actionable correction feedback.

### INDETERMINATE behavior

For `INDETERMINATE`:

- do not persist the attempted crop as authoritative;
- do not silently continue to OCR;
- remain on the crop screen;
- present a verification failure state distinct from a known bad crop.

The first production version will not include a `Use Anyway` bypass for OCR-critical screenshots.

---

## 12. Defensive OCR Gate Decision

A confirmation-time validator alone is insufficient because Rank Forge may encounter:

- crops created by older app versions;
- restored crops from cloud metadata;
- already-existing local crops;
- old OCR cache entries created before CR-005.

Therefore the OCR processing boundary must defensively enforce the same semantic crop contract before trusting stored crop metadata.

Required behavior:

```text
stored confirmed crop
    ↓
semantic crop validation
    ↓
VALID
    ↓
existing OCR extraction
```

Non-VALID stored crops must not be treated as safe OCR input.

---

## 13. OCR Cache Versioning Decision

Crop-content validation becomes part of the OCR correctness contract.

Therefore implementation of the defensive OCR gate must invalidate pre-CR-005 cache evidence by incrementing the applicable OCR pipeline version.

At minimum:

```text
MATCH_RESULT_OCR_CACHE_PIPELINE_VERSION
MATCH_LOBBY_OCR_CACHE_PIPELINE_VERSION
```

must be incremented when the corresponding defensive gate becomes active.

Changing crop coordinates already changes the cache fingerprint; the pipeline-version bump protects unchanged legacy crops/caches that predate semantic validation.

No destructive database migration is required solely to invalidate OCR caches.

---

## 14. Persistence and Cloud Decision

CR-005 v1 will use semantic validation as an execution gate rather than adding persistent validation-status columns.

The first implementation must not add:

- Room `crop_validation_status` columns;
- Supabase crop-validation columns;
- new Storage policy rules;
- new RPCs solely for validation state.

Reason:

- the safety problem can be solved before crop persistence;
- avoiding new schema state keeps the initial blast radius small;
- validation evidence can be persisted later only if performance or restoration requirements justify it.

Existing crop coordinates and crop profile metadata remain the authoritative persisted crop representation.

---

## 15. ML Kit Decision

CR-005 will reuse the existing on-device ML Kit text-recognition infrastructure.

The first implementation will not introduce:

- cloud vision APIs;
- paid AI APIs;
- OpenCV dependency;
- TensorFlow model;
- remote inference;
- a second OCR engine.

The validator must remain offline-capable and deterministic within the limits of ML Kit recognition.

---

## 16. Threshold Calibration Decision

CR-005 will not invent hard acceptance thresholds before calibration.

The implementation must first evaluate known-good and intentionally bad crop fixtures.

Positive fixture categories must include:

- correct crop;
- slightly loose crop;
- small positional variation;
- supported screenshots at different original resolutions.

Negative fixture categories must include:

- top clipped;
- bottom clipped;
- left clipped;
- right clipped;
- partial panel;
- unrelated game UI;
- full screenshot where the expected prepared panel is not isolated sufficiently;
- empty or nearly empty region where applicable.

Only after observing the evidence distribution may production acceptance thresholds be locked.

The Confirm-Crop semantic gate must not become authoritative before this calibration step is completed.

---

## 17. UI State Decision

The crop screens need explicit validation state separate from save state.

Conceptually, crop UI state should be able to represent:

```text
idle/editing
validation in progress
valid
invalid
indeterminate
saving
```

While semantic validation is running:

- Confirm must not launch duplicate validation requests;
- the crop must not be persisted yet;
- upload/reconciliation must not start yet.

User-visible messaging must distinguish:

```text
Crop is wrong/incomplete
```

from:

```text
Crop could not be verified
```

The existing finalized-match protection remains unchanged.

---

## 18. Concurrency and Cancellation Decision

Crop validation is part of the existing asynchronous screenshot workflow and must preserve cancellation semantics.

Requirements:

- cancellation must propagate rather than be converted into `INVALID`;
- a stale validation result must not persist a crop after the user has edited/replaced the screenshot;
- duplicate Confirm taps must not create duplicate persistence/upload actions;
- screenshot replacement must invalidate the prior crop as the current repositories already do;
- validation must operate against the same screenshot identity and draft crop that are later persisted.

If implementation discovers a generation/fingerprint race not covered by current screenshot mutation coordination, the change must stop for a new decision rather than silently broadening scope.

---

## 19. Implementation Slices

CR-005 implementation must proceed in the following order.

### CV-01 — Validation Contract

Create the pure domain validation contract and structured outcomes/reasons.

No UI, persistence, OCR, cloud, or navigation behavior changes.

### CV-02 — Match Result Evidence Evaluator

Build role-aware semantic evaluation for:

- `MATCH_RESULT_UPPER`;
- `MATCH_RESULT_LOWER`.

Use real positive and negative crop fixtures.

### Calibration Gate

Measure evaluator behavior and lock conservative production thresholds.

No authoritative Confirm gate before this step passes.

### CV-03 — Match Result Confirm Gate

Integrate semantic validation before `persistConfirmedCrop()` for result screenshots.

### CV-04 — Match Result OCR Defensive Gate

Protect legacy/restored result crops and invalidate pre-CR-005 result OCR cache evidence via pipeline-version increment.

### CV-05 — Lobby Evidence Evaluator

Build semantic validation for the existing 2×2 four-slot lobby panel.

### CV-06 — Lobby Confirm Gate

Integrate semantic validation before lobby crop persistence.

### CV-07 — Lobby OCR Defensive Gate

Protect legacy/restored lobby crops and invalidate pre-CR-005 lobby OCR cache evidence via pipeline-version increment.

### Final Verification and Closure

Run focused, regression, and physical-device verification before CR-005 closure.

---

## 20. Required Test Contract

### Domain validation tests

At minimum cover:

- valid upper result crop;
- valid lower result crop;
- valid lobby crop;
- clipped top/bottom/left/right variants;
- unrelated content;
- partial panel;
- recognition failure → `INDETERMINATE`;
- invalid bitmap/decode → `INDETERMINATE`;
- cancellation propagation.

### Confirm-gate integration tests

For `INVALID` and `INDETERMINATE` attempted crops, verify:

```text
persistConfirmedCrop calls = 0
upload/reconciliation scheduling = 0
successful navigation callback = 0
```

Where the crop screen currently triggers immediate debug/preview OCR after persistence, verify that path also does not run for rejected crops.

For `VALID`, verify the existing successful sequence remains intact and occurs exactly once.

### OCR defensive-gate tests

Verify:

- legacy unvalidated bad crop cannot reach trusted OCR extraction;
- valid restored crop can proceed;
- stale old cache evidence is not reused after pipeline-version bump;
- crop-coordinate changes continue to invalidate cache fingerprints.

---

## 21. Physical-Device Verification Contract

Before CR-005 closure, verify on the physical Android device:

1. select Result Screenshot 1;
2. create a correct crop and confirm successfully;
3. select Result Screenshot 2;
4. create an intentionally incorrect crop and confirm that it is blocked;
5. correct the crop and confirm successfully;
6. repeat equivalent valid/invalid checks for Lobby Screenshots 1–3;
7. run Calculate Points;
8. confirm OCR runs only from accepted crops;
9. verify the resulting OCR/scoring workflow still behaves as before for valid crops;
10. replace a screenshot and verify the old crop is no longer authoritative;
11. restart the app and verify persisted valid crop behavior;
12. verify finalized-match screenshot protection remains unchanged.

---

## 22. Explicit Out of Scope

CR-005 does not include:

- automatic screenshot cropping;
- AI-generated crop suggestions;
- OpenCV contour detection;
- remote/cloud image analysis;
- changes to scoring rules;
- changes to team matching;
- OCR parser redesign;
- changes to the upper 1–10 / lower 11–12 ownership contract;
- Room schema changes for persistent validation status;
- Supabase schema changes for persistent validation status;
- Storage policy loosening;
- full restoration redesign;
- unrelated Review Match UI refactors.

Automatic/suggested cropping may be considered as a separate follow-up after CR-005 proves the validation gate reliable.

---

## 23. Scope-Control Rule

Before every implementation slice:

1. inspect the exact current files on `main`;
2. freeze the smallest necessary existing-file list;
3. list any new files separately;
4. implement only that approved slice;
5. stop if another existing file unexpectedly becomes necessary;
6. run focused tests before broader regression tests;
7. inspect `git diff --check`, `git status --short`, and changed-file scope before commit.

No opportunistic OCR cleanup or refactor is allowed inside CR-005.

---

## 24. Rollback Decision

The first implementation is intentionally designed for low-cost rollback.

If semantic crop validation proves unreliable, the integration gates can be removed while leaving unchanged:

- existing OCR extraction/parsing;
- existing crop coordinate storage;
- Room schema;
- Supabase schema;
- screenshot Storage behavior;
- scoring;
- team matching;
- navigation.

Cache pipeline-version increments are safe and do not require data rollback.

No destructive migration should be necessary for CR-005 v1.

---

## 25. Completion Criteria

CR-005 is complete only when all of the following are true:

- semantic result-crop validation is calibrated and enforced;
- semantic lobby-crop validation is calibrated and enforced;
- invalid/indeterminate crops cannot become authoritative through normal Confirm flow;
- legacy/restored crop paths are defensively protected before trusted OCR use;
- pre-CR-005 OCR caches cannot bypass the new correctness contract;
- valid crops continue through the existing OCR pipeline without regression;
- screenshot replacement and finalized protection remain correct;
- focused unit tests pass;
- required regression tests pass;
- physical-device valid/invalid crop scenarios pass;
- final changed-file audit is clean;
- CR-005 closure record is merged;
- repository returns to clean synchronized `main`.

---

## 26. Locked Invariants

The following invariants are locked for implementation:

1. **Existing OCR correctness for valid crops must be preserved.**
2. **Geometry validation and semantic validation remain separate responsibilities.**
3. **Only semantically `VALID` new crops may become authoritative.**
4. **`INDETERMINATE` is not equivalent to `INVALID`.**
5. **No `Use Anyway` bypass in the first production version.**
6. **Result Upper remains authoritative only for positions 1–10.**
7. **Result Lower remains authoritative only for positions 11–12.**
8. **Lobby validation checks structure, not exact roster-name correctness.**
9. **No Room or Supabase validation-status schema in CR-005 v1.**
10. **No OpenCV, remote AI, or new OCR engine in CR-005 v1.**
11. **No authoritative thresholds before calibration.**
12. **Existing screenshot replacement, cloud reconciliation, and finalized protection must not be weakened.**
13. **Existing OCR/cache behavior may only change where required to enforce the new input contract.**
14. **Implementation proceeds CV-01 through CV-07 in order, one controlled slice at a time.**
