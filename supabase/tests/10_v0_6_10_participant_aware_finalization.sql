begin;

select plan(25);

insert into auth.users (id, email)
values
    ('a1000000-0000-0000-0000-000000000001', 'participant-owner@example.test'),
    ('a1000000-0000-0000-0000-000000000002', 'participant-other@example.test');

insert into public.tournaments (id, owner_id, name, revision)
values
    ('a2000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 'Ten Team Cup', 1),
    ('a2000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001', 'Twelve Team Cup', 1),
    ('a2000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000001', 'Gap Cup', 1),
    ('a2000000-0000-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000001', 'Zero Team Cup', 1);

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
select ('a3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
       'a2000000-0000-0000-0000-000000000001'::uuid,
       slot_number,
       case when slot_number <= 10 then 'Ten Team ' || slot_number else '' end
from generate_series(1, 12) as slot_number;

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
select ('a4000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
       'a2000000-0000-0000-0000-000000000002'::uuid,
       slot_number,
       'Twelve Team ' || slot_number
from generate_series(1, 12) as slot_number;

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
select ('a5000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
       'a2000000-0000-0000-0000-000000000003'::uuid,
       slot_number,
       case when slot_number <= 5 or slot_number = 7 then 'Gap Team ' || slot_number else '' end
from generate_series(1, 12) as slot_number;

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
select ('a6000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
       'a2000000-0000-0000-0000-000000000004'::uuid,
       slot_number,
       ''
from generate_series(1, 12) as slot_number;

insert into public.matches (id, tournament_id, match_number, status)
select ('a7000000-0000-0000-0000-' || lpad(match_number::text, 12, '0'))::uuid,
       'a2000000-0000-0000-0000-000000000001'::uuid,
       match_number,
       'draft'
from generate_series(1, 8) as match_number;

insert into public.matches (id, tournament_id, match_number, status)
values
    ('b7000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000002', 1, 'draft'),
    ('c7000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000003', 1, 'draft'),
    ('d7000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000004', 1, 'draft');

set local role authenticated;
set local request.jwt.claim.sub = 'a1000000-0000-0000-0000-000000000001';

select is(
    (select count(*) from public.tournament_team_slots where tournament_id = 'a2000000-0000-0000-0000-000000000001'),
    12::bigint,
    'ten-team tournament retains twelve structural team slots'
);

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('a8000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', ('a7000000-0000-0000-0000-000000000001')::uuid,
        'team_slot_id', ('a3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', slot_number,
        'kills', slot_number - 1,
        'source', 'manual',
        'review_status', 'confirmed'
    )) as value
    from generate_series(1, 10) as slot_number
)
select is((
    select outcome
    from public.finalize_match_snapshot(
        'a2000000-0000-0000-0000-000000000001',
        jsonb_build_object('id', 'a7000000-0000-0000-0000-000000000001'::uuid, 'status', 'finalized'),
        (select value from payload),
        1
    )
), 'success', 'ten-team finalization succeeds');

select is((select status from public.matches where id = 'a7000000-0000-0000-0000-000000000001'), 'finalized', 'ten-team match is finalized');
select is((select count(*) from public.match_results where match_id = 'a7000000-0000-0000-0000-000000000001'), 10::bigint, 'ten-team finalization stores ten results');
select is((
    select array_agg(s.slot_number order by s.slot_number)
    from public.match_results r
    join public.tournament_team_slots s on s.id = r.team_slot_id
    where r.match_id = 'a7000000-0000-0000-0000-000000000001'
), array[1,2,3,4,5,6,7,8,9,10]::integer[], 'ten-team result slots are exactly the active slots');
select is((
    select array_agg(placement order by placement)
    from public.match_results
    where match_id = 'a7000000-0000-0000-0000-000000000001'
), array[1,2,3,4,5,6,7,8,9,10]::integer[], 'ten-team placements are exactly one through ten');
select is((
    select count(*)
    from public.tournament_team_slots
    where tournament_id = 'a2000000-0000-0000-0000-000000000001'
      and slot_number in (11, 12)
      and btrim(coalesce(team_name, '')) = ''
), 2::bigint, 'inactive structural slots remain blank');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('b8000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'b7000000-0000-0000-0000-000000000001'::uuid,
        'team_slot_id', ('a4000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', slot_number,
        'kills', 0,
        'source', 'manual',
        'review_status', 'confirmed'
    )) as value
    from generate_series(1, 12) as slot_number
)
select is((
    select outcome
    from public.finalize_match_snapshot(
        'a2000000-0000-0000-0000-000000000002',
        jsonb_build_object('id', 'b7000000-0000-0000-0000-000000000001'::uuid, 'status', 'finalized'),
        (select value from payload),
        1
    )
), 'success', 'twelve-team finalization remains compatible');
select is((select count(*) from public.match_results where match_id = 'b7000000-0000-0000-0000-000000000001'), 12::bigint, 'twelve-team finalization stores twelve results');
select is((select status from public.matches where id = 'b7000000-0000-0000-0000-000000000001'), 'finalized', 'twelve-team match is finalized');
select is((
    select array_agg(placement order by placement)
    from public.match_results
    where match_id = 'b7000000-0000-0000-0000-000000000001'
), array[1,2,3,4,5,6,7,8,9,10,11,12]::integer[], 'twelve-team placements remain one through twelve');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('a8100000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'a7000000-0000-0000-0000-000000000003'::uuid,
        'team_slot_id', ('a3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', slot_number,
        'kills', 0,
        'source', 'manual',
        'review_status', 'confirmed'
    )) as value
    from generate_series(1, 9) as slot_number
)
select is((select outcome from public.finalize_match_snapshot(
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_object('id', 'a7000000-0000-0000-0000-000000000003'::uuid, 'status', 'finalized'),
    (select value from payload), 2
)), 'validation_failure', 'nine incoming rows are rejected');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('a8200000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'a7000000-0000-0000-0000-000000000004'::uuid,
        'team_slot_id', ('a3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', slot_number,
        'kills', 0,
        'source', 'manual',
        'review_status', 'confirmed'
    )) as value
    from generate_series(1, 11) as slot_number
)
select is((select outcome from public.finalize_match_snapshot(
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_object('id', 'a7000000-0000-0000-0000-000000000004'::uuid, 'status', 'finalized'),
    (select value from payload), 2
)), 'validation_failure', 'eleven incoming rows are rejected');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('a8300000-0000-0000-0000-' || lpad(result_index::text, 12, '0'))::uuid,
        'match_id', 'a7000000-0000-0000-0000-000000000005'::uuid,
        'team_slot_id', ('a3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', result_index,
        'kills', 0,
        'source', 'manual',
        'review_status', 'confirmed'
    )) as value
    from unnest(array[1,2,3,4,5,6,7,8,9,11]) with ordinality as rows(slot_number, result_index)
)
select is((select outcome from public.finalize_match_snapshot(
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_object('id', 'a7000000-0000-0000-0000-000000000005'::uuid, 'status', 'finalized'),
    (select value from payload), 2
)), 'validation_failure', 'inactive slot eleven is rejected');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('a8400000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'a7000000-0000-0000-0000-000000000006'::uuid,
        'team_slot_id', ('a3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', case when slot_number = 10 then 11 else slot_number end,
        'kills', 0,
        'source', 'manual',
        'review_status', 'confirmed'
    )) as value
    from generate_series(1, 10) as slot_number
)
select is((select outcome from public.finalize_match_snapshot(
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_object('id', 'a7000000-0000-0000-0000-000000000006'::uuid, 'status', 'finalized'),
    (select value from payload), 2
)), 'validation_failure', 'placement eleven is rejected for ten participants');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('a8500000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'a7000000-0000-0000-0000-000000000007'::uuid,
        'team_slot_id', ('a3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', case when slot_number = 10 then 9 else slot_number end,
        'kills', 0,
        'source', 'manual',
        'review_status', 'confirmed'
    )) as value
    from generate_series(1, 10) as slot_number
)
select is((select outcome from public.finalize_match_snapshot(
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_object('id', 'a7000000-0000-0000-0000-000000000007'::uuid, 'status', 'finalized'),
    (select value from payload), 2
)), 'validation_failure', 'duplicate placement is rejected');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('a8600000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'a7000000-0000-0000-0000-000000000008'::uuid,
        'team_slot_id', ('a3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', slot_number,
        'kills', case when slot_number = 10 then -1 else 0 end,
        'source', 'manual',
        'review_status', 'confirmed'
    )) as value
    from generate_series(1, 10) as slot_number
)
select is((select outcome from public.finalize_match_snapshot(
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_object('id', 'a7000000-0000-0000-0000-000000000008'::uuid, 'status', 'finalized'),
    (select value from payload), 2
)), 'validation_failure', 'negative kills are rejected');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('a8700000-0000-0000-0000-' || lpad(result_index::text, 12, '0'))::uuid,
        'match_id', 'c7000000-0000-0000-0000-000000000001'::uuid,
        'team_slot_id', ('a5000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', result_index,
        'kills', 0,
        'source', 'manual',
        'review_status', 'confirmed'
    )) as value
    from unnest(array[1,2,3,4,5,7]) with ordinality as rows(slot_number, result_index)
)
select is((select outcome from public.finalize_match_snapshot(
    'a2000000-0000-0000-0000-000000000003',
    jsonb_build_object('id', 'c7000000-0000-0000-0000-000000000001'::uuid, 'status', 'finalized'),
    (select value from payload), 1
)), 'success', 'sparse registered participant slots are accepted');

