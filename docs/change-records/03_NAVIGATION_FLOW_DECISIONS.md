CR-003 — Complete App Navigation Flow Decisions

Status

CR-003 Decisions Approved — CR-003.7 Full Workflow Verification and Closure Authorized

CR-003 defines the intended end-to-end Rank-Forge application navigation flow and identifies the smallest remaining navigation and workflow-integration gaps on main. Slice D, CR-003.1, CR-003.2, CR-003.3, CR-003.4, CR-003.5, and CR-003.6 are complete; CR-003 remains incomplete pending CR-003.7.

Only the approved CR-003.7 verification-and-closure scope recorded below is authorized. No new feature implementation is authorized by this update.

1. Purpose

Rank-Forge currently has 17 registered Navigation Compose destinations covering:

Tournament List

Authentication

Tournament Creation

Tournament Details

Draft Conflict Resolution

Tournament Standings

Team Entry

Roster Entry

Roster Review

Roster Screenshot Crop

Match Creation

Match Placement

Match Kills

Match Review

Match Result Screenshot Crop

Match OCR Review

Match Correction

Roster Screenshot Intake is embedded inside Roster Review and is not a standalone navigation destination.

The purpose of CR-003 is to ensure these existing screens form one complete, deterministic application workflow with:

correct forward navigation;

predictable Back behavior;

safe parent-screen restoration;

correct tournament and match identity propagation;

session restoration behavior;

screenshot/crop/OCR workflow gating;

finalization and correction return paths;

standings and export access;

targeted navigation test coverage;

physical-device verification.

CR-003 is not a redesign of the application.

2. Repository Baseline

CR-003 begins only from a clean synchronized main after CR-002.

Expected CR-002 closure baseline at audit time:

Branch: main
Remote baseline:
bda855b3435681165891b71d8fd40dd44babc779
Merge pull request #241
docs: close CR-002 hosted Supabase deployment

Before creating the CR-003 implementation branch, the local repository must satisfy:

current branch = main
working tree = clean
HEAD = origin/main

If origin/main has advanced beyond the audited SHA, the newer main must be reviewed before implementation begins.

No reset to the older SHA is approved.

3. Navigation Architecture Decision

The existing Navigation Compose architecture remains approved.

CR-003 must not:

replace Navigation Compose;

redesign all destinations;

introduce a second navigation framework;

create unnecessary new screens;

refactor stable navigation merely for stylistic consistency.

Changes must be limited to concrete workflow gaps identified in this record.

Tournament and match identifiers must continue to be carried explicitly through typed navigation destinations.

4. Intended Application Flow

4.1 App Launch and Session Restoration

Approved startup flow:

App Launch
   ↓
Tournament List
   ↓
Auth session restoration runs
   ├─ restored session
   │      ↓
   │  remain on Tournament List as signed in
   │
   └─ no valid session
          ↓
      remain on Tournament List as signed out

Tournament List remains the application start destination.

Authentication is not a mandatory launch gate.

This preserves the current local-first application behavior.

Session restoration must update authentication state without forcing navigation to or from the Authentication destination.

4.2 Interactive Authentication

Approved interactive flow:

Tournament List
      ↓
Authentication
      ├─ Email sign-in
      ├─ Email sign-up
      └─ Google Sign-In
              ↓
         OAuth callback
              ↓
       Authentication success
              ↓
        Tournament List

Decision

Successful interactive authentication must return to Tournament List exactly once.

Session restoration during normal application startup must not emit the same navigation event.

Authentication cancellation, validation failure, network failure, or provider failure must leave the user on the Authentication screen with controlled state.

System Back or explicit Back from Authentication must return to Tournament List.

5. Tournament and Roster Setup Flow

Approved setup flow:

Tournament List
      ↓
Tournament Creation
      ↓
Tournament Details
      ↓
Team Entry
      ↓
Roster Entry
      ↕
Team Entry
      ↓
Roster Review
      ├─ Edit Team
      │      ↓
      │  Team Entry
      │
      ├─ Edit Roster
      │      ↓
      │  Roster Entry
      │
      ├─ Roster Screenshot Intake
      │      ↓
      │  Roster Screenshot Crop
      │      ↓
      │  Roster Review
      │
      └─ Confirm Roster
             ↓
       Tournament Details

5.1 Tournament Creation Back Stack

After successful tournament creation:

Tournament Details
      ↓
Team Entry

Tournament Creation must no longer remain behind Team Entry.

System Back from initial Team Entry must return to the newly created Tournament Details screen.

This existing behavior remains approved.

5.2 Team and Roster Editing

Roster Entry must return to the Team Entry screen from which it was opened.

Roster Review edit actions may open Team Entry or Roster Entry while preserving the same tournament ID.

5.3 Roster Confirmation

Successful roster confirmation must return to the same Tournament Details destination.

The setup workflow must not create duplicate Tournament Details destinations when the correct one already exists in the back stack.

5.4 Roster Screenshot Crop

