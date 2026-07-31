# Rank-Forge v0.9.9 - Original and Corrected Data Preservation Decisions

## 1. Title and Status

**Phase:** 9 - Team Matching and Manual Correction  
**Version:** v0.9.9 - Original and Corrected Data Preservation  
**Status:** Approved documentation decision gate; no implementation is authorized by this document alone.

This document defines the v0.9.9 persistence and evidence-preservation contract for OCR-derived match processing. It creates no code, tests, migrations, Room entities, Supabase changes, UI, navigation, roadmap changes, scoring changes, or finalization-rule changes.

## 2. Decision Summary

v0.9.9 preserves original OCR-derived match evidence beside the operator-corrected and finalized match values for exactly one OCR-processed match result.

The approved decisions are:

- original OCR evidence and original parsed OCR values must remain distinguishable from manually corrected values;
- finalized match data remains the canonical source for scoring and standings;
- preserved OCR evidence is immutable after successful preservation;
- v0.9.9 requires dedicated local Room preservation entities because the current schema does not safely represent immutable OCR-row evidence and corrected/finalized snapshots;
- v0.9.8 OCR-correction finalization and v0.9.9 preservation must commit atomically through a repository/database transaction where Room is the local persistence boundary;
- current Room database version `7` requires a future non-destructive migration to version `8`;
- existing finalized-match sync remains unchanged; cloud OCR-evidence preservation is deferred until a separate Supabase schema and sync decision is approved;
- existing protected later correction history continues to handle post-finalization result corrections and must not rewrite OCR evidence.

## 3. Repository Context

The current repository evidence establishes the following implementation state:

- `RankForgeDatabase` uses Room database version `7`.
- Current Room entities cover tournaments, team slots, roster players, matches, match placements, match kills, match draft values, match correction history, sync queue/revision records, match screenshot metadata, and roster screenshot metadata.
- There is no current Room entity or DAO for immutable match OCR evidence, OCR row evidence, or an OCR finalization/correction snapshot.
- `Match` stores finalized result values and a `correctionHistory` list of `MatchCorrectionRecord` values.
- `MatchCorrectionRecord` stores previous and corrected finalized placements/kills for protected match correction history; it does not store raw OCR text, parsed OCR row values, suggestion/confidence/safety evidence, row indexes, screenshot association, manual-change flags, or initial OCR-correction provenance.
- `MatchOcrReviewUiState` preserves original parsed placement, original parsed kill, and original suggested team slot for presentation state, but that state is not domain storage and is documented as non-persistent display input.
- `MatchOcrReviewCorrectionDraft` is an in-memory correction draft model and is not canonical persistence.
- `FinalizeOcrCorrectionMatchUseCase` maps valid correction rows into the existing `FinalizeMatchUseCase` and existing finalized match model.
- Existing finalized match cloud sync maps finalized `Match` values into current match/result payloads; it does not carry OCR evidence.

Canonical documents require raw OCR, parsed OCR, corrected values, confirmed/finalized values, and correction history to remain distinguishable. Phase 5 requires normalized Room persistence, transactions, cascade-safe local integrity, and finalized-only standings. Phase 6 requires idempotency, revision-safe conflict handling, protected finalization, and protected correction history. v0.9.6 through v0.9.8 intentionally deferred durable original/corrected OCR evidence preservation to v0.9.9.

## 4. Scope

v0.9.9 is in scope for:

- durable local preservation of OCR-derived match evidence;
- durable local preservation of the corrected values selected during OCR review/correction;
- linking the preserved evidence to the finalized match result;
- storing correction provenance and preservation/finalization timestamps;
- providing read boundaries for later evidence display;
- tests for preservation, immutability, migration, transactionality, idempotency, and compatibility.

v0.9.9 must not:

- rerun OCR;
- change OCR extraction or parsing;
- recompute matching suggestions;
- recompute confidence or assignment safety;
- change placement or kill scoring;
- change finalization rules;
- create a correction workflow that bypasses existing finalized-data protection;
- implement roster OCR review or roster correction;
- implement export, sharing, or public evidence display.

## 5. Terminology and Canonical Data Boundaries

The canonical data boundaries are:

