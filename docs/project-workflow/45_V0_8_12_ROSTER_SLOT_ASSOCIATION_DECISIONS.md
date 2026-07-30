# v0.8.12 — Roster Slot Association Decisions

## 1. Status

Approved implementation decision gate for the Phase 8 roster OCR extension, v0.8.12. It follows the completed v0.8.9 Cropped Roster Layout Definition, v0.8.10 Roster Raw OCR Extraction, and v0.8.11 Roster Team and Player Parsing work.

This document authorizes deterministic association of roster candidates to fixed tournament-slot candidates only. It does not authorize validation, review, confirmation, persistence, synchronization, or any change to completed roster or match-result behavior.

## 2. Version scope

v0.8.12 consumes v0.8.11 roster candidate-parser output and produces an ordered roster-slot candidate set. The output remains candidate data only: it is not a confirmed roster, an update to a manually entered roster, or a replacement for manual roster entry.

The association boundary must use metadata only. It must not run ML Kit, process images or full screenshots, infer text, change v0.8.9 layout behavior, change v0.8.10 raw extraction behavior, or change v0.8.11 parsing behavior.

## 3. Canonical sources reviewed

This decision was reviewed against:

* `AGENTS.md` and `README.md`;
* `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`;
* `docs/project-workflow/37_ROSTER_SCREENSHOT_OCR_ROADMAP_DECISIONS.md`;
* `docs/project-workflow/42_V0_8_9_CROPPED_ROSTER_LAYOUT_DEFINITION_DECISIONS.md`, `docs/project-workflow/43_V0_8_10_ROSTER_RAW_OCR_EXTRACTION_DECISIONS.md`, and `docs/project-workflow/44_V0_8_11_ROSTER_TEAM_AND_PLAYER_PARSING_DECISIONS.md`;
* the current v0.8.9 cropped roster layout, v0.8.10 raw OCR extraction, and v0.8.11 roster candidate-parser contracts;
* the current fixed 12-team-slot model and manual roster workflow, including its four-to-six player-count rules; and
* relevant OCR, Android, testing, privacy, security, and AI workflow documents.

## 4. Relationship to v0.8.9 cropped roster layout

v0.8.9 remains the authority for roster screenshot positions 1–3, the four visible slot positions, their fixed reading order, and the intended tournament-slot ranges. v0.8.12 must consume that metadata without adding, recalibrating, guessing, or changing crop or layout geometry.

The association boundary must not process prepared crops, derive coordinates, or treat full screenshots as input. It consumes only the candidate metadata already derived from the approved cropped-panel layout.

## 5. Relationship to v0.8.10 raw OCR extraction

v0.8.10 remains the authority for raw evidence, region identity, raw extraction outcomes, and confidence availability. v0.8.12 must not call ML Kit or otherwise alter extraction, preprocessing, raw text, raw hierarchy, region order, confidence, or extraction failures.

Raw evidence references carried by v0.8.11 candidates must remain attached to the associated tournament-slot candidate. Association must not fabricate OCR text, confidence, or evidence.

## 6. Relationship to v0.8.11 candidate parsing

v0.8.11 remains the authority for candidate player text, player-row order, parse statuses, team-name unavailable or unsupported state, and parser input-failure outcomes. v0.8.12 consumes those contracts unchanged.

Association may order and identify a candidate by its fixed tournament slot only. It must preserve the original candidate status and raw evidence references, and must not reparse, normalize, correct, fuzzy-match, or validate names.

## 7. Deterministic association boundary

Implementation must add pure-domain association contracts that accept v0.8.11 parsed candidates and return ordered tournament-slot candidates plus typed association outcomes. For a valid candidate, the target slot is determined solely by its roster screenshot position and visible slot position.

The same candidate metadata and input ordering must always produce the same ordered result. Output must be sorted by tournament slot number ascending, not by recognized text, confidence, team name, or incidental raw-evidence order.

The boundary must expose tournament-slot candidates for slots 1–12 where available. Each candidate must retain its tournament slot number, source screenshot position, source visible slot position, optional or unavailable team-name candidate, player-row candidates 1–4, original parse statuses, raw evidence references, and association status.

## 8. Screenshot-position mapping decision

The approved screenshot-position mapping is fixed:

| Roster screenshot position | Tournament-slot range |
| --- | --- |
| 1 | 1–4 |
| 2 | 5–8 |
| 3 | 9–12 |

Only screenshot positions 1, 2, and 3 are supported. Missing positions produce partial candidate output and typed missing outcomes. Invalid, unknown, or inconsistent screenshot-position metadata must fail safely as a typed association failure; it must not be coerced into a valid range.

## 9. Visible-slot mapping decision

Within every supported screenshot position, the visible-slot offset is fixed:

| Visible position | Offset |
| --- | --- |
| Top left | 1 |
| Top right | 2 |
| Bottom left | 3 |
| Bottom right | 4 |

No alternate reading order is permitted. Missing visible positions produce partial output and typed missing outcomes. Invalid, unknown, duplicate, or inconsistent visible-slot metadata must fail safely and must not be assigned by list position, recognized slot-number text, or inference.

## 10. Tournament-slot candidate decision

For each supported source candidate, the association is `range first + visible-slot offset - 1`. The combined mapping yields only fixed tournament slot numbers 1–12.

An associated tournament-slot candidate must preserve the source screenshot position and visible slot position as well as the intended slot metadata already carried by v0.8.11. If that existing metadata conflicts with the deterministic mapping, association must return a typed conflict or failure rather than silently selecting one value. Candidates outside slots 1–12 must be rejected.

Association is positional candidate metadata only. It does not confirm that a candidate belongs to a manual roster slot or that any team/player text is correct.

## 11. Incomplete and duplicate association decision

Incomplete screenshot sets or missing visible slots must produce partial ordered candidate output plus explicit typed missing association outcomes. The boundary must not manufacture absent candidates or fill missing slots from neighboring screenshots or visible positions.

More than one source candidate resolving to the same tournament slot is a duplicate or conflict. It must be retained as a typed association conflict or failure with source candidates available for later review; it must not silently overwrite, merge, select, or discard one candidate. v0.8.12 does not decide whether duplicate player or team text is valid.

## 12. Team-name unavailable decision

The v0.8.11 team-name unavailable or unsupported state must be preserved exactly in the associated tournament-slot candidate. v0.8.12 must not infer a team name from player candidates, slot-content evidence, prefixes, clan tags, repeated text, tournament-slot numbers, or any other source.

Team-name extraction remains unsupported unless a dedicated, evidenced team-name region is approved in a later layout decision.

## 13. Player candidate association decision

v0.8.12 associates only the four evidenced player-row candidates 1–4 for each visible slot. Their order, candidate text, parse statuses, confidence availability, region identity, and raw evidence references must remain unchanged.

Five- and six-player rows are not available in the v0.8.9/v0.8.10/v0.8.11 contracts. They remain unsupported, deferred, and manual-correction-only. Association must not add rows, shift rows, infer players, validate player counts, or alter manual roster rules.

## 14. Failure and uncertainty decision

Association outcomes must be typed, project-owned, and safe for missing screenshot positions, missing visible slots, invalid or inconsistent source metadata, duplicate target slots, and candidates outside 1–12. Existing v0.8.11 parse statuses and parser input failures must be preserved rather than collapsed into an association success.

An association status records only positional association state. It must not validate empty, malformed, duplicate, uncertain, or ambiguous player/team candidates; those validation decisions belong to v0.8.13. No failure path may fabricate candidate data, modify raw evidence, persist a roster, or expose private image paths, image bytes, or sensitive OCR content.

## 15. Privacy and fixture decision

Real roster screenshots, real player names, raw OCR payloads, private local paths, signed URLs, and manually verified ground truth must not be committed without explicit privacy approval. Automated tests must use synthetic v0.8.11 candidate data, metadata, and names only.

Private representative evaluation remains local-only and subject to the documented privacy rules. It is not a committed fixture, is not required for v0.8.12 tests, and does not authorize roster confirmation or OCR-quality claims.

## 16. Explicit exclusions

v0.8.12 must not add or change:

* ML Kit execution, raw OCR extraction, image preprocessing, parsing, layout geometry, crop behavior, full-screenshot processing, or crop-coordinate guessing;
* full roster validation, duplicate-name validation, player-count validation, team/player fuzzy matching, automatic name correction, or candidate-to-roster confirmation;
* review or correction UI, confirmed roster persistence, Room schema or migration work, Supabase/backend changes, upload, synchronization, export, scoring, standings, or match finalization;
* real screenshot fixtures, real player-name fixtures, network behavior, cloud OCR, or external files; or
* the v0.8.9 layout, v0.8.10 extraction, v0.8.11 parsing, existing match-result OCR, Phase 7 screenshot lifecycle, or manual roster workflow.

## 17. Testing and verification expectations

Future v0.8.12 implementation tests must use synthetic v0.8.11 candidate output only and verify:

* screenshot-position mapping 1→1–4, 2→5–8, and 3→9–12;
* visible-position offsets for top-left, top-right, bottom-left, and bottom-right;
* combined deterministic mapping to tournament slots 1–12 and stable ascending output order;
* partial output and typed outcomes for incomplete screenshot sets and missing visible slots;
* safe typed handling of invalid or inconsistent source metadata and candidates outside 1–12;
* duplicate or conflicting target-slot candidates are preserved as conflicts and never silently overwritten;
* preservation of unavailable or unsupported team-name candidates, player rows 1–4, parse statuses, raw evidence references, and confidence availability;
* no validation of player counts, duplicate player names, duplicate team names, empty names, malformed names, or complete roster validity; and
* regression protection that the v0.8.11 parser remains unchanged.

Tests must not require ML Kit execution, OCR accuracy, network access, Google Play services, Room migrations, Supabase, real screenshots, real names, or external files.

## 18. Risks and mitigations

| Risk | Approved mitigation |
| --- | --- |
| Candidate order changes between runs. | Use only the approved screenshot and visible-slot metadata and sort output by fixed tournament slot number. |
| Duplicate source candidates silently replace evidence. | Return a typed association conflict and retain the affected source candidates for later review. |
| Missing screenshot or visible-slot data is guessed. | Produce partial output with typed missing outcomes and never fill a position by inference. |
| Candidate association is mistaken for roster validation or confirmation. | Keep the output candidate-only and defer validation to v0.8.13 and review to Phase 9. |
| Unsupported team-name evidence becomes a team name. | Preserve the unavailable or unsupported team candidate and prohibit inference. |
| Roster OCR changes manual or match-result workflows. | Keep this as a pure roster-specific domain boundary with regression protection for existing contracts. |
| Private roster data enters source control. | Use synthetic test data only and keep representative evaluation local and privacy-controlled. |

## 19. Acceptance criteria for implementation

v0.8.12 implementation is acceptable only when:

* it adds a pure-domain association boundary that consumes v0.8.11 candidates without running ML Kit or processing images;
* it deterministically maps screenshot positions 1–3 and the approved visible-slot offsets to tournament slots 1–12 only;
* it returns an ordered tournament-slot candidate set that preserves source metadata, team-name unavailable state, player rows 1–4, parse statuses, raw evidence references, confidence availability, and association status;
* incomplete input returns partial candidates with typed missing outcomes, and duplicate/conflicting mappings return typed association conflicts without silent overwrite;
* invalid, unknown, inconsistent, or out-of-range source metadata fails safely without inference;
* focused synthetic tests cover mapping, order, partial/conflict/failure handling, preservation, and v0.8.11 regression protection; and
* no validation, matching, correction UI, confirmation, persistence, Room, Supabase, scoring, standings, finalization, real-fixture, or full-screenshot behavior is introduced.

## 20. Next implementation action

After this decision document is reviewed and merged, and with explicit user approval, the next v0.8.12 implementation task is limited to pure-domain roster-slot association contracts, deterministic metadata-based mapping, typed incomplete/conflict outcomes, and focused synthetic tests.

v0.8.13 will validate missing, invalid, duplicate, incomplete, and uncertain roster OCR candidates later. Phase 9 will review and correct candidates before confirmation. v0.5.8 and v0.6.9 remain responsible for confirmed-roster persistence and synchronization safety.

Team-name extraction remains unsupported unless a dedicated evidenced team-name region exists. Five- and six-player extraction remains unsupported until representative evidence exists. Real roster OCR-quality evaluation remains deferred to Phase 12 unless separately approved.
