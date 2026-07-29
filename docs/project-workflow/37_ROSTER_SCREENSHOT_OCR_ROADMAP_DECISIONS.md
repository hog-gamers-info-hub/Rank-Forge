# Roster Screenshot OCR Roadmap Decisions

## 1. Status

Approved documentation decision gate for the proposed roster screenshot OCR
extension. This gate records the approved cross-phase direction only. It does
not amend the canonical roadmap, reopen completed implementation work, or
authorize implementation.

Implementation may begin only after this decision is approved and merged, the
required canonical documentation is updated in a later approved task, and the
user explicitly approves the applicable version.

## 2. Reason for decision gate

Phase 8 closed after v0.8.8, but the manual roster-entry workflow is impractical
when team and player data already exists in roster screenshots. The intended
workflow is:

1. Select three roster screenshots.
2. Preserve each original privately.
3. Let the operator crop the roster panel in-app.
4. Extract roster candidates from the cropped panel only.
5. Associate candidates with fixed slots 1 through 12.
6. Validate and review the candidate roster.
7. Allow correction, abandonment, or explicit confirmation.
8. Persist only the confirmed roster through approved existing Room and
   Supabase roster workflows.

Manual team and player entry remains available for corrections, incomplete or
unsupported screenshots, and every fallback case. OCR output is candidate data
only and must never silently overwrite or finalize a roster.

## 3. Sources reviewed

This gate was reviewed against:

- `AGENTS.md` and `README.md`;
- `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md` and
  `36_PHASE_8_CLOSURE_AUDIT.md`;
- Phase 8 decision documents 27 through 35;
- Phase 7 decision documents 19 through 25 and their current match-screenshot
  intake, preservation, Storage, and metadata implementations;
- the current tournament, manual team-entry, roster-entry, and roster-review
  workflows;
- Room roster, screenshot-metadata, repository, migration, and transaction
  contracts;
- Supabase roster snapshot, restoration, revision-conflict, ownership, RLS,
  and screenshot-storage contracts;
- current OCR preprocessing, raw extraction, parsing, and review contracts;
- product, architecture, database, Android, security, testing, and AI workflow
  documents.

## 4. Completed Phase 8 closure state

Phase 8 v0.8.0 through v0.8.8 remains complete, merged, closed, and protected.
The existing bundled ML Kit boundary, fixed scoreboard layout, preprocessing,
raw extraction, placement/player-name/kill parsers, failure analyzer, and
test-only scoreboard evaluation must not be rewritten or reinterpreted.

This decision does not alter Phase 1 through Phase 8 history. Existing
match-result screenshot OCR remains scoreboard-specific and must continue to
operate independently from roster screenshot OCR.

## 5. Audit findings adopted

The following read-only audit findings are adopted:

- The original proposal to add v0.8.9 through v0.8.16 entirely inside Phase 8
  is not phase-aligned and is rejected as written.
- Existing screenshot intake, preservation, metadata, and Storage contracts
  are match-bound; they do not represent a three-image tournament roster set or
  distinguish roster screenshots from match-result screenshots.
- The existing image preprocessor and player-name parser are tied to the
  two-panel match-result scoreboard layout and cannot be repurposed as roster
  processing by implication.
- The ML Kit adapter and raw OCR evidence boundary may be reused only through
  new roster-specific contracts that do not alter scoreboard behavior.
- Existing Room roster writes replace one slot at a time. Existing cloud roster
  snapshot writes upsert deterministic player rows but do not remove stale rows
  from a shorter replacement roster.
- The current roster-edit path can return a confirmed tournament to draft
  without checking for created or finalized matches. A roster replacement
  safety policy is therefore required before persistence work.

## 6. Product workflow decision

Roster screenshot OCR is approved only as a staged cross-phase extension,
pending user approval and a later canonical roadmap update. It is not an
automatic continuation of the completed Phase 8 work.

The extension supports exactly three roster screenshots, each expected to show
four team slots, for a fixed 12-slot tournament roster. This is the approved
workflow target for the extension, not a claim that every future layout is
supported. Each visible slot may provide one team name and four to six player
names.

Manual roster entry and its existing review/correction path remain available
and protected. No OCR result becomes a roster record, a confirmed roster, a
match value, scoring input, standing input, or finalization action without
explicit operator review and confirmation.

## 7. Manual in-app crop decision

Manual in-app crop is required before roster OCR. Full roster screenshots may
contain lobby panels, chat text, account/header UI, start or invite controls,
and other non-roster game UI.

- Preserve the original selected roster image privately.
- Allow the operator to choose the roster-panel crop in-app before OCR.
- OCR may use only a cropped app-private OCR image or the approved normalized
  crop metadata needed to reproduce that crop; it must not process the full
  screenshot as roster evidence.
- Crop selection is user-controlled. No full-screen crop coordinates may be
  guessed from the supplied sample or any single image.
