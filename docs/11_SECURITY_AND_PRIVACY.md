# Rank-Forge - Security and Privacy

## 1. Document Purpose

This document defines the authoritative Rank-Forge MVP security and privacy requirements for Android, Supabase, Room, OCR screenshots, tournament data, exports, credentials, RLS, storage, logging, destructive operations, release gates, and production safety.

It consolidates approved security and privacy requirements already established by the canonical product, architecture, database, Android, Supabase, export, testing, workflow, and roadmap documents.

This document is documentation only. It does not create Android code, Supabase migrations, SQL policies, Edge Functions, storage buckets, tests, CI workflows, secrets, configuration, or deployment changes.

## 2. Security and Privacy Principles

The approved security and privacy principles are:

* Protect tournament data from unauthorized access.
* Protect finalized results from silent overwrite or corruption.
* Minimize client-side secrets.
* Treat authentication and authorization as separate concerns.
* Enforce ownership-based access in Supabase.
* Keep privileged credentials backend-only.
* Preserve OCR evidence without exposing it unnecessarily.
* Avoid logging secrets, tokens, private screenshots, or sensitive OCR content.
* Validate before finalization, synchronization, and export.
* Make failures explicit and recoverable where practical.
* Do not use fake screenshots as OCR acceptance evidence.
* Do not expose private tournament screenshots publicly without explicit approval.

## 3. Scope and Current Status

This file defines planned MVP security and privacy requirements.

It does not claim security implementation is complete unless verified in tracked repository files.

Android implementation begins according to the roadmap.

Supabase schema, RLS, storage, synchronization, and Edge Function implementation begin according to the roadmap.

Security verification is required before release.

Current verified repository state must be represented factually:

* The repository contains governance and canonical documentation under `docs/` and `docs/ai/`.
* The repository contains a root `.gitignore`.
* Supabase scaffolding is present, including `supabase/config.toml` and placeholder directories for migrations, functions, and tests.
* No tracked Android project has been verified.
* No tracked production Supabase schema, RLS policy set, storage bucket configuration, synchronization implementation, or Edge Function implementation has been verified.
* No tracked security implementation should be described as complete without repository evidence.

## 4. User and Access Model

The approved MVP actors are:

* Tournament organizer
* Authorized tournament operator or administrator

Access-model requirements:

* Users may access only authorized tournament data.
* Tournament ownership is the root authorization boundary.
* Child records inherit authorization through the parent tournament.
* Public spectator access is not an approved MVP requirement.
* Player-login, team-owner, payment, subscription, collaboration, and public-user models are outside approved MVP scope unless separately approved and roadmapped.

This document does not invent additional roles or permissions.

## 5. Authentication Requirements

Authentication requirements are:

* Supabase Auth supplies authenticated user identity.
* Android authentication workflow includes sign-up, login, logout, session restoration, and authentication error handling when implemented.
* Invalid or expired sessions require explicit handling.
* Logout must clear or protect local authenticated state appropriately.
* Offline access to approved local data does not imply backend authorization.
* Authentication alone does not grant access to all data.

This document does not define social login, phone login, MFA, password recovery, account deletion, or admin-user flows.

## 6. Authorization and Row Level Security

Authorization and RLS requirements are:

* Every exposed Supabase table requires RLS.
* RLS must enforce tournament ownership or approved access.
* Child-table access must be derived through parent tournament ownership.
* `TO authenticated` alone is insufficient.
* Cross-owner access must be denied.
* RLS must be tested separately for `SELECT`, `INSERT`, `UPDATE`, and `DELETE`.
* `UPDATE` access requires both read or access validation and write validation.
* Storage authorization must follow tournament ownership.
* Privileged backend functions must include explicit authorization checks.

This document does not define policy SQL, helper functions, table names, or grants.

## 7. Android Client Security

Android client security requirements are:

* Android may contain only approved public or client credentials.
* Supabase service-role credentials must never be included in Android.
* Google privileged credentials must never be included in Android.
* No unnecessary Android permissions may be added.
* Screenshot selection should use the approved least-access Android mechanism.
* Network, background, file, and export permissions must be justified by approved requirements.
* Sensitive screenshot or OCR content must not be logged unnecessarily.
* UI must clearly distinguish draft, pending, failed, conflicted, and finalized states.
* Finalized results must not reopen as freely editable drafts.
* Destructive actions require explicit confirmation where approved.

This document does not define exact Android permissions, package names, security libraries, biometric authentication, or certificate pinning.

## 8. Local Data and Room Security

Local data and Room security requirements are:

* Room stores approved local drafts, cache, OCR review state where required, recovery state, and pending synchronization operations.
* Room is not the permanent source of truth.
* Unsynchronized local data must remain recoverable.
* Local state must distinguish draft, pending, synchronized, conflicted, failed, and finalized data.
* Finalized records cached locally must not become freely editable.
* Stale local data must not overwrite newer or finalized backend data.
* Sensitive local data must be minimized.
* Local schema changes require Room migrations.
* Multi-record updates require transactions where integrity depends on atomicity.

