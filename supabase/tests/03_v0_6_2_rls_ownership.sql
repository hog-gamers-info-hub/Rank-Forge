begin;

select plan(51);

select ok((select relrowsecurity from pg_class where oid = 'public.tournaments'::regclass), 'tournaments retains RLS');
select ok((select relrowsecurity from pg_class where oid = 'public.tournament_team_slots'::regclass), 'tournament_team_slots retains RLS');
select ok((select relrowsecurity from pg_class where oid = 'public.players'::regclass), 'players retains RLS');
select ok((select relrowsecurity from pg_class where oid = 'public.matches'::regclass), 'matches retains RLS');
select ok((select relrowsecurity from pg_class where oid = 'public.match_results'::regclass), 'match_results retains RLS');

select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and tablename in ('tournaments', 'tournament_team_slots', 'players', 'matches', 'match_results')
), 20::bigint, 'exactly 20 core operation policies exist');
select ok((
    select array_agg(lower(cmd) order by lower(cmd)) = array['delete', 'insert', 'select', 'update']::text[]
    from pg_policies
    where schemaname = 'public'
        and tablename = 'tournaments'
), 'tournaments has separate CRUD policies');
select ok((
    select array_agg(lower(cmd) order by lower(cmd)) = array['delete', 'insert', 'select', 'update']::text[]
    from pg_policies
    where schemaname = 'public'
        and tablename = 'tournament_team_slots'
), 'tournament_team_slots has separate CRUD policies');
select ok((
    select array_agg(lower(cmd) order by lower(cmd)) = array['delete', 'insert', 'select', 'update']::text[]
    from pg_policies
    where schemaname = 'public'
        and tablename = 'players'
), 'players has separate CRUD policies');
select ok((
    select array_agg(lower(cmd) order by lower(cmd)) = array['delete', 'insert', 'select', 'update']::text[]
    from pg_policies
    where schemaname = 'public'
        and tablename = 'matches'
), 'matches has separate CRUD policies');
select ok((
    select array_agg(lower(cmd) order by lower(cmd)) = array['delete', 'insert', 'select', 'update']::text[]
    from pg_policies
    where schemaname = 'public'
        and tablename = 'match_results'
), 'match_results has separate CRUD policies');

select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and tablename in ('tournaments', 'tournament_team_slots', 'players', 'matches', 'match_results')
        and roles = array['authenticated'::name]
), 20::bigint, 'all core policies target authenticated');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and tablename in ('tournaments', 'tournament_team_slots', 'players', 'matches', 'match_results')
        and 'anon'::name = any(roles)
), 0::bigint, 'no core policy targets anon');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and tablename in ('tournaments', 'tournament_team_slots', 'players', 'matches', 'match_results')
        and (coalesce(qual, '') ~* '(^|[^a-z])true([^a-z]|$)'
            or coalesce(with_check, '') ~* '(^|[^a-z])true([^a-z]|$)')
), 0::bigint, 'no core policy is unconditional');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and tablename in ('tournaments', 'tournament_team_slots', 'players', 'matches', 'match_results')
        and (coalesce(qual, '') || coalesce(with_check, '')) ~ 'auth\.uid'
), 20::bigint, 'all core policies use auth.uid ownership predicates');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and lower(cmd) = 'update'
        and tablename in ('tournaments', 'tournament_team_slots', 'players', 'matches', 'match_results')
        and qual is not null
        and with_check is not null
), 5::bigint, 'all update policies have USING and WITH CHECK');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and tablename in ('tournaments', 'tournament_team_slots', 'players', 'matches', 'match_results')
        and (coalesce(qual, '') || coalesce(with_check, '')) ~* 'auth\.role'
), 0::bigint, 'no core policy uses auth.role');
select is((
    select count(*)
    from pg_proc procedure_row
    join pg_namespace namespace_row on namespace_row.oid = procedure_row.pronamespace
    where namespace_row.nspname = 'public'
), 0::bigint, 'public has no functions or RPCs');
select is((
    select count(*)
    from pg_trigger trigger_row
    join pg_class class_row on class_row.oid = trigger_row.tgrelid
    join pg_namespace namespace_row on namespace_row.oid = class_row.relnamespace
    where namespace_row.nspname = 'public'
        and class_row.relname in ('tournaments', 'tournament_team_slots', 'players', 'matches', 'match_results')
        and not trigger_row.tgisinternal
), 0::bigint, 'core tables have no user-defined triggers');
select is((
    select count(*)
    from pg_proc procedure_row
    join pg_namespace namespace_row on namespace_row.oid = procedure_row.pronamespace
    where namespace_row.nspname = 'public'
        and procedure_row.prosecdef
), 0::bigint, 'public has no SECURITY DEFINER functions');

insert into auth.users (id, email)
values ('71000000-0000-0000-0000-000000000001', 'rls-owner@example.test');

set local role authenticated;
set local request.jwt.claim.sub = '71000000-0000-0000-0000-000000000001';

