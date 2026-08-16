# CR-004 — Finalized Match Result Snapshot Decisions

## Status

**Decisions recorded — implementation not started.**

CR-004 defines how Rank-Forge will preserve and synchronize the complete finalized scoreboard state for each match without changing OCR extraction, screenshot handling, or scoring rules.

Repository baseline for this decision record:

- `main` includes the squash merge of PR #298.
- Baseline commit: `d3961f6f2d8c85e2961bd736dbc69f39ffaa62f1`.
- Current Room schema after that merge: version 14.

No production schema, Room schema, RPC, or Android implementation is changed by this decision record.

---

## 1. Purpose

Today, match finalization persists and synchronizes the authoritative placement and kill values, but it does not freeze the complete scoreboard identity and calculated values that were true when the match was finalized.

CR-004 will make one finalized match result snapshot preserve:

- team slot number;
- team name;
- each roster player name and roster position;
- placement;
- kills;
- placement points;
- kill points;
- total points.

The purpose is to ensure that later tournament roster edits, delayed cloud retries, restoration, or future scoring-rule changes cannot silently change the historical meaning of an already-finalized match.

---

## 2. Current Baseline

The existing finalization path is:

```text
Finalize Match
    ↓
FinalizeMatchUseCase
    ↓
Room transaction
    ↓
SyncFinalizedMatchesUseCase
    ↓
FinalizedMatchCloudSyncMapper
    ↓
finalize_match_snapshot RPC
    ↓
matches + match_results
```

Current local finalization persists:

- `matches.status = FINALIZED`;
- `match_placements`;
- `match_kills`;
- OCR evidence tables when OCR evidence is supplied.

Current tournament identity data already exists separately in Room:

- `team_slots` with `slot_number` and `team_name`;
- `roster_players` with `roster_position` and `display_name`.

Current hosted `match_results` contains:

- `id`;
- `match_id`;
- `team_slot_id`;
- `placement`;
- `kills`;
- `source`;
- `review_status`;
- timestamps and revision.

The current finalized cloud snapshot contains tournament + matches only. It does not contain a persisted match-specific team/roster snapshot.

---

## 3. Core Architecture Decision

A finalized match must own an immutable historical scoreboard snapshot that is separate from the current tournament roster.

```text
CURRENT TOURNAMENT STATE
────────────────────────
team_slots
roster_players

can change later
```

```text
FINALIZED MATCH HISTORY
───────────────────────
finalized_match_result_snapshots
finalized_match_result_player_snapshots

frozen at finalization
```

Cloud sync and retry must consume the persisted finalized snapshot, not re-read the current team names or player names at retry time.

This is the central CR-004 invariant:

> Finalize once → build one deterministic result snapshot → persist it locally in the same finalization transaction → synchronize that same persisted snapshot to Supabase.

---

## 4. Finalized Team Result Contract

Each newly finalized match must contain exactly 12 finalized team-result snapshots, one for each team slot.

Each team-result snapshot contains:

```text
match_id
team_slot_number
team_name_snapshot
placement
kills
placement_points
kill_points
total_points
```

The team slot number is part of historical identity and must remain stable for the lifetime of the finalized snapshot.

### Required invariants

For every newly finalized match:

- exactly 12 team-result snapshots exist;
- team-slot numbers are exactly 1 through 12 with no duplicates;
- placements are exactly 1 through 12 with no duplicates;
- kills are non-negative;
- `team_name_snapshot` is the team name used at finalization;
- `placement_points` is produced by the existing `PositionPointsEngine`;
- `kill_points` is produced by the existing `KillPointsEngine`;
- `total_points` is produced by the existing `MatchTotalEngine`;
- `total_points = placement_points + kill_points`.

No new scoring algorithm is introduced by CR-004.

---

## 5. Finalized Player Snapshot Contract

Each finalized team-result snapshot must preserve every roster player belonging to that team at finalization time.

Each player snapshot contains:

```text
roster_position_snapshot
player_name_snapshot
```

Where a stable cloud player identity is available, cloud storage may additionally retain `player_id` as a reference to the live roster.

### Player rules

- use the existing Room roster position; do not invent a new ordering;
- player names are copied from the roster at finalization time;
- later roster edits must not mutate historical player snapshots;
- deleting or replacing a live roster player must not delete the historical name snapshot;
- the number of player snapshots is derived from the actual roster saved for that team, subject to the existing roster validation rules.

