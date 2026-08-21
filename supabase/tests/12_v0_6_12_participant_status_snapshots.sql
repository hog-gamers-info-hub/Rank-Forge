begin;

select plan(25);

insert into auth.users (id, email)
values
    ('f1000000-0000-0000-0000-000000000001', 'status-owner@example.test'),
    ('f1000000-0000-0000-0000-000000000002', 'status-other@example.test');

insert into public.tournaments (id, owner_id, name, revision)
values
    ('f2000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000001', 'Status Ten', 1),
    ('f2000000-0000-0000-0000-000000000002', 'f1000000-0000-0000-0000-000000000001', 'Status Sparse', 1),
    ('f2000000-0000-0000-0000-000000000003', 'f1000000-0000-0000-0000-000000000001', 'Status Twelve', 1);

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
select ('f3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
       'f2000000-0000-0000-0000-000000000001'::uuid,
       slot_number,
       case when slot_number <= 10 then 'Ten ' || slot_number else '' end
from generate_series(1, 12) as slot_number;

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
select ('f4000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
       'f2000000-0000-0000-0000-000000000002'::uuid,
       slot_number,
       case when slot_number in (1, 3, 6, 8, 11, 12) then 'Sparse ' || slot_number else '' end
from generate_series(1, 12) as slot_number;

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
select ('f5000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
       'f2000000-0000-0000-0000-000000000003'::uuid,
       slot_number,
       'Twelve ' || slot_number
from generate_series(1, 12) as slot_number;

insert into public.matches (id, tournament_id, match_number, status)
values
    ('f7000000-0000-0000-0000-000000000001', 'f2000000-0000-0000-0000-000000000001', 1, 'draft'),
    ('f7000000-0000-0000-0000-000000000002', 'f2000000-0000-0000-0000-000000000002', 1, 'draft'),
    ('f7000000-0000-0000-0000-000000000003', 'f2000000-0000-0000-0000-000000000003', 1, 'draft'),
    ('f7000000-0000-0000-0000-000000000004', 'f2000000-0000-0000-0000-000000000001', 2, 'draft');

set local role authenticated;
set local request.jwt.claim.sub = 'f1000000-0000-0000-0000-000000000001';

select ok(
    exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'match_results'
          and column_name = 'participation_status'
    ),
    'match_results stores explicit participation status'
);

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('f8000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'f7000000-0000-0000-0000-000000000001'::uuid,
        'team_slot_id', ('f3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', case when slot_number <= 8 then slot_number else null end,
        'kills', case when slot_number <= 8 then slot_number else 0 end,
        'source', 'manual', 'review_status', 'confirmed',
        'participation_status', case when slot_number <= 8 then 'PARTICIPATED' else 'NO_SHOW' end
    ) order by slot_number) as value
    from generate_series(1, 10) as slot_number
)
select is((select outcome from public.finalize_match_snapshot(
    'f2000000-0000-0000-0000-000000000001',
    jsonb_build_object('id', 'f7000000-0000-0000-0000-000000000001'::uuid, 'status', 'finalized'),
    (select value from payload), 1
)), 'success', 'ten-team finalization accepts complete participated and no-show rows');

select is((select count(*) from public.match_results where match_id = 'f7000000-0000-0000-0000-000000000001'), 10::bigint, 'no-show rows remain in the finalized snapshot');
select is((select count(*) from public.match_results where match_id = 'f7000000-0000-0000-0000-000000000001' and participation_status = 'NO_SHOW'), 2::bigint, 'ten-team snapshot stores two no-show statuses');
select is((select count(*) from public.match_results where match_id = 'f7000000-0000-0000-0000-000000000001' and participation_status = 'NO_SHOW' and placement is null and kills = 0), 2::bigint, 'no-show rows have nullable placement and zero kills');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('f8100000-0000-0000-0000-' || lpad(result_index::text, 12, '0'))::uuid,
        'match_id', 'f7000000-0000-0000-0000-000000000002'::uuid,
        'team_slot_id', ('f4000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', case when slot_number in (11, 12) then null else result_index end,
        'kills', case when slot_number in (11, 12) then 0 else result_index end,
        'source', 'manual', 'review_status', 'confirmed',
        'participation_status', case when slot_number in (11, 12) then 'NO_SHOW' else 'PARTICIPATED' end
    ) order by result_index) as value
    from unnest(array[1,3,6,8,11,12]) with ordinality as rows(slot_number, result_index)
)
select is((select outcome from public.finalize_match_snapshot(
    'f2000000-0000-0000-0000-000000000002',
    jsonb_build_object('id', 'f7000000-0000-0000-0000-000000000002'::uuid, 'status', 'finalized'),
    (select value from payload), 1
)), 'success', 'sparse registered slots finalize without contiguous-prefix assumptions');

