CR-003 — Complete App Navigation Flow Decisions

Status

Audit Complete — Decisions Pending Approval

CR-003 defines the intended end-to-end Rank-Forge application navigation flow and identifies the smallest remaining navigation and workflow-integration gaps on main.

No implementation work is approved until this decision record is reviewed and explicitly approved.

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
Implementation: NOT STARTED

The next action after approving this document is:

CR-003.1 — require both authoritative result screenshots
before Match OCR Review can be opened

No production code should be changed before this decision record is approved.

Completion Record

To be filled after implementation:

Implementation PR(s):
- TBD

Documentation closure PR:
- TBD

Final merged main SHA:
- TBD

Final status:
- TBD
