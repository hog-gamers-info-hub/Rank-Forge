# v0.13.0 — Internal Alpha Decisions

## 1. Status

**Decision status: APPROVED**

This document freezes the scope, execution environment, evidence requirements, safety boundaries, and completion criteria for:

**v0.13.0 — Internal Alpha**

Canonical roadmap purpose:

> Test the complete application using controlled local tournament data.

This is primarily an **execution and verification version**.

No production implementation change is planned as part of the normal v0.13.0 scope.

If alpha execution discovers a defect, the defect must be recorded and classified before any corrective implementation is approved.

---

## 2. Governing Sources

v0.13.0 must preserve the current approved Rank-Forge architecture and all completed Phase 0–12 behavior.

The governing sources include:

* `AGENTS.md`
* `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`
* Phase 11 workflow-integration decisions and closure evidence
* Phase 12 QA, compatibility, recovery, security, OCR acceptance, regression, and closure evidence
* `docs/project-workflow/100_PHASE_12_CLOSURE_AUDIT.md`
* existing Android, Room, Supabase, OCR, synchronization, correction, scoring, standings, and export implementation on `origin/main`

The current merged implementation is authoritative for what can actually be exercised during alpha.

v0.13.0 must not reinterpret deferred functionality as implemented functionality.

---

## 3. Version Objective

The objective of v0.13.0 is to verify that the application produced by Phases 0–12 operates coherently as an integrated application when used with controlled and sanitized tournament data.

Internal alpha must test the currently implemented workflow from the perspective of an operator rather than merely re-running isolated unit tests.

The alpha must answer:

1. Can a controlled tournament be created and restored?
2. Can a valid 12-team roster be created, reviewed, confirmed, persisted, and reopened?
3. Can matches be created and processed through the available manual and screenshot-assisted workflows?
4. Are scoring and cumulative standings correct?
5. Are draft and finalized states enforced?
6. Does controlled correction preserve finalized-data protection?
7. Does Room persistence survive restart?
8. Do offline, retry, synchronization, restoration, and conflict-safe paths behave safely against an isolated local backend?
9. Does the available OCR review path remain human-controlled?
10. Can finalized application data reach the currently implemented CSV preparation/export boundary?
11. Do blocked, invalid, unavailable, authentication, network, retry, conflict, and read-only states fail safely?
12. Can all of the above be exercised without using the hosted Supabase project or genuine tournament data?

---

## 4. Alpha Classification

v0.13.0 is classified as:

**Controlled local internal verification**

It is not:

* a hosted backend deployment;
* a production migration;
* a production security verification;
* a real-tournament beta;
* a genuine OCR accuracy evaluation;
* a performance-optimization version;
* a release configuration version;
* a production release;
* a defect-resolution batch.

The version should introduce no new product features.

---

## 5. Approved Alpha Environment

The approved v0.13.0 environment consists of three layers.

### 5.1 Android execution environment

Use:

* the existing debug Android application;
* an approved emulator and/or already approved physical Android device;
* the existing application architecture and build configuration.

A new release build, signing configuration, build variant, or production configuration is not required for v0.13.0.

Release configuration belongs to v0.13.5.

### 5.2 Local persistence environment

Room is the primary local alpha persistence lane.

The alpha must exercise the existing local persistence behavior using isolated controlled data.

No existing personal, beta, real-tournament, or production tournament database should be reused as alpha evidence.

### 5.3 Isolated local Supabase environment

The cloud-related alpha lane must use an **isolated local Supabase instance reconstructed from the migration chain in `origin/main`**.

The local backend lane may exercise:

* authentication;
* ownership;
* tournament/roster synchronization;
* match synchronization;
* restoration;
* offline queue retry;
* revision handling;
* protected operations;
* screenshot metadata/Storage behavior where reachable;
* relevant export backend behavior where already implemented and locally testable.

The local backend must not be treated as production.

---

## 6. Hosted Supabase Prohibition

The connected hosted Supabase project must **not** be modified during v0.13.0.

The following are prohibited during this version:

* hosted `supabase db push`;
* hosted migration application;
* migration-history repair against the hosted project;
* remote schema creation;
* remote table creation;
* remote RLS changes;
* remote Storage bucket or policy changes;
* remote function deployment;
* remote secret creation or modification;
* remote production-data creation;
* destructive remote SQL;
* using the hosted project as the alpha backend.

Any command capable of modifying the hosted Supabase project requires a separate approved deployment version.

---

## 7. Phase 13 Hosted Deployment Sequencing

A dedicated hosted deployment gate is required before controlled real-tournament beta.

The approved placement is:

**v0.13.0.1 — Hosted Supabase Deployment**

The resulting sequence is:

1. `v0.13.0 — Internal Alpha`
2. `v0.13.0.1 — Hosted Supabase Deployment`
3. `v0.13.1 — Controlled Real-Tournament Beta`

v0.13.0.1 requires its **own decision document and approval** before any hosted-project modification.

