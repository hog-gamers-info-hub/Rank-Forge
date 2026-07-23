# Testing and Acceptance Plan

## 1. Purpose

This document defines the testing strategy, test evidence, acceptance criteria, and release gates for Rank-Forge.

The objective is to verify that tournament results are processed accurately, consistently, and safely before any production release.

## 2. Current Testing Status

### Available

- Synthetic roster data for all 12 team slots
- Four to six players assigned to each team
- Defined scoring rules
- Defined confidence thresholds
- Manual correction requirements
- Local development environment

### Deferred

The following tests require real Free Fire MAX screenshots and cannot be completed yet:

- OCR extraction accuracy
- Fixed-layout scoreboard cropping
- Player-name recognition from screenshots
- Kill-value recognition from screenshots
- Team matching accuracy against real OCR output
- Screenshot quality and compression testing

Fake scoreboard screenshots must not be used as evidence for OCR acceptance.

## 3. Test Data

### Committed Test Data

Synthetic roster fixture:

`test-data/rosters/teams.csv`

The fixture must contain:

- Exactly 12 team slots
- One unique team name per slot
- Four to six player names per team
- No duplicate player names within the same team
- OCR-challenging names for normalization and fuzzy-matching tests

### Real Screenshot Test Data

Real screenshots will be added later after approved samples are available.

Required future samples:

- Clear screenshots
- Compressed screenshots
- Screenshots containing similar player names
- Screenshots containing symbols and mixed characters
- Screenshots with low but usable image quality
- Screenshots representing all 12 placements

Private or personally sensitive screenshots must not be committed to the public repository.

Each real screenshot must have a manually verified expected-result file before being used as test evidence.

## 4. Test Levels

### 4.1 Unit Tests

Unit tests must cover:

- Position-point calculation
- Kill-point calculation
- Match-total calculation
- Tournament-total calculation
- Tie-break ordering
- Player-name normalization
- Team-name normalization
- OCR-character confusion handling
- Damerau-Levenshtein distance
- Confidence-score calculation
- Duplicate assignment prevention
- Match-finalization validation

### 4.2 Integration Tests

Integration tests must cover:

- Roster import and validation
- Match result persistence
- Room database reads and writes
- Supabase synchronization
- Offline queue behavior
- Duplicate match detection
- Manual correction persistence
- Finalized result retrieval
- CSV export
- Google Sheets export through the approved backend flow

### 4.3 UI Tests

UI tests must cover:

- Creating or importing a 12-team roster
- Selecting a scoreboard screenshot
- Reviewing extracted match results
- Editing player names
- Editing kill values
- Editing placement
- Selecting a suggested team match
- Resolving duplicate team assignments
- Blocking incomplete finalization
- Finalizing a valid match
- Viewing tournament standings
- Exporting finalized results

### 4.4 Device Tests

Testing must include:

- Android API 26 minimum-supported device or emulator
- Current target Android API device or emulator
- At least one physical Android device
- Portrait orientation
- Offline mode
- Interrupted upload or synchronization
- App restart during an unfinished match
- Low-memory recovery where practical

## 5. Scoring Tests

The scoring engine must use:

| Position | Points |
|---|---:|
| 1 | 12 |
| 2 | 9 |
| 3 | 8 |
| 4 | 7 |
| 5 | 6 |
| 6 | 5 |
| 7 | 4 |
| 8 | 3 |
| 9 | 2 |
| 10 | 1 |
| 11 | 0 |
| 12 | 0 |

Each kill equals one point.

Match total:

`position points + kill points`

Tournament tie-break order:

1. Total points
2. Number of first-place finishes
3. Total kills
4. Placement in the latest match

Scoring tests must include:

- Every placement from 1 through 12
- Zero kills
- High kill totals
- Equal total points
- Equal first-place counts
- Equal total kills
- Latest-match placement tie-break
- Multiple matches for the same tournament

## 6. Team-Matching Tests

Team-matching tests must include:

- Exact player-name matches
- Case differences
- Extra whitespace
- Symbols and punctuation
- Zero and letter O confusion
- One and letter I confusion
- Similar player names across teams
- Missing player names
- Incorrect OCR characters
- Duplicate candidate teams
- Unmatched teams

Automatic team assignment is allowed only when:

- At least three of four detected players match the same team
- The confidence threshold is at least 90
- The leading team has at least a 10-point advantage over the second candidate
- The team has not already been assigned in the same match

Confidence behavior:

- 90–100: automatic assignment
- 75–89: suggestion requiring confirmation
- Below 75: manual or unmatched

## 7. Manual Correction Tests

The review screen must allow the user to:

- Review all 12 placements
- Correct player names
- Correct kill values
- Correct placement
- Select from the top three team suggestions
- manually select an unmatched team
- Resolve duplicate team assignments
- Confirm uncertain results

Finalization must be blocked when:

- A placement is missing
- A team is assigned more than once
- Required values are invalid
- A low-confidence result has not been confirmed
- A match contains unresolved errors

The system must never silently confirm uncertain OCR results.

## 8. Duplicate and Error Tests

Tests must cover:

- Duplicate screenshot hash
- Duplicate match number
- Duplicate team assignment
- Missing teams
- Missing players
- Invalid placement
- Negative kills
- Non-numeric kills
- More than 12 result rows
- Fewer than 12 finalized result rows
- Failed local save
- Failed Supabase synchronization
- Failed CSV export
- Failed Google Sheets export

The original OCR output must remain available for review after corrections.

## 9. OCR Acceptance Tests

OCR acceptance testing is deferred until real screenshots are available.

When testing begins:

- Every screenshot must have manually verified ground truth
- OCR output must be compared field by field
- Player names, kills, placements, and team assignments must be measured separately
- Corrections must be recorded
- Screenshot quality must be documented
- Test results must be reproducible

Target team-identification accuracy:

- At least 95 percent on the approved real screenshot test set

Scoring accuracy after user confirmation:

- 100 percent

## 10. Persistence and Recovery Tests

Tests must verify:

- Draft match data survives app restart
- Finalized matches survive app restart
- Offline changes remain queued
- Queued changes synchronize when connectivity returns
- Duplicate synchronization is prevented
- Failed synchronization can be retried
- Finalized data is not silently overwritten
- Original screenshot references are preserved where permitted

## 11. Export Tests

Only finalized results may be exported.

CSV and Google Sheets exports must verify:

- Correct tournament
- Correct match number
- All 12 teams
- Correct placements
- Correct kill points
- Correct position points
- Correct match totals
- Correct cumulative totals
- Correct tie-break ordering
- No duplicate rows
- Stable column order
- UTF-8 character preservation

## 12. Security Tests

Tests must verify:

- Users can access only authorized tournaments
- Supabase Row Level Security is enabled
- The Android app contains no service-role key
- Google credentials are not stored in the app
- Exports use the approved backend flow
- Unauthorized database writes are rejected
- Sensitive files are excluded from Git

## 13. Acceptance Criteria

A release is acceptable only when:

- Exactly 12 team slots are supported
- Up to 10 matches can be processed
- All scoring tests pass
- Tie-break tests pass
- Manual correction works
- Invalid results cannot be finalized
- Finalized data persists correctly
- CSV export passes
- Google Sheets export passes when implemented
- Required unit, integration, and UI tests pass
- No critical or high-severity defect remains open
- OCR acceptance passes after real screenshots become available

## 14. Release Evidence

Each release must record:

- Git commit or release tag
- Test environment
- Device or emulator details
- Commands executed
- Test results
- Failed-test details
- Screenshots or logs where relevant
- Known limitations
- Reviewer approval

## 15. Defect Handling

Defects must be classified as:

- Critical: data loss, security failure, or incorrect finalized tournament result
- High: core workflow blocked or materially incorrect
- Medium: important feature partially affected
- Low: cosmetic or minor usability issue

Critical and high defects block release.

Every defect fix must include a regression test where practical.
