# Phase 12 v0.12.4 — Compose UI Tests Decisions

## Status

**Approved for implementation after this decision document is merged.**

## Version

**Phase 12 — Quality Assurance and Security Validation**

**v0.12.4 — Compose UI Tests**

Canonical scope:

> Test navigation, data entry, correction, validation, and finalization screens.

---

## 1. Purpose

Complete the remaining material Compose UI coverage for Rank Forge's implemented tournament-processing workflows.

Existing Compose instrumentation already provides substantial coverage for:

- navigation
- tournament creation
- roster entry
- roster review
- match creation
- placement entry
- kill entry
- match review
- match finalization
- finalized read-only behavior
- correction entry
- match OCR review

The focused coverage review identified only two material remaining UI gaps:

1. complete correction submission through navigation back to corrected finalized review
2. correction-specific validation presentation and submission blocking

v0.12.4 therefore remains a narrowly scoped Compose instrumentation test version.

No production behavior changes are approved.

---

## 2. Core Decision

Modify exactly two existing Compose instrumentation test files:

```text
app/src/androidTest/java/com/hoggamers/rankforge/presentation/navigation/RankForgeNavigationTest.kt

app/src/androidTest/java/com/hoggamers/rankforge/presentation/screen/MatchCorrectionScreenTest.kt
```

Create no new test files.

Modify no production files.

---

## 3. Existing Compose UI Baseline

The existing instrumentation suite already covers the following materially implemented UI areas.

### Navigation

`RankForgeNavigationTest.kt` already exercises navigation through:

- tournament creation
- tournament list/details
- roster setup
- roster review
- match creation
- placement entry
- kill entry
- match review
- finalization
- standings
- OCR empty state
- correction entry
- finalized read-only state

The remaining material navigation gap is the complete correction submission and return to corrected review.

### Tournament Data Entry

Existing tests cover:

- tournament fields
- date selection
- required validation
- loading state
- dirty-back behavior

No new v0.12.4 tournament tests are required.

### Roster Data Entry

Existing tests cover:

- team-name entry
- player add/edit/remove
- maximum player handling
- roster validation presentation
- roster review
- confirmation blocking
- confirmed state

No new v0.12.4 roster-entry tests are required.

### Match Data Entry

Existing tests cover:

- match creation fields
- date selection
- placement entry
- placement validation
- duplicate placement errors
- kill entry
- kill validation
- save/reset behavior

No new v0.12.4 match-entry tests are required.

### Review and Finalization

Existing tests cover:

- entered values on review
- validation state
- finalization confirmation
- successful finalization
- finalized read-only UI
- correction entry/history presentation
- standings navigation

No additional generic review/finalization tests are required.

### Match OCR Review

Existing tests cover:

- loading
- empty
- error
- ready states
- team suggestions
- uncertain fields
- warnings
- blockers
- manual correction controls
- finalization states
- back behavior

No additional match OCR review tests are required in v0.12.4.

---

## 4. Approved Navigation Test Addition

Modify:

```text
app/src/androidTest/java/com/hoggamers/rankforge/presentation/navigation/RankForgeNavigationTest.kt
```

Add one complete Compose navigation workflow proving:

1. a finalized match is displayed
2. user enters the correction workflow
3. correction screen is reached for the correct tournament/match identity
4. placement and/or kill values are edited
5. valid correction submission is initiated
6. confirmation is completed
7. navigation returns to the corrected finalized review
8. corrected values are visible
9. correction history or equivalent correction indication is visible where currently implemented
10. finalized match remains protected/read-only after correction completion

Target user-visible sequence:

```text
finalized review
→ correction screen
→ edit values
→ confirm correction
→ corrected finalized review
→ read-only state
```

Use existing navigation/test infrastructure.

Do not add exhaustive destination assertions.

Do not duplicate unrelated tournament/roster/match navigation tests.

---

## 5. Approved Correction Validation Test Addition

Modify:

```text
app/src/androidTest/java/com/hoggamers/rankforge/presentation/screen/MatchCorrectionScreenTest.kt
```

Add focused Compose UI coverage proving:

1. correction fields accept user edits
2. invalid correction values visibly produce the currently implemented validation/error state
3. correction cannot be submitted while invalid
4. changing the input to valid correction values clears or resolves the blocking state as currently implemented
5. valid submission proceeds to the existing confirmation behavior

The purpose is to protect the user-visible correction contract.

Do not duplicate every domain validation rule.

Do not test validation implementation internals.

---

## 6. Navigation Coverage Decision

Current status:

**PARTIAL**

After the approved correction-flow addition, the material navigation coverage for v0.12.4 will be considered complete.

Do not add exhaustive navigation assertions for every destination.

Do not add static label-only navigation tests.

---

## 7. Data Entry Coverage Decision

### Tournament

**COMPLETE**

No additional tests.

### Roster

**COMPLETE**

No additional tests.

### Match

**COMPLETE**

No additional tests.

### Correction

**PARTIAL**

Complete only:

- real field editing
- invalid correction presentation
- valid correction submission

### OCR Review

Implemented match OCR review controls are already adequately covered.

No new v0.12.4 OCR review tests are required.

---

## 8. Validation and Error UI Decision

Existing creation, roster, placement, kill, match-review, and OCR validation UI coverage is sufficient.

The only material missing validation coverage is correction-specific behavior.

Add only:

- visible invalid correction state
- blocked invalid submission
- valid correction progression

Do not duplicate domain validator tests.

---

## 9. Review and Finalization Decision

Current Compose coverage already proves:

- match review rendering
- finalization confirmation
- finalization blocking when invalid
- successful finalized state
- finalized read-only controls
- navigation to downstream standings/correction behavior

