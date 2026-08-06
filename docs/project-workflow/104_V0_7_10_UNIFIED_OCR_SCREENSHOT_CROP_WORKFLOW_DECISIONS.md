# v0.7.10 - Unified OCR Screenshot Crop Workflow Decisions

## 1. Status

Approved decision gate for Phase 7 v0.7.10 - Unified OCR Screenshot Crop
Workflow.

This is a documentation-only decision. It authorizes the canonical direction
for later implementation work, but it does not change Android source, Gradle,
Room schemas, Supabase migrations, OCR code, navigation, tests, or closure
audits.

The expected implementation branch sequence must begin from:

* `feature/v0.7.10-unified-ocr-crop-contract`

Follow-on branches may split Part B, Part C, and Part D as described in this
document. Implementing all parts in one large pull request is explicitly not
approved.

## 2. Context

Rank-Forge already has two screenshot families:

* match-result screenshots, originally introduced in Phase 7 v0.7.0 through
  v0.7.6, with Photo Picker intake, validation, duplicate detection, local
  preservation, private Supabase Storage upload, and match screenshot metadata;
  and
* roster screenshots, introduced by the Phase 7 roster screenshot extension
  v0.7.7 through v0.7.9, with three tournament-scoped roster screenshots,
  manual crop metadata, local persistence, and local restore.

Later OCR work added ML Kit integration, preprocessing, raw extraction,
parsing, matching, review, correction, and evidence flows. Phase 13 internal
alpha work is currently paused because screenshot-to-OCR source identity,
manual crop authority, and coordinate-space mapping are not unified across
match and roster workflows.

v0.7.10 creates the missing Phase 7 screenshot workflow decision before any
paused OCR-core implementation resumes.

## 3. Canonical placement

The official version is:

* **v0.7.10 - Unified OCR Screenshot Crop Workflow**

This work belongs in Phase 7 as a controlled screenshot-intake and
screenshot-storage extension, not in Phase 8 or Phase 13.

Phase 7 owns original screenshot intake, validation, duplicate protection,
local preservation, Storage paths, screenshot metadata, and screenshot
identity. Manual crop selection changes how screenshots become valid OCR
sources, so its durable crop contract must be anchored with the screenshot
workflow before OCR reads it.

Phase 8 owns OCR layout, preprocessing, extraction, and parsing once a valid
OCR source exists. It must not invent screenshot ownership, crop persistence,
or multi-screenshot identity.

Phase 13 owns internal alpha integration and acceptance execution. It must not
repair foundational screenshot/crop identity defects inside alpha work.

Therefore v0.7.10 is a prerequisite before resuming paused Phase 13 OCR-core
work.

## 4. Problem statement

The current implementation has separate screenshot and crop concepts:

* match-result screenshots are match-scoped and historically one screenshot per
  match;
* roster screenshots are tournament-scoped and position-scoped, with crop
  edges persisted as roster metadata;
* OCR layout models use normalized x/y/width/height rectangles;
* roster crop preparation uses normalized left/top/right/bottom edges;
* match OCR preprocessing currently performs its own fixed overall scoreboard
  crop; and
* roster OCR preparation currently crops from saved roster metadata before
  running OCR.

Without a shared crop contract and screenshot identity model, future OCR can
double-crop, lose original-coordinate provenance, overwrite the wrong
screenshot role, merge upper and lower match-result captures incorrectly, or
persist evidence without knowing which original screenshot produced it.

## 5. Current state audit

The current repository state reviewed for this decision shows:

* the roadmap lists Phase 7 v0.7.0 through v0.7.9 and does not currently list
  v0.7.10;
* no existing decision document uses v0.7.10 or the Unified OCR Screenshot
  Crop Workflow title;
* the Phase 7 roster screenshot extension closure audit confirms v0.7.7 through
  v0.7.9 are completed extensions, so later controlled Phase 7 extensions are
  already an accepted project pattern;
* Room is currently version 8, with match screenshot metadata and roster
  screenshot metadata tables already present;
* current match screenshot metadata is keyed by match ID and is insufficient
  for upper/lower match-result screenshot identity;
