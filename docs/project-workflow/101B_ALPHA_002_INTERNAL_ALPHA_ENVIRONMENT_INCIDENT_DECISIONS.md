# ALPHA-002 — Internal Alpha Environment Incident Decisions

## 1. Status

**Decision status: APPROVED FOR CONTROLLED ALPHA RECOVERY**

This document governs the v0.13.0 Internal Alpha environment incident:

**ALPHA-002 — Hosted Backend Used During Local Internal Alpha**

ALPHA-002 is classified as:

**BLOCKER — Test Environment / Configuration Incident**

It is not currently classified as an application-code defect.

This document does not authorize any production-code change, Supabase migration, hosted deployment, hosted cleanup, database mutation, Storage mutation, Edge Function deployment, or new product behavior.

Its purpose is to determine how v0.13.0 may proceed after cloud-related alpha execution unintentionally contacted the hosted Rank-Forge Supabase project.

---

## 2. Governing Sources

This decision is governed by:

- `AGENTS.md`
- `docs/project-workflow/00_PHASE_AND_VERSION_ROADMAP.md`
- `docs/project-workflow/100_PHASE_12_CLOSURE_AUDIT.md`
- `docs/project-workflow/101_V0_13_0_INTERNAL_ALPHA_DECISIONS.md`
- `docs/project-workflow/101A_ALPHA_001_FIRST_CLOUD_UPLOAD_CORRECTION_DECISIONS.md`
- the merged ALPHA-001 corrective implementation
- the current synchronization, authentication, Room, Storage, revision, queue, and Supabase implementation on `main`
- the private sanitized ALPHA-002 incident record maintained outside Git

The current explicit project decision remains that v0.13.0 is a controlled local Internal Alpha and that hosted Supabase deployment is not part of normal v0.13.0 execution.

---

## 3. Incident Summary

During v0.13.0 cloud synchronization verification, the Android debug application was discovered to have been built with a hosted Supabase URL rather than the approved isolated local Supabase URL.

As a result, some Android authentication and synchronization attempts were directed to the hosted Rank-Forge Supabase project.

Read-only hosted API-log inspection established successful hosted authentication activity and failed application RPC requests.

Observed hosted authentication activity included successful HTTP responses for:

- signup;
- password-token authentication;
- refresh-token authentication.

Observed tournament synchronization attempts against the hosted project returned HTTP 404 for:

`/rest/v1/rpc/write_tournament_snapshot`

The Rank-Forge application schema and revision-safe RPC migration chain had intentionally not yet been deployed to the hosted project.

The hosted 404 response therefore must not be interpreted as a failure of the corrected local synchronization implementation.

---

## 4. Proven Hosted Impact

The following hosted impact is established by available evidence.

### 4.1 Authentication

Hosted Supabase Auth state was modified through successful authentication requests.

A hosted authentication user/session was created, updated, or refreshed during v0.13.0 execution.

This is a genuine hosted-project side effect.

### 4.2 Application database

No successful hosted Rank-Forge tournament or roster database write is evidenced.

The attempted `write_tournament_snapshot` requests returned HTTP 404 because the RPC did not exist in the hosted project.

No evidence establishes successful creation of:

- tournaments;
- team slots;
- roster players;
- matches;
- match results;
- synchronization revisions;
- correction audit data;
- screenshot metadata.

### 4.3 Database deployment

No hosted migration application is evidenced.

No hosted schema deployment is evidenced.

No hosted RLS change is evidenced.

No hosted RPC creation or replacement is evidenced.

No hosted trigger or privileged database-function deployment is evidenced.

### 4.4 Storage

No hosted screenshot object upload is evidenced.

No hosted Storage metadata write is evidenced.

No hosted bucket creation, deletion, configuration change, or policy change is evidenced.

### 4.5 Edge Functions

No hosted Edge Function deployment or modification is evidenced.

---

## 5. Incident Classification

ALPHA-002 is classified as a:

**Test Environment / Configuration Incident**

The evidence does not currently justify classifying ALPHA-002 as:

- a synchronization algorithm defect;
- a Room defect;
- a Supabase schema defect;
- a mapper defect;
- a revision-contract defect;
- an authentication implementation defect;
- an RLS defect;
- a Storage defect.

The Android application contacted the wrong backend because its build-time local configuration contained the hosted Supabase endpoint.

Therefore ALPHA-002 must remain separate from ALPHA-001.

