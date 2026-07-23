# Rank-Forge Repository Instructions

## Role

Codex is the implementation agent for Rank-Forge.

Implement only explicitly approved tasks. Do not add features, refactor unrelated code or continue to another task automatically.

## Before Editing

Always:

1. Read this file.
2. Read the relevant files under `docs/`.
3. Inspect existing code and tests.
4. Run `git status`.
5. Confirm the requested files and behavior actually exist.
6. Preserve unrelated user changes.

## Architecture

Use the approved stack:

* Kotlin
* Jetpack Compose and Material 3
* MVVM with layered architecture
* Coroutines and `StateFlow`
* Supabase as the primary source of truth
* Room for approved offline and synchronization behavior
* Google ML Kit for MVP OCR
* Google Sheets and CSV outputs

Keep UI, domain and data logic separated.

Do not place scoring, OCR matching or database logic inside composables.

## Scope Rules

* Modify only files required by the approved task.
* Minimize changes to working code.
* Do not perform unrelated refactoring.
* Do not rename existing public APIs, database objects or files without approval.
* Do not introduce new dependencies unless required and approved.
* Do not remove working behavior unless explicitly requested.
* Stop and report blockers instead of making speculative changes.

## Security

* Never expose secrets, API keys, service-role credentials or private tokens.
* Never commit local environment files.
* Do not add unnecessary Android permissions.
* Use RLS on every exposed Supabase table.
* Use ownership-based authorization.
* Do not use authentication-only policies as authorization.
* Do not use destructive SQL without explicit approval, backup and rollback instructions.
* Do not edit already-applied production migrations.

## Database Changes

* Use versioned Supabase migrations.
* Inspect existing migrations before creating new objects.
* Add database and RLS tests.
* Preserve existing data.
* Use a new corrective migration for rollback.
* Never rewrite migration history.

## OCR and Scoring

* Preserve original OCR values.
* Store corrections separately.
* Keep OCR, matching and scoring as separate components.
* Scoring must remain deterministic.
* Enforce unique teams and positions within each match.
* Do not silently confirm uncertain OCR or team matches.
* Use the approved confidence thresholds and manual-review flow.

## Verification

Run all checks relevant to the task.

Standard Android checks:

```bash
gradlew.bat test
gradlew.bat lint
gradlew.bat assembleDebug
```

Also run relevant:

* Compose UI tests
* Room tests
* Supabase database and RLS tests
* OCR fixture tests
* Export tests
* `git diff --check`

Do not remove or weaken tests to make the build pass.

## Git

* Do not commit, push, merge, rebase or open a pull request unless explicitly instructed.
* Never force-push without explicit approval.
* Keep changes focused.
* Review the final diff.
* Do not include secrets, private screenshots or generated build output.

## Completion Report

Report:

* Files added, modified and deleted
* What was implemented
* What was intentionally unchanged
* Commands and tests executed
* Verification results
* Known limitations or blockers

Do not claim completion when required verification failed or was not performed.