This v0.13.0 document does not authorize deployment.

v0.13.1 must not begin until the hosted deployment version has completed its migration, backend, security, Edge Function, configuration, and development/beta-build verification gates.

---

## 8. Controlled Alpha Data Policy

All v0.13.0 tournament data must be:

* synthetic;
* sanitized;
* non-sensitive;
* deterministic where expected results are important;
* safe to expose in sanitized verification documentation.

Do not use:

* genuine tournament participant identities;
* real player names unless already explicitly approved as public/non-sensitive test data;
* private tournament screenshots;
* private raw OCR output;
* genuine tournament ground truth;
* private device paths;
* authentication secrets;
* Supabase service-role credentials;
* Google service-account credentials.

---

## 9. Approved Controlled Tournament Fixture

The primary internal-alpha tournament should use:

* exactly **12 teams**;
* unique synthetic team names;
* exactly **4 synthetic players per team** for the baseline roster;
* unique synthetic player names;
* valid fixed team slots 1 through 12;
* multiple controlled matches sufficient to verify cumulative standings;
* deterministic placements;
* deterministic kill totals;
* pre-calculable expected scoring and standings.

The baseline fixture should use the minimum valid four-player roster because it provides a simple deterministic happy-path fixture while remaining within the approved four-to-six-player roster rule.

Additional temporary variations may be entered during alpha to exercise validation and failure behavior, but they do not redefine the baseline fixture.

No requirement is created to commit participant fixture data to Git.

---

## 10. Match Fixture Requirements

The alpha dataset must contain enough controlled matches to verify:

* match creation;
* draft persistence;
* placement entry;
* kill entry;
* validation;
* scoring;
* finalization;
* cumulative standings;
* tie-independent ranking;
* restart/reopen behavior;
* correction protection.

At least one controlled match must complete the normal manual-entry path.

Multiple finalized controlled matches must be used so cumulative standings are verified rather than only single-match totals.

Exact placement and kill values must be recorded in the alpha evidence or a sanitized companion fixture record so expected results can be reproduced.

The alpha does not need to exercise the maximum ten-match tournament limit unless a defect or verification requirement makes that necessary.

---

## 11. OCR and Screenshot Alpha Boundary

v0.13.0 may exercise the already implemented screenshot/OCR workflow using controlled, sanitized, or existing approved test fixtures.

Its purpose is to verify integration and safety, not to establish new OCR accuracy thresholds.

The alpha may verify:

* screenshot intake;
* candidate validation;
* screenshot association;
* OCR invocation;
* parsed candidate presentation;
* review state;
* uncertainty handling;
* manual correction;
* team matching integration where available;
* mandatory human review;
* safe finalization boundaries.

The alpha must not:

* claim new genuine OCR acceptance accuracy;
* change OCR thresholds;
* change supported layouts;
* change crop geometry;
* change matching algorithms;
* silently accept uncertain results;
* substitute synthetic evidence for the genuine acceptance work already completed in Phase 12.

Real tournament rosters and screenshots belong to v0.13.1.

---

## 12. Roster OCR Boundary

Existing roster OCR functionality may be exercised using approved controlled/sanitized inputs.

The operator-review guarantees remain mandatory:

* OCR output remains candidate data;
* candidate data is not authoritative until explicit review and confirmation;
* malformed or uncertain values remain reviewable;
* correction remains explicit;
* confirmed roster replacement retains existing transactional and match-protection rules.

No new roster OCR accuracy claim is required for v0.13.0.

---

## 13. Manual Match Workflow Lane

The alpha must verify the available manual match workflow through:

1. tournament selection;
2. match creation;
3. placement entry;
4. kill entry;
5. validation;
6. review;
7. finalization;
8. score calculation;
9. standings update;
10. reopening finalized data;
11. protected correction behavior where applicable.

Expected placement points remain:

* 1st: 12
* 2nd: 9
* 3rd: 8
* 4th: 7
* 5th: 6
* 6th: 5
* 7th: 4
* 8th: 3
* 9th: 2
* 10th: 1
* 11th: 0
* 12th: 0

One kill remains one point.

No scoring rule may be adjusted to make alpha results pass.

---

## 14. Persistence and Restart Lane

The alpha must verify controlled restart behavior.

At minimum, verify:

* tournament persistence;
* roster persistence;
* draft match persistence;
* finalized match persistence;
* standings restoration or deterministic regeneration;
* application reopen behavior;
* no silent loss of confirmed data;
* finalized state remains protected after restart.

The test must use the production Room provider through the normal application path rather than manually editing the database.

---

## 15. Offline and Recovery Lane

The internal alpha must exercise the already approved offline/recovery behavior using isolated data.

Where reachable through the application, verify:

* operation while network/backend access is unavailable;
* preservation of eligible queued work;
* retry behavior;
* restart with pending work;
* recovery after connectivity becomes available;
* duplicate prevention;
* finalized-state protection;
* non-retryable failures are not converted into blind network retries.

