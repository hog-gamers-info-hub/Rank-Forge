begin;

select plan(28);

insert into auth.users (id, email)
values
    ('81000000-0000-0000-0000-000000000001', 'rls-owner-a@example.com'),
    ('81000000-0000-0000-0000-000000000002', 'rls-owner-b@example.com');

insert into public.tournaments (id, owner_id, organizer_name, name, status)
values
    ('82000000-0000-0000-0000-000000000001', '81000000-0000-0000-0000-000000000001', 'Owner A', 'Owner A Tournament', 'draft'),
    ('82000000-0000-0000-0000-000000000002', '81000000-0000-0000-0000-000000000002', 'Owner B', 'Owner B Tournament', 'draft'),
    ('82000000-0000-0000-0000-000000000003', '81000000-0000-0000-0000-000000000001', 'Owner A', 'Owner A Second Tournament', 'draft');

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
values
    ('83000000-0000-0000-0000-000000000001', '82000000-0000-0000-0000-000000000001', 1, 'Owner A Team'),
    ('83000000-0000-0000-0000-000000000002', '82000000-0000-0000-0000-000000000002', 1, 'Owner B Team'),
    ('83000000-0000-0000-0000-000000000003', '82000000-0000-0000-0000-000000000003', 1, 'Owner A Second Team');

insert into public.players (id, team_slot_id, display_name, normalized_name)
values
    ('84000000-0000-0000-0000-000000000001', '83000000-0000-0000-0000-000000000001', 'Owner A Player', 'owner a player'),
    ('84000000-0000-0000-0000-000000000002', '83000000-0000-0000-0000-000000000002', 'Owner B Player', 'owner b player');

insert into public.matches (id, tournament_id, match_number, status)
values
    ('85000000-0000-0000-0000-000000000001', '82000000-0000-0000-0000-000000000001', 1, 'draft'),
    ('85000000-0000-0000-0000-000000000002', '82000000-0000-0000-0000-000000000002', 1, 'draft'),
    ('85000000-0000-0000-0000-000000000003', '82000000-0000-0000-0000-000000000003', 1, 'draft');

insert into public.match_results (id, match_id, team_slot_id, placement, kills)
values
    ('86000000-0000-0000-0000-000000000001', '85000000-0000-0000-0000-000000000001', '83000000-0000-0000-0000-000000000001', 1, 0),
    ('86000000-0000-0000-0000-000000000002', '85000000-0000-0000-0000-000000000002', '83000000-0000-0000-0000-000000000002', 1, 0);

set local role authenticated;
set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000001';

select is((select count(*) from public.tournaments where id = '82000000-0000-0000-0000-000000000002'), 0::bigint, 'owner A cannot select owner B tournament');
select is((select count(*) from public.tournament_team_slots where id = '83000000-0000-0000-0000-000000000002'), 0::bigint, 'owner A cannot select owner B team slot');
select is((select count(*) from public.players where id = '84000000-0000-0000-0000-000000000002'), 0::bigint, 'owner A cannot select owner B player');
select is((select count(*) from public.matches where id = '85000000-0000-0000-0000-000000000002'), 0::bigint, 'owner A cannot select owner B match');
select is((select count(*) from public.match_results where id = '86000000-0000-0000-0000-000000000002'), 0::bigint, 'owner A cannot select owner B match result');

with attempted as (
    update public.tournaments set organizer_name = 'Denied' where id = '82000000-0000-0000-0000-000000000002' returning 1
)
select is((select count(*) from attempted), 0::bigint, 'owner A cannot update owner B tournament through USING');

with attempted as (
    update public.tournament_team_slots set team_name = 'Denied Team' where id = '83000000-0000-0000-0000-000000000002' returning 1
)
select is((select count(*) from attempted), 0::bigint, 'owner A cannot update owner B team slot through USING');

with attempted as (
    update public.players set display_name = 'Denied Player' where id = '84000000-0000-0000-0000-000000000002' returning 1
)
select is((select count(*) from attempted), 0::bigint, 'owner A cannot update owner B player through USING');

with attempted as (
    update public.matches set status = 'finalized' where id = '85000000-0000-0000-0000-000000000002' returning 1
)
select is((select count(*) from attempted), 0::bigint, 'owner A cannot update owner B match through USING');

with attempted as (
    update public.match_results set kills = 1 where id = '86000000-0000-0000-0000-000000000002' returning 1
)
select is((select count(*) from attempted), 0::bigint, 'owner A cannot update owner B match result through USING');

with attempted as (
    delete from public.tournaments where id = '82000000-0000-0000-0000-000000000002' returning 1
)
select is((select count(*) from attempted), 0::bigint, 'owner A cannot delete owner B tournament');

