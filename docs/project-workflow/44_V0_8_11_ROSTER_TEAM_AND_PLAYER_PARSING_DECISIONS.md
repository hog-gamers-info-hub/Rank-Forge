# v0.8.11 — Roster Team and Player Parsing Decisions

## 1. Status

Approved implementation decision gate for the Phase 8 roster OCR extension, v0.8.11. It follows the completed v0.8.9 Cropped Roster Layout Definition and v0.8.10 Roster Raw OCR Extraction work.

This document authorizes roster candidate text parsing only. It does not authorize roster confirmation, persistence, synchronization, review UI, or any change to match-result OCR behavior.

## 2. Version scope

v0.8.11 consumes v0.8.10 roster raw OCR evidence and produces non-authoritative roster text candidates. A candidate is available for later validation, review, and confirmation only; it is not a roster record, a confirmed team or player, or a replacement for manual roster entry.

This version parses only the approved roster slot and player-row regions within a prepared cropped roster panel. It must not run ML Kit directly, process a full screenshot, change v0.8.9 layout regions, or change v0.8.10 raw OCR extraction behavior.

## 3. Canonical sources reviewed

This decision was reviewed against:

* `AGENTS.md` and `README.md`;
* `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`;
* `docs/project-workflow/37_ROSTER_SCREENSHOT_OCR_ROADMAP_DECISIONS.md`;
* `docs/project-workflow/42_V0_8_9_CROPPED_ROSTER_LAYOUT_DEFINITION_DECISIONS.md` and `docs/project-workflow/43_V0_8_10_ROSTER_RAW_OCR_EXTRACTION_DECISIONS.md`;
* the completed v0.8.5 match-result player-name parsing decision and implementation;
* the current roster cropped-layout, raw OCR extraction, and Phase 7 roster screenshot intake, crop, and set-association contracts;
* the current manual roster workflow and its four-to-six player-count rules; and
* relevant OCR, Android, testing, privacy, security, and AI workflow documents.

## 4. Relationship to v0.8.9 cropped roster layout

v0.8.9 remains the sole authority for prepared-crop input, roster screenshot positions 1–3, visible slot positions, intended tournament-slot metadata, and the four visible player-row regions per slot. v0.8.11 must preserve that metadata exactly and must not add, recalibrate, infer, or otherwise change layout geometry or crop coordinates.

The fixed screenshot-position plus visible-slot-position mapping remains positional metadata only. Parsing must not use it to confirm that text belongs to a roster record; candidate-to-slot association remains deferred to v0.8.12.

## 5. Relationship to v0.8.10 raw OCR extraction

v0.8.11 consumes only v0.8.10 typed roster raw OCR evidence and outcomes. It must preserve screenshot position, visible slot position, intended tournament-slot metadata, region identity, and references to the raw source evidence in its candidate output.

The parser must not call ML Kit, crop images, preprocess images, process full screenshots, or alter raw extraction ordering, text, hierarchy, geometry, language, confidence, empty outcomes, or failure behavior. The raw extraction boundary remains responsible for all recognizer interaction.

## 6. Candidate parsing boundary

Implementation must use roster-specific candidate parsing contracts when the existing v0.8.5 match-result player parser is too scoreboard-specific. The roster parser should be pure domain logic with v0.8.10 raw evidence as input and synthetic tests.

For every visible roster slot, output must include an optional or unavailable team-name candidate and candidates for player rows 1, 2, 3, and 4. Each candidate result must retain its positional metadata, raw evidence reference, parse status, and confidence value only when supplied by the raw evidence; otherwise confidence is explicitly unavailable.

The parser may consume slot-region evidence and player-row-region evidence only. Slot-region evidence may provide source context, but it must not be converted into a team or player candidate by inference. Text outside an expected player-row region must not be guessed into a player row.

Candidate parsing does not decide whether a complete roster is valid. Completeness, cross-candidate validation, and release decisions belong to v0.8.13.

## 7. Team-name visibility decision