Alpha does not authorize architectural changes to synchronization.

---

## 16. Local Synchronization Lane

The isolated local Supabase lane must verify representative existing synchronization behavior.

The purpose is to prove that the integrated Android/local-backend path is operational before hosted deployment is attempted.

Where currently implemented and reachable, verify:

* authentication;
* owner-scoped data;
* tournament synchronization;
* roster synchronization;
* match synchronization;
* restoration;
* revision-safe behavior;
* retry/idempotency;
* protected finalized operations;
* relevant Storage metadata behavior.

No hosted-project result may be used as substitute evidence.

---

## 17. Correction and Finalized-Data Safety Lane

The alpha must verify that:

* draft data remains editable according to existing rules;
* finalized match data remains protected;
* corrections use the approved controlled correction workflow;
* previous information remains preserved where required;
* unauthorized or invalid direct mutation is blocked;
* reopening the application does not make finalized data editable.

Alpha must not weaken finalized protection for convenience.

---

## 18. Standings Verification

For the controlled tournament:

* expected match totals must be known;
* expected cumulative totals must be known;
* displayed standings must match expected totals;
* placement and kill points must remain deterministic;
* finalized-only contribution rules must remain intact.

If the chosen deterministic fixture creates a tie, the existing tie-break rules must be verified.

A tie is not mandatory for the baseline fixture if avoiding one makes the baseline easier to audit.

---

## 19. CSV Boundary

v0.13.0 should exercise the currently implemented finalized-only CSV preparation/export behavior that is reachable from the merged application.

Verify where currently available:

* finalized-only eligibility;
* 12-team completeness;
* expected scoring values;
* deterministic ordering;
* UTF-8 handling;
* no duplicated teams;
* no missing teams.

The alpha must not invent Android file/share/save functionality that remains deferred.

Deferred Android file/share/save integration is not a v0.13.0 failure.

---

## 20. Google Sheets Boundary

The Android Google Sheets client integration remains deferred where the merged application does not currently expose it.

v0.13.0 must not create new client integration solely to claim complete alpha coverage.

Existing backend/Edge Function behavior may be locally verified where already supported and where required for the isolated backend lane.

Absence of the deferred Android client workflow must be recorded as a known scope limitation rather than an alpha defect.

---

## 21. Error-State Lane

The alpha must deliberately exercise representative safe failures.

These should include existing reachable examples of:

* invalid roster state;
* invalid match state;
* duplicate placement;
* missing required result information;
* unavailable network/backend;
* authentication failure or unavailable session;
* queued synchronization;
* retry state;
* conflict state;
* finalized read-only state;
* unavailable/deferred export integration.

The objective is to verify controlled failure and recovery.

Do not intentionally corrupt Room or Supabase data outside supported application/test mechanisms.

---

## 22. Pre-Execution Technical Verification

Before manual alpha execution, the implementation baseline should remain buildable.

Relevant verification should include the standard non-destructive Android checks appropriate to the current repository, such as:

```text
gradlew.bat test
gradlew.bat lint
gradlew.bat assembleDebug
```

The local Supabase environment must also reconstruct cleanly from the existing migration chain before being used for the alpha cloud lane.

Existing database/backend tests may be rerun where necessary to prove the isolated environment is healthy.

Historical Phase 12 evidence must not be falsely reported as a fresh Phase 13 execution.

---

## 23. Alpha Evidence Record

v0.13.0 must produce a separate sanitized verification document after execution.

The intended next verification document is:

`docs/project-workflow/102_V0_13_0_INTERNAL_ALPHA_VERIFICATION.md`

The verification record should contain:

* repository commit tested;
* branch/state tested;
* Android build tested;
* emulator/device environment;
* local Supabase CLI/environment state;
* sanitized fixture description;
* execution lanes completed;
* expected result;
* actual result;
* PASS/FAIL/BLOCKED classification for each lane;
* defects discovered;
* skipped checks and exact reason;
* known limitations;
* privacy confirmation;
* final v0.13.0 verdict.

Do not include secrets or private tournament evidence.

---

## 24. Defect Recording Policy

Every newly discovered alpha defect must first be recorded rather than immediately repaired.

Use stable identifiers such as:

* `ALPHA-001`
* `ALPHA-002`
* `ALPHA-003`

For each defect record:

* affected workflow;
* reproducible steps;
* expected behavior;
* actual behavior;
* severity;
* whether data integrity is affected;
* whether finalized-data safety is affected;
* whether the defect blocks remaining alpha execution;
* sanitized evidence reference.

Suggested severity classes:

* **BLOCKER** — prevents safe continuation or invalidates core alpha execution;
* **MAJOR** — significant functional/data defect but remaining alpha work can continue safely;
* **MINOR** — limited functional issue with a safe workaround;
* **USABILITY** — interaction or clarity issue without incorrect data behavior.

Defects must not be hidden by changing expected results.

---

