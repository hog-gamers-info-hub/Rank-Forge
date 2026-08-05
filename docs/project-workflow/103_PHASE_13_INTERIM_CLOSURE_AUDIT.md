# Phase 13 — Beta Testing and Production Readiness Interim Closure Audit

## 1. Interim decision

**Verdict: Phase 13 remains open. v0.13.0 is partially completed and temporarily paused.**

This document closes only the current Phase 13 execution workstream so that the project can separately complete the core match-result OCR extraction and data-management function.

It does not formally close:

- Phase 13;
- `v0.13.0 — Internal Alpha`;
- any later Phase 13 version;
- the hosted Supabase deployment gate;
- controlled real-tournament beta;
- production readiness.

The completed v0.13.0 evidence and corrective work are preserved. The unfinished match OCR implementation is checkpointed on a dedicated remote branch and is intentionally not merged into `main`.

Phase 13 will be reopened after the core OCR extraction, coordinate interpretation, player/kill association, placement handling, and deterministic result construction are sufficiently complete and verified.

---

## 2. Audit purpose

The purpose of this interim audit is to record:

1. what has been completed during `v0.13.0`;
2. what defects were discovered and corrected;
3. what controlled alpha lanes passed;
4. what remains incomplete;
5. where the unfinished OCR implementation is preserved;
6. why Phase 13 is being paused;
7. what conditions must be satisfied before `v0.13.0` resumes.

This document is a controlled pause record, not a completed-version verification document.

The final completed v0.13.0 verification document remains reserved as:

`docs/project-workflow/102_V0_13_0_INTERNAL_ALPHA_VERIFICATION.md`

---

## 3. Governing scope

The canonical Phase 13 roadmap defines:

- `v0.13.0 — Internal Alpha`
- `v0.13.1 — Controlled Real-Tournament Beta`
- `v0.13.2 — Beta Defect Resolution`
- `v0.13.3 — Performance Optimization`
- `v0.13.4 — Migration Rehearsal`
- `v0.13.5 — Release Configuration`
- `v0.13.6 — Production Operations Review`

The approved v0.13.0 decisions additionally establish the required intermediate deployment gate:

- `v0.13.0.1 — Hosted Supabase Deployment`

The intended sequence remains:

1. complete `v0.13.0 — Internal Alpha`;
2. complete `v0.13.0.1 — Hosted Supabase Deployment`;
3. begin `v0.13.1 — Controlled Real-Tournament Beta`.

No hosted deployment was authorized or performed as part of this interim closure.

---

## 4. Approved alpha environment

The executed v0.13.0 work used:

- the Android debug application;
- the existing Room database and production local persistence path;
- an approved physical Android device;
- an isolated local Supabase environment reconstructed from the repository migration chain;
- synthetic and sanitized tournament data;
- controlled screenshot evidence;
- no genuine private tournament roster;
- no production database;
- no hosted Supabase schema deployment.

The hosted Supabase project remained outside the approved execution boundary except for the separately recorded ALPHA-002 environment incident.

No secrets, service-role credentials, private screenshots, private participant information, or authentication credentials are included in this document.

---

## 5. Controlled alpha fixture

The primary controlled tournament used:

- exactly 12 fixed team slots;
- 12 synthetic team names;
- exactly 4 synthetic players per team;
- 48 total synthetic roster players;
- a confirmed roster;
- deterministic match placements;
- deterministic kill totals;
- known expected scoring;
- known expected cumulative standings.

The baseline alpha tournament contained three finalized deterministic matches.

Two additional temporary matches were created during screenshot/OCR verification:

- Match 4 — DRAFT;
- Match 5 — DRAFT.

The temporary matches were not finalized and must remain non-authoritative test data.

---

## 6. Completed tournament and roster lanes

The following controlled v0.13.0 behavior passed:

- tournament creation;
- tournament reopening;
- Room persistence;
- application-restart recovery;
- exactly 12 team slots;
- complete 48-player roster;
- roster review;
- roster confirmation;
- confirmed-roster persistence;
- roster reopening after process restart;
- preservation of the confirmed roster during later alpha operations.