---

## 6. Relationship to ALPHA-001

ALPHA-001 remains:

**First Cloud Upload Missing Revision**

ALPHA-001 was a genuine application defect.

Its root cause was that a never-synchronized local tournament with:

- a valid positive `localRevision`; and
- `baseCloudRevision = null`

could lose its explicit first-cloud-create expectation when `localRevision` was greater than 1.

The approved correction changed first-write expectation behavior so that:

1. a known `baseCloudRevision` remains authoritative;
2. a valid positive local revision with no cloud base receives expected revision `0`;
3. an invalid or missing local revision receives no write expectation.

That correction was implemented, regression tested, independently verified, and merged into `main`.

ALPHA-002 does not invalidate that code correction.

However, ALPHA-001 remains operationally OPEN because its required successful isolated-local-Supabase runtime reverification has not yet completed.

The hosted HTTP 404 and resulting Android validation failure are not valid ALPHA-001 reverification evidence.

---

## 7. Valid Existing v0.13.0 Evidence

The ALPHA-002 environment incident does not automatically invalidate alpha work that was independent of Supabase.

The following previously completed controlled evidence remains valid unless later evidence proves otherwise:

- controlled tournament creation;
- Room tournament persistence;
- exactly 12 fixed team slots;
- confirmed 48-player roster;
- roster restart persistence;
- manual Match 1 workflow;
- manual Match 2 workflow;
- manual Match 3 workflow;
- placement validation;
- kill entry;
- match validation;
- deterministic scoring;
- cumulative standings;
- deterministic final standings;
- finalized-match protection;
- restart/reopen persistence;
- currently reachable finalized-only CSV preparation boundary.

These behaviors use the local application and Room-backed workflow and do not need to be repeated solely because the Supabase endpoint was misconfigured.

No alpha result may be discarded or repeated merely to manufacture a cleaner execution history.

---

## 8. Invalidated Cloud Evidence

The following evidence collected while the Android build targeted hosted Supabase is invalid as evidence for the required isolated-local-Supabase lane:

- hosted authentication activity presented as local authentication evidence;
- post-ALPHA-001 tournament upload attempts;
- hosted `write_tournament_snapshot` HTTP 404 results;
- Android validation results derived from those hosted RPC failures;
- any conclusion that ALPHA-001 failed after its correction based on those hosted requests;
- any synchronization success or failure conclusion that depended on the hosted backend;
- any hosted response used as substitute evidence for local RLS, revision, ownership, retry, restoration, or Storage behavior.

This evidence remains historically relevant as ALPHA-002 incident evidence and must not be erased from the final alpha record.

---

## 9. Clean Local Re-Execution Decision

A controlled re-execution of the invalidated cloud-dependent v0.13.0 lanes is approved.

This is not a restart of the entire Internal Alpha.

It is a recovery execution limited to evidence that was:

- not previously completed;
- invalidated by ALPHA-002; or
- explicitly required for ALPHA-001 runtime reverification.

Valid Room-only and manual-workflow evidence remains reusable.

The clean recovery execution must use:

- the current merged `main`;
- the merged ALPHA-001 correction;
- an Android debug APK built from local Supabase configuration;
- an isolated local Supabase instance reconstructed from the repository migration chain;
- synthetic and sanitized alpha data;
- no hosted project as an application backend.

---

## 10. Environment Identity Gate

Before any cloud-dependent alpha action is resumed, the environment identity must be explicitly verified.

The gate requires all of the following:

1. `local.properties` resolves the Android Supabase URL to the isolated local API;
2. generated debug `BuildConfig.SUPABASE_URL` points to the isolated local API;
3. the installed debug APK is the rebuilt local-target APK;
4. ADB reverse or the approved equivalent local networking route is active when required by the physical device;
5. local Supabase is healthy;
6. the local database has been reconstructed from the current migration chain;
7. the required local RPCs, RLS policies, tables, Storage configuration, and synchronization objects are present;
8. authentication used for subsequent alpha work belongs to the isolated local Supabase instance.

The environment identity check must occur before the first cloud write.

A hosted `supabase.co` endpoint must not be used by the Android alpha build during the recovery execution.

---

## 11. Local Backend Reconstruction

The isolated local Supabase instance must be treated as disposable alpha infrastructure.

Before cloud recovery execution, it may be reconstructed from the current repository migration chain.