## 25. Defect Resolution Boundary

The canonical roadmap assigns general beta defect resolution to:

**v0.13.2 — Beta Defect Resolution**

Therefore, normal alpha findings should be recorded for later controlled resolution.

If a v0.13.0 defect is a true blocker that prevents the alpha from being completed safely:

1. stop the dependent alpha lane;
2. record the defect;
3. do not modify production code on the decision/verification branch;
4. perform a separate read-only root-cause review;
5. obtain explicit approval for a narrow corrective version or task;
6. verify the correction independently;
7. resume alpha only after the correction is merged into `main`.

No opportunistic refactoring is permitted.

---

## 26. Production-Code Boundary

The default approved v0.13.0 implementation file boundary is:

**No production code changes.**

Expected version artifacts are primarily:

* this decisions document;
* the later sanitized alpha verification document;
* local-only controlled test data/evidence that is not committed unless explicitly approved.

If execution proves that a committed test fixture, test harness, or production correction is genuinely required, it must be separately reviewed and approved before editing.

---

## 27. Privacy Rules

The following must remain outside Git:

* private screenshots;
* genuine player identities;
* raw genuine OCR payloads;
* private ground-truth datasets;
* device-private storage paths;
* access tokens;
* API keys;
* service-role keys;
* service-account credentials;
* `.env` secrets;
* local production configuration.

Verification documentation must contain only sanitized evidence.

---

## 28. Alpha Pass Criteria

v0.13.0 may be classified **PASS** only when:

1. the approved controlled tournament can be created and persisted;
2. the 12-team roster workflow completes safely;
3. representative manual match processing completes;
4. scoring matches deterministic expected results;
5. cumulative standings match expected results;
6. draft/finalized state protection behaves correctly;
7. restart/reopen recovery succeeds;
8. representative offline/retry behavior succeeds;
9. the isolated local Supabase lane is operational for the approved representative cloud paths;
10. correction safety remains intact;
11. controlled OCR/screenshot integration is exercised where supported without bypassing human review;
12. the currently reachable CSV boundary behaves correctly;
13. deferred Google Sheets/Android file integration is not falsely treated as implemented;
14. no hosted Supabase modification occurs;
15. no private tournament data is used;
16. all discovered defects are documented;
17. no unresolved BLOCKER remains against the approved alpha scope.

---

## 29. Alpha Failure Classification

The version must be classified **FAIL** or **BLOCKED**, rather than forced to PASS, if:

* deterministic scoring is incorrect;
* finalized-data protection can be bypassed;
* confirmed tournament/roster/match data is lost unexpectedly;
* restart/recovery corrupts data;
* synchronization creates unsafe duplication or incorrect ownership behavior;
* required local migration reconstruction fails;
* a core approved alpha workflow cannot be completed;
* an unresolved security or authorization failure affects the approved local-backend lane;
* the hosted Supabase project was modified outside the approved deployment version;
* required evidence cannot be produced safely.

---

## 30. Explicit Non-Goals

v0.13.0 does not include:

* hosted Supabase deployment;
* production database migration;
* production secrets;
* production Edge Function deployment;
* production security verification;
* genuine tournament beta execution;
* new genuine OCR acceptance thresholds;
* OCR algorithm redesign;
* team-matching algorithm redesign;
* performance optimization;
* broad defect fixing;
* UI redesign;
* architecture refactoring;
* release signing;
* production build variants;
* Play Store/release preparation;
* production operations setup.

---

## 31. Git and Branch Policy

Decision work uses:

`docs/v0.13.0-internal-alpha-decisions`

The decision branch must contain documentation only.

No Android, Room, Supabase, Gradle, test, configuration, or production files should be changed on this branch.

After approval and merge of this decision document, v0.13.0 execution should start from the updated `origin/main`.

Any later verification document must follow the same focused Git workflow.

---

## 32. Rollback and Safety

v0.13.0 is designed to avoid destructive operations.

If local alpha data becomes invalid:

* discard only the isolated local alpha data;
* reconstruct the local Supabase environment from the migration chain;
* recreate the synthetic fixture;
* do not repair the hosted project;
* do not edit already-approved historical migrations.

If an unexpected command appears capable of modifying the hosted Supabase project, stop before running it.

---

## 33. Final Decision

v0.13.0 is approved as a **sanitized internal alpha execution and verification version**.

It will test the complete currently merged application scope using:

* controlled synthetic tournament data;
* normal Room-backed Android operation;
* emulator and/or approved physical-device execution;
* an isolated local Supabase backend for representative cloud-related paths;
* deterministic expected scoring and standings;
* controlled OCR/screenshot integration where already supported;
* explicit error, recovery, restart, retry, correction, and finalized-data safety checks.

No hosted Supabase deployment is authorized.

The next hosted deployment gate is formally positioned as:

**v0.13.0.1 — Hosted Supabase Deployment**