* current roster screenshot metadata is keyed by tournament ID and roster
  screenshot index and already contains nullable crop edge columns;
* current match Storage uses private owner-scoped objects under the
  `match-screenshots` bucket;
* no Supabase roster screenshot Storage or roster screenshot metadata table is
  present;
* OCR preprocessing and extraction models preserve candidate metadata, but
  coordinate provenance is still tied to the image submitted to OCR rather than
  a unified original-screenshot mapping contract; and
* Phase 13 interim closure documents a paused OCR-core state and requires a
  fresh source/layout contract before alpha OCR work is resumed.

No direct conflict with assigning this decision to v0.7.10 was found.

## 6. Approved workflow

The approved end-to-end workflow is:

1. The operator selects or restores an original screenshot through the existing
   match-result or roster screenshot workflow.
2. The original image remains preserved unchanged as private evidence.
3. The operator must manually define or confirm the OCR crop before ML Kit is
   allowed to process the screenshot.
4. The selected crop is validated against the owning OCR profile.
5. A valid crop is stored as normalized metadata tied to the exact screenshot
   identity and the original image dimensions.
6. OCR preprocessing receives an explicit source mode indicating that the input
   is an approved crop source.
7. ML Kit receives only the approved crop/candidate image, never an
   unapproved full screenshot.
8. OCR geometry is mapped back to original screenshot coordinates for evidence
   and review.
9. OCR output remains candidate evidence and must go through mandatory human
   review before finalization.

Manual crop is mandatory for both match-result and roster screenshot OCR.

## 7. Implementation split

v0.7.10 must be implemented in these parts:

### Part A - Shared pure crop contract

Introduce shared pure Kotlin crop models, crop validation, crop-to-pixel
conversion, source-mode metadata, and original-coordinate mapping helpers.

Part A must not touch Compose UI, Android bitmap code, Room, Supabase, ML Kit,
or navigation except where tests need pure model access.

### Part B - Visual reusable crop UI

Introduce a reusable visual crop component that can display a screenshot
preview and allow drag, resize, reset, and re-crop behavior using a profile
supplied by the owning workflow.

Part B must not persist data, run OCR, upload files, or alter scoring,
standings, finalization, or correction behavior.

### Part C - Screenshot-level identity and persistence

Introduce durable screenshot identity and crop metadata for each OCR-relevant
screenshot role, including Room migration and Supabase migration/RLS strategy
where approved by the implementation task.

Part C must preserve existing data and must not edit historical Room schemas or
historical Supabase migrations.

### Part D - OCR pipeline integration

Wire OCR preprocessing and raw extraction to consume only confirmed crop
sources, avoid double-cropping, map OCR geometry back to original coordinates,
and preserve screenshot identity in OCR evidence.

Part D must not auto-accept OCR output or bypass mandatory review.

## 8. Shared crop contract decisions

The canonical crop data model is a shared pure model representing:

* a stable screenshot identity;
* screenshot kind: match result or roster;
* screenshot role: match-result upper, match-result lower, or roster position
  1, 2, or 3;
* owning tournament ID;
* nullable match ID for match-result screenshots only;
* original image width and height;
* normalized crop rectangle;
* crop profile ID;
* crop revision or updated timestamp; and
* source mode for OCR preprocessing.

The canonical normalized crop rectangle uses edge coordinates:

* `left`
* `top`
* `right`
* `bottom`

The contract must not continue to grow separate incompatible crop models for
match OCR and roster OCR. Existing x/y/width/height OCR layout rectangles may
remain layout definitions, but persisted and operator-selected screenshot crop
metadata must use the shared edge-based crop model.

## 9. Crop geometry and validation decisions

All normalized crop values must be finite `Double` values in `0.0..1.0`.

A valid crop requires:

* `left < right`;
* `top < bottom`;
* positive width and height after pixel conversion;
* crop edges inside the original image bounds;
* profile-specific minimum size;
* profile-specific aspect or dimension constraints where required; and
* original image width and height greater than zero.

