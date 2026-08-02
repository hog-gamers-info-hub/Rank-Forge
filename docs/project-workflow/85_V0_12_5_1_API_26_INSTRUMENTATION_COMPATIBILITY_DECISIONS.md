# Phase 12 v0.12.5.1 — API 26 Instrumentation Compatibility Decisions

## Status

**Blocking test-only patch required before v0.12.5 device compatibility verification can continue.**

## Parent Version

**v0.12.5 — Device Compatibility**

Canonical parent scope:

> Test API 26, target API, emulators, and physical Android devices.

---

## 1. Discovery

During isolated API 26 emulator verification:

- API 26 emulator booted successfully
- debug APK build succeeded
- instrumentation APK build succeeded
- application installed successfully
- application launch succeeded
- full connected instrumentation suite executed 201 tests
- 9 tests failed

The same failures reproduced when their six affected test classes were run individually on the API 26 emulator.

Therefore the failures are not caused by simultaneous multi-device execution.

---

## 2. Classification

Current evidence indicates an **instrumentation compatibility/test-harness issue**, not a confirmed application runtime compatibility defect.

The failures fall into two patterns:

### Viewport-sensitive Compose assertions/actions

Several tests assume that a target is already visible on screen.

That assumption holds on the existing physical-device environment but is not reliable on the API 26 emulator viewport.

Existing Compose test infrastructure already supports deterministic scrolling with:

`performScrollTo()`

The affected tests should explicitly scroll the target into view before asserting visibility or invoking lower-screen controls where appropriate.

### Text-input semantics compatibility

Three `MatchOcrReviewScreenTest` callback tests use `performTextInput()` against controlled Compose text fields.

On API 26, the callback receives the initial empty value rather than the intended replacement value.

The tests should use the smallest deterministic Compose semantics text-edit action that represents the intended final field value across supported Android versions.

The behavioral contract must remain unchanged.

---

## 3. No Production Defect Established

The API 26 environment currently proves:

- application installation succeeds
- application starts successfully
- no immediate API-level crash occurs

No production source file has been identified as defective.

Therefore:

**No production modification is approved.**

If the corrected instrumentation tests expose a genuine product/runtime failure, stop and create a separate production compatibility patch decision.

---

## 4. Approved Existing Test Files

Modify only:

```text
app/src/androidTest/java/com/hoggamers/rankforge/presentation/navigation/RankForgeNavigationTest.kt

app/src/androidTest/java/com/hoggamers/rankforge/presentation/screen/MatchCreationScreenTest.kt

app/src/androidTest/java/com/hoggamers/rankforge/presentation/screen/MatchOcrReviewScreenTest.kt

app/src/androidTest/java/com/hoggamers/rankforge/presentation/screen/RosterScreenshotIntakeSectionTest.kt

app/src/androidTest/java/com/hoggamers/rankforge/presentation/screen/TournamentListAndDetailsScreenTest.kt

app/src/androidTest/java/com/hoggamers/rankforge/presentation/screen/TournamentStandingsScreenTest.kt
```

No new test files are planned.

---

## 5. RankForgeNavigationTest Decision

Affected test:

`finalizedMatchReviewDoesNotOfferPlacementOrKillEditing`

The finalized tournament-status assertion is below the immediately visible viewport on API 26.

Update only the test interaction so the existing finalized-status node is scrolled into view before asserting it.

Preserve assertions that:

- finalized review does not expose placement editing
- finalized review does not expose kill editing
- correction mode is available
- discarded correction returns to finalized review
- tournament details show FINALIZED
- finalized match actions remain protected

Do not change navigation production code.

---

## 6. MatchCreationScreenTest Decision

Affected test:

`detailsShowsCreatedDraftAndBlocksAtTenMatches`

The test renders ten match rows and expects lower-screen content to already be displayed.

Update viewport-sensitive assertions to explicitly bring the intended content into view.

Preserve verification that:

- ten draft matches are represented
- maximum-match blocking message exists and is visible
- Match 1 exists
- ten DRAFT states exist

Do not alter the maximum-match product rule.

---

## 7. MatchOcrReviewScreenTest Decision

Affected tests:

- `placementEditCallbackFiresWithRowIndexAndValue`
- `killsEditCallbackFiresWithRowIndexAndValue`
- `teamSlotEditCallbackFiresWithRowIndexAndValue`
- `dirtyMarkerAndWarningLabelsAreDisplayed`

### Input callbacks

Use a deterministic Compose semantics edit action that sets/replaces the complete field value rather than depending on incremental IME-style input behavior.

