# v0.13.1 — Controlled Real-Tournament Beta

## Objective

Validate Rank-Forge using genuine tournament rosters, screenshots, match data, corrections, scoring, synchronization, and export under the normal application workflow.

This version is primarily a validation and defect-discovery milestone.

Non-blocking defects should be recorded for v0.13.2 — Beta Defect Resolution rather than fixed immediately.

---

## Beta Tournament

- Tournament:
- Date:
- Device:
- App branch: beta/v0.13.1-controlled-real-tournament
- App build:
- Number of teams:
- Number of matches tested:

---

## Workflow Checklist

### Tournament Setup

- [x] Tournament created successfully
- [x] Tournament reopened successfully
- [x] Correct tournament state restored

### Roster Workflow

- [x] Genuine roster screenshots selected
- [x] Manual crop completed
- [x] Roster OCR completed
- [x] Team/player parsing reviewed
- [x] Manual corrections completed where required
- [x] Final roster data is correct

### Match Result Workflow

For every tested match:

- [x] MATCH_RESULT_UPPER screenshot selected
- [x] MATCH_RESULT_LOWER screenshot selected when required
- [x] Both result crops confirmed
- [x] OCR preview completed
- [x] Positions 1–10 sourced from upper screenshot only
- [x] Positions 11–12 sourced from lower screenshot only
- [x] Player evidence reviewed
- [x] Kill values reviewed
- [x] Team suggestions reviewed
- [x] Unsafe/manual assignments handled correctly
- [x] Correction draft completed
- [x] Match finalized successfully

### Scoring

- [x] Placement points correct
- [x] Kill totals correct
- [x] Kill points correct
- [x] Match totals correct
- [x] Tournament standings correct
- [ ] Tie-break behavior correct where applicable

### Persistence / Recovery

- [x] Data survives normal screen navigation
- [x] Data survives app restart
- [x] Finalized data remains protected
- [x] Corrections behave correctly

### Synchronization

- [x] Expected cloud synchronization succeeds
- [x] Offline/retry behavior remains functional where tested
- [x] No duplicate records created
- [x] Finalized protection preserved

### Export

- [x] Match CSV export works
- [x] Tournament CSV export works
- [x] Google Sheets export works where configured
- [x] Exported values match final tournament data

### Google Sheets Export Verification

Verified during controlled real-tournament beta:

- Google Cloud project and Google Sheets API configuration confirmed.
- Existing Rank-Forge Google service account reused.
- Local Supabase google-sheets-export Edge Function connection verification succeeded.
- erify_connection returned ok = true and spreadsheet_access = verified.
- Android standings export used the current authenticated Supabase session.
- Physical-device export progressed from Exporting to Google Sheets... to Google Sheets export succeeded.
- Exactly 12 tournament standings rows were appended to the configured Google Sheet.
- No Edge Function runtime error was observed during the successful export.
- Google Sheets export action UI commit: 2dc3a5a.
- Authenticated Android standings export implementation commit: 6c29b8.
---

## Defect Register

| ID | Area | Match / Context | Expected | Actual | Severity | Blocks Beta? | Target |
|---|---|---|---|---|---|---|---|
| BETA-001 | Scoring | Tournament standings tie-break | When total points are equal, the team with higher cumulative placement points must rank above the team with lower placement points | Tie resolution currently prioritizes kill points ahead of cumulative placement points, producing a different standings order than required | High | No | v0.13.2 |

### Defect Areas

Use one of:

- OCR
- Roster OCR
- Team Matching
- Scoring
- Persistence
- Synchronization
- Export
- UI / Usability
- Error Handling
- Crash
- Performance

### Severity

- Critical — data loss, crash, corruption, or workflow cannot continue
- High — major result is wrong or important workflow fails
- Medium — incorrect behavior with available workaround
- Low — cosmetic/usability/minor issue

---

## Performance Observations

Record observations only. Dedicated optimization belongs to v0.13.3.

| Area | Observation |
|---|---|
| Screenshot processing | |
| OCR processing | |
| Roster matching | |
| Database operations | |
| Synchronization | |
| UI responsiveness | |
| Export | |

---

## v0.13.1 Exit Criteria

v0.13.1 may be considered complete when:

- At least one genuine tournament workflow has been exercised.
- Genuine roster screenshots have been processed.
- Genuine match-result screenshots have been processed.
- Complete match review/correction/finalization has been exercised.
- Scoring and standings have been checked.
- Persistence/synchronization have been exercised where applicable.
- Export has been exercised.
- All discovered defects are recorded.
- Any blocker preventing completion of the beta is resolved or explicitly documented.
- Remaining defects are ready to move into v0.13.2.

No Phase 13 closure audit is created at this stage.