Cancel and Confirm from Roster Screenshot Crop must both return to the originating Roster Review screen.

Confirmed crop state is persisted independently from navigation.

6. Match Creation and Manual Entry Flow

Approved manual match workflow:

Tournament Details
      ↓
Match Creation
      ↓
Match Placement
      ↓
Match Kills
      ↓
Match Review

Forward progression should remove obsolete one-time entry destinations where already implemented.

Tournament Details remains the stable parent for the match workflow.

6.1 Match Review Editing Paths

Approved paths:

Match Review
   ├─ Edit Placements
   │      ↓
   │  Match Placement
   │      ↓ Back
   │  Match Review
   │
   ├─ Edit Kills
   │      ↓
   │  Match Kills
   │      ↓ Back
   │  Match Review
   │
   ├─ Start Correction
   │      ↓
   │  Match Correction
   │      ↓
   │  Match Review
   │
   └─ Back to Details
          ↓
      Tournament Details

Each path must preserve the same tournamentId and matchId.

7. Match Result Screenshot and OCR Flow

Rank-Forge uses two authoritative match-result screenshots.

The roles are:

MATCH_RESULT_UPPER
MATCH_RESULT_LOWER

Authoritative row boundary:

MATCH_RESULT_UPPER
→ Teams 1–10 only

MATCH_RESULT_LOWER
→ Teams 11–12 only

The partial Team 11 that may appear in the upper screenshot must never compete with or override the lower screenshot.

7.1 Approved Screenshot Workflow

Match Review
   │
   ├─ Screenshot 1 — MATCH_RESULT_UPPER
   │      ↓
   │   Photo Picker
   │      ↓
   │   Validate image
   │      ↓
   │   Duplicate check
   │      ↓
   │   Preserve local image
   │      ↓
   │   Persist screenshot asset
   │      ↓
   │   Upload/sync where available
   │      ↓
   │   Match Result Screenshot Crop
   │      ↓
   │   Confirm crop
   │      ↓
   │   Match Review
   │
   └─ Screenshot 2 — MATCH_RESULT_LOWER
          ↓
       Photo Picker
          ↓
       Validate image
          ↓
       Duplicate check
          ↓
       Preserve local image
          ↓
       Persist screenshot asset
          ↓
       Upload/sync where available
          ↓
       Match Result Screenshot Crop
          ↓
       Confirm crop
          ↓
       Match Review

Cancel from either crop screen must return to the same Match Review.

Confirm must persist the crop before returning.

8. OCR Entry Gate

Current Gap

The current MatchReviewUiState.canOpenOcrReview permits OCR Review when any one result screenshot satisfies the readiness conditions.

That is insufficient for the approved two-screenshot authoritative workflow.

Approved Behavior

OCR Review must be enabled only when both screenshot roles are ready.

Required conditions for MATCH_RESULT_UPPER:

linked asset exists
confirmed crop exists
correct crop profile
local file exists
not busy

Required conditions for MATCH_RESULT_LOWER:

linked asset exists
confirmed crop exists
correct crop profile
local file exists
not busy

Overall gate:

UPPER ready
AND
LOWER ready

Only then may the user enter Match OCR Review.

This is the first approved implementation candidate after CR-003 decisions are accepted.

9. Match OCR Review Flow

Approved OCR workflow:

Match Review
      ↓
Match OCR Review
      ↓
OCR evidence review
      ↓
Manual corrections where required
      ↓
Validation
      ↓
Finalization

Back from Match OCR Review must return to the same Match Review.

If Match Review is not already in the back stack, navigation may recreate the correct Match Review using the same tournament and match identity.

9.1 OCR Finalization

Approved post-finalization behavior:

Match OCR Review
      ↓
Finalize
      ↓
Show finalization result
      ↓
Back
      ↓
Finalized Match Review

CR-003 does not approve automatic redirection directly to Tournament Details or Standings immediately after OCR finalization.

The finalized Match Review remains the authoritative inspection point.

10. Manual Match Finalization

Approved flow:

Match Review
      ↓
Finalize confirmation
      ↓
Finalized Match Review

Finalized Match Review is read-only except through the protected Match Correction workflow.

Placement and kill entry navigation must not remain available on finalized matches.

11. Match Correction Flow

Approved correction flow:

Finalized Match Review
      ↓
Start Correction
      ↓
Match Correction
      ├─ Submit
      │     ↓
      │ Match Review
      │
      ├─ Discard
      │     ↓
      │ Match Review
      │
      └─ Back
            ↓
        Match Review

Correction drafts may persist independently through repository-backed draft storage.

System Back must not bypass an in-progress protected submission.

Successful correction must return to the same finalized Match Review and display the updated corrected result and correction history.

12. Tournament Standings Flow

Approved flow:

Tournament Details
      ↓
Tournament Standings
      ↓ Back
Tournament Details

Standings remain based only on finalized matches.

Tournament Standings is currently a display destination.

No CR-003 requirement exists to duplicate export actions onto the Standings screen.

13. Export Flow

