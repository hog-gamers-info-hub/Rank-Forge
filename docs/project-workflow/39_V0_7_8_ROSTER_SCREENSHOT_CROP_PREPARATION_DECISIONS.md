# v0.7.8 — Roster Screenshot Crop Preparation Decisions

## 1. Status

Approved implementation decision gate for the Phase 7 extension v0.7.8. This
document authorizes manual roster screenshot crop preparation only. It does not
authorize implementation until it is reviewed, merged, and the user explicitly
approves the corresponding implementation task.

## 2. Version scope

v0.7.8 follows the completed v0.7.7 roster screenshot intake boundary. It adds
operator-controlled, in-app crop preparation for the selected roster
screenshots in the three-image tournament draft set.

This version prepares user-selected crop regions only. It neither defines a
supported roster layout nor derives team, player, slot, or OCR data from an
image. No fixed coordinates are defined or inferred.

## 3. Canonical sources reviewed

This decision was reviewed against:

* `AGENTS.md` and `README.md`;
* `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`;
* `docs/project-workflow/37_ROSTER_SCREENSHOT_OCR_ROADMAP_DECISIONS.md` and
  `38_V0_7_7_ROSTER_SCREENSHOT_INTAKE_DECISIONS.md`;
* Phase 7 decisions `19_V0_7_0_PHOTO_PICKER_INTEGRATION_DECISIONS.md` through
  `25_V0_7_6_SCREENSHOT_METADATA_DECISIONS.md`, and the Phase 7 closure audit;
* the current v0.7.7 roster screenshot intake state, ViewModel, Photo Picker,
  image validation, and in-memory duplicate-selection behavior;
* current match screenshot linking, local preservation, metadata, and storage
  implementations, which are match-ID-bound;
* current tournament detail, roster review, manual team-entry, and manual
  roster-entry workflows; and
* relevant Android, Compose, OCR, storage, privacy, testing, and AI workflow
  documents.

## 4. Relationship to v0.7.7 roster screenshot intake

v0.7.7 provides a tournament-scoped, in-memory draft set with exactly three
ordered roster screenshot positions. It validates selected image candidates and
allows selection, replacement, removal, cancellation, and duplicate rejection
without associating the images with a match.

v0.7.8 operates only on a currently selected and validated roster screenshot
position from that state. Replacing or removing an image must clear its pending
crop state. An incomplete roster screenshot draft remains valid intake state;
it must not start OCR, roster validation, or confirmation.

## 5. Manual in-app crop boundary

* The operator must be able to define a crop region for each selected roster
  screenshot position.
* Crop selection is manual and user-controlled because full roster screenshots
  may contain chat, lobby, account, header, start, invite, and other non-roster
  UI.
* v0.7.8 must not guess crop coordinates, infer a region, or provide automatic
  crop detection.
* v0.7.8 must not define fixed roster-panel or team-slot coordinates. Fixed
  roster layout definition belongs to v0.8.9 after approved representative
  screenshots and manually verified ground truth exist.
* Keep crop controls narrow: selecting, replacing, clearing, and validating a
  rectangular roster-panel crop are approved; broad image-editing features are
  not.

## 6. Crop metadata decision

* Crop state is maintained for exactly the three roster screenshot positions.
* Represent a crop rectangle with normalized coordinates where possible, such
  as left, top, width, and height relative to the selected image dimensions.
  This makes a user-selected crop resolution-independent without defining a
  fixed layout.
* The implementation must validate that every normalized edge is within the
  source-image bounds and that the rectangle has positive dimensions and a
  documented minimum crop size.
* Supported state may include no crop, crop pending, crop set, crop cleared,
  crop invalid, and crop ready. A crop becomes ready only after source and
  bounds validation succeeds.
* Crop metadata records the operator's selection. It does not express team
  slots, player rows, layout confidence, OCR evidence, parsing results, or
  confirmation.
* If durable crop metadata would require Room, Supabase, or backend changes,
  v0.7.8 keeps it in memory or draft presentation state only. Durable set
  identity and restore behavior remain v0.7.9 work.

## 7. Original image preservation decision

Original selected roster images should remain private and unchanged before a
crop or OCR-specific variant is prepared. The current local preserver and
metadata model use a mandatory match ID and deterministic match-owned paths;
they cannot be reused for roster images without creating false match ownership.

Accordingly, v0.7.8 must not use the existing match-only path or metadata for
roster originals. If a separate safe tournament-scoped, roster-image-scoped
app-private preservation boundary is not available without schema or backend
changes, originals and crop state remain in the v0.7.7 in-memory draft until a
later approved storage contract. No Room migration, Supabase change, cloud
upload, or second image-storage architecture is authorized here.

## 8. Cropped OCR image decision

* A cropped OCR-ready image may be prepared only from an operator-selected,
  validated crop and only in app-private/local scope.
* Crop preparation must retain the original image unchanged and must not expose
  cropped variants through public storage, URLs, logs, exports, or network
  operations.
* A prepared cropped image is not OCR execution. It must not invoke ML Kit,
  preprocessing, parsing, or any external service.
* If a safe roster-image-scoped app-private output path is unavailable without
  an unapproved durable contract, do not write a cropped image file; retain
  only in-memory crop state and defer durable derived-image behavior.
* Cropped images and crop metadata must never use match-only paths, match
  metadata, or match upload state.

## 9. Tournament association decision