The primary controlled tournament remained available after force-stop, application restart, connected-test recovery, and offline/retry execution.

---

## 7. Completed manual match lane

Three controlled matches completed the normal manual workflow:

1. match creation;
2. placement entry;
3. kill entry;
4. validation;
5. review;
6. finalization;
7. scoring;
8. standings update;
9. reopening finalized data;
10. finalized read-only protection.

The finalized match data remained protected after application restart.

No scoring rules were changed to make the fixture pass.

---

## 8. Scoring and standings evidence

The controlled standings after the three finalized baseline matches were:

| Rank | Team | Total points |
| ---: | --- | ---: |
| 1 | Kilo | 46 |
| 2 | Lima | 42 |
| 3 | Delta | 40 |
| 4 | India | 37 |
| 5 | Alpha | 35 |
| 6 | Hotel | 34 |
| 7 | Bravo | 29 |
| 8 | Golf | 28 |
| 9 | Charlie | 26 |
| 10 | Echo | 23 |
| 11 | Juliett | 21 |
| 12 | Foxtrot | 20 |

The displayed standings matched the deterministic expected result.

The approved scoring rules remained:

- placement points according to the existing 1–12 scoring table;
- one point per kill;
- finalized-only contribution to cumulative standings.

---

## 9. Finalized-data protection

The alpha verified that:

- finalized matches reopen as protected data;
- finalized state survives process restart;
- normal draft editing does not silently mutate finalized data;
- protected correction boundaries remain enforced;
- scoring and standings are not silently recalculated from incomplete draft OCR evidence.

No finalized match was modified to support screenshot or OCR testing.

---

## 10. ALPHA-001 — first cloud upload revision defect

### Classification

**ALPHA-001 — First Cloud Upload Missing Revision**

Severity:

**BLOCKER**

Affected area:

- first tournament/roster cloud synchronization;
- revision-safe write behavior.

### Root cause

The existing revision write preparation rejected a never-synchronized local record when its local revision was greater than one and its cloud base revision was absent.

The local record was valid, but the first cloud write incorrectly required an already-established cloud revision relationship.

### Resolution

The correction allowed any valid positive local revision with no prior cloud base revision to perform a first cloud write using expected cloud revision zero.

The correction retained stale-write rejection and revision protection.

### Evidence

- decision PR: #215;
- corrective implementation PR: #216;
- first upload: PASS;
- repeat upload/idempotency: PASS;
- stale write rejection: PASS.

### Status

**CLOSED**

---

## 11. ALPHA-002 — hosted backend environment incident

### Classification

**ALPHA-002 — Hosted Backend Used During Local Internal Alpha**

Severity:

**BLOCKER — Test environment/configuration incident**

### Incident

The Android debug application initially targeted the connected hosted Supabase project instead of the approved isolated local Supabase instance.

Hosted authentication requests succeeded, while hosted table/RPC requests failed because the hosted schema had not been deployed.

### Confirmed boundary

No evidence was found of:

- hosted database migration;
- hosted schema creation;
- hosted table creation;
- hosted RLS modification;
- hosted Storage policy modification;
- hosted Edge Function deployment;
- destructive hosted SQL;
- production tournament data creation.

A hosted authentication-side effect occurred and was recorded.

### Recovery

The debug application was returned to the approved isolated local Supabase environment.

A debug-only cleartext network configuration was added so the physical device could access the local Supabase instance safely during development.

Relevant merged work included:

- ALPHA-002 decision PR #217;
- debug local-network recovery PR #218.

### Status

The incident is contained.

Formal final closure remains tied to the completed v0.13.0 verification record and confirmation that all remaining cloud-dependent alpha lanes used only the isolated local backend.

No hosted deployment is authorized by this audit.

---

## 12. Local synchronization and restoration

The isolated local Supabase lane verified representative existing behavior:

- local authentication;
- tournament upload;
- roster upload;
- finalized-match synchronization;
- repeat synchronization;
- idempotent cloud state;
- match restoration;
- local/cloud revision alignment;
- restoration into Room;
- finalized-state preservation.