select lives_ok($$
    insert into public.tournaments (id, owner_id, name)
    values ('72000000-0000-0000-0000-000000000001', '71000000-0000-0000-0000-000000000001', 'Owned tournament')
$$, 'owner can insert an owned tournament');
select is((
    select count(*)
    from public.tournaments
    where id = '72000000-0000-0000-0000-000000000001'
), 1::bigint, 'owner can select an owned tournament');
select lives_ok($$
    update public.tournaments
    set organizer_name = 'Owner'
    where id = '72000000-0000-0000-0000-000000000001'
$$, 'owner can update an owned tournament');
select lives_ok($$
    insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
    values ('73000000-0000-0000-0000-000000000001', '72000000-0000-0000-0000-000000000001', 1, 'Owned Team')
$$, 'owner can insert a team slot in an owned tournament');
select lives_ok($$
    insert into public.players (id, team_slot_id, display_name, normalized_name)
    values ('74000000-0000-0000-0000-000000000001', '73000000-0000-0000-0000-000000000001', 'Owned Player', 'owned player')
$$, 'owner can insert a player through an owned team slot');
select lives_ok($$
    insert into public.matches (id, tournament_id, match_number)
    values ('75000000-0000-0000-0000-000000000001', '72000000-0000-0000-0000-000000000001', 1)
$$, 'owner can insert a match in an owned tournament');
select lives_ok($$
    insert into public.match_results (id, match_id, team_slot_id, placement)
    values ('76000000-0000-0000-0000-000000000001', '75000000-0000-0000-0000-000000000001', '73000000-0000-0000-0000-000000000001', 1)
$$, 'owner can insert a same-tournament match result');
select lives_ok($$
    update public.tournament_team_slots
    set status = 'complete'
    where id = '73000000-0000-0000-0000-000000000001'
$$, 'owner can update an owned team slot');
select lives_ok($$
    update public.players
    set display_name = 'Owned Player Updated'
    where id = '74000000-0000-0000-0000-000000000001'
$$, 'owner can update an owned player');
select lives_ok($$
    update public.matches
    set map_name = 'Owned Map'
    where id = '75000000-0000-0000-0000-000000000001'
$$, 'owner can update an owned match');
select lives_ok($$
    update public.match_results
    set kills = 1
    where id = '76000000-0000-0000-0000-000000000001'
$$, 'owner can update an owned match result');
select lives_ok($$
    insert into public.tournaments (id, owner_id, name)
    values ('72000000-0000-0000-0000-000000000002', '71000000-0000-0000-0000-000000000001', 'Second owned tournament')
$$, 'owner can insert a second owned tournament');
select lives_ok($$
    insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
    values ('73000000-0000-0000-0000-000000000002', '72000000-0000-0000-0000-000000000002', 1, 'Second Owned Team')
$$, 'owner can insert a team slot in the second owned tournament');
select lives_ok($$
    insert into public.matches (id, tournament_id, match_number)
    values ('75000000-0000-0000-0000-000000000002', '72000000-0000-0000-0000-000000000002', 1)
$$, 'owner can insert a match in the second owned tournament');
select throws_ok($$
    insert into public.match_results (id, match_id, team_slot_id, placement)
    values ('76000000-0000-0000-0000-000000000002', '75000000-0000-0000-0000-000000000001', '73000000-0000-0000-0000-000000000002', 2)
$$, '42501', null, 'owner cannot mix match and team slot from different tournaments');
select lives_ok($$
    delete from public.players where id = '74000000-0000-0000-0000-000000000001'
$$, 'owner can delete an owned player');
select lives_ok($$
    delete from public.match_results where id = '76000000-0000-0000-0000-000000000001'
$$, 'owner can delete an owned match result');
select lives_ok($$
    delete from public.matches where id = '75000000-0000-0000-0000-000000000001'
$$, 'owner can delete an owned match');
select lives_ok($$
    delete from public.tournament_team_slots where id = '73000000-0000-0000-0000-000000000001'
$$, 'owner can delete an owned team slot');
select lives_ok($$
    delete from public.tournaments where id = '72000000-0000-0000-0000-000000000001'
$$, 'owner can delete an owned tournament');

set local role anon;
set local request.jwt.claim.sub = '';

select is((
    select count(*)
    from public.tournaments
), 0::bigint, 'unauthenticated context cannot read core tournaments');
select is((
    select count(*)
    from public.tournament_team_slots
), 0::bigint, 'unauthenticated context cannot read core team slots');
select is((
    select count(*)
    from public.players
), 0::bigint, 'unauthenticated context cannot read core players');
select is((
    select count(*)
    from public.matches
), 0::bigint, 'unauthenticated context cannot read core matches');
select is((
    select count(*)
    from public.match_results
), 0::bigint, 'unauthenticated context cannot read core match results');
select throws_ok($$
    insert into public.tournaments (id, owner_id, name)
    values ('72000000-0000-0000-0000-000000000003', '71000000-0000-0000-0000-000000000001', 'Anonymous tournament')
$$, '42501', null, 'unauthenticated context cannot insert core tournaments');
select throws_ok($$
    insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
    values ('73000000-0000-0000-0000-000000000003', '72000000-0000-0000-0000-000000000002', 2, 'Anonymous Team')
$$, '42501', null, 'unauthenticated context cannot insert core team slots');
select throws_ok($$
    insert into public.players (id, team_slot_id, display_name, normalized_name)
    values ('74000000-0000-0000-0000-000000000002', '73000000-0000-0000-0000-000000000002', 'Anonymous Player', 'anonymous player')
$$, '42501', null, 'unauthenticated context cannot insert core players');
select throws_ok($$
    insert into public.matches (id, tournament_id, match_number)
    values ('75000000-0000-0000-0000-000000000003', '72000000-0000-0000-0000-000000000002', 2)
$$, '42501', null, 'unauthenticated context cannot insert core matches');
select throws_ok($$
    insert into public.match_results (id, match_id, team_slot_id, placement)
    values ('76000000-0000-0000-0000-000000000003', '75000000-0000-0000-0000-000000000002', '73000000-0000-0000-0000-000000000002', 1)
$$, '42501', null, 'unauthenticated context cannot insert core match results');

reset role;

select lives_ok($$
    delete from public.tournaments where id = '72000000-0000-0000-0000-000000000002'
$$, 'owner can delete a second owned tournament');

select * from finish();
rollback;