This document does not define encryption choices, Room entities, DAOs, database versions, or migration code.

## 9. Supabase Backend Security

Supabase backend security requirements are:

* Supabase PostgreSQL is the permanent backend authority.
* Backend schema changes require versioned migrations.
* Applied production migrations must not be rewritten.
* Every exposed table requires ownership-based RLS.
* Foreign keys, uniqueness constraints, status transitions, and finalized-data protections must support integrity.
* Privileged database functions must be narrowly scoped.
* Service-role credentials are backend-only.
* Edge Functions must validate authentication, authorization, and request payloads.
* Manual production SQL must not replace migration history.
* Destructive changes require approval, backup, rollback planning, and rehearsal.

This document does not create SQL, policies, RPC names, or migration content.

## 10. Screenshot and OCR Privacy

Screenshot and OCR privacy requirements are:

* OCR applies only to genuine supported Free Fire MAX scoreboard screenshots.
* Scoreboard screenshots may contain private or personally sensitive data.
* Private screenshots must not be committed publicly.
* Fake screenshots cannot be used as OCR acceptance evidence.
* Original screenshots and raw OCR output must be preserved where permitted.
* Raw OCR evidence and corrected values must remain distinguishable.
* Screenshot access must follow tournament ownership.
* Logs must not expose private screenshot content unnecessarily.
* Real screenshot acceptance requires approved samples and manually verified ground truth.
* Roster-screenshot OCR and roster-image import are outside MVP scope.

This document does not define screenshot retention periods, deletion schedules, or public sharing behavior.

## 11. Storage Security

Storage security requirements are:

* Screenshot binaries belong in private controlled Supabase Storage.
* Relational records store metadata and storage references.
* Original screenshots remain distinguishable from processed variants.
* Storage reads, writes, updates, deletes, and signed access require explicit authorization.
* Storage access must follow tournament ownership.
* Missing or deleted storage objects must not silently corrupt tournament data.
* Service-role credentials remain backend-only.
* Private screenshots must not be committed to Git.

The following storage details remain deferred:

* Bucket names
* Object paths
* File-size limits
* Signed URL duration
* Retention periods
* Deletion schedules
* Image lifecycle rules

## 12. Export Security

Export security requirements are:

* Only finalized results may be exported.
* Draft, invalid, unresolved, or unconfirmed results must not be exported.
* CSV and Google Sheets are outputs, not sources of truth.
* Exported values must match finalized application data.
* Google Sheets export must run through the approved secure backend flow.
* Google credentials must never be stored in Android.
* Service-role credentials must never be stored in Android.
* Export authorization must verify tournament ownership.
* Repeated export attempts must not duplicate destination rows.
* Export logs must avoid secrets and sensitive private data.
* Private screenshots and raw OCR evidence are not official result exports unless separately approved.

This document does not define exact columns, sheet names, function names, payloads, or credentials.

## 13. Secrets and Credential Handling

Secrets and credential requirements are:

* Supabase service-role keys are backend-only.
* Google privileged credentials are backend-only.
* Android uses only approved public or client configuration.
* Secrets must not be committed.
* Local environment files containing secrets must not be committed.
* Repository history must not contain secrets.
* Secret exposure requires rotation and review.
* Production secrets require controlled access.
* Logs must not expose credentials, tokens, or sensitive content.

This document does not define exact secret names, environment variable names, hosting platform settings, or rotation procedures.

## 14. Logging and Error Reporting

Logging and error-reporting requirements are:

* Errors must be explicit enough for troubleshooting.
* Logs must not expose secrets, tokens, service-role keys, Google credentials, private screenshot content, or sensitive OCR data.
* Logs should distinguish validation errors, authorization failures, synchronization conflicts, duplicate operations, failed exports, and ignored idempotent retries where approved.
* Failed operations must not appear successful.
* Partial failures must not silently corrupt data.
* Error reporting must preserve existing valid data and support retry or correction where practical.

This document does not define exact log schemas, log tables, monitoring vendors, or alerting tools.

## 15. Data Integrity and Finalized-Result Protection

Data-integrity and finalized-result protection requirements are:

* Finalization is a controlled state transition.
* Finalized matches contribute to official standings and become export-eligible.
* Finalized data requires stronger protection than drafts.
* Finalized records must not be silently overwritten.
* Corrections to finalized results require explicit authorization and auditable history.
* Corrected finalized results must trigger deterministic recalculation.
* Stale local data must not restore older finalized values.
* Synchronization, export, and scoring must not duplicate finalized records or totals.
* Derived scoring values must remain reproducible from canonical finalized inputs.