13.1 Tournament Export

Tournament export is already integrated from Tournament Details.

Approved available actions:

Tournament Details
   ├─ Prepare Tournament/Standings CSV
   └─ Export Tournament Standings to Google Sheets

CR-003 must preserve this behavior.

Moving those controls to Tournament Standings is out of scope unless a separate usability requirement is approved.

13.2 Match CSV Export

Finalized Match Review already supports match CSV preparation.

Approved location:

Finalized Match Review
      ↓
Prepare Match CSV

This remains unchanged unless tests expose a defect.

13.3 Match Google Sheets Export Gap

The application currently has incomplete match-level Google Sheets integration.

The Match Review ViewModel contains a Google Sheets preparation method, but the current behavior reports match export as unavailable and the screen does not expose a working hosted match-export action.

The hosted backend supports authorized match export.

Decision

Match Google Sheets integration is a valid CR-003 workflow gap, but it must not be mixed with the first OCR-gating navigation slice.

It should be implemented as a later independent slice after the core navigation and screenshot/OCR gates are verified.

14. Back-Button Contract

The following Back behavior is approved.

Current screen

Expected Back destination

Authentication

Tournament List

Tournament Creation

Tournament List, subject to dirty-state confirmation

Tournament Details

Tournament List

Draft Conflict Resolution

Previous Tournament Details context

Tournament Standings

Tournament Details

Team Entry

Tournament Details

Roster Entry

Team Entry

Roster Review

Team Entry

Roster Screenshot Crop

Roster Review

Match Creation

Tournament Details

Match Placement opened from creation flow

Tournament Details after forward flow replacement

Match Placement opened from Review

Match Review

Match Kills opened from forward flow

Tournament Details after forward flow replacement

Match Kills opened from Review

Match Review

Match Review

Tournament Details

Match Result Screenshot Crop

Match Review

Match OCR Review

Match Review

Match Correction

Match Review

Where a required logical parent is unexpectedly absent from the current back stack, the application may recreate the correct parent using the same route identity rather than exiting the app or navigating to an unrelated screen.

15. Saved-State and Restoration Decision

CR-003 distinguishes persisted application state from transient UI state.

15.1 Persisted State

The following must remain repository/session-backed where already implemented:

tournaments;

rosters;

matches;

match draft values;

correction drafts;

screenshot metadata;

match-result screenshot assets;

confirmed crop metadata;

authentication session;

finalized results;

correction history.

15.2 Transient State

Not every incomplete UI edit is required to survive full process death in CR-003.

Examples include:

unsubmitted Tournament Creation field edits;

an unconfirmed visual crop adjustment;

transient dialog visibility;

temporary navigation events.

CR-003 does not approve a broad SavedStateHandle refactor.

15.3 Required Restoration Verification

Navigation and persisted workflow context must be tested across activity recreation.

At minimum verify that route identity and persisted domain state remain correct after recreation.

If activity recreation exposes a concrete user-visible workflow break, fix only the smallest affected state boundary.

16. Read-Only Audit Findings

Already Connected

The audit confirmed the following existing flows are substantially connected:

Tournament List → Tournament Creation;

Tournament Creation → Tournament Details → Team Entry;

Team Entry ↔ Roster Entry;

Team Entry → Roster Review;

Roster Review → Tournament Details after confirmation;

Roster Review ↔ Roster Screenshot Crop;

Tournament Details → Match Creation;

Match Creation → Placement → Kills → Review;

Match Review → Placement;

Match Review → Kills;

Match Review → Details;

Match Review → OCR Review;

Match Review → Match Correction;

Match Review ↔ Match Result Screenshot Crop;

Match Correction → Match Review;

Tournament Details → Standings → Details;

finalized Match Review → correction workflow;

Tournament Details → tournament CSV export;

Tournament Details → tournament Google Sheets export;

finalized Match Review → match CSV preparation.

17. Confirmed Navigation / Integration Gaps

P0 — OCR Readiness Gate

Current behavior allows OCR Review with only one ready result screenshot.

Required behavior is both authoritative screenshot roles ready.

P0 — Authentication Success Return

Interactive sign-in/sign-up currently does not automatically close Authentication and return to Tournament List.

Session restoration must remain non-navigational.

P0 — Result Screenshot Crop Navigation Coverage

Existing navigation tests do not sufficiently cover both match-result screenshot crop destinations and their Cancel/Confirm return behavior.

P0 — Activity Recreation / Navigation Restoration Coverage

Existing navigation integration tests do not verify destination/back-stack behavior across activity recreation.

P1 — Match Google Sheets Export

Match-level Google Sheets export remains incomplete at the application UI/ViewModel integration boundary.

P1 — Parent Fallback Hardening

Any additional missing logical-parent fallback discovered during new navigation tests may be fixed only where the test proves an actual broken workflow.

No speculative navigation refactor is approved.

18. Approved Implementation Sequence

Implementation must occur one small slice at a time.

CR-003.1 — OCR Entry Gate

