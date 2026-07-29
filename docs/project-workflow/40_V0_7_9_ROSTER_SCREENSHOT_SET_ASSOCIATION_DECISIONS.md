# v0.7.9 — Roster Screenshot Set Association Decisions

## 1. Status

Approved implementation decision gate for the Phase 7 extension v0.7.9. This
document authorizes roster screenshot set association and durable local restore
only. It does not authorize implementation until reviewed, merged, and
explicitly approved by the user.

## 2. Version scope

v0.7.9 follows v0.7.7 roster screenshot intake and v0.7.8 manual crop
preparation. It adds only the association of three ordered roster screenshot
positions with one tournament, including safe durable local restoration.

The association is metadata only. It does not run OCR, parse text, extract
visible slots, validate roster contents, or produce confirmed roster data.

## 3. Canonical sources reviewed

This decision was reviewed against:

* AGENTS.md and README.md;
* the Phase and Version Roadmap;
* the roster screenshot OCR roadmap and the v0.7.7 and v0.7.8 decisions;
* completed Phase 7 image and screenshot decisions v0.7.0 through v0.7.6;
* current roster screenshot intake and crop-preparation state, ViewModel, Photo
  Picker, validation, and fingerprint duplicate-detection code;
* current match-bound local preservation, screenshot metadata, Room database,
  DAO, repository, migration, schema, Storage, and cloud metadata contracts;
* current tournament detail and manual roster workflows; and
* relevant Android, storage, privacy, testing, security, and AI workflow
  documentation.

## 4. Relationship to v0.7.7 intake

v0.7.7 provides a tournament-scoped three-position draft, candidate
validation, fingerprinting, selection, replacement, removal, cancellation, and
in-session duplicate rejection without any match identity.

v0.7.9 preserves these image-state patterns. A durable association may be
created only for a valid tournament and validated roster image. An incomplete
set remains draft state and never implies OCR success or confirmed roster data.

## 5. Relationship to v0.7.8 crop preparation

v0.7.8 records an operator-selected normalized crop for one roster screenshot
position. It does not define fixed layout coordinates or derive slots or text.

v0.7.9 persists a valid crop only as metadata for the same tournament and
position. Replacing or removing an image clears its crop metadata. Restore must
not infer a crop or make a crop OCR-ready when the source is missing or invalid.

## 6. Roster screenshot set boundary

* A set belongs to exactly one tournament and contains at most three ordered
  screenshot positions.
* The intended complete set is exactly positions 1, 2, and 3; no other
  position is valid.
* Fewer than three valid preserved records is an incomplete durable set, not
  an OCR failure, validation result, or confirmed roster.
* Association represents roster screenshot evidence only. It does not identify
  a team, player, visible row, confirmed slot content, or match.
* Manual roster entry, validation, review, and correction remain available and
  unchanged.

## 7. Ordering and slot-set association decision

The ordered position provides the following intended fixed tournament range:

| Roster screenshot position | Intended tournament slots |
| --- | --- |
| 1 | 1–4 |
| 2 | 5–8 |
| 3 | 9–12 |

This mapping is static association metadata only. v0.7.9 does not define crop
coordinates, identify visible regions, extract a slot, parse names, validate a
roster, or replace manual data. Visible-slot association remains v0.8.12 work
after its approved layout, extraction, and parsing prerequisites.

## 8. Durable local association decision

Durable local association is approved for v0.7.9. The existing
screenshot_metadata Room model has matchId as its primary key and the current
local preserver writes match-owned paths; neither can safely represent
tournament-scoped roster evidence.

Future v0.7.9 implementation may add one minimal Room migration from version 6
to 7 and a dedicated tournament-scoped roster screenshot metadata table. Its
composite identity is tournament_id plus roster_screenshot_index; the index is
constrained to 1 through 3 and the record references the existing tournament.
It must not contain matchId.

The minimal durable record is:

* tournament ID and roster screenshot index;
* an app-private relative preserved-original path, never an absolute path or a
  persisted external Photo Picker URI;
* image MIME type, extension, dimensions, and byte size where necessary to
  retain the validated-image boundary;
