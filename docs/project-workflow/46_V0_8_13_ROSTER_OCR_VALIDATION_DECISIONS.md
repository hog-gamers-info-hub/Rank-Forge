# v0.8.13 — Roster OCR Validation Decisions

## 1. Status

Approved implementation decision gate for the Phase 8 roster OCR extension, v0.8.13. It follows the completed v0.8.9 Cropped Roster Layout Definition, v0.8.10 Roster Raw OCR Extraction, v0.8.11 Roster Team and Player Parsing, and v0.8.12 Roster Slot Association work.

This document authorizes roster OCR candidate validation only. It does not authorize roster confirmation, persistence, synchronization, review UI, team matching, or changes to completed roster and match-result workflows.

## 2. Version scope

v0.8.13 consumes v0.8.12 roster slot-association output and produces a structured candidate-validation result. It validates candidate completeness and quality for safe later review, but it does not decide final roster correctness or confirm any data.

The validation boundary must not run ML Kit, process images or full screenshots, change the v0.8.9 layout, alter v0.8.10 extraction, reparse v0.8.11 text, or change v0.8.12 association behavior. It must not persist confirmed roster data or update Room or Supabase/backend files.

## 3. Canonical sources reviewed

This decision was reviewed against:

* `AGENTS.md` and `README.md`;
* `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`;
* `docs/project-workflow/37_ROSTER_SCREENSHOT_OCR_ROADMAP_DECISIONS.md`;
* `docs/project-workflow/42_V0_8_9_CROPPED_ROSTER_LAYOUT_DEFINITION_DECISIONS.md` through `docs/project-workflow/45_V0_8_12_ROSTER_SLOT_ASSOCIATION_DECISIONS.md`;
* the current v0.8.9 cropped roster layout, v0.8.10 raw extraction, v0.8.11 candidate parser, and v0.8.12 slot-association contracts;
* the current fixed 12-team-slot model and manual roster workflow, including its four-to-six player-count rules; and
* relevant OCR, Android, testing, privacy, security, and AI workflow documents.

## 4. Relationship to v0.8.9 cropped roster layout

v0.8.9 remains the authority for prepared-crop scope, screenshot positions 1–3, visible-slot order, and the four evidence-backed player-row regions. v0.8.13 consumes only the resulting candidate metadata and must not process a crop, image, or full screenshot, define coordinates, or change layout behavior.

Five- and six-player OCR rows remain outside the approved geometry and must not become required validation rows.

## 5. Relationship to v0.8.10 raw OCR extraction

v0.8.10 remains the authority for raw OCR evidence, region identity, extraction failures, and confidence availability. v0.8.13 must preserve candidate links to that evidence and must not run ML Kit, alter extraction, fabricate confidence, or discard raw evidence.

Extraction failures and unavailable metadata carried forward through v0.8.11 and v0.8.12 must become visible validation issues where applicable, not successful validation outcomes.

## 6. Relationship to v0.8.11 candidate parsing

v0.8.11 remains the authority for conservative text handling, player-row candidate order, candidate parse status, team-name unavailable or unsupported state, and parser input failures. v0.8.13 validates those outcomes as received.

Validation must not reparse text, normalize or fuzzy-match names, select an ambiguous candidate, autocorrect a name, infer a team name, or alter parse status or raw evidence references.

## 7. Relationship to v0.8.12 slot association

v0.8.12 remains the authority for deterministic candidate association to fixed tournament slots 1–12 and for association failures. v0.8.13 consumes its ordered candidate output and typed association outcomes unchanged.

Validation may report an association failure as a validation issue, but must not retry association, choose between conflicting candidates, overwrite duplicates, or fill missing slots by inference.

## 8. Validation boundary

Implementation must add pure-domain validation contracts that accept a v0.8.12 `RosterSlotAssociationResult` and return a structured roster OCR validation result. The result must preserve ordered tournament-slot candidates and add global and slot-scoped validation issues.

Each validation issue must include a severity, issue type, tournament slot where applicable, source screenshot and visible-slot metadata where available, and references to source candidate or raw evidence where applicable. The result must expose an overall validation status derived from the presence of blocking issues, warnings, and informational/manual-review issues.

Validation output remains candidate-only. It is neither a confirmed roster nor an instruction to persist or replace manual roster data.

## 9. Required slot coverage decision

v0.8.13 must validate the required fixed tournament-slot coverage of slots 1–12. A missing associated tournament-slot candidate is a slot-scoped blocking validation issue. The validator must report every missing slot rather than stopping after the first one.

Duplicate or conflicting tournament-slot candidates, invalid tournament slot numbers, invalid screenshot-position metadata, invalid visible-slot metadata, and association metadata conflicts are blocking validation issues. The validator must surface the original association outcomes and sources without repairing or selecting them.