A finalized-match synchronization defect discovered during execution was corrected and merged through PR #219.

Runtime verification passed:

- first finalized-match synchronization;
- repeat synchronization;
- unchanged cloud row counts on idempotent repeat;
- Room revision/base revision update;
- cloud match restoration.

---

## 13. Offline, retry, and restart recovery

The alpha deliberately removed device access to the local backend and verified:

- controlled network failure;
- safe user-facing failure state;
- queue creation;
- `BLOCKED_NETWORK` status;
- retry-attempt accounting;
- preservation of pending work;
- application restart with pending work;
- tournament persistence during offline restart;
- automatic retry after connectivity returned;
- eventual queue completion;
- no duplicate upload caused by retries.

The pending restoration operation survived process termination and completed automatically after local connectivity was restored.

This lane is assessed as:

**PASS**

---

## 14. Screenshot intake and preservation

The screenshot lane verified:

- controlled screenshot selection;
- image validation;
- match association;
- local private preservation;
- screenshot metadata persistence;
- screenshot reopening after process restart;
- draft-only screenshot workflow;
- preservation of the local copy when cloud upload initially failed.

No genuine private tournament screenshot was required for this controlled alpha lane.

---

## 15. Screenshot cloud identity defect

### Root cause

Local draft matches and synchronized cloud matches use different identifiers.

The normal draft-match synchronization path maps the local match identifier to a deterministic canonical cloud match identifier.

The screenshot Storage path and screenshot metadata upload originally bypassed this identity mapping and attempted to use the raw local match identifier.

Storage authorization correctly rejected the upload because the raw local identifier did not identify the corresponding cloud match row.

### Resolution

The screenshot upload and metadata path were corrected to use the same canonical cloud match identity as match synchronization.

### Evidence

PR #220 merged the correction.

Runtime verification passed:

- screenshot local preservation;
- retry upload;
- private Storage upload;
- screenshot metadata creation;
- canonical cloud match identifier usage;
- no SQL or Storage-policy weakening.

### Status

**CLOSED**

---

## 16. Restart-safe screenshot duplicate detection

### Root cause

Screenshot duplicate detection used an activity-retained in-memory map as its principal runtime memory.

After process restart, that in-memory state was lost even though screenshot metadata remained persisted.

This allowed the same screenshot to reach selection for another match after restart.

### Resolution

Duplicate detection was changed to consult persisted screenshot metadata before accepting a screenshot.

The implementation retained:

- in-memory concurrency protection;
- same-match behavior;
- other-match rejection;
- cancellation propagation;
- safe failure when persisted duplicate state cannot be read;
- unlink behavior.

### Evidence

PR #221 merged the correction.

After a fresh process start, the same controlled screenshot was selected for Match 5 and correctly rejected with the existing duplicate warning.

### Status

**CLOSED**

---

## 17. Match OCR orchestration gap

During the screenshot-assisted alpha lane, the existing OCR Review screen was found not to invoke the production OCR pipeline.

The screen loaded route identifiers but did not connect:

- screenshot metadata;
- preserved local image;
- image preprocessing;
- ML Kit raw extraction;
- placement parser;
- player-name parser;
- kill parser;
- OCR failure analysis;
- roster observation;
- team-identification evaluation;
- correction-draft initialization.

This prevented the screenshot-assisted workflow from functioning as an integrated production path.

---

## 18. OCR orchestration checkpoint

A dedicated implementation branch was created:

`fix/v0.13.0-match-ocr-orchestration`

The current checkpoint commit is:

`e89b23cc6fe32ffa0160172899e354c49810f8c0`

Commit message:

`chore(alpha): checkpoint match OCR orchestration work`

The checkpoint is pushed to the remote repository.

No pull request was created, and the branch is not merged into `main`.

### Checkpoint implementation

The branch currently contains work for:

- loading persisted screenshot metadata;
- resolving the app-private screenshot file;
- validating MIME type and dimensions;
- safely decoding the bitmap;
- releasing bitmap resources;
- invoking preprocessing;
- enforcing preprocessing candidate order zero only;
- invoking raw ML Kit extraction;
- invoking placement, player-name, and kill parsers;
- invoking OCR failure analysis;
- observing the confirmed roster;
- invoking existing team-identification evaluation;
- mapping the result into the existing OCR Review UI;
- initializing the existing correction draft;
- preserving human review and finalization protection;
- controlled failure handling;
- focused regression coverage.

The checkpoint changes 13 source/test files.

Temporary raw OCR diagnostic logging was removed before the checkpoint was committed.

---

## 19. Candidate selection decision

For the current v0.13.0 OCR boundary, the approved preprocessing policy is:

- use candidate order `0` only;
- do not merge multiple preprocessing candidates;
- do not choose a candidate based on OCR text quality;
- do not choose a candidate based on parser completeness;
- do not introduce automatic scaling/contrast retries;
- do not add new OCR confidence thresholds;
- preserve uncertainty for human review.

Automatic candidate retry and selection remain deferred.

---

## 20. Coordinate-space defect

### Root cause

The Android preprocessor crops the original screenshot to the supported scoreboard content rectangle before sending the bitmap to ML Kit.

ML Kit bounding boxes are therefore relative to the cropped candidate bitmap.

The existing placement, player-name, and kill parsers compared those candidate-local coordinates against zones calculated in the original calibration coordinate space.

This caused incorrect field association and cross-zone contamination.

### Correction in checkpoint

The parsers were changed to:

1. calculate normalized field regions from the approved scoreboard layout;
2. express those regions relative to the overall scoreboard crop;
3. scale them to the actual candidate crop dimensions;
4. compare ML Kit geometry in the correct candidate-local coordinate space.

Regression tests were added using a realistic crop:

- original crop origin: `(208, 158)`;
- crop size: `1168 × 468`;
- candidate bitmap size: `1168 × 468`;
- candidate order: `0`.

Focused parser tests, focused orchestration tests, the full JVM suite, `git diff --check`, and `assembleDebug` passed before the checkpoint was created.

### Status

The coordinate-space correction is retained in the checkpoint but is not merged.

It must be reviewed as part of the fresh OCR core-function work before final integration.

---

## 21. Physical-device OCR orchestration evidence

After installing the checkpoint build on the physical device, Match 4 reached the production OCR Review screen and displayed:

- 12 expected review rows;
- extracted player evidence;
- parser states;
- team suggestions;
- confidence states;
- correction-draft state;
- finalization blockers;
- mandatory manual review.

This proves that the previously disconnected production OCR components can be orchestrated into the existing review workflow.

The result was not considered accurate enough to merge because the remaining parser/data model does not correctly represent the real scoreboard structure.

Match 4 remained DRAFT.

---

## 22. Raw ML Kit evidence diagnostic

A temporary local diagnostic was used to record:

- candidate crop coordinates;
- candidate dimensions;
- full OCR text;
- OCR blocks;
- OCR lines;
- OCR elements;
- bounding boxes.

The temporary logging source change was removed before the checkpoint commit.

The sanitized diagnostic established:

- original screenshot class: controlled Free Fire MAX result screen;
- original screenshot dimensions: `1600 × 720`;
- scoreboard crop origin: `(208, 158)`;
- ML Kit candidate dimensions: `1168 × 468`;
- ML Kit bounding boxes were candidate-local;
- player names were extracted as positioned text evidence;
- elimination values were extracted as positioned text evidence;
- lines and elements were not always semantically grouped according to Free Fire player rows;
- some text tokens were joined;
- some player names were split into multiple elements;
- the letter `O` was sometimes returned for numeric zero;
- stylized placement numbers were not consistently recognized.

The diagnostic output remains outside the tracked repository.

---

## 23. Confirmed placement-one OCR evidence

For the visible first-place squad, ML Kit extracted four player-name candidates and four elimination values.

The controlled evidence corresponded to:

- player 1 — 2 eliminations;
- player 2 — 7 eliminations;
- player 3 — 8 eliminations;
- player 4 — 8 eliminations.

The deterministic team kill total should therefore be:

`2 + 7 + 8 + 8 = 25`

The existing kill parser instead treated multiple same-row values as ambiguity or duplicate evidence because it was originally modeled around a single elimination value per placement row.

This is a parser/data-model mismatch, not a scoring-engine defect.

---

## 24. Remaining core OCR problems

The following core problems remain unresolved:

### 24.1 Scoreboard semantic model

A placement result contains up to four visible player rows, not one player-name field and one kill field.

The required structure is closer to:

- placement;
- left player column;
- left elimination column;
- right player column;
- right elimination column;
- two player subrows per column;
- four player/elimination associations;
- deterministic team-kill aggregation.

### 24.2 Player-name grouping

ML Kit may return:

- one full player name as one element;
- one player name split into multiple elements;
- unrelated semantic columns grouped into one line;
- player text joined to elimination labels.

Rank Forge must group player evidence by approved spatial regions rather than treating one ML Kit line or element as one player.

### 24.3 Player-to-kill association

Player names and elimination values must be associated using:

- placement-card membership;
- field-column membership;
- vertical overlap;
- vertical center distance;
- deterministic spatial tolerances.

### 24.4 Kill normalization

The parser must safely support observed forms such as:

- `2`;
- `2 Eliminations`;
- `1Eliminations`;
- `O Eliminations` within an approved numeric elimination region.

Normalization must remain field-specific and must not globally convert player-name characters.

### 24.5 Team kill aggregation

Repeated individual values are legitimate.

For example, `8` and `8` may belong to two different players and must not be treated as duplicate OCR evidence when their geometry identifies separate player rows.

The team total must be derived from the accepted individual player kill values.

### 24.6 Placement recognition

ML Kit did not recognize every visible stylized placement number.

The diagnostic recognized only a subset of placement numbers.

The future implementation must distinguish:

- directly recognized placement evidence;
- geometrically inferred card order;
- constrained/obscured rows;
- missing or uncertain placement evidence.

No placement fallback has yet been approved.

### 24.7 Two-screenshot coverage

The supported tournament workflow requires enough screenshot evidence to capture all 12 placements, including positions that may be constrained or obscured in one result screenshot.

The fresh OCR design must confirm how multiple screenshot evidence is associated and combined without cross-placement duplication.

### 24.8 Review authority

OCR output must remain candidate evidence.

Human correction and confirmation remain authoritative.

No uncertain OCR result may silently become finalized tournament data.

---

## 25. Why the OCR checkpoint is not merged

The checkpoint is not merged because:

- the integration path is now functional;
- coordinate handling is materially improved;
- tests pass;
- but the parser/data model still represents the scoreboard incorrectly;
- individual player kills are not correctly aggregated;
- player-name multiplicity is still classified using assumptions from a single-name row model;
- placement recognition/fallback policy remains unresolved;
- the supported multi-screenshot result model must be reviewed;
- merging now would make incomplete semantics part of `main`.

The remote branch protects the completed engineering work while allowing the core OCR function to be redesigned from a clean starting point.

---

## 26. Work intentionally deferred to the fresh OCR effort

The separate OCR-focused work must determine and document:

1. exact ML Kit output contracts;
2. original-image and candidate-image dimensions;
3. coordinate-system transformations;
4. bounding-box semantics;
5. block, line, and element reliability;
6. placement-card geometry;
7. player-column geometry;
8. elimination-column geometry;
9. player subrow geometry;
10. name-fragment grouping;
11. player-to-kill pairing;
12. team-kill aggregation;
13. field-specific OCR normalization;
14. placement fallback policy;
15. multi-screenshot association;
16. uncertainty classification;
17. correction-draft representation;
18. regression fixtures based on sanitized real geometry;
19. physical-device verification;
20. safe reintegration with the existing OCR Review flow.

No AI interpretation layer is approved for this work.

The intended direction is deterministic:

- ML Kit text recognition;
- geometry;
- known layout;
- deterministic parsing;
- roster matching;
- human review.

---

## 27. Repository state at pause

At the time of this interim audit:

- `main` contains the merged alpha decisions and completed corrective PRs;
- the unfinished OCR work is not present on `main`;
- the OCR checkpoint is available on the remote branch;
- checkpoint commit: `e89b23cc6fe32ffa0160172899e354c49810f8c0`;
- temporary diagnostic logging is absent from the repository;
- the checkpoint working tree was clean after push;
- no OCR checkpoint pull request exists;
- no hosted deployment was performed.

The interim audit itself is being prepared on:

`docs/phase-13-interim-closure-audit`

---

## 28. Phase 13 roadmap status

| Version | Status |
| --- | --- |
| `v0.13.0 — Internal Alpha` | PARTIALLY COMPLETE — PAUSED |
| `v0.13.0.1 — Hosted Supabase Deployment` | NOT STARTED |
| `v0.13.1 — Controlled Real-Tournament Beta` | NOT STARTED |
| `v0.13.2 — Beta Defect Resolution` | NOT STARTED |
| `v0.13.3 — Performance Optimization` | NOT STARTED |
| `v0.13.4 — Migration Rehearsal` | NOT STARTED |
| `v0.13.5 — Release Configuration` | NOT STARTED |
| `v0.13.6 — Production Operations Review` | NOT STARTED |

Phase 13 must not be represented as complete.

---

## 29. Conditions for reopening v0.13.0

The Internal Alpha execution may resume after the separate core OCR work provides:

1. an approved match-result data model;
2. an approved coordinate and layout contract;
3. deterministic four-player row interpretation;
4. deterministic player/kill association;
5. deterministic team-kill aggregation;
6. safe zero and joined-token normalization;
7. an approved placement recognition/fallback policy;
8. multi-screenshot association behavior;
9. focused parser regression tests;
10. full JVM verification;
11. successful Android build;
12. physical-device controlled screenshot verification;
13. no weakening of human review;
14. no weakening of finalization protection;
15. a clean integration plan for the checkpoint branch.

After those conditions are met, v0.13.0 should resume from the preserved alpha state rather than restarting all completed lanes without cause.

Representative affected lanes must nevertheless be rerun where the new OCR implementation could change behavior.

---

## 30. Required work after reopening

After the core OCR function is integrated, v0.13.0 must still complete:

- accurate controlled screenshot-assisted match processing;
- OCR correction workflow verification;
- safe team-identification review;
- multi-screenshot result handling where required;
- finalization-blocking verification;
- confirmation that Match 4 and Match 5 remain controlled draft evidence or are safely removed;
- remaining representative error-state checks;
- final ALPHA-002 closure assessment;
- reconciliation of the Phase 13 roadmap with `v0.13.0.1`;
- correction of any duplicated v0.13.0 decision-document content;
- final sanitized v0.13.0 verification document;
- final v0.13.0 closure verdict.

Only after v0.13.0 is formally complete may the hosted deployment decision gate begin.

---

## 31. Privacy and safety confirmation

This audit does not include:

- authentication passwords;
- API secrets;
- Supabase service-role keys;
- private screenshot files;
- raw private player data;
- genuine tournament participant identities;
- device-private storage contents;
- destructive SQL;
- hosted production data.

The controlled OCR example is described only to the extent necessary to record the parser and data-model finding.

---

## 32. Final interim verdict

**Phase 13 remains open.**

**v0.13.0 is partially complete and paused.**

The completed alpha work establishes that the current application can perform the controlled tournament, roster, manual-match, scoring, standings, persistence, local synchronization, restoration, offline retry, screenshot storage, and duplicate-protection workflows.

The remaining blocker is the correctness of the core match-result OCR extraction and data-management function.

The unfinished OCR orchestration and coordinate work is safely preserved at:

- branch: `fix/v0.13.0-match-ocr-orchestration`;
- commit: `e89b23cc6fe32ffa0160172899e354c49810f8c0`.

The checkpoint must not be treated as production-ready or merged solely to close this interim audit.

The next project activity should be a fresh, dedicated OCR-design and implementation workflow. After that function is complete and verified, Phase 13 and v0.13.0 must be reopened and formally finished.