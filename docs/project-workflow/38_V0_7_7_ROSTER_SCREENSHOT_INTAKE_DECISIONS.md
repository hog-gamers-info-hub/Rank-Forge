# v0.7.7 — Roster Screenshot Intake Decisions

## 1. Status

Approved implementation decision gate for the Phase 7 extension v0.7.7. This
document authorizes roster screenshot intake only. It does not authorize
implementation until it is reviewed, merged, and the user explicitly approves
the corresponding implementation task.

## 2. Version scope

v0.7.7 is the first implementation version in the approved screenshot-first
roster OCR extension. It allows an operator to select or attach roster
screenshot candidates for one tournament and manage an incomplete draft set
safely. The intended complete set is exactly three roster screenshots for one
tournament.

Each selected image is expected to show four visible team slots, but this
version neither parses nor validates roster text. It produces no OCR output and
does not establish crop coordinates, crop behavior, roster slot association, or
confirmed roster data.

## 3. Canonical sources reviewed

This decision was reviewed against:

* `AGENTS.md` and `README.md`;
* `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`;
* `docs/project-workflow/37_ROSTER_SCREENSHOT_OCR_ROADMAP_DECISIONS.md`;
* Phase 7 decisions `19_V0_7_0_PHOTO_PICKER_INTEGRATION_DECISIONS.md` through
  `25_V0_7_6_SCREENSHOT_METADATA_DECISIONS.md`, and
  `26_PHASE_7_CLOSURE_AUDIT.md`;
* current Photo Picker, image validation, duplicate detection, local image
  preservation, match screenshot metadata/linking, and screenshot workflow
  implementations;
* current tournament creation/detail and manual roster workflows; and
* relevant product, architecture, Android, OCR, testing, security, and AI
  workflow documentation.

## 4. Relationship to completed Phase 7 behavior

The completed v0.7.0 through v0.7.6 match-result screenshot workflow remains
closed and protected. It selects one image in Match Review, validates it,
associates it with a draft match, detects in-session duplicates, preserves the
original in a deterministic match-owned app-private path, and persists
match-owned metadata.

v0.7.7 must not reinterpret or generalize those match-owned contracts by
assumption. Its intake may reuse safe Photo Picker, image-validation, private
copying, and duplicate-detection concepts where their boundaries remain valid,
but roster screenshots are tournament-scoped and are not match-result
screenshots.

## 5. Roster screenshot intake boundary

* Use the existing Android system Photo Picker image-only selection behavior
  where it can be safely reused.
* Allow selection, replacement, removal, and cancellation for a roster
  screenshot draft set associated with one tournament.
* Keep incomplete draft sets safe and free of OCR work.
* Do not require all three images before a draft intake state may be saved,
  unless the existing UX architecture makes staged selection unsafe.
* Do not create a second roster repository or duplicate existing image-storage
  architecture.
* If durable storage or metadata cannot be added safely without a schema change,
  implementation is limited to in-memory or draft state until a later approved
  storage version defines that contract.

## 6. Three-screenshot set decision

* The intended complete roster set is exactly three images for one tournament.
* Each image is expected to represent four visible team slots, for the existing
  fixed 12-slot tournament roster.
* The set must carry a roster screenshot order or index so later versions can
  associate the three images deterministically.
* A set with fewer than three images is an incomplete draft, not a crash, OCR
  failure, or roster-validation result.
* v0.7.7 does not parse the four slots, confirm their contents, or map any
  image content to fixed tournament slots.

## 7. Image validation decision

* Validate roster image candidates using the existing safe image-validation
  concepts where possible: image MIME type, supported format, readable content,
  positive decodable dimensions, and a bounded safe size.
* Reuse the existing PNG, JPEG, and supported WebP validation rules unless a
  later approved roster-intake requirement requires stricter limits.
* Reject invalid, unreadable, unsupported, malformed, or oversized candidates
  with controlled feedback and without persisting or processing roster text.
* Reuse the existing SHA-256 duplicate-detection approach only where its
  tournament-scoped, stream-based semantics can be applied safely. A duplicate
  selection must not silently create a second roster-image entry.

## 8. Original image preservation decision

* Prefer app-private storage for original selected roster screenshots if this
  version's implementation scope can establish safe ownership without an
  unapproved schema change.
* Preserve an original before later versions create a crop or OCR-specific
  image. Preservation copies source bytes without cropping, resizing,
  re-encoding, OCR, or other image mutation.
* Roster originals require their own tournament-scoped identity and safe
  deterministic path rules. They must not be written to the current
  `screenshots/<tournament>/<match>/original.<extension>` match-owned path.
* No public, external, or arbitrary URI-derived path is permitted.
* Cloud upload, cloud metadata, sync, restart restoration, and retention or
  deletion policy are not authorized in v0.7.7.

## 9. Tournament association decision

* Every roster screenshot draft set belongs to one tournament ID, not a match
  ID.
* Each selected roster image is associated with that tournament and its roster
  screenshot order or index.
* Missing or invalid tournament context blocks selection-state confirmation,
  preservation, and replacement safely.
