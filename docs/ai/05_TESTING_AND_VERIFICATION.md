# Rank-Forge Testing and Verification Rules

## 1. Mandatory Verification

Every implementation must run the checks relevant to its scope.

Standard Android verification:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

On Windows PowerShell or Command Prompt:

```bash
gradlew.bat test
gradlew.bat lint
gradlew.bat assembleDebug
```

## 2. Required Tests

* Scoring calculations must have unit tests.
* Position-point rules must have unit tests for all positions from 1 to 12.
* Fuzzy player and team matching must have deterministic tests.
* Duplicate team and duplicate match detection must have tests.
* Room database behavior must have repository or database tests.
* Supabase database functions and RLS policies must have database tests.
* OCR processing must be checked against the approved screenshot dataset.
* Changed Compose screens must be manually verified on an emulator or physical device.

## 3. Verification Rules

* A task is not complete while required tests are failing.
* Existing passing tests must not be removed to make a change pass.
* Test failures must be investigated to identify the root cause.
* OCR results must be compared with manually prepared expected results.
* Manual corrections must be tested for player names, teams, kills and positions.
* Google Sheets and CSV exports must be checked for missing or duplicate teams.
* The Git diff must be reviewed before committing.

## 4. Required Completion Report

Every completed implementation must report:

* Files added
* Files modified
* Features implemented
* Tests executed
* Verification results
* Known limitations
* Files intentionally left unchanged
