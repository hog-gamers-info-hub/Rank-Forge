# v0.11.0 — Tournament Setup Flow Decisions

## 1. Decision Status

**Status:** Approved for implementation after this decision document is merged.

This document defines the implementation contract for Phase 11:

**v0.11.0 — Tournament Setup Flow**

The version integrates already-completed tournament creation, roster entry, validation, and local persistence into one coherent workflow.

This version is primarily workflow integration.

It must reuse existing tournament, roster, validation, and Room persistence behavior rather than introduce parallel implementations.

---

## 2. Canonical Scope

The canonical roadmap defines v0.11.0 as:

> Connect tournament creation, roster entry, validation, and persistence.

The intended workflow is:

`Tournament Creation`
→ `Persisted Tournament`
→ `Team / Roster Entry`
→ `Roster Validation`
→ `Roster Review / Confirmation`
→ `Confirmed Roster Persistence`
→ `Tournament Details`

The same tournament identity must be preserved throughout the complete workflow.

---

## 3. Existing Integration Baseline

The repository already provides the core capabilities required for v0.11.0.

Existing behavior includes:

- tournament creation
- tournament list and tournament details
- team-slot management
- manual roster entry
- roster review
- roster validation
- confirmed roster persistence
- Room-backed tournament persistence
- Room-backed roster persistence
- application restart recovery
- existing authentication and cloud synchronization capabilities from later completed phases

v0.11.0 must connect these capabilities into a clear tournament-setup sequence.

It must not replace working domain or persistence logic with new workflow-specific implementations.

---

## 4. Tournament Setup Sequence

The authoritative v0.11.0 sequence is:

1. User creates a tournament.
2. Tournament creation is validated through the existing creation rules.
3. The tournament is persisted through the existing repository and Room persistence boundary.
4. The exact persisted tournament identity is used to continue the setup workflow.
5. The user proceeds directly to the existing team/roster setup flow for that tournament.
6. The user enters or completes the tournament roster.
7. Existing roster validation rules are applied.
8. A roster that fails validation remains editable and cannot be confirmed.
9. A valid roster may be reviewed and confirmed through the existing confirmation flow.
10. Confirmed roster persistence uses the existing atomic persistence behavior.
11. After successful confirmation, the workflow returns to the existing tournament details destination for the same tournament.

No duplicate tournament-setup screen or parallel persistence flow is required solely for v0.11.0.

---

## 5. Tournament Identity and Navigation

The tournament created at the beginning of the workflow must remain the same tournament throughout the setup process.

The authoritative tournament identifier returned by the existing tournament creation/persistence flow must be propagated into subsequent workflow steps.

The tournament must not be rediscovered using:

- tournament name
- date
- organizer
- list ordering
- most recently created tournament
- any other non-unique property

Navigation should carry the minimum stable tournament identity required by the existing architecture.

Persisted tournament and roster state should be reloaded through the existing repository architecture rather than passing mutable domain objects between screens as a second source of truth.

### Post-create navigation

After tournament creation has successfully persisted, the user should continue directly into the existing team/roster setup workflow for that tournament.

The user should not be required to return to the tournament list and manually locate the tournament before continuing initial setup.

Navigation must not occur if tournament creation or persistence fails.

---

## 6. Local Persistence Boundary

The existing Room-backed repository architecture remains the authoritative local persistence boundary for v0.11.0.

Tournament creation must be durably persisted before the workflow advances into roster setup.

v0.11.0 must not introduce:

- an in-memory-only tournament as the authoritative source
- a second tournament persistence mechanism
- duplicate roster persistence
- a workflow-specific database representation

The existing Room schema should remain unchanged unless implementation inspection proves that the approved workflow cannot be implemented without a schema change.

If such a conflict is discovered, implementation must stop and the file/scope boundary must be reviewed before any schema change is made.

Complete cloud synchronization orchestration is not part of this version.

That belongs to:

**v0.11.5 — Cloud Synchronization Flow**

Existing cloud behavior must remain compatible.

---

## 7. Roster Validation and Confirmation

Existing roster validation rules remain authoritative.

