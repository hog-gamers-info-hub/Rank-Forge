# CR-006 — Shareable Tournament Standings Decisions

## Status

**Phase 1 and Phase 2 implemented locally — hosted deployment and end-to-end verification pending.**

## Purpose

Allow a PointIQ tournament organizer to eventually share one URL that anyone with the URL can open in a normal browser to view Tournament Standings.

## Scope of Phase 1

Phase 1 establishes only the Supabase/browser foundation.

It will later support:
authenticated Android user
-> publish prepared standings snapshot
-> obtain opaque share token
-> public browser URL
-> render read-only standings.

Android Share UI/integration is NOT part of Phase 1.

## Architecture

Use Supabase as the public JSON backend and GitHub Pages as the static browser
viewer.

The browser-hosting flow is locked as:
Android
-> GitHub Pages share URL
-> GitHub Pages standings viewer
-> public Supabase Edge Function JSON
-> stored standings snapshot

The hosted Supabase function domain serves the previous HTML response as
text/plain in production, so HTML/CSS rendering is hosted by the static
GitHub Pages viewer instead. Use:
1. one dedicated standings-share database table
2. one Supabase Edge Function returning validated JSON
3. one self-contained static viewer under web/standings/

## Authoritative standings data

The browser must not calculate tournament standings.

The existing PointIQ Android/domain standings pipeline remains authoritative:
CumulativeTournamentStandingsEngine
-> TieBreakRules
-> TournamentStandingRowUiState

The published snapshot must represent the already prepared standings output.

Do not duplicate placement scoring, kill scoring, cumulative scoring, finalized-match filtering, or tie-break rules in TypeScript or SQL.

## Published row contract

The snapshot rows must be capable of representing:

- displayOrder
- teamSlotNumber
- teamName
- totalPoints
- totalPositionPoints
- totalKillPoints
- firstPlaceFinishes
- latestMatchPlacement
- matchesIncluded
- isCompleteTie

## Share record

Use one share record per tournament.

The implementation should use a dedicated table named:

public.tournament_standings_shares

The exact migration implementation may include only fields required for:

- tournament ownership/linkage
- opaque public token
- standings snapshot payload
- created/updated timestamps

Do not add analytics, view counts, expiry, custom titles, QR codes, social previews, permissions systems, multiple links per tournament, or other optional features.

## Public token

The URL must use a cryptographically strong, high-entropy, unguessable token.

Do not expose the tournament UUID as the public access credential.

The token grants read-only access only to the corresponding published standings snapshot.

## Database security

Enable RLS on the new table.

Anonymous browser clients must not receive direct broad table access.

Existing tournaments, matches, match_results, tournament_team_slots, players, OCR data, screenshots, owner IDs, sync metadata, and other private data must remain protected by their existing access rules.

The public browser path must return only the published standings presentation data.

Do not loosen any existing RLS policy.

## Edge Function

Use exactly one new Edge Function for the public standings browser page.

Suggested slug:

public-tournament-standings

It is intentionally public and therefore may use verify_jwt = false.

The function must validate the opaque share token before returning any standings content.

Invalid or unknown tokens must return a simple not-found response and must not reveal whether a tournament exists.

The function returns validated JSON for the static GitHub Pages viewer; it does
not render HTML.

No JavaScript framework is required.

## Browser UI

Reproduce the existing TournamentStandingsScreen presentation as closely as practical using simple HTML and CSS.

Show only the standings page.

Include:
- Tournament standings heading
- finalized-only informational message
- ranking cards/rows
- team name or team-slot fallback
- Kill points
- Position points
- Total points
- 1st place finishes
- Latest placement
- Matches included
- complete-tie message when applicable

Preserve special visual treatment for ranks 1, 2, and 3.

Do not add browser Back navigation, menus, account controls, filtering, search, editing, or additional pages.

## Out of scope

Explicitly exclude:

- Android Share button/action
- Android networking/publication integration
- React/Vite/Next.js
- standalone website
- web login
- web account functionality
- web tournament management
- realtime updates
- polling
- analytics
- view counters
- link expiration
- QR codes
- Open Graph/social previews
- multiple links per tournament
- manual web editing
- scoring or tie-break implementation
- OCR changes
- screenshot changes
- existing database/RLS redesign
- custom domain setup

## Phase 1 completion boundary

Phase 1 is complete only when the later implementation can prove:

1. a valid published snapshot exists for test data
2. an opaque token can resolve exactly that snapshot
3. the public Edge Function renders the standings in a browser
4. an invalid token does not expose data
5. anonymous callers cannot directly browse private tournament tables
6. existing Android scoring and standings behavior is unchanged

## Future Phase 2

Phase 2 will add the minimal Android integration:
- one Share Standings action
- publish/update the current prepared standings snapshot
- obtain/reuse the share URL
- open the Android system share sheet

Do not design or implement Phase 2 in this decision task.
