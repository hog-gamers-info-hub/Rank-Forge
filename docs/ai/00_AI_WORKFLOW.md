# Rank-Forge - AI Workflow

## 1. Document Purpose

This document defines the approved AI-assisted workflow for Rank-Forge.

It governs how ChatGPT Work Mode and Codex coordinate, how approvals and tasks are prepared, how verification is reviewed, how documentation is kept aligned, and how blockers are handled.

This document does not define product requirements, does not replace canonical product documents, and does not authorize implementation by itself.

## 2. Scope and Current Status

Phase 0 remains documentation and governance cleanup because the Phase 0 closure audit found unresolved blockers.

Phase 1 Android implementation must not begin until Phase 0 blockers are resolved and the user explicitly approves Phase 1.

This file is one of the approved AI-governance documents.

This task populates only `docs/ai/00_AI_WORKFLOW.md`.

Other empty AI-governance files remain separate tasks unless explicitly approved.

This document does not claim that Phase 0 is closed.

## 3. Core AI Workflow Principles

* User decisions govern project direction.
* Work happens one approved task at a time.
* ChatGPT plans, reviews, audits, and prepares prompts only when requested.
* Codex implements only explicitly approved tasks.
* Codex must inspect existing repository state before editing.
* Implementation must remain phase-aligned.
* Documentation and code must remain consistent with approved decisions.
* Verification evidence is required before work is marked complete.
* Blockers must be reported instead of bypassed.
* Speculative implementation is prohibited.
* No task may automatically continue to the next task, phase, or version.
* Secrets and private data must not be exposed.

## 4. Source-of-Truth Precedence

When information conflicts, follow this order:

1. Current explicit user instructions and approved user decisions govern project intent, product scope, corrections, and approved exceptions.
2. Current explicitly approved implementation task governs immediate execution boundaries, files, restrictions, and verification requirements for that task only.
3. `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md` governs phase boundaries, version sequencing, dependencies, and implementation order.
4. Approved canonical product documents govern requirements within their respective domains.
5. `AGENTS.md` and approved `docs/ai/` workflow documents govern ChatGPT and Codex behavior, safety, approval gates, verification, Git workflow, and reporting.
6. Verified repository implementation and deployed configuration represent actual current state, but do not automatically redefine approved requirements or future scope.
7. Earlier discussions, drafts, assumptions, and inferred requirements rank last.

The approved implementation task controls immediate execution boundaries only. It cannot override product scope, roadmap sequencing, canonical requirements, or safety restrictions.

If a task conflicts with higher-priority authority, the dependent task must stop and the conflict must be reported.

## 5. User Role

The user owns approvals.

The user approves product decisions, phase transitions, scope changes, and implementation prompts.

The user decides when a Codex result is accepted.

The user decides when changes are saved to GitHub unless that authority is explicitly delegated.

The user must explicitly approve Phase 1 before Android implementation begins.

When the user clearly corrects an earlier assistant assumption, the user correction overrides that earlier assumption.

## 6. ChatGPT Work Mode Role

ChatGPT Work Mode is responsible for planning, architecture, requirement clarification, documentation review, task scoping, Codex prompt preparation when requested, verification review, debugging guidance, and closure audits.

ChatGPT must not provide Codex prompts unless explicitly requested.

ChatGPT must not treat recommendations as approved requirements.

ChatGPT must not mark work complete without evidence.

ChatGPT must correct the user when a requested task is already complete, out of sequence, unsafe, or inconsistent with the roadmap.

When repository state matters, ChatGPT must verify repository state before preparing the next task.

ChatGPT must distinguish completed work, planned work, deferred decisions, and blockers.

ChatGPT must not prepare or approve a Codex task that conflicts with higher-priority product scope, roadmap sequencing, or safety rules.

## 7. Codex Role

Codex is the implementation agent.

Codex must implement only the explicitly approved task.

Codex must read `AGENTS.md` and relevant documents before editing.

Codex must inspect repository state before editing.

Codex must modify only approved files.

Codex must not expand scope, refactor unrelated code, add unapproved dependencies, commit, push, or move to the next task unless explicitly instructed.

Codex must run required verification and report exact results.

Codex must stop and report blockers or conflicts.

## 8. GitHub Role

GitHub is the source of truth for code, documentation, project history, branches, pull requests, releases, tags, and verification evidence.

The `main` branch must contain only stable and verified work.

During the current Phase 0 manual-save workflow, user-reviewed documentation changes may be saved from `main` only after explicit save commands from the user. This does not authorize automatic commits, pushes, or broader branch-policy changes.

Branch creation is not part of the current Phase 0 manual-save flow unless explicitly approved.

Commits must remain focused.

Secrets, private screenshots, local environment files, generated build output, and sensitive artifacts must not be committed.

## 9. Planning Workflow

1. The user raises a requirement, correction, bug, audit item, or task request.
2. ChatGPT identifies the governing source documents.
3. ChatGPT checks whether the request is in scope and phase-aligned.
4. ChatGPT identifies dependencies and blockers.
5. ChatGPT proposes a bounded task only when requested or when planning is explicitly required.
6. The user approves the task before Codex implementation.
7. Codex executes only the approved task.
8. ChatGPT reviews Codex output and verification evidence.
9. The user confirms completion before the next task proceeds.

## 10. Codex Task Preparation Workflow

A Codex-ready task should include:

* Objective
* Files allowed
* Files restricted
* Authoritative sources
* Scope boundaries
* Out-of-scope items
* Required behavior
* Acceptance criteria
* Verification commands
* Completion-report requirements
* Explicit no-commit and no-push instruction unless save or commit is specifically approved
* Conflict-handling instruction

For current Phase 0 documentation tasks, prompts must preserve:

* One task at a time
* No branch creation unless approved
* No Android implementation
* No product invention
* No roadmap modification
* No `CHANGELOG.md` or `CONTRIBUTING.md` population unless explicitly approved

## 11. Codex Execution Workflow

* Codex must start by checking branch, working tree, and repository alignment.
* Codex must read authoritative sources.
* Codex must inspect target files before editing.
* Codex must avoid modifying unrelated files.
* Codex must preserve unrelated user changes.
* Codex must run required verification.
* Codex must return a completion report.
* Codex must not claim success when verification failed or was skipped.
* Codex must not continue into the next task automatically.

## 12. Review and Verification Workflow

ChatGPT reviews Codex reports against the approved task.

Verification must include changed files, diffs, commands run, and results.

For documentation tasks, verification must include at minimum `git diff --check`, scoped diffs, and `git status --short`.

For implementation tasks, verification must include the relevant tests, builds, lint, database or RLS checks, device checks, or other commands required by the approved task.

A task is not complete if required verification failed, was skipped, or modified unapproved files.

Any unverified claim must be treated as unverified.

## 13. Documentation Alignment Workflow

* Canonical documents must remain aligned with approved decisions.
* `README.md` is a high-level entry point only.
* Product documents govern their own domains.
* Workflow and AI documents govern process and agent behavior.
* The roadmap governs phase sequencing.
* Documentation updates must not silently change product scope.
* Deferred decisions must remain deferred unless explicitly approved.
* `CHANGELOG.md` and `CONTRIBUTING.md` remain deferred until their content is clearly defined.

## 14. Conflict and Blocker Handling

When a material conflict exists:

1. Stop the dependent task.
2. Identify the conflicting statements and their governing purposes.
3. Do not resolve the conflict through assumptions, inference, or speculative implementation.
4. Require an explicit user decision.
5. Update the appropriate canonical authority before continuing dependent work.

Blockers that must be reported include:

* Missing source documents
* Dirty working tree
* Unaligned branch
* Missing files
* Conflicting requirements
* Unverified implementation claims
* Failed verification
* Unauthorized file changes
* Security or secret exposure risk

## 15. Security and Privacy Workflow

* Secrets must not be exposed or committed.
* Android must not contain service-role or Google privileged credentials.
* Private screenshots must not be committed publicly.
* Supabase authorization must be ownership-based.
* Authentication alone is insufficient authorization.
* Destructive database or Git operations require explicit approval and rollback planning.
* Logs and reports must avoid secrets, tokens, private screenshot content, and sensitive OCR data.
* Security blockers must stop dependent work.

## 16. Git and Save Workflow

* Codex must not commit or push unless explicitly instructed.
* In the current manual-save workflow, ChatGPT may provide separate save commands after the Codex prompt when the user asks.
* Save commands must stage only approved files.
* Before committing, verify `git diff --check`, staged file names, staged diff, and branch alignment.
* Commit messages must remain focused and descriptive.
* Never force-push without explicit approval.
* Do not rewrite shared history.
* Do not include unrelated changes.

## 17. Phase and Roadmap Control

* The roadmap controls phase and version order.
* Phase 0 is the documentation and governance foundation.
* Phase 1 Android implementation cannot start until Phase 0 closure blockers are resolved and the user explicitly approves Phase 1.
* Later phases must not be started early.
* Completing a document does not automatically approve the next phase.
* A closure audit may identify blockers that require corrective documentation tasks before implementation.

## 18. Completion Reporting Requirements

Completion reports must include:

* Files changed
* Summary of changes
* Verification commands run
* Verification results
* Tests skipped and why
* Files intentionally unchanged
* Risks, blockers, assumptions, or unresolved conflicts
* Confirmation that no unauthorized files changed
* Confirmation that no commit or push occurred unless explicitly requested

## 19. Explicit Prohibitions

* Do not invent requirements.
* Do not override canonical documentation.
* Do not start Android implementation without explicit Phase 1 approval.
* Do not modify the roadmap unless explicitly approved.
* Do not populate `CHANGELOG.md` or `CONTRIBUTING.md` until their content is clearly defined.
* Do not modify files outside approved scope.
* Do not commit secrets or private screenshots.
* Do not treat OCR output as automatically correct.
* Do not treat fake screenshots as OCR acceptance evidence.
* Do not use Google Sheets as a source of truth.
* Do not claim implementation exists without repository evidence.
* Do not continue automatically to the next task.

## 20. Deferred AI Workflow Decisions

The following workflow decisions remain deferred:

* Exact future pull-request workflow after Phase 0
* Exact branch naming convention after branch-based development begins
* Exact issue-label taxonomy
* Exact release-note and changelog policy
* Exact contribution workflow
* Exact final closure-audit format after remaining blockers are resolved
* Exact implementation-task template for Phase 1
* Exact CI status-check policy after Android project creation
* Exact automated documentation validation rules

## 21. Roadmap Alignment

This file supports Phase 0 governance closure.

It does not start Phase 1.

Phase 1 still requires explicit user approval.

Future implementation tasks must follow roadmap sequencing and approved workflow rules.

A fresh Phase 0 closure audit is required after remaining audit blockers are resolved.
