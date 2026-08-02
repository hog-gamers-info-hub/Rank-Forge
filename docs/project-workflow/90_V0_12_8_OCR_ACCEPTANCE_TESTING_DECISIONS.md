# Phase 12 v0.12.8 — OCR Acceptance Testing Decisions

## Status

**Approved to establish the acceptance protocol.**

Actual genuine-screenshot acceptance execution remains gated on approval of the specific genuine screenshot set and corresponding manually verified ground truth.

## Version

**Phase 12 — Quality Assurance and Security Validation**

**v0.12.8 — OCR Acceptance Testing**

Canonical scope:

> Verify team-identification accuracy against the approved genuine screenshot set.

---

## 1. Purpose

v0.12.8 provides the formal acceptance measurement for the implemented Rank-Forge scoreboard OCR and team-identification pipeline.

This version must determine whether the current implemented workflow achieves:

```text
team-identification accuracy >= 95%
```

on the complete approved genuine Free Fire MAX scoreboard test set.

The acceptance result must be based on genuine screenshots and manually verified ground truth.

Synthetic screenshots, fabricated OCR text, synthetic parser output, and synthetic rosters may continue to support automated regression testing, but they cannot be counted as genuine OCR acceptance evidence.

---

## 2. Existing Canonical Acceptance Requirement

Canonical testing documentation requires:

- genuine Free Fire MAX scoreboard screenshots
- manually verified ground truth
- field-by-field OCR comparison
- separate measurement of:
  - placements
  - player names
  - kills
  - team identification

- screenshot-quality recording
- correction recording
- reproducible results
- at least 95% team-identification accuracy
- 100% scoring accuracy after operator confirmation

v0.12.8 evaluates the team-identification target.

It does not replace the existing placement, player-name, kill, review-marker, scoring, or manual-correction requirements.

---

## 3. Relationship to v0.8.8

Phase 8 v0.8.8 created a test-only real-screenshot evaluation boundary.

Existing evaluator:

```text
app/src/test/java/com/hoggamers/rankforge/ocr/evaluation/RealScreenshotEvaluator.kt
```

The existing evaluator measures:

- row coverage
- placement correctness
- player-name correctness
- kill correctness
- review-marker correctness
- false accepts

It does not currently establish the Phase 12 team-identification accuracy requirement.

v0.12.8 may extend test-only acceptance infrastructure to evaluate the Phase 9 team-matching output.

The existing v0.8.8 evaluator must not be rewritten merely to improve acceptance numbers.

---

## 4. Pipeline Under Acceptance Test

The genuine acceptance path must evaluate the implemented production behavior conceptually as:

```text
genuine scoreboard screenshot
-> image validation
-> supported-layout handling
-> preprocessing
-> ML Kit raw OCR extraction
-> placement/player-name/kill parsing
-> OCR review/failure analysis
-> player-name normalization
-> player similarity matching
-> team candidate scoring
-> top-three suggestions
-> confidence classification
-> assignment-safety evaluation
```

Where the current production workflow exposes equivalent integration boundaries, the acceptance test should use those production implementations rather than reimplementing matching formulas in the test.

No test-only shortcut may bypass OCR and then claim end-to-end OCR team-identification accuracy.

---

## 5. Genuine Screenshot Requirement

Every acceptance image must be a genuine Free Fire MAX scoreboard screenshot.

Not allowed as acceptance evidence:

- fabricated scoreboard images
- manually drawn scoreboards
- synthetic OCR text pretending to originate from an image
- test doubles replacing genuine ML Kit recognition
- generated screenshots
- screenshots edited to make OCR artificially easier
- screenshots without manually verified ground truth

Synthetic fixtures remain valid for ordinary regression tests but are excluded from the v0.12.8 accuracy numerator and denominator.

---

## 6. Approved Screenshot Set Gate

There is currently no repository-tracked genuine acceptance corpus approved for v0.12.8.

Before acceptance execution, the user must explicitly approve the specific screenshot set.

Approval must identify the screenshots by sanitized case identity rather than by exposing real player names in committed documentation.

Example local identities:

```text
match-case-01-a
match-case-01-b
match-case-02-a
match-case-02-b
```

The actual filenames do not need to be committed.

Only screenshots explicitly included in the approved set may contribute to the final accuracy figure.

Once approved, the complete set must be evaluated. Individual difficult screenshots must not be removed after results are known merely to improve accuracy.

---

## 7. Two-Screenshot Match Handling

Rank-Forge match evidence may use two genuine screenshots for the same match where one screenshot does not make every lower placement sufficiently visible.

An acceptance case may therefore consist of:

```text
one match
-> screenshot A
-> screenshot B
-> one combined manually verified 12-team ground truth
```