select is((select count(*) from public.match_results where match_id = 'f7000000-0000-0000-0000-000000000002'), 6::bigint, 'sparse finalization stores every registered snapshot row');
select is((select array_agg(slot_number order by slot_number) from public.match_results r join public.tournament_team_slots s on s.id = r.team_slot_id where r.match_id = 'f7000000-0000-0000-0000-000000000002'), array[1,3,6,8,11,12]::integer[], 'sparse snapshot preserves TeamSlot identity');
select is((select count(*) from public.match_results where match_id = 'f7000000-0000-0000-0000-000000000002' and participation_status = 'NO_SHOW'), 2::bigint, 'sparse snapshot stores no-show rows');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('f8200000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'f7000000-0000-0000-0000-000000000003'::uuid,
        'team_slot_id', ('f5000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', slot_number, 'kills', 0,
        'source', 'manual', 'review_status', 'confirmed',
        'participation_status', 'PARTICIPATED'
    ) order by slot_number) as value
    from generate_series(1, 12) as slot_number
)
select is((select outcome from public.finalize_match_snapshot(
    'f2000000-0000-0000-0000-000000000003',
    jsonb_build_object('id', 'f7000000-0000-0000-0000-000000000003'::uuid, 'status', 'finalized'),
    (select value from payload), 1
)), 'success', 'twelve-team participated snapshot remains supported');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('f8300000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'f7000000-0000-0000-0000-000000000004'::uuid,
        'team_slot_id', ('f3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', null, 'kills', 0,
        'source', 'manual', 'review_status', 'confirmed', 'participation_status', 'NO_SHOW'
    ) order by slot_number) as value
    from generate_series(1, 10) as slot_number
)
select is((select outcome from public.finalize_match_snapshot(
    'f2000000-0000-0000-0000-000000000001',
    jsonb_build_object('id', 'f7000000-0000-0000-0000-000000000004'::uuid, 'status', 'finalized'),
    (select value from payload), 2
)), 'validation_failure', 'a snapshot with no participating rows is rejected');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('f8400000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'f7000000-0000-0000-0000-000000000004'::uuid,
        'team_slot_id', ('f3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', case when slot_number = 10 then null else slot_number end,
        'kills', case when slot_number = 10 then 1 else 0 end,
        'source', 'manual', 'review_status', 'confirmed',
        'participation_status', case when slot_number = 10 then 'NO_SHOW' else 'PARTICIPATED' end
    ) order by slot_number) as value
    from generate_series(1, 10) as slot_number
)
select is((select outcome from public.finalize_match_snapshot(
    'f2000000-0000-0000-0000-000000000001',
    jsonb_build_object('id', 'f7000000-0000-0000-0000-000000000004'::uuid, 'status', 'finalized'),
    (select value from payload), 2
)), 'validation_failure', 'no-show rows with nonzero kills are rejected');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', r.id, 'match_id', r.match_id, 'team_slot_id', r.team_slot_id,
        'placement', case when s.slot_number = 9 then 9 else r.placement end,
        'kills', case when s.slot_number = 9 then 2 else r.kills end,
        'source', r.source, 'review_status', r.review_status,
        'participation_status', case when s.slot_number = 9 then 'PARTICIPATED' else r.participation_status end
    ) order by r.team_slot_id) as value
    from public.match_results r
    join public.tournament_team_slots s on s.id = r.team_slot_id
    where r.match_id = 'f7000000-0000-0000-0000-000000000001'
)
select is((select outcome from public.correct_finalized_match_snapshot(
    'f2000000-0000-0000-0000-000000000001',
    'f7000000-0000-0000-0000-000000000001', (select value from payload), 2, 'status transition'
)), 'success', 'NO_SHOW can be corrected to PARTICIPATED');

