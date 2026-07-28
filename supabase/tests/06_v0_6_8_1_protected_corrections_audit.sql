begin;

select plan(9);

insert into auth.users (id, email)
values ('81000000-0000-0000-0000-000000000001', 'correction-owner@example.test');
insert into public.tournaments (id, owner_id, name, revision)
values ('82000000-0000-0000-0000-000000000001', '81000000-0000-0000-0000-000000000001', 'Correction Cup', 1);
insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
select ('83000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
       '82000000-0000-0000-0000-000000000001'::uuid, slot_number, 'Team ' || slot_number
from generate_series(1, 12) as slot_number;
insert into public.matches (id, tournament_id, match_number, status)
values ('84000000-0000-0000-0000-000000000001', '82000000-0000-0000-0000-000000000001', 1, 'finalized');
insert into public.match_results (id, match_id, team_slot_id, placement, kills, review_status)
select ('85000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
       '84000000-0000-0000-0000-000000000001'::uuid,
       ('83000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
       slot_number, slot_number - 1, 'confirmed'
from generate_series(1, 12) as slot_number;

set local role authenticated;
set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000001';

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('85000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', '84000000-0000-0000-0000-000000000001'::uuid,
        'team_slot_id', ('83000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', case when slot_number = 1 then 2 when slot_number = 2 then 1 else slot_number end,
        'kills', slot_number - 1, 'source', 'manual', 'review_status', 'confirmed'
    )) as value from generate_series(1, 12) as slot_number
)
select is((select outcome from public.correct_finalized_match_snapshot(
    '82000000-0000-0000-0000-000000000001', '84000000-0000-0000-0000-000000000001',
    (select value from payload), 1, null
)), 'success', 'protected correction changes a finalized match through the RPC');

select is((select revision from public.tournaments where id = '82000000-0000-0000-0000-000000000001'), 2, 'successful correction advances tournament revision once');
select is((select count(*) from public.match_correction_audit_entries where match_id = '84000000-0000-0000-0000-000000000001'), 12::bigint, 'one immutable audit row is retained per corrected result');
select is((select previous_placement from public.match_correction_audit_entries where team_slot_id = '83000000-0000-0000-0000-000000000001'), 1, 'audit retains the prior placement');
select is((select corrected_placement from public.match_correction_audit_entries where team_slot_id = '83000000-0000-0000-0000-000000000001'), 2, 'audit retains the corrected placement');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('85000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', '84000000-0000-0000-0000-000000000001'::uuid,
        'team_slot_id', ('83000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'placement', case when slot_number = 1 then 2 when slot_number = 2 then 1 else slot_number end,
        'kills', slot_number - 1, 'source', 'manual', 'review_status', 'confirmed'
    )) as value from generate_series(1, 12) as slot_number
)
select is((select outcome from public.correct_finalized_match_snapshot(
    '82000000-0000-0000-0000-000000000001', '84000000-0000-0000-0000-000000000001',
    (select value from payload), 2, null
)), 'already_corrected', 'repeating an identical correction is idempotent');
select is((select count(*) from public.match_correction_audit_entries where match_id = '84000000-0000-0000-0000-000000000001'), 12::bigint, 'idempotent correction does not duplicate audit history');

select is((select outcome from public.correct_finalized_match_snapshot(
    '82000000-0000-0000-0000-000000000001', '84000000-0000-0000-0000-000000000001',
    '[]'::jsonb, 1, null
)), 'stale_write', 'stale correction is rejected before it can overwrite finalized data');

insert into public.matches (id, tournament_id, match_number, status)
values ('84000000-0000-0000-0000-000000000002', '82000000-0000-0000-0000-000000000001', 2, 'draft');
select is((select outcome from public.correct_finalized_match_snapshot(
    '82000000-0000-0000-0000-000000000001', '84000000-0000-0000-0000-000000000002',
    '[]'::jsonb, 2, null
)), 'match_not_finalized', 'draft matches cannot be corrected through the protected RPC');

select * from finish();
rollback;
