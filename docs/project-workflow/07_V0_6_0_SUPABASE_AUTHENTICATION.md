# v0.6.0 Supabase Authentication

## Scope

`v0.6.0` adds the Supabase Authentication foundation only.

Implemented scope:

- Email/password sign-up
- Email/password login
- Logout
- Session restoration
- Authentication loading, success, and error states
- Additive authentication navigation
- Physical-device verification guidance

Not implemented in `v0.6.0`:

- Tournament synchronization
- Cloud tournament or match storage
- Offline sync queue
- WorkManager sync
- Tournament-data RLS policies
- Conflict resolution
- Finalized result protection
- Room replacement or schema changes

## Additive Authentication Model

Authentication is additive in `v0.6.0`.

Local tournament workflows remain available without sign-in. Login success does not upload, claim, reassign, synchronize, or delete existing local tournaments. Logout clears only the Supabase authentication session and must not delete Room tournament data.

Existing tournaments remain local-only until a later version adds explicit backup or synchronization actions.

## Local Configuration

The Android app reads Supabase client values from Gradle properties or `local.properties`.

Use these local-only keys:

```properties
RANK_FORGE_SUPABASE_URL=https://your-project.supabase.co
RANK_FORGE_SUPABASE_PUBLISHABLE_KEY=your-publishable-or-anon-key
```

Do not commit `local.properties`, `.env`, service-role keys, database passwords, JWT secrets, or Supabase secret keys.

If these values are not configured, local tournament workflows still work. Auth actions show a configuration error instead of using placeholder credentials.

## Physical-Device Verification

Before testing on a device:

1. Configure `RANK_FORGE_SUPABASE_URL` and `RANK_FORGE_SUPABASE_PUBLISHABLE_KEY` locally.
2. Confirm the Supabase project has email/password authentication enabled.
3. Install a debug build on a physical Android device.
4. Open the app while signed out and create or open a local tournament.
5. Open Account, sign up with email/password, and confirm email if the project requires it.
6. Log in with email/password.
7. Verify the signed-in account state appears.
8. Log out.
9. Verify local tournaments are still visible and editable after logout.
10. Restart the app and verify session restoration does not block local tournament access.

## v0.6.0.1 Boundary

Follow-up work may harden authentication UX and device verification evidence, but it must remain auth-only unless a later version explicitly approves sync, schema, RLS, ownership, conflict handling, or finalized-result protection.
