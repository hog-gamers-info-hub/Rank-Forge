# Rank-Forge — OCR and Team Matching

## 1. Document Purpose

This document defines the approved MVP rules for scoreboard screenshot intake, the separately staged roster screenshot OCR extension, Google ML Kit OCR, parsing, normalization, player matching, team-confidence assessment, manual correction, assignment safety, finalization, and OCR acceptance requirements.

It is a canonical documentation artifact only. It does not create Android code, OCR processing code, parser logic, matching formulas, database schema changes, tests, storage configuration, or implementation details that remain deferred.

This document follows the approved authority hierarchy. Product scope, roadmap sequencing, workflow governance, architecture boundaries, database boundaries, and testing requirements remain governed by their respective canonical authorities.

## 2. Scope and Boundaries

This document covers scoreboard screenshot processing, the separately staged roster screenshot OCR workflow, and team identification.

Approved MVP scope boundaries:

* Manual tournament roster entry remains available and maintained as structured application data.
* Scoreboard OCR applies only to genuine supported Free Fire MAX scoreboard screenshots.
* The separately staged roster OCR workflow may create review-required candidates from approved roster screenshots; it does not automatically create, replace, or confirm tournament rosters.
* Unsupported screenshot layouts must be rejected or marked for manual processing.
* OCR-assisted processing is optional; manual match entry remains supported.
* Scoring rules remain governed by `docs/05_SCORING_AND_PROCESSING_RULES.md`.
* Database implementation remains governed by `docs/03_DATABASE_DESIGN.md`.

This document does not authorize implementation beyond approved roadmap sequencing, automatic roster acceptance, unsupported screenshot processing, or any crop coordinates without representative screenshots and manually verified ground truth.

## 3. OCR and Matching Principles

The approved OCR and matching principles are:

* Preserve original evidence.
* Preserve raw OCR output.
* Keep raw, parsed, normalized, matched, and corrected values distinguishable.
* Treat OCR as uncertain input.
* Require human review before finalization.
* Keep scoring deterministic and independent from OCR confidence.
* Prevent duplicate player and team assignments.
* Make every uncertain or failed state explicit.
* Support reproducible testing.
* Do not silently discard warnings or errors.

OCR extraction, parsing, normalization, matching, scoring, correction, and finalization remain separate responsibilities.

## 4. Scoreboard Screenshot Intake

The approved logical intake flow is:

1. Select a scoreboard screenshot using the approved Android file-selection flow when implemented.
2. Associate it with one tournament and one match.
3. Validate that the file is a usable image.
4. Preserve the original image where permitted.
5. Generate a content hash for duplicate detection.
6. Record approved metadata.
7. Reject or flag duplicate, invalid, corrupted, or unsupported screenshots.
8. Begin OCR only after basic validation succeeds.

Approved metadata may include:

* Stable screenshot identity
* Tournament and match association
* File type
* Dimensions
* Orientation
* Content hash
* Processing status
* Original storage reference
* Processed-image reference where approved
* Creation and processing timestamps

This document does not define bucket names, object paths, file-size limits, exact supported resolutions, or exact metadata schemas.

### Staged roster screenshot intake

The staged roster workflow expects three screenshots for one tournament, with
four visible team slots per screenshot. The original selected image is private
evidence and must be preserved through approved owner-scoped storage behavior.
Before roster OCR, the operator must crop the roster panel in-app. OCR may use
only the cropped panel or reproducible crop metadata, never the full roster
screenshot. Candidate team and player data must be mapped to fixed slots 1
through 12, validated, reviewed, and explicitly confirmed before persistence.

Representative screenshots and manually verified expected data are prerequisites
for roster layout, crop coordinates, extraction accuracy, and genuine evaluation.
Manual roster entry remains the correction, unsupported-input, and fallback path.

## 5. Image Validation and Duplicate Detection

Image intake must validate file type, resolution, orientation, and basic usability.

Required rules:

* Corrupted or unreadable images must not enter OCR processing.
* Duplicate screenshot hashes must be detected.
* Reusing the same screenshot for conflicting match data must require explicit review.
* Original images and processed variants must remain distinguishable.
* Duplicate detection must not delete evidence automatically.

The hashing algorithm remains deferred unless separately approved.

## 6. Image Preparation Boundary

Later approved implementation may include:

* Fixed-layout cropping
* Operator-controlled roster-panel cropping for the separately staged roster workflow
* Scaling
* Contrast adjustment
* Image enhancement
* Controlled retry with approved preparation variants

Required boundaries:

* The original screenshot must not be destructively modified.
* Processed variants remain linked to the original.
* Every preparation attempt must be reproducible where practical.
* Failure to prepare an image must result in explicit manual processing or rejection.
* Preparation must not invent missing scoreboard content.

Crop coordinates, scaling ratios, contrast values, retry counts, image-processing libraries, and the exact supported layout remain deferred until genuine screenshots are approved.

## 7. ML Kit OCR Extraction

The approved MVP OCR behavior is:

* MVP OCR uses the bundled Google ML Kit Latin Text Recognition v2 model.
* OCR runs on-device.
* Raw OCR blocks and lines must be preserved.
* Available recognition metadata should be retained where useful.
* OCR processing status, warnings, and failures must be recorded.
* OCR extraction must not directly assign final teams or finalize matches.
* Empty or incomplete OCR output requires manual review.

This document does not introduce cloud OCR, external AI APIs, generative correction, fabricated text, or unapproved OCR models.

## 8. Scoreboard Parsing

The parser is responsible for deriving candidate values from raw OCR, including:

* Placements from 1 through 12
* Player names associated with scoreboard rows
* Player or team kill values as supported by the approved layout
* Row-level warnings and missing values

Required rules:

* Parsing is separate from OCR extraction.
* Parsed values must retain links to their raw OCR evidence.
* Missing placements, names, or kills must be flagged.
* Invalid placements and non-numeric or negative kill values must be rejected or flagged.
* More than 12 or fewer than 12 final result rows must block finalization.
* Parsing must not silently infer values unsupported by OCR evidence.

This document does not define regular expressions, coordinates, row geometries, or parser implementation.

## 9. Text Normalization

The approved normalization categories are:

* Letter case
* Leading, trailing, and repeated whitespace
* Symbols and punctuation
* Common OCR character confusion
* Zero and letter `O`
* One and letter `I`

Requirements:

* Original detected names remain preserved.
* Normalized names are derived comparison values.
* Normalization must be deterministic.
* Normalization must not silently replace the displayed confirmed player name.
* Normalization rules require unit-test fixtures.
* Unsupported transformations must not be introduced without approval.

This document does not invent transliteration, phonetic matching, language conversion, or aggressive character removal.

## 10. Player Similarity Matching

Approved player similarity matching behavior:

* Detected player names are compared against the manually maintained tournament roster.
* Approved implementation uses Damerau-Levenshtein comparison with approved OCR-confusion handling.
* Exact matches and normalized matches must be considered.
* Similar names across different teams must be treated cautiously.
* Missing or malformed names reduce matching certainty.
* A detected player may match only one roster player within a matching decision.
* A roster player must not be counted multiple times for the same result row.
* Matching output must include enough evidence for operator review.

Exact distance-to-score conversion, character weights, confidence calculation formulas, tie-handling formulas, and unapproved fallback algorithms remain deferred.

## 11. Team Candidate Scoring

Team candidates are calculated from matched roster players.

Required behavior:

* Candidate teams are ranked.
* The three strongest candidates must be available for review.
* Candidate evidence must show which detected players contributed.
* Duplicate use of a detected or roster player is prohibited.
* Candidate scores remain advisory until assignment requirements are satisfied.
* Candidate scoring is separate from tournament scoring.
* A team candidate does not become final solely because it ranks first.