The tests must continue proving exact callbacks:

```text
placement → (0, "7")
kills → (0, "5")
team slot → (0, "6")
```

Do not weaken these assertions.

Do not call ViewModel/domain methods directly instead of exercising the Compose text field.

### Dirty/warning labels

Explicitly scroll relevant text/tag targets into view before visibility assertions when they can reside outside the API 26 viewport.

Continue proving:

- dirty-row marker
- Draft changed
- OCR-value-change warning
- unsaved correction-draft warning

Do not modify production OCR review UI.

---

## 8. RosterScreenshotIntakeSectionTest Decision

Affected test:

`selectedSlotShowsCropControlsAndForwardsCropActions`

Make the isolated test layout/interaction explicitly scroll-capable using existing Compose test patterns already present in this test file, and scroll the crop action controls into view before invoking them.

Continue proving:

- crop status
- crop error
- all four crop inputs
- Set Crop callback receives slot 1
- Clear Crop callback receives slot 1

Do not change screenshot/crop production behavior.

---

## 9. TournamentListAndDetailsScreenTest Decision

Affected test:

`detailsScreenShowsSavedTeamNameInSlotDisplayAndEntryAction`

Explicitly scroll the saved team-name slot and team-entry action into view as necessary.

Continue proving:

- saved team name `Alpha` renders for slot 1
- team-entry action invokes the correct tournament ID

Do not modify TournamentDetailsScreen production layout.

---

## 10. TournamentStandingsScreenTest Decision

Affected test:

`completeTieIsShownAsUnresolved`

The second standings row may be below the API 26 viewport.

Explicitly scroll each relevant complete-tie row/indicator into view before display assertions.

Continue proving:

- slot 1 complete tie indication
- slot 2 complete tie indication
- exactly two unresolved complete-tie messages

Do not modify standings production behavior.

---

## 11. Test Quality Rules

The patch must not:

- remove assertions
- replace `assertIsDisplayed` with existence-only checks unless existence is the actual original contract
- select arbitrary nodes by index
- add sleeps
- add API-level conditional skips
- skip tests on API 26
- change production semantics/tags merely for testing
- change application layouts to fit the emulator
- change domain behavior
- alter minSdk or targetSdk

The goal is deterministic testing of the same user-visible contracts.

---

## 12. Production Boundary

Production files:

```text
None.
```

Gradle files:

```text
None.
```

Dependencies:

```text
None.
```

New test files:

```text
None.
```

If any production file appears necessary, stop before editing it.

---

## 13. Verification

After implementation:

### Compile instrumentation

```powershell
.\gradlew.bat assembleDebugAndroidTest
```

### API 26 focused classes

With only the API 26 emulator connected, run the six affected classes individually.

All must pass.

### API 26 full suite

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected:

```text
201 tests
0 failed
```

### Regression on physical device

After API 26 passes, reconnect the physical device with the emulator disconnected and run:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

This verifies the compatibility adjustments do not regress the previously passing device environment.

### Scope

```powershell
git diff --check
git diff --name-only
```

Only the six approved existing test files may be modified.

---

## 14. Parent v0.12.5 Continuation

v0.12.5 must remain open while this patch is implemented.

After v0.12.5.1 merges:

1. synchronize `main`
2. rerun API 26 full compatibility verification
3. proceed to API 36 emulator
4. perform final physical-device verification
5. record the complete device matrix
6. close v0.12.5 only if all environments pass

---

## 15. Completion Criteria

v0.12.5.1 is complete when:

1. this decision document is merged
2. implementation starts from synchronized `main`
3. only the six approved androidTest files are modified
4. API 26 viewport assumptions are made deterministic
5. OCR text-edit semantics are deterministic across API 26
6. no assertions are materially weakened
7. no production files are modified
8. `assembleDebugAndroidTest` passes
9. all six focused API 26 test classes pass
10. API 26 full 201-test suite passes
11. physical-device regression suite passes
12. `git diff --check` passes
13. implementation is merged through PR
14. `main` is synchronized
15. v0.12.5 device-matrix verification resumes

---

## 16. Decision Summary

```text
v0.12.5.1 — API 26 Instrumentation Compatibility

6 existing androidTest files modified
0 new files
0 production files
0 Gradle changes

Fix:
- explicit viewport scrolling
- deterministic Compose text-edit semantics

Do not:
- skip API 26 tests
- weaken behavioral assertions
- alter production UI
- change Android compatibility configuration

After patch:
API 26 focused → API 26 full suite → physical regression
then resume v0.12.5 with API 36.
```