Original dimensions are captured from the validated original image and are
part of the crop contract. A crop is valid only for the original dimensions it
was recorded against. Replacing the original screenshot invalidates stale crop
metadata unless the implementation can prove the replacement has the same
screenshot identity and crop revision, which is not the baseline assumption.

Pixel conversion is deterministic:

* `pixelLeft = floor(left * originalWidth)`;
* `pixelTop = floor(top * originalHeight)`;
* `pixelRight = ceil(right * originalWidth)`;
* `pixelBottom = ceil(bottom * originalHeight)`;
* converted edges must be clamped only after validation and must still remain
  inside the original dimensions; and
* pixel width and height are `pixelRight - pixelLeft` and
  `pixelBottom - pixelTop`.

Rounding must not silently produce a zero-size crop. Invalid or unsafe crop
conversion blocks OCR and surfaces a controlled UI error.

OCR geometry is mapped to original coordinates by reversing the submitted
candidate transformations in order:

1. undo scale or enhancement coordinate changes, if any;
2. add the confirmed crop pixel offset;
3. preserve both submitted-image coordinates and original-image coordinates
   when evidence is stored or displayed.

## 10. Visual crop UI decisions

The crop UI must be a reusable visual component, not separate coordinate-entry
forms for each workflow.

The component owns only visual interaction:

* displaying the screenshot preview;
* showing the current crop rectangle;
* drag movement;
* edge or corner resize;
* reset to profile default;
* re-crop from an existing saved crop; and
* emitting draft crop changes and confirmation events.

The component must not own:

* tournament or match identity;
* roster screenshot position identity;
* Room persistence;
* Supabase upload or metadata sync;
* duplicate detection;
* OCR execution;
* scoring or standings state; or
* finalized-match protection rules.

Workflow ViewModels own the selected screenshot identity, profile selection,
crop validation result, and controlled UI error state.

## 11. Match-result screenshot decisions

Match-result screenshot crop workflow belongs to the existing Match Review
context because that screen already owns tournament ID, match ID, draft/final
match status, and screenshot workflow state.

Match-result OCR must support separate screenshot identities for:

* `MATCH_RESULT_UPPER`
* `MATCH_RESULT_LOWER`

These identities must not be merged into a single image. Upper and lower match
result assets are separate originals, separate crop metadata records, separate
Storage objects, and separate OCR evidence sources.

Draft matches may select, replace, crop, reset, and re-crop screenshot assets.
Finalized matches must block screenshot replacement, crop mutation, unlink,
deletion, and any Storage/metadata mutation unless a later explicit
finalized-safe correction decision authorizes it.

Linking, replacement, cropping, and OCR preparation must not modify placement,
kills, totals, scoring, standings, finalization, or correction audit behavior.

## 12. Roster screenshot decisions

Roster screenshot crop workflow belongs to the tournament-scoped roster
screenshot intake/review context.

Roster screenshot identity remains:

* tournament ID;
* roster screenshot position 1, 2, or 3; and
* the restored/preserved original image for that position.

The roster crop profile owns roster-panel constraints. It must remain distinct
from match-result crop profiles because roster screenshots and match result
screenshots have different semantic regions and OCR targets.

Roster crop metadata is required before roster OCR can run. A missing,
invalid, stale, or unsafe crop blocks roster OCR with controlled feedback.

Manual roster entry, roster review, roster confirmation, and roster correction
remain unchanged.

## 13. Multi-screenshot identity decisions

Every OCR screenshot asset must have stable identity independent of external
Photo Picker URI strings.

The canonical identity tuple is:

* owner/user ID where cloud ownership is involved;
* tournament ID;
* screenshot kind;
* screenshot role;
* nullable match ID for match-result screenshots;
* roster position for roster screenshots; and
* revision/update metadata.

For match-result OCR, upper and lower screenshots are separate records for the
same match. For roster OCR, positions 1, 2, and 3 remain separate records for
the same tournament.

Duplicate screenshot protection must be based on SHA-256 fingerprint of source
bytes, not URI string equality. A duplicate fingerprint within the same
tournament is idempotent only when it targets the same screenshot identity. A
same-tournament duplicate targeting another screenshot identity must be
rejected with controlled feedback and must not silently move, link, or replace
the existing asset.

