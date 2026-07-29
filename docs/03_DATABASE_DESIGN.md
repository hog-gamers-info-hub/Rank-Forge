# Rank-Forge — Database Design

## 1. Document Purpose

This document defines the approved MVP database design for Rank-Forge at the logical design level.

It documents the approved local and backend data models, entity relationships, integrity constraints, authorization boundaries, migration principles, synchronization requirements, and finalized-data protections without creating migrations, Room entities, SQL, Supabase configuration, or application code.

This document follows the approved authority hierarchy. Product requirements define the required data and behavior, system architecture defines local and remote boundaries, the roadmap controls implementation order, and verified repository state defines what is and is not currently implemented.

## 2. Database Design Principles

The approved database design principles are:

* Supabase PostgreSQL is the permanent backend source of truth.
* Room is a local persistence, draft, cache, recovery, and synchronization-support store.
* Google Sheets and CSV are outputs, not databases of record.
* Stable record identity is required across local and remote stores.
* Referential integrity must be preserved.
* Duplicate records and repeated synchronization must be prevented.
* Draft and finalized data must remain distinct.
* Raw OCR data and corrected confirmed data must remain distinguishable.
* Derived scoring data must remain deterministic and reproducible.
* Sensitive and privileged data must be minimized.
* Every database change must be version-controlled and testable.

This document does not select additional implementation technologies beyond the approved Room and Supabase boundaries.

## 3. Data Authority and Storage Boundaries

Approved data-authority boundaries are:

* Supabase owns permanent authenticated tournament data.
* Room may temporarily contain local copies, drafts, OCR review state, and pending operations.
* Room data must not silently override newer backend data.
* Finalized backend records require stronger protection than drafts.
* Screenshot binary files belong in controlled storage, while relational databases contain metadata and references.
* Google Sheets must not become an alternate source of tournament truth.
* Local data that has not synchronized must remain recoverable and visibly pending.

The current tracked repository contains Supabase scaffolding but no verified production schema, migrations, or Room implementation.

## 4. Core Domain Entity Model

The approved logical entity groups are:

* Authenticated user identity
* Tournament
* Tournament team slot
* Player
* Match
* Match result
* Screenshot
* OCR processing run
* OCR observation or parsed row
* Team-match candidate or matching assessment
* Confirmed correction or result revision
* Synchronization operation
* Export operation
* Derived tournament standing

These are logical entities required by approved product scope. They do not imply that physical tables, Room entities, or code structures already exist in the current repository.

This document does not add spectator entities, player-login accounts, team-owner accounts, payment or subscription entities, public social features, or unapproved multi-tenant organization structures. The separately staged roster screenshot OCR extension must use approved future phase-specific contracts rather than duplicate existing roster persistence.

## 5. Tournament and Roster Data

Tournament data must support:

* Stable identifier
* Owning authenticated user
* Tournament name
* Tournament date
* Organizer details
* Tournament status
* Creation and update timestamps

Tournament team slot data must support:

* Stable identifier
* Parent tournament
* Fixed slot number from 1 through 12
* Team name
* Validation or completion state where needed
* Creation and update timestamps

Player data must support:

* Stable identifier
* Parent team slot
* Player display name
* Normalized value required for approved duplicate detection and matching
* Creation and update timestamps

Required constraints:

* Exactly 12 fixed slot numbers are available per tournament.
* Slot numbers are unique within a tournament.
* Team names are unique within a tournament.
* Each completed team contains four to six players.
* Invalid player counts block roster completion.
* Duplicate players must be detected according to approved validation rules.
* A complete roster review is required before match processing.

The exact cross-team duplicate-player rule remains a deferred decision requiring explicit confirmation. The staged roster screenshot OCR extension must not invent that rule, and it must keep manual roster entry available.

## 6. Match and Result Data

Match data must support:

* Stable identifier
* Parent tournament
* Match number
* Match date
* Map
* Draft or finalized status
* Finalization timestamp and actor where required
* Creation and update timestamps

Match result data must support one result per tournament team for a match:

* Stable identifier
* Parent match
* Referenced tournament team
* Placement from 1 through 12
* Kill value
* Source classification where required, such as manual or OCR-assisted
* Review or confirmation state
* Creation and update timestamps