* Crop state belongs to the same tournament ID and roster screenshot index
  (1, 2, or 3) as its intake candidate.
* Missing, invalid, removed, or replaced source-image state blocks crop-ready
  state safely and clears stale crop data.
* Crop preparation must not change tournament status, manual roster data,
  created or finalized matches, scoring, standings, correction workflows, or
  synchronization behavior.

## 10. Roster-vs-match image separation decision

Roster crop data must remain distinguishable from match-result screenshot crop
or metadata.

* Do not attach a roster crop to a match merely to reuse the completed Phase 7
  match workflow.
* Do not use `ScreenshotMetadataEntity`, its match-ID identity, or the current
  `screenshots/<tournament>/<match>/original.<extension>` path for roster
  originals, crops, or crop metadata.
* Existing match screenshot selection, validation, linking, preservation,
  duplicate detection, metadata, Storage behavior, finalized-match protection,
  and scoreboard OCR remain unchanged.

## 11. Privacy and fixture decision

* Roster screenshots, originals, crop rectangles, cropped images, and any
  future OCR evidence are private tournament data and must not be logged or
  committed unnecessarily.
* No real screenshots, real player names, raw OCR payloads, private paths, or
  signed URLs may be committed without explicit privacy approval.
* The roster screenshot supplied in conversation demonstrates why a manual crop
  is needed; it must not be copied into the repository without explicit privacy
  approval.
* Synthetic, sanitized values are required for automated crop tests. Approved
  representative screenshot evaluation remains local-only under documented
  privacy rules until a later canonical policy permits otherwise.

## 12. Explicit exclusions

v0.7.8 does not implement:

* automatic crop detection, fixed crop coordinates, fixed roster layout, team
  slot coordinates, or any guessed crop region;
* ML Kit execution, OCR, preprocessing, raw text extraction, parsing, slot
  association, or roster OCR validation;
* roster review or correction UI beyond minimal crop preparation controls;
* confirmed roster persistence, Room changes, Supabase changes, migrations,
  cloud roster replacement, or synchronization;
* scoring, standings, match creation, match finalization, finalized-data
  protection changes, or correction-workflow changes;
* public/external image storage, sharing, networking, new Android permissions,
  broad image editing, real screenshots, or real player-name fixtures.

## 13. Testing and verification expectations

Future implementation must provide focused synthetic-data coverage for:

* normalized crop-rectangle validation, including in-bounds positive rectangles
  and minimum crop-size rules;
* invalid bounds, zero or negative dimensions, out-of-range coordinates, and
  too-small crop rejection;
* crop set, replace, clear, pending, invalid, and ready transitions for all
  three roster screenshot positions;
* clearing stale crop state when the selected source image is replaced or
  removed;
* ViewModel handling of missing tournament context, incomplete sets, and
  controlled crop failures;
* Compose UI behavior if crop controls or previews are introduced;
* regression that manual roster review remains available; and
* regression that the completed match screenshot workflow remains unchanged.

Tests must not require ML Kit, OCR accuracy, network, Supabase, Room migration,
external files, real screenshots, or real player names. If crop UI or image
preview behavior is introduced, manual device smoke verification may be
required alongside relevant unit, UI, instrumentation, lint, build, and
`git diff --check` checks.

## 14. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| A crop could silently include unrelated full-screen content. | Require an explicit user-selected rectangle and do not guess coordinates. |
| A resolution-specific rectangle could fail on a later image size. | Store normalized crop metadata and validate it against the current source bounds. |
| Replacing an image could leave a crop for the wrong source. | Clear crop state whenever the source image is removed or replaced. |
| Match-owned paths or metadata could misrepresent roster images. | Keep crop state tournament/index-scoped and prohibit match metadata and paths. |
| Private screenshot information could enter source control or logs. | Use private app-local handling, synthetic tests, and prohibit committing real inputs or private paths. |
| Crop work could be mistaken for OCR or layout support. | Keep this version manual preparation only and defer fixed layout and extraction work. |

## 15. Acceptance criteria for implementation

v0.7.8 implementation is acceptable only when it:

* adds manual crop preparation only for validated v0.7.7 roster screenshot
  candidates in positions 1 through 3;
* stores and validates an operator-selected normalized crop rectangle without
  guessing fixed coordinates;
* supports safe crop pending, set, clear, invalid, and ready states;
* clears stale crop state on source replacement or removal;
* keeps originals unchanged and creates any cropped image only through a safe,
  private, roster-image-scoped local boundary;
* retains in-memory/draft-only behavior when durable storage would require an
  unapproved schema, backend, or metadata contract;
* preserves manual roster entry and all completed match screenshot behavior;
  and
* introduces none of the excluded OCR, parsing, persistence, cloud, scoring,
  standing, finalization, permission, or real-fixture scope.

## 16. Next implementation action

After this decision is reviewed and merged, the next approved implementation
task may add only the v0.7.8 manual roster crop state and minimal crop controls
inside the v0.7.7 roster screenshot intake boundary. It must not proceed into
OCR or durable roster-image storage without a separately approved contract.

Representative roster screenshots and manually verified ground truth remain
blockers for v0.8.9 cropped roster layout definition. OCR extraction is
deferred to v0.8.10, roster parsing to v0.8.11, roster review/correction UI to
Phase 9, and confirmed roster persistence to v0.5.8/v0.6.9 plus the Phase 9
confirmation flow.
