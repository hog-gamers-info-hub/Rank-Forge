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
    select coalesce(array_agg(procedure_row.proname::text order by procedure_row.proname), array[]::text[])::text
    from pg_proc procedure_row
    join pg_namespace namespace_row on namespace_row.oid = procedure_row.pronamespace
    where namespace_row.nspname = 'public'
        and procedure_row.proname in (
            'write_tournament_snapshot',
            'write_match_snapshot',
            'finalize_match_snapshot',
            'correct_finalized_match_snapshot'
        )
)::text, '{correct_finalized_match_snapshot,finalize_match_snapshot,write_match_snapshot,write_tournament_snapshot}'::text, 'approved revision-safe RPCs exist');
select is((
    select coalesce(
        array_agg(
            (class_row.relname || ':' || trigger_row.tgname)::text
            order by class_row.relname, trigger_row.tgname
        ),
        array[]::text[]
    )::text
    from pg_trigger trigger_row
    join pg_class class_row on class_row.oid = trigger_row.tgrelid
    join pg_namespace namespace_row on namespace_row.oid = class_row.relnamespace
    where namespace_row.nspname = 'public'
        and class_row.relname in ('tournaments', 'tournament_team_slots', 'players', 'matches', 'match_results')
        and not trigger_row.tgisinternal
), '{tournaments:tournaments_owner_limit_before_insert}'::text, 'core tables have exactly the approved tournament-owner-limit trigger');
select is((
    select coalesce(array_agg(procedure_row.proname::text order by procedure_row.proname), array[]::text[])::text
    from pg_proc procedure_row
    join pg_namespace namespace_row on namespace_row.oid = procedure_row.pronamespace
    where namespace_row.nspname = 'public'
        and procedure_row.prosecdef
)::text, '{claim_export_operation,complete_export_operation_success,correct_finalized_match_snapshot,delete_match_idempotent,delete_tournament_idempotent,finalize_match_snapshot,mark_export_operation_outcome_uncertain,mark_export_operation_retryable_failure,mark_export_operation_write_started,resolve_export_operation_verified_success,signup_email_is_registered}'::text, 'only approved protected, export-state, and deletion RPCs use documented security definer ownership checks');

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
