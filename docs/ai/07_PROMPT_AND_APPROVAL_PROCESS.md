# Rank-Forge - Prompt and Approval Process

## 1. Document Purpose

This document defines how prompts and approvals are handled in Rank-Forge.

It governs how ChatGPT prepares prompts when requested, how Codex tasks are scoped and approved, and how corrections, audits, completion reviews, and save commands are handled.

It does not authorize implementation by itself and does not replace product, roadmap, testing, security, or Git rules.

## 2. Scope and Current Status

Phase 0 remains documentation and governance cleanup until blockers are resolved and a fresh closure audit passes.

Phase 1 Android implementation must not begin from this document.

This task populates only `docs/ai/07_PROMPT_AND_APPROVAL_PROCESS.md`.

Prompt rules apply to current documentation tasks and future implementation tasks.

`CHANGELOG.md` and `CONTRIBUTING.md` remain deferred until their required content is clearly defined.

This document does not claim that Phase 0 is closed.

## 3. Core Approval Principles

* User approval is required before implementation.
* Work proceeds one approved task at a time.
* ChatGPT must not provide a Codex prompt unless explicitly requested.
* ChatGPT must correct the user when the requested task is already complete, out of sequence, unsafe, or blocked.
* Codex must implement only the approved prompt.
* Verification evidence is required before task closure.
* Saving to GitHub is separate from Codex completion unless explicitly approved.
* A completed task does not automatically approve the next task.
* Phase transitions require explicit user approval.
* Blockers must be resolved before dependent work continues.

## 4. Source-of-Truth Precedence

When information conflicts, follow this order:

1. Current explicit user instructions and approved user decisions govern project intent, product scope, corrections, and approved exceptions.
2. Current explicitly approved implementation task governs immediate execution boundaries, files, restrictions, and verification requirements for that task only.
3. `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md` governs phase boundaries, version sequencing, dependencies, and implementation order.
4. Approved canonical product documents govern requirements within their respective domains.
5. `AGENTS.md` and approved `docs/ai/` workflow documents govern ChatGPT and Codex behavior, safety, approval gates, verification, Git workflow, and reporting.
6. Verified repository implementation and deployed configuration represent actual current state, but do not automatically redefine approved requirements or future scope.
7. Earlier discussions, drafts, assumptions, and inferred requirements rank last.

The approved task controls immediate execution boundaries only. It cannot override product scope, roadmap sequencing, canonical requirements, or safety restrictions.

If a prompt conflicts with higher-priority authority, the dependent task must stop and the conflict must be reported.

## 5. Task Request Intake

When a user requests work, ChatGPT must:

* Identify whether the request is for planning, a Codex prompt, a correction prompt, an audit, a review, or save commands.
* Verify whether the previous task is completed when repository state matters.
* Verify the latest commit before issuing the next task when changes were saved.
* Determine whether the requested task is next in sequence.
* Identify the allowed target files.
* Identify restricted files.
* Identify authoritative sources.
* Identify blockers and dependencies.
* Ask for clarification only when essential; otherwise provide a best-effort scoped response.
* Not repeat a prompt for a task that is already completed.

## 6. Task Readiness Review

Before producing a Codex prompt, ChatGPT must check:

* Whether the previous task is completed and saved, if required
* Whether the requested task is still valid
* Whether it is in the correct phase and sequence
* Whether the closure audit identifies unresolved blockers
* Whether authoritative source files are available
* Whether the target file is empty, stale, incorrect, or already populated
* Whether source conflicts exist
* Whether the task requires implementation approval
* Whether the task touches restricted files
* Whether the task risks secrets, destructive changes, or unapproved scope

If a task is not ready, ChatGPT must explain why and provide the correct next task.

## 7. Prompt Preparation Rules

A prompt must be:

* Bounded
* Specific
* Source-grounded
* File-scoped
* Phase-aligned
* Verification-driven
* Explicit about out-of-scope items
* Explicit about prohibited actions
* Explicit about completion-report requirements
* Conservative about security and destructive operations

A prompt must not:

* Invent requirements
* Modify unrelated files
* Start the next phase
* Create branches unless approved
* Commit or push unless explicitly approved
* Override canonical documents
* Resolve deferred decisions by assumption
* Hide blockers

## 8. Codex Prompt Requirements

Every Codex prompt should include:

* Task title
* Files allowed
* Objective
* Documentation or implementation type
* Authoritative sources
* Authority rules
* Required structure or behavior
* Required changes
* Explicit `Do not` section
* Before-editing checks
* Verification commands
* Acceptance criteria
* Completion-report requirements

For implementation tasks, also include:

* Files likely involved
* Files restricted
* Tests required
* Build or lint checks
* Rollback considerations
* Security considerations
* Data-integrity requirements

For documentation-only tasks, explicitly state:

* Documentation work only
* No code
* No tests
* No migrations
* No configuration
* No implementation
* No phase transition

## 9. Correction Prompt Requirements

Correction prompts must:

* Identify the exact defect or inconsistency.
* Cite or name the affected file or files.
* Limit changes to the smallest safe scope.
* Preserve approved content outside the defect.
* Avoid broad rewrites unless necessary.
* Include before-and-after intent where helpful.
* Include verification proving only approved files changed.
* Avoid changing roadmap or product scope unless explicitly approved.

Use correction prompts for:

* Broken links
* Stale status text
* Inconsistent source-of-truth rules
* Empty approved governance files
* Incorrect scope wording
* Local machine-specific paths
* Documentation contradictions

## 10. Audit Prompt Requirements

Audit prompts must:

* Define the audit question.
* Define the repository baseline.
* Define authoritative sources to review.
* Require factual findings.
* Distinguish complete, blocked, deferred, and not applicable items.
* Record evidence and verification commands.
* Avoid modifying canonical documents unless explicitly scoped.
* Avoid resolving blockers by assumption.
* Produce a closure decision only when requested.
* Require a follow-up corrective task when blockers remain.

Audit outputs must not approve implementation unless the user explicitly grants the phase transition.

## 11. Save-to-GitHub Command Process

Save commands are separate from the Codex prompt.

The user may ask ChatGPT for save commands after reviewing Codex output.

Save commands must stage only the approved changed files.

Save commands must include:

* `git status --short`
* `git diff --check`
* Scoped `git diff`
* `git fetch origin`
* Branch-alignment check
* Scoped `git add`
* Staged file-name check
* Staged diff check
* Focused commit message
* `git push origin main`
* Final status and latest-commit check

Save commands must not include force-push.

Save commands must not stage unrelated files.

Commit messages must be focused and descriptive.

For the current Phase 0 manual-save workflow, branch creation is not included unless explicitly approved.

## 12. Completion Review Process

After Codex reports completion, ChatGPT must review:

* Files changed
* Whether only approved files changed
* Whether scope was preserved
* Whether required sections or behavior were implemented
* Verification commands run
* Verification results
* Tests skipped and reasons
* Known blockers or limitations
* Whether the result should be accepted, corrected, or reworked

If evidence is missing, ChatGPT must not mark the task complete.

## 13. Task Closure Rules

A task can be treated as complete only when:

* The requested scope is satisfied.
* Required verification passed.
* No unapproved files changed.
* No unresolved blocker remains for that task.
* The completion report is adequate.
* The user accepts or saves the result as appropriate.
* GitHub save is verified when the task is supposed to be saved.

A saved task should be verified by checking the latest commit on `main`.

## 14. Next-Task Rules

Before providing the next task:

* Verify the previous saved commit when GitHub state matters.
* Check whether the previous task introduced new findings.
* Check the closure audit or roadmap for the correct next task.
* Do not assume the task number implies the next phase.
* If the user asks for a completed task again, inform them that it is already completed.
* If the user asks for an out-of-sequence task, explain the correct sequence.
* If blockers remain, target blockers before Phase 1.
* Do not start Android implementation until Phase 1 is explicitly approved.

## 15. Out-of-Sequence Request Handling

ChatGPT must correct the sequence when:

* The requested task is already completed.
* A blocker remains.
* The requested task would start a future phase too early.
* The requested task would modify restricted files.
* The requested task conflicts with roadmap sequencing.
* The requested task would implement unapproved scope.
* The requested task would expose secrets or perform destructive operations.

Correction must be direct and include the correct next task.

## 16. Blocker and Conflict Handling

When a material conflict exists:

1. Stop the dependent task.
2. Identify the conflicting statements and their governing purposes.
3. Do not resolve the conflict through assumptions, inference, or speculative implementation.
4. Require an explicit user decision.
5. Update the appropriate canonical authority before continuing dependent work.

Blockers include:

* Empty required governance files
* Stale canonical status text
* Broken or local-machine-specific links
* Dirty working tree
* Branch mismatch
* Local or remote divergence
* Missing target file
* Contradictory source documents
* Verification failure
* Unauthorized file changes
* Security or privacy risk

## 17. Security and Restricted-Action Approval

* Destructive operations require explicit approval, risk explanation, backup plan, rollback plan, and verification plan.
* Secret exposure requires stopping and reporting.
* Production database changes require explicit approval.
* Applied migrations must not be rewritten.
* Force-push requires explicit approval and should not be part of normal workflow.
* Private screenshots must not be committed.
* Android must not contain backend-only credentials.
* Google privileged credentials must remain backend-only.
* Any prompt involving secrets, production changes, storage deletion, release publishing, or signing must include explicit safety constraints.

## 18. Phase Transition Approval

* Phase closure requires audit evidence.
* A closure audit may return `not ready`.
* If blockers remain, the next tasks must resolve blockers.
* Phase 1 Android implementation requires explicit user approval after Phase 0 closure.
* No prompt may treat a completed document as automatic approval for implementation.
* Later phases must follow roadmap dependencies.

## 19. Explicit Prohibitions

* Do not provide Codex prompts unless explicitly requested.
* Do not repeat prompts for tasks already completed.
* Do not start Phase 1 without explicit approval.
* Do not bypass blockers.
* Do not invent requirements.
* Do not modify the roadmap unless explicitly approved.
* Do not modify files outside approved scope.
* Do not populate `CHANGELOG.md` or `CONTRIBUTING.md` until their content is clearly defined.
* Do not commit or push unless explicitly requested.
* Do not force-push unless explicitly approved.
* Do not expose secrets.
* Do not claim verification passed when it did not run.
* Do not claim implementation exists without repository evidence.
* Do not allow Codex to continue to the next task automatically.

## 20. Deferred Prompt and Approval Decisions

The following prompt and approval decisions remain deferred:

* Exact future pull-request workflow after Phase 0
* Exact branch naming convention after branch-based development begins
* Exact issue and milestone workflow
* Exact changelog approval process
* Exact contributing workflow
* Exact release approval checklist
* Exact Phase 1 implementation-task template
* Exact CI-required status checks after Android project creation
* Exact automated documentation validation
* Exact prompt-review checklist format
* Exact final Phase 0 closure approval wording

## 21. Roadmap Alignment

This file supports Phase 0 governance closure.

It does not start Phase 1.

Phase 1 still requires explicit user approval after closure blockers are resolved.

Future implementation prompts must follow roadmap sequencing.

A fresh Phase 0 closure audit is required after remaining audit blockers are resolved.
