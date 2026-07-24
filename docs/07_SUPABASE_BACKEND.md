# Rank-Forge — Supabase Backend

## 1. Document Purpose

This document defines the authoritative Rank-Forge MVP Supabase backend requirements, including authentication, PostgreSQL data authority, Row Level Security, storage, Edge Functions, synchronization, migrations, secrets, environment boundaries, backend testing, deployment controls, and operational safeguards.

It is documentation only. It does not create migrations, SQL, Edge Functions, storage buckets, Supabase config changes, secrets, Android code, tests, or deployment changes.

This document follows the approved authority hierarchy. Product documents define what the backend must support, architecture and database documents define backend boundaries, and the roadmap governs when backend capabilities are implemented.

## 2. Scope and Current Status

This file defines planned Supabase backend requirements for the Rank-Forge MVP.

Approved scope and status boundaries:

* Supabase backend implementation begins according to the roadmap, primarily Phase 6 and later backend-dependent phases.
* Current repository state must not be described as containing a production schema, deployed functions, storage buckets, or finalized backend implementation unless verified in tracked repository files.
* Supabase is the permanent backend data authority for authenticated tournament data.
* Room is local-only support for drafts, offline work, cache, recovery, and pending synchronization.
* CSV and Google Sheets are outputs, not backend databases of record.

Current verified repository status:

* The repository contains Supabase scaffolding, including `supabase/config.toml`.
* Placeholder directories for migrations, functions, and tests are present.
* No tracked production schema has been verified.
* No tracked Edge Function implementation has been verified.
* No tracked storage bucket definition has been verified.

## 3. Supabase Backend Principles

The approved Supabase backend principles are:

* Backend data must be reproducible from versioned migrations.
* Every exposed table requires RLS.
* Authorization must be ownership-based, not authentication-only.
* Service-role credentials remain backend-only.
* Google credentials remain backend-only.
* Backend operations must protect finalized data from silent overwrite.
* Synchronization and export operations must be idempotent.
* Privileged backend functions must be narrowly scoped.
* Destructive changes require explicit approval, backup, and rollback planning.
* Production migrations must not be rewritten after application.
* Manual production SQL must not replace maintained migration history.

## 4. Backend Responsibilities

Supabase is responsible for:

* Authentication
* Permanent PostgreSQL storage
* Ownership-based authorization
* Row Level Security
* Screenshot and export-related controlled storage
* Versioned database migrations
* Restricted backend functions
* Secure Google Sheets integration
* Synchronization authority
* Idempotency and duplicate prevention
* Finalized-data protection
* Backend-side validation where required
* Operational logging where approved

This document does not add payments, subscriptions, public spectator access, player accounts, social features, advertising, or unapproved multi-tenant organization features.

## 5. Authentication Requirements

Planned authentication requirements:

* Sign-up
* Login
* Logout
* Session restoration
* Authentication error handling
* Authenticated access to authorized tournament data

Requirements:

* Supabase Auth supplies user identity.
* Authenticated identity must link to tournament ownership.
* Expired or invalid sessions must be handled explicitly.
* Authenticated users may access only authorized tournament data.
* Authentication alone does not authorize access to every row.
* Anonymous public access is not an approved MVP requirement.
* Android must use only approved public or client credentials.
* Service-role credentials must never be included in Android.

This document does not define social login, phone login, MFA, account deletion, password recovery, admin accounts, or collaboration.

## 6. Authorization and Row Level Security

Approved authorization and RLS rules:

* Tournament ownership is the root authorization boundary.
* Child records must derive authorization through the parent tournament.
* RLS must apply to every exposed table.
* Policies must be tested for `SELECT`, `INSERT`, `UPDATE`, and `DELETE`.
* `TO authenticated` alone is insufficient because it checks authentication but not ownership.
* `UPDATE` policies require both read or access validation and write validation.
* Privileged backend functions must not bypass ownership checks unless narrowly approved and safely constrained.
* Storage access must follow the same ownership model.
* Cross-owner access must be denied.

This document does not define exact policy SQL, table names, helper functions, or grants.

## 7. PostgreSQL Data Model Boundary

The backend model must support the logical entities already defined in the database design:

* Authenticated user identity
* Tournament
* Tournament team slot
* Player
* Match
* Match result
* Screenshot metadata
* OCR processing run
* OCR observation or parsed row
* Team-match candidate or matching assessment
* Correction or revision history
* Synchronization operation
* Export operation
* Derived standing or reproducible standings projection

Requirements:

* Foreign keys and uniqueness constraints must protect data integrity.
* Exactly 12 team slots per tournament must be supported.
* Four-to-six-player roster validation must be supported.
* Maximum 10 matches per tournament must be supported.
* Finalized matches require exactly 12 valid result rows.
* Draft and finalized states must remain distinct.
* Raw OCR and corrected confirmed data must remain distinguishable.
* Derived scoring data must remain reproducible from canonical inputs.