Change only the smallest logic required so Match OCR Review requires both authoritative result screenshots to be fully ready.

Required verification:

unit tests;

focused Compose test;

existing relevant tests.

CR-003.2 — Match Result Crop Navigation Coverage

Add navigation coverage for:

Match Review
→ Upper Crop
→ Cancel/Confirm
→ same Match Review

Match Review
→ Lower Crop
→ Cancel/Confirm
→ same Match Review

Avoid unrelated production changes.

CR-003.3 — Authentication Return Flow

Implement interactive authentication success returning to Tournament List exactly once.

Must distinguish:

interactive auth success

from:

startup session restoration

Required device coverage includes Google Sign-In callback and cancellation.

CR-003.4 — Recreation and Back-Stack Verification

Add the smallest tests needed to validate:

current destination restoration;

route argument preservation;

same tournament identity;

same match identity;

persisted workflow data still available.

Production changes are allowed only if these tests expose a real defect.

CR-003.5 — Remaining Parent Navigation Fixes

Address only specific parent/back-stack failures proven by testing.

No broad refactor.

CR-003.6 — Match Google Sheets Export

Connect finalized Match Review to the already deployed hosted match-export capability.

Keep this separate from the navigation core.

CR-003.7 — Full Workflow Verification and Closure

Run full required verification on the final CR-003 implementation.

After success:

merge implementation PR;

mark CR-003 Complete;

reference implementation PR in this record;

merge documentation closure update;

synchronize local main;

confirm clean working tree.

19. Required Unit Tests

At minimum:

OCR gate

no screenshots ready → OCR disabled;

only Upper ready → disabled;

only Lower ready → disabled;

both ready → enabled;

Upper local file missing → disabled;

Lower local file missing → disabled;

Upper busy → disabled;

Lower busy → disabled;

wrong or missing crop profile → disabled.

Authentication navigation state

startup restored session does not trigger interactive-auth navigation;

successful interactive email sign-in triggers one return event;

successful interactive Google authentication triggers one return event;

navigation event is cleared after handling;

failed authentication does not navigate;

cancellation does not navigate.

Correction/back state

Retain regression coverage that correction submit, discard, and Back preserve the correct match context.

20. Required Compose Navigation Tests

At minimum:

Tournament List
→ Authentication
→ Back
→ Tournament List

Tournament List
→ Authentication
→ successful interactive sign-in
→ Tournament List

Tournament Creation
→ successful create
→ Team Entry
→ Back
→ created Tournament Details

Roster Review
→ Roster Screenshot Crop
→ Back
→ same Roster Review

Match Review
→ Upper Result Screenshot Crop
→ Cancel
→ same Match Review

Match Review
→ Upper Result Screenshot Crop
→ Confirm
→ same Match Review

Match Review
→ Lower Result Screenshot Crop
→ Cancel
→ same Match Review

Match Review
→ Lower Result Screenshot Crop
→ Confirm
→ same Match Review

only Upper ready
→ OCR action disabled

only Lower ready
→ OCR action disabled

both ready
→ OCR action enabled
→ Match OCR Review
→ Back
→ same Match Review

Finalized Match Review
→ Match Correction
→ Submit
→ same Match Review

Tournament Details
→ Standings
→ Back
→ same Tournament Details

Activity recreation coverage must verify route arguments and the active logical destination.

21. Required Physical-Device Verification

Use the approved physical Android device.

At minimum verify:

Authentication

cold launch with signed-out session;

cold launch with restored authenticated session;

email sign-in;

Google Sign-In;

Google provider/browser callback;

Google cancellation/back;

logout;

force-stop and relaunch;

no duplicate navigation after callback/restoration.

Tournament setup

create tournament;

automatically enter setup;

Back returns to same tournament;

edit team;

edit roster;

roster review;

roster screenshot crop;

roster confirmation;

return to same tournament details.

Match workflow

create match;

manual placement;

kills;

review;

Back/edit paths;

both match-result screenshots selected independently;

both crop screens;

confirmed crops restored after returning;

OCR unavailable until both roles are ready;

OCR becomes available when both roles are ready;

OCR Review returns to same match;

finalization;

finalized read-only behavior;

correction workflow;

standings.

Export

finalized match CSV;

tournament standings CSV;

tournament Google Sheets export;

match Google Sheets export after CR-003.6 implementation;

authenticated hosted backend behavior.

22. Acceptance Criteria

CR-003 may be marked Complete only when all of the following are true:

local implementation started from clean synchronized main;

all intended workflow decisions in this document are approved;

no unnecessary new navigation destinations were added;

interactive authentication success returns to Tournament List;

startup session restoration does not cause duplicate navigation;

tournament setup has deterministic parent/back behavior;

roster crop returns correctly to Roster Review;

manual match flow remains intact;

Upper and Lower result screenshot roles remain independent;

OCR Review requires both authoritative screenshot roles to be ready;

both match-result crop routes return to the same Match Review;

OCR Review returns to the correct Match Review;

correction returns to the correct Match Review;