- Crop preparation itself must not confirm, alter, or persist roster data.

## 8. Screenshot and ground-truth prerequisite decision

Representative roster screenshots and manually verified expected data are
mandatory prerequisites for fixed roster-layout coordinates and extraction
accuracy work.

The repository currently contains no approved representative roster screenshot
set or manually verified ground truth. Until those inputs exist, v0.8.9 is
blocked and no crop zones, team/player regions, supported resolutions, or OCR
accuracy claims may be defined. Unsupported layouts remain manual-entry cases.

## 9. Roadmap extension decision

The extension must be opened in the phases that own the affected behavior:

- Phase 7 owns roster image selection, validation, original preservation,
  manual crop preparation, set identity, image-type distinction, and local
  restore behavior.
- Phase 8 owns cropped roster layout, roster-specific preprocessing/raw OCR,
  candidate team/player parsing, deterministic slot association, and OCR
  validation.
- Phase 5 owns atomic local confirmed-roster replacement.
- Phase 6 owns revision-safe cloud replacement, stale-player deletion,
  ownership, RLS, queue, idempotency, conflict, restoration, and rollback
  behavior.
- Phase 9 owns roster OCR review, correction, abandonment, and explicit
  confirmation UI.
- Phase 12 owns real roster OCR acceptance evaluation.

## 10. Phase-boundary ownership decision

The cross-phase work must not be forced into Phase 8 merely because it uses
OCR. Each phase retains its established responsibility, and no phase may
silently absorb another phase's persistence, UI, backend, or acceptance scope.

Team-name fuzzy matching against independently registered teams, match-result
team/player matching, scoring, standings, match finalization, and unsupported
layout OCR remain outside this extension unless separately approved in their
canonical phases.

## 11. Approved version placement

### Phase 7 extension

- **v0.7.7 — Roster Screenshot Intake:** Select exactly three roster
  screenshots, validate image candidates, preserve originals privately, and
  associate them with one tournament.
- **v0.7.8 — Roster Screenshot Crop Preparation:** Add manual in-app crop for
  each roster screenshot, crop metadata, and cropped OCR-image preparation. No
  OCR is allowed in this version.
- **v0.7.9 — Roster Screenshot Set Association:** Store roster screenshot set
  ordering, distinguish roster images from match-result images, protect
  duplicate or incorrect associations, and support local restore behavior.

### Phase 8 extension

- **v0.8.9 — Cropped Roster Layout Definition:** Define one supported cropped
  roster-panel layout for four visible team slots per screenshot. This version
  is blocked until representative screenshots and ground truth exist.
- **v0.8.10 — Roster Raw OCR Extraction:** Reuse the ML Kit/raw OCR boundary
  through roster-specific preprocessing from cropped roster panels only.
- **v0.8.11 — Roster Team and Player Parsing:** Parse candidate team names and
  four to six player names per visible slot from roster OCR evidence.
- **v0.8.12 — Roster Slot Association:** Map screenshot number plus visible
  slot position to fixed tournament slots 1 through 12.
- **v0.8.13 — Roster OCR Validation:** Detect missing team/player names,
  invalid player counts, empty slots, malformed text, uncertainty, duplicate
  teams, and policy-defined duplicate-player issues.

### Phase 5 extension

- **v0.5.8 — Atomic Confirmed Roster Replacement:** Add safe local all-12-slot
  confirmed roster replacement after explicit confirmation, retaining old data
  until commit succeeds and respecting match/finalized-match safety policy.

### Phase 6 extension

- **v0.6.9 — Revision-Safe Roster Sync Replacement:** Add safe Supabase roster
  replacement semantics, stale-player deletion, revision conflict handling,
  rollback behavior, ownership/RLS protection, queue/idempotency behavior, and
  restoration safety.

### Phase 9

- **v0.9.x — Roster OCR Review and Correction:** Present extracted roster
  candidates, highlight invalid or uncertain fields, allow correction or
  abandonment, and require explicit confirmation before persistence.

### Phase 12

- **v0.12.x — Real Roster OCR Acceptance Evaluation:** Evaluate real roster
  screenshots using approved representative screenshots and manually verified
  expected data.

The approved implementation order is:

1. Product and roadmap decision gate.
2. Representative roster screenshot and ground-truth collection.
3. Phase 7 extension v0.7.7 through v0.7.9.
4. Phase 8 extension v0.8.9 through v0.8.13.
5. Phase 5 extension v0.5.8.
6. Phase 6 extension v0.6.9.
7. Phase 9 roster OCR review and correction.
8. Phase 12 real roster OCR acceptance evaluation.
9. Updated closure or reclosure audit for each affected phase as needed.

## 12. Explicitly rejected original sequence

The original all-Phase-8 sequence v0.8.9 through v0.8.16 is rejected as-is.
It incorrectly places image lifecycle work, production review UI, atomic local
persistence, cloud replacement semantics, and real acceptance evaluation in
the OCR parsing phase.

