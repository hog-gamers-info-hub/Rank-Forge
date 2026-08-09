# CR-001 — Google Sign-In Decisions

## Status

**Audit Complete — Decisions Documented**

Implementation has not started.

This change is managed independently from Phase 13 and must not modify the approved Phase 13 version sequence.

---

## 1. Purpose

Add Google Sign-In as an additional authentication method for Rank Forge while preserving the existing Supabase email/password authentication workflow and existing authenticated-user ownership semantics.

Google Sign-In is an additive authentication option.

It must not:

- replace email/password authentication;
- make authentication mandatory for existing local workflows;
- change existing tournament ownership behavior;
- change Room data;
- change scoring, OCR, export, or tournament workflows;
- alter the approved Phase 13 roadmap.

---

## 2. Existing Authentication Baseline

Rank Forge already has Supabase authentication with:

- email/password sign-up;
- email/password login;
- persisted Supabase session restoration;
- authenticated/signed-out state observation;
- typed authentication failures;
- network and timeout handling;
- expired/invalid-session handling;
- logout;
- local session clearing if remote logout fails;
- authenticated foreground sync recovery;
- protection against logout deleting local tournament data.

The existing authentication screen is reachable from the application without globally gating local tournament workflows.

The existing implementation therefore remains the authentication foundation.

Google Sign-In must extend this architecture rather than replace it.

---

## 3. Approved Authentication Strategy

The first Google Sign-In implementation will use:

**Supabase browser-based Google OAuth with PKCE and an Android deep-link callback.**

Approved flow:

```text
Auth Screen
    ↓
Sign in with Google
    ↓
Supabase Auth OAuth request
    ↓
Google authentication in browser / Custom Tab
    ↓
Supabase OAuth callback
    ↓
Android app deep-link callback
    ↓
Supabase session established
    ↓
existing auth-state observer
    ↓
AuthState.SignedIn
```

This approach is selected because it:

- works with the existing Supabase Auth dependency;
- keeps authentication ownership inside the existing Supabase auth architecture;
- does not require a second native Google authentication stack;
- does not require Google Credential Manager for the initial implementation;
- minimizes new dependencies and implementation surface;
- supports PKCE;
- preserves the existing session restoration and logout architecture.

---

## 4. Critical Authentication-State Rule

Launching Google OAuth is **not authentication success**.

The existing email/password success reducer must not be reused in a way that immediately marks the user signed in after the Google browser is launched.

For Google OAuth:

```text
OAuth browser successfully opened
        ≠
authenticated user
```

Only a confirmed Supabase authenticated session may transition application auth state to signed in.

Authoritative success remains:

```text
Supabase session established
        ↓
existing auth-state observation
        ↓
AuthState.SignedIn
```

The Google launch operation must therefore use an outcome equivalent to:

```text
ExternalAuthenticationLaunched
```

or another non-authenticated launch state.

It must not emit:

```text
SignedIn
```

merely because the browser or Custom Tab opened successfully.

---

## 5. PKCE Decision

Google OAuth will use the Supabase Auth **PKCE** flow.

The Android Supabase Auth client must be configured with the approved callback scheme and host before implementation.

Conceptually:

```text
install(Auth) {
    scheme = <approved scheme>
    host = <approved host>
    flowType = PKCE
}
```

Exact implementation syntax must follow the Supabase Kotlin version currently used by Rank Forge.

---

## 6. Android Callback Decision

The Android app requires a deep-link callback that returns the completed OAuth flow to Rank Forge.

Current application state:

- `MainActivity` is the launcher activity;
- no authentication deep-link intent filter currently exists;
- no OAuth callback handler currently exists.

The smallest initial implementation should use the existing application activity unless implementation evidence demonstrates that a dedicated callback activity is safer.

The callback must support:

- app already running;
- app in background;
- cold application start;
- successful authentication;
- invalid callback;
- callback without a valid authenticated session.

The callback must ultimately be passed to the Supabase Auth deep-link/session handling mechanism.

---

## 7. Callback URI Gate

The exact callback scheme and host are **not approved in this document**.

They must be selected before implementation.

The final callback must:

- be unique to Rank Forge;
- not conflict with common application schemes;
- match Android manifest configuration;
- match Supabase Auth client configuration;
- be added to the hosted Supabase Auth redirect allow-list;
- be verified on the physical Android device.

Example structure only:

```text
<rank-forge-specific-scheme>://<auth-callback-host>
```

This example is not an approved production URI.

The exact value must be recorded before source-code implementation.

---

## 8. Google OAuth Client Decision

For the approved browser-based Supabase OAuth implementation, the initial Google OAuth configuration requires a Google OAuth client of type:

**Web application**