1. **Original evidence** - immutable OCR-derived evidence captured from the match screenshot workflow. This includes source screenshot/image association, raw OCR text or safe structured OCR evidence, original parsed values, suggested team slot, and stable summaries of matching/confidence/safety evidence.
2. **Correction record** - the user-selected corrected placement, kills, and assigned team slot values produced by the v0.9.7 correction draft and accepted by the v0.9.8 finalization path.
3. **Finalized match data** - the existing `Match` placements/kills written through the approved finalization model. This remains the source of truth for scoring, standings, and finalized sync.
4. **Protected later correction history** - existing protected correction/audit behavior for changes made after a match is already finalized.

Original OCR evidence must never become the source of truth for standings. Corrected preservation records must never replace the existing finalized match model. Protected later corrections may change current finalized match values through the approved correction workflow, but must not rewrite the original OCR evidence preserved at OCR-assisted finalization time.

## 6. Preservation Scope

Preservation applies to exactly one OCR-processed match result.

For each of the 12 scoreboard rows, v0.9.9 must preserve, where available:

- OCR row index;
- OCR source screenshot or image association;
- raw OCR text or safe structured OCR evidence;
- original parsed placement;
- original parsed kills;
- original proposed or suggested team slot;
- original confidence and assignment-safety evidence, or a stable summary sufficient for later audit display;
- corrected placement;
- corrected kills;
- corrected assigned team slot;
- whether placement, kills, and team slot were manually changed from the original OCR-derived values;
- finalization timestamp;
- correction provenance.

Player-name OCR evidence remains evidence only. It must not mutate roster player identities, create roster corrections, or become an alternate roster source of truth.

## 7. Persistence Design

The current Room schema cannot safely represent v0.9.9 preservation by reusing existing tables.

The decision is to add dedicated local Room tables in the future implementation:

- a match-level OCR preservation table;
- a row-level OCR preservation table;
- an immutable correction/finalization snapshot table associated with the OCR-assisted finalization.

The existing `match_corrections`/`MatchCorrectionRecord` model must not be reused for this purpose because its semantics are post-finalization result correction history. It lacks the required OCR evidence fields and would blur initial OCR preservation with later protected corrections.

The existing screenshot metadata table must not be reused as the row-evidence table because it represents screenshot storage/status metadata, not row-level OCR, parsed, suggestion, correction, and finalization evidence.

## 8. Entity and Domain Model Boundary

The approved repository-aligned logical entity names for future implementation are:

```text
MatchOcrEvidenceEntity
MatchOcrRowEvidenceEntity
MatchOcrCorrectionSnapshotEntity
```

The approved logical domain model names are:

```text
PreservedMatchOcrEvidence
PreservedMatchOcrRowEvidence
PreservedMatchOcrCorrectionSnapshot
```

Entity boundaries:

- `MatchOcrEvidenceEntity` is the match-level preservation record.
  - Primary key: stable preservation id, or `match_id` if implementation proves one preserved OCR snapshot per match is sufficient.
  - Foreign keys: tournament id and match id linked to existing tournament/match hierarchy.
  - Required fields: tournament id, match id, source screenshot metadata id or match screenshot association where available, preservation status, created/preserved timestamp, finalization timestamp, and provenance fields.
  - Unique constraint: one complete OCR preservation record per match unless a later approved version adds multi-run preservation.
- `MatchOcrRowEvidenceEntity` is the row-level preservation record.
  - Primary key: composite identity or stable row id.
  - Foreign keys: parent preservation record and match id.
  - Required fields: row index, safe raw/structured OCR evidence, original parsed placement, original parsed kills, original suggested team slot, original confidence/safety summary, corrected placement, corrected kills, corrected team slot, changed-field flags, and row provenance.
  - Unique constraint: `(tournament_id, match_id, row_index)` or equivalent parent-preservation plus row-index uniqueness.
- `MatchOcrCorrectionSnapshotEntity` is the immutable finalization/correction snapshot associated with OCR-assisted finalization.
  - Primary key: stable snapshot id.
  - Foreign keys: parent preservation record and match id.
  - Required fields: match id, finalization timestamp, correction provenance, warning-confirmation/provenance metadata where available, and snapshot status.

