# Rank-Forge - AI Security and Restrictions

## 1. Document Purpose

This document defines security restrictions for ChatGPT, Codex, and any AI-assisted project work in Rank-Forge.

It protects secrets, data integrity, private screenshots, backend authority, Git history, and approved product scope.

It does not define product features, does not replace `docs/11_SECURITY_AND_PRIVACY.md`, and does not approve implementation by itself.

## 2. Scope and Current Status

Phase 0 remains documentation and governance cleanup until closure blockers are resolved.

Phase 1 Android implementation must not begin from this document.

This task populates only `docs/ai/04_SECURITY_AND_RESTRICTIONS.md`.

Remaining blockers must be handled through separate approved tasks.

These security rules apply to documentation tasks and future implementation tasks.

This document does not claim that Phase 0 is closed.

## 3. Security Principles for AI-Assisted Work

* Protect secrets and credentials.
* Preserve approved product scope.
* Preserve roadmap sequencing.
* Protect user and tournament data.
* Protect private screenshots and OCR evidence.
* Prevent unauthorized backend access.
* Prevent stale local data from overwriting server data.
* Keep privileged credentials backend-only.
* Require explicit approval for destructive actions.
* Stop when security uncertainty exists.
* Prefer reporting blockers over making unsafe assumptions.

## 4. Source-of-Truth Precedence

When information conflicts, follow this order:

1. Current explicit user instructions and approved user decisions govern project intent, product scope, corrections, and approved exceptions.
2. Current explicitly approved implementation task governs immediate execution boundaries, files, restrictions, and verification requirements for that task only.
3. `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md` governs phase boundaries, version sequencing, dependencies, and implementation order.
4. Approved canonical product documents govern requirements within their respective domains.
5. `AGENTS.md` and approved `docs/ai/` workflow documents govern ChatGPT and Codex behavior, safety, approval gates, verification, Git workflow, and reporting.
6. Verified repository implementation and deployed configuration represent actual current state, but do not automatically redefine approved requirements or future scope.
7. Earlier discussions, drafts, assumptions, and inferred requirements rank last.

The approved task controls immediate execution boundaries only. It cannot override product scope, roadmap sequencing, canonical requirements, or security restrictions.

If a task conflicts with security restrictions, Codex must stop and report the conflict.

## 5. Secret and Credential Restrictions

* Never expose, request, print, commit, log, or invent secrets.
* Supabase service-role keys are backend-only.
* Google privileged credentials are backend-only.
* Android may contain only approved public or client configuration.
* Local `.env` files and credential files must not be committed.
* Repository history must not contain secrets.
* If a secret is suspected to be exposed, stop and report the risk.
* Do not ask the user to paste secrets into chat unless absolutely necessary and approved.
* Completion reports must not include secrets, tokens, private keys, signed URLs, or credential contents.

## 6. Android Client Restrictions

* Do not add Android code before Phase 1 is explicitly approved.
* Do not add unnecessary Android permissions.
* Do not place service-role keys or Google privileged credentials in Android.
* Do not log private screenshots, OCR content, auth tokens, or sensitive tournament data unnecessarily.
* Do not treat cached Room data as permanent authority.
* Do not allow finalized records to reopen as freely editable drafts.
* Do not add camera capture unless separately approved; screenshot intake uses the approved least-access mechanism when implemented.
* Do not introduce biometric auth, certificate pinning, encryption libraries, or security dependencies unless explicitly approved.
* Do not claim Android implementation exists unless verified in tracked files.

## 7. Supabase and Database Restrictions

* Do not create Supabase migrations unless an approved implementation task explicitly allows it.
* Do not run destructive SQL without explicit approval, backup, and rollback plan.
* Do not edit, delete, reorder, or rewrite applied production migrations.
* Do not use manual production SQL as a replacement for migration history.
* Do not expose service-role credentials to Android.
* Every exposed table must use ownership-based RLS.
* Authentication-only policies are insufficient.
* Privileged functions must not bypass ownership checks unless narrowly approved and safely constrained.
* Stale local data must not silently overwrite newer or finalized backend data.
* Database and RLS behavior must be tested when affected.
* Do not invent table names, policies, grants, or RPC names in governance documents unless already approved.

## 8. Storage and Screenshot Restrictions

* Private screenshots must not be committed publicly.
* Screenshot storage must follow tournament ownership.
* Original screenshots and processed variants must remain distinguishable when implemented.
* Missing or deleted storage objects must not silently corrupt tournament data.
* Signed access, bucket names, object paths, retention, and deletion policies are deferred.
* Do not invent storage bucket names or paths.
* Do not log private screenshot binary data.
* Do not expose private screenshot URLs in completion reports.

## 9. OCR and Image-Data Restrictions

* Scoreboard OCR applies only to genuine supported Free Fire MAX scoreboard screenshots.
* The separately staged roster screenshot OCR extension uses privately preserved, operator-cropped roster panels only.
* Roster OCR output is candidate data only and must never automatically replace or confirm a roster.
* Representative screenshots and manually verified ground truth are required before roster layout or extraction-accuracy work; crop coordinates must not be guessed.
* Fake screenshots are not OCR acceptance evidence.
* OCR output must never be treated as automatically correct.
* Low-confidence or uncertain OCR must require review.
* Raw OCR and corrected confirmed values must remain distinguishable.
* OCR confidence must not affect tournament scoring.
* Do not introduce external OCR, cloud OCR, generative vision, or OpenAI Vision unless separately approved and roadmapped.
* Do not claim real OCR acceptance without approved real screenshots and manually verified ground truth.
* Do not commit real roster screenshots, player names, raw OCR payloads, or private paths without explicit privacy approval; prefer synthetic sanitized fixtures and local-only approved evaluation.