The authorized redirect URI must be the exact Supabase Google provider callback shown by the hosted Supabase project.

For the current hosted Rank Forge project, the expected standard form is:

```text
https://jfllzadfduzktczzdvil.supabase.co/auth/v1/callback
```

Before configuration, the exact callback must still be copied from the Supabase Dashboard rather than relying solely on the expected value above.

---

## 9. Android OAuth Client / SHA Decision

An Android-specific Google OAuth client and SHA-1 signing certificate are **not required for the initial browser-based Supabase OAuth implementation**.

They become relevant if Rank Forge later adopts native Google authentication using Android Credential Manager / Google ID tokens.

Therefore the initial implementation will not introduce native Google credential authentication.

Future native authentication would require separate decisions covering:

- Android OAuth client;
- package name;
- debug signing SHA-1;
- release signing SHA-1;
- Google Play App Signing SHA-1 where applicable;
- Credential Manager dependencies;
- native ID-token exchange.

---

## 10. Google Credential Manager Decision

Google currently recommends Credential Manager for native Android Google authentication.

Rank Forge will **not** introduce Credential Manager as part of CR-001 initial scope.

Reason:

The browser-based Supabase OAuth flow provides the required Google authentication capability with a substantially smaller architecture and dependency change.

Credential Manager may be considered later as a separate UX enhancement.

---

## 11. Existing-Account Linking Decision

Existing account identity is a critical data-ownership boundary.

Supabase `auth.uid()` is used as the ownership identity for authenticated cloud data.

Therefore:

**An existing Rank Forge email/password user signing in with Google using the same verified email must remain the same Supabase user UUID.**

Expected identity result:

```text
Existing user UUID
    ├── email identity
    └── google identity
```

Unacceptable result:

```text
Existing email user UUID
+
new Google user UUID
```

A second UUID could make existing RLS-protected cloud data appear inaccessible to the user.

---

## 12. Identity Linking Strategy

The initial implementation will rely on Supabase automatic identity linking for matching verified email identities.

CR-001 will not introduce:

- manual identity-link UI;
- identity unlinking;
- user-account merging;
- administrator account merging;
- service-role based identity manipulation.

Manual identity linking remains outside the initial scope.

Before release, same-email identity linking must be explicitly verified against hosted Supabase.

---

## 13. Duplicate-Account Protection

Verification must confirm that:

1. an existing email/password account has a known Supabase user UUID;
2. Google authentication is performed using the same verified email;
3. the user UUID remains unchanged;
4. a Google identity is attached to the same user;
5. no unwanted second `auth.users` record is created;
6. previously owned RLS-protected data remains accessible;
7. repeated Google authentication does not create duplicate identities or users.

Failure of any of these conditions blocks release of Google Sign-In.

---

## 14. OAuth Cancellation Behavior

Browser-based OAuth cancellation may occur when the user:

- presses Back;
- closes the browser;
- abandons authentication;
- returns to Rank Forge without completing Google authentication.

For the initial implementation:

- launching OAuth may return the UI to an idle/non-submitting state;
- absence of a successful OAuth callback leaves the app signed out;
- an existing authenticated session must not be corrupted;
- Google Sign-In must remain usable for another attempt;
- ordinary abandonment must not be incorrectly converted into authentication success;
- ordinary cancellation should not be surfaced as an unknown fatal authentication error.

A dedicated cancellation result is optional and may be added only if required by implementation behavior.

---

## 15. Offline and Network Failure Behavior

The Google Sign-In flow must handle:

### Offline before launch

The app remains stable and signed-out state is preserved.

A controlled authentication/network message may be shown.

### Network loss during OAuth

No false signed-in state may be produced.

The user must be able to retry.

### OAuth provider error

Provider/session errors must be mapped to controlled application auth failures.

Raw OAuth responses, tokens, secrets, or stack traces must not be exposed in the UI.

### Callback without valid session

The application remains safely signed out unless a valid existing session already exists.

---

## 16. Session Restoration Decision

Google-authenticated sessions will use the existing Supabase session restoration pipeline.

No separate Google session storage will be added.

After successful Google authentication:

```text
Supabase session
    ↓
existing persistence
    ↓
application restart
    ↓
existing restoreSession()
    ↓
existing auth-state observer
```

A successfully authenticated Google user must therefore restore normally after application restart.

---

## 17. Token Refresh Decision

Google Sign-In will not introduce a separate Android-managed Google token lifecycle.

Rank Forge will rely on the existing Supabase Auth session lifecycle and refresh handling.

The initial implementation does not require the application to retain or use Google provider access tokens.

---

## 18. Logout Decision

Google-authenticated users will use the existing Rank Forge / Supabase logout workflow.