* The association must not alter tournament status, existing manual roster
  data, created or finalized matches, scoring, standings, or correction
  workflows.

## 10. Roster image type separation decision

Roster screenshots must be distinguishable from match-result screenshots.

* Do not reuse `ScreenshotMetadataEntity`, whose canonical identity is a match
  ID, in a way that creates incorrect match ownership.
* Do not use current match-only local or cloud paths for roster originals.
* Do not attach a roster screenshot to a match merely to reuse the completed
  Phase 7 workflow.
* Durable roster image type, set association, and local restore behavior are
  reserved for v0.7.9 and any later approved storage contract.

## 11. Manual roster fallback decision

Manual roster entry, validation, review, and correction remain available and
unchanged. Roster screenshot intake is optional draft evidence only. It cannot
replace manual roster values, confirm a roster, change existing roster
persistence, or make any team or player data available to scoring or standings.

## 12. Privacy and fixture decision

* Original roster screenshots are private evidence and must receive the same
  least-access and private-storage treatment as other sensitive screenshots.
* No real screenshots, real player names, raw OCR payloads, private paths, or
  signed URLs may be committed.
* The roster screenshot supplied in conversation establishes product need only;
  it must not be copied into the repository without explicit privacy approval.
* Synthetic, sanitized fixtures are required for automated testing.
* Any representative roster screenshot evaluation remains local-only under the
  documented privacy rules until a later canonical policy permits otherwise.

## 13. Explicit exclusions

v0.7.7 does not implement:

* crop UI, crop metadata, or crop preparation, which are deferred to v0.7.8;
* roster screenshot set persistence, durable type metadata, or local restore,
  which are deferred to v0.7.9;
* ML Kit execution, OCR, preprocessing, raw text extraction, roster parsing,
  slot association, or roster OCR validation;
* OCR review or correction UI, which is deferred to Phase 9;
* confirmed-roster persistence, Room schema changes, Supabase schema changes,
  Supabase roster replacement, synchronization, or migrations;
* scoring, standings, match creation, match finalization, finalized-data
  protection changes, or correction-workflow changes;
* a new Android permission, network behavior, real image fixture, or real
  player-name fixture.

## 14. Testing and verification expectations

Future implementation must provide focused synthetic-data coverage for:

* roster screenshot set state and exactly-three complete-set validation;
* incomplete draft sets, including safe select, replace, remove, and cancel
  behavior;
* candidate validation using the established MIME, readability, dimension, and
  safe-size rules;
* duplicate selection behavior when duplicate detection is implemented;
* ViewModel transitions for select, replace, remove, cancellation, missing
  tournament context, and controlled failures;
* Compose UI behavior if roster intake UI is introduced; and
* regression proving that the completed match screenshot flow remains
  unchanged.

Tests must not require ML Kit, OCR accuracy, Room migration, Supabase, network,
external files, real screenshots, or real player names unless a later approved
version explicitly justifies them. If Photo Picker or UI changes, manual device
smoke verification may be required in addition to the relevant unit, UI,
instrumentation, lint, build, and `git diff --check` checks.

## 15. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Match-only metadata or paths could falsely represent roster images as match evidence. | Keep roster intake tournament-scoped and do not reuse match identity, metadata, or paths. |
| Partial selection could be mistaken for a complete roster. | Represent incomplete sets explicitly as drafts and block OCR and confirmation. |
| The same source image could be selected more than once. | Reuse stream-based duplicate detection only where safe and report a controlled duplicate state. |
| Private roster content could enter version control or logs. | Use private handling, synthetic fixtures, and prohibit committing real screenshots, names, paths, and OCR payloads. |
| Later OCR work could infer a layout from a single image. | Defer crop behavior and require representative screenshots plus verified ground truth before v0.8.9. |

## 16. Acceptance criteria for implementation

v0.7.7 implementation is acceptable only when it:

* permits image-only roster screenshot selection for a valid tournament context;
* supports a safe incomplete draft set and identifies exactly three selected
  images as the intended complete set;
* records tournament association and image order without creating match
  ownership;
* validates candidates using safe established image-validation concepts;
* preserves originals privately only when a safe approved boundary exists;
* handles replacement, removal, cancellation, duplicates where implemented,
  and controlled failures without crashes or data changes;
* leaves manual roster entry and the completed match screenshot workflow
  unchanged; and
* introduces none of the excluded OCR, crop, persistence, backend, scoring,
  standings, finalization, correction, permission, or real-fixture scope.

## 17. Next implementation action

After this decision is reviewed and merged, the next approved implementation
task may add only the v0.7.7 roster screenshot intake boundary. It must inspect
the current match-bound screenshot architecture first and stop if safe
tournament-scoped draft handling cannot be implemented without an unapproved
schema or storage contract.

Representative roster screenshots and manually verified ground truth remain
required before v0.8.9 cropped roster layout work. Crop behavior remains
deferred to v0.7.8; confirmed roster persistence remains deferred to v0.5.8 and
v0.6.9 plus the Phase 9 confirmation flow; and roster review/correction UI
remains deferred to Phase 9.