That version requires a separate manual decision workflow after v0.13.0 is completed and before v0.13.1 begins.
# v0.13.0 — Internal Alpha Decisions

## 1. Status

**Decision status: APPROVED**

This document freezes the scope, execution environment, evidence requirements, safety boundaries, and completion criteria for:

**v0.13.0 — Internal Alpha**

Canonical roadmap purpose:

> Test the complete application using controlled local tournament data.

This is primarily an **execution and verification version**.

No production implementation change is planned as part of the normal v0.13.0 scope.

If alpha execution discovers a defect, the defect must be recorded and classified before any corrective implementation is approved.

---

## 2. Governing Sources

v0.13.0 must preserve the current approved Rank-Forge architecture and all completed Phase 0–12 behavior.

The governing sources include:

* `AGENTS.md`
* `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`
* Phase 11 workflow-integration decisions and closure evidence
* Phase 12 QA, compatibility, recovery, security, OCR acceptance, regression, and closure evidence
* `docs/project-workflow/100_PHASE_12_CLOSURE_AUDIT.md`
* existing Android, Room, Supabase, OCR, synchronization, correction, scoring, standings, and export implementation on `origin/main`

The current merged implementation is authoritative for what can actually be exercised during alpha.

v0.13.0 must not reinterpret deferred functionality as implemented functionality.

---

## 3. Version Objective

The objective of v0.13.0 is to verify that the application produced by Phases 0–12 operates coherently as an integrated application when used with controlled and sanitized tournament data.

Internal alpha must test the currently implemented workflow from the perspective of an operator rather than merely re-running isolated unit tests.

The alpha must answer:

1. Can a controlled tournament be created and restored?
2. Can a valid 12-team roster be created, reviewed, confirmed, persisted, and reopened?
3. Can matches be created and processed through the available manual and screenshot-assisted workflows?
4. Are scoring and cumulative standings correct?
5. Are draft and finalized states enforced?
6. Does controlled correction preserve finalized-data protection?
7. Does Room persistence survive restart?
8. Do offline, retry, synchronization, restoration, and conflict-safe paths behave safely against an isolated local backend?
9. Does the available OCR review path remain human-controlled?
10. Can finalized application data reach the currently implemented CSV preparation/export boundary?
11. Do blocked, invalid, unavailable, authentication, network, retry, conflict, and read-only states fail safely?
12. Can all of the above be exercised without using the hosted Supabase project or genuine tournament data?

---

## 4. Alpha Classification

v0.13.0 is classified as:

**Controlled local internal verification**

It is not:

* a hosted backend deployment;
* a production migration;
* a production security verification;
* a real-tournament beta;
* a genuine OCR accuracy evaluation;
* a performance-optimization version;
* a release configuration version;
* a production release;
* a defect-resolution batch.

The version should introduce no new product features.

---

## 5. Approved Alpha Environment

The approved v0.13.0 environment consists of three layers.

### 5.1 Android execution environment

Use:

* the existing debug Android application;
* an approved emulator and/or already approved physical Android device;
* the existing application architecture and build configuration.

A new release build, signing configuration, build variant, or production configuration is not required for v0.13.0.

Release configuration belongs to v0.13.5.

### 5.2 Local persistence environment

Room is the primary local alpha persistence lane.

The alpha must exercise the existing local persistence behavior using isolated controlled data.

No existing personal, beta, real-tournament, or production tournament database should be reused as alpha evidence.

### 5.3 Isolated local Supabase environment

The cloud-related alpha lane must use an **isolated local Supabase instance reconstructed from the migration chain in `origin/main`**.

The local backend lane may exercise:

* authentication;
* ownership;
* tournament/roster synchronization;
* match synchronization;
* restoration;
* offline queue retry;
* revision handling;
* protected operations;
* screenshot metadata/Storage behavior where reachable;
* relevant export backend behavior where already implemented and locally testable.

The local backend must not be treated as production.

---

## 6. Hosted Supabase Prohibition

The connected hosted Supabase project must **not** be modified during v0.13.0.

The following are prohibited during this version:

* hosted `supabase db push`;
* hosted migration application;
* migration-history repair against the hosted project;
* remote schema creation;
* remote table creation;
* remote RLS changes;
* remote Storage bucket or policy changes;
* remote function deployment;
* remote secret creation or modification;
* remote production-data creation;
* destructive remote SQL;
* using the hosted project as the alpha backend.

Any command capable of modifying the hosted Supabase project requires a separate approved deployment version.

---

## 7. Phase 13 Hosted Deployment Sequencing

A dedicated hosted deployment gate is required before controlled real-tournament beta.

The approved placement is:

**v0.13.0.1 — Hosted Supabase Deployment**

The resulting sequence is:

1. `v0.13.0 — Internal Alpha`
2. `v0.13.0.1 — Hosted Supabase Deployment`
3. `v0.13.1 — Controlled Real-Tournament Beta`

v0.13.0.1 requires its **own decision document and approval** before any hosted-project modification.