v0.11.0 must not redefine or weaken them.

The integrated flow must preserve existing constraints including:

- exactly 12 tournament team slots
- existing team-slot identity rules
- existing team-name validation
- existing player-name validation
- existing player-count rules
- 4–6 players per team where required by the current roster contract
- existing duplicate prevention
- existing roster review behavior
- existing confirmed-roster persistence semantics

### Confirmation gate

Roster validation is the gate for confirmed roster persistence.

An invalid or incomplete roster must not be confirmed.

Validation failure must:

- keep the current setup recoverable
- keep the user able to correct the roster
- surface the existing validation failure
- prevent confirmed roster replacement
- avoid partial persistence of a new confirmed roster

v0.11.0 must reuse the existing roster validation logic rather than duplicating it in navigation or UI code.

---

## 8. Confirmed Roster Persistence

Confirmed roster replacement must continue to use the existing atomic persistence boundary.

Presentation/navigation code must not independently:

1. delete the existing confirmed roster,
2. insert new players one by one,
3. and attempt to reconstruct atomic behavior itself.

The repository/data-layer operation that currently owns confirmed roster replacement remains responsible for persistence integrity.

A persistence failure must not leave a partially replaced confirmed roster.

---

## 9. Interruption and Recovery

Tournament creation and roster completion do not form one destructive all-or-nothing UI transaction.

Once tournament creation succeeds locally, the tournament remains persisted even if roster setup is not completed immediately.

The following situations must remain recoverable:

- user navigates back during roster setup
- user leaves the setup workflow
- application is closed
- application process is restarted
- roster confirmation is postponed

An incomplete setup must not cause automatic deletion of the successfully created tournament.

On later re-entry, the application must use existing persisted tournament and roster state.

v0.11.0 must not introduce a separate permanent `TournamentSetupState`, global mutable cache, or duplicate persistence layer merely to recover setup progress.

---

## 10. Successful Completion

Tournament setup is considered complete for this workflow when:

1. the tournament has been successfully persisted,
2. the roster passes existing validation,
3. the roster is successfully confirmed and persisted,
4. the application navigates to tournament details for the same tournament.

The tournament details flow must read authoritative persisted state through the existing repository architecture.

The setup workflow must not remain a long-lived competing source of truth after completion.

---

## 11. Manual Roster Entry and OCR Compatibility

Manual roster entry remains a fully supported tournament-setup path.

Roster OCR must not become mandatory for v0.11.0.

Existing roster screenshot and OCR capabilities must remain compatible, but complete OCR workflow orchestration is outside this version.

The following remain later Phase 11 work:

- **v0.11.2 — Screenshot Processing Flow**
- **v0.11.3 — Team Matching Flow**

v0.11.0 must not pull those workflows forward.

---

## 12. Failure Handling

v0.11.0 must preserve existing typed failures wherever they already exist.

### Tournament creation failure

If tournament creation or persistence fails:

- roster setup must not start
- no unrelated tournament may be opened
- the user remains in a recoverable creation state

### Tournament or roster loading failure

A load failure must not silently create or substitute a different tournament.

The failure must remain visible through the existing error-handling path.

### Roster validation failure

Validation failure must:

- prevent confirmation
- preserve editable data according to existing behavior
- not partially replace persisted confirmed roster data

### Roster persistence failure

Persistence failure must:

- prevent successful setup completion
- keep the workflow recoverable
- preserve prior valid persisted state according to the existing atomic persistence contract

Broad cross-application error-state standardization is deferred to:

**v0.11.7 — Complete Error-State Handling**

---

## 13. Workflow Invariants

The implementation must preserve all of the following:

1. One persisted tournament identity is used throughout the setup flow.
2. Tournament persistence completes before navigation to roster setup.
3. Room remains the authoritative local persistence layer.
4. Incomplete roster setup does not automatically delete the tournament.
5. Existing roster validation remains authoritative.
6. Invalid rosters cannot be confirmed.
7. Confirmed roster replacement remains atomic.
8. Successful confirmation returns to the same tournament.
9. Manual roster setup works without OCR.
10. Existing cloud behavior remains compatible.
11. Navigation does not duplicate domain validation or persistence logic.
12. No second source of truth is introduced for tournament or roster data.
13. Existing tournament and roster behavior must not regress.