The exact confidence formula remains deferred and is not defined here.

## 12. Confidence Tiers and Automatic Assignment

Approved confidence tiers:

* `90-100`: automatic-assignment tier
* `75-89`: suggestion requiring operator confirmation
* Below `75`: manual or unmatched

Automatic team assignment is allowed only when all of these conditions are satisfied:

1. At least three of four detected players match the same roster team.
2. The team confidence score is at least `90`.
3. The leading candidate has at least a `10`-point advantage over the second candidate.
4. The candidate team has not already been assigned in the same match.
5. No unresolved duplicate-player or assignment conflict remains.
6. Required detected values are valid.

Clarifications:

* Being in the `90-100` tier does not bypass the additional automatic-assignment conditions.
* `75-89` always requires confirmation.
* Below `75` must remain manual or unmatched.
* Any missing automatic-assignment condition requires operator review.
* No uncertain result may be silently confirmed.

## 13. Assignment Safety Rules

Required assignment safety rules:

* One detected player maps to at most one roster player.
* One roster player cannot be counted repeatedly for the same result.
* One tournament team can appear only once in a match.
* One placement can appear only once in a match.
* Duplicate candidate teams must be detected.
* Previously assigned teams must be excluded or flagged for conflicting rows.
* Unmatched teams remain unresolved.
* Assignment conflicts block finalization.
* Manual assignment must still satisfy unique-team and placement constraints.

This document does not invent global tournament exclusions beyond the current match.

## 14. Review and Manual Correction

The operator review workflow must allow:

* Review of all 12 placements
* Review of raw OCR values
* Review of normalized and parsed values
* Review of warnings and confidence information
* Review of the top three team suggestions
* Correction of player names
* Correction of kill values
* Correction of placement
* Selection of a suggested team
* Manual selection of an unmatched team
* Resolution of duplicate team assignments
* Confirmation of uncertain results

Requirements:

* Corrected values must not overwrite raw OCR evidence.
* Corrections must be auditable according to the database design.
* Manual assignment must not bypass validation.
* The operator must understand which fields remain unresolved.
* Review completion does not imply finalization until all validation passes.

This document does not design the exact screen layout or user-interface components.

## 15. Match Finalization Rules

Finalization must be blocked when:

* A placement is missing.
* A placement is duplicated.
* A team is missing.
* A team is assigned more than once.
* Required player or kill values are invalid.
* Kill values are negative or non-numeric.
* A low-confidence result has not been confirmed.
* An unmatched result remains unresolved.
* A duplicate screenshot conflict remains unresolved.
* More or fewer than 12 final result rows exist.
* Any required warning or validation error remains unresolved.

Finalization requirements:

* Exactly 12 valid unique team results
* Exactly 12 unique placements
* All uncertain results confirmed
* All corrections persisted
* Raw OCR evidence preserved
* Confirmed values passed to deterministic scoring
* Draft and finalized states remain distinct

OCR output must never finalize a match directly.

## 16. Raw, Parsed, and Corrected Data Preservation

The following states must remain distinct:

1. Original screenshot
2. Processed image variant
3. Raw OCR blocks and lines
4. Parsed scoreboard values
5. Normalized comparison values
6. Player-match assessments
7. Team candidates and confidence information
8. Operator corrections
9. Confirmed result values
10. Finalized match results

Requirements:

* Later states must not destructively replace earlier evidence.
* Original and corrected values must remain distinguishable.
* Processing attempts and failures must be traceable.
* Finalized corrections require the approved authorization and revision history.
* Storage and retention details remain governed by later approval.

## 17. Error and Uncertainty Handling

Explicit handling is required for:

* Unsupported scoreboard layout
* Corrupted image
* Duplicate screenshot
* Empty OCR result
* Partial OCR result
* Missing placement
* Missing player name
* Missing kill value
* Non-numeric or negative kill value
* Similar player names across teams
* Duplicate candidate teams
* Unmatched teams
* Low confidence
* Duplicate team assignment
* Local-save failure
* Processing interruption
* App restart during unfinished review

