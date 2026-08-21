begin;

select plan(32);

insert into auth.users (id, email)
values
    ('b1000000-0000-0000-0000-000000000001', 'correction-6b-owner@example.test'),
    ('b1000000-0000-0000-0000-000000000002', 'correction-6b-other@example.test');

insert into public.tournaments (id, owner_id, name, revision)
values
    ('b2000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', 'Ten Team Correction Cup', 1),
    ('b2000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000001', 'Twelve Team Correction Cup', 1);

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
select ('b3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
       'b2000000-0000-0000-0000-000000000001'::uuid,
       slot_number,
       case when slot_number <= 10 then 'Ten Team ' || slot_number else '' end
from generate_series(1, 12) as slot_number;

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
select ('b4000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
       'b2000000-0000-0000-0000-000000000002'::uuid,
       slot_number,
       'Twelve Team ' || slot_number
from generate_series(1, 12) as slot_number;

insert into public.matches (id, tournament_id, match_number, status)
values
    ('b7000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0000-000000000001', 1, 'draft'),
    ('b7000000-0000-0000-0000-000000000002', 'b2000000-0000-0000-0000-000000000001', 2, 'finalized'),
    ('b7000000-0000-0000-0000-000000000003', 'b2000000-0000-0000-0000-000000000002', 1, 'finalized'),
    ('b7000000-0000-0000-0000-000000000004', 'b2000000-0000-0000-0000-000000000001', 3, 'draft');

insert into public.match_results (id, match_id, team_slot_id, placement, kills, source, review_status)
select ('b9000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
       'b7000000-0000-0000-0000-000000000002'::uuid,
       ('b3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
       slot_number, slot_number - 1, 'manual', 'confirmed'
from generate_series(1, 10) as slot_number;

insert into public.match_results (id, match_id, team_slot_id, placement, kills, source, review_status)
select ('ba000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
       'b7000000-0000-0000-0000-000000000003'::uuid,
       ('b4000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
       slot_number, slot_number - 1, 'manual', 'confirmed'
from generate_series(1, 12) as slot_number;

set local role authenticated;
set local request.jwt.claim.sub = 'b1000000-0000-0000-0000-000000000001';

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('b8000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'b7000000-0000-0000-0000-000000000001'::uuid,
        'team_slot_id', ('b3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', slot_number,
        'kills', slot_number - 1,
        'source', 'manual',
        'review_status', 'confirmed'
    )) as value from generate_series(1, 10) as slot_number
)
select is((select outcome from public.finalize_match_snapshot(
    'b2000000-0000-0000-0000-000000000001',
    jsonb_build_object('id', 'b7000000-0000-0000-0000-000000000001'::uuid, 'status', 'finalized'),
    (select value from payload), 1
)), 'success', 'a valid ten-team match can be finalized before correction');

select is((select count(*) from public.match_results where match_id = 'b7000000-0000-0000-0000-000000000001'), 10::bigint, 'the finalized ten-team match has ten results');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('b8000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'b7000000-0000-0000-0000-000000000001'::uuid,
        'team_slot_id', ('b3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', case when slot_number = 1 then 2 when slot_number = 2 then 1 else slot_number end,
        'kills', slot_number,
        'source', 'manual',
        'review_status', 'confirmed'
    )) as value from generate_series(1, 10) as slot_number
)
select is((select outcome from public.correct_finalized_match_snapshot(
    'b2000000-0000-0000-0000-000000000001', 'b7000000-0000-0000-0000-000000000001',
    (select value from payload), 2, '10-team correction'
)), 'success', 'ten-team finalized correction succeeds');

select is((select count(*) from public.match_results where match_id = 'b7000000-0000-0000-0000-000000000001'), 10::bigint, 'ten-team correction keeps ten results');
select is((select count(*) from public.match_results where match_id = 'b7000000-0000-0000-0000-000000000001' and id::text like 'b8000000-0000-0000-0000-%'), 10::bigint, 'ten-team correction preserves every result identity');
select is((select placement from public.match_results where id = 'b8000000-0000-0000-0000-000000000001'), 2, 'ten-team correction swaps placement one');
select is((select kills from public.match_results where id = 'b8000000-0000-0000-0000-000000000001'), 1, 'ten-team correction changes kills');
select is((select count(*) from public.match_correction_audit_entries where match_id = 'b7000000-0000-0000-0000-000000000001'), 10::bigint, 'ten-team correction writes one audit row per result');
select is((select correction_reason from public.match_correction_audit_entries where match_id = 'b7000000-0000-0000-0000-000000000001' limit 1), '10-team correction', 'correction reason is preserved');
select is((select previous_revision from public.match_correction_audit_entries where match_id = 'b7000000-0000-0000-0000-000000000001' limit 1), 2, 'audit records the previous tournament revision');
select is((select new_revision from public.match_correction_audit_entries where match_id = 'b7000000-0000-0000-0000-000000000001' limit 1), 3, 'audit records the new tournament revision');
select is((select revision from public.matches where id = 'b7000000-0000-0000-0000-000000000001'), 3, 'successful correction advances match revision');
select is((select revision from public.tournaments where id = 'b2000000-0000-0000-0000-000000000001'), 3, 'finalization and correction advance tournament revision independently');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('b8000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'b7000000-0000-0000-0000-000000000001'::uuid,
        'team_slot_id', ('b3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', case when slot_number = 1 then 2 when slot_number = 2 then 1 else slot_number end,
        'kills', slot_number,
        'source', 'manual',
        'review_status', 'confirmed'
    )) as value from generate_series(1, 10) as slot_number
)
select is((select outcome from public.correct_finalized_match_snapshot(
    'b2000000-0000-0000-0000-000000000001', 'b7000000-0000-0000-0000-000000000001',
    (select value from payload), 3, '10-team correction'
)), 'already_corrected', 'ten-team no-op correction remains idempotent');
select is((select count(*) from public.match_correction_audit_entries where match_id = 'b7000000-0000-0000-0000-000000000001'), 10::bigint, 'ten-team no-op adds no audit rows');
select is((select revision from public.tournaments where id = 'b2000000-0000-0000-0000-000000000001'), 3, 'ten-team no-op does not advance revisions');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('b9000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'b7000000-0000-0000-0000-000000000002'::uuid,
        'team_slot_id', ('b3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', slot_number, 'kills', slot_number - 1,
        'source', 'manual', 'review_status', 'confirmed'
    )) as value from generate_series(1, 9) as slot_number
)
select is((select outcome from public.correct_finalized_match_snapshot(
    'b2000000-0000-0000-0000-000000000001', 'b7000000-0000-0000-0000-000000000002', (select value from payload), 3, null
)), 'validation_failure', 'nine rows are rejected for an existing ten-team match');

with payload as (
    select jsonb_build_object(
        'id', ('b9000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'b7000000-0000-0000-0000-000000000002'::uuid,
        'team_slot_id', ('b3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', slot_number, 'kills', slot_number - 1,
        'source', 'manual', 'review_status', 'confirmed'
    ) as value from generate_series(1, 10) as slot_number
    union all
    select jsonb_build_object(
        'id', 'b9ff0000-0000-0000-0000-000000000001'::uuid,
        'match_id', 'b7000000-0000-0000-0000-000000000002'::uuid,
        'team_slot_id', 'b3000000-0000-0000-0000-000000000010'::uuid,
        'placement', 10, 'kills', 9, 'source', 'manual', 'review_status', 'confirmed'
    ) as value
)
select is((select outcome from public.correct_finalized_match_snapshot(
    'b2000000-0000-0000-0000-000000000001', 'b7000000-0000-0000-0000-000000000002', (select jsonb_agg(value) from payload), 3, null
)), 'validation_failure', 'eleven rows are rejected for an existing ten-team match');

select is((select outcome from public.correct_finalized_match_snapshot(
    'b2000000-0000-0000-0000-000000000001', 'b7000000-0000-0000-0000-000000000002',
    (select jsonb_agg(jsonb_build_object(
        'id', case when slot_number = 10 then 'b9ff0000-0000-0000-0000-000000000001'::uuid else ('b9000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid end,
        'match_id', 'b7000000-0000-0000-0000-000000000002'::uuid,
        'team_slot_id', ('b3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', slot_number, 'kills', slot_number - 1, 'source', 'manual', 'review_status', 'confirmed'
    )) from generate_series(1, 10) as slot_number), 3, null
)), 'validation_failure', 'a foreign result identity is rejected');

select is((select outcome from public.correct_finalized_match_snapshot(
    'b2000000-0000-0000-0000-000000000001', 'b7000000-0000-0000-0000-000000000002',
    (select jsonb_agg(jsonb_build_object(
        'id', ('b9000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'b7000000-0000-0000-0000-000000000002'::uuid,
        'team_slot_id', ('b3000000-0000-0000-0000-' || lpad(case when slot_number = 10 then 9 else slot_number end::text, 12, '0'))::uuid,
        'placement', slot_number, 'kills', slot_number - 1, 'source', 'manual', 'review_status', 'confirmed'
    )) from generate_series(1, 10) as slot_number), 3, null
)), 'validation_failure', 'a result identity paired with the wrong team slot is rejected');

select is((select outcome from public.correct_finalized_match_snapshot(
    'b2000000-0000-0000-0000-000000000001', 'b7000000-0000-0000-0000-000000000002',
    (select jsonb_agg(jsonb_build_object(
        'id', ('b9000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'b7000000-0000-0000-0000-000000000002'::uuid,
        'team_slot_id', ('b3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', case when slot_number = 10 then 11 else slot_number end,
        'kills', slot_number - 1, 'source', 'manual', 'review_status', 'confirmed'
    )) from generate_series(1, 10) as slot_number), 3, null
)), 'validation_failure', 'placement eleven is rejected for ten participants');

select is((select outcome from public.correct_finalized_match_snapshot(
    'b2000000-0000-0000-0000-000000000001', 'b7000000-0000-0000-0000-000000000002',
    (select jsonb_agg(jsonb_build_object(
        'id', ('b9000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'b7000000-0000-0000-0000-000000000002'::uuid,
        'team_slot_id', ('b3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', case when slot_number = 10 then 9 else slot_number end,
        'kills', slot_number - 1, 'source', 'manual', 'review_status', 'confirmed'
    )) from generate_series(1, 10) as slot_number), 3, null
)), 'validation_failure', 'duplicate placement is rejected');

select is((select outcome from public.correct_finalized_match_snapshot(
    'b2000000-0000-0000-0000-000000000001', 'b7000000-0000-0000-0000-000000000002',
    (select jsonb_agg(jsonb_build_object(
        'id', ('b9000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'b7000000-0000-0000-0000-000000000002'::uuid,
        'team_slot_id', ('b3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', slot_number, 'kills', case when slot_number = 10 then -1 else slot_number - 1 end,
        'source', 'manual', 'review_status', 'confirmed'
    )) from generate_series(1, 10) as slot_number), 3, null
)), 'validation_failure', 'negative kills are rejected');

select is((select count(*) from public.match_results where match_id = 'b7000000-0000-0000-0000-000000000002'), 10::bigint, 'invalid ten-team corrections do not change result count');
select is((select placement from public.match_results where id = 'b9000000-0000-0000-0000-000000000001'), 1, 'invalid ten-team corrections do not change result values');
select is((select count(*) from public.match_correction_audit_entries where match_id = 'b7000000-0000-0000-0000-000000000002'), 0::bigint, 'invalid ten-team corrections do not write audit rows');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('ba000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', 'b7000000-0000-0000-0000-000000000003'::uuid,
        'team_slot_id', ('b4000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', case when slot_number = 1 then 2 when slot_number = 2 then 1 else slot_number end,
        'kills', slot_number, 'source', 'manual', 'review_status', 'confirmed'
    )) as value from generate_series(1, 12) as slot_number
)
select is((select outcome from public.correct_finalized_match_snapshot(
    'b2000000-0000-0000-0000-000000000002', 'b7000000-0000-0000-0000-000000000003', (select value from payload), 1, null
)), 'success', 'twelve-team correction remains compatible');
select is((select count(*) from public.match_results where match_id = 'b7000000-0000-0000-0000-000000000003'), 12::bigint, 'twelve-team correction keeps twelve results');
select is((select array_agg(placement order by placement) from public.match_results where match_id = 'b7000000-0000-0000-0000-000000000003'), array[1,2,3,4,5,6,7,8,9,10,11,12]::integer[], 'twelve-team correction retains complete placement coverage');

select is((select outcome from public.correct_finalized_match_snapshot(
    'b2000000-0000-0000-0000-000000000001', 'b7000000-0000-0000-0000-000000000001', '[]'::jsonb, 2, null
)), 'stale_write', 'stale correction remains rejected');

set local request.jwt.claim.sub = 'b1000000-0000-0000-0000-000000000002';
select is((select outcome from public.correct_finalized_match_snapshot(
    'b2000000-0000-0000-0000-000000000001', 'b7000000-0000-0000-0000-000000000001', '[]'::jsonb, 3, null
)), 'unauthorized', 'another owner cannot correct the finalized match');

set local request.jwt.claim.sub = 'b1000000-0000-0000-0000-000000000001';
select is((select outcome from public.correct_finalized_match_snapshot(
    'b2000000-0000-0000-0000-000000000001', 'b7000000-0000-0000-0000-000000000004', '[]'::jsonb, 3, null
)), 'match_not_finalized', 'draft matches cannot be corrected');

select * from finish();
rollback;