finalized protection remains intact;

standings return to the correct Tournament Details;

existing tournament export remains intact;

existing match CSV export remains intact;

match Google Sheets integration is completed or explicitly moved to a separate approved change record;

required unit tests pass;

required Compose navigation tests pass;

physical-device workflow verification passes;

full required project verification passes;

final diff contains only approved CR-003 scope;

implementation PR is merged;

this document references the merged implementation PR;

CR-003 is marked Complete;

repository is returned to clean synchronized main.

23. Out of Scope

CR-003 does not include:

scoring algorithm changes;

OCR recognition/parser algorithm redesign;

team matching algorithm changes;

database schema redesign;

Room migration changes unless a navigation-restoration defect absolutely requires one;

Supabase RLS redesign;

Supabase migration changes unrelated to the approved navigation slices;

Google Sign-In provider reconfiguration;

full application visual redesign;

replacing Navigation Compose;

adding unnecessary destinations;

broad SavedStateHandle adoption;

Phase 13 beta work;

changes to the checkpointed OCR branch;

unrelated refactoring;

performance optimization unrelated to navigation correctness.

24. Security and Data Boundaries

CR-003 must preserve:

authenticated ownership boundaries;

existing Supabase RLS;

local-first persisted tournament data;

finalized-match immutability;

protected correction flow;

screenshot role identity;

screenshot Storage ownership/correlation;

crop metadata ownership;

cloud revision behavior;

export authorization;

OAuth callback validation.

Navigation must never be used to bypass domain authorization or finalized-data protection.

A destination being reachable does not imply that its operation is authorized.

25. Rollback Plan

Each implementation slice must remain small enough to revert independently.

Preferred rollback:

revert the specific CR-003 implementation commit or PR

Rollback must not:

reset the database;

delete tournament data;

delete Auth users;

rewrite migration history;

weaken RLS;

delete screenshot assets;

force-reset shared main.

If a navigation slice causes regression:

stop further CR-003 implementation;

identify the exact failing slice;

revert that slice only;

rerun focused navigation tests;

rerun required regression tests;

resume only after the previous stable workflow is restored.

26. Documentation and Git Workflow

Approved CR-003 workflow:

sync main
→ read-only navigation audit
→ approve this decision record
→ create implementation branch
→ implement one small slice
→ focused unit tests
→ focused Compose navigation tests
→ device verification where required
→ scope/diff audit
→ commit
→ push
→ PR
→ review
→ merge
→ next slice

After all slices:

full verification
→ final implementation PR merged
→ update CR-003 status to Complete
→ reference implementation PR(s)
→ merge documentation closure
→ sync main
→ confirm clean working tree

Do not merge a slice before its required verification passes.

27. Current Decision Gate

Current state:

Read-only audit: COMPLETE
Intended flow: DEFINED
Navigation gaps: IDENTIFIED
CR-003 Slice D implementation PR: #277 — MERGED
CR-003 Slice D status: COMPLETE
CR-003.1 implementation PR: #279 — MERGED
CR-003.1 status: COMPLETE
CR-003.2 implementation/test PR: #281 — MERGED
CR-003.2 status: COMPLETE
CR-003.3 implementation/test PR: #283 — MERGED
CR-003.3 status: COMPLETE
CR-003.4 implementation/test PR: #285 — MERGED
CR-003.4 status: COMPLETE
CR-003.5 implementation/test PR: #287 — MERGED
CR-003.5 status: COMPLETE
CR-003.6 implementation PR: #289 — MERGED
CR-003.6 status: COMPLETE
CR-003 status: INCOMPLETE

Slice D verification:

- Offline Match 3 parent-first screenshot recovery was verified.
- Lobby Screenshot 1 and Result Upper automatically recovered after reconnect.
- Hosted metadata reached `UPLOADED`.
- Corresponding Storage objects were present.
- No duplicate child rows were observed.

CR-003.1 verification:

- Connected `MatchReviewScreenTest`: PASS.
- Both `MATCH_RESULT_UPPER` and `MATCH_RESULT_LOWER` are required before OCR Review.
- Local-first behavior was preserved.
- Full JVM: 1174 tests, 57 established failures in the same 9 baseline classes.
- `assembleDebug`: PASS.
- `assembleDebugAndroidTest`: PASS.

CR-003.2 verification:

- Upper Result Crop → Cancel → same Match Review: PASS.
- Lower Result Crop → Cancel → same Match Review: PASS.
- Upper Result Crop → Confirm → same Match Review: PASS.
- Lower Result Crop → Confirm → same Match Review: PASS.
- Exact `tournamentId`, `matchId`, and Upper/Lower role were preserved.
- Confirmed crop persisted for the correct role and the opposite role remained unchanged.
- Connected physical-device verification passed for all four focused cases.
- `assembleDebugAndroidTest`: PASS.
- `git diff --check`: PASS.
- Production changes: NONE; implementation was test-only.

CR-003.3 verification:

- `AuthViewModelTest`: 25/25 PASS.
- Nine focused `AuthNavigationTest` connected tests: PASS.
- `assembleDebugAndroidTest`: PASS.
- Real email/password login: PASS.
- Real Google OAuth callback: PASS.
- Google browser Back/cancel: PASS.
- Existing state-driven authentication architecture was retained.
- Production changes: NONE.

CR-003.4 verification:

- Genuine `ActivityScenario.recreate()` coverage was added.
- Tournament Details retained the exact `tournamentId`.
- Match Review retained the exact `tournamentId` and `matchId`.
- Persisted Room workflow data survived recreation.
- Logical Back destinations were preserved.
- Both focused tests passed on physical device I2019 - 14.
- No production navigation defect was found.
- No `app/src/main` production changes were required.

CR-003.5 verification:

- Finalized Match Review → Match Correction → system Back → same Match Review: PASS on physical device I2019 - 14.
- Exact `tournamentId` and `matchId` were preserved.
- Finalized match context was preserved.
- Tournament Details → Tournament Standings → Back → same Tournament Details: PASS on physical device I2019 - 14.
- Exact `tournamentId` was preserved and no production navigation defect was found.
- Production changes: NONE.

The current gate is:

CR-003.6 — Match Google Sheets Export implementation authorized

Historical CR-003.6 read-only audit findings:

1. Hosted backend capability already exists.

The deployed `supabase/functions/google-sheets-export` supports
`operation = "export_match"` with `tournament_id`, `match_id`, and exactly 12
match-result rows. It authenticates the Supabase user, validates tournament and
match visibility, reads authoritative hosted match results, team slots, and
players, validates the submitted payload, uses the existing export-operation
idempotency mechanism, writes exactly 12 Match Results rows, verifies the
Google write, and returns success or failure. No Edge Function redesign is
authorized unless implementation testing proves a backend defect.

2. Android contains partial match-export scaffolding.

`AndroidExportType.MATCH_GOOGLE_SHEETS` and the result states
`GoogleSheetsExporting`, `GoogleSheetsSuccess`, and `GoogleSheetsFailure`
already exist. `AndroidExportCoordinator` currently exposes only
`googleSheetsMatchUnavailable(...)`; match-specific exporting, success, and
failure coordinator methods are not yet present.

3. `MatchReviewUiState.googleSheetsExportResult` and
`MatchReviewViewModel.prepareGoogleSheetsExport()` already exist, but the
current ViewModel behavior returns `GOOGLE_SHEETS_CLIENT_NOT_CONFIGURED`
instead of invoking the deployed hosted match-export capability.

4. `GoogleSheetsExportDataModule` binds only
`GoogleSheetsStandingsExportRemoteDataSource`; no equivalent match remote data
source binding exists.

5. Tournament standings export is the approved Android precedent:
validated typed rows → authenticated remote data source → existing
`google-sheets-export` Edge Function → exporting/success/failure state.
`MatchCsvExporter` owns the authoritative finalized-match export validation and
20-column schema and should be reused rather than duplicating scoring or
validation rules.

Approved CR-003.6 behavior:

For a valid finalized Match Review:

Match Google Sheets export request
→ build exactly 12 approved match-export rows
→ call the existing authenticated `google-sheets-export` Edge Function with
  `operation = "export_match"`
→ hosted authoritative-data validation
→ Google Match Results export
→ verified success or mapped failure.

Successful results must preserve the same `tournamentId` and `matchId` and
confirm exactly 12 rows written.

Export eligibility requires nonblank valid context, an existing tournament and
match, `FINALIZED` match status, valid finalized-match export data, and exactly
12 valid rows. Draft matches and invalid finalized data must be blocked locally
and must not call the remote exporter.

Required row fields are the existing Phase 10 schema:

`export_schema_version`, `export_type`, `tournament_id`, `tournament_name`,
`match_id`, `match_label`, `match_finalized_at`, `row_number`, `placement`,
`team_slot`, `team_name`, `player_1_name`, `player_2_name`, `player_3_name`,
`player_4_name`, `placement_points`, `kills`, `kill_points`, `total_points`,
and `correction_status`.

Authorize the smallest Android match remote data source equivalent to the
existing standings data source. Reuse `SupabaseAuthConfig`,
`SupabaseAccessTokenProvider`, `GoogleSheetsExportHttpTransport`, the existing
endpoint, and existing failure mapping. Requests must contain the exact
`tournament_id`, exact `match_id`, and exactly 12 rows. Success is valid only
when the response confirms `ok = true`, `operation = "export_match"`, matching
IDs where supplied, and `rows_written = 12`.

Match Review must reject missing context, draft/non-finalized matches, and
invalid finalized data locally; expose `GoogleSheetsExporting`; execute one
request; map success to `GoogleSheetsSuccess`; map failures to
`GoogleSheetsFailure`; preserve the result in
`MatchReviewUiState.googleSheetsExportResult`; and prevent uncontrolled
duplicate concurrent requests. Add only the smallest action/status integration
to the retained finalized Match Review export surface. Do not re-enable hidden
legacy/manual controls in normal simplified navigation.

