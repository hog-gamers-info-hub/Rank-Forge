# v0.9.0 — Text Normalization Decisions

## 1. Title and Status

**Phase:** 9 — Team Matching and Manual Correction  
**Version:** v0.9.0 — Text Normalization  
**Status:** Approved documentation decision gate; no implementation is authorized by this document alone.

Phase 8, including its roster-OCR extension, is complete and closed. This document defines only the deterministic, comparison-only normalization boundary that a later approved v0.9.0 implementation may add.

## 2. Decision Summary

v0.9.0 will add a pure, deterministic normalizer for OCR-derived player-name candidates and manually maintained roster player display names. It produces a derived comparison value for later Phase 9 similarity matching.

Normalization is not correction and is not confirmation. It must never replace raw OCR text, parsed OCR text, roster display names, or persisted roster data. It must not assign a team, calculate distance or similarity, score candidates, apply thresholds, perform manual correction, modify scoring, or change match finalization.

## 3. Repository Context

The existing `RosterNameNormalizer` in `domain/tournament/RosterValidation.kt` performs only `trim()` and is used by roster validation for team and duplicate-player checks. Changing it would alter established roster-validation behavior.

Phase 8 player-name parsing returns `ParsedPlayerNameRow.detectedName` together with typed status and `PlayerNameOcrEvidence`. Raw extraction preserves source text, hierarchy, geometry, language, and explicit confidence availability through project-owned raw OCR models. These values are in-memory evidence, not confirmed match results.

Tournaments retain exactly 12 fixed team slots, and roster players are structured by tournament and slot. Existing parser tests use synthetic data and focused deterministic unit tests. No existing edit-distance, fuzzy-matching, team-candidate, or match-confidence implementation exists.

## 4. Scope

The approved v0.9.0 scope is a domain-layer comparison normalization capability for player-name strings only. It prepares OCR-derived and roster-player strings for the later v0.9.1 similarity boundary.

The capability may accept a nullable string at its API boundary. It produces either one stable non-blank comparison string or no comparison value. It has no Android, persistence, network, UI, scoring, assignment, or logging side effects.

## 5. Terminology and Data Boundaries

The following values remain conceptually distinct:

1. raw OCR text and hierarchy;
2. parsed OCR player-name candidate;
3. normalized comparison value;
4. later player-match assessment;
5. later team candidate and confidence information;
6. operator correction;
7. confirmed result value; and
8. finalized match result.

The normalizer consumes a string value only. It does not own evidence, field status, or match state, and it must not write its output back to a raw, parsed, roster-display, corrected, or confirmed field.

## 6. Dedicated Normalizer Decision

v0.9.0 will introduce a dedicated Phase 9 normalizer rather than expanding `RosterNameNormalizer`. The existing trim-only normalizer must retain its roster-validation semantics unless a later separately approved version explicitly changes that behavior.

The Phase 9 normalizer may perform equivalent low-level trimming internally, but must not silently alter roster creation, roster editing, duplicate validation, persistence, or display behavior.

The proposed production location is `app/src/main/java/com/hoggamers/rankforge/domain/matching/`, a new Android-independent domain package alongside the existing OCR and tournament domain boundaries.

## 7. Ordered Normalization Pipeline

For a non-null input, the future implementation must apply these steps in exactly this order:

1. **Null boundary:** `null` returns no comparison value immediately.
2. **Unicode canonicalization:** apply NFC using `java.text.Normalizer.normalize(value, Normalizer.Form.NFC)`.
3. **Whitespace canonicalization and trimming:** classify Unicode whitespace using `Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)`, convert each such code point to ASCII space (`U+0020`), then remove leading and trailing ASCII spaces.
4. **Initial whitespace collapse:** replace every remaining run of ASCII spaces with one ASCII space. This step handles only whitespace canonicalized in step 3; it is not the final separator cleanup.
5. **Case normalization:** apply `lowercase(Locale.ROOT)`.
6. **Punctuation-to-separator conversion:** transform every Unicode punctuation code point into one ASCII space. Do not treat the initial whitespace collapse as sufficient after this transformation.
7. **Decorative-symbol handling:** remove Unicode symbol code points, emoji, emoji modifiers, variation selectors, and non-whitespace format/control characters.
8. **OCR-confusion normalization:** replace only the approved comparison characters described in Section 11.
9. **Final separator canonicalization:** after punctuation conversion, symbol removal, and confusion mapping, collapse every repeated ASCII-space comparison separator to one ASCII space and trim ASCII-space comparison separators from both ends.
10. **Blank handling:** if the result is blank after final separator canonicalization, return no comparison value.
11. **Final invariants:** otherwise return the stable non-blank comparison value unchanged.

The final separator canonicalization in step 9 is required because punctuation conversion can create repeated, leading, or trailing separators. It is part of the specified pipeline, not a discretionary cleanup step.

## 8. Unicode and Case Rules

