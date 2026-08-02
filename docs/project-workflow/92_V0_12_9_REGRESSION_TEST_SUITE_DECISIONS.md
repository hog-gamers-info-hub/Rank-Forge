# v0.12.9 — Regression Test Suite Decisions

## Purpose

Add permanent automated regression protection for important defects already discovered and fixed in Rank-Forge.

This is a test-focused version. No production behavior change is planned.

## Core Rule

Every important historical defect must be:

1. already protected by an adequate permanent automated test;
2. given missing regression coverage in v0.12.9; or
3. explicitly documented as not meaningfully automatable.

Do not duplicate an existing test when it already fails if the original defect is reintroduced.

## Defect Audit

Before implementation, review merged fix PRs, Phase closure audits, Phase 12 findings, and current tests.

For each important defect record:

- defect/failure
- version or PR where fixed
- affected subsystem
- existing regression coverage
- whether additional coverage is required
- exact test file required if coverage is missing

Important candidates include:

- match date-picker behavior
- Room migration/restart recovery
- authentication/session recovery
- offline sync queue/retry behavior
- finalized-match and protected-correction safety
- screenshot Storage policy correlation
- Google Sheets retry/idempotency and uncertain writes
- API 26 instrumentation compatibility
- OCR team-matching evidence bridge defects
- important OCR acceptance behavior that can be protected deterministically

## Important Defect Definition

Regression protection is required where recurrence could materially affect:

- persisted data
- finalized match integrity
- scoring or standings
- roster integrity
- authentication
- offline recovery
- synchronization/idempotency
- authorization or screenshot privacy
- OCR team identification
- correction auditability
- export correctness or duplicate prevention
- supported-device workflows

Temporary command mistakes, documentation typos, and insignificant test-fixture mistakes do not automatically require regression tests.

## Test-Level Preference

Use the lowest reliable layer:

1. JVM/domain
2. Room/repository instrumentation
3. Compose/navigation instrumentation
4. pgTAP
5. Deno Edge Function
6. private/genuine-device testing only when necessary

## Production Defect Discovery

If the audit finds that an important production defect still exists, stop v0.12.9.

Create a separate narrow defect-fix version with its regression test, merge it, then resume v0.12.9.

## Privacy

Do not commit genuine screenshots, real player names, credentials, secrets, service-account material, or private OCR fixtures.

## Implementation Boundary

The exact test-file boundary will be frozen only after comparing the historical defect inventory with current permanent coverage.

No production files are approved for v0.12.9.

## Acceptance Criteria

v0.12.9 is complete when:

- important historical defects are inventoried;
- every defect has a regression disposition;
- adequate existing coverage is not duplicated;
- missing important coverage is added;
- all affected suites pass;
- no production behavior is changed;
- private acceptance material remains outside Git.

## Out of Scope

- product features
- production bug fixes
- scoring or standings changes
- OCR/matching tuning
- Room schema changes
- Supabase/RLS changes
- auth or sync algorithm changes
- export behavior changes
- UI redesign
- deployment