## 10. Export and Google Sheets Restrictions

* Only finalized data may be exported.
* Draft, invalid, unresolved, or unconfirmed results must not be exported.
* Google Sheets is an output destination, not the source of truth.
* Google Sheets export must run through an approved secure backend flow.
* Google credentials must never be stored in Android.
* Repeated export attempts must not duplicate destination rows.
* Export failures must not appear successful.
* Do not invent sheet names, spreadsheet IDs, function names, payloads, credential names, or exact columns unless approved.
* Do not export raw OCR evidence, secrets, internal backend metadata, or private screenshot binaries as official result output unless separately approved.

## 11. Git and Repository Restrictions

* Do not commit or push unless explicitly instructed.
* Do not force-push without explicit approval.
* Do not rewrite shared history.
* Do not create or switch branches unless approved.
* Do not stage unrelated files.
* Do not commit secrets, private screenshots, local environment files, generated build output, or sensitive artifacts.
* Do not modify `CHANGELOG.md` or `CONTRIBUTING.md` until their content is clearly defined and approved.
* GitHub remains the source of truth.
* Completion reports must list changed files and verification results.

## 12. Destructive Operation Restrictions

The following require explicit approval, risk explanation, backup, rollback plan, and verification plan:

* Destructive SQL
* Production database changes
* Storage deletion
* Migration rollback
* File deletion
* Force push
* History rewrite
* Secret rotation actions
* Production deployment changes
* Release publishing
* App signing changes

If backup or rollback cannot be verified, stop and report the blocker.

Do not recommend destructive operations as routine cleanup.

## 13. Documentation Safety Restrictions

* Documentation must not invent product requirements.
* Documentation must not silently resolve deferred decisions.
* Documentation must not claim implementation exists without repository evidence.
* Documentation must preserve roadmap sequencing.
* Documentation must distinguish completed work, planned work, deferred decisions, and blockers.
* Documentation must not include secrets, tokens, credentials, private screenshot content, or sensitive OCR data.
* Documentation must not use local machine-specific paths in GitHub-facing links.
* Documentation must not populate unrelated files in a scoped task.
* `README.md` is an entry point only and does not replace canonical documents.

## 14. Testing and Verification Restrictions

* Do not claim tests pass unless they were run and results are reported.
* Do not remove or weaken tests to make work pass.
* Do not skip required verification silently.
* Failed verification blocks completion.
* Documentation tasks require at least scoped diff checks and `git diff --check`.
* Implementation tasks require relevant unit, integration, UI, Room, Supabase and RLS, OCR, export, security, device, or recovery tests as applicable.
* If Android implementation does not exist, do not claim Android build or test success.
* If Supabase implementation does not exist, do not claim database or RLS test success.

## 15. Logging and Reporting Restrictions

* Do not log secrets, tokens, service-role keys, Google credentials, private screenshots, sensitive OCR content, or private user data.
* Completion reports must be factual.
* Known skipped tests must be reported.
* Unverified implementation claims must be labeled unverified.
* Blockers must be explicit.
* Reports must distinguish failed, skipped, not applicable, and successful verification.
* Do not hide partial failures.
* Do not claim completion when required verification failed or was not performed.

## 16. Privacy and Sensitive-Data Restrictions

* Collect and store only approved tournament workflow data.
* Private screenshots and OCR evidence require controlled access.
* Do not commit real private screenshots to the repository.
* Do not export raw OCR data as official result output unless approved.
* Do not expose internal IDs, storage paths, tokens, or backend-only metadata in public-facing outputs unless approved.
* Privacy-sensitive test data must not be committed publicly.
* Retention and deletion policy remains deferred.
* Do not make legal compliance claims.

## 17. Dependency and Tooling Restrictions

* Do not add dependencies unless explicitly approved and phase-aligned.
* Do not add monitoring or crash-reporting tools without privacy and operational approval.
* Do not introduce cloud OCR, generative vision, payment tools, analytics, or external APIs outside the approved roadmap.
* Do not add Android permissions merely because a dependency requests them.
* Tool versions must be chosen during implementation tasks, not invented in governance documents unless already approved.
* Verify current CLI or tool behavior before using commands that may change state.

## 18. Blocker and Stop Conditions

ChatGPT or Codex must stop and report when:

* The working tree is dirty with unrelated changes.
* The branch is not the expected branch.
* Local and remote are not aligned.
* Required source documents are missing.
* Target files do not exist.
* Requirements conflict.
* A requested task conflicts with product scope, roadmap sequencing, or safety rules.
* Secrets or private data are exposed.
* A command may modify production state without approval.
* Verification fails.
* Unauthorized files are changed.
* Implementation is requested before phase approval.
* The user asks for a task that is already complete or out of sequence.

## 19. Deferred Security Decisions

The following security decisions remain deferred:

* Exact secret names and environment variable names
* Exact local encryption strategy
* Exact screenshot retention and deletion policy
* Exact signed URL duration
* Exact logging schema
* Exact monitoring and alerting platform
* Exact incident-response procedure
* Exact backup provider and retention window
* Exact Android permission list
* Exact RLS policy SQL
* Exact storage bucket names and object paths
* Exact release security checklist
* Exact secret-rotation procedure
* Exact production access model

## 20. Roadmap Alignment

This file supports Phase 0 governance closure.

It does not start Phase 1.

Phase 1 still requires explicit user approval.

Security restrictions apply across all future phases.

A fresh Phase 0 closure audit is required after remaining audit blockers are resolved.

Future implementation tasks must follow roadmap sequencing and approved security restrictions.