The approved v0.8.9 layout and v0.8.10 evidence model contain no dedicated, evidenced visible team-name region. Therefore, v0.8.11 must represent the team-name candidate for every visible slot as unavailable or unsupported.

The implementation must not invent a team name from player names, slot content, prefixes, clan tags, repeated text, or any other inferred relationship. Team-name extraction remains unsupported until a dedicated visible team-name region is established by privacy-approved representative evidence and a later explicit layout decision.

## 8. Player-name parsing decision

v0.8.11 may parse player-name candidates only from the four evidenced `PLAYER_ROW` regions for each visible slot. The parser must retain raw source evidence rather than claiming a confirmed identity.

Missing row evidence, empty text, multiple OCR fragments in one row, duplicate text, malformed text, uncertain text, or text outside the expected player-row regions must be represented as candidate parser outcomes without guessing. The parser must not automatically correct a name, fuzzy-match a name, or use a player candidate as a team name.

Five- and six-player rows are not evidenced by the approved layout. They are unsupported by this version and remain a deferred or manual-correction-only case.

## 9. Player-row ordering decision

Player-row output must preserve the fixed visible order 1, 2, 3, and 4 for every slot. A missing, empty, invalid, ambiguous, duplicate, or uncertain earlier row must not cause later rows to be shifted, collapsed, or renumbered.

The parser must retain the source row index and visible slot position so later phases can review the candidate in its original evidenced context.

## 10. Text preservation and normalization decision

Candidate parsing may trim surrounding whitespace only. It must preserve the recognized symbols, case, punctuation, clan tags, and special characters in the candidate text and raw evidence.

The parser must not aggressively rewrite OCR text, fuzzy-match names, autocorrect names, normalize away meaningful characters, merge unrelated fragments, or silently select one value from conflicting text. Any conservative interpretation needed to represent fragments or malformed text must remain visible through the candidate status and raw evidence reference.

## 11. Confidence and uncertainty decision

ML Kit confidence values must not be invented, estimated, defaulted, ranked, or thresholded by v0.8.11. If confidence is unavailable in the v0.8.10 raw evidence, it remains explicitly unavailable in the candidate output.

Ambiguous, empty, missing, duplicate, malformed, and uncertain text are candidate parser outcomes only. They are not validation findings, confirmation decisions, or reasons to modify roster data. Full validation of those outcomes remains v0.8.13 work.

## 12. Failure handling decision

The candidate parser must handle missing raw evidence, empty extraction outcomes, typed extraction failures, missing expected player-row evidence, multiple fragments, and unsupported team or fifth/sixth-player cases safely and deterministically. It must return typed candidate outcomes or preserve the applicable unavailable/unsupported state rather than crash, fabricate text, or guess an identity.

No failure path may persist roster data, alter raw evidence, expose private image paths or image bytes, or convert an unavailable confidence value into a score. Cancellation and recognizer failures remain the responsibility of the v0.8.10 extraction boundary and must not be recast as successful candidate data.

## 13. Privacy and fixture decision

Real roster screenshots, real player names, raw OCR payloads, private local paths, signed URLs, and manually verified ground truth must not be committed without explicit privacy approval. Automated tests must use synthetic raw evidence, synthetic metadata, and synthetic names only.

Private representative screenshot evaluation may occur locally only under the documented privacy rules. It is not a committed fixture, must not be required for v0.8.11 tests, and does not authorize real-name persistence or OCR-quality claims.

## 14. Explicit exclusions

v0.8.11 must not add or change:

* ML Kit execution, raw OCR extraction, image preprocessing, full-screenshot processing, crop-coordinate guessing, or v0.8.9 layout geometry;
* team or player fuzzy matching, automatic name correction, candidate-to-slot association beyond preserved v0.8.10 metadata, or complete roster validation;
* review or correction UI, confirmation behavior, confirmed roster persistence, Room schema or migration work, Supabase or backend changes, upload, synchronization, export, scoring, standings, or match finalization;
* real screenshot fixtures, real player-name fixtures, cloud OCR, network behavior, or external files; or
* the completed match-result scoreboard player-name parsing, OCR, manual roster workflow, finalized-data protection, correction workflows, or Phase 7 screenshot behavior.