* SHA-256 fingerprint where available;
* controlled local preservation and validation status;
* nullable normalized crop edges only when a valid crop is set; and
* created and updated UTC epoch-millisecond timestamps, following existing
  Room conventions.

Use the existing screenshot data-layer conventions and extend that bounded
capability with a roster-image-specific DAO/repository contract. Do not create a
second roster repository or duplicate image-storage architecture.

## 9. Original-image and crop-metadata restore decision

A durable record preserves the validated original before writing metadata, using
an atomic app-private copy and a deterministic roster-only path beneath the
existing screenshot root, for example:
screenshots/<tournament>/roster/<position>/original.<extension>. The path
encodes the tournament and position safely, contains no match ID, and is
distinct from the existing match-only path.

On app restart, restore records by tournament and position order, including the
original reference, fingerprint where available, validation status, and crop
metadata. Do not persist an external source URI; it is session-scoped and is
not durable ownership proof. A missing, unreadable, inconsistent, or
invalid-crop record becomes controlled missing or invalid state. Do not guess,
recreate, OCR, or confirm it.

Replacement retains the last known-good durable record until the new original
copy and metadata write succeed. Removal affects only the exact
tournament/position record and its crop metadata, never another roster position
or a match screenshot.

## 10. Duplicate and incorrect association protection decision

* The composite Room identity prevents more than one roster image at a
  tournament position.
* Reject a fingerprint already associated with another position in the same
  tournament with a controlled duplicate state. Do not silently move, copy, or
  relabel it.
* Read, replace, and remove only by exact tournament ID and position. Do not
  infer ownership from a path, URI, or fingerprint.
* Persist only validated image candidates. A missing tournament, invalid index,
  failed candidate, or failed copy must not create a partial durable record.
* Cross-tournament fingerprint reuse neither proves a roster match nor creates
  a cross-tournament association.

## 11. Roster-vs-match screenshot separation decision

Roster screenshot metadata is tournament-scoped evidence and remains distinct
from match-result screenshot metadata.

* Do not associate a roster screenshot with matchId.
* Do not use ScreenshotMetadataEntity, its matchId primary key, match metadata
  DAO methods, or match upload state as roster metadata.
* Do not use match-only local paths, Storage paths, or Supabase metadata rows.
* Existing match screenshot linking, preservation, duplicate detection, upload,
  restoration, draft replacement, and finalized-match protection remain
  unchanged.

## 12. Room and schema decision

The Room 6-to-7 migration in section 8 is explicitly approved for the future
v0.7.9 implementation because current match-bound metadata cannot safely store
a tournament-owned three-position roster set. It may add only the roster
screenshot metadata table, focused indexes and constraints, DAO/repository
operations, and its exported schema.

The migration must preserve existing tournament, roster, match, scoring,
standings, authentication, sync, and match-screenshot metadata data. It must
not alter screenshot_metadata, its matchId identity, or an already-applied
migration. A migration test must prove existing-data survival and the new
identity, index-range, restoration, replacement, and deletion protections.

## 13. Supabase and backend decision

No Supabase Storage, database schema, RLS policy, metadata row, upload, sync,
or backend change is approved in v0.7.9. Existing private Storage and metadata
are match-owned; repository evidence does not establish safe roster-image
ownership, path, RLS, revision, cleanup, or recovery rules.

Local-only persistence is safe with the approved Room boundary, so it does not
justify a Supabase change. Roster image upload and sync are deferred to a later
approved backend/sync version that first defines ownership, private paths, RLS,
metadata, conflict, retention, and recovery rules.

## 14. Privacy and fixture decision

* Roster originals, private paths, fingerprints, crop metadata, and future OCR
  evidence are private tournament data and must not be logged unnecessarily.
* Preserve images only in app-private storage. Do not add broad storage,
  media-library, camera, or filesystem permissions.
* Do not commit real roster screenshots, player names, raw OCR payloads,
  private paths, signed URLs, or other private fixtures.
* The supplied sample roster screenshot is product evidence only and must not
  be copied into the repository without explicit privacy approval.
* Automated tests use synthetic sanitized values. Local-only representative
  screenshot evaluation remains subject to documented privacy rules.

