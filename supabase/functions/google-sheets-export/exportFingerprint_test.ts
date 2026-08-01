import {
  canonicalizeExportForFingerprint,
  createExportPayloadFingerprint,
} from "../_shared/exportFingerprint.ts";
import {
  type MatchExportRequest,
  parseMatchExportRequest,
} from "../_shared/matchExport.ts";
import {
  parseStandingsExportRequest,
  type StandingsExportRequest,
} from "../_shared/standingsExport.ts";

const TOURNAMENT_ID = "11111111-1111-4111-8111-111111111111";
const MATCH_ID = "22222222-2222-4222-8222-222222222222";

function assert(
  condition: unknown,
  message = "assertion failed",
): asserts condition {
  if (!condition) {
    throw new Error(message);
  }
}

function assertEquals(
  actual: unknown,
  expected: unknown,
  message = "values differ",
): void {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(
      `${message}: ${JSON.stringify(actual)} !== ${JSON.stringify(expected)}`,
    );
  }
}

function makeMatchRequest(): MatchExportRequest {
  return parseMatchExportRequest({
    operation: "export_match",
    tournament_id: TOURNAMENT_ID,
    match_id: MATCH_ID,
    rows: Array.from({ length: 12 }, (_, index) => {
      const placement = index + 1;
      const placementPoints = [12, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 0][index];
      const kills = index;

      return {
        export_schema_version: "phase_10_v1",
        export_type: "match_result",
        tournament_id: TOURNAMENT_ID,
        tournament_name: "Championship Ω",
        match_id: MATCH_ID,
        match_label: "Match 1",
        match_finalized_at: "",
        row_number: placement,
        placement,
        team_slot: placement,
        team_name: `Team ${placement}`,
        player_1_name: `Player ${placement} A`,
        player_2_name: "",
        player_3_name: "",
        player_4_name: "",
        placement_points: placementPoints,
        kills,
        kill_points: kills,
        total_points: placementPoints + kills,
        correction_status: "original_finalized",
      };
    }),
  });
}

function makeStandingsRequest(): StandingsExportRequest {
  return parseStandingsExportRequest({
    operation: "export_standings",
    tournament_id: TOURNAMENT_ID,
    rows: Array.from({ length: 12 }, (_, index) => {
      const rank = index + 1;
      const totalPositionPoints = 20 - index;
      const totalKills = 30 - index;

      return {
        export_schema_version: "phase_10_v1",
        export_type: "tournament_standings",
        tournament_id: TOURNAMENT_ID,
        tournament_name: "Championship Ω",
        exported_match_count: 2,
        standings_rank: rank,
        team_slot: rank,
        team_name: `Team ${rank}`,
        player_1_name: `Player ${rank} A`,
        player_2_name: "",
        player_3_name: "",
        player_4_name: "",
        matches_played: 2,
        total_position_points: totalPositionPoints,
        total_kills: totalKills,
        total_kill_points: totalKills,
        total_points: totalPositionPoints + totalKills,
        best_placement: rank,
        first_place_count: rank === 1 ? 1 : 0,
        tie_break_status: "unique_order",
      };
    }),
  });
}

Deno.test("match fingerprint is stable and lowercase SHA-256", async () => {
  const request = makeMatchRequest();

  const first = await createExportPayloadFingerprint(request);
  const second = await createExportPayloadFingerprint(request);

  assertEquals(first, second);
  assert(/^[0-9a-f]{64}$/.test(first));
});

Deno.test("standings fingerprint is stable and lowercase SHA-256", async () => {
  const request = makeStandingsRequest();

  const first = await createExportPayloadFingerprint(request);
  const second = await createExportPayloadFingerprint(request);

  assertEquals(first, second);
  assert(/^[0-9a-f]{64}$/.test(first));
});

