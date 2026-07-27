begin;

select plan(32);

select has_table('public', 'tournaments', 'tournaments table exists');
select has_table('public', 'tournament_team_slots', 'tournament_team_slots table exists');
select has_table('public', 'players', 'players table exists');
select has_table('public', 'matches', 'matches table exists');
select has_table('public', 'match_results', 'match_results table exists');

select ok(exists (
    select 1
    from pg_constraint constraint_row
    join pg_attribute attribute_row
        on attribute_row.attrelid = constraint_row.conrelid
        and attribute_row.attnum = any(constraint_row.conkey)
    where constraint_row.conrelid = 'public.tournaments'::regclass
        and constraint_row.contype = 'p'
        and attribute_row.attname = 'id'
        and attribute_row.atttypid = 'uuid'::regtype
), 'tournaments has a UUID primary key');
select ok(exists (
    select 1
    from pg_constraint constraint_row
    join pg_attribute attribute_row
        on attribute_row.attrelid = constraint_row.conrelid
        and attribute_row.attnum = any(constraint_row.conkey)
    where constraint_row.conrelid = 'public.tournament_team_slots'::regclass
        and constraint_row.contype = 'p'
        and attribute_row.attname = 'id'
        and attribute_row.atttypid = 'uuid'::regtype
), 'tournament_team_slots has a UUID primary key');
select ok(exists (
    select 1
    from pg_constraint constraint_row
    join pg_attribute attribute_row
        on attribute_row.attrelid = constraint_row.conrelid
        and attribute_row.attnum = any(constraint_row.conkey)
    where constraint_row.conrelid = 'public.players'::regclass
        and constraint_row.contype = 'p'
        and attribute_row.attname = 'id'
        and attribute_row.atttypid = 'uuid'::regtype
), 'players has a UUID primary key');
select ok(exists (
    select 1
    from pg_constraint constraint_row
    join pg_attribute attribute_row
        on attribute_row.attrelid = constraint_row.conrelid
        and attribute_row.attnum = any(constraint_row.conkey)
    where constraint_row.conrelid = 'public.matches'::regclass
        and constraint_row.contype = 'p'
        and attribute_row.attname = 'id'
        and attribute_row.atttypid = 'uuid'::regtype
), 'matches has a UUID primary key');
select ok(exists (
    select 1
    from pg_constraint constraint_row
    join pg_attribute attribute_row
        on attribute_row.attrelid = constraint_row.conrelid
        and attribute_row.attnum = any(constraint_row.conkey)
    where constraint_row.conrelid = 'public.match_results'::regclass
        and constraint_row.contype = 'p'
        and attribute_row.attname = 'id'
        and attribute_row.atttypid = 'uuid'::regtype
), 'match_results has a UUID primary key');

select ok((
    select count(*) = 2
    from pg_attribute attribute_row
    where attribute_row.attrelid = 'public.tournaments'::regclass
        and attribute_row.attname in ('created_at', 'updated_at')
        and attribute_row.atttypid = 'timestamp with time zone'::regtype
        and attribute_row.attnotnull
), 'tournaments retains required timestamps');
select ok((
    select count(*) = 2
    from pg_attribute attribute_row
    where attribute_row.attrelid = 'public.tournament_team_slots'::regclass
        and attribute_row.attname in ('created_at', 'updated_at')
        and attribute_row.atttypid = 'timestamp with time zone'::regtype
        and attribute_row.attnotnull
), 'tournament_team_slots retains required timestamps');
select ok((
    select count(*) = 2
    from pg_attribute attribute_row
    where attribute_row.attrelid = 'public.players'::regclass
        and attribute_row.attname in ('created_at', 'updated_at')
        and attribute_row.atttypid = 'timestamp with time zone'::regtype
        and attribute_row.attnotnull
), 'players retains required timestamps');
select ok((
    select count(*) = 2
    from pg_attribute attribute_row
    where attribute_row.attrelid = 'public.matches'::regclass
        and attribute_row.attname in ('created_at', 'updated_at')
        and attribute_row.atttypid = 'timestamp with time zone'::regtype
        and attribute_row.attnotnull
), 'matches retains required timestamps');
select ok((
    select count(*) = 2
    from pg_attribute attribute_row
    where attribute_row.attrelid = 'public.match_results'::regclass
        and attribute_row.attname in ('created_at', 'updated_at')
        and attribute_row.atttypid = 'timestamp with time zone'::regtype
        and attribute_row.attnotnull
), 'match_results retains required timestamps');

select ok(exists (
    select 1 from pg_constraint
    where conrelid = 'public.tournaments'::regclass
        and contype = 'f'
        and confrelid = 'auth.users'::regclass
), 'tournaments references auth.users');
select ok(exists (
    select 1 from pg_constraint
    where conrelid = 'public.tournament_team_slots'::regclass
        and contype = 'f'
        and confrelid = 'public.tournaments'::regclass
), 'tournament_team_slots references tournaments');
select ok(exists (
    select 1 from pg_constraint
    where conrelid = 'public.players'::regclass
        and contype = 'f'
        and confrelid = 'public.tournament_team_slots'::regclass
), 'players references tournament_team_slots');
select ok(exists (
    select 1 from pg_constraint
    where conrelid = 'public.matches'::regclass
        and contype = 'f'
        and confrelid = 'public.tournaments'::regclass
), 'matches references tournaments');
select ok(exists (
    select 1 from pg_constraint
    where conrelid = 'public.match_results'::regclass
        and contype = 'f'
        and confrelid = 'public.matches'::regclass
), 'match_results references matches');
select ok(exists (
    select 1 from pg_constraint
    where conrelid = 'public.match_results'::regclass
        and contype = 'f'
        and confrelid = 'public.tournament_team_slots'::regclass
), 'match_results references tournament_team_slots');

select ok((select relrowsecurity from pg_class where oid = 'public.tournaments'::regclass), 'tournaments has RLS enabled');
select ok((select relrowsecurity from pg_class where oid = 'public.tournament_team_slots'::regclass), 'tournament_team_slots has RLS enabled');
select ok((select relrowsecurity from pg_class where oid = 'public.players'::regclass), 'players has RLS enabled');
select ok((select relrowsecurity from pg_class where oid = 'public.matches'::regclass), 'matches has RLS enabled');
select ok((select relrowsecurity from pg_class where oid = 'public.match_results'::regclass), 'match_results has RLS enabled');

select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and tablename in ('tournaments', 'tournament_team_slots', 'players', 'matches', 'match_results')
), 0::bigint, 'core tables have no RLS policies');
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
), 0::bigint, 'public has no functions or RPCs');
select is((
    select coalesce(array_agg(tablename order by tablename), array[]::text[])
    from pg_tables
    where schemaname = 'public'
), array['match_results', 'matches', 'players', 'tournament_team_slots', 'tournaments']::text[], 'no excluded public tables exist');
select ok(to_regclass('public.idx_tournaments_owner_id') is not null, 'owner index exists');
select ok(to_regclass('public.idx_match_results_team_slot_id') is not null, 'match-result team-slot index exists');

select * from finish();
rollback;
