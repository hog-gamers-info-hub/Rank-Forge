# Rank-Forge Coding and Naming Rules

## 1. Naming Rules

### Kotlin

* Classes, interfaces, objects and files must use `PascalCase`.
* Functions and variables must use `camelCase`.
* Constants must use `UPPER_SNAKE_CASE`.
* Package names must use lowercase letters only.
* Names must describe their actual responsibility.

Examples:

```text
TournamentRepository.kt
ScoreCalculationService.kt
calculateMatchPoints()
selectedTournamentId
MAX_TEAM_COUNT
com.rankforge.app
```

### Android Resources

Android resource names must use `snake_case`.

Examples:

```text
screen_tournament_results
ic_upload_scoreboard
color_primary_background
string_tournament_name
```

### Supabase

* Tables and columns must use `snake_case`.
* Table names should normally be plural.
* Primary keys should use `id`.
* Foreign keys should use `<entity>_id`.
* Boolean columns should use clear prefixes such as `is_`, `has_` or `can_`.

Examples:

```text
tournaments
tournament_teams
team_players
tournament_id
is_finalized
```

### Documentation

Documentation filenames must use uppercase words separated by underscores.

Examples:

```text
OCR_PROCESSING_RULES.md
DATABASE_DESIGN.md
TESTING_AND_ACCEPTANCE.md
```

### General Restrictions

* Do not use unclear abbreviations.
* Do not create duplicate names for the same concept.
* Do not rename working public APIs, database columns or files without approval.
* Do not introduce unrelated naming refactors during active feature development.
