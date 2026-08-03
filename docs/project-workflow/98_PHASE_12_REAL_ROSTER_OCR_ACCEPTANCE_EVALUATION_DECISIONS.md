# Phase 12 — Real Roster OCR Acceptance Evaluation Decisions

## 1. Status

Approved decision gate for the non-numbered Phase 12 real roster OCR acceptance evaluation.

The canonical roadmap currently identifies this work as:

`v0.12.x — Real Roster OCR Acceptance Evaluation`

This work must remain non-numbered.

It must not be renamed to `v0.12.10`.

This document defines the acceptance protocol only. It does not itself authorize production OCR changes, parser changes, layout changes, persistence changes, or genuine-data commits.

---

## 2. Purpose

The purpose of this evaluation is to measure the implemented roster screenshot OCR workflow against approved genuine Free Fire MAX roster screenshots with manually verified expected data.

The evaluation covers the implemented path:

genuine roster screenshots
→ preserved source images
→ operator-approved crops
→ cropped roster-panel preparation
→ ML Kit roster OCR extraction
→ roster candidate parsing
→ screenshot/visible-slot association
→ tournament slots 1–12
→ roster OCR validation
→ operator review/correction
→ explicit confirmed-roster replacement

OCR output remains candidate evidence only.

No OCR result may become authoritative roster data without operator review and explicit confirmation.

---

## 3. Completed Prerequisites

The acceptance evaluation begins only after the completed roster OCR implementation sequence:

### Phase 7

- v0.7.7 — Roster Screenshot Intake
- v0.7.8 — Roster Screenshot Crop Preparation
- v0.7.9 — Roster Screenshot Set Association

### Phase 8

- v0.8.9 — Cropped Roster Layout Definition
- v0.8.10 — Roster Raw OCR Extraction
- v0.8.11 — Roster Team and Player Parsing
- v0.8.12 — Roster Slot Association
- v0.8.13 — Roster OCR Validation

### Phase 9

- v0.9.10 — Roster OCR Review and Correction

### Safe replacement dependencies

- v0.5.8 — Atomic Confirmed Roster Replacement
- v0.6.9 — Revision-Safe Roster Sync Replacement

The acceptance run measures the existing implementation. It must not silently redesign these components.

---

## 4. Genuine Dataset Requirement

Acceptance evidence must use genuine Free Fire MAX roster screenshots.

A complete roster acceptance case consists of:

- exactly three roster screenshots
- one tournament roster
- four visible roster slots per screenshot
- twelve tournament slots in total
- operator-selected crop metadata for every screenshot
- manually verified expected player data
- screenshot-quality classifications

Synthetic screenshots, generated images, fabricated OCR text, mock ML Kit output, or synthetic parser output may remain regression fixtures but cannot contribute to genuine acceptance results.

---

## 5. Dataset Approval Gate

No genuine screenshot may contribute to the acceptance metric until the dataset is explicitly approved.

Each case must receive a sanitized identifier such as:

`roster-case-01`

Each screenshot may be identified locally as:

- `roster-case-01-a`
- `roster-case-01-b`
- `roster-case-01-c`

Real filenames do not need to appear in committed documentation.

Once the approved dataset is frozen and the first acceptance execution begins:

- every approved case must remain in the dataset
- difficult screenshots must not be removed
- failed cases must not be replaced by easier screenshots
- expected results must not be changed to match application output

Ground-truth corrections are permitted only when independent human reinspection proves that the original annotation was incorrect.

Such corrections must be recorded separately.

---

## 6. Privacy Boundary

All genuine acceptance inputs remain:

`LOCAL ONLY`
`NOT COMMITTED`

Do not commit:

- genuine roster screenshots
- real player names
- raw roster OCR text
- manually verified private ground truth
- local filesystem paths
- device gallery paths
- private tournament identifiers
- private crop images
- unsanitized OCR reports

Committed evidence may contain only:

- sanitized case identifiers
- counts
- percentages
- screenshot-quality categories
- failure categories
- correction categories
- sanitized defect descriptions
- PASS / FAIL / BLOCKED outcome

