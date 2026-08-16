begin;

select plan(33);

insert into auth.users (id, email)
values
    ('96000000-0000-0000-0000-000000000001', 'cr004-owner@example.test'),
    ('96000000-0000-0000-0000-000000000002', 'cr004-other@example.test');

insert into public.tournaments (id, owner_id, name, revision)
values (
    '96100000-0000-0000-0000-000000000001',
    '96000000-0000-0000-0000-000000000001',
    'CR004 Finalization Cup',
    1
);

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
select
    ('96200000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
    '96100000-0000-0000-0000-000000000001'::uuid,
    slot_number,
    'Team ' || slot_number
from generate_series(1, 12) as slot_number;

insert into public.players (id, team_slot_id, display_name, normalized_name)
select
    ('96300000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
    ('96200000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
    'Player ' || slot_number,
    'player ' || slot_number
from generate_series(1, 12) as slot_number;

insert into public.matches (id, tournament_id, match_number, status)
values (
    '96400000-0000-0000-0000-000000000001',
    '96100000-0000-0000-0000-000000000001',
    1,
    'draft'
);

create temporary table cr004_valid_payload (payload jsonb not null);
insert into cr004_valid_payload(payload)
select jsonb_agg(
    jsonb_build_object(
        'id', ('96500000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', '96400000-0000-0000-0000-000000000001'::uuid,
        'team_slot_id', ('96200000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'team_slot_number_snapshot', slot_number,
        'team_name_snapshot', 'Team ' || slot_number,
        'placement', slot_number,
        'kills', slot_number - 1,
        'placement_points', case slot_number
            when 1 then 12 when 2 then 9 when 3 then 8 when 4 then 7
            when 5 then 6 when 6 then 5 when 7 then 4 when 8 then 3
            when 9 then 2 when 10 then 1 when 11 then 0 when 12 then 0
        end,
        'kill_points', slot_number - 1,
        'total_points', (case slot_number
            when 1 then 12 when 2 then 9 when 3 then 8 when 4 then 7
            when 5 then 6 when 6 then 5 when 7 then 4 when 8 then 3
            when 9 then 2 when 10 then 1 when 11 then 0 when 12 then 0
        end) + slot_number - 1,
        'source', 'manual',
        'review_status', 'confirmed',
        'players', jsonb_build_array(jsonb_build_object(
            'id', ('96600000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'player_id', ('96300000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'roster_position_snapshot', 1,
            'player_name_snapshot', 'Player ' || slot_number
        ))
    ) order by slot_number
)
from generate_series(1, 12) as slot_number;

set local role authenticated;
set local request.jwt.claim.sub = '96000000-0000-0000-0000-000000000001';

with old_payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('96500000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', '96400000-0000-0000-0000-000000000001'::uuid,
        'team_slot_id', ('96200000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', slot_number,
        'kills', slot_number - 1,
        'source', 'manual',
        'review_status', 'confirmed'
    ) order by slot_number) as payload
    from generate_series(1, 12) as slot_number
)
select is((
    select outcome from public.finalize_match_snapshot(
        '96100000-0000-0000-0000-000000000001',
        jsonb_build_object('id', '96400000-0000-0000-0000-000000000001'::uuid, 'status', 'finalized'),
        (select payload from old_payload),
        1
    )
), 'validation_failure', 'legacy finalization payload without CR-004 snapshot fields is rejected');

select is((
    select outcome from public.finalize_match_snapshot(
        '96100000-0000-0000-0000-000000000001',
        jsonb_build_object('id', '96400000-0000-0000-0000-000000000001'::uuid, 'status', 'finalized'),
        jsonb_set((select payload from cr004_valid_payload), '{0,placement_points}', '99'::jsonb),
        1
    )
), 'validation_failure', 'incorrect placement points are rejected');

select is((
    select outcome from public.finalize_match_snapshot(
        '96100000-0000-0000-0000-000000000001',
        jsonb_build_object('id', '96400000-0000-0000-0000-000000000001'::uuid, 'status', 'finalized'),
        jsonb_set((select payload from cr004_valid_payload), '{0,team_name_snapshot}', '""'::jsonb),
        1
    )
), 'validation_failure', 'blank team name snapshots are rejected');

select is((
    select outcome from public.finalize_match_snapshot(
        '96100000-0000-0000-0000-000000000001',
        jsonb_build_object('id', '96400000-0000-0000-0000-000000000001'::uuid, 'status', 'finalized'),
        jsonb_set((select payload from cr004_valid_payload), '{0,team_slot_number_snapshot}', '2'::jsonb),
        1
    )
), 'validation_failure', 'mismatched or duplicate team slot snapshots are rejected');

select is((
    select outcome from public.finalize_match_snapshot(
        '96100000-0000-0000-0000-000000000001',
        jsonb_build_object('id', '96400000-0000-0000-0000-000000000001'::uuid, 'status', 'finalized'),
        jsonb_set(
            (select payload from cr004_valid_payload),
            '{0,players,0,player_id}',
            to_jsonb('96300000-0000-0000-0000-000000000002'::text)
        ),
        1
    )
), 'validation_failure', 'live player references must belong to the corresponding team slot');

select is((
    select outcome from public.finalize_match_snapshot(
        '96100000-0000-0000-0000-000000000001',
        jsonb_build_object('id', '96400000-0000-0000-0000-000000000001'::uuid, 'status', 'finalized'),
        jsonb_set(
            (select payload from cr004_valid_payload),
            '{0,players}',
            jsonb_build_array(
                jsonb_build_object(
                    'id', '96600000-0000-0000-0000-000000000001'::uuid,
                    'player_id', '96300000-0000-0000-0000-000000000001'::uuid,
                    'roster_position_snapshot', 1,
                    'player_name_snapshot', 'Player 1'
                ),
                jsonb_build_object(
                    'id', '96700000-0000-0000-0000-000000000001'::uuid,
                    'player_id', null,
                    'roster_position_snapshot', 1,
                    'player_name_snapshot', 'Second Historical Player'
                )
            )
        ),
        1
    )
), 'validation_failure', 'duplicate player roster positions are rejected within one result');

select is(
    (select status from public.matches where id = '96400000-0000-0000-0000-000000000001'),
    'draft',
    'failed snapshot validations leave the match draft'
);
select is(
    (select count(*) from public.match_results where match_id = '96400000-0000-0000-0000-000000000001'),
    0::bigint,
    'failed snapshot validations do not write result rows'
);
select is(
    (select count(*) from public.match_result_players),
    0::bigint,
    'failed snapshot validations do not write player snapshot rows'
);

set local role postgres;
update public.tournament_team_slots
set team_name = 'Renamed Team 1'
where id = '96200000-0000-0000-0000-000000000001';
update public.players
set display_name = 'Renamed Player 1', normalized_name = 'renamed player 1'
where id = '96300000-0000-0000-0000-000000000001';

set local role authenticated;
set local request.jwt.claim.sub = '96000000-0000-0000-0000-000000000001';
select is((
    select outcome from public.finalize_match_snapshot(
        '96100000-0000-0000-0000-000000000001',
        jsonb_build_object('id', '96400000-0000-0000-0000-000000000001'::uuid, 'status', 'finalized'),
        (select payload from cr004_valid_payload),
        1
    )
), 'success', 'frozen snapshot finalizes even after live roster names change before a retry');
select is(
    (select revision from public.tournaments where id = '96100000-0000-0000-0000-000000000001'),
    2,
    'successful CR-004 finalization advances the tournament revision'
);
select is(
    (select status from public.matches where id = '96400000-0000-0000-0000-000000000001'),
    'finalized',
    'successful CR-004 finalization marks the match finalized'
);
select is(
    (select count(*) from public.match_results where match_id = '96400000-0000-0000-0000-000000000001'),
    12::bigint,
    'successful CR-004 finalization stores exactly 12 result snapshots'
);
select is(
    (
        select count(*) from public.match_results
        where match_id = '96400000-0000-0000-0000-000000000001'
          and team_slot_number_snapshot is not null
          and team_name_snapshot is not null
          and placement_points is not null
          and kill_points is not null
          and total_points is not null
    ),
    12::bigint,
    'all newly finalized result snapshots contain the five CR-004 fields'
);
select ok(
    (
        select count(distinct team_slot_number_snapshot) = 12
           and min(team_slot_number_snapshot) = 1
           and max(team_slot_number_snapshot) = 12
        from public.match_results
        where match_id = '96400000-0000-0000-0000-000000000001'
    ),
    'stored team slot snapshots are exactly 1 through 12'
);
select is(
    (
        select count(*)
        from public.match_results
        where match_id = '96400000-0000-0000-0000-000000000001'
          and placement_points = case placement
              when 1 then 12 when 2 then 9 when 3 then 8 when 4 then 7
              when 5 then 6 when 6 then 5 when 7 then 4 when 8 then 3
              when 9 then 2 when 10 then 1 when 11 then 0 when 12 then 0
          end
          and kill_points = kills
          and total_points = placement_points + kill_points
    ),
    12::bigint,
    'all stored point snapshots match the approved scoring rules'
);
select is(
    (select team_name_snapshot from public.match_results where id = '96500000-0000-0000-0000-000000000001'),
    'Team 1',
    'finalization stores the frozen team name rather than the later live team name'
);
select is(
    (select count(*) from public.match_result_players),
    12::bigint,
    'successful CR-004 finalization stores every player snapshot from the payload'
);
select is(
    (select player_name_snapshot from public.match_result_players where id = '96600000-0000-0000-0000-000000000001'),
    'Player 1',
    'finalization stores the frozen player name rather than the later live player name'
);
select is(
    (
        select count(*)
        from public.match_result_players as snapshot
        join public.match_results as result_row on result_row.id = snapshot.match_result_id
        join public.players as live_player on live_player.id = snapshot.player_id
        where result_row.match_id = '96400000-0000-0000-0000-000000000001'
          and live_player.team_slot_id = result_row.team_slot_id
          and snapshot.roster_position_snapshot = 1
    ),
    12::bigint,
    'stored live player references remain scoped to the matching team result'
);

select is((
    select outcome from public.finalize_match_snapshot(
        '96100000-0000-0000-0000-000000000001',
        jsonb_build_object('id', '96400000-0000-0000-0000-000000000001'::uuid, 'status', 'finalized'),
        (select payload from cr004_valid_payload),
        2
    )
), 'already_finalized', 'repeating CR-004 finalization is idempotent');
select ok(
    (select count(*) from public.match_results where match_id = '96400000-0000-0000-0000-000000000001') = 12
    and (select count(*) from public.match_result_players) = 12,
    'idempotent finalization does not duplicate result or player snapshots'
);
select is(
    (select count(*) from public.match_result_players),
    12::bigint,
    'owner can select all own player snapshots'
);

set local request.jwt.claim.sub = '96000000-0000-0000-0000-000000000002';
select is(
    (select count(*) from public.match_result_players),
    0::bigint,
    'different user cannot select another owner player snapshots'
);

set local request.jwt.claim.sub = '96000000-0000-0000-0000-000000000001';
create temporary table cr004_corrected_payload (payload jsonb not null);
insert into cr004_corrected_payload(payload)
select jsonb_agg(
    jsonb_build_object(
        'id', ('96500000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', '96400000-0000-0000-0000-000000000001'::uuid,
        'team_slot_id', ('96200000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', case slot_number when 1 then 2 when 2 then 1 else slot_number end,
        'kills', case slot_number when 1 then 5 else slot_number - 1 end,
        'source', 'manual',
        'review_status', 'confirmed'
    ) order by slot_number
)
from generate_series(1, 12) as slot_number;

select is((
    select outcome from public.correct_finalized_match_snapshot(
        '96100000-0000-0000-0000-000000000001',
        '96400000-0000-0000-0000-000000000001',
        (select payload from cr004_corrected_payload),
        2,
        'CR004 scoring correction test'
    )
), 'success', 'protected correction succeeds for a CR-004 finalized match');
select is(
    (select revision from public.tournaments where id = '96100000-0000-0000-0000-000000000001'),
    3,
    'protected correction advances the tournament revision'
);
select ok(
    (
        select placement = 2 and kills = 5
        from public.match_results
        where id = '96500000-0000-0000-0000-000000000001'
    ),
    'protected correction updates the raw placement and kills'
);
select is(
    (select placement from public.match_results where id = '96500000-0000-0000-0000-000000000002'),
    1,
    'protected correction can safely swap placements while preserving unique placement integrity'
);
select ok(
    (
        select placement_points = 9 and kill_points = 5 and total_points = 14
        from public.match_results
        where id = '96500000-0000-0000-0000-000000000001'
    ),
    'protected correction recomputes placement, kill, and total point snapshots atomically'
);
select is(
    (
        select count(*)
        from public.match_results
        where match_id = '96400000-0000-0000-0000-000000000001'
          and team_name_snapshot = 'Team ' || team_slot_number_snapshot
    ),
    12::bigint,
    'protected correction leaves all frozen team identity snapshots unchanged'
);
select is(
    (select count(*) from public.match_result_players),
    12::bigint,
    'protected correction leaves player snapshots unchanged'
);

set local role postgres;
select is(
    (select count(*) from public.match_correction_audit_entries where match_id = '96400000-0000-0000-0000-000000000001'),
    12::bigint,
    'protected correction preserves the existing per-result correction audit behavior'
);

set local role authenticated;
set local request.jwt.claim.sub = '96000000-0000-0000-0000-000000000001';
select is((
    select outcome from public.correct_finalized_match_snapshot(
        '96100000-0000-0000-0000-000000000001',
        '96400000-0000-0000-0000-000000000001',
        (select payload from cr004_corrected_payload),
        3,
        'CR004 scoring correction test'
    )
), 'already_corrected', 'repeating the same protected correction is idempotent');

select * from finish();
rollback;