---

## 6. Local Room Decision

CR-004 must not repurpose `team_slots`, `roster_players`, `match_placements`, or `match_kills` as historical snapshot storage.

Add two match-history entities.

### 6.1 `finalized_match_result_snapshots`

Logical columns:

```text
match_id
team_slot_number
team_name_snapshot
placement
kills
placement_points
kill_points
total_points
```

Primary key:

```text
(match_id, team_slot_number)
```

Foreign key:

```text
match_id → matches.id ON DELETE CASCADE
```

### 6.2 `finalized_match_result_player_snapshots`

Logical columns:

```text
match_id
team_slot_number
roster_position_snapshot
player_name_snapshot
```

Primary key:

```text
(match_id, team_slot_number, roster_position_snapshot)
```

Foreign key:

```text
(match_id, team_slot_number)
→ finalized_match_result_snapshots(match_id, team_slot_number)
ON DELETE CASCADE
```

### 6.3 Transaction boundary

For a new match finalization, the following must succeed or fail as one Room transaction:

```text
mark match FINALIZED
+ replace 12 placements
+ replace 12 kills
+ persist 12 finalized team-result snapshots
+ persist finalized player snapshots
+ clear draft values
+ persist OCR evidence when applicable
+ update local revision state
```

If snapshot construction or snapshot persistence fails, the match must not be partially finalized.

### 6.4 Room migration

Because the current merged baseline is Room version 14, the expected implementation migration is additive version 14 → 15.

The exact migration SQL/Room entity definitions must be verified during implementation. No destructive migration is authorized.

---

## 7. Supabase `matches` Decision

No new `matches` columns are required for CR-004.

Existing fields such as `finalized_at`, `finalized_by`, timestamps, and revision remain server-managed as today.

---

## 8. Supabase `match_results` Decision

Extend `match_results` with these five snapshot columns:

```text
team_slot_number_snapshot integer
team_name_snapshot        text
placement_points          integer
kill_points               integer
total_points              integer
```

### Historical compatibility

Existing production finalized rows predate CR-004. Their historical team names and historical calculated-point snapshots cannot be proven reliably from current mutable tournament state.

Therefore the migration must be additive and backward-safe:

- the five new columns are initially nullable at the database-schema level;
- existing rows are not fabricated or destructively backfilled;
- CR-004 finalization RPC validation requires these fields for every newly finalized match;
- newly finalized CR-004 rows must never contain NULL snapshot values.

---

## 9. Supabase `match_result_players` Decision

Add one new table:

```text
match_result_players
```

Logical columns:

```text
id                       uuid primary key
match_result_id          uuid not null
player_id                uuid null
roster_position_snapshot integer not null
player_name_snapshot     text not null
created_at               timestamptz not null
```

Relationships:

```text
match_result_id
→ match_results.id
ON DELETE CASCADE
```

```text
player_id
→ players.id
ON DELETE SET NULL
```

`player_name_snapshot` remains authoritative historical text. `player_id` is only a relationship to the live roster and must not be required for historical preservation.

Player-snapshot IDs must be deterministic from the parent match-result identity plus roster position so retries are idempotent.

Exact constraint/index names are implementation details, but the table must prevent duplicate roster positions within one `match_result_id`.

---

## 10. Cloud Snapshot and Mapper Decision

The current `FinalizedMatchCloudSyncSnapshot` is insufficient because it contains only tournament + matches.

CR-004 must extend the finalized cloud-sync boundary so the mapper receives persisted finalized result snapshots, including player snapshots.

The mapper must not reconstruct historical team/player names by reading mutable tournament state during a retry.

Each outgoing finalized result must carry:

```text
id
match_id
team_slot_id
team_slot_number_snapshot
team_name_snapshot
placement
kills
placement_points
kill_points
total_points
source
review_status
players[]
```

Each nested player payload must carry at least:

```text
id
player_id (nullable when appropriate)
roster_position_snapshot
player_name_snapshot
```

The existing deterministic team-slot and player identity strategy should be reused where applicable.

---

## 11. Supabase Finalization RPC Decision

Keep finalization protected by one transactional RPC. Do not introduce separate client writes that can leave a finalized match with incomplete result/player snapshots.

The existing RPC name remains preferred:

```text
finalize_match_snapshot(
    p_tournament_id,
    p_match,
    p_match_results,
    p_expected_revision
)
```

