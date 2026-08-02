# Phase 12 v0.12.8.1 — OCR Team-Matching Evidence Bridge Decisions

## Status

**Approved prerequisite patch for v0.12.8 genuine OCR acceptance testing.**

## Purpose

v0.12.8.1 adds the smallest missing domain boundary required to feed genuine scoreboard OCR player evidence into the already completed Phase 9 team-matching algorithms.

It does not change matching formulas or result-output requirements.

---

## 1. Acceptance Intent

Player names extracted from a scoreboard are matching evidence only.

Rank-Forge does not require exact player-name transcription as a result output.

For example:

```text
Roster: KTS BE4STz
OCR:    KTS BE4ST2
```

is acceptable when the existing normalization and similarity-matching pipeline still identifies the correct tournament team.

The acceptance target remains the already-entered team identity.

---

## 2. Existing Gap

The repository contains completed implementations for:

- player-name normalization
- player similarity matching
- team candidate scoring
- top-three suggestions
- confidence classification
- assignment safety

However, no current production caller supplies scoreboard OCR-derived player-name collections to those matching components.

The current OCR review UI accepts already-computed matching evidence and does not create it.

Therefore genuine v0.12.8 end-to-end team-identification measurement currently lacks the required OCR-to-matching evidence bridge.

---

## 3. Existing Matching Algorithms Remain Authoritative

v0.12.8.1 must reuse without modification:

```text
PlayerNameComparisonNormalizer
PlayerNameSimilarityMatcher
TeamCandidateScorer
TopTeamCandidateSuggestionProvider
TeamMatchConfidenceTierClassifier
TeamAssignmentSafetyEvaluator
```

No second matching algorithm may be created.

---

## 4. Player Evidence Model

The bridge must preserve multiple rough OCR player strings for each scoreboard result row.

Conceptually:

```text
placement row
-> player OCR evidence 1
-> player OCR evidence 2
-> player OCR evidence 3
-> player OCR evidence 4
```

The bridge must not collapse a multi-player team row into one exact player-name value.

The evidence strings remain unconfirmed OCR input.

---

## 5. Exact-Name Boundary

v0.12.8.1 does not attempt to establish exact player-name correctness.

It must not:

- require byte-perfect player spelling
- manually correct OCR before matching
- rewrite player names using ground truth
- expose OCR player strings as official result output
- replace roster display names with OCR output
- treat punctuation or decorative-symbol differences alone as team-identification failures

Approximate player evidence is intentionally passed to the existing comparison normalizer and similarity matcher.

---

## 6. Team Output Boundary

The useful matching output is:

```text
team slot
```

The tournament's existing entered team name remains the authoritative display name associated with that slot.

The OCR process does not need to recognize the team name from the result screenshot.

---

## 7. Raw OCR Input

The bridge consumes existing:

```text
RawOcrExtractionResult.Extracted
```

evidence.

It must preserve the source preprocessing candidate and its coordinate context.

It must not invoke cloud OCR or create another OCR engine.

---

## 8. Candidate Isolation

Each preprocessing candidate must be processed independently.

Evidence from baseline, scaled, and contrast-enhanced candidates must not simply be merged into one list because that could count the same player multiple times.

Conceptually:

```text
candidate 0 -> row evidence
candidate 1 -> row evidence
candidate 2 -> row evidence
...
```

A later acceptance caller may compare or retry candidates according to an explicitly approved deterministic policy.

v0.12.8.1 does not select a candidate using genuine ground truth.

---

## 9. Coordinate Mapping

Raw ML Kit geometry is relative to the image actually submitted to ML Kit.

The existing preprocessing pipeline may:

1. crop the overall scoreboard
2. scale that crop
3. contrast-enhance a scaled candidate

Therefore the bridge must not assume OCR geometry uses the original full-screenshot coordinate system.

Player-field zones must be transformed into the current preprocessing candidate's coordinate space using existing layout and preprocessing metadata.

The implementation must reuse the existing:

```text
FreeFireMaxScoreboardLayout
OcrPreprocessingCandidate
```

metadata.

No new scoreboard coordinates are authorized.

---

## 10. Player Evidence Collection

For each supported fixed-layout scoreboard row, the collector should return:

```text
rowIndex
expectedPlacementId
detectedPlayerNames
```

`detectedPlayerNames` is a collection of rough OCR strings.

Collection behavior must:

- use player-name field geometry
- trim empty surrounding whitespace
- exclude blank strings
- exclude purely numeric placement/kill values where appropriate
- preserve imperfect OCR text
- prevent the same raw OCR entity from being counted repeatedly inside one candidate
- maintain deterministic ordering

No fuzzy matching occurs inside the collector.

---

## 11. Team Identification Evaluation

The evaluator consumes:

```text
row player evidence
+
TeamCandidateRosterInput for tournament slots
```

For each scoreboard row it must invoke:

```text
TopTeamCandidateSuggestionProvider.suggestTopThree()
```

and then the existing confidence and safety boundaries.

The evaluator may expose:

```text
rowIndex
expectedPlacementId
detectedPlayerNames
suggestions
confidenceAssessment
safetyResult
```

It must not persist an assignment.

---

## 12. Roster Mapping

Tournament roster players remain authoritative matching candidates.

For one team slot:

```text
RosterPlayer.displayName
```

becomes the existing:

```text
TeamCandidateRosterInput.rosterPlayerNames
```

input.

The demo or real team name itself does not participate in player similarity calculations.

---

## 13. Assignment Safety

Existing assignment-safety rules remain unchanged.

Automatic assignment still requires the existing conditions, including:

- automatic confidence tier
- minimum contributing player-match count
- candidate-lead requirement
- unique-team protection

v0.12.8.1 must not weaken those rules to make genuine acceptance easier.

---

## 14. Manual Review

A correct rank-1 team suggestion may still require confirmation or manual review.

That is acceptable.

Team-identification accuracy and automatic-assignment rate remain different measurements.

v0.12.8 uses correct rank-1 team identity for the canonical accuracy calculation.

---

## 15. No Exact Player-Name Metric

The earlier OCR field evaluator may continue to exist for historical Phase 8 diagnostics.

For v0.12.8 team-identification acceptance:

```text
exact player-name correctness
```

is not a release acceptance threshold.

Player OCR quality is relevant only insofar as it affects correct team identification or safe review behavior.

---

## 16. Genuine Fixture Boundary

No genuine screenshot is committed by v0.12.8.1.

The user-approved genuine scoreboard and roster screenshots remain private/local-only.

This patch contains only reusable production domain logic and synthetic unit tests.

---

## 17. Approved Implementation Boundary

### Existing files to modify

```text
NONE
```

### New production files

```text
app/src/main/java/com/hoggamers/rankforge/domain/matching/ScoreboardRowPlayerEvidenceCollector.kt
app/src/main/java/com/hoggamers/rankforge/domain/matching/ScoreboardTeamIdentificationEvaluator.kt
```

### New test files

```text
app/src/test/java/com/hoggamers/rankforge/domain/matching/ScoreboardRowPlayerEvidenceCollectorTest.kt
app/src/test/java/com/hoggamers/rankforge/domain/matching/ScoreboardTeamIdentificationEvaluatorTest.kt
```

If implementation requires modification of any existing file, implementation must stop and report the requirement before expanding scope.

---

## 18. Required Collector Tests

Synthetic tests must cover at least:

1. multiple rough player strings can be collected for one result row
2. imperfect spelling is preserved rather than corrected
3. blank evidence is ignored
4. numeric-only evidence is not treated as a player
5. fixed 12-row ordering is deterministic
6. left-panel rows map correctly
7. right-panel rows map correctly
8. cropped candidate geometry is handled correctly
9. scaled candidate geometry is handled correctly
10. separate preprocessing candidates remain isolated
11. duplicate raw entities within one candidate are not double-counted
12. no genuine player fixture is required

---

## 19. Required Identification Tests

Synthetic tests must verify:

1. exact evidence identifies the expected team
2. small OCR errors still identify the expected team through existing similarity logic
3. several imperfect names can collectively identify the expected team
4. unrelated names do not produce a false high-confidence team
5. top-three ordering comes from the existing provider
6. confidence classification comes from the existing classifier
7. assignment safety comes from the existing safety evaluator
8. duplicate team safety remains intact
9. low evidence remains review/manual rather than silently confirmed
10. team slot is the authoritative identification output
11. no player-name output is promoted to confirmed match data

---

## 20. Explicit Exclusions

v0.12.8.1 must not modify:

- `PlayerNameParser`
- placement parsing
- kill parsing
- OCR preprocessing
- ML Kit configuration
- text-normalization rules
- Damerau-Levenshtein implementation
- team candidate scoring formula
- confidence thresholds
- assignment-safety rules
- OCR review UI
- navigation
- Room
- Supabase
- cloud synchronization
- screenshot Storage
- scoring
- standings
- correction workflows
- finalized-data protection
- CSV or Google Sheets export
- roster OCR confirmation behavior

It must not commit genuine screenshots or real acceptance data.

---

## 21. Verification

After implementation:

```text
focused collector tests: PASS
focused team-identification evaluator tests: PASS
full JVM suite: PASS
lintDebug: PASS
assembleDebug: PASS
assembleDebugAndroidTest: PASS
git diff --check: PASS
exact four-file implementation boundary: PASS
```

Connected-device testing is not required for this Android-independent domain bridge itself.

Real ML Kit/device acceptance remains v0.12.8 execution after this prerequisite patch.

---

## 22. Relationship to v0.12.8

After v0.12.8.1 is merged, v0.12.8 may add a local-only/instrumented genuine acceptance harness that executes:

```text
approved genuine scoreboard screenshot
-> existing Android bitmap preprocessing
-> existing ML Kit raw OCR
-> v0.12.8.1 row-player evidence collector
-> existing matching algorithms
-> correct already-entered team identity
```

The genuine acceptance harness must not substitute manually typed OCR strings for ML Kit output.

---

## 23. Relationship to Roster OCR Acceptance

The three genuine roster screenshots supplied for this evaluation establish the roster used for scoreboard team matching.

Their own OCR accuracy remains outside v0.12.8.

Real roster screenshot OCR acceptance remains the separate canonical Phase 12 roster OCR acceptance evaluation.

---

## 24. Final Decision

**Approved.**

v0.12.8.1 may implement only the four new files listed above.

The objective is not exact player-name OCR.

The objective is to make rough scoreboard player evidence consumable by the already completed team-identification algorithms so genuine v0.12.8 acceptance can measure the correct entered team.