The Unicode normalization form is **NFC**. NFC makes canonically equivalent composed and decomposed forms compare identically while preserving legitimate Unicode letters, digits, and diacritics. v0.9.0 must not use ASCII-only normalization, transliteration, or broad diacritic removal.

The implementation must operate on Unicode code points rather than assume one UTF-16 code unit equals one character. Unsupported or unusual Unicode input must be classified by the deterministic rules in Sections 9 and 10 and must never crash the caller.

Case normalization must use Kotlin/JVM `value.lowercase(java.util.Locale.ROOT)`. It must not use a device-default locale or locale-sensitive overload. This rule applies after NFC and before punctuation, symbol, and OCR-confusion handling.

## 9. Whitespace Rules

Initial Unicode-whitespace canonicalization occurs in steps 3 and 4. Leading and trailing Unicode whitespace is removed, and tabs, newlines, carriage returns, Unicode space separators, and other code points recognized by `Character.isWhitespace` or `Character.isSpaceChar` are all converted to the same ASCII space before that initial trimming and collapse.

Punctuation later becomes the same ASCII-space comparison separator. Therefore, final separator canonicalization occurs only in step 9, after punctuation-to-separator conversion, symbol removal, and OCR-confusion normalization. It collapses every repeated comparison separator, trims comparison separators from both ends, and preserves one internal separator rather than removing all separators. This keeps separate name tokens distinguishable and makes OCR-versus-roster comparison predictable.

## 10. Punctuation and Symbol Rules

The following classification applies after case normalization:

| Category | Treatment |
| --- | --- |
| Spaces and Unicode whitespace | Convert to, and retain only, a single internal ASCII-space separator. |
| Underscores and hyphens | Convert to a separator. |
| Periods, apostrophes, and quotation marks | Convert to a separator. |
| Brackets | Convert to a separator. |
| Slashes and pipes | Convert to a separator. |
| Colons, semicolons, and commas | Convert to a separator. |
| Other Unicode punctuation (`P*`) | Convert to a separator. |
| Decorative Unicode symbols (`S*`) | Remove. |
| Emoji and emoji modifiers | Remove. |
| Unicode variation selectors and non-whitespace format/control characters | Remove. |
| Letters (`L*`), digits (`N*`), and combining marks (`M*`) | Retain, subject only to case and approved OCR-confusion normalization. |

Emoji modifiers include `U+1F3FB` through `U+1F3FF`. Variation selectors include `U+FE00` through `U+FE0F` and `U+E0100` through `U+E01EF`. Whitespace/control code points already handled by Section 9 remain separators; other control or format code points are removed.

This is not an unrestricted non-alphanumeric deletion rule: letters, digits, and combining marks are retained; punctuation becomes a stable separator; and only decorative/symbolic or non-display control content is removed. The original display value is never altered.

## 11. OCR-Confusion Rules

The v0.9.0 comparison-only confusion map is deliberately limited to these groups:

| Observed comparison character after lowercase | Canonical comparison character |
| --- | --- |
| `0` and `o` | `0` |
| `1`, `i`, and `l` | `1` |

Because case normalization already applies `lowercase(Locale.ROOT)`, uppercase `O` and `I` enter this step as `o` and `i`. The mapping preserves original characters in raw OCR and roster display values; it affects only the derived comparison value.

No v0.9.0 mapping for `5/S`, `8/B`, `2/Z`, `6/G`, or any other speculative pair is approved. Additional confusion-aware comparison behavior belongs only to a separately approved v0.9.1 contract.

## 12. Blank and Invalid Input

The API returns `String?`:

| Input condition | Output |
| --- | --- |
| `null` | `null` |
| Empty string | `null` |
| Whitespace-only input | `null` |
| Punctuation-only input | `null` |
| Decorative-symbol-only input | `null` |
| Emoji-only input | `null` |
| Any input that becomes blank during the pipeline | `null` |

`null` means “no usable comparison value,” not a valid normalized player name. Later matching must not treat it as an exact, low-confidence, or assignable match.

## 13. Proposed API Contract

The intended minimal public production API is:

```kotlin
package com.hoggamers.rankforge.domain.matching

object PlayerNameComparisonNormalizer {
    fun normalize(value: String?): String?
}
```

The type is a public Kotlin `object`, the input is nullable `String`, and the nullable return represents blank or unusable comparison output. The API is synchronous, stateless, Android-independent, and suitable for reuse by v0.9.1. It must not expose candidate scores, thresholds, assignments, evidence storage, or UI state.

## 14. Idempotence and Determinism

The implementation must satisfy:

```text
normalize(normalize(value)) == normalize(value)
```

It must produce the same result across repeated calls, device locales, and supported JVM environments. It must not depend on Android framework APIs, persist data, mutate input/evidence models, or log roster or OCR names.

## 15. Synthetic Unit-Test Matrix

Later implementation tests must use only synthetic values and must include at least the following cases:

| Case | Input | Expected output |
| --- | --- | --- |
| Ordinary mixed case | `EchoNova` | `ech0n0va` |
| Leading/trailing spaces | `  EchoNova  ` | `ech0n0va` |
| Repeated spaces | `Echo   Nova` | `ech0 n0va` |
| Tabs and newlines | `Echo\t\nNova` | `ech0 n0va` |
| Punctuation variants | `Echo,Nova;Unit` | `ech0 n0va un1t` |
| Repeated punctuation between words | `Alpha--Beta` | `a1pha beta` |
| Repeated underscore punctuation | `Alpha__Beta` | `a1pha beta` |
| Mixed whitespace and punctuation | `Alpha - Beta` | `a1pha beta` |
| Leading/trailing punctuation | `--Alpha--` | `a1pha` |
| Repeated periods between words | `Alpha...Beta` | `a1pha beta` |
| Underscore and hyphen | `Unit_7-Alpha` | `un1t 7 a1pha` |
| Apostrophe | `Rin'Kai` | `r1n ka1` |
| Decorative symbols | `★Echo☆` | `ech0` |
| Emoji | `🙂Echo🔥` | `ech0` |
| Composed Unicode | `Café` | `café` |
| Decomposed Unicode | `Café` | `café` |
| Locale-sensitive casing | `IOTA` | `10ta` under `Locale.ROOT`, including when the test temporarily uses a non-root default locale |
| `0/O` group | `O0o` | `000` |
| `1/I/l` group | `1Il` | `111` |
| Empty input | `` | `null` |
| Whitespace-only input | ` \t\n ` | `null` |
| Punctuation-only input | `--__...` | `null` |
| Symbol-only input | `★★☆` | `null` |
| Idempotence | `Echo,Nova` | `normalize(normalize(input)) == normalize(input)` |
| Idempotence after separator cleanup | ` --Alpha...Beta__ ` | `normalize(normalize(input)) == normalize(input) == a1pha beta` |
| Raw input preservation | `  Echo-Nova  ` | Assert the original input string remains unchanged after normalization. |

Tests must also assert that null output is not converted to an empty string and that no Android framework dependency is required.

## 16. Compatibility Requirements

v0.9.0 must not break or modify:

* roster creation, editing, or the existing `RosterNameNormalizer` behavior;
* tournament management or fixed 12-team-slot behavior;
* manual match processing, placement validation, or kill validation;
* scoring or standings;
* Room persistence;
* authentication, cloud synchronization, or conflict resolution;
* finalized-data protection;
* screenshot preservation;
* Phase 8 OCR extraction, parsing, raw evidence, or review markers; or
* existing correction workflows.

## 17. Security and Privacy

Normalization is local pure computation. It must not log raw OCR or roster names, upload comparison values, create persistence records, or change screenshot handling. Tests and documentation must use synthetic values only; real player names, screenshots, raw OCR payloads, private paths, and credentials remain prohibited.

## 18. Out of Scope

v0.9.0 excludes:

* Damerau-Levenshtein distance, similarity percentages, and weighted edit operations;
* player matching, team candidate scoring, top-three suggestions, confidence thresholds, assignment rules, and duplicate-team resolution;
* OCR review UI and manual-correction UI;
* Room or Supabase schema changes, OCR-evidence persistence, and finalization changes;
* roster OCR review and correction, fifth/sixth roster-player OCR extraction, and team-name OCR extraction;
* scoring changes, public sharing, and exports.

The roadmap’s separate non-numbered `v0.9.x — Roster OCR Review and Correction` remains distinct future scope and must not be absorbed into v0.9.0.

## 19. Acceptance Criteria

A later v0.9.0 implementation is acceptable only when it:

* follows the exact ordered pipeline in Section 7;
* produces deterministic, locale-independent NFC comparison values;
* is idempotent;
* preserves original OCR and roster display values;
* leaves existing roster-validation behavior unchanged;
* handles blank, symbolic, emoji-only, and unsupported input safely as no comparison value;
* has the complete synthetic unit-test coverage in Section 15;
* remains Android-independent; and
* adds no persistence, UI, matching, scoring, assignment, correction, or finalization behavior.

## 20. Deferred Decisions

The following remain deferred to their owning later versions or separately approved work:

* Damerau-Levenshtein implementation, score conversion, weights, and tie handling;
* player-to-roster matching and one-to-one matching evidence;
* team-candidate score aggregation and ranking;
* top-three suggestion presentation;
* confidence scoring and threshold application;
* assignment conflict resolution and manual-review interaction;
* OCR review-state persistence, raw/corrected data retention, and cloud compatibility;
* safe OCR-assisted finalization; and
* roster OCR review/correction, team-name extraction, fifth/sixth-player support, and cross-team duplicate-player policy.

## 21. Implementation Handoff

After this decision document is reviewed, merged, and followed by explicit user approval, the implementation task may add only the proposed pure `PlayerNameComparisonNormalizer` and its focused synthetic unit tests. It must not modify `RosterNameNormalizer`, existing roster validation, Phase 8 processing, persistence, UI, similarity matching, candidate scoring, thresholds, assignments, corrections, scoring, or finalization.
