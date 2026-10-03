begin;

select plan(12);

select has_column('public', 'matches', 'group_pairing_key', 'matches persist their selected pairing');
select ok(exists (
    select 1
    from pg_constraint
    where conrelid = 'public.matches'::regclass
      and conname = 'matches_group_pairing_fkey'
), 'match pairing references a selected tournament pairing');
select ok(exists (
    select 1
    from pg_trigger
    where tgrelid = 'public.matches'::regclass
      and tgname = 'matches_group_pairing_guard'
), 'match pairing guard trigger exists');
select ok(to_regprocedure('public.write_match_snapshot(uuid,jsonb,jsonb,integer)') is not null, 'draft snapshot RPC keeps its signature');
select ok(to_regprocedure('public.finalize_match_snapshot(uuid,jsonb,jsonb,integer)') is not null, 'finalization RPC keeps its signature');

insert into auth.users (id, email)
values ('b1000000-0000-0000-0000-000000000001', 'batch2-owner@example.test');
insert into public.tournaments (id, owner_id, name, format, group_count)
values ('b2000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', 'Batch 2 Rotation', 'group_rotation', 3);
insert into public.tournaments (id, owner_id, name)
values ('b2000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000001', 'Batch 2 Standard');
insert into public.tournament_group_pairings (tournament_id, first_group, second_group)
values ('b2000000-0000-0000-0000-000000000001', 'A', 'B');

set local role authenticated;
set local request.jwt.claim.sub = 'b1000000-0000-0000-0000-000000000001';

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name, "group")
select
    ('b3000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
    'b2000000-0000-0000-0000-000000000001',
    slot_number,
    'Team ' || slot_number,
    case when slot_number <= 6 then 'A' else 'B' end
from generate_series(1, 12) as slot_number;

insert into public.matches (id, tournament_id, match_number, status)
values ('b4000000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0000-000000000002', 1, 'draft');
select is((select group_pairing_key from public.matches where id = 'b4000000-0000-0000-0000-000000000001'), null, 'standard-style null pairing remains nullable for legacy rows');

select throws_ok($$
    insert into public.matches (id, tournament_id, match_number, status, group_pairing_key)
    values ('b4000000-0000-0000-0000-000000000002', 'b2000000-0000-0000-0000-000000000001', 2, 'draft', 'B:C')
$$, '23503', null, 'a match cannot use an unselected pairing');

insert into public.matches (id, tournament_id, match_number, status, group_pairing_key)
values ('b4000000-0000-0000-0000-000000000003', 'b2000000-0000-0000-0000-000000000001', 2, 'draft', 'A:B');
select is((select group_pairing_key from public.matches where id = 'b4000000-0000-0000-0000-000000000003'), 'A:B', 'selected pairing is persisted on a group match');

select throws_ok($$
    update public.matches
    set group_pairing_key = null
    where id = 'b4000000-0000-0000-0000-000000000003'
$$, 'P0001', null, 'group pairing cannot be cleared');
select throws_ok($$
    update public.matches
    set group_pairing_key = 'A:B'
    where id = 'b4000000-0000-0000-0000-000000000001'
$$, 'P0001', null, 'standard legacy match cannot gain a pairing');

select ok(position('group_pairing_key' in pg_get_functiondef('public.write_match_snapshot(uuid,jsonb,jsonb,integer)'::regprocedure)) > 0, 'draft RPC carries pairing metadata');
select ok(position('v_group_pairing_key' in pg_get_functiondef('public.finalize_match_snapshot(uuid,jsonb,jsonb,integer)'::regprocedure)) > 0, 'finalization RPC resolves pairing eligibility');

select * from finish();
rollback;