## 15. Explicit exclusions

v0.7.9 does not implement:

* OCR, ML Kit execution, preprocessing, raw text preservation, parsing,
  visible-slot extraction, team or player matching, or roster OCR validation;
* fixed roster layout coordinates, automatic crop detection, inferred crop
  regions, or production review/correction UI;
* confirmed roster replacement, manual roster changes, scoring, standings,
  match creation, finalization, finalized-data protection, or correction
  workflow changes;
* Supabase Storage upload, Supabase schema or RLS changes, backend sync, cloud
  restoration, or a second storage or roster repository architecture;
* match metadata/path changes, changes to existing match screenshot behavior,
  or a roster-image-to-match association;
* real screenshots, real player-name fixtures, public URLs, external-file
  dependencies, or Android storage/media/camera permissions.

## 16. Testing and verification expectations

Future implementation must use synthetic data to cover:

* screenshot positions 1 through 3 and intended ranges 1–4, 5–8, and 9–12;
* complete and incomplete durable sets, invalid-index rejection, and safe
  tournament-scoped ordering;
* duplicate and incorrect association rejection where applicable;
* original and valid crop retention, restoration, replacement, clearing,
  missing-file handling, and invalid-restored-crop handling;
* Room DAO operations, the 6-to-7 migration, constraints, and existing-data
  preservation;
* app-restart restoration and ViewModel transitions for prior associations;
* regression that manual roster flow remains available; and
* regression that match metadata, linking, preservation, storage, duplicate
  detection, and restore behavior are unchanged.

Tests must not require ML Kit, OCR accuracy, preprocessing, parsing, slot
extraction, roster OCR validation, network, Supabase, real screenshots, real
player names, or external files. The future implementation must run relevant
unit, Room migration, instrumentation, lint, build, and git diff --check
verification. A device-only blockage must be reported accurately, not passed.

## 17. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Match metadata or paths could falsely represent roster evidence. | Use tournament-and-position identity, a roster-only private path, and no matchId. |
| One image could occupy two positions. | Reject same-tournament, different-position fingerprint duplicates before write. |
| A partial set could be treated as a roster. | Keep incomplete association state and prohibit OCR, parsing, validation, and confirmation. |
| Replacement or restore could lose original or crop state. | Copy atomically, retain last known-good state until success, and restore in position order. |
| Private content could enter source control or logs. | Use app-private handling, synthetic tests, and prohibit real screenshots and names without approval. |
| Cloud storage could gain an unsafe ownership model. | Defer Supabase roster screenshot operations to a dedicated approved decision. |

## 18. Acceptance criteria for implementation

v0.7.9 is acceptable only when it:

* associates validated roster image evidence with one tournament and positions
  1, 2, and 3 without matchId;
* preserves the metadata-only intended ranges 1–4, 5–8, and 9–12 without OCR,
  extraction, parsing, or validation;
* safely creates, replaces, removes, and restores local records using the
  approved Room migration and roster-only private paths;
* restores missing or invalid original/crop state without guessing or crashing;
* rejects invalid positions and same-tournament duplicate fingerprint
  associations without corrupting last known-good state;
* leaves manual roster and completed match screenshot behavior unchanged; and
* adds none of the excluded OCR, UI, backend, sync, scoring, finalization,
  permission, real-fixture, or unrelated persistence scope.

## 19. Next implementation action

After review and merge, the next approved implementation task may add only the
v0.7.9 tournament-scoped durable association boundary, including the approved
Room 6-to-7 migration and local restore. It must inspect the current database
version and match screenshot contracts before editing and stop if this boundary
cannot be implemented safely.

Representative roster screenshots and manually verified ground truth remain
blockers for v0.8.9 cropped roster layout definition. OCR extraction remains
deferred to v0.8.10, roster parsing to v0.8.11, and roster OCR validation to
v0.8.13. Review and correction UI remain deferred to Phase 9. Confirmed roster
persistence remains deferred to v0.5.8 and v0.6.9 plus the Phase 9 confirmation
flow. Supabase roster screenshot upload and sync remain deferred unless a later
explicitly approved backend ownership, Storage, and RLS decision authorizes
them.