This reconstruction is authorized only for the local Supabase instance.

It must not:

- repair the hosted project;
- apply migrations to hosted Supabase;
- synchronize migration history with hosted Supabase;
- create hosted tables;
- create hosted RPCs;
- create hosted policies;
- create hosted Storage resources;
- deploy hosted Edge Functions.

If local migration reconstruction fails, cloud-dependent alpha execution stops and the failure must be investigated before continuing.

---

## 12. Local Authentication Recovery

Because the Android application previously used hosted authentication state, cloud recovery execution must establish authentication against isolated local Supabase.

Existing hosted authentication state must not be treated as local alpha authentication evidence.

The application may use the normal supported logout/sign-in/sign-up flow as needed to establish an isolated local session.

No manual editing of Room data, authentication storage, tokens, or application databases is authorized solely to make the alpha pass.

No hosted Auth deletion or cleanup is required during v0.13.0.

---

## 13. Approved Cloud Recovery Order

After the environment identity and local migration gates pass, the cloud-dependent alpha work should proceed in dependency order.

### 13.1 Local authentication

Verify:

- local authentication succeeds;
- session state is restored according to the implemented behavior;
- application requests remain local;
- ownership identity is available to the cloud synchronization workflow.

### 13.2 Tournament and roster first upload

Use the controlled prepared alpha tournament.

Verify:

- first upload reaches isolated local Supabase;
- `write_tournament_snapshot` is available;
- expected revision `0` is accepted only for the missing cloud tournament;
- exactly one cloud tournament is created;
- all required team slots are represented;
- the expected roster players are represented;
- owner identity is correct;
- no duplicate tournament is created.

### 13.3 ALPHA-001 revision confirmation

After first upload succeeds, verify:

- cloud revision becomes positive;
- initial persisted cloud revision is revision `1` where defined by the current RPC contract;
- Room stores the positive cloud baseline;
- `baseCloudRevision` is no longer null for the successfully synchronized tournament.

This is the first required runtime acceptance gate for ALPHA-001.

### 13.4 Second revision-safe synchronization

Perform a legitimate subsequent local change through the supported application workflow if required to create a second write.

Verify:

- the positive known cloud baseline is used;
- create expectation `0` is not reused;
- the existing cloud tournament is updated revision-safely;
- revision advances according to the existing RPC contract;
- no duplicate tournament is created;
- stale-write protection remains intact.

Successful completion of this gate is required before ALPHA-001 may be classified CLOSED.

### 13.5 Match synchronization

Only after the tournament/roster baseline is valid, exercise representative supported match synchronization.

Verify the reachable draft and finalized paths according to the current production implementation.

### 13.6 Restoration

Verify representative cloud-to-local restoration through existing application workflows.

Preserve:

- ownership checks;
- revision/divergence safety;
- local transaction safety;
- unrelated local tournament data.

### 13.7 Offline queue and recovery

Exercise supported backend unavailability and recovery behavior.

Verify:

- eligible work is preserved;
- retry behavior follows existing classifications;
- duplicate work is not blindly created;
- retry counters behave as implemented;
- non-retryable validation, authorization, persistence, or conflict failures remain non-retryable;
- restart/foreground recovery behaves safely.

### 13.8 Storage and metadata

Where currently reachable using synthetic/sanitized screenshots, verify representative local Storage behavior and metadata behavior.

Do not introduce new screenshot workflows or genuine private tournament evidence solely for this incident recovery.

---

## 14. ALPHA-001 Closure Gate

ALPHA-001 may be classified CLOSED only after the clean isolated-local-Supabase recovery execution proves:

1. first tournament/roster upload succeeds;
2. revision `0` is used only as a first-create expectation;
3. the server returns/persists the first positive revision;
4. Room records the positive cloud baseline;
5. a subsequent synchronization uses the positive baseline;
6. revision advances safely;
7. no duplicate tournament is created;
8. stale/conflict protection remains intact.

Passing unit tests alone is not sufficient for ALPHA-001 closure because the existing ALPHA-001 decision explicitly requires alpha runtime reverification.

---

## 15. ALPHA-002 Closure Gate

ALPHA-002 may be classified RESOLVED when:

- its incident record is preserved;
- the wrong-backend cause is documented;
- the Android alpha environment is proven to target isolated local Supabase;
- invalidated cloud evidence is not reused;
- a clean local cloud-dependent recovery execution is completed or independently blocked by another recorded defect;
- no additional unauthorized hosted-project use occurs during v0.13.0.

