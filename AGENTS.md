# Rank-Forge Repository Instructions

## Role

Codex is the implementation agent for Rank-Forge.

Implement only explicitly approved tasks. Do not add features, refactor unrelated code, or continue to another task automatically.

## Before Editing

Always:

1. Read this file.
2. Read the relevant files under `docs/`.
3. Inspect the existing code and tests.
4. Run `git status`.
5. Confirm that referenced files and behavior actually exist.
6. Preserve unrelated user changes.

## Approved Architecture

Use the approved stack:

* Kotlin
* Jetpack Compose and Material 3
* MVVM with layered architecture
* Kotlin Coroutines and `StateFlow`
* Google ML Kit for MVP OCR
* Supabase as the primary source of truth
* Room for approved offline drafts, caching, and synchronization
* Google Sheets and CSV outputs
* GitHub as the source of truth

Keep UI, domain, and data logic separated.

Do not place OCR processing, team matching, scoring, database, or synchronization logic inside composables.

## Scope Rules

* Modify only files required by the approved task.
* Minimize changes to existing working code.
* Do not perform unrelated refactoring.
* Do not add unapproved features.
* Do not rename public APIs, database objects, files, or packages without approval.
* Do not introduce dependencies unless required by the approved task.
* Do not remove working behavior unless explicitly requested.
* Do not continue to another phase or version automatically.
* Stop and report blockers instead of making speculative changes.

## Android Rules

* Follow Kotlin and Jetpack Compose best practices.
* Keep composables stateless where practical.
* Use ViewModels for screen state and actions.
* Use immutable UI state where practical.
* Handle loading, empty, success, and error states.
* Do not block the main thread.
* Do not hard-code user-facing strings.
* Do not add unnecessary Android permissions.
* Do not store secrets in Kotlin files, resources, manifests, or the APK.
* Preserve process and configuration-change safety where relevant.

## OCR and Team Matching Rules

* Preserve original OCR values.
* Store corrected values separately.
* Keep OCR extraction, player normalization, team matching, and scoring as separate components.
* Use deterministic scoring calculations.
* Enforce one detected player to one roster-player match.
* Enforce unique team assignment within each match.
* Enforce unique positions from 1 to 12.
* Use the approved confidence thresholds.
* Require manual review for uncertain or unmatched teams.
* Do not silently confirm uncertain OCR results.
* Add deterministic tests for normalization, fuzzy matching, duplicate detection, and confidence rules.

## Scoring Rules

Use the approved placement points:

* 1st: 12
* 2nd: 9
* 3rd: 8
* 4th: 7
* 5th: 6
* 6th: 5
* 7th: 4
* 8th: 3
* 9th: 2
* 10th: 1
* 11th: 0
* 12th: 0

Additional rules:

* One kill equals one point.
* Match points equal position points plus kill points.
* Overall points equal the sum of finalized match points.
* Do not export or synchronize unconfirmed OCR results.

## Supabase Rules

* Use versioned Supabase migration files.
* Inspect existing migrations before creating new schema objects.
* Enable RLS on every exposed table.
* Use ownership-based authorization.
* Do not rely on authentication-only policies.
* Use both `USING` and `WITH CHECK` for update policies where required.
* Keep service-role and secret keys out of the Android client.
* Restrict privileged database functions.
* Add database and RLS tests for affected behavior.
* Preserve existing tournament and user data.
* Do not edit or delete already-applied production migrations.
* Use a new corrective migration for database rollback.
* Do not run destructive SQL without explicit approval, backup, and rollback instructions.

## Google Sheets and CSV Rules

* Google Sheets updates must run through a secure Supabase Edge Function.
* Google credentials must never be embedded in the Android app.
* Only finalized results may be exported.
* Synchronization retries must not duplicate rows.
* CSV files must use UTF-8 encoding.
* Exported data must not contain missing or duplicated teams.

## Security Rules

* Never expose secrets, tokens, API keys, service-account credentials, or database passwords.
* Never commit `.env` files, `local.properties`, keystores, service-account JSON files, or generated build output.
* Do not commit private tournament screenshots unless explicitly approved.
* Follow least-privilege access.
* Warn before destructive, irreversible, database, or breaking operations.
* Include rollback steps before risky changes.

## Verification

Run all checks relevant to the approved task.

Standard Android checks:

```bash
gradlew.bat test
gradlew.bat lint
gradlew.bat assembleDebug
```

Also run relevant checks such as:

* Compose UI tests
* Room database tests
* Supabase database tests
* RLS policy tests
* Edge Function tests
* OCR fixture tests
* Fuzzy-matching tests
* Scoring tests
* CSV export validation
* Google Sheets synchronization tests
* Manual emulator or physical-device verification
* `git diff --check`

Rules:

* Do not remove or weaken tests to make verification pass.
* Investigate the root cause of failed tests.
* A task is not complete while required verification is failing.
* Clearly report any skipped or blocked verification.

## Git Rules

* Run `git status` before editing.
* Preserve unrelated changes.
* Keep changes focused on one approved task.
* Review the final Git diff.
* Do not commit, push, merge, rebase, switch branches, or open a pull request unless explicitly instructed.
* Never force-push without explicit approval.
* Use Conventional Commit messages when commits are requested.
* Never commit secrets, private screenshots, local configuration, or generated output.

## Required Completion Report

Every implementation report must include:

### Files Changed

* Files added
* Files modified
* Files deleted

### Implementation

* What was implemented
* What behavior changed
* What was intentionally left unchanged

### Verification

* Commands executed
* Tests passed
* Tests failed
* Manual checks performed

### Risks and Limitations

* Known limitations
* Remaining blockers
* Assumptions made
* Follow-up work required

Do not claim completion when required verification failed, was skipped, or could not be performed.

## Source-of-Truth Order

When instructions conflict, follow this order:

1. Current explicitly approved implementation task
2. Latest approved user decision
3. This `AGENTS.md` file
4. Active project workflow phase and version
5. Approved documentation under `docs/`
6. GitHub `main`
7. Existing implementation patterns
8. Earlier discussions or assumptions

Report any material conflict before proceeding.