Duplicate protection must survive app restart by consulting durable local
metadata, not only in-memory session state.

## 14. Room persistence and migration decisions

Part C may introduce the next sequential Room migration. In the current
repository state, that is expected to be from version 8 to version 9, but the
implementation must inspect the current database version before editing.

The migration strategy is additive:

* do not edit historical migrations;
* do not rewrite exported historical schemas;
* do not weaken existing foreign keys, primary keys, or indexes;
* preserve existing match screenshot metadata;
* preserve existing roster screenshot metadata and crop fields;
* preserve existing match OCR evidence and correction snapshot data;
* add only the minimum tables or columns needed for screenshot-level identity,
  role-specific crop metadata, crop profile ID, original dimensions, source
  fingerprint, Storage path, and revision/update metadata; and
* include migration tests proving existing-data survival and new identity
  constraints.

If adapting existing tables would risk corrupting old data or overloading
match ID as screenshot identity, implementation must add a new normalized
screenshot asset table rather than mutate existing semantics.

## 15. Supabase metadata, Storage, and RLS decisions

Supabase changes are allowed only in Part C after the Room/local identity is
defined and tested.

Supabase metadata must be owner-scoped and private. Policies must use
`auth.uid()` ownership checks tied to the existing tournament and match owner
relationships. Policies must not use public access, service-role client logic,
or unauthenticated access.

If a new unified bucket is introduced, the approved private bucket name is:

* `ocr-screenshots`

If the implementation extends existing Storage instead, it must prove that the
current `match-screenshots` bucket can support roster and multi-match roles
without weakening its owner-scoped policy or breaking existing objects.

The canonical new Storage paths are:

* match upper/lower:
  `users/<auth-user-id>/tournaments/<tournament-id>/matches/<match-id>/result/<upper-or-lower>/original.<extension>`
* roster positions:
  `users/<auth-user-id>/tournaments/<tournament-id>/roster/<1-2-or-3>/original.<extension>`

The extension must be MIME-derived from the validated original image and must
remain one of `png`, `jpg`, or `webp`.

Upsert or deterministic replacement must be covered by insert, select, and
update policies. Delete policies are required only if the implementation
actually deletes Storage objects for replacement or unlink cleanup. All
policies must preserve private owner-scoped access.

Supabase metadata must preserve screenshot identity, owner ID, tournament ID,
nullable match ID, screenshot kind, screenshot role, local/cloud status,
fingerprint, original dimensions, byte size, MIME type, Storage path, crop
profile ID, normalized crop edges, revision, timestamps, and retry/idempotency
keys where applicable.

Retry and restoration must be screenshot-specific and idempotent. A retry for
one screenshot role must not overwrite another role or another tournament's
asset.

## 16. OCR preprocessing and evidence decisions

OCR preprocessing must support an explicit source-mode contract.

Approved source modes are:

* `ORIGINAL_WITH_CONFIRMED_CROP` - the preprocessor receives the original
  screenshot and the confirmed crop, performs exactly one crop, and records the
  original mapping;
* `PREPARED_CONFIRMED_CROP` - the preprocessor receives an already-cropped
  image and verified original-coordinate mapping metadata, and must not crop
  it again; and
* `LEGACY_FIXED_LAYOUT` - permitted only for compatibility tests or migration
  bridges and not for new OCR production flow after v0.7.10 implementation.

ML Kit must receive only the approved candidate image for the selected source
mode. The pipeline must not crop a crop, apply fixed overall-scoreboard crop
after an operator-confirmed crop, or discard the mapping back to the original
screenshot.

OCR evidence must retain:

* screenshot identity;
* screenshot kind and role;
* original image dimensions;
* confirmed crop rectangle and profile ID;
* submitted candidate dimensions;
* preprocessing source mode;
* scale/enhancement metadata;
* raw OCR geometry in submitted-image coordinates; and
* mapped geometry in original-image coordinates.