This v0.13.0 document does not authorize deployment.

v0.13.1 must not begin until the hosted deployment version has completed its migration, backend, security, Edge Function, configuration, and development/beta-build verification gates.

---

## 8. Controlled Alpha Data Policy

All v0.13.0 tournament data must be:

* synthetic;
* sanitized;
* non-sensitive;
* deterministic where expected results are important;
* safe to expose in sanitized verification documentation.

Do not use:

* genuine tournament participant identities;
* real player names unless already explicitly approved as public/non-sensitive test data;
* private tournament screenshots;
* private raw OCR output;
* genuine tournament ground truth;
* private device paths;
* authentication secrets;
* Supabase service-role credentials;
* Google service-account credentials.

---

## 9. Approved Controlled Tournament Fixture

The primary internal-alpha tournament should use:

* exactly **12 teams**;
* unique synthetic team names;
* exactly **4 synthetic players per team** for the baseline roster;
* unique synthetic player names;
* valid fixed team slots 1 through 12;
* multiple controlled matches sufficient to verify cumulative standings;
* deterministic placements;
* deterministic kill totals;
* pre-calculable expected scoring and standings.

The baseline fixture should use the minimum valid four-player roster because it provides a simple deterministic happy-path fixture while remaining within the approved four-to-six-player roster rule.

Additional temporary variations may be entered during alpha to exercise validation and failure behavior, but they do not redefine the baseline fixture.

No requirement is created to commit participant fixture data to Git.

---

## 10. Match Fixture Requirements

The alpha dataset must contain enough controlled matches to verify:

* match creation;
* draft persistence;
* placement entry;
* kill entry;
* validation;
* scoring;
* finalization;
* cumulative standings;
* tie-independent ranking;
* restart/reopen behavior;
* correction protection.

At least one controlled match must complete the normal manual-entry path.

Multiple finalized controlled matches must be used so cumulative standings are verified rather than only single-match totals.

Exact placement and kill values must be recorded in the alpha evidence or a sanitized companion fixture record so expected results can be reproduced.

The alpha does not need to exercise the maximum ten-match tournament limit unless a defect or verification requirement makes that necessary.

---

## 11. OCR and Screenshot Alpha Boundary

v0.13.0 may exercise the already implemented screenshot/OCR workflow using controlled, sanitized, or existing approved test fixtures.

Its purpose is to verify integration and safety, not to establish new OCR accuracy thresholds.

The alpha may verify:

* screenshot intake;
* candidate validation;
* screenshot association;
* OCR invocation;
* parsed candidate presentation;
* review state;
* uncertainty handling;
* manual correction;
* team matching integration where available;
* mandatory human review;
* safe finalization boundaries.

The alpha must not:

* claim new genuine OCR acceptance accuracy;
* change OCR thresholds;
* change supported layouts;
* change crop geometry;
* change matching algorithms;
* silently accept uncertain results;
* substitute synthetic evidence for the genuine acceptance work already completed in Phase 12.

Real tournament rosters and screenshots belong to v0.13.1.

---

## 12. Roster OCR Boundary

Existing roster OCR functionality may be exercised using approved controlled/sanitized inputs.

The operator-review guarantees remain mandatory:

* OCR output remains candidate data;
* candidate data is not authoritative until explicit review and confirmation;
* malformed or uncertain values remain reviewable;
* correction remains explicit;
* confirmed roster replacement retains existing transactional and match-protection rules.

No new roster OCR accuracy claim is required for v0.13.0.

---

## 13. Manual Match Workflow Lane

The alpha must verify the available manual match workflow through:

1. tournament selection;
2. match creation;
3. placement entry;
4. kill entry;
5. validation;
6. review;
7. finalization;
8. score calculation;
9. standings update;
10. reopening finalized data;
11. protected correction behavior where applicable.

Expected placement points remain:

* 1st: 12
* 2nd: 9
* 3rd: 8
* 4th: 7
* 5th: 6
* 6th: 5
* 7th: 4
* 8th: 3
* 9th: 2
* 10th: 1
* 11th: 0
* 12th: 0

One kill remains one point.

No scoring rule may be adjusted to make alpha results pass.

---

## 14. Persistence and Restart Lane

The alpha must verify controlled restart behavior.

At minimum, verify:

* tournament persistence;
* roster persistence;
* draft match persistence;
* finalized match persistence;
* standings restoration or deterministic regeneration;
* application reopen behavior;
* no silent loss of confirmed data;
* finalized state remains protected after restart.

The test must use the production Room provider through the normal application path rather than manually editing the database.

---

## 15. Offline and Recovery Lane

The internal alpha must exercise the already approved offline/recovery behavior using isolated data.

Where reachable through the application, verify:

* operation while network/backend access is unavailable;
* preservation of eligible queued work;
* retry behavior;
* restart with pending work;
* recovery after connectivity becomes available;
* duplicate prevention;
* finalized-state protection;
* non-retryable failures are not converted into blind network retries.