Deno.test("match fingerprint changes when one canonical row value changes", async () => {
  const original = makeMatchRequest();
  const changed = structuredClone(original);
  changed.rows[0].team_name = "Different Team";

  const originalFingerprint = await createExportPayloadFingerprint(original);
  const changedFingerprint = await createExportPayloadFingerprint(changed);

  assert(originalFingerprint !== changedFingerprint);
});

Deno.test("standings fingerprint changes when one canonical total changes", async () => {
  const original = makeStandingsRequest();
  const changed = structuredClone(original);
  changed.rows[0].total_kills += 1;
  changed.rows[0].total_kill_points += 1;
  changed.rows[0].total_points += 1;

  const originalFingerprint = await createExportPayloadFingerprint(original);
  const changedFingerprint = await createExportPayloadFingerprint(changed);

  assert(originalFingerprint !== changedFingerprint);
});

Deno.test("fingerprint preserves exact Unicode and whitespace", async () => {
  const original = makeMatchRequest();
  const changed = structuredClone(original);
  changed.rows[0].player_1_name = `${changed.rows[0].player_1_name} `;

  const originalFingerprint = await createExportPayloadFingerprint(original);
  const changedFingerprint = await createExportPayloadFingerprint(changed);

  assert(originalFingerprint !== changedFingerprint);
});

Deno.test("canonical input does not normalize punctuation", () => {
  const original = makeMatchRequest();
  const changed = structuredClone(original);
  changed.rows[0].team_name = "Team-1";

  assert(
    canonicalizeExportForFingerprint(original) !==
      canonicalizeExportForFingerprint(changed),
  );
});

Deno.test("canonicalization ignores runtime object property insertion order", async () => {
  const original = makeMatchRequest();
  const row = original.rows[0];

  const reorderedRow = {
    correction_status: row.correction_status,
    total_points: row.total_points,
    kill_points: row.kill_points,
    kills: row.kills,
    placement_points: row.placement_points,
    player_4_name: row.player_4_name,
    player_3_name: row.player_3_name,
    player_2_name: row.player_2_name,
    player_1_name: row.player_1_name,
    team_name: row.team_name,
    team_slot: row.team_slot,
    placement: row.placement,
    row_number: row.row_number,
    match_finalized_at: row.match_finalized_at,
    match_label: row.match_label,
    match_id: row.match_id,
    tournament_name: row.tournament_name,
    tournament_id: row.tournament_id,
    export_type: row.export_type,
    export_schema_version: row.export_schema_version,
  };

  const changed = {
    ...original,
    rows: [reorderedRow, ...original.rows.slice(1)],
  } as MatchExportRequest;

  assertEquals(
    await createExportPayloadFingerprint(changed),
    await createExportPayloadFingerprint(original),
  );
});

Deno.test("canonical input contains operation and target identifiers", () => {
  const matchCanonical = canonicalizeExportForFingerprint(makeMatchRequest());
  const standingsCanonical = canonicalizeExportForFingerprint(
    makeStandingsRequest(),
  );

  assert(matchCanonical.includes('"export_match"'));
  assert(matchCanonical.includes(`"${MATCH_ID}"`));
  assert(standingsCanonical.includes('"export_standings"'));
  assert(standingsCanonical.includes(`"${TOURNAMENT_ID}"`));
});

Deno.test("match and standings domains cannot share the same fingerprint", async () => {
  const matchFingerprint = await createExportPayloadFingerprint(
    makeMatchRequest(),
  );
  const standingsFingerprint = await createExportPayloadFingerprint(
    makeStandingsRequest(),
  );

  assert(matchFingerprint !== standingsFingerprint);
});

Deno.test("canonical fingerprint input contains no auth or Google configuration", () => {
  const canonical = canonicalizeExportForFingerprint(makeMatchRequest());

  assert(!canonical.includes("Bearer "));
  assert(!canonical.includes("SUPABASE_ANON_KEY"));
  assert(!canonical.includes("GOOGLE_SERVICE_ACCOUNT"));
  assert(!canonical.includes("spreadsheet"));
});