Implementation may extend the JSON shape inside `p_match_results` to include snapshot fields and nested player snapshots rather than adding a second non-transactional endpoint.

### RPC validation for new CR-004 finalizations

The RPC must verify at minimum:

- authenticated user;
- tournament ownership;
- expected revision matches;
- match belongs to tournament;
- match is currently draft;
- incoming match requests finalized status;
- exactly 12 result rows;
- exactly 12 distinct tournament team slots;
- team-slot snapshots are exactly 1 through 12;
- placements are exactly 1 through 12;
- kills are non-negative;
- snapshot team names are valid nonblank values under the existing roster/team contract;
- `placement_points` equals the approved placement scoring rule;
- `kill_points` equals the approved kill scoring rule;
- `total_points = placement_points + kill_points`;
- player snapshot identities/positions are unique within each result;
- referenced live players, when supplied, belong to the corresponding tournament team slot.

### RPC transaction

One transaction must perform:

```text
validate all payload data
↓
write 12 match_results
↓
write all match_result_players
↓
mark match finalized
↓
set finalized_at/finalized_by
↓
increment match/tournament revisions
↓
commit
```

Any validation or insert failure must roll back the complete finalization write.

---

## 12. Cloud Retry Rule

Cloud failure must not cause Rank-Forge to rebuild the historical result from the current roster.

Required retry behavior:

```text
Finalize locally
↓
persist immutable snapshot
↓
cloud attempt fails
↓
team/player names may change later
↓
retry
↓
upload ORIGINAL persisted finalized snapshot
```

This requirement is mandatory for deterministic offline/retry behavior.

---

## 13. Protected Correction Compatibility

Rank-Forge already supports explicit protected correction of finalized matches.

CR-004 therefore defines two classes of finalized data.

### Stable historical identity

Protected corrections must not change:

```text
team_slot_number_snapshot
team_name_snapshot
player snapshots
```

### Correctable score data

An authorized protected correction may change:

```text
placement
kills
placement_points
kill_points
total_points
```

The three point fields must be recomputed using the existing scoring engines/rules whenever placement or kills change.

The local correction transaction and the Supabase protected-correction RPC must update raw values + calculated points atomically so a corrected finalized match can never contain internally inconsistent totals.

Existing correction audit behavior must remain preserved.

---

## 14. Security and RLS Decision

The new cloud table is user data and must follow the existing tournament ownership boundary.

Requirements:

- RLS enabled on `match_result_players`;
- ownership derived through `match_result_players → match_results → matches → tournaments.owner_id`;
- no authentication-only policy;
- direct client writes must not bypass finalized-match protection;
- the protected finalization/correction RPCs must retain explicit `auth.uid()` and ownership checks;
- existing RLS on matches/results must not be weakened;
- service-role credentials remain absent from Android;
- database/RLS regression tests are required for the new table and RPC behavior.

---

## 15. Historical Data Decision

CR-004 does not claim to reconstruct historical truth for already-finalized matches.

Do not backfill historical snapshot names/players/points from today's mutable roster and present them as if they were captured at the original finalization time.

Existing rows may therefore remain legacy rows with NULL CR-004 snapshot columns and no `match_result_players` children.

Future restoration/export code must be able to distinguish legacy rows from complete CR-004 snapshots.

---

## 16. Explicitly Out of Scope

CR-004 does not include:

- OCR extraction/parser/preprocessing changes;
- Lobby OCR cache changes;
- Result OCR cache changes;
- OCR evidence cloud synchronization;
- screenshot/crop/storage changes;
- scoring-rule changes;
- tournament standings redesign;
- Google Sheets or CSV format changes;
- correction of the existing `source = "manual"` provenance behavior;
- full cloud restoration orchestration;
- historical result backfill;
- unrelated navigation/UI redesign.

These must remain separate follow-up changes if needed.

---

## 17. Implementation Sequence

After this decision record is approved and merged, CR-004 implementation should proceed in small slices:

1. synchronize clean `main`;
2. create the implementation branch;
3. add additive Room 14 → 15 snapshot tables and migration tests;
4. make local finalization construct and persist the deterministic snapshot in the existing transaction;
5. extend finalized cloud-sync snapshot/payload mapping to consume persisted snapshots;
6. add the additive Supabase migration for the five `match_results` columns and `match_result_players`;
7. extend `finalize_match_snapshot` transactionally;
8. extend protected correction so points stay consistent while identity snapshots remain unchanged;
9. add restoration compatibility only as required to keep existing restore behavior safe; full restoration enhancement remains follow-up;
10. run focused + full required verification;
11. verify one real finalized match against hosted Supabase row-for-row;
12. perform diff/scope audit before PR/merge.

