# Phase 6 Kickoff Audit

## Purpose

This audit verifies whether Rank-Forge is ready to begin Phase 6: Authentication, Backend, and Cloud Sync.

Phase 6 must preserve the Phase 5 local-first persistence model. Room remains the phone working database. Supabase will be added for authentication, backup, multi-device synchronization, and server-enforced protection in later versions.

## Branch

Audit branch:

`docs/phase-6-kickoff-audit`

Base branch:

`main`

Base commit:

`84164cb Merge pull request #37 from hog-gamers-info-hub/docs/phase-5-closure-audit`

## Repository State

Verified before audit:

* `main` was up to date with `origin/main`.
* Working tree was clean.
* `main` and `origin/main` were synchronized: `0 0`.
* Phase 5 closure audit merge was the latest commit.
* Audit branch was created successfully.

## Source-of-Truth Documents

The project workflow folder contains:

* `00_PHASE_AND_VERSION_ROADMAP.md`
* `01_PHASE_0_CLOSURE_AUDIT.md`
* `02_PHASE_0_RE_CLOSURE_AUDIT.md`
* `03_PHASE_4_CLOSURE_AUDIT.md`
* `04_PHASE_5_CLOSURE_AUDIT.md`

Phase 5 closure is therefore represented before Phase 6 begins.

## Android Structure

The Android project already has the expected layered structure:

* `core`
* `data`
* `domain`
* `presentation`

Relevant packages exist for:

* `data/di`
* `data/local`
* `data/tournament`
* `domain/tournament`
* `presentation/navigation`
* `presentation/screen`
* `presentation/component`
* `presentation/theme`

## Room Persistence

Room persistence exists and must not be replaced during Phase 6.

Evidence:

* `data/local/RankForgeDatabase.kt`
* `data/local/TournamentDaos.kt`
* `data/local/TournamentEntities.kt`
* `data/tournament/RoomTournamentRepository.kt`
* `domain/tournament/TournamentRepository.kt`

`TournamentDataModule` binds `RoomTournamentRepository` to `TournamentRepository`.

Phase 6 must preserve this local-first contract.

## Existing Domain Contracts

The domain layer already includes tournament, roster, match, draft, finalization, and correction use cases.

Important existing workflows include:

* Tournament creation
* Tournament observation
* Team slot naming
* Roster entry and review
* Match creation
* Draft placement entry
* Draft kill entry
* Match finalization
* Match correction
* Standings derivation

These contracts should be preserved. Phase 6 must add authentication and later synchronization around the existing local-first model, not bypass it.

## Test Baseline

JVM test structure exists under:

`app/src/test/java/com/hoggamers/rankforge`

Connected Android test structure exists under:

`app/src/androidTest/java/com/hoggamers/rankforge`

This provides a baseline for v0.6.0 authentication unit, Compose, and physical-device tests.

## Supabase Footprint

The Supabase folder exists.

Current committed Supabase placeholders include:

* `supabase/config.toml`
* `supabase/functions/.gitkeep`
* `supabase/migrations/.gitkeep`
* `supabase/tests/.gitkeep`

No real Phase 6 migrations, Edge Functions, or Supabase tests are present yet.

`supabase/.temp/` exists locally and must not be committed.

## Dependency Baseline

Room is already configured.

Evidence:

* `androidx.room:room-runtime`
* `androidx.room:room-ktx`
* `androidx.room:room-compiler`
* `androidx.room:room-testing`
* `room.schemaLocation`

No Android Supabase client, GoTrue, PostgREST, Ktor, WorkManager, or AndroidX Security Crypto dependency was found during the dependency scan.

## Approved Phase 6 Architecture

Phase 6 must follow this architecture:

`Compose UI -> Domain use cases -> Local-first repositories -> Room database -> Persistent sync queue -> Sync coordinator -> Supabase Data API / RPC -> Postgres + RLS`

## Phase 6 Rules

The following rules are mandatory:

* UI reads from Room, not directly from Supabase.
* Local changes are written to Room first.
* Synchronization runs separately and updates Room only after server confirmation.
* Every cloud record uses a stable client-generated UUID.
* Internet failure must never block the existing offline tournament workflow.
* The Android app must contain only the Supabase project URL and publishable key.
* The Android app must never contain a service-role key or secret key.
* Protected finalization and correction operations must be enforced by Postgres in later versions.

## v0.6.0 Scope

Allowed in v0.6.0:

* Email/password sign-up
* Email/password login
* Authentication-aware navigation
* Authentication loading states
* Authentication success states
* Authentication error states
* Secure session restoration foundation where needed
* Unit tests
* Compose tests
* Physical-device verification

Not allowed in v0.6.0:

* Tournament synchronization
* Cloud tournament tables
* Cloud roster storage
* Cloud match storage
* Offline sync queue
* WorkManager sync
* Room replacement
* Local tournament deletion on login or logout
* Service-role or secret keys in Android
* Finalized result protection
* Correction sync
* Conflict resolution
* Large unrelated refactors

## Required Decisions Before v0.6.0 Implementation

Before creating `feature/v0.6.0-supabase-authentication`, record decisions for:

1. Authentication method
2. Email confirmation behaviour
3. Existing local data behaviour after first login
4. Ownership model
5. Multi-device support model
6. Logout behaviour
7. Conflict interface baseline for later versions
8. Correction audit baseline for later versions

Recommended decisions:

* Authentication method: email/password only for Phase 6.
* Email confirmation: optional during local development; production can require confirmation later if needed.
* Existing local data: keep local-only unless the user explicitly chooses Back up existing tournaments in a later sync version.
* Ownership: one owner per tournament; no collaboration in Phase 6.
* Multi-device support: supported later through optimistic revisions.
* Logout behaviour: retain local data on device but hide account-owned cloud-backed records after logout once ownership is introduced.
* Conflict interface: user chooses local draft or cloud draft; finalized data cannot be replaced through this interface.
* Correction audit: retain prior finalized values and correction metadata.

## Risks

| Risk                                                       |   Impact | Mitigation                                                     |
| ---------------------------------------------------------- | -------: | -------------------------------------------------------------- |
| Authentication work accidentally starts tournament sync    |     High | Keep v0.6.0 auth-only                                          |
| Supabase client is added directly into tournament UI flows |     High | Keep auth isolated from tournament persistence                 |
| Room persistence is replaced or bypassed                   |     High | Preserve `RoomTournamentRepository` and `TournamentRepository` |
| Local tournament data is deleted during login/logout       |     High | Explicitly prohibit local deletion in v0.6.0                   |
| Secrets are added to Android resources                     | Critical | Android may contain only URL and publishable key               |
| `supabase/.temp/` is accidentally committed                |   Medium | Check `git status --short` before commit                       |
| Auth navigation breaks offline tournament workflow         |     High | Verify existing local workflow on physical device              |

## Audit Decision

`Ready to proceed to Phase 6 decision recording and then start v0.6.0.`

v0.6.0 implementation must not begin until the eight required Phase 6 decisions are recorded.

## Next Branch After Audit Merge

`feature/v0.6.0-supabase-authentication`