This document does not define the exact approval workflow for finalized corrections.

## 16. Synchronization Security

Synchronization security requirements are:

* Supabase is the server authority.
* Android may queue local changes while offline.
* Pending operations require stable identity and idempotency.
* Repeated retries must not create duplicate records.
* Conflicts must be detected explicitly.
* Stale local data must not overwrite newer or finalized backend data.
* Failed sync operations must remain recoverable and visible.
* Cross-owner synchronization attempts must be rejected.
* Synchronization failures must not silently finalize or export invalid data.

This document does not define exact queue schema, retry timing, version columns, or merge algorithms.

## 17. Destructive Operations and Production Safety

Destructive-operation and production-safety requirements are:

* Destructive data operations require explicit approval.
* Production database changes require backup and rollback planning.
* Applied production migrations must not be rewritten.
* Corrective migrations must be used instead of editing history.
* Production deployment requires migration rehearsal where appropriate.
* Storage deletion requires approved handling.
* Secrets rotation requires controlled procedure when exposure is suspected.
* Release and operations documentation governs production deployment evidence.
* Critical and high security defects block release.

This document does not define exact backup providers, retention windows, or incident-response runbooks.

## 18. Repository and Git Security

Repository and Git security requirements are:

* GitHub is the source of truth for code and documentation.
* Secrets, private screenshots, local environment files, build artifacts, generated sensitive files, and personal data must not be committed.
* `.gitignore` must protect sensitive local files when implementation begins.
* Commits must be reviewed for accidental secret or private-data inclusion.
* Private tournament screenshots must not be stored in the public repository.
* Documentation must not include real secrets, access tokens, private keys, or sensitive screenshots.
* Any suspected secret commit requires immediate disclosure, rotation, and remediation.

This task does not add or modify `.gitignore`.

## 19. Security Testing Requirements

Required security tests must verify:

* Supabase RLS is enabled on exposed tables.
* Owners can access their own tournament hierarchy.
* Cross-owner access is denied.
* Authentication-only access is insufficient.
* `SELECT`, `INSERT`, `UPDATE`, and `DELETE` policy behavior is verified.
* Storage access follows tournament ownership.
* No service-role key exists in Android.
* No Google privileged credentials exist in Android.
* No secrets are committed.
* Unauthorized database writes are rejected.
* Edge Function authentication and authorization are verified.
* Export backend authorization is verified.
* Google Sheets credential isolation is verified.
* Finalized-data overwrite prevention is verified.
* Synchronization idempotency and conflict handling are verified.
* Duplicate export prevention is verified.
* Private screenshot access control is verified.
* Logs avoid secrets and private screenshot content.
* Destructive-operation approval evidence exists where required.

This document does not claim that these tests currently pass.

## 20. Privacy Requirements

Privacy requirements are:

* Collect and store only data required for approved tournament workflows.
* Do not expose private screenshots publicly.
* Do not export raw OCR data as official result output unless separately approved.
* Do not export secrets, tokens, storage paths, internal IDs intended only for infrastructure, or backend-only metadata.
* User-facing exports should contain approved tournament result and standings data only.
* Private screenshots and OCR evidence require controlled access.
* Retention and deletion rules remain deferred.
* Privacy-sensitive test data must be handled carefully and not committed publicly.

This document does not make legal compliance claims.

## 21. Deferred Security Decisions

The following security decisions remain deferred:

* Exact RLS policy SQL
* Exact storage bucket names and policies
* Exact secret names and environment variables
* Exact local data encryption decision
* Exact screenshot retention and deletion policy
* Exact signed URL duration
* Exact logging schema
* Exact monitoring and alerting approach
* Exact finalized-correction authorization workflow
* Exact incident-response procedure
* Exact backup provider and retention period
* Exact Android permission list
* Exact release security checklist template
* Exact privacy notice or user-facing policy text

These decisions must not be resolved in this document.

## 22. Roadmap Alignment

Security and privacy roadmap alignment is:

* Phase 1 establishes Android baseline without adding unapproved secrets or permissions.
* Phase 2 and Phase 3 validate manual tournament, roster, and match workflows.
* Phase 4 protects deterministic scoring.
* Phase 5 validates Room persistence, offline work, and local recovery.
* Phase 6 implements Supabase authentication, RLS, synchronization, idempotency, conflict handling, and finalized-data protection.
* Phase 7 introduces controlled screenshot intake and storage.
* Phase 8 and Phase 9 validate OCR, matching, correction, and raw-versus-confirmed data preservation.
* Phase 10 secures CSV and Google Sheets export.
* Phase 11 integrates full workflow error handling.
* Phase 12 completes security, privacy, backend, device, recovery, and regression validation.
* Phase 13 validates controlled real-tournament beta data handling.
* Phase 14 requires release-candidate and production-release evidence.
