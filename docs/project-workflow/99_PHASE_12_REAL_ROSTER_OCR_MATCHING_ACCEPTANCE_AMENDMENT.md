# Phase 12 — Real Roster OCR Matching Acceptance Amendment

## 1. Purpose

This document amends only the roster-player accuracy and ground-truth requirements in:

`98_PHASE_12_REAL_ROSTER_OCR_ACCEPTANCE_EVALUATION_DECISIONS.md`

All privacy, genuine-data, crop, slot-association, review-safety, dataset-freeze,
defect-handling, and no-silent-production-change requirements from document 98 remain active.

This work remains the non-numbered Phase 12 Real Roster OCR Acceptance Evaluation.

It is not v0.12.10.

---

## 2. Product Goal Clarification

Roster player names are intermediate matching evidence.

They are not final user-facing text and do not need to reproduce every decorative
symbol, capitalization choice, spacing difference, or OCR-sensitive glyph exactly.

The relevant product question is:

Can genuine roster screenshot OCR produce player-name evidence that allows the
existing Rank-Forge normalization, similarity, candidate scoring, confidence,
and assignment-safety pipeline to identify the correct team from genuine result
screenshots?

Therefore exact player-name transcription accuracy is not the primary acceptance metric.

---

## 3. Superseded Requirements

Sections 11 through 14 of document 98 are superseded only where they require:

- manually transcribing every roster player name as acceptance ground truth
- exact displayed player-name correctness as the primary acceptance result
- a >=95% exact roster-player OCR transcription threshold

A separate 48-row exact-name ground-truth file is not required.

Exact and normalized roster OCR text may still be inspected locally as diagnostics.

No real player names may appear in committed acceptance evidence.

---

## 4. Genuine Acceptance Inputs

The acceptance case uses:

### Roster evidence

Three genuine cropped roster screenshots:

- screenshot 1 -> roster slots 1-4
- screenshot 2 -> roster slots 5-8
- screenshot 3 -> roster slots 9-12

### Result evidence

The existing genuine result-screen acceptance case and its already approved
team-slot ground truth may be reused when it represents the same tournament case.

Private screenshots, raw OCR text, player names, and local ground truth remain
local-only and must not be committed.

---

## 5. Acceptance Pipeline

The genuine acceptance path is:

genuine roster screenshots
-> roster ML Kit OCR
-> roster candidate parsing
-> roster slot association
-> OCR-derived roster player evidence for slots 1-12
-> genuine result screenshot OCR
-> existing player normalization and similarity matching
-> team candidate scoring
-> confidence assessment
-> assignment safety
-> expected team identification comparison

The acceptance harness must reuse existing production algorithms.

It must not duplicate or tune them specifically for the acceptance dataset.

---

## 6. Primary Acceptance Metrics

### A. Roster slot association

Required:

`100%`

All OCR-derived roster evidence must remain associated with the correct deterministic
tournament slot.

Any wrong slot association is acceptance-blocking.

### B. Downstream team identification

The primary functional metric is correct team identification on the genuine
result-screen acceptance rows using the OCR-derived roster evidence.

The same approved team-identification threshold used by the existing genuine
scoreboard acceptance evaluation applies.

### C. False automatic assignments

Required:

`0`

An incorrect team must never be considered a safe automatic assignment.

---

## 7. Roster OCR Diagnostics

The report may additionally record sanitized counts for:

- player-row regions processed
- extracted player candidates
- empty player candidates
- failed player candidates
- parsed candidates
- candidates requiring manual review
- unusable candidates
- roster slots with sufficient matching evidence
- roster slots requiring correction

These are diagnostics, not exact-name transcription requirements.

No player names or raw OCR payloads may appear in the committed report.

---

## 8. Review and Correction Safety

The existing safety requirements from document 98 remain unchanged.

OCR candidates remain non-authoritative.

Incorrect or incomplete candidates must remain reviewable and correctable.

Explicit confirmation remains required before roster replacement.

Existing-match protection must not be bypassed.

---

## 9. Harness Boundary

After this amendment is merged, the intended implementation remains test-only.

The smallest expected boundary is one new Android instrumentation acceptance test
that combines genuine roster OCR with the existing genuine result-screen matching
evaluation.

No production OCR, parser, matching, Room, Supabase, scoring, navigation, or UI
changes are authorized by this amendment.

---

## 10. Final Acceptance Evidence

The committed report should contain only sanitized metrics such as:

- roster screenshot count
- roster slot count
- player regions processed
- usable roster player evidence count
- manual-review count
- slot-association percentage
- evaluable result-row count
- correct team identifications
- incorrect team identifications
- team-identification percentage
- false automatic assignments
- PASS / FAIL / BLOCKED

No player names are required in committed evidence.