OCR evidence remains unconfirmed candidate evidence until manually reviewed.

## 17. Finalized-data protection decisions

Finalized match protection remains binding.

For finalized matches, the implementation must block:

* screenshot selection;
* screenshot replacement;
* link or unlink mutations;
* crop create/update/reset/re-crop mutations;
* local file cleanup that would mutate protected evidence;
* Storage replacement or deletion; and
* metadata updates tied to match-result screenshots.

Duplicate detection, crop validation, OCR preprocessing, retry, or restore
must not bypass finalized-match protection.

Any future finalized-safe correction flow must be separately approved and must
preserve correction audit history.

## 18. Security and privacy decisions

Original screenshots, crop metadata, fingerprints, OCR raw text, OCR geometry,
private paths, and Storage object paths are private user data.

The implementation must:

* use app-private local storage for originals and generated temporary files;
* keep cloud Storage private and owner-scoped;
* avoid broad storage, media-library, camera, and filesystem permissions;
* avoid logging private image contents, raw OCR payloads, player names,
  fingerprints, signed URLs, access tokens, or credentials;
* use synthetic test fixtures for automated tests; and
* keep genuine screenshots and genuine player names out of tracked source.

Private genuine-device OCR acceptance evidence may be summarized only as
sanitized counts and outcomes.

## 19. Testing and verification decisions

Future implementation must use synthetic automated tests for each part.

Part A tests must cover:

* normalized crop validation;
* non-finite and out-of-bounds rejection;
* invalid and too-small crop rejection;
* original dimension validation;
* deterministic pixel conversion;
* original-coordinate mapping;
* source-mode no-double-crop behavior; and
* profile-specific validation.

Part B tests must cover:

* crop UI is available from match-result and roster workflows;
* drag and resize update draft crop state;
* reset restores the profile default;
* re-crop starts from existing saved metadata;
* invalid crop cannot be confirmed; and
* finalized matches block crop mutation controls.

Part C tests must cover:

* Room migration data survival;
* upper and lower match-result screenshots are distinct;
* roster positions 1 through 3 remain distinct;
* replacement invalidates stale crop metadata;
* duplicate protection survives restart;
* retry and restoration are screenshot-specific and idempotent;
* Supabase metadata and RLS preserve owner-scoped access; and
* Storage replacement policies cover insert, select, and update without public
  access.

Part D tests must cover:

* ML Kit receives the confirmed crop source;
* no double-cropping occurs;
* OCR boxes map back to original coordinates;
* evidence retains screenshot identity and crop provenance;
* OCR remains candidate evidence;
* mandatory review is still required; and
* scoring, standings, export, roster confirmation, finalization, and
  correction behavior remain unchanged.

Required implementation verification includes focused unit tests, focused
Compose UI tests where UI changes occur, Room migration tests where persistence
changes occur, Supabase local/policy tests where backend changes occur,
`testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`,
and `git diff --check`.

Private genuine screenshot acceptance must be performed manually on a physical
device before closure, with sanitized reporting only. No manual verification is
required for this documentation-only decision.

## 20. Acceptance criteria

Implementation is acceptable only when:

* manual crop is required before ML Kit for both match-result and roster
  screenshot OCR;
* original screenshots remain unchanged byte-for-byte;
* crop metadata is stored as normalized left/top/right/bottom edges;
* crop metadata is tied to original image dimensions and screenshot identity;
* selected crop is validated against the owning OCR profile;
* invalid crops are blocked with controlled feedback;
* visual crop UI supports drag, resize, reset, and re-crop;
* one shared visual crop component supports separate match and roster profiles;
* match-result upper and lower screenshots remain separate assets;
* replacing an original screenshot invalidates stale crop metadata;
* ML Kit receives the confirmed crop through the approved preprocessing source
  mode;
* double-cropping is prevented;
* OCR boxes map back to original screenshot coordinates;
* OCR evidence retains screenshot identity and crop provenance;
* Room and Supabase migrations preserve existing data and security;
* retry and restoration are screenshot-specific and idempotent;
* duplicate screenshot protection survives app restart;
* finalized-match mutations are blocked;
* OCR remains candidate evidence only;
* mandatory review is not bypassed;
* scoring, standings, export, roster confirmation, finalized-match behavior,
  and correction workflows remain unchanged;