Logout must:

- clear the local Supabase session;
- return application auth state to signed out;
- preserve local tournament data;
- remain safe if remote revocation fails;
- preserve existing typed warning behavior.

CR-001 does not require global Google-account sign-out from the Android device.

---

## 19. Google API Access Decision

CR-001 is authentication only.

The application will not request Google API access for:

- Google Drive;
- Google Sheets;
- Gmail;
- Calendar;
- Contacts;
- profile management beyond authentication identity;
- any unrelated Google service.

Google authentication scopes must remain minimal.

Expected scopes are limited to identity requirements such as:

- `openid`;
- email;
- basic profile.

---

## 20. Security Boundaries

### Allowed in Android application

- Supabase project URL;
- Supabase publishable/anonymous client key appropriate for mobile clients;
- Google OAuth Client ID where required by the selected architecture;
- Android callback URI.

### Forbidden in Android application

The following must never be embedded in source code, resources, BuildConfig, logs, APK assets, or other client-accessible locations:

- Supabase `service_role` key;
- Supabase secret key;
- Google OAuth Client Secret;
- raw long-lived authentication tokens;
- administrative credentials.

The Google OAuth Client Secret must remain in hosted Supabase / Google provider configuration only.

---

## 21. Logging Rules

Authentication logs must not contain:

- access tokens;
- refresh tokens;
- authorization codes;
- OAuth Client Secret;
- session JWT contents;
- service-role credentials.

Diagnostic logging may record controlled state transitions and sanitized failure categories.

---

## 22. Hosted Supabase Configuration Requirements

Before device implementation verification, hosted Supabase Auth must be checked manually for:

1. Google provider availability;
2. Google provider enabled status;
3. configured Google Web Client ID;
4. configured Google Client Secret;
5. exact Google OAuth callback URL;
6. Android application callback in Redirect URLs;
7. any Site URL behavior relevant to mobile OAuth;
8. authentication logs during verification.

The previous audit did not have connector access to hosted provider configuration.

Therefore current hosted Google provider configuration must not be assumed.

---

## 23. Google Cloud Configuration Requirements

Google Auth Platform must be configured with:

- suitable Google Cloud project;
- OAuth consent configuration;
- required identity scopes only;
- Web OAuth Client ID;
- authorized redirect URI matching Supabase;
- test users if the Google OAuth application remains in testing mode.

The Google OAuth Client Secret must never be copied into the Android application.

---

## 24. Existing Hosted Authentication State

The read-only audit observed:

```text
auth.users: 3
```

and existing identities were:

```text
email: 3
google: 0
```

This establishes a useful pre-Google baseline.

These counts may naturally change before implementation verification and must therefore be rechecked at execution time.

---

## 25. Approved Initial Implementation Scope

CR-001 implementation is limited to:

1. preserve existing email/password authentication;
2. configure Supabase Auth PKCE with the approved callback;
3. add Android OAuth deep-link handling;
4. add one Google OAuth launch operation through the existing auth architecture;
5. add one `Sign in with Google` action to the existing Auth screen;
6. prevent OAuth launch from being treated as signed-in success;
7. let Supabase session state remain authoritative;
8. handle launch/provider/network failures safely;
9. preserve safe cancellation/abandonment behavior;
10. reuse existing restoration, refresh, and logout behavior;
11. add focused automated verification;
12. perform physical-device verification;
13. verify hosted Supabase identity linking and UUID preservation.

---

## 26. Explicitly Out of Scope

CR-001 does not include:

- replacing email/password auth;
- mandatory sign-in;
- Auth screen redesign;
- native Credential Manager integration;
- Google One Tap implementation;
- manual identity linking;
- identity unlinking;
- account-merging tools;
- Google API access;
- Google Drive integration;
- Google Sheets export changes;
- profile/avatar features;
- password-reset redesign;
- account deletion;
- database migrations;
- Room schema changes;
- RLS redesign;
- Edge Functions;
- service-role usage in Android;
- Phase 13 modifications;
- Phase 13 version changes;
- OCR changes;
- screenshot workflow changes;
- tournament workflow changes.

---

## 27. Acceptance Criteria

Google Sign-In is acceptable only when all applicable criteria pass.

### Existing behavior

- Email/password sign-up still works.
- Email/password login still works.
- Existing session restoration still works.
- Existing logout still works.
- Existing local tournament workflows remain available as before.
- Local tournament data is not deleted by logout.

### Google authentication

- Google Sign-In is available from the existing Auth screen.
- Selecting Google Sign-In launches the intended OAuth flow.
- Browser launch alone does not mark the user signed in.
- Successful OAuth callback establishes a valid Supabase session.
- Application auth state changes only after Supabase confirms authentication.
- Successful Google session restores after restart.
- Existing token/session refresh behavior remains functional.