Alpha does not authorize architectural changes to synchronization.

---

## 16. Local Synchronization Lane

The isolated local Supabase lane must verify representative existing synchronization behavior.

The purpose is to prove that the integrated Android/local-backend path is operational before hosted deployment is attempted.

Where currently implemented and reachable, verify:

* authentication;
* owner-scoped data;
* tournament synchronization;
* roster synchronization;
* match synchronization;
* restoration;
* revision-safe behavior;
* retry/idempotency;
* protected finalized operations;
* relevant Storage metadata behavior.

No hosted-project result may be used as substitute evidence.

---

## 17. Correction and Finalized-Data Safety Lane

The alpha must verify that:

* draft data remains editable according to existing rules;
* finalized match data remains protected;
* corrections use the approved controlled correction workflow;
* previous information remains preserved where required;
* unauthorized or invalid direct mutation is blocked;
* reopening the application does not make finalized data editable.

Alpha must not weaken finalized protection for convenience.

---

## 18. Standings Verification

For the controlled tournament:

* expected match totals must be known;
* expected cumulative totals must be known;
* displayed standings must match expected totals;
* placement and kill points must remain deterministic;
* finalized-only contribution rules must remain intact.

If the chosen deterministic fixture creates a tie, the existing tie-break rules must be verified.

A tie is not mandatory for the baseline fixture if avoiding one makes the baseline easier to audit.

---

## 19. CSV Boundary

v0.13.0 should exercise the currently implemented finalized-only CSV preparation/export behavior that is reachable from the merged application.

Verify where currently available:

* finalized-only eligibility;
* 12-team completeness;
* expected scoring values;
* deterministic ordering;
* UTF-8 handling;
* no duplicated teams;
* no missing teams.

The alpha must not invent Android file/share/save functionality that remains deferred.

Deferred Android file/share/save integration is not a v0.13.0 failure.

---

## 20. Google Sheets Boundary

The Android Google Sheets client integration remains deferred where the merged application does not currently expose it.

v0.13.0 must not create new client integration solely to claim complete alpha coverage.

Existing backend/Edge Function behavior may be locally verified where already supported and where required for the isolated backend lane.

Absence of the deferred Android client workflow must be recorded as a known scope limitation rather than an alpha defect.

---

## 21. Error-State Lane

The alpha must deliberately exercise representative safe failures.

These should include existing reachable examples of:

* invalid roster state;
* invalid match state;
* duplicate placement;
* missing required result information;
* unavailable network/backend;
* authentication failure or unavailable session;
* queued synchronization;
* retry state;
* conflict state;
* finalized read-only state;
* unavailable/deferred export integration.

The objective is to verify controlled failure and recovery.

Do not intentionally corrupt Room or Supabase data outside supported application/test mechanisms.

---

## 22. Pre-Execution Technical Verification

Before manual alpha execution, the implementation baseline should remain buildable.

Relevant verification should include the standard non-destructive Android checks appropriate to the current repository, such as:

```text
gradlew.bat test
gradlew.bat lint
gradlew.bat assembleDebug
```

The local Supabase environment must also reconstruct cleanly from the existing migration chain before being used for the alpha cloud lane.

Existing database/backend tests may be rerun where necessary to prove the isolated environment is healthy.

Historical Phase 12 evidence must not be falsely reported as a fresh Phase 13 execution.

---

## 23. Alpha Evidence Record

v0.13.0 must produce a separate sanitized verification document after execution.

The intended next verification document is:

`docs/project-workflow/102_V0_13_0_INTERNAL_ALPHA_VERIFICATION.md`

The verification record should contain:

* repository commit tested;
* branch/state tested;
* Android build tested;
* emulator/device environment;
* local Supabase CLI/environment state;
* sanitized fixture description;
* execution lanes completed;
* expected result;
* actual result;
* PASS/FAIL/BLOCKED classification for each lane;
* defects discovered;
* skipped checks and exact reason;
* known limitations;
* privacy confirmation;
* final v0.13.0 verdict.

Do not include secrets or private tournament evidence.

---

## 24. Defect Recording Policy

Every newly discovered alpha defect must first be recorded rather than immediately repaired.

Use stable identifiers such as:

* `ALPHA-001`
* `ALPHA-002`
* `ALPHA-003`

For each defect record:

* affected workflow;
* reproducible steps;
* expected behavior;
* actual behavior;
* severity;
* whether data integrity is affected;
* whether finalized-data safety is affected;
* whether the defect blocks remaining alpha execution;
* sanitized evidence reference.

Suggested severity classes:

* **BLOCKER** — prevents safe continuation or invalidates core alpha execution;
* **MAJOR** — significant functional/data defect but remaining alpha work can continue safely;
* **MINOR** — limited functional issue with a safe workaround;
* **USABILITY** — interaction or clarity issue without incorrect data behavior.

Defects must not be hidden by changing expected results.

---

## 25. Defect Resolution Boundary

