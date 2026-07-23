# Release, Backup, and Rollback Operations

## 1. Purpose

This document defines the release process, backup requirements, rollback procedures, and recovery controls for Rank-Forge.

The objective is to prevent data loss, incorrect tournament results, unrecoverable deployments, and undocumented production changes.

## 2. Release Principles

Every release must follow these rules:

- GitHub is the source of truth.
- The `main` branch must remain stable.
- Development must occur on a dedicated branch.
- Releases must use reviewed and tested commits.
- Secrets must never be committed to Git.
- Production data must never be modified manually without an approved recovery plan.
- Database changes must use version-controlled migrations.
- Finalized tournament results must not be silently overwritten.
- A rollback or corrective action must be defined before deployment.

## 3. Release Environments

Rank-Forge should use the following environments when available:

### Local Development

Used for:

- Feature development
- Unit tests
- Local database testing
- Migration verification
- UI testing
- Offline testing

### Staging

Used before production for:

- Integration testing
- Supabase synchronization testing
- Edge Function testing
- Google Sheets export testing
- Migration rehearsal
- Release-candidate verification

### Production

Used only for approved live tournament data and released application versions.

Development experiments must not run against production.

## 4. Version Control Requirements

Before release:

- The working tree must be clean.
- All required files must be committed.
- Tests must pass.
- Lint checks must pass.
- The debug build must succeed.
- Database migrations must be committed.
- Documentation must reflect the release.
- No credentials or private test data may appear in Git.
- The release commit must be identifiable by tag or release record.

Recommended release tag format:

`vMAJOR.MINOR.PATCH`

Examples:

- `v0.1.0`
- `v0.2.0`
- `v1.0.0`

## 5. Pre-Release Checklist

Before approving a release, verify:

- Required acceptance criteria pass.
- No critical or high-severity defect remains open.
- The 12-team workflow works correctly.
- Scoring and tie-break calculations pass.
- Invalid matches cannot be finalized.
- Finalized results persist correctly.
- CSV export is verified.
- Google Sheets export is verified when implemented.
- Offline and retry behavior is verified.
- Supabase Row Level Security is verified.
- No service-role key exists in the Android application.
- Database migration compatibility is verified.
- Backup completion is recorded.
- Rollback instructions are confirmed.
- Known limitations are documented.

## 6. Backup Scope

The backup process must protect:

- Git repository source code
- Database schema
- Database data
- Supabase Storage objects
- Tournament records
- Team rosters
- Match records
- Original screenshot references
- OCR output
- Corrected match results
- Finalized standings
- Export records
- Edge Function source code
- Google Sheets configuration
- Environment-variable names
- Release evidence

Secret values must be stored only in approved secret-management systems.

Do not include secret values in documentation, screenshots, logs, Git commits, or backup manifests.

## 7. Source-Code Backup

Source-code protection must include:

- Local Git repository
- GitHub remote repository
- Pushed feature branches where required
- Stable `main` branch
- Release tags
- Pull-request history

Before release, run checks confirming:

- The required commit exists on GitHub.
- The release tag points to the correct commit.
- The working tree contains no uncommitted production changes.
- Generated build files are not committed.

GitHub is the authoritative source for application and migration code.

## 8. Database Backup Process

Before any production database migration or risky data operation:

1. Record the current Git commit.
2. Record the migration files being deployed.
3. Create a schema backup.
4. Create a data backup where production data could be affected.
5. Record the backup date and environment.
6. Verify that the backup file exists and is readable.
7. Store the backup in an approved restricted location.
8. Confirm that restoration procedures are available.
9. Deploy only after backup verification.

Do not assume automatic provider backups are available. Their availability and retention must be verified for the active Supabase plan before relying on them.

Database backups must never be committed to the public repository.

## 9. Storage and Screenshot Backup

Original approved screenshots must be preserved without modification.

Rules:

- Keep the original file separately from processed versions.
- Do not overwrite the original screenshot.
- Store cropped, enhanced, or compressed versions as separate files.
- Preserve the relationship between the screenshot and its match.
- Preserve upload timestamps and identifiers where practical.
- Restrict access to private tournament screenshots.
- Do not commit private screenshots to a public repository.

Deletion of an original screenshot requires explicit authorization and confirmed backup availability.

## 10. Tournament Data Protection

Finalized tournament data requires additional safeguards:

- Draft and finalized states must be distinguishable.
- Finalized matches must not be silently edited.
- Corrections must be attributable and auditable.
- Original OCR output must remain available after correction.
- Duplicate synchronization must be prevented.
- Exported results must identify the source tournament and match.
- A correction must not erase prior evidence without authorization.

Before any bulk modification:

- Identify affected tournaments.
- Export the existing data.
- Record row counts.
- Define validation queries.
- Define rollback or corrective steps.

## 11. Release Deployment Process

A production release should follow this order:

1. Confirm approved release scope.
2. Confirm clean Git status.
3. Pull the latest stable branch.
4. Run required tests.
5. Run lint checks.
6. Build the Android application.
7. Verify migration files.
8. Complete and record backups.
9. Deploy database migrations.
10. Deploy Edge Functions when applicable.
11. Deploy or distribute the application build.
12. Run smoke tests.
13. Verify logs and synchronization.
14. Record release evidence.
15. Monitor for regressions.

Each release must have one clearly identified release owner.

## 12. Post-Release Smoke Tests

Immediately after deployment, verify:

- Authentication works.
- Authorized tournament access works.
- Team roster loading works.
- Match creation works.
- Draft saving works.
- Manual correction works.
- Scoring calculation works.
- Finalization validation works.
- Finalized result retrieval works.
- Supabase synchronization works.
- CSV export works.
- Google Sheets export works when implemented.
- Unauthorized access is rejected.
- No unexpected critical errors appear in logs.

Failure of a critical smoke test requires release rollback or an approved corrective deployment.

## 13. Rollback Triggers

Rollback evaluation is required when:

- Finalized scores are calculated incorrectly.
- Tournament data is lost or corrupted.
- Authentication is unavailable.
- Authorized users cannot access tournaments.
- Unauthorized data access becomes possible.
- A migration breaks the application.
- Synchronization creates duplicate or conflicting data.
- The application crashes during a core workflow.
- Exported results are materially incorrect.
- A critical security defect is discovered.
- A release causes widespread unusability.

Minor cosmetic defects do not normally require rollback unless they block a core workflow.

## 14. Application Rollback

For an application-code failure:

1. Stop further distribution of the faulty build where possible.
2. Identify the last known-good Git tag or commit.
3. Confirm database compatibility with the older application version.
4. Build the last known-good source.
5. Run critical smoke tests.
6. Distribute or redeploy the corrected version.
7. Record the affected release and replacement version.
8. Add regression tests for the defect.

Do not roll back the application when the previous version is incompatible with the current database schema.

In that case, use a corrective release.

## 15. Database Rollback Policy

Production migrations should be designed to be backward-compatible whenever practical.

Preferred recovery order:

1. Stop the affected deployment.
2. Preserve logs and evidence.
3. Assess affected data.
4. Apply a corrective forward migration.
5. Validate data integrity.
6. Re-run smoke tests.

Direct destructive rollback should be used only when:

- A forward fix cannot safely restore service.
- A verified backup exists.
- The affected data range is understood.
- Restoration has been approved.
- The restore procedure has been tested or validated.

Do not manually delete migration-history records to simulate rollback.

Never edit an already-applied production migration file. Add a new corrective migration instead.

## 16. Edge Function Rollback

For an Edge Function failure:

1. Identify the last known-good source commit.
2. Confirm compatibility with the current database.
3. Redeploy the known-good function source or deploy a corrective version.
4. Verify authentication and authorization.
5. Verify retry and idempotency behavior.
6. Run the related integration tests.
7. Monitor logs after deployment.

Secrets must not be changed during rollback unless the incident specifically requires credential rotation.

## 17. Data Restoration Process

Before restoring data:

- Confirm the incident scope.
- Preserve the current damaged state for investigation where safe.
- Identify the correct backup.
- Verify the backup timestamp.
- Verify the target environment.
- Determine which records must be restored.
- Prevent new conflicting writes.
- Record expected row counts.

After restoration:

- Verify schema integrity.
- Verify row counts.
- Verify tournament ownership.
- Verify finalized results.
- Verify duplicate prevention.
- Verify application access.
- Verify exports.
- Record the restoration outcome.

A full database restoration must not be performed when a controlled record-level repair is safer.

## 18. Rollback Verification

A rollback or corrective deployment is complete only when:

- Core workflows pass.
- Scoring results are correct.
- No new data corruption is detected.
- Authentication and authorization work.
- Synchronization works.
- Required exports work.
- Logs show no continuing critical failure.
- The incident record is updated.
- The corrective commit is pushed to GitHub.

## 19. Incident Record

Every production rollback or recovery action must record:

- Incident date and time
- Environment
- Affected release
- Affected features
- Affected tournaments or users
- Detection method
- Root cause
- Actions taken
- Backup used
- Data restored or corrected
- Validation performed
- Responsible reviewer
- Follow-up tasks
- Required regression tests

## 20. Release Evidence

Each release record should contain:

- Version number
- Git commit
- Git tag
- Release date
- Release owner
- Migration list
- Edge Function versions
- Test commands
- Test results
- Device or emulator details
- Backup confirmation
- Smoke-test results
- Known limitations
- Approval status

## 21. Current Project Status

At the current preparation stage:

- The testing plan is defined.
- The backup process is defined.
- The rollback process is defined.
- Synthetic roster data is available for all 12 team slots.
- Real Free Fire MAX screenshots are not yet available.
- OCR acceptance testing remains deferred.
- Production backup commands must be validated before the first production deployment.