Before any local acceptance dataset is installed, its storage location must be verified as excluded from Git.

---

## 7. Supported Layout Boundary

Acceptance applies only to the currently implemented supported cropped roster-panel layout.

Each genuine screenshot must use the supported layout and operator-approved crop.

An unexpected genuine layout variant must be classified as:

`UNSUPPORTED_LAYOUT`

It must not be used to modify layout coordinates during the same acceptance run.

Any decision to support an additional layout requires a separate defect or compatibility decision.

---

## 8. Crop Policy

Roster OCR must operate only on the operator-selected cropped roster panel.

The acceptance harness must not:

- OCR the complete original screenshot
- automatically infer another crop
- adjust crop coordinates to improve results
- replace the approved operator crop after seeing OCR output

A crop that is discovered to be humanly incorrect before evaluation may be corrected and re-approved.

Crop changes made because OCR performed poorly are not allowed inside the same frozen acceptance run.

---

## 9. OCR Rows Under Measurement

The implemented roster OCR layout contains exactly four OCR-derived player rows per tournament slot.

Therefore the genuine OCR denominator contains only:

- player row 1
- player row 2
- player row 3
- player row 4

for each evaluable tournament slot.

Rows 5 and 6 are manual-only review fields and must not be counted as OCR extraction successes or failures.

Their manual-entry behavior may be verified separately as workflow safety evidence.

---

## 10. Team-Name Boundary

Team names are not an OCR accuracy field in this evaluation.

The implemented v0.9.10 review flow obtains team names from the existing tournament team-slot context rather than inferring team names from unsupported OCR regions.

Therefore:

- team-name OCR accuracy has no numerator or denominator
- team names must not be invented from screenshot text
- current tournament team names may be displayed as review context
- team-name persistence safety may still be verified

---

## 11. Ground Truth

Ground truth must be manually prepared before inspecting Rank-Forge OCR output.

For every expected visible OCR row, local ground truth must identify:

- sanitized case ID
- screenshot position 1–3
- visible slot position 1–4
- expected tournament slot 1–12
- player row 1–4
- manually verified player name
- field visibility state
- screenshot-quality category
- constrained/unreadable status where applicable

Ground truth must come from human inspection of the genuine screenshot.

Rank-Forge output must never be used to generate expected values.

---

## 12. Evaluable and Non-Evaluable Rows

A player row is `EVALUABLE` when a human reviewer can determine the intended visible player name from the approved genuine screenshot.

A row may be marked `NOT_EVALUABLE` only when the source evidence itself is genuinely unreadable or absent.

A row must not be excluded merely because OCR failed.

For every non-evaluable row, the limitation and reason must be recorded.

The number of non-evaluable rows must appear in the final report.

---

## 13. Primary Player OCR Metric

The primary genuine roster OCR accuracy metric is:

correct OCR-derived player candidates
/
all manually verified evaluable OCR player rows
× 100

A candidate is correct only when the candidate presented for that player row represents the manually verified expected player name.

The primary acceptance threshold is:

`>= 95.00%`

Do not round a value below 95 percent upward to claim acceptance.

The report must include:

- correct player rows
- incorrect player rows
- missing player rows
- malformed player rows
- evaluable denominator
- exact percentage to at least two decimal places

---

## 14. Exact and Normalized Diagnostics

The report should separately retain:

- exact displayed candidate correctness
- deterministic normalized-name correctness where the existing production normalizer can be reused without changing the roster OCR algorithm

Normalized correctness is diagnostic only.

It must not be used to hide a visibly incorrect OCR candidate.

The primary acceptance number remains the genuine player-candidate accuracy defined by this document.

---

## 15. Slot-Association Requirement

Screenshot and visible-slot association is deterministic:

- screenshot 1 → tournament slots 1–4
- screenshot 2 → tournament slots 5–8
- screenshot 3 → tournament slots 9–12

Acceptance requires:

`slot association accuracy = 100%`

Any player candidate assigned to the wrong tournament slot is an acceptance-blocking defect.

