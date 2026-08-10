# Rank Forge Change Register

## Purpose

This directory tracks UI, workflow, feature, infrastructure, deployment, and product improvements that are intentionally managed outside the approved Phase 13 sequence.

These change records must not modify, rename, reorder, or become part of the existing Phase 13 roadmap.

## Current Baseline

Phase 13 has been completed through:

* v0.13.0 — Internal Alpha
* v0.13.1 — Controlled Real-Tournament Beta
* v0.13.2 — Beta Defect Resolution

The remaining approved Phase 13 versions remain unchanged:

* v0.13.3 — Performance Optimization
* v0.13.4 — Migration Rehearsal
* v0.13.5 — Release Configuration
* v0.13.6 — Production Operations Review

New product, infrastructure, or deployment changes documented here are handled separately before Phase 13 resumes.

## Change Records

| ID     | Change                     | Decision Record                              | Status             |
| ------ | -------------------------- | -------------------------------------------- | ------------------ |
| CR-001 | Google Sign-In             | `01_GOOGLE_SIGN_IN_DECISIONS.md`             | Complete           |
| CR-002 | Hosted Supabase Deployment | `02_HOSTED_SUPABASE_DEPLOYMENT_DECISIONS.md` | Decisions Approved |

## Status Definitions

* **Planned** — identified but not yet audited.
* **Audit In Progress** — existing implementation and external requirements are being reviewed.
* **Audit Complete — Decisions Pending** — read-only audit is complete; implementation decisions have not yet been formally approved.
* **Decisions Approved** — decision document has been reviewed and accepted.
* **Implementation In Progress** — approved implementation work has started.
* **Verification In Progress** — implementation is complete and verification is underway.
* **Complete** — implementation, verification, and documentation are complete.

## Change Management Rules

1. Every substantial change receives its own numbered decision record.
2. Existing implementation must be audited before implementation decisions are finalized.
3. Documentation decisions are completed before source-code implementation begins.
4. Each implementation must have a defined scope, acceptance criteria, verification plan, and rollback plan.
5. Existing working behavior must remain unchanged unless the relevant decision record explicitly approves a modification.
6. Phase 13 files, scope, and version sequence remain independent from these change records.
7. Supabase production changes, external provider configuration, database changes, or other deployment actions require their own explicit execution step.
8. GitHub remains the source of truth for merged project state.
9. Historical production migration files must not be edited after application; corrections must normally use new forward migrations.
10. Production-impacting deployment work must stop on unexpected migration history, target-project mismatch, failed verification, or unexplained hosted-state divergence.

## Current Change Record

CR-002 — Hosted Supabase Deployment

Current status:

**Decisions Approved**

Approved next stage:

```text
Synchronize clean main
→ confirm exact approved baseline
→ inspect installed Supabase CLI capabilities
→ confirm hosted Rank-Forge project
→ inspect remote migration history
→ run migration dry-run
→ stop and review before actual deployment
```

No hosted database, Storage, Edge Function, Auth, or secret change is authorized until the deployment preconditions and dry-run gates in `02_HOSTED_SUPABASE_DEPLOYMENT_DECISIONS.md` pass.
