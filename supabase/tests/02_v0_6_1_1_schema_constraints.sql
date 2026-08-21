begin;

select plan(29);

insert into auth.users (id, email)
values ('10000000-0000-0000-0000-000000000001', 'schema-owner@example.test');

insert into public.tournaments (id, owner_id, name)
values ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Schema test tournament');
insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
values ('30000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 1, 'Alpha');
insert into public.players (id, team_slot_id, display_name, normalized_name)
values ('40000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', 'Player One', 'player one');
insert into public.matches (id, tournament_id, match_number)
values ('50000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001', 1);
insert into public.match_results (id, match_id, team_slot_id, placement)
values ('60000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', 1);
insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
values ('30000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', 2, 'Bravo');

select is((select revision from public.tournaments where id = '20000000-0000-0000-0000-000000000001'), 1, 'tournaments revision defaults to 1');
select is((select revision from public.tournament_team_slots where id = '30000000-0000-0000-0000-000000000001'), 1, 'tournament_team_slots revision defaults to 1');
select is((select revision from public.players where id = '40000000-0000-0000-0000-000000000001'), 1, 'players revision defaults to 1');
select is((select revision from public.matches where id = '50000000-0000-0000-0000-000000000001'), 1, 'matches revision defaults to 1');
select is((select revision from public.match_results where id = '60000000-0000-0000-0000-000000000001'), 1, 'match_results revision defaults to 1');

select throws_ok($$
    insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
    values ('30000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000001', 1, 'Charlie')
$$, '23505', null, 'duplicate team-slot number is rejected');
select throws_ok($$
    insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
    values ('30000000-0000-0000-0000-000000000004', '20000000-0000-0000-0000-000000000001', 3, 'Alpha')
$$, '23505', null, 'duplicate team name is rejected');
select throws_ok($$
    insert into public.players (id, team_slot_id, display_name, normalized_name)
    values ('40000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', 'Player One Duplicate', 'player one')
$$, '23505', null, 'duplicate normalized player name is rejected');
select throws_ok($$
    insert into public.matches (id, tournament_id, match_number)
    values ('50000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', 1)
$$, '23505', null, 'duplicate match number is rejected');
select throws_ok($$
    insert into public.match_results (id, match_id, team_slot_id, placement)
    values ('60000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', 2)
$$, '23505', null, 'duplicate result team is rejected');
select throws_ok($$
    insert into public.match_results (id, match_id, team_slot_id, placement)
    values ('60000000-0000-0000-0000-000000000003', '50000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000002', 1)
$$, '23505', null, 'duplicate result placement is rejected');

select throws_ok($$
    update public.tournaments set status = 'invalid' where id = '20000000-0000-0000-0000-000000000001'
$$, '23514', null, 'invalid tournament status is rejected');
select throws_ok($$
    update public.tournament_team_slots set slot_number = 13 where id = '30000000-0000-0000-0000-000000000001'
$$, '23514', null, 'invalid slot number is rejected');
select throws_ok($$
    update public.matches set match_number = 11 where id = '50000000-0000-0000-0000-000000000001'
$$, '23514', null, 'invalid match number is rejected');
select throws_ok($$
    update public.match_results set placement = 13 where id = '60000000-0000-0000-0000-000000000001'
$$, '23514', null, 'invalid placement is rejected');
select throws_ok($$
    update public.match_results set kills = -1 where id = '60000000-0000-0000-0000-000000000001'
$$, '23514', null, 'negative kills are rejected');

select throws_ok($$
    update public.tournaments set revision = 0 where id = '20000000-0000-0000-0000-000000000001'
$$, '23514', null, 'zero tournament revision is rejected');
select throws_ok($$
    update public.tournament_team_slots set revision = 0 where id = '30000000-0000-0000-0000-000000000001'
$$, '23514', null, 'zero team-slot revision is rejected');
select throws_ok($$
    update public.players set revision = 0 where id = '40000000-0000-0000-0000-000000000001'
$$, '23514', null, 'zero player revision is rejected');
select throws_ok($$
    update public.matches set revision = 0 where id = '50000000-0000-0000-0000-000000000001'
$$, '23514', null, 'zero match revision is rejected');
select throws_ok($$
    update public.match_results set revision = 0 where id = '60000000-0000-0000-0000-000000000001'
$$, '23514', null, 'zero match-result revision is rejected');
select throws_ok($$
    update public.tournaments set revision = -1 where id = '20000000-0000-0000-0000-000000000001'
$$, '23514', null, 'negative revision is rejected');

select throws_ok($$
    insert into public.tournament_team_slots (id, tournament_id, slot_number)
    values ('30000000-0000-0000-0000-000000000005', '20000000-0000-0000-0000-000000000099', 4)
$$, '23503', null, 'team slot rejects a missing tournament');
select throws_ok($$
    insert into public.players (id, team_slot_id, display_name, normalized_name)
    values ('40000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000099', 'Missing Parent', 'missing parent')
$$, '23503', null, 'player rejects a missing team slot');
select throws_ok($$
    insert into public.matches (id, tournament_id, match_number)
    values ('50000000-0000-0000-0000-000000000003', '20000000-0000-0000-0000-000000000099', 2)
$$, '23503', null, 'match rejects a missing tournament');
select throws_ok($$
    insert into public.match_results (id, match_id, team_slot_id, placement)
    values ('60000000-0000-0000-0000-000000000004', '50000000-0000-0000-0000-000000000099', '30000000-0000-0000-0000-000000000001', 2)
$$, '23503', null, 'match result rejects a missing match');
select throws_ok($$
    update public.matches
    set finalized_by = '10000000-0000-0000-0000-000000000099'
    where id = '50000000-0000-0000-0000-000000000001'
$$, '23503', null, 'match rejects an invalid finalized_by reference');
select throws_ok($$
    set constraints match_results_team_slot_id_fkey immediate;
    delete from public.tournament_team_slots where id = '30000000-0000-0000-0000-000000000001'
$$, '23503', null, 'referenced team slot cannot be deleted');

insert into auth.users (id, email)
values ('10000000-0000-0000-0000-000000000002', 'schema-cascade-owner@example.test');
insert into public.tournaments (id, owner_id, name)
values ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'Cascade test tournament');
insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
values ('30000000-0000-0000-0000-000000000010', '20000000-0000-0000-0000-000000000002', 1, 'Cascade Team');
insert into public.players (id, team_slot_id, display_name, normalized_name)
values ('40000000-0000-0000-0000-000000000010', '30000000-0000-0000-0000-000000000010', 'Cascade Player', 'cascade player');
insert into public.matches (id, tournament_id, match_number)
values ('50000000-0000-0000-0000-000000000010', '20000000-0000-0000-0000-000000000002', 1);
insert into public.match_results (id, match_id, team_slot_id, placement)
values ('60000000-0000-0000-0000-000000000010', '50000000-0000-0000-0000-000000000010', '30000000-0000-0000-0000-000000000010', 1);
delete from public.matches where id = '50000000-0000-0000-0000-000000000010';
delete from public.tournaments where id = '20000000-0000-0000-0000-000000000002';
select is((
    (select count(*) from public.tournament_team_slots where id = '30000000-0000-0000-0000-000000000010')
    + (select count(*) from public.players where id = '40000000-0000-0000-0000-000000000010')
    + (select count(*) from public.matches where id = '50000000-0000-0000-0000-000000000010')
    + (select count(*) from public.match_results where id = '60000000-0000-0000-0000-000000000010')
), 0::bigint, 'tournament deletion cascades through the approved hierarchy');

select * from finish();
rollback;
