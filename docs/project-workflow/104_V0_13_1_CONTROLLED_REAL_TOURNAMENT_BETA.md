# v0.13.1 ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â Controlled Real-Tournament Beta

## Objective

Validate Rank-Forge using genuine tournament rosters, screenshots, match data, corrections, scoring, synchronization, and export under the normal application workflow.

This version is primarily a validation and defect-discovery milestone.

Non-blocking defects should be recorded for v0.13.2 ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â Beta Defect Resolution rather than fixed immediately.

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

- [ ] Tournament created successfully
- [ ] Tournament reopened successfully
- [ ] Correct tournament state restored

### Roster Workflow

- [ ] Genuine roster screenshots selected
- [ ] Manual crop completed
- [ ] Roster OCR completed
- [ ] Team/player parsing reviewed
- [ ] Manual corrections completed where required
- [ ] Final roster data is correct

### Match Result Workflow

For every tested match:

- [ ] MATCH_RESULT_UPPER screenshot selected
- [ ] MATCH_RESULT_LOWER screenshot selected when required
- [ ] Both result crops confirmed
- [ ] OCR preview completed
- [ ] Positions 1ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Å“10 sourced from upper screenshot only
- [ ] Positions 11ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Å“12 sourced from lower screenshot only
- [ ] Player evidence reviewed
- [ ] Kill values reviewed
- [ ] Team suggestions reviewed
- [ ] Unsafe/manual assignments handled correctly
- [ ] Correction draft completed
- [ ] Match finalized successfully

### Scoring

- [x] Placement points correct
- [x] Kill totals correct
- [x] Kill points correct
- [ ] Match totals correct
- [x] Tournament standings correct
- [ ] Tie-break behavior correct where applicable

### Persistence / Recovery

- [ ] Data survives normal screen navigation
- [x] Data survives app restart
- [x] Finalized data remains protected
- [ ] Corrections behave correctly

### Synchronization

- [x] Expected cloud synchronization succeeds
- [ ] Offline/retry behavior remains functional where tested
- [x] No duplicate records created
- [x] Finalized protection preserved

### Export

- [ ] Match CSV export works
- [ ] Tournament CSV export works
- [x] Google Sheets export works where configured
- [ ] Exported values match final tournament data

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
| BETA-001 |  |  |  |  |  | No | v0.13.2 |

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

- Critical ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â data loss, crash, corruption, or workflow cannot continue
- High ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â major result is wrong or important workflow fails
- Medium ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â incorrect behavior with available workaround
- Low ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â cosmetic/usability/minor issue

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