Do not combine OCR-evidence cloud synchronization or unrelated restoration work into the CR-004 implementation PR.

---

## 18. Acceptance Criteria

CR-004 implementation is complete only when all applicable criteria pass.

### Local finalization

For one newly finalized match:

- exactly 12 finalized team-result snapshots exist in Room;
- every result has the team name frozen at finalization;
- every result has placement, kills, placement points, kill points, and total points;
- every team's actual roster players are snapshotted with roster position + display name;
- all snapshot rows are written in the same finalization transaction;
- failure to persist the snapshot prevents partial finalization.

### Scoring

For every result:

```text
placement_points = PositionPointsEngine(placement)
kill_points      = KillPointsEngine(kills)
total_points     = placement_points + kill_points
```

### Cloud

For one newly finalized hosted match:

- exactly one finalized `matches` row exists;
- exactly 12 `match_results` rows exist;
- all five CR-004 snapshot columns are non-null for those rows;
- the exact player snapshot rows exist in `match_result_players`;
- Supabase values match the local finalized snapshot row-for-row;
- retry does not duplicate result or player rows.

### Historical stability

After finalization, changing a tournament team name or player name must not change the stored finalized snapshot locally or in Supabase.

A cloud retry after such a roster edit must still upload the original finalized snapshot values.

### Protected correction

After an authorized correction:

- placement/kills are updated;
- placement/kill/total points are recomputed atomically;
- team name and player snapshots are unchanged;
- existing correction audit behavior remains intact.

### Backward compatibility

- existing production finalized rows remain valid;
- migration does not fabricate historical snapshot values;
- existing tournaments/matches/results are preserved;
- no destructive Room or Supabase migration is used.

---

## 19. Verification Plan

Required implementation verification includes:

### Android / domain

- finalization snapshot construction tests;
- 12-slot completeness and uniqueness tests;
- team/player snapshot immutability tests;
- scoring consistency tests;
- delayed-retry uses persisted snapshot test;
- protected correction recomputes points test;
- existing finalization tests remain green.

### Room

- migration 14 → 15;
- existing data preservation;
- DAO/entity FK and cascade behavior;
- transaction rollback when snapshot persistence fails;
- finalized snapshot survives roster edits.

### Supabase

- schema tests for new columns/table/constraints/indexes;
- RLS owner access and cross-owner denial;
- finalization RPC accepts one complete valid snapshot;
- rejects missing/invalid point calculations;
- rejects incomplete/duplicate team results;
- rejects invalid player relationships;
- writes result + player snapshot atomically;
- protected correction recomputes/validates score fields while preserving identity snapshots;
- existing finalized protection remains intact.

### Device / hosted acceptance

Finalize one real test match and compare:

```text
App/Room snapshot
        ↕ exact comparison
Hosted Supabase match_results
Hosted Supabase match_result_players
```

Verify all 12 teams and all player rows manually/query-wise before CR-004 closure.

### General

- `testDebugUnitTest` / relevant focused unit tests;
- `assembleDebug`;
- `assembleDebugAndroidTest`;
- relevant connected tests on physical device;
- Supabase pgTAP/database tests;
- `git diff --check`;
- final changed-file/scope audit.

---

## 20. Rollback Plan

Rollback must be corrective and non-destructive.

If Android implementation fails before merge:

- revert the implementation branch/PR; do not modify production data.

If an unapplied Supabase migration is defective:

- fix the migration before deployment.

If an already-applied Supabase migration requires correction:

- create a new corrective migration;
- do not edit or delete the applied migration;
- preserve existing columns/tables/data unless explicit destructive rollback is separately approved.

If a Room migration issue is found:

- fix forward with the next safe migration/version; never use destructive fallback for production user data.

---

## 21. Approval Boundary

This document authorizes only the CR-004 finalized-result snapshot architecture described above.

Implementation must remain limited to the data needed for:

```text
team slot
team name
player names/positions
placement
kills
placement points
kill points
total points
```

Any OCR-evidence cloud sync, source-provenance redesign, export redesign, broad restoration work, or unrelated refactor requires a separate explicit decision.