ALPHA-002 resolution does not erase the fact that hosted Auth was unintentionally modified.

---

## 16. Hosted Auth State

The hosted Auth side effect must remain documented.

No v0.13.0 cleanup of the hosted authentication user/session is authorized by this decision.

Do not:

- delete hosted Auth users;
- manually modify hosted Auth users;
- revoke hosted users solely to sanitize alpha history;
- apply migrations to compensate for the incident;
- make unrelated hosted configuration changes.

Any future hosted cleanup must occur under an explicitly approved hosted-environment task with its own safety and rollback considerations.

---

## 17. Hosted Deployment Prohibition

This incident does not authorize hosted deployment.

During the remaining v0.13.0 Internal Alpha work, do not:

- run hosted `supabase db push`;
- apply hosted migrations;
- repair hosted migration history;
- create hosted schema objects;
- deploy hosted RLS;
- create or replace hosted RPCs;
- create hosted Storage buckets or policies;
- deploy hosted Edge Functions;
- modify hosted secrets;
- use hosted Rank-Forge as the alpha backend.

The hosted deployment gate remains separate from v0.13.0 recovery execution.

---

## 18. v0.13.0 Final Verdict Treatment

The original contaminated cloud execution cannot itself receive a PASS classification.

It must remain documented as a BLOCKED execution because ALPHA-002 violated the approved backend-environment boundary.

However, the entire v0.13.0 version does not need to be permanently failed solely because an internal-alpha environment incident occurred.

A controlled clean recovery execution is allowed to provide replacement evidence for the invalidated cloud-dependent lanes while preserving the historical incident.

The final v0.13.0 verification document must distinguish:

- valid evidence from the original execution;
- invalidated hosted-backend evidence;
- ALPHA-001 defect and correction;
- ALPHA-002 incident;
- clean recovery execution evidence;
- any remaining blockers or limitations.

v0.13.0 may receive an overall PASS verdict only if all required alpha acceptance criteria are ultimately satisfied through valid evidence and no unresolved BLOCKER remains.

The final verification document must not state that no hosted interaction occurred.

Instead, it must explicitly record ALPHA-002 and explain its disposition.

---

## 19. Interpretation of the Hosted-Prohibition Criterion

The approved v0.13.0 decisions require the hosted project not to be used or modified during normal Internal Alpha execution.

ALPHA-002 proves that this boundary was violated.

This decision does not redefine that requirement as having been satisfied.

Instead:

- the original cloud execution is classified as contaminated/BLOCKED;
- the incident is preserved permanently in the alpha evidence;
- subsequent valid local execution is treated as a clean recovery execution;
- the final version verdict must disclose the incident rather than erase it.

A later clean run is corrective verification, not evidence that the incident never occurred.

---

## 20. Application-Code Boundary

ALPHA-002 currently authorizes:

**No production-code changes.**

No Android production file is approved for modification.

No Room production file is approved for modification.

No Supabase migration is approved.

No test file is approved solely for ALPHA-002.

No authentication behavior change is approved.

No synchronization behavior change is approved.

No UI behavior change is approved.

If the clean local recovery execution discovers another genuine implementation defect, that finding must receive its own stable alpha identifier and separate decision gate before production correction.

---

## 21. Local Configuration Boundary

`local.properties` is local untracked configuration and must remain outside Git.

The local alpha configuration may point to the isolated local Supabase endpoint.

Hosted credentials, private keys, tokens, or authentication data must not be committed.

Verification documentation may record only sanitized endpoint classification such as:

- isolated local Supabase;
- hosted Supabase incident;

and must not expose secrets.

---

## 22. Evidence Preservation

Private local alpha evidence may remain outside Git.

The final committed verification document must be sanitized.

Do not commit:

- authentication passwords;
- access tokens;
- refresh tokens;
- API keys;
- private screenshots;
- genuine player identities;
- private OCR payloads;
- device-private files;
- local configuration backups.

ALPHA-002 must be represented sufficiently in sanitized verification documentation to preserve the historical execution record.

---

## 23. Canonical Roadmap Inconsistency

The current canonical roadmap lists:

- `v0.13.0 — Internal Alpha`
- `v0.13.1 — Controlled Real-Tournament Beta`

without the separately approved hosted deployment gate.