## 15. Testing and verification expectations

Future v0.8.11 implementation tests must use synthetic v0.8.10 raw OCR evidence only and verify:

* candidate parsing for player rows 1–4, including preserved row order and no shifting after missing or invalid rows;
* preservation of symbols, case, punctuation, clan tags, and special characters apart from surrounding-whitespace trimming;
* safe typed outcomes for missing or empty row evidence, multiple fragments in one row, duplicate, malformed, and uncertain text, and text outside expected player-row regions;
* unavailable or unsupported team-name candidates when no dedicated team-name region exists;
* unsupported or deferred fifth/sixth-player rows;
* preservation of screenshot position, visible slot position, intended tournament-slot metadata, region identity, and raw evidence references;
* explicitly unavailable candidate confidence when raw confidence is unavailable; and
* regression protection that the existing match-result player-name parser remains unchanged.

Tests must not require ML Kit execution, OCR accuracy, network access, Google Play services, Room migrations, Supabase, real screenshots, real names, or external files.

## 16. Risks and mitigations

| Risk | Approved mitigation |
| --- | --- |
| Roster parsing changes completed scoreboard player-name behavior. | Use roster-specific candidate contracts where the scoreboard parser is shape-specific and retain regression coverage. |
| Raw OCR text is mistaken for a confirmed roster identity. | Mark every result as candidate-only and preserve its raw evidence reference and parse status. |
| A team name is inferred without an evidenced team-name region. | Return unavailable or unsupported team candidates and prohibit prefix, clan-tag, repeated-text, and player-name inference. |
| Missing or conflicting row evidence shifts player order. | Retain explicit row indices 1–4 and represent the affected row's outcome without collapsing the list. |
| Missing confidence is treated as a quality decision. | Preserve only supplied confidence and explicitly represent all other confidence as unavailable. |
| Private screenshots or names enter source control. | Use synthetic automated fixtures only and keep any representative evaluation local and privacy-controlled. |

## 17. Acceptance criteria for implementation

v0.8.11 implementation is acceptable only when:

* it adds a pure, roster-specific candidate parsing boundary that consumes v0.8.10 raw evidence without invoking ML Kit or changing raw extraction;
* it produces non-authoritative candidate fields per visible slot: an unavailable or unsupported team candidate and four ordered player-row candidates;
* every candidate retains screenshot position, visible slot position, intended tournament-slot metadata only, region identity, raw evidence reference, parse status, and unavailable confidence where applicable;
* player text is parsed only from the four evidenced player-row regions, preserves row order and conservative text, and does not guess missing, conflicting, out-of-region, fifth, or sixth rows;
* team candidates remain unavailable or unsupported when no dedicated team-name region exists;
* focused synthetic tests cover candidate, metadata, text-preservation, unavailable-confidence, failure, and scoreboard-regression behavior; and
* no validation, association, UI, confirmation, persistence, Room, Supabase, scoring, standings, finalization, real-fixture, or full-screenshot behavior is introduced.

## 18. Next implementation action

After this decision document is reviewed and merged, and with explicit user approval, the next v0.8.11 implementation task is limited to roster-specific candidate parsing contracts, pure domain parsing from v0.8.10 raw evidence, typed candidate outcomes, and focused synthetic tests.

v0.8.12 will map parsed roster candidates to tournament-slot candidates later. v0.8.13 will validate missing, invalid, duplicate, and uncertain candidates later. Phase 9 will provide review and correction before confirmation. v0.5.8 and v0.6.9 remain responsible for confirmed-roster persistence and synchronization safety.

Team-name extraction remains unsupported until a dedicated evidenced team-name region exists. Five- and six-player extraction remains unsupported until representative evidence exists. Real roster OCR-quality evaluation remains deferred to Phase 12 unless separately approved.
