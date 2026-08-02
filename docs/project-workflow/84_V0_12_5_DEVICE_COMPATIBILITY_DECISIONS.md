# Phase 12 v0.12.5 — Device Compatibility Decisions

## Status

**Approved for manual compatibility verification after this decision document is merged.**

## Version

**Phase 12 — Quality Assurance and Security Validation**

**v0.12.5 — Device Compatibility**

Canonical scope:

> Test API 26, target API, emulators, and physical Android devices.

---

## 1. Purpose

Verify that the current Rank Forge Android application operates correctly across the minimum supported Android API, the current target API, emulated Android environments, and a real physical Android device.

This version is a compatibility verification version.

It does not introduce new product functionality.

---

## 2. Current Android Compatibility Contract

The application currently defines:

```text
minSdk = 26
targetSdk = 36
compileSdk = 36.1
```

Therefore the required compatibility boundaries are:

- Android API 26 — minimum supported API
- Android API 36 — current target API
- physical Android device — real-device verification

---

## 3. Core Decision

v0.12.5 will use manual/device verification against the existing application and existing instrumentation suite.

No production code changes are planned.

No new test files are planned.

No Gradle configuration changes are planned.

No Gradle Managed Device configuration is required merely to complete this version.

If compatibility testing exposes a genuine defect, stop and treat that defect separately before closing v0.12.5.

---

## 4. Approved Device Matrix

### Environment A — Minimum API Emulator

Use an Android emulator running:

```text
API 26
```

Purpose:

- verify minimum supported Android installation
- verify application startup
- verify existing instrumentation compatibility
- detect APIs used incorrectly above the minimum supported level

---

### Environment B — Target API Emulator

Use an Android emulator running:

```text
API 36
```

Purpose:

- verify behavior on the application's current target Android API
- verify current platform behavior
- detect target-version compatibility problems

---

### Environment C — Physical Android Device

Use the currently available physical Android test device.

Purpose:

- verify installation and execution on real hardware
- verify Compose/instrumentation behavior outside an emulator
- verify that compatibility is not emulator-only

No additional physical-device matrix is required for v0.12.5 unless testing discovers hardware-specific behavior.

---

## 5. Verification Required Per Environment

For each approved environment verify:

1. device/emulator is visible through ADB
2. debug APK can be installed
3. application launches without an immediate crash
4. instrumentation APK installs
5. existing connected instrumentation tests execute
6. instrumentation suite completes successfully
7. no API-specific runtime exception appears

Where practical, use the full existing:

```text
connectedDebugAndroidTest
```

suite rather than creating compatibility-specific duplicate tests.

---

## 6. Minimum API 26 Acceptance

API 26 passes when:

- emulator boots normally
- Rank Forge installs
- Rank Forge launches
- instrumentation suite executes
- existing UI/database/workflow tests pass
- no unsupported-platform API crash occurs

Do not lower `minSdk`.

Do not introduce compatibility workarounds unless an actual failure demonstrates they are required.

---

## 7. Target API 36 Acceptance

API 36 passes when:

- emulator boots normally
- Rank Forge installs
- Rank Forge launches
- instrumentation suite executes
- existing UI/database/workflow tests pass
- no target-API-specific crash or behavioral blocker occurs

The purpose is runtime compatibility, not compile verification alone.

---

## 8. Physical Device Acceptance

The physical-device verification passes when:

- ADB recognizes the device
- application installs
- application starts
- connected instrumentation executes
- existing workflows/tests complete successfully

Existing successful physical-device results from earlier Phase 12 versions provide supporting evidence, but v0.12.5 should perform an explicit synchronized-main verification before closure.

---

## 9. Emulator Strategy

Use standard local Android Emulator/AVD tooling.

Do not add:

- Firebase Test Lab
- cloud device farms
- paid device-testing services
- Gradle Managed Devices
- CI emulator infrastructure

unless separately approved.

The v0.12.5 requirement can be satisfied with local API 26 and API 36 Android emulators plus a physical device.

---

## 10. Existing Tests

Reuse the existing instrumentation suite.

Do not create device-specific copies of:

- navigation tests
- Compose tests
- Room tests
- integration tests
- OCR UI tests

Compatibility verification should prove that the existing suite runs across Android versions rather than duplicate its functional assertions.

---

## 11. Production Boundary

Planned production changes:

```text
None.
```

Planned test-code changes:

```text
None.
```

Planned Gradle changes:

```text
None.
```

If any compatibility failure requires code changes:

1. identify the failing environment
2. record the exact exception/test
3. identify the production file
4. stop before modifying it
5. create a narrowly scoped compatibility patch decision if required

Do not silently expand v0.12.5.

---

## 12. Out of Scope

v0.12.5 does not include:

- new product functionality
- additional unit tests
- additional Compose UI scenarios
- Room migration expansion
- backend testing
- Supabase testing
- OCR accuracy evaluation
- offline/recovery testing
- security review
- performance benchmarking
- battery profiling
- device-farm infrastructure
- exhaustive OEM testing
- tablets/foldables unless separately required
- all Android API versions between 26 and 36

The required matrix is boundary-oriented, not exhaustive.

---

## 13. Compatibility Failure Classification

Any failure discovered during verification must be classified as one of:

- environment/tooling failure
- emulator configuration failure
- flaky existing test
- genuine Android API compatibility defect
- physical-device-specific defect

Only genuine application defects may justify production changes.

Environment/setup failures must not be fixed by altering application behavior.

---

## 14. Required Evidence

Before v0.12.5 closes, record results for:

### API 26 Emulator

- Android API
- emulator/AVD identity
- ADB status
- application installation/launch status
- instrumentation result
- passed/failed test count

### API 36 Emulator

- Android API
- emulator/AVD identity
- ADB status
- application installation/launch status
- instrumentation result
- passed/failed test count

### Physical Device

- Android/API version
- device identity
- ADB status
- instrumentation result
- passed/failed test count

Do not record private device identifiers such as ADB serial numbers in committed documentation.

---

## 15. Verification Commands

Build once before device verification:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest
```

For each connected target:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

ADB may be invoked using:

```powershell
$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $Adb devices
```

If multiple devices are simultaneously connected, isolate the intended environment before running connected tests rather than mixing evidence from multiple targets.

---

## 16. Completion Criteria

v0.12.5 is complete when:

1. this decision document is merged
2. testing is performed from synchronized `main`
3. API 26 emulator verification passes
4. API 36 emulator verification passes
5. physical Android device verification passes
6. app installs and launches on all three targets
7. instrumentation passes on all three targets
8. no unresolved compatibility defect remains
9. no unnecessary production/test/Gradle changes are made
10. compatibility evidence is recorded
11. working tree remains clean
12. local `main` remains synchronized with `origin/main`

---

## 17. Decision Summary

```text
v0.12.5 — Device Compatibility

Required matrix:

API 26 emulator
API 36 emulator
physical Android device

Reuse existing instrumentation suite.

0 planned production changes
0 planned test-code changes
0 planned Gradle changes

If a genuine compatibility defect appears:
stop → document → patch separately.

No Codex implementation is required unless a defect is discovered.
```