Required constraints:

* Maximum 10 matches per tournament.
* Match number is unique within a tournament.
* Every finalized match contains exactly 12 team-result rows.
* Each tournament team appears at most once per match.
* Each placement from 1 through 12 appears at most once per match.
* Kill values cannot be negative.
* Invalid, missing, duplicate, or unresolved result data blocks finalization.
* Draft and finalized records remain distinguishable.

This document does not define UI fields, API payloads, Kotlin classes, or SQL.

## 7. Scoring and Standings Data

Approved scoring and standings data rules are:

* Placement and kill values are authoritative scoring inputs.
* Position points, kill points, match total, and tournament totals are deterministic derived values.
* Derived values must not become independently editable sources of truth.
* A finalized scoring snapshot may be persisted for audit or export consistency only when it remains reproducible from canonical inputs.
* Tournament standings derive only from finalized match results.
* Tie-break information must remain reproducible from finalized match history.
* Cached or materialized standings must be safely rebuildable.

This document does not redefine scoring values. Detailed scoring behavior remains governed by [docs/01_PRODUCT_REQUIREMENTS.md](docs/01_PRODUCT_REQUIREMENTS.md) and the future canonical scoring document [docs/05_SCORING_AND_PROCESSING_RULES.md](docs/05_SCORING_AND_PROCESSING_RULES.md).

## 8. Screenshot and OCR Data

Screenshot metadata must support:

* Stable identifier
* Parent tournament and match
* Original storage reference
* Processed-image reference where approved
* Content hash for duplicate detection
* File type
* Dimensions
* Orientation
* Processing status
* Creation and processing timestamps

OCR processing-run data must support:

* Stable identifier
* Parent screenshot
* Processing status
* OCR engine or model-version metadata where available
* Start and completion timestamps
* Failure or warning information
* Raw OCR output or a controlled reference to it

OCR observation or parsed-row data must support:

* Parent OCR run
* Detected placement
* Raw player-name values
* Parsed player-name values
* Detected kill values
* Uncertainty, warning, or confidence-related metadata
* Association with a scoreboard row or result candidate

Required boundaries:

* Original screenshot metadata and raw OCR data must be preserved where permitted.
* Raw OCR values must not be overwritten by corrected values.
* Scoreboard OCR applies only to genuine supported Free Fire MAX scoreboard screenshots.
* The separately staged roster OCR extension uses privately preserved roster screenshots, operator-controlled crop data, and cropped roster panels only; its candidate data remains separate from confirmed roster data.
* Screenshot hashes must support duplicate detection.

This document does not define crop coordinates, exact JSON formats, confidence fields, storage paths, bucket names, or OCR parser schemas.

## 9. Correction and Finalization History

The logical correction or revision history must preserve:

* Affected entity
* Previous value or previous revision reference
* Corrected value
* Actor
* Timestamp
* Reason or correction context where required
* Whether the change occurred before or after finalization
* Approval or authorization state where required

Required protections:

* Corrections must not destroy original OCR evidence.
* Previous match-result information must remain recoverable.
* Finalized-result corrections require explicit authorization.
* Correction history must be auditable.
* Silent overwriting is prohibited.

This document does not prescribe event sourcing or an exact audit-table implementation.

## 10. Local Room Data Model

Room's logical responsibilities are:

* Local tournament drafts
* Local roster drafts and cached confirmed roster data
* Match drafts and review state
* Temporary OCR processing and correction state
* Cached finalized records required for offline viewing
* Pending synchronization operations
* Synchronization status and failure information
* Recovery after app restart

Local Room design requirements:

* Use transactions for multi-record updates.
* Use Room migrations for every schema change.
* Preserve unsynchronized drafts.
* Distinguish locally created, synchronized, conflicted, failed, and finalized states.
* Avoid stale local writes silently replacing newer backend data.
* Keep locally stored sensitive data to the minimum required.
* Finalized records must not become freely editable because they exist locally.

This document does not define exact Room entity names, DAO methods, converters, indices, or database version numbers.

## 11. Supabase Backend Data Model

The logical Supabase relational model is:

* Authentication identity is provided by Supabase Auth.
* Tournament records belong to an authenticated owner.
* Teams belong to tournaments.
* Players belong to teams.
* Matches belong to tournaments.
* Results belong to matches and reference tournament teams.
* Scoreboard screenshots belong to tournaments and matches.
* The staged roster screenshot set belongs to one tournament and is distinct from match screenshots.
* OCR records belong to screenshots.
* Corrections or revisions reference affected result data.
* Export and synchronization records reference their relevant tournament or match operations.

Backend requirements:

* Every exposed table must have RLS enabled.
* Child-record access must be derived from tournament ownership.
* Authentication without ownership is insufficient.
* Foreign keys and uniqueness constraints must protect data integrity.
* Versioned migrations create and modify the schema.
* Privileged functions must be narrowly scoped.
* Finalized records require database-level protection where practical.
* Existing applied production migrations must never be rewritten.

This document does not create SQL, policy definitions, RPC names, table DDL, or migration files.

## 12. Relationships and Cardinality

The approved logical relationships are:

* One authenticated owner may own multiple tournaments.
* One tournament contains exactly 12 team slots.
* One team slot contains four to six players when complete.
* One tournament contains zero to 10 matches.
* One finalized match contains exactly 12 match results.
* Each match result references one tournament team.
* One match may reference one or more approved scoreboard screenshots where later workflow rules allow.
* One screenshot may have multiple processing attempts or OCR runs.
* One OCR run may contain multiple parsed observations.
* The staged roster workflow expects three ordered roster screenshots, each with four visible team slots, before association to the fixed 12-slot roster.
* One match result may have multiple revisions but one current confirmed state.
* One tournament has one derived current standings projection that is reproducible from finalized matches.
* One domain record may have multiple synchronization attempts without creating duplicate domain records.
* One finalized export request may have multiple attempts but must not create duplicate destination rows.

This document does not invent collaborative membership cardinality or public access models.

## 13. Integrity and Validation Constraints

Required constraint categories are:

* Primary-key or stable-identity constraints
* Foreign-key integrity
* Tournament ownership linkage
* Unique team-slot number per tournament
* Unique team name per tournament
* Valid team-slot range `1`–`12`
* Valid player-count range `4`–`6` for completed rosters
* Unique match number per tournament
* Match-count limit of `10`
* Valid placement range `1`–`12`
* Unique placement per match
* Unique team per match
* Non-negative kill values
* Finalized match completeness
* Duplicate screenshot-hash detection
* Idempotency-key uniqueness where used
* Controlled status transitions
* Finalized-data overwrite prevention

Constraint enforcement may require application-level validation, transaction-level validation, database constraints, or combined enforcement. This document does not claim that PostgreSQL alone automatically enforces exact child-row counts without a specific approved transactional design.

## 14. Authentication and Row Level Security

Approved authentication and authorization boundaries are:

* Supabase Auth supplies user identity.
* Tournament ownership is the root authorization boundary.
* Owners may access only their authorized tournament hierarchy.
* Child-table policies must verify access through the parent tournament.
* Authentication-only policies are insufficient.
* Anonymous public access is not an approved MVP requirement.
* Service-role access is backend-only.
* Privileged integrations must use secure backend functions.
* RLS must be tested separately for `SELECT`, `INSERT`, `UPDATE`, and `DELETE`.
* Storage access must follow the same tournament-ownership boundary.

This document does not invent collaboration, sharing, administrator bypass, or team-member access policies.

## 15. Synchronization and Idempotency

Synchronization requires:

* Stable local and remote identities
* Explicit synchronization state
* Pending operation identity
* Operation type
* Attempt tracking
* Recoverable failure information
* Idempotency protection
* Conflict detection
* Server-authoritative confirmation
* Protection against stale overwrites
* Safe retry after connectivity returns

Clarifications:

* Repeating an operation must not create duplicate tournaments, teams, players, matches, results, screenshots, or exports.
* Finalized server data must not be overwritten by stale local drafts.
* Exact version-field, timestamp, revision-token, and merge strategy remain deferred technical decisions.
* Local queue implementation belongs to Phase 5 and cloud synchronization belongs to Phase 6.

This document does not invent retry intervals or background-worker configuration.