A slot-association error must not be absorbed into the ordinary player-name accuracy percentage.

---

## 16. Processing Requirement

For every approved supported-layout screenshot, record whether the genuine pipeline reaches:

- source loading
- crop preparation
- ML Kit extraction
- parsing
- association
- validation
- review-state construction

Controlled failure handling is valid product behavior, but a processing failure that prevents OCR of an otherwise valid approved screenshot contributes to the acceptance failure analysis.

Processing failures must not disappear from the denominator merely because no candidate was produced.

---

## 17. Validation and Review-Safety Requirement

The acceptance run must verify that malformed, missing, uncertain, or invalid candidate evidence remains reviewable.

Required safety behavior:

- OCR candidates remain non-authoritative before confirmation
- incorrect candidates can be edited
- incorrect candidates can be cleared
- missing candidate rows can be supplied manually
- fifth and sixth players can remain manual-only
- OCR validation issues remain visible where applicable
- invalid final roster data blocks confirmation
- abandonment leaves the current confirmed roster unchanged
- confirmation remains explicit

---

## 18. Correction Metrics

After recording the raw OCR result, correction workflow recovery may be evaluated separately.

Report:

- number of candidate rows requiring correction
- number of missing rows requiring manual entry
- number of malformed rows
- number of corrections successfully completed
- number of cases that reach a valid 12-slot roster after correction
- number of cases that cannot be safely corrected

A manually corrected player name must never be counted as a successful raw OCR result.

---

## 19. Authoritative-Persistence Safety

Acceptance requires zero instances of:

- OCR candidate persistence without explicit confirmation
- silent replacement of the confirmed roster
- raw OCR evidence being mutated into corrected evidence
- existing-match protection being bypassed
- confirmed roster replacement before final roster validation
- cloud synchronization occurring before successful local replacement

Tolerance:

`0`

Any such occurrence is an acceptance-blocking safety defect regardless of the OCR percentage.

---

## 20. Existing-Match Protection

Where confirmation safety is exercised, the existing v0.5.8 zero-match replacement rule remains authoritative.

If any draft or finalized match exists:

- replacement must remain blocked
- no match may be deleted
- no match may be modified
- no roster replacement may silently continue

This behavior is safety evidence and does not alter the OCR accuracy numerator.

---

## 21. Raw Evidence Separation

The evaluation must preserve a logical distinction between:

- original genuine screenshot
- approved crop
- raw OCR evidence
- parsed candidate
- associated tournament slot
- validation result
- corrected review value
- confirmed roster value

The acceptance harness must not overwrite raw OCR evidence with corrected data.

---

## 22. Screenshot Quality

Every approved screenshot must receive a quality classification before or during ground-truth preparation.

Approved descriptive categories may include:

- `CLEAR`
- `COMPRESSED`
- `LOW_BUT_USABLE`
- `PARTIALLY_CONSTRAINED`

Quality categories must not be changed after seeing OCR results merely to excuse a failure.

The final report must include counts and OCR results by quality category.

---

## 23. Acceptance Outcome

The real roster OCR acceptance result is `PASS` only when all of the following are true:

- genuine player OCR accuracy is at least 95.00%
- slot association is 100%
- zero silent authoritative OCR persistence occurs
- zero existing-match safety bypasses occur
- all approved genuine cases are evaluated
- private evidence remains outside Git
- no unresolved Critical or High acceptance defect remains
- required regression verification passes

Otherwise the result is:

`FAIL`

or:

`BLOCKED`

with exact sanitized reasons.

---

## 24. Defect Handling

If the acceptance run fails:

1. preserve the complete approved dataset
2. preserve the original pre-fix aggregate metrics
3. classify the failure
4. determine the responsible boundary

Possible categories include:

- source loading
- crop preparation
- roster layout
- ML Kit extraction
- player-row extraction
- candidate parsing
- slot association
- candidate validation
- review-state construction
- correction behavior
- confirmation safety
- incorrect human ground truth