## 10. Player-row validation decision

Only player rows 1–4 are OCR-evidenced and must be validated for each associated slot. Their row index, parse status, candidate text, confidence availability, region identity, and raw evidence references must remain attached to the validation result.

Missing, empty, ambiguous, duplicate, malformed, uncertain, unsupported, or extraction-failure player-row candidates are validation issues. Missing, empty, ambiguous, duplicate, malformed, uncertain, and extraction-failure outcomes prevent a clean candidate set and are blocking until later review/correction resolves them. An unsupported fifth or sixth row is an unsupported/deferred manual-review issue, not a required missing row.

Manual roster rules allow four to six players, but v0.8.13 validates only the four evidenced OCR rows. It must not apply manual player-count validation to OCR candidate data.

## 11. Team-name unavailable decision

The unavailable or unsupported team-name state from v0.8.11 and v0.8.12 must be preserved exactly. When no dedicated evidenced team-name region exists, v0.8.13 must report team-name unavailability as an informational/manual-review issue.

Team-name unavailability alone must not fail the entire candidate set. The validator must not infer, generate, normalize, fuzzy-match, or otherwise manufacture a team name from player names, prefixes, clan tags, repeated text, slot-content evidence, or slot numbers. Duplicate-team-name validation is not meaningful while team names remain unavailable and must not be invented.

## 12. Duplicate and conflict validation decision

Duplicate or conflicting tournament-slot association outcomes are blocking validation issues. The validation output must retain the source candidates and association references so Phase 9 can present the conflict for review.

Duplicate player-name candidate text within or across associated slots must be surfaced as a warning/manual-review indicator. It must not be automatically corrected, used for team matching, or treated as a confirmed duplicate roster player. v0.8.13 does not validate duplicate manual roster names or final cross-team roster correctness.

## 13. Missing, empty, ambiguous, and uncertain text decision

Validation must convert v0.8.11 candidate statuses into explicit issues without guessing:

| Candidate outcome | v0.8.13 validation handling |
| --- | --- |
| Missing player row | Blocking issue for the affected row. |
| Empty player row | Blocking issue for the affected row. |
| Ambiguous player row | Blocking issue; preserve all candidate evidence. |
| Duplicate player-row candidate | Blocking issue; do not select or merge a value. |
| Malformed or uncertain player row | Blocking issue until later review/correction. |
| Player-row extraction failure or parser input failure | Blocking issue with the available source failure reference. |
| Unsupported team name | Informational/manual-review issue only. |
| Unsupported fifth/sixth player row | Unsupported/deferred manual-review issue only; never a required row. |

No issue may silently discard raw evidence, convert unresolved text into a valid value, or decide final roster validity.

## 14. Confidence handling decision

Confidence values must be preserved only as supplied by v0.8.10 raw evidence through the candidate and association contracts. v0.8.13 must not invent, default, estimate, rank, or threshold a confidence value.

When a required player-row candidate has unavailable confidence, the validator may report a confidence-unavailable informational/manual-review issue. Unavailable confidence alone is not a substitute for, or an automatic override of, the candidate's parse status. Confidence does not alter scoring and does not confirm a roster.

## 15. Validation severity decision

Validation contracts must distinguish at least the following severities:

* **Blocking:** required slot coverage failures, association conflicts or invalid metadata, and unresolved quality failures in required player rows 1–4. Blocking issues prevent a clean candidate-validation result and require later review/correction.
* **Warning:** duplicate player-name candidate text or another non-confirmatory candidate condition that should be reviewed but is not a structural slot or required-row absence.
* **Info/manual-review:** team-name unavailable, confidence unavailable, and unsupported/deferred fifth/sixth-player visibility.

The overall validation status must reflect these severities without claiming confirmation. A result with no blocking issues is ready for Phase 9 review, not ready for automatic roster persistence or replacement.

## 16. Review handoff decision

v0.8.13 must produce a safe, structured handoff for the later Phase 9 review/correction UI. The handoff must include ordered candidate slots, issues by slot, global issues, issue severity and type, preserved candidate statuses, and available source candidate/raw-evidence references.

This version must not add review or correction UI, accept an operator action, mutate a candidate, persist a correction, confirm a roster, or finalise data. Phase 9 remains responsible for review and correction before any confirmation workflow.

## 17. Privacy and fixture decision

Real roster screenshots, real player names, raw OCR payloads, private local paths, signed URLs, and manually verified ground truth must not be committed without explicit privacy approval. Automated tests must use synthetic association output, synthetic names, and synthetic raw-evidence references only.

Private representative evaluation remains local-only under documented privacy controls. It is not a committed fixture, is not required for v0.8.13 unit tests, and does not authorize confirmation or a real OCR-quality claim.

## 18. Explicit exclusions