Status:

**COMPLETE**

Do not expand generic review/finalization coverage.

The corrected-finalized return flow belongs specifically to the correction navigation addition.

---

## 10. Correction Coverage Decision

Current status:

**PARTIAL**

Existing coverage already proves:

- previous values are displayed
- correction fields exist
- submission confirmation exists
- unavailable draft behavior
- correction history presentation

The remaining approved additions are:

### Screen-level

```text
edit
→ invalid state
→ blocked submit
→ valid state
→ confirmation
```

### Navigation-level

```text
finalized match
→ correction
→ edit
→ submit
→ corrected finalized review
```

No other correction UI expansion is approved.

---

## 11. OCR Review Boundary

### Match OCR Review

Current implemented Compose behavior is adequately covered.

Status:

**COMPLETE**

### Roster OCR Review

No persisted roster OCR matching/review screen currently exists.

Status:

**BLOCKED BY EXISTING PRODUCT DEFERRAL**

Do not implement production roster OCR review functionality merely to create a UI test.

### Persisted OCR Team-Matching Orchestration

The following remains deferred:

```text
persisted OCR evidence
→ team matching
→ assignment
→ persisted review
```

Do not add it to v0.12.4.

### Real OCR Acceptance

Real screenshot recognition/accuracy is outside this version.

It belongs to:

**v0.12.8 — OCR Acceptance Testing**

and the separately tracked real roster OCR acceptance evaluation.

---

## 12. Test Infrastructure Decision

Existing Compose test infrastructure is sufficient.

Reuse:

- `createAndroidComposeRule<ComponentActivity>()`
- `createComposeRule()`
- existing manually supplied ViewModels/repositories
- `RankForgeTheme`
- current test tags
- existing text/content selectors
- existing navigation setup

No new production semantics or test tags are approved.

No new test framework/infrastructure is required.

---

## 13. Production Boundary

No production files may be modified.

Do not modify:

- Compose screens
- navigation production code
- ViewModels
- domain use cases
- repositories
- Room
- OCR code
- scoring
- export
- Supabase
- Gradle
- dependencies

If reliable implementation of either approved test requires production changes, stop before editing.

Report:

1. exact production file
2. exact untestable control/state
3. why existing Compose semantics cannot address it
4. whether it is:
   - testability limitation
   - production defect
   - undefined UI contract
   - existing product deferral

Do not silently add production test tags or semantics.

---

## 14. Approved File Boundary

### Existing androidTest files to modify

```text
app/src/androidTest/java/com/hoggamers/rankforge/presentation/navigation/RankForgeNavigationTest.kt

app/src/androidTest/java/com/hoggamers/rankforge/presentation/screen/MatchCorrectionScreenTest.kt
```

### New androidTest files

```text
None.
```

### Production files

```text
None.
```

This is the complete approved implementation boundary.

If another existing test file becomes necessary, stop before editing it.

---

## 15. Explicitly Out of Scope

Do not add:

- additional tournament screen tests
- additional roster screen tests
- additional placement tests
- additional kill tests
- generic finalization tests
- static-label tests
- screenshot/golden tests
- exhaustive navigation tests
- genuine OCR image tests
- roster OCR product implementation
- persisted OCR/team-matching implementation
- real Android CSV save/share
- Google Sheets Android integration
- device compatibility testing
- offline/recovery testing
- backend tests
- Supabase tests
- security review
- regression-suite restructuring
- production UI refactoring

These are already covered, deferred, or belong to other Phase 12 versions.

---

## 16. Verification Policy

Because v0.12.4 modifies only Compose instrumentation tests, required verification is:

### Compile instrumentation tests

```powershell
.\gradlew.bat assembleDebugAndroidTest
```

### Focused connected tests

Run:

- `RankForgeNavigationTest`
- `MatchCorrectionScreenTest`

using instrumentation-runner class filtering.

### Complete connected instrumentation

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

### Scope checks

```powershell
git diff --check
git status --short
git diff --name-only
```

No JVM `testDebugUnitTest` is required unless JVM tests are unexpectedly modified, which is not approved.

No Supabase, Docker, Deno, backend, or remote verification is required.

---

## 17. Completion Criteria

v0.12.4 is complete when:

1. this decision document is merged
2. implementation starts from synchronized `main`
3. only the two approved Compose instrumentation files are modified
4. correction screen field editing is tested
5. invalid correction state is visibly tested
6. invalid correction submission is blocked
7. valid correction submission proceeds
8. finalized match → correction → corrected finalized review navigation is tested
9. corrected finalized state remains read-only
10. no production files are modified
11. no new test files are created
12. instrumentation test APK compiles
13. focused Compose tests pass on connected device
14. complete connected instrumentation suite passes
15. `git diff --check` passes
16. implementation is merged through PR
17. local `main` is synchronized with `origin/main`
18. working tree is clean

---

## 18. Decision Summary

Approved v0.12.4 implementation:

```text
2 existing Compose instrumentation tests modified
0 new test files
0 production files

RankForgeNavigationTest:
finalized review
→ correction
→ edit
→ submit
→ corrected finalized review
→ read-only

MatchCorrectionScreenTest:
edit
→ invalid validation state
→ blocked submission
→ valid state
→ confirmation

Tournament UI coverage already complete
Roster UI coverage already complete
Match-entry UI coverage already complete
Generic review/finalization coverage already complete
Match OCR review coverage already complete

Roster OCR review remains blocked by existing product deferral
Real OCR acceptance remains deferred to v0.12.8
```

After this document is merged, implementation may proceed on:

`feature/v0.12.4-compose-ui-tests`