Deletion behavior must follow existing local hierarchy rules. Deleting the parent tournament or match may cascade to preserved OCR evidence according to existing deletion policy. Normal match correction, standings recalculation, sync conflict handling, or protected later correction must not delete or overwrite preserved evidence.

## 9. Row Identity and Constraints

Each preserved row is identified by:

- tournament id;
- match id;
- OCR row index, zero-based `0..11`.

Required constraints:

- at most one preserved original evidence row per match and row index;
- exactly 12 rows are required for a complete finalized OCR snapshot;
- corrected team slots must be valid `1..12`;
- corrected placements must be valid `1..12`;
- corrected kills must be non-negative;
- duplicate row indexes are rejected;
- original evidence cannot be silently overwritten;
- duplicate corrected team slots and duplicate corrected placements remain invalid through existing finalization validation;
- the row-order contract remains deterministic and follows the fixed scoreboard row order.

## 10. Immutability Rules

After successful preservation, original evidence is immutable.

Allowed later operations:

- read original evidence;
- read original parsed values;
- read corrected/finalized snapshot values;
- append or record later protected corrections through the existing protected correction workflow;
- display current finalized values beside original/corrected snapshot values.

Disallowed operations:

- overwrite raw OCR evidence after finalization;
- replace original parsed values with corrected values;
- erase original evidence through normal match correction;
- mutate original evidence when standings change;
- mutate original evidence during cloud sync, retry, restoration, or conflict resolution;
- edit preserved player-name evidence into roster identity changes.

If a future implementation detects an existing preservation row with different original evidence for the same `(tournament_id, match_id, row_index)`, it must return a deterministic conflict or preservation failure rather than overwrite it.

## 11. Finalization Transaction Boundary

v0.9.8 finalization and v0.9.9 preservation must be atomic for OCR-assisted finalization.

Approved transaction behavior:

- finalized match placements/kills and the complete preservation snapshot are written in one repository/database transaction;
- if preservation fails, finalization must not commit;
- if finalization fails, no preservation snapshot may be treated as complete;
- repeated finalization/preservation attempts with the same already-finalized and already-preserved snapshot must be idempotently accepted or deterministically rejected as already preserved;
- duplicate preservation attempts must not create duplicate match-level, row-level, or snapshot rows;
- no ViewModel may compose separate direct database writes that can leave finalization and preservation inconsistent.

The current repository architecture already uses `RoomDatabase.withTransaction` and write serialization for multi-record updates. Future v0.9.9 implementation must extend that repository boundary or add a repository method that coordinates finalization and preservation inside one local transaction. If implementation evidence shows the existing `FinalizeMatchUseCase` cannot be safely wrapped without partial writes, coding must stop and the transaction design must be revised before implementation proceeds.

## 12. Migration Boundary

A Room migration is required.

The current Room database version is `7`; the next v0.9.9 implementation migration must move to version `8`.

The migration must:

- create the dedicated match-level OCR evidence table;
- create the dedicated row-level OCR evidence table;
- create the dedicated correction/finalization snapshot table if implemented as a separate table;
- add foreign keys to the existing tournament/match hierarchy;
- add uniqueness for one preservation record per match where approved;
- add uniqueness for one row per `(tournament_id, match_id, row_index)` or equivalent parent-preservation row identity;
- add indexes needed for match-level reads, tournament cleanup, and row ordering;
- preserve all existing user data;
- avoid destructive migration;
- update exported Room schema files;
- include migration tests from schema version `7` to version `8`;
- verify that older finalized matches without OCR preservation remain readable.

No migration is implemented by this documentation task.

## 13. Repository and Use-Case Boundary

Future implementation must use repository and use-case boundaries.

Responsibilities:

- a preservation use case validates the complete snapshot before persistence;
- the repository performs atomic Room persistence;
- the OCR finalization use case coordinates the approved finalization-plus-preservation boundary;
- ViewModels expose immutable UI state and call use cases only;
- ViewModels and composables must not write Room entities directly;
- scoring and standings continue reading existing finalized `Match` placements/kills;
- evidence display reads through a dedicated query/repository boundary.

Suggested logical use cases:

```text
PreserveFinalizedOcrMatchEvidenceUseCase
GetPreservedMatchOcrEvidenceUseCase
```