Overlapping team rows between screenshots must not be counted twice.

The evaluation must identify one canonical result row per expected team/placement for the match.

Screenshot pairing must not silently substitute values from ground truth into OCR output.

---

## 8. Privacy Policy

Genuine screenshots remain private evaluation evidence.

Default policy:

```text
LOCAL ONLY
NOT COMMITTED
```

Do not commit:

- genuine screenshots
- real player names
- raw OCR text containing real names
- local screenshot paths
- device gallery paths
- storage URLs
- private tournament identifiers
- unsanitized evaluation logs

Committed documentation may contain only:

- sanitized case IDs
- aggregate metrics
- mismatch categories
- counts
- quality classifications
- sanitized defect descriptions

If repository inclusion of any genuine fixture is ever desired, it requires a separate explicit privacy approval.

---

## 9. Ground-Truth Requirement

Every approved match case must have manually verified expected data before evaluation begins.

Ground truth must include, for each of the 12 result rows where determinable:

- expected placement
- expected team identity
- expected visible player names used for matching
- expected kill value where required for OCR measurement
- field visibility state
- constrained/missing state
- screenshot source within the pair where applicable

Ground truth must be created by human inspection of the genuine screenshot evidence.

Ground truth must not be derived from Rank-Forge OCR output.

---

## 10. Ground-Truth Immutability

After an acceptance run begins, the expected team identity must not be changed merely because Rank-Forge produced a different result.

If human ground truth itself is discovered to be wrong or ambiguous:

1. stop that case
2. re-inspect the original screenshot
3. correct the human annotation
4. record that the ground-truth correction occurred
5. rerun the case

Ground-truth correction must be distinguishable from application correction.

---

## 11. Team-Identification Unit of Measurement

The team-identification denominator is the set of canonical expected result rows in the complete approved genuine screenshot set for which a human team identity is known.

For each such expected row, Rank-Forge produces a team-matching outcome.

A row is counted as **correctly identified** when:

```text
rank-1 team candidate == manually verified expected team
```

A row is counted as **incorrectly identified** when:

- rank-1 candidate is another team
- no candidate is produced
- the row is unmatched
- OCR failure prevents Rank-Forge from identifying the expected team

This ensures the metric measures the complete implemented OCR-to-team-identification pipeline rather than only easy cases that successfully reached the matcher.

---

## 12. Why Rank-1 Candidate Is the Accuracy Metric

The canonical requirement is team-identification accuracy, not automatic-assignment rate.

Confidence tier and assignment-safety eligibility are separate properties.

Therefore:

- correct rank-1 candidate in `AUTOMATIC` tier = correct identification
- correct rank-1 candidate requiring confirmation = correct identification
- correct rank-1 candidate in manual-required state = correct identification, while still recorded as low-confidence/manual
- wrong rank-1 candidate = incorrect identification regardless of confidence

Automatic-assignment behavior must be reported separately.

---

## 13. Accuracy Formula

The canonical aggregate metric is:

```text
team identification accuracy =
correct rank-1 team identifications
/
all manually verified evaluable team rows in the approved genuine set
× 100
```

Acceptance threshold:

```text
>= 95.00%
```

Do not round a value below 95% upward to claim acceptance.

Report:

- numerator
- denominator
- exact percentage to at least two decimal places

---

## 14. Per-Match Reporting

In addition to the aggregate accuracy, report each sanitized match case separately.

For each match:

- expected team rows
- correctly identified teams
- incorrect teams
- unmatched teams
- automatic-tier rows
- confirmation-required rows
- manual-required rows
- false automatic assignments
- screenshot-quality category
- relevant OCR failure categories

Do not expose real team/player names in committed reports.

---

## 15. Automatic-Assignment Safety Measurement

Team-identification accuracy and automatic-assignment safety must remain separate.

For every row, record whether the existing assignment-safety evaluator permits automatic assignment.

A false automatic assignment occurs when:

```text
automatic assignment is permitted
AND
assigned/rank-1 candidate != expected team
```

False automatic assignments must be explicitly reported.

They must never be hidden inside the aggregate accuracy percentage.

Any false automatic assignment is a safety finding requiring review before v0.12.8 is accepted.

Do not weaken:

- confidence thresholds
- three-of-four player-match requirement
- lead-margin requirement
- unique-team assignment rule
- duplicate-player safety rules

to improve the metric.

---

## 16. Confidence-Tier Metrics

Report counts for:

```text
AUTOMATIC
CONFIRMATION_REQUIRED
MANUAL_REQUIRED
```

Also report team-identification correctness within each tier.

This shows whether the confidence classifier accurately separates stronger and weaker matches.

The 95% requirement remains the aggregate team-identification target.

---

