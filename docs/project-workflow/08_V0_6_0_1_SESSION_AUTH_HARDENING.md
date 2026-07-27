# v0.6.0.1 Session Authentication Hardening

## 1. Purpose

This document is the implementation authority for `v0.6.0.1 — Session restoration, logout, and authentication errors`.

The version hardens and verifies the authentication foundation introduced in `v0.6.0` while preserving Rank-Forge's local-first architecture and additive authentication model.

## 2. Governing authorities

This document is governed by:

* `AGENTS.md`
* `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`
* `docs/project-workflow/05_PHASE_6_KICKOFF_AUDIT.md`
* `docs/project-workflow/06_PHASE_6_DECISIONS.md`
* `docs/project-workflow/07_V0_6_0_SUPABASE_AUTHENTICATION.md`
* `docs/02_SYSTEM_ARCHITECTURE.md`
* `docs/03_DATABASE_DESIGN.md`
* `docs/06_ANDROID_APP.md`
* `docs/07_SUPABASE_BACKEND.md`
* `docs/09_TESTING_AND_ACCEPTANCE.md`
* `docs/11_SECURITY_AND_PRIVACY.md`
* Relevant documents under `docs/ai/`

Current explicit user decisions and this approved task govern the immediate scope and file boundaries.

## 3. Dependency on completed v0.6.0

`v0.6.0` is complete and merged in `253b5ca`.

It introduced email/password Supabase authentication, additive authentication navigation, session observation and restoration, login, sign-up, logout, authentication UI states, and focused unit and Compose tests.

`v0.6.0.1` depends on that baseline and must not reintroduce authentication or begin cloud synchronization.

## 4. Current implementation baseline

The merged baseline currently includes:

* Supabase Auth client initialization using the project URL and publishable client key.
* Auth domain contracts for users, session state, repositories, and authentication operations.
* Remote session observation, session restoration, sign-up, login, and logout operations.
* A ViewModel that observes session state and coordinates authentication actions.
* Resource-backed authentication screen labels and additive navigation from local tournament workflows.
* Unit and Compose coverage for authentication state reduction, restore/login/sign-up/logout flows, local tournament access while signed out, and preservation of local tournament data on logout.

The baseline remains insufficient for this version because restoration ordering, temporary versus definitive session failures, logout failure handling, stable typed error categories, and the two sign-up outcomes require explicit hardening and verification.

## 5. Approved decisions

The approved decisions are:

* `v0.6.0.1` hardens and verifies session restoration, logout, expired-session handling, and authentication errors only.
* Logout is current-device/local-session logout; global logout is excluded.
* Logout never deletes or modifies Room tournament, roster, match, correction, scoring, or standings data.
* Temporary restoration failures preserve the saved session where safe, preserve Room data, keep local workflows available, and show a recoverable warning without claiming confirmed backend authorization.
* Definitively invalid or expired sessions are cleared, transition the UI to signed out, and show an actionable sign-in-again message while preserving Room data.
* Logout clears the local device session even when remote revocation cannot be confirmed, so the UI cannot remain falsely signed in.
* Authentication errors use stable typed application/domain failures and resource-backed user messages; sensitive credentials, tokens, keys, raw HTTP bodies, and backend details are not exposed.
* Sign-up distinguishes immediate authentication from email-confirmation-required registration.

## 6. Exact implementation scope

### Allowed

* Deterministic Supabase Auth initialization before resolving restoration
* Persisted-session restoration hardening
* Valid, absent, expired, invalid, offline, and temporarily unverifiable session handling
* Reliable current-device logout
* Stable typed authentication failures
* Android resource-backed authentication messages
* Distinction between immediate sign-in and email-confirmation-required sign-up
* Focused unit, ViewModel, Compose, restart, offline, and device tests
* Preservation of additive authentication navigation
* Documentation directly required for v0.6.0.1

## 7. Explicit exclusions

### Excluded

