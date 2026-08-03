# v0.9.10 — Roster OCR Review and Correction Decisions

## 1. Status

Approved decision gate for the Phase 9 roster OCR extension.

The canonical roadmap currently identifies this work as:

`v0.9.x — Roster OCR Review and Correction`

Because the existing numbered Phase 9 sequence ends at v0.9.9, this decision assigns the concrete version:

**v0.9.10 — Roster OCR Review and Correction**

This document is documentation only. It does not authorize implementation until reviewed, merged, and explicitly approved.

## 2. Purpose

v0.9.10 completes the operator-facing handoff from the already implemented roster screenshot/OCR candidate pipeline into safe roster review, correction, abandonment, and explicit confirmed-roster replacement.

The version must preserve the central product rule:

**Roster OCR output is candidate evidence only and must never silently become confirmed roster data.**

## 3. Completed prerequisites

The following prerequisites are complete before v0.9.10:

### Phase 7

- v0.7.7 — Roster Screenshot Intake
- v0.7.8 — Roster Screenshot Crop Preparation
- v0.7.9 — Roster Screenshot Set Association

These provide:

- exactly three ordered tournament-scoped roster screenshots
- private local original-image preservation
- validation and duplicate protection
- normalized operator-selected crop metadata
- restore behavior
- separation from match screenshots

### Phase 8

- v0.8.9 — Cropped Roster Layout Definition
- v0.8.10 — Roster Raw OCR Extraction
- v0.8.11 — Roster Team and Player Parsing
- v0.8.12 — Roster Slot Association
- v0.8.13 — Roster OCR Validation

These provide:

- prepared cropped-panel layout validation
- roster-specific ML Kit raw OCR
- candidate player parsing
- deterministic screenshot/visible-slot to tournament-slot association
- candidate validation and review severity/status

### Phase 5

- v0.5.8 — Atomic Confirmed Roster Replacement

This provides safe local 12-slot replacement after explicit confirmation.

### Phase 6

- v0.6.9 — Revision-Safe Roster Sync Replacement

This provides safe cloud synchronization of the already-confirmed local replacement.

## 4. Current missing seam

The completed components exist independently but are not currently connected into one roster OCR review workflow.

There is currently no production:

- roster OCR review ViewModel
- roster OCR review screen
- roster OCR processing/orchestration use case
- runtime handoff from preserved screenshot plus crop metadata into the roster raw extractor
- candidate correction state
- candidate abandonment action
- explicit OCR-roster replacement confirmation flow

The existing manual roster review workflow must not be repurposed in a way that destroys its current behavior.

## 5. Processing orchestration decision

v0.9.10 may add the minimum orchestration required to connect the completed prerequisite components.

For one tournament, processing must:

1. load the three persisted roster screenshot associations
2. require all three source images to remain readable
3. require a valid operator-selected crop for each screenshot
4. load each preserved original through a roster-specific local image-input boundary
5. apply only the persisted operator-selected crop in memory
6. construct the prepared cropped-panel input for screenshot positions 1, 2, and 3
7. invoke the existing `RosterRawOcrExtractor`
8. pass raw extraction output to the existing `RosterCandidateParser`
9. combine parsed candidates through the existing `RosterSlotAssociator`
10. validate the associated candidates through the existing `RosterOcrValidator`
11. expose the resulting review evidence without changing the confirmed roster

The orchestration must reuse the completed extractor, parser, associator, validator, layout, screenshot metadata, and local-image boundaries.

It must not duplicate their algorithms.

## 6. Cropped image preparation decision

The preserved roster screenshot remains the immutable original.

v0.9.10 may introduce the minimum Android/data-layer adapter required to:

- resolve the existing app-private roster screenshot
- decode it safely
- convert the persisted normalized crop metadata to source-image pixel bounds
- create an in-memory cropped `AndroidBitmapOcrImage`
- pass only that cropped panel into roster OCR
- release generated bitmap resources safely

The implementation must not:

- send the full screenshot to roster OCR
- guess crop coordinates
- automatically detect the roster panel
- create public files or URLs
- change the scoreboard preprocessor
- persist a second cropped image unless separately approved
- expose private paths or image content in logs

A missing file, unreadable source, invalid crop, decode failure, unsafe dimensions, or crop failure must produce a controlled processing/review error rather than a crash.

## 7. Review model decision

The roster OCR review state must retain, per tournament slot 1 through 12:

- tournament slot number
- source screenshot position
- source visible-slot position
- candidate player rows
- raw/source evidence references already available from the Phase 8 models
- candidate parse status
- available confidence state
- validation issues
- original candidate text
- current corrected draft text

Team names remain important because the confirmed roster requires all 12 team names.