v0.8.13 must not add or change:

* ML Kit execution, raw OCR extraction, parsing, association, layout geometry, image preprocessing, crop behavior, full-screenshot processing, or crop-coordinate guessing;
* team/player fuzzy matching, automatic correction, team-name inference, candidate-to-roster confirmation, manual roster validation, or final roster correctness decisions;
* confirmed roster persistence, Room schema or migration work, Supabase/backend changes, upload, synchronization, export, review/correction UI, scoring, standings, or match finalization;
* enforcement of fifth/sixth-player OCR rows, real screenshot fixtures, real player-name fixtures, network behavior, cloud OCR, or external files; or
* the completed v0.8.9–v0.8.12 contracts, existing match-result OCR/parser behavior, Phase 7 screenshot lifecycle, manual roster workflow, finalized-data protection, or correction workflows.

## 19. Testing and verification expectations

Future v0.8.13 implementation tests must use synthetic v0.8.12 association output only and verify:

* a complete valid 12-slot candidate set, including ordered candidate preservation and no blocking slot or row issues;
* every missing tournament slot, duplicate/conflicting tournament slot, invalid slot number, invalid screenshot/visible-slot metadata, and metadata conflict is surfaced as a validation issue;
* missing, empty, ambiguous, duplicate, malformed, uncertain, unsupported, and extraction-failure player-row outcomes are classified at the approved severity;
* rows 1–4 are validated while unsupported fifth/sixth rows remain deferred/manual-review and are not required;
* unavailable/unsupported team names are retained as information/manual-review without inference or whole-set failure;
* duplicate player-name candidate text is a warning/manual-review issue and is not corrected;
* unavailable confidence is retained and handled without invention;
* global parser and association input failures are surfaced;
* severity classification, overall candidate-validation status, source candidate references, and raw evidence references are preserved; and
* regression protection confirms v0.8.9–v0.8.12 behavior remains unchanged.

Tests must not require ML Kit execution, OCR accuracy, network access, Google Play services, Room migrations, Supabase, real screenshots, real names, or external files.

## 20. Risks and mitigations

| Risk | Approved mitigation |
| --- | --- |
| Candidate validation is mistaken for final roster confirmation. | Keep all output candidate-only and direct it to Phase 9 review rather than persistence. |
| Missing slots or unusable rows are silently overlooked. | Emit structured blocking issues for every affected fixed slot and required row. |
| Team names are fabricated despite no team-name region. | Preserve unavailable/unsupported state as informational/manual-review and prohibit inference. |
| Duplicate candidate text is mistaken for a confirmed roster duplicate. | Surface it as a warning only; defer roster identity and correctness decisions. |
| Confidence becomes an invented quality score. | Preserve only supplied confidence and report unavailable confidence without estimating it. |
| OCR work changes manual or match-result workflows. | Keep validation a roster-specific pure-domain boundary with regression protection. |
| Private screenshots or real names enter source control. | Require synthetic fixtures and keep representative evaluation local and privacy-controlled. |

## 21. Acceptance criteria for implementation

v0.8.13 implementation is acceptable only when:

* it adds a pure-domain validation boundary that consumes v0.8.12 association output without calling ML Kit or processing images;
* it returns ordered candidate slots together with structured global and slot-scoped validation issues, overall candidate-validation status, severity, issue type, and available source references;
* it validates required coverage of fixed tournament slots 1–12 and four evidenced player rows 1–4, while preserving all candidate, status, confidence, and raw-evidence data;
* it reports association conflicts, invalid metadata, missing/empty/ambiguous/duplicate/malformed/uncertain/extraction-failure rows, and parser/association input failures without repair or confirmation;
* it preserves team-name unavailable state as informational/manual-review, treats duplicate player-name candidate text as warning/manual-review, and keeps fifth/sixth rows unsupported/deferred;
* focused synthetic tests cover the specified validation states, severities, preservation, and regression boundaries; and
* no matching, correction UI, confirmation, persistence, Room, Supabase, scoring, standings, finalization, real-fixture, or full-screenshot behavior is introduced.

## 22. Next implementation action

After this decision document is reviewed and merged, and with explicit user approval, the next v0.8.13 implementation task is limited to pure-domain validation contracts, candidate and issue models, severity classification, and focused synthetic tests described here.

Phase 9 will add review/correction UI before confirmation. v0.5.8 and v0.6.9 will handle confirmed-roster persistence and synchronization safety later. Team-name extraction remains unsupported unless a dedicated evidenced team-name region exists, and five/six-player OCR extraction remains unsupported until representative evidence exists. Real OCR-quality evaluation remains deferred to Phase 12 unless separately approved.

v0.8.13 is the final planned Phase 8 roster OCR extension implementation before a Phase 8 roster-extension closure audit.