This document does not define actual table names, SQL column types, indexes, enum values, triggers, RPCs, or functions.

## 8. Database Migration Rules

Approved migration rules:

* Supabase schema changes require versioned migration files.
* Migration files must be committed to the repository.
* Applied production migrations must not be edited, deleted, reordered, or rewritten.
* Corrections require new corrective migrations.
* Destructive migrations require explicit approval, backup, rollback plan, and rehearsal.
* Manual production SQL must not replace migration history.
* Migration order must remain deterministic.
* Production schema must be reproducible from migration history.
* Database changes require tests.
* Data backfills require explicit approval and verification.
* Local and remote schema history must remain aligned before pushing changes.

This task does not create a migration.

## 9. Storage Requirements

Approved storage requirements:

* Scoreboard screenshot binaries belong in private controlled Supabase Storage.
* Relational records store metadata and storage references.
* Original screenshots remain distinguishable from processed variants.
* Storage authorization follows tournament ownership.
* Storage upload, read, update, and delete behavior must be explicitly authorized.
* Service-role credentials remain backend-only.
* Private screenshots must not be committed to the repository.
* Missing or deleted storage objects must not silently corrupt relational tournament data.
* Duplicate screenshot detection uses approved metadata or content-hash boundaries.

Deferred storage details:

* Bucket names
* Object paths
* File-size limits
* Signed URL duration
* Retention period
* Deletion schedule
* Image lifecycle rules

These details remain unresolved here.

## 10. Edge Function Requirements

Planned Edge Function responsibilities:

* Secure Google Sheets export
* Privileged integration work that must not run in Android
* Backend-only credential use
* Controlled validation of authenticated and authorized requests
* Idempotent export operations
* Structured error responses
* Operational logging where approved

Requirements:

* Edge Functions must validate authentication where required.
* Authorization must confirm tournament ownership or approved access.
* Service-role and Google credentials remain server-side only.
* Request payloads must be validated.
* Repeated export retries must not duplicate destination rows.
* Failed function calls must not appear successful.
* Privileged functions must be narrowly scoped and testable.

This document does not define function names, request bodies, response shapes, deployment commands, or secret names.

## 11. Synchronization Requirements

Approved synchronization requirements:

* Supabase is the server authority for synchronized tournament data.
* Android may queue local changes while offline.
* Pending operations retry after connectivity returns.
* Synchronization operations require stable identity and idempotency.
* Repeated retries must not create duplicate tournaments, teams, players, matches, results, screenshots, or exports.
* Conflicts must be detected explicitly.
* Stale local data must not silently overwrite newer or finalized backend data.
* Failed sync operations must remain recoverable and visible.
* Draft and finalized data require different protections.
* Finalized backend data must not be downgraded by local cached data.

This document does not define retry intervals, merge algorithms, queue schemas, version columns, timestamp strategy, or conflict UI.

## 12. Finalized-Data Protection

Approved finalized-data protection rules:

* Finalization is a controlled state transition.
* Finalized matches contribute to official standings and become export-eligible.
* Finalized data requires stronger protection than draft data.
* Finalized records must not be silently overwritten.
* Post-finalization corrections require explicit authorization and auditable history.
* Correction must replace the current derived scoring contribution without losing history.
* Stale local writes must not restore older finalized values.
* Backend constraints, RLS, and transactional validation must work with Android validation.

This document does not define the exact approval hierarchy for finalized corrections.

## 13. Export Backend Requirements

Approved export backend requirements:

* CSV export may be Android-generated from finalized application data where approved.
* Google Sheets export must run through a secure Supabase backend flow.
* Google credentials must never be stored in Android.
* Only finalized data may be exported.
* Export output must match finalized scoring and standings.
* Repeated export attempts must not duplicate destination rows.
* Export status, success, failure, and retry state must be traceable.
* Google Sheets is an output destination, not a source of tournament truth.

This document does not define spreadsheet IDs, sheet names, exact columns, credential format, function names, or API payloads.

## 14. Secrets and Environment Boundaries

Approved secrets and environment boundaries:

* Android may contain only approved public or client configuration.
* Service-role keys are backend-only.
* Google credentials are backend-only.
* Local environment files and secrets must not be committed.
* Repository history must not contain secrets.
* Secret rotation is required if exposure is suspected.
* Production secrets require controlled access.
* Logs must not expose credentials, tokens, private screenshot data, or sensitive OCR content unnecessarily.

This document does not define specific secret names or hosting environment variables.

## 15. Local Development and CLI Rules

Approved local development and CLI rules:

* Supabase CLI usage must follow approved project workflow.
* CLI commands must be checked against current help or docs before implementation because Supabase changes frequently.
* Local development must not modify production without explicit approval.
* Local database work must use migrations rather than ad hoc permanent SQL.
* Supabase changes must be reviewed before push.
* Destructive commands require explicit approval and rollback plan.
* Docker or local Supabase requirements remain implementation-environment concerns and must be verified before use.
* No Supabase command is run by this documentation task.

This document does not add CLI commands as implementation instructions.

## 16. Backend Error Handling and Logging

Required explicit backend handling:

* Authentication failure
* Authorization failure
* RLS denial
* Invalid tournament ownership
* Invalid request payload
* Duplicate sync operation
* Conflict detection
* Finalized-data overwrite attempt
* Storage upload failure
* Storage access denial
* Missing storage object
* Edge Function failure
* Google Sheets export failure
* Migration failure
* Partial write failure

Requirements:

* Errors must be explicit and recoverable where practical.
* Failed backend operations must not appear successful.
* Partial failures must not silently corrupt data.
* Logs must support troubleshooting without exposing secrets or private screenshot data.
* Operational logs must distinguish ignored duplicates from failed operations where applicable.

This document does not define exact log tables, log schemas, messages, or monitoring vendors.

## 17. Backup, Rollback, and Production Safety

Approved production-safety rules:

* Production database changes require backup and rollback planning.
* Destructive data operations require explicit approval.
* Applied production migrations must not be rewritten.
* Corrective migrations must be preferred over editing history.
* Production deployment requires migration rehearsal where appropriate.
* Rollback plans must account for database, storage, Edge Functions, and secrets.
* Private screenshots and user tournament data require careful backup and deletion handling.
* Release and operations documentation governs production deployment evidence.

This document does not define exact backup providers, retention windows, or incident-response runbooks.

## 18. Backend Testing Requirements

Required database tests:

* Migration application
* Foreign-key integrity
* Uniqueness constraints
* Check constraints where approved
* Controlled status transitions
* Draft and finalized separation
* Finalized-data overwrite prevention

Required RLS and authorization tests:

* Owner can access own tournament hierarchy
* Cross-owner access is denied
* Authentication-only access is insufficient
* `SELECT`, `INSERT`, `UPDATE`, and `DELETE` are tested separately
* Storage access follows tournament ownership
* Privileged function access is constrained

Required synchronization tests:

* Offline queue replay
* Retry without duplicate records
* Conflict detection
* Stale local write prevention
* Duplicate operation idempotency

Required storage tests:

* Valid screenshot metadata and storage reference
* Duplicate screenshot hash handling
* Private access controls
* Missing object handling

Required Edge Function and export tests:

* Authenticated request validation
* Ownership validation
* Invalid payload handling
* Google Sheets credential isolation
* Retry without duplicate rows
* Export totals matching finalized data

Required security tests:

* No service-role or Google credentials in Android
* No secrets committed
* Logs avoid sensitive data
* Destructive operations require approval evidence

This document does not claim that these tests currently pass.

## 19. Security Review Requirements

Approved security review requirements:

* RLS must be reviewed before release.
* Backend tables exposed through APIs require RLS.
* Authorization must be ownership-based.
* Privileged functions require narrow purpose and explicit checks.
* Storage policies must match tournament ownership.
* Secrets must be isolated from client code.
* Logs must not expose sensitive data.
* Migration history must be clean and reproducible.
* Production data operations require approval evidence.
* Security review belongs to Phase 12 and release readiness phases.

## 20. Deferred Supabase Decisions

The following Supabase decisions remain deferred:

* Exact Supabase project or environment structure
* Exact schema names
* Exact table names
* Exact SQL column types
* Exact RLS policy definitions
* Exact RPC or function names
* Exact Edge Function names
* Exact request or response payloads
* Exact storage bucket names
* Exact object path structure
* Exact signed URL duration
* Exact retention and deletion policy
* Exact sync conflict-resolution strategy
* Exact idempotency key format
* Exact migration naming strategy beyond Supabase CLI generation
* Exact backup process
* Exact monitoring and alerting approach
* Exact production deployment checklist details

These decisions are intentionally not resolved here.

## 21. Roadmap Alignment

Approved roadmap alignment:

* Phase 6 implements Supabase authentication, backend schema, RLS, tournament cloud storage, match cloud storage, offline sync queue, idempotency, conflict resolution, and finalized-data protection.
* Phase 7 uses Supabase Storage for approved screenshot storage and metadata.
* Phase 8 and Phase 9 rely on backend persistence for OCR, matching, review, correction, and raw-versus-confirmed preservation.
* Phase 10 uses Supabase Edge Functions for secure Google Sheets export.
* Phase 11 integrates local persistence, backend sync, finalization, and export flows.
* Phase 12 validates backend schema, RLS, synchronization, authorization, storage, security, and recovery.
* Phase 13 rehearses production migrations and release configuration.
* Phase 14 deploys approved database, Edge Functions, storage configuration, and production release artifacts.