---

## 14. Testing Requirements

Implementation must include focused coverage appropriate to the actual files changed.

### Tournament identity and navigation

Verify:

- successful tournament creation continues with the exact created tournament identity
- tournament lookup is not based on non-unique properties
- successful roster confirmation returns to the same tournament details flow

### Tournament persistence

Verify:

- tournament persistence succeeds before post-create navigation
- creation failure prevents roster navigation
- created tournament remains recoverable after interrupted setup
- existing repository state can reload the tournament

### Roster validation

Verify:

- incomplete roster cannot be confirmed
- invalid roster cannot be confirmed
- valid roster can be confirmed
- existing 12-slot rule remains enforced
- existing player-count rules remain enforced
- existing duplicate rules remain enforced

### Confirmed roster persistence

Verify:

- existing atomic replacement behavior is used
- failure cannot leave partially replaced confirmed roster data

### Regression

Preserve relevant existing tests covering:

- tournament creation
- tournament list/details
- team entry
- roster entry
- roster review
- roster validation
- Room persistence
- restart/restoration behavior

Broad Phase 12 end-to-end QA is not required in v0.11.0.

---

## 15. Compatibility Requirements

v0.11.0 must preserve completed behavior from earlier phases, including:

- tournament management
- roster management
- Room persistence
- authentication
- cloud synchronization
- offline support
- finalized-data protection
- screenshot intake
- roster OCR
- match processing
- scoring
- standings
- correction workflows
- export capabilities

Only the minimum integration changes required for tournament setup should be made.

Unrelated refactoring is prohibited.

---

## 16. Explicitly Out of Scope

The following are not part of v0.11.0:

- new tournament database schema
- Room schema redesign
- Supabase schema changes
- RLS changes
- authentication changes
- cloud synchronization policy redesign
- offline queue redesign
- conflict-resolution redesign
- match creation workflow integration
- placement-entry integration
- kill-entry integration
- match scoring integration
- match finalization integration
- standings integration
- screenshot processing orchestration
- OCR extraction orchestration
- OCR parsing orchestration
- team matching orchestration
- confidence/suggestion orchestration
- OCR review workflow integration
- CSV export UI
- Google Sheets export UI
- export backend changes
- broad application-wide error-state standardization
- dependency upgrades
- package restructuring
- unrelated architecture refactoring
- visual redesign
- Phase 12 broad integration testing

---

## 17. Implementation Guidance

Implementation should make the smallest possible changes required to connect the existing workflow.

Before implementation begins:

1. determine the complete implementation file boundary
2. separate existing files to modify from new files to create
3. obtain and review the complete current version of every existing file that will be modified
4. confirm the boundary before editing code
5. decide whether implementation remains suitable for manual editing or requires Codex

Manual implementation is preferred when the final boundary is small and localized.

Codex may be used only if the approved implementation is sufficiently complex or cross-cutting to justify it.

Codex must not redesign this decision contract.

---

## 18. Acceptance Criteria

v0.11.0 is complete when:

- tournament creation persists successfully before workflow continuation
- the exact persisted tournament identity is retained through setup
- successful creation directly reaches the existing roster setup path
- existing manual roster entry remains available
- existing roster validation gates confirmation
- confirmed roster persistence remains atomic
- invalid or failed persistence cannot report successful setup
- interrupted tournament setup remains recoverable
- successful roster confirmation returns to details for the same tournament
- no duplicate domain or persistence logic has been introduced
- no later Phase 11 functionality has been pulled into this version
- relevant automated verification passes
- only approved implementation files are changed

---

## 19. Implementation Readiness

The architectural direction for v0.11.0 is resolved.

The version is ready for implementation planning after this document is:

1. reviewed,
2. committed,
3. pushed,
4. merged through a decision-only pull request,
5. and `main` is synchronized again.

Implementation must not begin before the decision PR is merged.