select is((select participation_status from public.match_results r join public.tournament_team_slots s on s.id = r.team_slot_id where r.match_id = 'f7000000-0000-0000-0000-000000000001' and s.slot_number = 9), 'PARTICIPATED', 'correction updates the participant status');
select is((select placement from public.match_results r join public.tournament_team_slots s on s.id = r.team_slot_id where r.match_id = 'f7000000-0000-0000-0000-000000000001' and s.slot_number = 9), 9, 'correction assigns the transitioned participant a placement');
select is((select count(*) from public.match_correction_audit_entries where match_id = 'f7000000-0000-0000-0000-000000000001' and previous_participation_status = 'NO_SHOW' and corrected_participation_status = 'PARTICIPATED' and previous_placement is null), 1::bigint, 'audit captures nullable previous placement and status transition');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', r.id, 'match_id', r.match_id, 'team_slot_id', r.team_slot_id,
        'placement', case when s.slot_number = 1 then null else r.placement - 1 end,
        'kills', case when s.slot_number = 1 then 0 else r.kills end,
        'source', r.source, 'review_status', r.review_status,
        'participation_status', case when s.slot_number = 1 then 'NO_SHOW' else r.participation_status end
    ) order by r.team_slot_id) as value
    from public.match_results r
    join public.tournament_team_slots s on s.id = r.team_slot_id
    where r.match_id = 'f7000000-0000-0000-0000-000000000001'
)
select is((select outcome from public.correct_finalized_match_snapshot(
    'f2000000-0000-0000-0000-000000000001',
    'f7000000-0000-0000-0000-000000000001', (select value from payload), 3, 'reverse status transition'
)), 'success', 'PARTICIPATED can be corrected to NO_SHOW');

select is((select count(*) from public.match_results where match_id = 'f7000000-0000-0000-0000-000000000001'), 10::bigint, 'correction preserves the complete result row count');
select is((select count(distinct team_slot_id) from public.match_results where match_id = 'f7000000-0000-0000-0000-000000000001'), 10::bigint, 'correction preserves immutable TeamSlot identity');
select is((select count(*) from public.match_correction_audit_entries where match_id = 'f7000000-0000-0000-0000-000000000001' and previous_participation_status = 'PARTICIPATED' and corrected_participation_status = 'NO_SHOW' and corrected_placement is null), 1::bigint, 'audit captures the reverse status transition');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', r.id, 'match_id', r.match_id, 'team_slot_id', r.team_slot_id,
        'placement', r.placement, 'kills', r.kills, 'source', r.source,
        'review_status', r.review_status, 'participation_status', r.participation_status
    ) order by r.team_slot_id) as value
    from public.match_results r where r.match_id = 'f7000000-0000-0000-0000-000000000001'
)
select is((select outcome from public.correct_finalized_match_snapshot(
    'f2000000-0000-0000-0000-000000000001',
    'f7000000-0000-0000-0000-000000000001', (select value from payload), 4, 'no-op'
)), 'already_corrected', 'identical participant correction is a no-op');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', r.id, 'match_id', r.match_id, 'team_slot_id', r.team_slot_id,
        'placement', case when s.slot_number = 1 then 1 else r.placement end,
        'kills', r.kills, 'source', r.source, 'review_status', r.review_status,
        'participation_status', case when s.slot_number = 1 then 'NO_SHOW' else r.participation_status end
    ) order by r.team_slot_id) as value
    from public.match_results r join public.tournament_team_slots s on s.id = r.team_slot_id
    where r.match_id = 'f7000000-0000-0000-0000-000000000001'
)
select is((select outcome from public.correct_finalized_match_snapshot(
    'f2000000-0000-0000-0000-000000000001',
    'f7000000-0000-0000-0000-000000000001', (select value from payload), 4, 'invalid no-show'
)), 'validation_failure', 'no-show placement is rejected');

with numbered as (
    select r.*, row_number() over (order by r.team_slot_id) as row_number
    from public.match_results r where r.match_id = 'f7000000-0000-0000-0000-000000000001'
), payload as (
    select jsonb_agg(jsonb_build_object(
        'id', case when row_number = 1 then 'f9990000-0000-0000-0000-000000000001'::uuid else id end,
        'match_id', match_id, 'team_slot_id', team_slot_id, 'placement', placement,
        'kills', kills, 'source', source, 'review_status', review_status,
        'participation_status', participation_status
    ) order by team_slot_id) as value
    from numbered
)
select is((select outcome from public.correct_finalized_match_snapshot(
    'f2000000-0000-0000-0000-000000000001',
    'f7000000-0000-0000-0000-000000000001', (select value from payload), 4, 'foreign identity'
)), 'validation_failure', 'foreign result identity is rejected');

select is((select outcome from public.correct_finalized_match_snapshot(
    'f2000000-0000-0000-0000-000000000001',
    'f7000000-0000-0000-0000-000000000001', '[]'::jsonb, 3, 'stale'
)), 'stale_write', 'stale correction revision remains protected');
select is((select revision from public.tournaments where id = 'f2000000-0000-0000-0000-000000000001'), 4, 'rejected and no-op corrections do not advance revision');

select * from finish();
rollback;