Preserve Supabase authentication, RLS, authoritative hosted-data validation,
export idempotency, and Google write verification. Do not add direct Google
credentials to Android. No new migration, RLS, Storage, or Google credential
architecture is authorized without a concrete blocker.

Required focused tests cover the remote request/response and failure cases,
ViewModel eligibility/exporting/success/failure/exact-ID/concurrency behavior,
and Compose finalized-only action/status behavior. Before closing CR-003.6,
verify on physical device I2019 - 14 against hosted Supabase that an
authenticated finalized match exports exactly 12 Match Results rows with no
duplicate write and a visible success state.

Out of scope: navigation destinations, authentication flow, screenshot
recovery, OCR, scoring, finalization or correction semantics, Tournament
Google Sheets behavior except safe shared reuse, Room schema/migrations,
Supabase schema/RLS, Edge Function changes without a proven blocker, Phase 13,
and checkpointed OCR work.

CR-003.6 implementation and verification:

- Implementation PR #289 was merged with the authorized 14-file Android
  production/test scope: `MatchReviewScreenTest.kt`,
  `GoogleSheetsExportDataModule.kt`, `AndroidExportCoordinator.kt`,
  `MatchCsvExporter.kt`, `MatchOcrReviewViewModel.kt`, `MatchReviewScreen.kt`,
  `MatchReviewViewModel.kt`, `AndroidExportCoordinatorTest.kt`,
  `MatchCsvExporterTest.kt`, `MatchOcrReviewViewModelTest.kt`,
  `MatchReviewViewModelTest.kt`, `SupabaseGoogleSheetsMatchExport.kt`,
  `MatchExportRow.kt`, and `SupabaseGoogleSheetsMatchExportTest.kt`.
- Focused CR-003.6 JVM tests: PASS; `MatchReviewViewModelTest`: 52 tests PASS.
- Full JVM: 1196 tests, 57 known baseline failures in the same established 9
  failing classes, with no new failing class.
- `assembleDebug`: PASS.
- `assembleDebugAndroidTest`: PASS.
- Verification tournament: `f1e7a9b6-0543-4786-a328-fe927ca90814`.
- Local Match 2 ID: `2c7ed56f-e9e3-44b3-a830-0b9ef0866438`.
- Hosted Match 2 ID: `152837b7-65f3-3b03-a797-113848cbbf6d`.
- Existing `MatchCloudIdentity` mapping from local to hosted Match 2 was
  verified. The hosted match was finalized with 12 authoritative
  `match_results` rows.
- Android Google Sheets export succeeded; the Edge Function returned HTTP
  200 for `export_match`; the export operation was `succeeded` with
  `attempt_count = 1` and `rows_written = 12`.
- Exactly one export operation was observed and no duplicate write occurred.
- No backend, schema, migration, or Room change was required.
- The earlier failed export sent the local Match ID directly. The correction
  reused `MatchCloudIdentity` and was physically verified before merge.

The current gate is:

CR-003.7 — Full Workflow Verification and Closure AUTHORIZED

CR-003.7 is verification-first/test-first. No speculative refactor is
authorized. Production changes are allowed only if final verification proves
a concrete CR-003 regression or workflow defect.

CR-003.7 final verification contract:

A. Repository baseline

- Branch `main`.
- Clean working tree.
- HEAD synchronized with `origin/main` and includes PR #289.

B. JVM verification

Run `testDebugUnitTest`. The accepted baseline is exactly 1196 tests with 57
failures, all within these established classes:

1. `MatchRepositoryTest`
2. `FinalizeMatchUseCaseTest`
3. `FinalizeOcrCorrectionMatchUseCaseTest`
4. `MatchCorrectionUseCaseTest`
5. `SaveMatchKillsUseCaseTest`
6. `SaveMatchPlacementsUseCaseTest`
7. `MatchCorrectionViewModelTest`
8. `MatchKillViewModelTest`
9. `TournamentStandingsViewModelTest`

Additional failures, a new failing class, or a CR-003-specific regression
must not be treated as baseline.

C. Build verification

- `assembleDebug`.
- `assembleDebugAndroidTest`.
- `git diff --check`.

D. Connected/device CR-003 regression coverage

Verify the approved workflow remains intact, including:

- Authentication startup/session behavior, interactive return, Google
  callback, and Google cancellation.
- Tournament creation, Team Entry parent behavior, and Tournament Details
  identity preservation.
- Match creation, exact `tournamentId`/`matchId` propagation, and Match
  Review parent behavior.
- Upper and Lower Result Screenshot crop Cancel and Confirm flows, same Match
  Review return, and independent roles.
- OCR Review entry requiring both authoritative result screenshot roles.
- Tournament Details and Match Review activity recreation with route IDs and
  persisted data preserved.
- Match Correction → system Back → same finalized Match Review.
- Standings → Back → same Tournament Details.
- Simplified finalized Match Review retaining hidden legacy
  Placement/Kill/Finalize/Correction controls and exposing Google Sheets
  export for a valid finalized match.