* OAuth
* Phone authentication
* MFA
* Password recovery
* Account deletion
* Global logout
* Tournament or roster synchronization
* Match synchronization
* Supabase database migrations
* PostgreSQL schema changes
* RLS policies
* Storage
* Room schema changes
* WorkManager
* Offline sync queues
* Ownership implementation
* Conflict resolution
* Finalized-result protection
* OCR
* Exports
* Collaboration
* Dependency upgrades without a verified blocker
* Broad UI redesign
* Unrelated refactoring

## 8. Acceptance criteria

1. A valid persisted session restores after process restart without re-entering credentials.
2. Authentication initialization cannot incorrectly settle on signed out before persisted-session loading completes.
3. No saved session resolves deterministically to signed out.
4. A definitively expired or invalid session is cleared and produces an actionable sign-in-again state.
5. Temporary network or timeout failure does not destroy a potentially valid saved session.
6. Local tournament workflows remain usable during restoration failure.
7. Successful logout clears the local session and remains logged out after restart.
8. Logout never deletes or modifies Room data.
9. Logout failure cannot leave stale signed-in UI state.
10. Authentication failures map to stable resource-backed user messages.
11. Raw SDK, server, token, credential, or HTTP details are not exposed.
12. Sign-up distinguishes immediate authentication from email-confirmation-required registration.
13. Existing v0.6.0 sign-up, login, additive navigation, and offline local workflows remain functional.
14. No Room schema, migration, RLS, backend schema, storage, synchronization, or WorkManager changes are introduced.

## 9. Automated verification requirements

Implementation must add focused tests for:

* Auth initialization ordering and restoration state transitions.
* Valid, absent, expired, invalid, offline, timeout, and temporarily unverifiable sessions.
* Current-device logout success and logout failure with local session clearing.
* Stable mapping of SDK and network failures to the approved typed categories.
* Resource-backed user-facing messages without raw exception details.
* Immediate-authentication and email-confirmation-required sign-up outcomes.
* ViewModel state transitions and preservation of local tournament workflows.
* Compose behavior for loading, warnings, signed-out recovery, sign-up outcomes, and logout.
* Process-restart and offline recovery behavior where the test environment supports it.

Required repository verification includes:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
git diff --check
```

## 10. Connected-device and physical-device verification

Verification must include an Android API 26 device or emulator, the configured target API device or emulator, and at least one physical Android device when available.

On a configured local Supabase project, verify:

* A valid saved session restores after process restart.
* Restoration remains recoverable during temporary network loss and timeout.
* Definitively invalid or expired sessions become signed out with a sign-in-again action.
* Local tournament creation and access remain available during restoration failure.
* Successful logout remains signed out after restart.
* Logout failure does not leave stale signed-in UI state.
* Local tournament, roster, match, correction, scoring, and standings data remain unchanged after logout.
* Immediate sign-up and email-confirmation-required sign-up show distinct outcomes.

No real credentials or local configuration values may be committed as verification evidence.

## 11. Security verification

Security verification must confirm:

* Only the Supabase URL and publishable client configuration are available to Android.
* Service-role keys, secret keys, database passwords, JWT secrets, tokens, and private credentials are absent from source, resources, APK contents, logs, and documentation.
* Passwords, access tokens, refresh tokens, raw HTTP bodies, and sensitive backend details are not displayed or logged.
* Typed error mapping prevents arbitrary SDK or server exception text from reaching the user interface.
* Temporary network failure cannot be mistaken for confirmed backend authorization or definitive session invalidation.
* Logout does not modify Room data.
* No synchronization, schema, migration, RLS, storage, or WorkManager behavior is introduced.

## 12. Rollback boundary

The v0.6.0.1 documentation gate is independently reversible through the documentation commit or branch changes. Reverting the implementation commit, if later created, must remove only the v0.6.0.1 authentication-hardening changes and preserve the completed v0.6.0 baseline.

This version must not require database rollback, migration rollback, Room migration rollback, data deletion, secret rotation, or production backend recovery because those systems are outside scope.

## 13. Implementation readiness decision

Ready to implement v0.6.0.1 after this documentation gate is reviewed, committed, merged, and main is confirmed clean and synchronized.

## 14. Required next branch

`feature/v0.6.0.1-session-auth-hardening`