### Failure behavior

- cancellation does not create false success;
- offline launch does not crash;
- network failure does not create false success;
- provider failure remains controlled;
- callback failure remains controlled;
- retry remains possible.

### Account identity

- existing email/password + same verified Google email retains the same Supabase user UUID;
- no unintended duplicate user is created;
- Google identity is associated with the expected user;
- repeated Google login remains idempotent;
- existing RLS-owned cloud data remains accessible.

### Android lifecycle

- OAuth callback works while app is running;
- OAuth callback works after app backgrounding;
- OAuth callback works from cold start.

### Security

- no Google Client Secret exists in Android;
- no Supabase service-role or secret key exists in Android;
- no authentication tokens are logged;
- only minimum identity scopes are requested.

### Project boundary

- no Phase 13 source or documentation is modified by implementation;
- unrelated workflows remain unchanged.

---

## 28. Automated Verification

At minimum, implementation verification should cover:

### Repository / domain tests

- Google OAuth launch operation;
- OAuth launch does not produce `SignedIn`;
- successful session observation produces signed-in state;
- provider/launch failure mapping;
- duplicate Google-button submission protection where applicable;
- cancellation/no-session behavior;
- existing email/password regressions;
- session restoration regression;
- logout regression;
- local-data preservation regression.

### UI tests

- Google Sign-In action is displayed;
- action invokes the expected callback;
- submitting state is handled safely;
- existing email/password controls remain functional;
- signed-in UI remains driven by authoritative session state.

### Navigation / callback tests

- deep-link intent routing;
- valid callback processing;
- malformed callback safety where practical.

### Build verification

Run the relevant existing project gates including:

```text
testDebugUnitTest
lintDebug
assembleDebug
assembleDebugAndroidTest
```

and focused connected tests where required.

---

## 29. Physical Device Verification

Verification must include a real Android device.

Required scenarios:

1. new Google account authentication;
2. existing email/password user using matching Google email;
3. repeat Google login;
4. different Google account;
5. browser Back/cancel;
6. offline before OAuth launch;
7. network loss during authentication;
8. app warm callback;
9. app background callback;
10. cold-start callback;
11. kill/restart after successful authentication;
12. logout;
13. restart after logout;
14. verify local tournament data remains intact.

---

## 30. Hosted Backend Verification

Before release, verify hosted Supabase state.

For same-email account linking:

```text
before:
existing user UUID
auth.users count
existing identities

after Google login:
same user UUID
google identity attached
no unintended duplicate user
existing cloud data accessible
```

Supabase authentication logs should also be reviewed for unexpected OAuth or callback failures.

---

## 31. Rollback Plan

### Android rollback

If Google Sign-In introduces instability:

- remove/revert Google button;
- revert Google OAuth launch operation;
- revert OAuth callback handling if no longer required;
- revert Google-specific PKCE/deep-link configuration where appropriate.

Preserve:

- existing email/password authentication;
- existing session architecture;
- existing logout;
- existing local data;
- existing cloud ownership.

### Hosted Supabase rollback

Google authentication can be disabled at the hosted Supabase provider level to stop new Google login attempts.

Before disabling Google authentication after users have begun using it, identify whether Google-only users exist.

A Google-only user may otherwise lose their available login method.

### Forbidden rollback actions

Do not:

- delete `auth.users` records as routine rollback;
- delete existing identities without explicit migration planning;
- change tournament ownership UUIDs;
- merge accounts using Android client code;
- introduce service-role credentials into the application;
- delete production OAuth credentials merely to perform a temporary rollback.

Provider disablement is preferred because it is controlled and reversible.

---

## 32. Pre-Implementation Gates

Source-code implementation must not begin until all of the following are resolved:

- [ ] this decision document is reviewed and merged;
- [ ] exact Android OAuth callback scheme is approved;
- [ ] exact Android OAuth callback host is approved;
- [ ] exact callback URI is recorded;
- [ ] hosted Supabase Google provider configuration is verified;
- [ ] Google Web OAuth client configuration is verified;
- [ ] Supabase callback is registered in Google;
- [ ] Android callback is registered in Supabase Redirect URLs;
- [ ] implementation branch is created from synchronized `main`.

---

## 33. Implementation Principle

The implementation must remain additive and minimal.

The governing rules are:

> Google OAuth launch is not authentication success.

> Supabase authenticated session state is authoritative.

> Same-email authentication must preserve the existing Supabase user UUID.

> Existing email/password authentication must remain unchanged.

> Secrets must remain outside the Android client.

> CR-001 remains independent from Phase 13.
