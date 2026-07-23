# Rank-Forge Git, Commit and Rollback Rules

## 1. Branching Rules

The `main` branch is the stable source of truth.

Use these branch formats:

```text
feature/<short-description>
fix/<short-description>
docs/<short-description>
test/<short-description>
chore/<short-description>
```

Examples:

```text
feature/tournament-creation
feature/scoreboard-ocr
fix/duplicate-team-detection
docs/system-architecture
test/scoring-rules
chore/android-project-setup
```

Branch requirements:

* Do not develop directly on `main`.
* Create one branch for one approved task or closely related scope.
* Start branches from the latest verified `main`.
* Do not combine unrelated features or fixes.
* Merge only after required verification passes.
* Delete completed branches after successful merging.
* Do not force-push shared branches without explicit approval.

## 2. Commit Rules

Use Conventional Commit messages:

```text
feat: add tournament creation
fix: prevent duplicate team assignment
test: add scoring calculation tests
docs: define OCR processing rules
chore: configure Android project
refactor: simplify local result mapping
```

Commit requirements:

* Each commit must contain one logical change.
* Commit messages must clearly describe the result.
* Do not commit unrelated files.
* Do not commit secrets, API keys or private credentials.
* Do not commit local environment files.
* Do not commit generated build directories.
* Do not commit private tournament screenshots unless explicitly approved.
* Relevant verification must pass before committing.

## 3. Rollback Rules

### Application Code

* Revert the specific faulty commit whenever possible.
* Do not delete unrelated working changes.
* Verify the Android build and affected tests after rollback.

### Supabase Database

* Never edit or delete an already-applied production migration.
* Fix production database issues through a new corrective migration.
* Back up affected data before destructive or high-risk changes.
* Do not drop tables, columns or stored data without explicit approval.
* Test corrective migrations before production execution.
* Confirm RLS policies and database functions after rollback.

### Releases

* Releases must be created only from verified `main`.
* Every release must have a version number and release notes.
* Major user-visible changes must update the changelog.
* Failed releases must be rolled back to the last verified release.

## 4. Rollback Documentation

Every rollback must record:

* What failed
* Affected version or commit
* Rollback action taken
* Database impact
* Verification performed
* Follow-up corrective work