The Phase 8 names v0.8.9 through v0.8.13 above are approved only as future
placement following the Phase 7 prerequisites. The original proposed v0.8.14,
v0.8.15, and v0.8.16 are respectively reassigned to Phase 9, Phases 5 and 6,
and Phase 12.

## 13. Persistence, overwrite, and finalized-match safety decision

Existing Room and Supabase roster persistence must be extended, not duplicated.
No roster OCR candidate may write through the existing per-slot replacement
path until v0.5.8 and v0.6.9 establish the approved safety contracts.

Roster OCR must not silently overwrite a confirmed roster. Replacement after
created or finalized matches is prohibited until an explicit safety policy
defines eligibility, warnings, revision behavior, conflict handling, rollback,
and finalized-match protection. Existing finalized match protection, correction
workflows, manual match processing, scoring, standings, authentication, cloud
sync, and Phase 7 match-screenshot behavior remain unchanged.

## 14. Duplicate-player policy decision

Cross-team duplicate-player behavior remains unresolved. Existing documentation
requires duplicate-player detection but defers the exact cross-team rule, and
the current validator detects duplicates within an individual team.

No roster OCR version may invent a cross-team duplicate policy. v0.8.13 may
report policy-defined duplicate-player issues only after a canonical product
decision resolves whether the same player name can legitimately appear in
multiple teams.

## 15. Privacy and fixture policy

- Original roster screenshots are private evidence and must be preserved only
  through approved owner-scoped local and cloud storage contracts.
- Real screenshots, real player names, raw OCR payloads, private paths, signed
  URLs, and ground-truth fixtures must not be committed without explicit
  privacy approval.
- Synthetic, sanitized fixtures remain required for automated tests.
- Approved genuine evaluation inputs remain local-only or otherwise explicitly
  privacy-controlled until a later canonical policy states otherwise.
- No secrets, storage credentials, or privileged backend credentials may enter
  Android code, tests, documentation, or reports.

## 16. Testing and verification policy

Each approved version must use focused tests appropriate to its owner phase:

- Phase 7: Photo Picker, image validation, crop-state, three-image cardinality,
  set ordering, original preservation, restore, duplicate, metadata, and
  match-versus-roster association tests.
- Phase 8: synthetic layout, crop-metadata, preprocessing, raw OCR test-double,
  parsing, slot-association, malformed/empty/uncertain evidence, and validation
  tests without OCR accuracy claims.
- Phase 5: Room transaction, migration, replacement rollback, app-restart, and
  existing-match/finalized-match safety tests.
- Phase 6: Supabase migration, schema, ownership/RLS, stale-player deletion,
  revision conflict, idempotency, queue, retry, restoration, and rollback tests.
- Phase 9: ViewModel, navigation, Compose UI, accessibility, correction,
  abandon, explicit-confirmation, and manual-entry regression tests.
- Phase 12: synthetic evaluator tests plus approved local-only real-screenshot
  evaluation against manually verified expected data.

Relevant unit, instrumentation, connected-device, Room, Supabase, RLS,
security, sync, regression, and manual verification must be specified by each
future version decision. No version may claim real OCR accuracy before the
required representative screenshots and verified ground truth exist.

## 17. Documentation updates required before implementation

After this decision is approved and merged, a separate documentation task must
make the exact canonical changes to:

- `README.md`;
- product, architecture, database, OCR, Android, testing, and security
  documents;
- relevant AI scope, security, and testing workflow documents;
- `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`; and
- the affected phase closure or reclosure records.

That task must record the approved scope change without rewriting completed
Phase 1 through Phase 8 history. It must also record the screenshot/ground-
truth prerequisite, supported-layout policy, duplicate-player policy status,
and overwrite/finalized-match safety decisions before any implementation task.

## 18. Acceptance criteria for this decision gate

This decision gate is complete only when it:

- preserves the completed Phase 1 through Phase 8 workflow and Phase 8 closure;
- rejects the original all-Phase-8 v0.8.9 through v0.8.16 proposal;
- records the staged Phase 7, 8, 5, 6, 9, and 12 ownership and version
  placement;
- requires manual in-app crop and private original preservation;
- blocks roster layout and extraction-accuracy work on representative
  screenshots and manually verified ground truth;
- preserves manual roster entry and makes OCR output review-required candidate
  data only;
- protects existing match screenshot OCR and scoreboard behavior; and
- records persistence, overwrite, finalized-match, duplicate-player, privacy,
  and verification constraints without changing implementation.

## 19. Next action after approval

After this decision is approved and merged, obtain explicit user approval for a
separate canonical documentation-alignment task. That task must update the
roadmap and governing product scope before implementation. The next operational
prerequisite is privacy-approved collection of representative roster screenshots
and manually verified ground truth outside version control; it is not an
implementation task and does not authorize coordinate guessing.