## 17. Top-Three Measurement

For diagnostic purposes also report:

```text
expected team at rank 1
expected team at rank 2
expected team at rank 3
expected team absent from top 3
```

Only rank-1 correctness contributes to the canonical 95% team-identification numerator.

Top-three inclusion must not be substituted for rank-1 accuracy.

---

## 18. OCR Field Metrics

The acceptance run should retain the existing v0.8.8 field metrics where practical:

- placement correctness
- player-name correctness
- kill correctness
- review-marker correctness
- false-accept count

These metrics help explain team-identification failures.

They are diagnostic metrics and do not replace the canonical team-identification percentage.

---

## 19. Screenshot Quality Classification

Every screenshot must be categorized before or during ground-truth preparation using descriptive categories such as:

```text
CLEAR
COMPRESSED
LOW_BUT_USABLE
PARTIALLY_CONSTRAINED
```

The report should retain counts by quality category.

Do not reclassify a screenshot after seeing OCR results merely to exclude a failure.

Unsupported-layout screenshots should be recorded separately rather than silently included as ordinary supported-layout failures.

---

## 20. Supported Layout Boundary

The acceptance test applies to the currently approved Free Fire MAX scoreboard layout.

A screenshot that genuinely uses another layout must be classified as:

```text
UNSUPPORTED_LAYOUT
```

It must not be used to tune crop coordinates or parser rules during the same acceptance run.

If the approved screenshot set contains an unexpected genuine layout variant, record it as a compatibility finding and decide separately whether the MVP supports it.

---

## 21. Position 12 / Constrained Rows

Lower scoreboard rows that are obscured or constrained must not be fabricated.

Where two screenshots of the same match collectively expose the lower result rows, use the appropriate genuine screenshot evidence for those rows.

Where a field remains genuinely unreadable:

- retain the human visibility annotation
- record the OCR/review behavior
- do not invent a player name
- do not silently remove the row solely to protect accuracy

If team identity itself cannot be manually verified, the row is not valid ground truth for the team-identification denominator and must be explicitly counted as a ground-truth limitation.

---

## 22. Correction Policy

The raw acceptance metric must be measured **before operator correction**.

Manual correction may then be exercised separately to verify workflow recoverability.

Record:

- raw team-identification result
- whether correction was required
- whether the correct team could be selected manually
- whether finalization remained safely blocked until conflicts were resolved

A corrected result must never be counted as a successful raw OCR/team identification.

---

## 23. Scoring Boundary

The canonical requirement remains:

```text
scoring accuracy after operator confirmation = 100%
```

v0.12.8 may record scoring/finalization observations if the acceptance workflow reaches them, but it must not change deterministic scoring rules.

A scoring defect discovered during OCR acceptance is a separate blocking defect and must receive its own regression patch.

---

## 24. Acceptance Dataset Integrity

Once the screenshot set and ground truth are approved:

- evaluate every approved case
- do not discard failures
- do not replace hard screenshots with easier ones
- do not change expected values based on Rank-Forge output
- do not tune implementation and then report only post-tuning results without recording the original failure
- preserve sanitized before/after evidence for any required defect patch

---

## 25. Existing Test Infrastructure

Existing test-side Phase 8 evaluation code should be reused where appropriate:

```text
app/src/test/java/com/hoggamers/rankforge/ocr/evaluation/RealScreenshotEvaluator.kt
app/src/test/java/com/hoggamers/rankforge/ocr/evaluation/RealScreenshotEvaluatorTest.kt
```

Existing production Phase 9 matching components should remain authoritative for:

- normalization
- player similarity
- team candidate scoring
- top-three ranking
- confidence classification
- assignment safety

Do not create a second matching algorithm solely for acceptance testing.

---

## 26. Expected v0.12.8 Test Infrastructure

After the genuine set is approved, the smallest likely implementation is a **test-only acceptance harness** that:

1. consumes OCR/parser/review output from the genuine local screenshot
2. consumes the manually verified local roster/ground truth
3. invokes existing production matching components
4. records sanitized team-identification outcomes
5. computes aggregate and diagnostic metrics
6. fails acceptance when aggregate team-identification accuracy is below 95%

Exact files must be frozen only after the approved local dataset and current test boundaries are reviewed.

Do not authorize production changes in advance.

---

## 27. Local-Only Genuine Inputs

The default genuine-data location must be outside Git tracking.

The exact local path may be chosen during execution.

Before using it, verify:

```text
git check-ignore
```

or otherwise confirm that the screenshot and ground-truth files cannot be committed accidentally.

No committed test should depend on a developer-specific absolute path.

---

## 28. Evaluation Output Privacy

The committed final report may include:

- sanitized case IDs
- screenshot count
- match-case count
- evaluated team-row count
- accuracy percentages
- confidence-tier counts
- top-three statistics
- false automatic-assignment count
- quality categories
- sanitized mismatch categories
- defect references

It must not include:

- real player names
- private team names where privacy-sensitive
- screenshots
- raw OCR text containing real names
- local filesystem paths
- Supabase storage URLs
- private tournament IDs

---

## 29. Initial Dataset Status

At the start of v0.12.8:

```text
Approved genuine scoreboard acceptance corpus: NOT YET LOCATED / APPROVED
Committed genuine scoreboard fixtures: NONE
Approved fake screenshots: PROHIBITED AS ACCEPTANCE EVIDENCE
Existing synthetic OCR fixtures: REGRESSION ONLY
```

A previously discussed genuine scoreboard screenshot does not automatically become part of this acceptance set.

Explicit dataset approval is required.

---

## 30. Blocking Gate Before Evaluation

Do not start the genuine acceptance run until all are available:

1. the specific genuine scoreboard screenshots
2. explicit approval to use them for v0.12.8
3. their grouping into match cases
4. manually verified team identities
5. the roster corresponding to those matches
6. privacy-safe local handling
7. screenshot-quality annotations

If any required item is missing, v0.12.8 remains:

```text
BLOCKED ON ACCEPTANCE DATA
```

This is an evidence blocker, not a production-code defect.

---

## 31. Defect Handling

If acceptance accuracy is below 95%:

1. do not weaken the threshold
2. do not remove difficult cases
3. classify each failure
4. determine whether the cause is:
   - unsupported layout
   - preprocessing
   - OCR extraction
   - parsing
   - normalization
   - player similarity
   - candidate scoring
   - confidence classification
   - assignment safety
   - incorrect human ground truth

5. create a separate narrowly scoped defect decision
6. add regression coverage
7. rerun the **entire approved genuine set**

The final acceptance number must be from the complete post-fix rerun.

---

## 32. No Silent Production Changes

v0.12.8 acceptance execution must not itself silently modify:

- OCR preprocessing
- layout coordinates
- ML Kit configuration
- parser rules
- normalization
- similarity algorithm
- candidate-scoring formula
- confidence thresholds
- assignment-safety rules
- UI
- Room
- Supabase
- screenshot Storage
- scoring
- exports

Any genuine defect requiring such changes must use a separate explicit patch boundary.

---

## 33. Codex Policy

Do not use Codex to invent the genuine dataset or ground truth.

Do not use Codex for the decision-document stage.

After the genuine dataset is approved, Codex may be used only if a test-only acceptance harness or a genuine production defect patch requires non-trivial implementation.

Codex must never be asked to infer real ground truth from expected application output.

---

## 34. Required Verification Before Acceptance

At minimum, after the acceptance harness is ready:

```text
existing OCR/matching unit tests: PASS
acceptance harness tests: PASS
genuine local acceptance execution: COMPLETE
aggregate team-identification metric: calculated
false automatic assignments: reported
full JVM regression: PASS
connected Android regression where required: PASS
git diff --check: PASS
private screenshots remain untracked: CONFIRMED
```

Actual command lines will be frozen after the harness boundary and local dataset method are approved.

---

## 35. Final Evidence Document

The eventual v0.12.8 verification document must contain:

### Dataset

- sanitized case count
- screenshot count
- team-row denominator
- quality-category counts

### OCR metrics

- placement correctness
- player-name correctness
- kill correctness
- review-marker correctness
- false accepts

### Team-matching metrics

- correct rank-1 identifications
- incorrect rank-1 identifications
- team-identification percentage
- rank-2/rank-3 recoveries
- expected team absent from top three

### Safety metrics

- automatic-tier count
- confirmation-required count
- manual-required count
- false automatic-assignment count

### Corrections

- number of rows requiring correction
- sanitized correction categories

### Final outcome

```text
PASS
```

only when:

```text
team-identification accuracy >= 95%
```

and no unresolved acceptance-blocking defect remains.

Otherwise:

```text
FAIL / BLOCKED
```

with exact sanitized reasons.

---

## 36. Separate Roster OCR Acceptance

The Phase 12 roadmap contains a separate:

```text
v0.12.x — Real Roster OCR Acceptance Evaluation
```

v0.12.8 applies to **match scoreboard OCR → team identification**.

Do not mix roster-screenshot OCR acceptance into the v0.12.8 denominator or report.

---

## 37. Acceptance Decision

**v0.12.8 is approved to establish the genuine-scoreboard OCR acceptance protocol.**

Actual execution remains blocked until the specific genuine screenshot set and manually verified roster/team ground truth are explicitly approved.

No production implementation is authorized by this decision document.