The completed Phase 8 parser deliberately does not infer team names from unsupported OCR regions. Therefore v0.9.10 must initialize team-name review values from the current manually maintained tournament team-slot names.

It must not invent or infer OCR team names.

## 8. Player-count decision

The existing Phase 8 roster layout currently supports exactly four OCR player rows per visible slot.

v0.9.10 must not invent fifth or sixth OCR rows.

During review, however, the operator must be able to produce a final roster satisfying the existing canonical roster rule of four to six players per team.

Therefore:

- the four OCR-derived rows may initialize the correction draft
- the operator may correct those names
- the operator may add fifth or sixth player names manually where required
- the operator may not confirm fewer than four or more than six players for a team
- final validity remains governed by the existing roster validator

No new OCR extraction algorithm for players five or six is authorized.

## 9. Correction decision

Before confirmation, the operator may correct candidate roster values.

Approved corrections are:

- edit candidate player names
- clear incorrect candidate player names
- add manually required fifth or sixth players
- edit team names using the existing/manual team value as the initial value

Corrections affect only the review draft until explicit confirmation.

Changing review-draft values must not mutate:

- confirmed Room roster state
- cloud roster state
- source screenshots
- crop metadata
- raw OCR evidence
- match data
- scoring or standings

## 10. Validation decision

The Phase 8 `RosterOcrValidator` remains the authority for OCR-candidate evidence quality.

The existing roster validator remains the authority for final roster validity.

These are different responsibilities.

Before explicit confirmation:

- OCR blocking/warning/info issues must remain visible where relevant
- corrected draft data must be transformed into a complete 12-slot confirmed-roster candidate
- the final candidate must satisfy the existing roster validator
- all 12 team slots must exist
- every team must contain four to six valid players
- existing duplicate-team/player policies remain unchanged
- no new duplicate-player policy may be invented in this version

An OCR issue may be resolved through explicit operator correction, but it must not disappear merely because the UI ignores it.

## 11. Abandonment and fallback decision

The operator must be able to abandon roster OCR review safely.

Abandonment must:

- discard only the in-memory review/correction draft
- leave the existing confirmed/manual roster unchanged
- leave original roster screenshots and crop metadata unchanged unless the operator separately removes them through the existing intake workflow
- return the operator to the existing manual roster workflow

Manual roster entry remains the permanent fallback.

## 12. Explicit confirmation decision

Roster OCR review must never persist automatically.

A dedicated explicit confirmation action is required.

Confirmation must be blocked unless:

- the tournament still exists
- a complete 12-slot correction draft exists
- the draft satisfies the existing final roster validation rules
- replacement is still eligible under v0.5.8 safety rules
- no confirmation operation is already running

The final corrected candidate must be passed to the existing:

`ReplaceConfirmedTournamentRosterUseCase`

v0.9.10 must not implement a second local roster-replacement path.

## 13. Existing-match protection decision

v0.5.8 remains authoritative for local replacement eligibility.

If any draft or finalized match exists, roster replacement is rejected.

v0.9.10 must surface this as a controlled blocked-confirmation result.

It must not:

- delete matches
- modify matches
- modify finalized results
- bypass the zero-match rule
- silently keep retrying an invalid local replacement

## 14. Cloud synchronization decision

After successful local confirmed-roster replacement, v0.9.10 may invoke the existing:

`ReplaceTournamentRosterInCloudUseCase`

The local replacement remains authoritative for the operator action.

Cloud behavior must retain v0.6.9 semantics:

- success completes normally
- authentication/network cases use existing queue behavior
- retry rereads the current local confirmed roster
- revision conflict remains explicit
- authorization failure remains explicit
- match-blocked cloud replacement remains terminal validation failure
- no blind overwrite is allowed

v0.9.10 must not introduce a second sync queue, RPC, cloud roster model, or conflict algorithm.

A temporary network/authentication failure must not roll back an already successful local replacement.

## 15. UI placement decision

Roster OCR remains part of the existing tournament roster workflow.

The existing roster review screen already hosts the roster screenshot intake section.

v0.9.10 should extend that workflow with the minimum review/correction presentation required for:

- starting roster OCR processing when all three screenshots and crops are ready
- showing processing state
- showing controlled processing errors
- displaying all 12 roster candidate slots
- editing correction drafts
- showing validation/blocking state
- abandoning OCR review
- explicitly confirming the corrected replacement

A completely separate top-level tournament workflow is not required unless implementation evidence proves the existing screen cannot safely host the state.

Existing manual team and player editing controls must remain available.

## 16. State and lifecycle decision

Roster OCR review/correction state may remain transient presentation/domain state unless durable draft persistence is independently required by an existing canonical requirement.

This version does not require a new Room table merely to preserve an unfinished OCR review draft.