Names may be adjusted to match repository conventions, but the responsibilities must remain separated from OCR execution, matching recomputation, scoring, and protected later correction.

## 14. Cloud and Sync Boundary

v0.9.9 requires local Room preservation. It does not automatically add cloud schema, cloud storage, Supabase RPC, sync queue, or sync payload changes.

Approved cloud/sync boundary:

- existing finalized match sync continues unchanged;
- preserved OCR evidence must not be silently inserted into current finalized match payloads;
- evidence must not be sent through incompatible draft/finalized match result payload semantics;
- cloud preservation is deferred until a separate Supabase schema, RLS, storage, revision, and sync decision is approved;
- local-only preserved evidence must remain clearly documented as local preservation, not backend-authoritative evidence;
- future cloud preservation must respect Phase 6 revision-safe writes, owner authorization, RLS, idempotency, and finalized-data protection.

## 15. Protected Correction Interaction

v0.9.9 preservation and existing protected corrections serve different purposes.

Required interaction rules:

- original OCR evidence remains unchanged after initial preservation;
- the initial corrected/finalized OCR-assisted snapshot remains preserved;
- later protected corrections use the existing correction workflow;
- later correction audit records may reference the match but must not rewrite OCR evidence;
- standings derive from the current protected finalized match data, not from preserved OCR evidence;
- historical OCR evidence remains informational and auditable;
- a later correction may make current finalized values differ from the initially corrected OCR snapshot, and the UI/read model must be able to distinguish those values.

The existing protected correction history is not a substitute for original OCR evidence preservation.

## 16. Read and UI Boundary

Allowed v0.9.9 read/UI behavior:

- display original OCR-derived values;
- display manually corrected values accepted at OCR finalization;
- display current finalized values;
- display changed-field indicators;
- display preservation and finalization timestamps;
- distinguish original, corrected, and current values clearly;
- expose deterministic empty/error states when evidence is unavailable.

Not allowed:

- editing original evidence;
- direct database repair controls;
- roster correction;
- scoring overrides;
- cloud sync controls;
- export or sharing controls unless separately approved;
- hiding the fact that current finalized values may differ from initially preserved corrected values after protected later correction.

Connected-device tests are required only if v0.9.9 adds or changes visible UI.

## 17. Privacy and Data Minimization

v0.9.9 must preserve only evidence required for auditability and correction traceability.

Rules:

- prefer screenshot metadata/image association over duplicating image binaries;
- preserve safe structured OCR evidence when it is sufficient;
- avoid duplicating full raw OCR payloads unless required for approved audit behavior;
- do not expose unnecessary raw OCR text in general UI;
- do not log raw OCR evidence, private screenshot data, secrets, tokens, account credentials, or unrelated metadata;
- follow app-private local storage rules;
- deletion behavior must follow tournament/match deletion policy and finalized-data protections;
- future cloud preservation requires a separate privacy, storage, RLS, and retention decision.

## 18. Failure Handling

Future implementation must return deterministic failures for:

- missing OCR evidence;
- fewer or more than 12 rows;
- malformed row evidence;
- duplicate row index;
- invalid corrected placement;
- invalid corrected kills;
- invalid corrected team slot;
- duplicate corrected placement;
- duplicate corrected team slot;
- missing tournament;
- missing match;
- match not draft when OCR-assisted finalization is requested;
- match already finalized without compatible preserved snapshot;
- finalization/preservation transaction failure;
- duplicate retry with conflicting evidence;
- migration failure;
- read failure.

Partial preservation is never success. A complete snapshot has exactly 12 valid row records and a valid match-level preservation record linked to the finalized match.

## 19. Testing Strategy

Required JVM/database tests:

- valid 12-row snapshot preserves original and corrected values;
- original and corrected values remain distinct;
- original evidence cannot be overwritten;
- duplicate row index is rejected;
- invalid row count is rejected;
- invalid placement is rejected;
- invalid kills are rejected;
- invalid team slot is rejected;
- duplicate corrected placement is rejected;
- duplicate corrected team slot is rejected;
- transaction failure rolls back finalization and preservation;
- repeated request is idempotent or deterministically rejected without duplicates;
- finalized match remains the scoring source;
- preserved evidence does not alter standings;
- protected later correction does not overwrite original evidence;
- deletion/foreign-key behavior is deterministic;
- migration from schema version `7` to `8` preserves existing data;
- exported schema verification covers the new Room schema.