* synthetic automated tests pass for each implemented part; and
* private genuine screenshot acceptance is manually completed on a physical
  device before closure with no private screenshots or names committed.

## 21. Out of scope

v0.7.10 does not approve:

* auto-accepting OCR;
* scoring, standings, or correction behavior changes;
* replacing mandatory review;
* merging upper and lower match-result screenshots into one image;
* public URLs, public buckets, or sharing features;
* export changes;
* editing historical Room migrations or exported historical schemas;
* editing historical Supabase migrations;
* changing finalized protection semantics;
* committing genuine screenshots;
* using genuine player names or private OCR payloads in tracked fixtures;
* implementing all parts in one huge PR;
* cloud OCR or external AI services;
* camera capture;
* multiple roster screenshots per roster position;
* more than the approved upper/lower match-result screenshot roles;
* image editing or mutation beyond temporary OCR candidate preparation;
* OCR-driven roster confirmation;
* OCR-driven match finalization; or
* unrelated navigation, authentication, sync queue, export, scoring, standings,
  roster confirmation, or correction changes.

## 22. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Existing match metadata cannot represent upper/lower screenshots. | Add screenshot-level identity in Part C instead of overloading match ID. |
| Roster and match crop models diverge further. | Introduce the shared pure crop contract in Part A before UI or persistence work. |
| OCR coordinates are interpreted in the wrong coordinate space. | Preserve submitted-image and original-image coordinates with explicit source-mode mapping. |
| Manual crop becomes a hidden coordinate-entry workflow. | Use a reusable visual crop component with drag, resize, reset, and re-crop. |
| Replacement leaves stale crop or evidence attached to a new original. | Invalidate crop and OCR evidence whenever original identity changes. |
| Duplicate screenshots reappear after restart. | Store fingerprint ownership in durable screenshot metadata and enforce identity-scoped idempotency. |
| Supabase policies become too broad for roster or multi-match assets. | Use private owner-scoped paths and RLS checks tied to tournament/match ownership. |
| Phase 13 alpha work absorbs foundational fixes. | Require v0.7.10 implementation parts to land before resuming paused OCR-core integration. |
| Private genuine OCR data leaks into source. | Use synthetic tests and sanitized manual evidence only. |

## 23. Open follow-ups before implementation

Before implementation starts, the team must resolve:

1. Whether the roadmap should receive a separate v0.7.10 entry after this
   decision is reviewed, since the current roadmap stops Phase 7 at v0.7.9.
2. Whether Part C should introduce a new `ocr-screenshots` private bucket or
   extend the existing `match-screenshots` bucket with proven backward
   compatibility.
3. The exact Room table and entity names for unified screenshot identity after
   inspecting the current database version on the implementation branch.
4. The exact crop profile IDs and default crop rectangles for match upper,
   match lower, and roster positions.
5. The branch and pull request sequence for Part A through Part D so the work
   is not implemented in one large PR.
6. The sanitized physical-device genuine screenshot acceptance plan for closure
   after implementation and automated verification are complete.

## 24. Final decision

v0.7.10 - Unified OCR Screenshot Crop Workflow is approved as the canonical
Phase 7 extension that must precede resumed Phase 13 OCR-core work.

The approved baseline is:

* use a shared pure crop contract;
* require visual manual crop before ML Kit for match-result and roster OCR;
* keep originals unchanged;
* persist crop metadata by exact screenshot identity;
* separate match-result upper and lower screenshot assets;
* preserve roster screenshot positions 1 through 3;
* enforce duplicate protection across restart;
* preserve private local and cloud ownership rules;
* map OCR geometry back to original screenshot coordinates;
* keep OCR as candidate evidence requiring mandatory review; and
* split implementation into Part A, Part B, Part C, and Part D.

No Android implementation, Room migration, Supabase migration, OCR pipeline
change, or UI change is authorized by this documentation task alone.