The following remain durable through existing storage:

- original roster screenshots
- crop metadata
- current confirmed roster
- local revision state
- cloud revision/queue state

If the app/process is recreated before confirmation, OCR candidates may be regenerated from the preserved screenshot/crop inputs.

No duplicate raw-OCR evidence database is required.

## 17. Raw evidence preservation decision

Original screenshots and Phase 8 raw/candidate models must remain distinguishable from corrected review values.

v0.9.10 must not mutate raw OCR evidence in place.

The confirmed roster is the corrected authoritative result after explicit confirmation.

This version does not introduce a new permanent roster-OCR evidence-history schema unless a separate approved decision demonstrates that such persistence is required.

## 18. Privacy decision

Roster screenshots, paths, raw OCR text, candidate player names, corrected player names, and local ground truth remain private tournament data.

Implementation and tests must:

- avoid logging screenshot contents, raw OCR payloads, player names, or private local paths
- use synthetic/sanitized automated fixtures
- not commit real roster screenshots
- not commit real player-name datasets
- not commit local/private acceptance paths

Real roster OCR acceptance remains separate Phase 12 work.

## 19. Failure handling

The review workflow must expose controlled states for at least:

- missing tournament
- incomplete screenshot set
- missing crop
- missing local original
- unreadable/decode failure
- invalid crop bounds
- roster layout incompatibility
- OCR extraction failure
- parser/association failure
- validation blocked/manual-review states
- invalid corrected roster
- replacement blocked by existing matches
- local replacement failure
- cloud authentication/network/conflict/authorization/validation failure

Failures must not silently mutate the roster.

## 20. Explicit exclusions

v0.9.10 does not authorize:

- changing v0.8.9 layout coordinates
- changing ML Kit configuration
- changing OCR parsing algorithms
- fuzzy correction or generative correction
- automatic crop detection
- automatic roster confirmation
- automatic team-name inference
- fifth/sixth player OCR extraction
- new duplicate-player policy
- new Room schema or migration unless later evidence proves unavoidable
- new Supabase schema or migration
- new RPC
- new sync queue type
- scoreboard OCR changes
- match OCR changes
- scoring or standings changes
- match finalization changes
- export changes
- real roster screenshots or real-name fixtures
- Phase 12 acceptance evaluation

## 21. Testing expectations

Implementation must provide focused synthetic coverage for:

- complete three-image/crop orchestration
- correct screenshot positions 1–3
- preserved-crop to in-memory cropped-panel preparation
- extractor → parser → associator → validator handoff
- missing image and missing crop failures
- decode/crop failure
- processing cancellation/error handling
- 12-slot review-state construction
- original candidate versus corrected value separation
- correction of OCR player names
- fifth/sixth manual player addition
- team-name review using current manual team names
- invalid final roster blocking
- abandonment without mutation
- explicit confirmation only
- local replacement success
- existing-match replacement rejection
- no local mutation before confirmation
- cloud sync invoked only after successful local replacement
- retryable cloud outcomes preserving local replacement
- controlled cloud conflict/auth/network/authorization states
- manual roster workflow regression
- screenshot intake/crop workflow regression
- no match/scoring/standings mutation

Real OCR accuracy is not part of this version's automated acceptance.

## 22. Implementation strategy

Keep v0.9.10 as one product version.

If implementation size requires multiple coding prompts, split the work by responsibility while preserving one frozen version boundary:

1. roster OCR input preparation and orchestration
2. review/correction state and UI
3. explicit local confirmation plus existing cloud-sync handoff
4. focused regression verification

Do not create additional version numbers merely to make implementation prompting easier.

## 23. Acceptance criteria

v0.9.10 is complete only when:

- the three persisted roster screenshots and user crops can drive the existing roster OCR pipeline
- the resulting candidates are displayed as review-only data
- all 12 tournament slots are represented
- team names come from current manual team-slot data rather than unsupported OCR inference
- candidate player names can be corrected
- fifth/sixth players can be manually supplied where needed
- corrected data remains non-authoritative before confirmation
- abandonment leaves the confirmed roster unchanged
- explicit confirmation uses `ReplaceConfirmedTournamentRosterUseCase`
- cloud synchronization reuses `ReplaceTournamentRosterInCloudUseCase`
- existing-match protection remains enforced
- raw OCR/candidate evidence remains distinct from corrected values
- manual roster entry remains available
- existing scoreboard/match workflows remain unchanged
- no private real-image/name fixtures are committed
- focused tests, required builds, and `git diff --check` pass

## 24. Next action

After this decision is reviewed and merged, perform a read-only implementation-boundary review.

Freeze the exact files before implementation.

No production file may be added to the implementation boundary without evidence that it is required by this decision contract.