Error-handling rules:

* Errors must remain visible.
* Available evidence must be preserved.
* Silent confirmation is prohibited.
* Retry or manual entry must be allowed where practical.
* Finalization must be blocked when required.

This document does not define retry schedules or UI wording.

## 18. Testing and Acceptance Requirements

Required OCR and parsing tests:

* Genuine clear screenshots
* Compressed screenshots
* Similar player names
* Symbols and mixed characters
* Low but usable image quality
* All 12 placements
* Missing and malformed fields
* Duplicate screenshots
* Unsupported layouts

Required normalization and matching tests:

* Exact matches
* Case differences
* Extra whitespace
* Symbols and punctuation
* `0` and `O` confusion
* `1` and `I` confusion
* Incorrect OCR characters
* Similar names across teams
* Missing names
* Duplicate candidates
* Unmatched teams
* Automatic-assignment conditions
* All confidence tiers
* Unique assignment rules
* Top-three candidate ordering

Required manual correction and finalization tests:

* Correct every editable field
* Resolve duplicate assignments
* Confirm uncertain results
* Block incomplete finalization
* Preserve raw values after correction
* Recover unfinished review after restart

OCR acceptance requirements:

* Fake scoreboard screenshots must not be used as OCR acceptance evidence.
* Every genuine test screenshot requires manually verified ground truth.
* OCR output must be compared field by field.
* Player names, kills, placements, and team assignments must be measured separately.
* Corrections and screenshot quality must be recorded.
* Results must be reproducible.
* Target team-identification accuracy is at least `95%` on the approved genuine screenshot test set.
* Scoring accuracy after operator confirmation must be `100%`.
* OCR acceptance remains deferred until approved genuine screenshots are available.

This document does not claim that these acceptance targets currently pass.

## 19. Privacy and Storage Boundaries

Approved privacy and storage boundaries:

* Private or personally sensitive screenshots must not be committed publicly.
* Screenshot access must follow tournament ownership.
* Original and processed screenshots require controlled storage.
* Raw OCR and correction logs must avoid unnecessary sensitive data exposure.
* Privileged storage credentials remain backend-only.
* Retention and deletion rules remain deferred.
* Debug logs must not expose private screenshot content unnecessarily.

## 20. Deferred Technical Decisions

The following technical decisions remain explicitly deferred:

* Supported genuine Free Fire MAX scoreboard layout
* Exact crop coordinates
* Supported image resolutions
* File-size limits
* Image-preprocessing parameters
* Enhancement retry strategy
* Exact OCR metadata representation
* Raw OCR payload format
* Scoreboard row-detection logic
* Parsing expressions
* Exact normalization character map
* Distance-to-confidence formula
* Team-confidence weighting formula
* Candidate tie-handling formula
* Screenshot storage paths
* Retention and deletion policy
* Physical database structures
* Exact UI presentation of OCR evidence and suggestions

These decisions are intentionally not resolved here.

## 21. Roadmap Alignment

Approved roadmap alignment:

* Phase 2 creates the manually maintained roster used for matching.
* Phase 7 implements screenshot intake, validation, duplicate detection, preservation, storage, and metadata.
* Phase 8 implements ML Kit OCR, supported-layout definition, preprocessing, extraction, parsing, and genuine screenshot evaluation.
* Phase 9 implements normalization, similarity matching, candidate scoring, confidence tiers, assignment safety, review, correction, and safe finalization.
* The roster OCR extension adds Phase 7 intake/crop/set association, Phase 8 cropped roster OCR, Phase 5/6 safe confirmed-roster replacement, Phase 9 roster review/correction, and Phase 12 real acceptance evaluation.
* Phase 11 integrates the full screenshot-processing workflow.
* Phase 12 completes OCR, matching, security, and regression validation.
* Phase 13 validates the workflow using controlled genuine tournament data.