5. create a separate narrowly scoped defect decision
6. add appropriate regression coverage
7. implement only the approved patch
8. rerun the complete approved genuine dataset

Do not report only the successful post-fix cases.

---

## 25. No Silent Production Changes

Acceptance execution itself does not authorize changes to:

- roster layout coordinates
- crop behavior
- ML Kit configuration
- raw OCR extraction algorithms
- parser rules
- slot association
- validation policy
- roster validator
- local replacement
- cloud replacement
- Room schema
- Supabase schema
- sync queue
- match processing
- scoring
- exports

Any required production change receives a separate defect boundary.

---

## 26. Acceptance Harness Strategy

After the genuine dataset is approved, perform a read-only boundary review.

The smallest likely implementation is a test-only Android instrumentation acceptance harness modeled after the existing genuine scoreboard OCR acceptance pattern.

The roster harness should:

1. consume the approved local private dataset
2. verify the approved dataset identity/integrity
3. execute genuine Android/ML Kit roster OCR
4. reuse existing production crop/OCR/parser/association/validation boundaries
5. compare results against local manually verified ground truth
6. compute sanitized metrics
7. write a sanitized local report
8. fail when the approved acceptance requirements are violated

It must not duplicate production roster OCR algorithms.

No exact implementation files are authorized until that read-only review is complete.

---

## 27. Local Dataset Installation

The exact local genuine-data location remains unfrozen until the acceptance harness boundary is reviewed.

Before any dataset is used:

- confirm its location is ignored or outside Git
- verify with `git check-ignore` where applicable
- ensure no committed test depends on a developer-specific absolute path
- confirm private screenshots do not appear in `git status`

The existing scoreboard acceptance strategy may be reused structurally where appropriate, but roster-specific metrics and models remain authoritative here.

---

## 28. Required Verification

Before claiming acceptance:

- approved genuine roster dataset: CONFIRMED
- manually verified ground truth: CONFIRMED
- dataset frozen before run: CONFIRMED
- genuine ML Kit execution: COMPLETE
- all approved cases evaluated: COMPLETE
- player OCR metric calculated
- slot-association metric calculated
- correction metrics calculated
- safety violations reported
- full JVM regression: PASS
- required Android build checks: PASS
- genuine connected-device acceptance: PASS
- `git diff --check`: PASS
- genuine screenshots remain untracked: CONFIRMED
- real player names remain uncommitted: CONFIRMED

---

## 29. Final Sanitized Evidence

The eventual committed verification document may include:

### Dataset

- sanitized case count
- screenshot count
- evaluable OCR player-row count
- non-evaluable row count
- screenshot-quality counts

### OCR

- correct player rows
- incorrect player rows
- missing player rows
- malformed player rows
- player OCR accuracy percentage

### Association

- correct slot associations
- incorrect slot associations
- slot-association percentage

### Review and correction

- rows requiring correction
- missing rows manually supplied
- successful corrections
- cases producing a valid roster after correction

### Safety

- unauthorized/silent persistence count
- existing-match bypass count
- confirmation-safety failures
- abandonment-mutation failures

### Result

- `PASS`
- `FAIL`
- `BLOCKED`

No private names, screenshots, OCR payloads, or local paths may appear.

---

## 30. Codex Policy

Do not use Codex to:

- choose genuine screenshots
- fabricate genuine screenshots
- infer ground truth from application output
- create private player-name ground truth
- modify acceptance thresholds after seeing results

Codex may be used later only if the approved test-only harness or a separate defect patch is sufficiently complex to justify it.

Manual implementation remains preferred for small acceptance infrastructure changes.

---

## 31. Acceptance Decision

The non-numbered Phase 12 Real Roster OCR Acceptance Evaluation is approved to proceed to dataset preparation and acceptance-harness boundary review under this protocol.

No genuine acceptance result is claimed by this document.

No production change is authorized.

The next gate is:

1. approve the genuine three-screenshot roster dataset
2. prepare manually verified local ground truth
3. verify private local handling
4. perform a read-only acceptance-harness boundary review
5. freeze exact test files and commands before implementation