E. Physical end-to-end smoke workflow

From final `main`, perform one real-user workflow on the physical device:

Authentication/session → Home/Tournament List → Create Tournament → Team
setup → Tournament Details → Calculate Points/Match Review → Lobby screenshots
→ Result Upper and crop → Result Lower and crop → OCR Review → finalization
→ Finalized Match Review → Google Sheets export → Back to the same Tournament
Details.

Local-first behavior remains required; cloud confirmation must not become a
navigation gate.

F. Hosted checks for the smoke workflow

Where applicable verify stable tournament and hosted match identities, a
finalized hosted match with 12 authoritative result rows, successful Match
Google Sheets export, exactly one successful export operation for the
verification action, and `rows_written = 12`. Do not weaken RLS or Storage
policies.

Closure rule:

CR-003 may be marked COMPLETE only after CR-003.7 verification passes. If a
real defect is found, CR-003 remains incomplete and only the smallest exact
correction may be authorized and reverified. If verification passes, record
the final evidence, reference all relevant implementation/test PRs, merge the
documentation closure update, and return the repository to clean synchronized
`main`.

28. Approved Offline-First and Slice D Decisions

28.1 Global Offline-First Navigation Rule

- Successful LOCAL persistence is the navigation gate.
- Immediate Supabase confirmation must never block tournament, team, match, screenshot, crop, or review navigation.
- Failed cloud operations preserve local state and recover automatically when connectivity and authentication become available.
- Stable tournament and match IDs must be preserved.
- Retries must be idempotent and must not create duplicates.

28.2 Verified Slice A — Tournament Creation

- Offline tournament creation persists locally and navigation continues to Enter Teams.
- The existing `TOURNAMENT_UPLOAD` foreground retry uploads the same tournament after connectivity returns.
- Verification found no duplicate tournament.

28.3 Verified Slice B — Team Names

- Team names persist locally offline and the local workflow continues.
- Existing tournament snapshot retry uploads the latest team-slot names after reconnect.
- Verification found all 12 slots and no duplicate tournament.

28.4 Verified Slice C — Calculate Points

- Calculate Points creates the next match locally before cloud sync.
- `DRAFT_MATCH_SYNC` failure does not block Match Review.
- Existing foreground queue retry uploads the same match after reconnect.
- Verification found no duplicate Match 1.

28.5 Slice D Audit Finding — Screenshot Recovery Gap

- Lobby and match-result screenshots are preserved locally before cloud upload.
- Confirmed crops remain local when Storage or cloud metadata upload fails, so the local screenshot/crop workflow continues offline.
- Physical-device verification found that offline Match 2 later uploaded automatically, but offline-confirmed Lobby Screenshot 1 and Result Screenshot Upper did not upload after reconnect; no corresponding hosted metadata rows or Storage objects appeared.
- The existing persistent `SyncQueueOperationType` does not include screenshot upload operations.

28.6 Approved Slice D Behavior

On foreground connectivity restoration:

a. Existing parent tournament/match queue recovery runs first.
b. Eligible locally persisted screenshot assets are then retried.
c. Screenshot retry preserves the existing `tournamentId`, `matchId`, Lobby index or result role, fingerprint, crop metadata, and local file.
d. Only the correct existing parent match may receive the screenshot.
e. Successful retry uploads the Storage object and upserts the corresponding cloud metadata.
f. Failures remain local and retryable and never interrupt navigation.
g. Retries are idempotent and must not create duplicate Storage or metadata records.

28.7 Slice D Scope Boundary

- Scope is limited to Lobby screenshots and match-result screenshots.
- Reuse the existing foreground connectivity mechanism.
- Reuse existing screenshot Storage uploaders, cloud data sources, local repositories, and upload checkpoint behavior where practical.
- Do not add a new background worker.
- Do not redesign Room, Supabase schema, RLS, Storage policies, tournament/match revisions, OCR, scoring, or navigation.
- Parent recovery must occur before screenshot-child recovery.

Completion Record

Implementation PR(s):
- #277 — CR-003 Slice D automatic offline screenshot recovery (merged)
- #279 — CR-003.1 OCR Entry Gate (merged)
- #281 — CR-003.2 Match Result Crop Navigation Coverage (merged)
- #283 — CR-003.3 Authentication Return Flow (merged)
- #285 — CR-003.4 Recreation and Back-Stack Verification (merged)
- #287 — CR-003.5 Remaining Parent Navigation Fixes (merged)
- #289 — CR-003.6 Match Google Sheets Export (merged)

Documentation closure PR:
- TBD

Final merged main SHA:
- TBD

Final status:
- CR-003 incomplete pending CR-003.7; Slice D, CR-003.1, CR-003.2, CR-003.3,
  CR-003.4, CR-003.5, and CR-003.6 Match Google Sheets Export complete;
  CR-003.7 Full Workflow Verification and Closure authorized.