Required ViewModel/Compose tests if UI is implemented:

- original, corrected, and finalized/current values are visibly distinguished;
- changed-field indicators display correctly;
- no edit controls exist for original evidence;
- no save, score override, sync, export, or sharing controls are added;
- empty/error states are deterministic.

Connected-device tests are required only if v0.9.9 changes visible UI.

## 20. Compatibility Requirements

v0.9.9 must not modify or break:

- Phase 8 OCR extraction/parsing behavior;
- v0.9.0 text normalization;
- v0.9.1 similarity matching;
- v0.9.2 candidate scoring;
- v0.9.3 top-three suggestions;
- v0.9.4 confidence thresholds;
- v0.9.5 assignment safety;
- v0.9.6 OCR review interface;
- v0.9.7 correction draft behavior;
- v0.9.8 safe match finalization;
- existing manual match workflow;
- existing scoring and standings;
- existing protected correction workflow;
- existing finalized match sync;
- authentication and RLS;
- roster management;
- finalized-data protection.

## 21. Security and Data Integrity

Security and integrity requirements:

- preserve private OCR evidence only within approved local app storage boundaries;
- do not expose raw OCR evidence through logs or broad UI surfaces;
- protect immutable evidence from ordinary update paths;
- use database constraints plus repository validation for row count, row identity, and valid corrected values;
- keep finalization and preservation atomic;
- keep retries idempotent;
- preserve finalized-data protection;
- ensure cloud conflict resolution cannot mutate local original OCR evidence;
- keep evidence reads scoped to the owning tournament/match path;
- avoid adding secrets, credentials, or sensitive unrelated metadata to preservation records.

## 22. Out of Scope

The following are explicitly out of scope:

- OCR execution or retry;
- OCR parsing changes;
- player-name correction;
- roster correction;
- roster OCR review/correction;
- matching recomputation;
- confidence recomputation;
- assignment-safety recomputation;
- new scoring rules;
- direct standings mutation;
- export or sharing;
- public evidence display;
- Supabase schema changes;
- cloud sync changes;
- new cloud RPCs;
- new storage buckets or storage paths;
- non-numbered roadmap item `v0.9.x - Roster OCR Review and Correction`.

## 23. Acceptance Criteria

A later v0.9.9 implementation is accepted only when:

- original OCR evidence and corrected values are stored distinctly;
- finalized match data remains canonical for scoring;
- original evidence is immutable;
- exactly 12 rows are preserved for a complete snapshot;
- preservation is atomic with finalization;
- retries are idempotent or deterministically rejected without duplicate rows;
- no destructive migration is used;
- existing user data is preserved;
- protected later corrections do not overwrite original OCR evidence;
- standings behavior is unchanged;
- comprehensive unit/database/migration tests pass;
- connected-device tests pass if UI changes;
- existing behavior remains unchanged.

## 24. Deferred Decisions

The following remain deferred:

- exact physical column names and JSON/structured evidence shape;
- whether the match-level preservation primary key is a dedicated id or `match_id`;
- exact raw OCR retention format;
- exact confidence/safety summary serialization;
- exact provenance fields beyond timestamp and source/action identity;
- whether cloud preservation is needed in Phase 10 or later;
- Supabase schema, RLS, RPC, storage, sync, and retention design for OCR evidence;
- public/export display of evidence;
- roster OCR review and correction;
- long-term retention/deletion policy for preserved OCR evidence.

## 25. Implementation Handoff

The future v0.9.9 implementation task may add only the preservation behavior described here.

Implementation should start from the v0.9.8 OCR-correction finalization boundary, add a validated preservation snapshot model, add dedicated Room entities/DAOs, migrate Room from version `7` to `8`, write finalized match data and preservation evidence in one repository transaction, and add tests for immutability, distinct original/corrected/finalized data, rollback, idempotency, migration, and standings compatibility.

Implementation must not add OCR execution, OCR parsing changes, matching recomputation, scoring changes, protected correction redesign, Supabase changes, sync payload changes, export/sharing, roster OCR review/correction, or visible UI changes unless separately approved.