The canonical roadmap assigns general beta defect resolution to:

**v0.13.2 — Beta Defect Resolution**

Therefore, normal alpha findings should be recorded for later controlled resolution.

If a v0.13.0 defect is a true blocker that prevents the alpha from being completed safely:

1. stop the dependent alpha lane;
2. record the defect;
3. do not modify production code on the decision/verification branch;
4. perform a separate read-only root-cause review;
5. obtain explicit approval for a narrow corrective version or task;
6. verify the correction independently;
7. resume alpha only after the correction is merged into `main`.

No opportunistic refactoring is permitted.

---

## 26. Production-Code Boundary

The default approved v0.13.0 implementation file boundary is:

**No production code changes.**

Expected version artifacts are primarily:

* this decisions document;
* the later sanitized alpha verification document;
* local-only controlled test data/evidence that is not committed unless explicitly approved.

If execution proves that a committed test fixture, test harness, or production correction is genuinely required, it must be separately reviewed and approved before editing.

---

## 27. Privacy Rules

The following must remain outside Git:

* private screenshots;
* genuine player identities;
* raw genuine OCR payloads;
* private ground-truth datasets;
* device-private storage paths;
* access tokens;
* API keys;
* service-role keys;
* service-account credentials;
* `.env` secrets;
* local production configuration.

Verification documentation must contain only sanitized evidence.

---

## 28. Alpha Pass Criteria

v0.13.0 may be classified **PASS** only when:

1. the approved controlled tournament can be created and persisted;
2. the 12-team roster workflow completes safely;
3. representative manual match processing completes;
4. scoring matches deterministic expected results;
5. cumulative standings match expected results;
6. draft/finalized state protection behaves correctly;
7. restart/reopen recovery succeeds;
8. representative offline/retry behavior succeeds;
9. the isolated local Supabase lane is operational for the approved representative cloud paths;
10. correction safety remains intact;
11. controlled OCR/screenshot integration is exercised where supported without bypassing human review;
12. the currently reachable CSV boundary behaves correctly;
13. deferred Google Sheets/Android file integration is not falsely treated as implemented;
14. no hosted Supabase modification occurs;
15. no private tournament data is used;
16. all discovered defects are documented;
17. no unresolved BLOCKER remains against the approved alpha scope.

---

## 29. Alpha Failure Classification

The version must be classified **FAIL** or **BLOCKED**, rather than forced to PASS, if:

* deterministic scoring is incorrect;
* finalized-data protection can be bypassed;
* confirmed tournament/roster/match data is lost unexpectedly;
* restart/recovery corrupts data;
* synchronization creates unsafe duplication or incorrect ownership behavior;
* required local migration reconstruction fails;
* a core approved alpha workflow cannot be completed;
* an unresolved security or authorization failure affects the approved local-backend lane;
* the hosted Supabase project was modified outside the approved deployment version;
* required evidence cannot be produced safely.

---

## 30. Explicit Non-Goals

v0.13.0 does not include:

* hosted Supabase deployment;
* production database migration;
* production secrets;
* production Edge Function deployment;
* production security verification;
* genuine tournament beta execution;
* new genuine OCR acceptance thresholds;
* OCR algorithm redesign;
* team-matching algorithm redesign;
* performance optimization;
* broad defect fixing;
* UI redesign;
* architecture refactoring;
* release signing;
* production build variants;
* Play Store/release preparation;
* production operations setup.

---

## 31. Git and Branch Policy

Decision work uses:

`docs/v0.13.0-internal-alpha-decisions`

The decision branch must contain documentation only.

No Android, Room, Supabase, Gradle, test, configuration, or production files should be changed on this branch.

After approval and merge of this decision document, v0.13.0 execution should start from the updated `origin/main`.

Any later verification document must follow the same focused Git workflow.

---

## 32. Rollback and Safety

v0.13.0 is designed to avoid destructive operations.

If local alpha data becomes invalid:

* discard only the isolated local alpha data;
* reconstruct the local Supabase environment from the migration chain;
* recreate the synthetic fixture;
* do not repair the hosted project;
* do not edit already-approved historical migrations.

If an unexpected command appears capable of modifying the hosted Supabase project, stop before running it.

---

## 33. Final Decision

v0.13.0 is approved as a **sanitized internal alpha execution and verification version**.

It will test the complete currently merged application scope using:

* controlled synthetic tournament data;
* normal Room-backed Android operation;
* emulator and/or approved physical-device execution;
* an isolated local Supabase backend for representative cloud-related paths;
* deterministic expected scoring and standings;
* controlled OCR/screenshot integration where already supported;
* explicit error, recovery, restart, retry, correction, and finalized-data safety checks.

No hosted Supabase deployment is authorized.

The next hosted deployment gate is formally positioned as:

**v0.13.0.1 — Hosted Supabase Deployment**

That version requires a separate manual decision workflow after v0.13.0 is completed and before v0.13.1 begins.