The merged v0.13.0 decision and ALPHA-001 decision establish the intended sequence:

1. `v0.13.0 — Internal Alpha`
2. `v0.13.0.1 — Hosted Supabase Deployment`
3. `v0.13.1 — Controlled Real-Tournament Beta`

This is a material documentation inconsistency.

The roadmap must be reconciled before v0.13.0 is formally closed and before hosted deployment begins.

This document does not itself modify the roadmap.

No later existing version is renumbered.

---

## 24. Duplicate v0.13.0 Decision Content

The current `101_V0_13_0_INTERNAL_ALPHA_DECISIONS.md` contains duplicated decision content.

The duplication is a documentation-quality issue.

It does not create two separate v0.13.0 decisions because the duplicated sections express the same decision.

The duplication should be corrected through a separate documentation-only cleanup before v0.13.0 closure.

This ALPHA-002 document does not edit the existing decision document.

---

## 25. Recovery Verification Artifact

After valid execution completes, the intended sanitized verification artifact remains:

`docs/project-workflow/102_V0_13_0_INTERNAL_ALPHA_VERIFICATION.md`

That document must record:

- repository commit tested;
- Android build/environment;
- isolated local Supabase environment;
- valid original alpha evidence;
- ALPHA-001;
- ALPHA-002;
- invalidated cloud evidence;
- clean recovery execution;
- expected and actual results;
- PASS/FAIL/BLOCKED results by lane;
- skipped checks and reason;
- known limitations;
- privacy confirmation;
- final v0.13.0 verdict.

---

## 26. Required Documentation Before v0.13.0 Closure

Before v0.13.0 may be formally closed, the following documentation inconsistencies must be resolved:

1. reconcile the canonical roadmap with the approved `v0.13.0.1 — Hosted Supabase Deployment` sequencing;
2. remove the accidental duplicate content from `101_V0_13_0_INTERNAL_ALPHA_DECISIONS.md` without changing its approved meaning;
3. create the final sanitized v0.13.0 verification record;
4. accurately record ALPHA-001 and ALPHA-002 outcomes.

These are documentation/governance tasks and must not be combined with unrelated production implementation.

---

## 27. Rollback and Safety

If the local recovery Supabase environment becomes invalid:

- discard or reconstruct only the isolated local Supabase environment;
- rebuild it from the current migration chain;
- preserve valid Room alpha data where safe;
- do not repair or modify hosted Supabase;
- do not edit already-approved historical migrations;
- do not weaken revision, RLS, ownership, finalized-data, or queue protections.

If any command appears capable of modifying hosted Supabase during v0.13.0, stop before running it.

---

## 28. Implementation Method

ALPHA-002 does not require Codex implementation at this stage.

The recovery execution is primarily controlled environment verification.

Codex may be used for read-only repository analysis if an unexpected failure requires tracing implementation behavior.

Codex must not be used to make speculative fixes without a separate approved decision gate.

---

## 29. Completion Criteria

This ALPHA-002 decision gate is complete when:

- this decision document is reviewed and merged;
- no production implementation is included in its branch;
- ALPHA-002 remains separately recorded;
- valid versus invalidated v0.13.0 evidence is clearly distinguished;
- controlled local re-execution is formally authorized;
- hosted cleanup remains prohibited;
- ALPHA-001 remains separately governed;
- the roadmap inconsistency and duplicate v0.13.0 decision content are explicitly retained as documentation tasks before closure.

---

## 30. Final Decision

ALPHA-002 is recognized as a genuine v0.13.0 Internal Alpha environment incident.

It contaminated the original cloud-dependent alpha execution because the Android application contacted hosted Supabase instead of the required isolated local Supabase environment.

The incident is not currently evidence of another application-code defect.

Previously valid Room/manual/scoring/standings/CSV evidence remains valid.

Cloud-dependent evidence collected against hosted Supabase is invalid as local-alpha evidence.

A clean local-only recovery execution of the invalidated and outstanding cloud-dependent lanes is approved after this decision gate is merged.

ALPHA-001 remains corrected but operationally open until its isolated-local-Supabase runtime acceptance succeeds.

No hosted cleanup or deployment is authorized.

The historical incident must remain visible in the final v0.13.0 verification record.

The canonical roadmap must be reconciled with the approved `v0.13.0.1 — Hosted Supabase Deployment` sequencing before v0.13.0 closure.