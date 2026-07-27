# Phase 6 Decisions

## Purpose

This document records the required Phase 6 decisions before starting `v0.6.0 — Supabase Authentication`.

Phase 6 must preserve Rank-Forge's local-first architecture. Room remains the phone working database. Supabase is added for authentication, backup, multi-device synchronization, and server-enforced protection in later versions.

## Decisions

### 1. Authentication method

Decision: Use email/password authentication only for Phase 6.

Rationale: This is the smallest secure authentication scope and avoids adding OAuth/provider complexity before synchronization, RLS, and ownership rules are implemented.

### 2. Email confirmation behaviour

Decision: Email confirmation may remain optional during local development. Production can require confirmation later after the authentication flow is stable.

Rationale: Optional confirmation keeps local-device testing practical for `v0.6.0`, while leaving room to harden production behaviour later.

### 3. Existing local data after first login

Decision: Existing local tournaments remain local-only after first login. They must not be silently uploaded, claimed, reassigned, or deleted.

A later sync version may add an explicit one-time action such as `Back up existing tournaments`.

Rationale: Silent ownership assignment can create security, privacy, and data-integrity risks.

### 4. Ownership model

Decision: One owner per tournament for Phase 6. No collaboration or shared editing in Phase 6.

Rationale: Single-owner ownership keeps RLS, conflict handling, and multi-device sync simpler and safer.

### 5. Multi-device support model

Decision: Multi-device support is planned through optimistic revisions.

Rationale: Revision-based updates prevent silent last-write-wins overwrites and prepare the app for safe conflict handling.

### 6. Logout behaviour

Decision: `v0.6.0` must not delete local tournament data on logout.

In `v0.6.0`, authentication is additive. Local tournament workflows remain accessible without sign-in.

Once cloud-backed ownership is introduced in later versions, account-owned cloud-backed records should be hidden after logout unless the same account signs in again. Pure local-only records remain on the device.

Rationale: Logout must not destroy offline work, but account-owned synced data should not remain casually visible after logout once ownership exists.

### 7. Conflict interface baseline

Decision: Later conflict resolution should let the user choose between local draft and cloud draft when both changed.

Finalized server data cannot be replaced through the draft conflict interface.

Rationale: This avoids silent data loss and protects finalized results from ordinary draft overwrites.

### 8. Correction audit baseline

Decision: Protected corrections must retain prior finalized values and correction metadata.

Rationale: Finalized result changes need traceability and should be handled through the explicit correction workflow, not direct edits.

## v0.6.0 Boundary

Allowed in `v0.6.0`:

- Email/password sign-up
- Email/password login
- Logout
- Session restoration
- Authentication loading, success, and error states
- Authentication-aware navigation
- Expired-session handling
- Unit tests
- Compose tests
- Physical-device verification

Not allowed in `v0.6.0`:

- Tournament synchronization
- Cloud tournament storage
- Cloud match storage
- Offline sync queue
- WorkManager sync
- RLS implementation for tournament data
- Room replacement
- Local tournament deletion on login/logout
- Service-role or secret keys in Android
- Finalized result protection
- Conflict resolution
- Large unrelated refactors

## Authentication Navigation Decision for v0.6.0

`v0.6.0` must use an additive authentication model.

The app must not require sign-in before local tournament workflows can be used.

Authentication screens may be accessible from navigation or account entry points, but existing local tournament creation, roster, match, scoring, finalization, correction, and standings workflows must remain available offline.

Login success may show signed-in account state, but must not trigger tournament sync.

Logout success must clear only the Supabase/auth session. It must not clear Room tournament data.

## Decision Status

Ready to create or continue `feature/v0.6.0-supabase-authentication` after this decision document is restored and committed.

## v0.6.0.1 Session Authentication Hardening Decisions

### Version boundary

`v0.6.0.1` hardens and verifies the session restoration, logout, expired-session, and authentication-error foundation already introduced in `v0.6.0`.

It must not reintroduce authentication or start cloud synchronization.

### Logout scope

Phase 6 logout is current-device/local-session logout.

Do not add global logout across all devices.

Logout must never delete or modify Room tournament, roster, match, correction, scoring, or standings data.

### Offline restoration

A temporary network failure during restoration must:

* preserve the saved local session where safe;
* preserve all local Room data;
* keep local tournament workflows available;
* show a recoverable authentication warning;
* not claim confirmed backend authorization until validation succeeds.

### Invalid or expired session

A definitively invalid or expired session must:

* clear the unusable local authentication session;
* transition the UI to signed out;
* show an actionable sign-in-again message;
* preserve all Room data.

Temporary connectivity failures must not be treated as definitive session invalidation.

### Logout failure behavior

After the user requests logout, the local device session must be cleared even when remote revocation cannot be confirmed.

The UI must not remain falsely signed in.

Local tournament data must remain unchanged.

### Authentication error model

Authentication errors must be represented through stable typed application/domain failures rather than displaying arbitrary SDK or server exception messages directly.

The approved minimum error categories are:

* invalid credentials;
* invalid email;
* weak password;
* account already registered;
* email confirmation required;
* rate limited;
* network unavailable;
* timeout;
* expired or invalid session;
* missing Supabase configuration;
* unknown authentication failure.

User-facing messages must use Android string resources during implementation.

Errors and logs must not expose passwords, access tokens, refresh tokens, keys, raw HTTP bodies, or sensitive backend details.

### Sign-up outcome

The implementation must distinguish:

* sign-up that immediately creates an authenticated session;
* sign-up that requires email confirmation.

The UI must not present both outcomes as the same generic success state.