select is((select outcome from public.finalize_match_snapshot(
    'a2000000-0000-0000-0000-000000000004',
    jsonb_build_object('id', 'd7000000-0000-0000-0000-000000000001'::uuid, 'status', 'finalized'),
    '[]'::jsonb,
    1
)), 'validation_failure', 'zero active participants are rejected');

select is((select outcome from public.finalize_match_snapshot(
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_object('id', 'a7000000-0000-0000-0000-000000000002'::uuid, 'status', 'finalized'),
    '[]'::jsonb,
    1
)), 'stale_write', 'stale revision remains rejected');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 2, 'rejected writes do not advance the tournament revision');

set local request.jwt.claim.sub = 'a1000000-0000-0000-0000-000000000002';
select is((select outcome from public.finalize_match_snapshot(
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_object('id', 'a7000000-0000-0000-0000-000000000002'::uuid, 'status', 'finalized'),
    '[]'::jsonb,
    2
)), 'unauthorized', 'wrong owner remains unauthorized');

set local request.jwt.claim.sub = 'a1000000-0000-0000-0000-000000000001';
select is((select status from public.matches where id = 'a7000000-0000-0000-0000-000000000003'), 'draft', 'rejected finalization leaves the match draft');
select is((select count(*) from public.match_results where match_id = 'a7000000-0000-0000-0000-000000000003'), 0::bigint, 'rejected finalization inserts no partial results');
select is((select count(*) from public.tournament_team_slots where tournament_id = 'a2000000-0000-0000-0000-000000000002'), 12::bigint, 'twelve-team tournament retains all structural slots');

select * from finish();
rollback;