with attempted as (
    delete from public.tournament_team_slots where id = '83000000-0000-0000-0000-000000000002' returning 1
)
select is((select count(*) from attempted), 0::bigint, 'owner A cannot delete owner B team slot');

with attempted as (
    delete from public.players where id = '84000000-0000-0000-0000-000000000002' returning 1
)
select is((select count(*) from attempted), 0::bigint, 'owner A cannot delete owner B player');

with attempted as (
    delete from public.matches where id = '85000000-0000-0000-0000-000000000002' returning 1
)
select is((select count(*) from attempted), 0::bigint, 'owner A cannot delete owner B match');

with attempted as (
    delete from public.match_results where id = '86000000-0000-0000-0000-000000000002' returning 1
)
select is((select count(*) from attempted), 0::bigint, 'owner A cannot delete owner B match result');

select throws_ok(
    $$insert into public.tournament_team_slots (tournament_id, slot_number, team_name) values ('82000000-0000-0000-0000-000000000002', 2, 'Forged Owner B Team')$$,
    '42501',
    null,
    'owner A cannot insert a team slot using owner B tournament UUID'
);

select throws_ok(
    $$insert into public.players (team_slot_id, display_name, normalized_name) values ('83000000-0000-0000-0000-000000000002', 'Forged Owner B Player', 'forged owner b player')$$,
    '42501',
    null,
    'owner A cannot insert a player using owner B team slot UUID'
);

select throws_ok(
    $$insert into public.matches (tournament_id, match_number, status) values ('82000000-0000-0000-0000-000000000002', 2, 'draft')$$,
    '42501',
    null,
    'owner A cannot insert a match using owner B tournament UUID'
);

select throws_ok(
    $$insert into public.match_results (match_id, team_slot_id, placement, kills) values ('85000000-0000-0000-0000-000000000002', '83000000-0000-0000-0000-000000000002', 2, 0)$$,
    '42501',
    null,
    'owner A cannot insert a match result using owner B parent UUIDs'
);

select throws_ok(
    $$update public.tournaments set owner_id = '81000000-0000-0000-0000-000000000002' where id = '82000000-0000-0000-0000-000000000001'$$,
    '42501',
    null,
    'owner A cannot transfer its tournament to owner B through WITH CHECK'
);

set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000002';

with attempted as (
    update public.tournaments set owner_id = '81000000-0000-0000-0000-000000000002' where id = '82000000-0000-0000-0000-000000000001' returning 1
)
select is((select count(*) from attempted), 0::bigint, 'owner B cannot update owner A tournament ownership through USING');

set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000001';

select throws_ok(
    $$update public.tournament_team_slots set tournament_id = '82000000-0000-0000-0000-000000000002' where id = '83000000-0000-0000-0000-000000000001'$$,
    '42501',
    null,
    'owner A cannot reparent its team slot to owner B tournament through WITH CHECK'
);

select throws_ok(
    $$update public.players set team_slot_id = '83000000-0000-0000-0000-000000000002' where id = '84000000-0000-0000-0000-000000000001'$$,
    '42501',
    null,
    'owner A cannot reparent its player to owner B team slot through WITH CHECK'
);

select throws_ok(
    $$update public.matches set tournament_id = '82000000-0000-0000-0000-000000000002' where id = '85000000-0000-0000-0000-000000000001'$$,
    '42501',
    null,
    'owner A cannot reparent its match to owner B tournament through WITH CHECK'
);

select throws_ok(
    $$update public.match_results set match_id = '85000000-0000-0000-0000-000000000002', team_slot_id = '83000000-0000-0000-0000-000000000002' where id = '86000000-0000-0000-0000-000000000001'$$,
    '42501',
    null,
    'owner A cannot reparent its match result to owner B parents through WITH CHECK'
);

select throws_ok(
    $$insert into public.match_results (match_id, team_slot_id, placement, kills) values ('85000000-0000-0000-0000-000000000001', '83000000-0000-0000-0000-000000000002', 2, 0)$$,
    '42501',
    null,
    'owner A cannot mix its match with owner B team slot'
);

select throws_ok(
    $$insert into public.match_results (match_id, team_slot_id, placement, kills) values ('85000000-0000-0000-0000-000000000002', '83000000-0000-0000-0000-000000000001', 2, 0)$$,
    '42501',
    null,
    'owner A cannot mix owner B match with its team slot'
);

select throws_ok(
    $$insert into public.match_results (match_id, team_slot_id, placement, kills) values ('85000000-0000-0000-0000-000000000001', '83000000-0000-0000-0000-000000000003', 2, 0)$$,
    '42501',
    null,
    'owner A cannot mix match result parents from different owned tournaments'
);

reset role;

select * from finish();

rollback;