## 16. Finalized-Data Protection

Approved finalized-data protections are:

* Draft-to-finalized is a controlled state transition.
* Finalization requires valid complete data.
* Finalized matches contribute to standings and become export-eligible.
* Finalized data cannot be silently overwritten.
* Corrections to finalized data require explicit authorization and audit history.
* Stale local data cannot downgrade or replace a finalized backend state.
* Database and application validation must work together.
* Exports must reference finalized, current, confirmed data.

This document does not define an unapproved approval hierarchy or role system.

## 17. Storage Design

Approved storage design boundaries are:

* Screenshot binaries belong in private controlled Supabase Storage.
* Relational records store metadata and storage references.
* Original screenshots remain distinguishable from processed images.
* Storage objects require ownership-based access controls.
* Service-role credentials remain backend-only.
* Private screenshots must not be committed to the public repository.
* Missing, failed, or deleted storage objects must not silently corrupt relational tournament data.

Deferred storage details:

* Bucket names
* Exact object paths
* File-size limits
* Retention periods
* Deletion schedules
* Signed-URL duration
* Image-processing storage lifecycle

## 18. Migration and Schema-Change Rules

Approved migration and schema-change rules are:

* Supabase schema changes require new versioned migration files.
* Applied production migrations must not be edited or deleted.
* Corrections require new corrective migrations.
* Room schema changes require explicit Room migrations.
* Destructive changes require approval, backup, rollback planning, and rehearsal.
* Schema changes require tests.
* Migration order must remain deterministic.
* Production schema must be reproducible from migration history.
* Manual production SQL must not replace maintained migration history.
* Data backfills require explicit approval and verification.

## 19. Testing Requirements

Required Room database tests:

* CRUD behavior
* Transactions
* Migrations
* Constraints
* Restart recovery
* Unsynchronized draft preservation
* Finalized-state protection

Required Supabase database tests:

* Migration application
* Foreign keys
* Uniqueness constraints
* RLS for `SELECT`, `INSERT`, `UPDATE`, and `DELETE`
* Unauthorized cross-owner access
* Privileged function restrictions
* Synchronization idempotency
* Conflict handling
* Finalized-data protection
* Screenshot metadata and storage authorization

Required integration tests:

* Local-to-remote synchronization
* Retry without duplication
* App restart during pending work
* Correction-history preservation
* Standings regeneration
* Finalized-only export

No database implementation or test currently exists unless verified in the repository. The current tracked repository does not yet verify Room entities, Supabase schema objects, or database test code.

## 20. Deferred Database Decisions

The following matters remain explicitly deferred:

* Exact physical table names
* Exact SQL column types
* UUID or alternative identity-generation implementation
* Precise duplicate-player scope across teams
* Exact revision or concurrency-control field
* Synchronization merge strategy
* Local queue schema
* Exact audit-history implementation
* Standings cache or materialized-view strategy
* OCR raw-payload representation
* Matching-candidate persistence structure
* Storage bucket names and object paths
* Screenshot retention and deletion rules
* Export-operation schema
* Production indexing strategy
* Archival and deletion behavior
* Collaborative tournament-access model, if later approved

These decisions require authoritative approval or later canonical documentation and are not resolved here.

## 21. Roadmap Alignment

Approved roadmap alignment is:

* Phase 2 introduces tournament, team-slot, and player domain data.
* Phase 3 introduces match and result data.
* Phase 4 introduces deterministic standings.
* Phase 5 implements Room entities, DAOs, migrations, and local integrity.
* Phase 6 implements Supabase schema, RLS, synchronization, idempotency, conflict handling, and finalized-data protection.
* Phase 7 adds screenshot storage and metadata.
* Phase 8 adds OCR processing data.
* Phase 9 adds matching, correction, and raw-versus-confirmed preservation.
* The roster OCR extension adds approved Phase 5 atomic local replacement, Phase 6 revision-safe cloud replacement, Phase 7 image/crop/set state, Phase 8 cropped OCR, Phase 9 review/correction, and Phase 12 real acceptance evaluation work.
* Phase 10 adds export-operation and idempotency support.
* Phase 12 validates database, RLS, synchronization, and migration behavior.
