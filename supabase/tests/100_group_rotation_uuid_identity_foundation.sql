begin;

select no_plan();

select has_table(
    'public',
    'tournament_team_identities',
    'UUID team identity registry table exists'
);
select has_table(
    'public',
    'tournament_group_pairing_team_assignments',
    'UUID pairing assignment table exists'
);

select has_column('public', 'tournament_team_identities', 'tournament_id', 'identity registry has tournament_id');
select has_column('public', 'tournament_team_identities', 'team_uuid', 'identity registry has team_uuid');
select has_column('public', 'tournament_team_identities', 'display_name', 'identity registry has display_name');
select has_column('public', 'tournament_team_identities', 'normalized_name', 'identity registry has normalized_name');
select has_column('public', 'tournament_team_identities', 'created_at', 'identity registry has created_at');
select has_column('public', 'tournament_team_identities', 'updated_at', 'identity registry has updated_at');
select has_column('public', 'tournament_group_pairing_team_assignments', 'tournament_id', 'assignment table has tournament_id');
select has_column('public', 'tournament_group_pairing_team_assignments', 'pairing_key', 'assignment table has pairing_key');
select has_column('public', 'tournament_group_pairing_team_assignments', 'lobby_slot_number', 'assignment table has lobby_slot_number');
select has_column('public', 'tournament_group_pairing_team_assignments', 'team_uuid', 'assignment table has team_uuid');

select ok(
    (select relrowsecurity from pg_class where oid = 'public.tournament_team_identities'::regclass),
    'UUID team identity registry has RLS enabled'
);
select ok(
    (select relrowsecurity from pg_class where oid = 'public.tournament_group_pairing_team_assignments'::regclass),
    'UUID pairing assignments have RLS enabled'
);

select ok(exists (
    select 1
    from pg_constraint
    where conrelid = 'public.tournament_team_identities'::regclass
        and conname = 'tournament_team_identities_pkey'
        and contype = 'p'
        and convalidated
), 'identity registry has the tournament-scoped composite primary key');
select ok(exists (
    select 1
    from pg_constraint
    where conrelid = 'public.tournament_team_identities'::regclass
        and conname = 'tournament_team_identities_tournament_fkey'
        and contype = 'f'
        and confrelid = 'public.tournaments'::regclass
        and convalidated
), 'identity registry references its tournament');
select ok(exists (
    select 1
    from pg_constraint
    where conrelid = 'public.tournament_team_identities'::regclass
        and conname = 'tournament_team_identities_normalized_name_unique'
        and contype = 'u'
        and convalidated
), 'identity registry enforces normalized-name uniqueness per tournament');
select ok(exists (
    select 1
    from pg_constraint
    where conrelid = 'public.tournament_team_identities'::regclass
        and conname = 'tournament_team_identities_display_name_check'
        and contype = 'c'
        and convalidated
), 'identity registry rejects blank display names');
select ok(exists (
    select 1
    from pg_constraint
    where conrelid = 'public.tournament_team_identities'::regclass
        and conname = 'tournament_team_identities_normalized_name_check'
        and contype = 'c'
        and convalidated
), 'identity registry validates normalized names');
select ok(exists (
    select 1
    from pg_trigger
    where tgrelid = 'public.tournament_team_identities'::regclass
        and tgname = 'tournament_team_identities_key_immutable'
        and not tgisinternal
), 'identity registry protects its tournament and UUID keys from updates');

select ok(exists (
    select 1
    from pg_constraint
    where conrelid = 'public.tournament_group_pairing_team_assignments'::regclass
        and conname = 'tournament_group_pairing_team_assignments_pkey'
        and contype = 'p'
        and convalidated
), 'assignments have tournament/pairing/lobby primary key');
select ok(exists (
    select 1
    from pg_constraint
    where conrelid = 'public.tournament_group_pairing_team_assignments'::regclass
        and conname = 'tournament_group_pairing_team_assignments_pairing_fkey'
        and contype = 'f'
        and confrelid = 'public.tournament_group_pairings'::regclass
        and convalidated
), 'assignments reference configured tournament pairings');
select ok(exists (
    select 1
    from pg_constraint
    where conrelid = 'public.tournament_group_pairing_team_assignments'::regclass
        and conname = 'tournament_group_pairing_team_assignments_team_fkey'
        and contype = 'f'
        and confrelid = 'public.tournament_team_identities'::regclass
        and convalidated
), 'assignments reference tournament-scoped UUID identities');
select ok(exists (
    select 1
    from pg_constraint
    where conrelid = 'public.tournament_group_pairing_team_assignments'::regclass
        and conname = 'tournament_group_pairing_team_assignments_team_unique'
        and contype = 'u'
        and convalidated
), 'assignments reject duplicate UUIDs within one pairing');
select ok(exists (
    select 1
    from pg_constraint
    where conrelid = 'public.tournament_group_pairing_team_assignments'::regclass
        and conname = 'tournament_group_pairing_team_assignments_lobby_check'
        and contype = 'c'
        and convalidated
), 'assignments restrict lobby positions to 1..12');

select ok(has_table_privilege(
    'authenticated',
    'public.tournament_team_identities',
    'select,insert,update'
), 'authenticated receives identity registry read/insert/update access');
select ok(not has_table_privilege('anon', 'public.tournament_team_identities', 'select'), 'anon cannot read identity registry');
select ok(not has_table_privilege('authenticated', 'public.tournament_team_identities', 'delete'), 'authenticated cannot delete permanent identities');
select ok(not has_table_privilege('authenticated', 'public.tournament_team_identities', 'truncate'), 'authenticated cannot truncate identity registry');
select ok(not has_table_privilege('authenticated', 'public.tournament_team_identities', 'references'), 'authenticated cannot grant references on identity registry');
select ok(not has_table_privilege('authenticated', 'public.tournament_team_identities', 'trigger'), 'authenticated cannot create triggers on identity registry');
select ok(has_table_privilege(
    'authenticated',
    'public.tournament_group_pairing_team_assignments',
    'select,insert,update,delete'
), 'authenticated receives assignment CRUD');
select ok(not has_table_privilege('anon', 'public.tournament_group_pairing_team_assignments', 'select'), 'anon cannot read assignments');
select ok(not has_table_privilege('authenticated', 'public.tournament_group_pairing_team_assignments', 'truncate'), 'authenticated cannot truncate assignments');
select ok(not has_table_privilege('authenticated', 'public.tournament_group_pairing_team_assignments', 'references'), 'authenticated cannot grant references on assignments');
select ok(not has_table_privilege('authenticated', 'public.tournament_group_pairing_team_assignments', 'trigger'), 'authenticated cannot create triggers on assignments');

select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and tablename in (
            'tournament_team_identities',
            'tournament_group_pairing_team_assignments'
        )
), 7::bigint, 'UUID tables have owner policies with permanent-identity protection');

select ok(to_regclass('public.tournaments') is not null, 'Standard tournament table remains present');
select ok(to_regclass('public.tournament_team_slots') is not null, 'numeric TeamSlot table remains present');
select ok(to_regclass('public.tournament_group_pairings') is not null, 'legacy Group Rotation pairing table remains present');
select ok(to_regclass('public.tournament_group_pairing_lobby_slots') is not null, 'legacy numeric pairing mapping remains present');
select ok(to_regprocedure('public.write_tournament_snapshot(jsonb,jsonb,jsonb,integer)') is not null, 'Standard snapshot RPC remains present');
select ok(to_regprocedure('public.write_tournament_snapshot_v2(jsonb,jsonb,jsonb,jsonb,integer)') is not null, 'legacy Group Rotation snapshot RPC remains present');
select ok(to_regprocedure('public.write_tournament_snapshot_v3(jsonb,jsonb,jsonb,jsonb,integer)') is not null, 'V3 Group Rotation snapshot RPC remains present');

insert into auth.users (id, email)
values
    ('e2a00000-0000-0000-0000-000000000001', 'phase3d2a-owner@example.test'),
    ('e2a00000-0000-0000-0000-000000000002', 'phase3d2a-other@example.test');

insert into public.tournaments (id, owner_id, name, format, group_count, status)
values
    ('e2a10000-0000-0000-0000-000000000001', 'e2a00000-0000-0000-0000-000000000001', 'UUID Group Rotation', 'group_rotation', 3, 'draft'),
    ('e2a10000-0000-0000-0000-000000000002', 'e2a00000-0000-0000-0000-000000000001', 'Second UUID Group Rotation', 'group_rotation', 3, 'draft'),
    ('e2a10000-0000-0000-0000-000000000003', 'e2a00000-0000-0000-0000-000000000002', 'Other Owner Group Rotation', 'group_rotation', 3, 'draft'),
    ('e2a10000-0000-0000-0000-000000000004', 'e2a00000-0000-0000-0000-000000000001', 'Unchanged Standard', 'standard', null, 'draft');

insert into public.tournament_group_pairings (tournament_id, first_group, second_group)
values
    ('e2a10000-0000-0000-0000-000000000001', 'A', 'B'),
    ('e2a10000-0000-0000-0000-000000000001', 'A', 'C'),
    ('e2a10000-0000-0000-0000-000000000002', 'A', 'B'),
    ('e2a10000-0000-0000-0000-000000000003', 'A', 'B');

insert into public.tournament_team_identities (
    tournament_id, team_uuid, display_name, normalized_name
)
values
    ('e2a10000-0000-0000-0000-000000000002', 'e2a20000-0000-0000-0000-000000000004', 'Second Tournament Team', 'second tournament team');

insert into public.tournament_group_pairing_team_assignments (
    tournament_id, pairing_key, lobby_slot_number, team_uuid
)
values
    ('e2a10000-0000-0000-0000-000000000002', 'A:B', 2, 'e2a20000-0000-0000-0000-000000000004');

set local role authenticated;
set local request.jwt.claim.sub = 'e2a00000-0000-0000-0000-000000000001';

insert into public.tournament_team_identities (
    tournament_id, team_uuid, display_name, normalized_name
)
values
    ('e2a10000-0000-0000-0000-000000000001', 'e2a20000-0000-0000-0000-000000000001', 'Phoenix', 'phoenix'),
    ('e2a10000-0000-0000-0000-000000000001', 'e2a20000-0000-0000-0000-000000000002', 'Alpha Team', 'alpha team');

select is((
    select count(*)
    from public.tournament_team_identities
    where tournament_id = 'e2a10000-0000-0000-0000-000000000001'
), 2::bigint, 'owner can create registered UUID identities');

update public.tournament_team_identities
set display_name = 'Phoenix Renamed', normalized_name = 'phoenix renamed'
where tournament_id = 'e2a10000-0000-0000-0000-000000000001'
    and team_uuid = 'e2a20000-0000-0000-0000-000000000001';

select is((
    select team_uuid
    from public.tournament_team_identities
    where tournament_id = 'e2a10000-0000-0000-0000-000000000001'
        and normalized_name = 'phoenix renamed'
), 'e2a20000-0000-0000-0000-000000000001'::uuid, 'rename preserves the registered UUID');
select is((
    select display_name
    from public.tournament_team_identities
    where tournament_id = 'e2a10000-0000-0000-0000-000000000001'
        and team_uuid = 'e2a20000-0000-0000-0000-000000000001'
), 'Phoenix Renamed', 'owner can rename an identity without replacing it');
select throws_ok($$
    update public.tournament_team_identities
    set team_uuid = 'e2a20000-0000-0000-0000-000000000099'
    where tournament_id = 'e2a10000-0000-0000-0000-000000000001'
        and team_uuid = 'e2a20000-0000-0000-0000-000000000001'
$$, '23514', null, 'registered UUID cannot be reassigned');
select throws_ok($$
    delete from public.tournament_team_identities
    where tournament_id = 'e2a10000-0000-0000-0000-000000000001'
        and team_uuid = 'e2a20000-0000-0000-0000-000000000002'
$$, '42501', null, 'permanent identity deletion is rejected');

insert into public.tournament_group_pairing_team_assignments (
    tournament_id, pairing_key, lobby_slot_number, team_uuid
)
values
    ('e2a10000-0000-0000-0000-000000000001', 'A:B', 1, 'e2a20000-0000-0000-0000-000000000001');

select is((
    select team_uuid
    from public.tournament_group_pairing_team_assignments
    where tournament_id = 'e2a10000-0000-0000-0000-000000000001'
        and pairing_key = 'A:B'
        and lobby_slot_number = 1
), 'e2a20000-0000-0000-0000-000000000001'::uuid, 'owner can create a valid pairing assignment');

insert into public.tournament_group_pairing_team_assignments (
    tournament_id, pairing_key, lobby_slot_number, team_uuid
)
values
    ('e2a10000-0000-0000-0000-000000000001', 'A:C', 7, 'e2a20000-0000-0000-0000-000000000001');

select is((
    select count(*)
    from public.tournament_group_pairing_team_assignments
    where tournament_id = 'e2a10000-0000-0000-0000-000000000001'
        and team_uuid = 'e2a20000-0000-0000-0000-000000000001'
), 2::bigint, 'one UUID can be reused in different pairings');

select throws_ok($$
    insert into public.tournament_group_pairing_team_assignments (
        tournament_id, pairing_key, lobby_slot_number, team_uuid
    ) values (
        'e2a10000-0000-0000-0000-000000000001', 'A:B', 2,
        'e2a20000-0000-0000-0000-000000000001'
    )
$$, '23505', null, 'duplicate UUID within one pairing is rejected');

select throws_ok($$
    insert into public.tournament_group_pairing_team_assignments (
        tournament_id, pairing_key, lobby_slot_number, team_uuid
    ) values (
        'e2a10000-0000-0000-0000-000000000001', 'A:B', 1,
        'e2a20000-0000-0000-0000-000000000002'
    )
$$, '23505', null, 'duplicate lobby position is rejected');

select throws_ok($$
    insert into public.tournament_team_identities (
        tournament_id, team_uuid, display_name, normalized_name
    ) values (
        'e2a10000-0000-0000-0000-000000000001',
        'e2a20000-0000-0000-0000-000000000003',
        'phoenix renamed', 'phoenix renamed'
    )
$$, '23505', null, 'duplicate normalized name within a tournament is rejected');

select throws_ok($$
    insert into public.tournament_team_identities (
        tournament_id, team_uuid, display_name, normalized_name
    ) values (
        'e2a10000-0000-0000-0000-000000000001',
        'e2a20000-0000-0000-0000-000000000003',
        '   ', ''
    )
$$, '23514', null, 'blank display names are rejected');

select throws_ok($$
    insert into public.tournament_team_identities (
        tournament_id, team_uuid, display_name, normalized_name
    ) values (
        'e2a10000-0000-0000-0000-000000000001',
        'e2a20000-0000-0000-0000-000000000003',
        'Different Name', 'not the normalized name'
    )
$$, '23514', null, 'normalized names must follow the local normalization rule');

select throws_ok($$
    insert into public.tournament_group_pairing_team_assignments (
        tournament_id, pairing_key, lobby_slot_number, team_uuid
    ) values (
        'e2a10000-0000-0000-0000-000000000001', 'A:B', 0,
        'e2a20000-0000-0000-0000-000000000002'
    )
$$, '23514', null, 'lobby slot zero is rejected');

select throws_ok($$
    insert into public.tournament_group_pairing_team_assignments (
        tournament_id, pairing_key, lobby_slot_number, team_uuid
    ) values (
        'e2a10000-0000-0000-0000-000000000001', 'A:B', 13,
        'e2a20000-0000-0000-0000-000000000002'
    )
$$, '23514', null, 'lobby slot thirteen is rejected');

select throws_ok($$
    insert into public.tournament_group_pairing_team_assignments (
        tournament_id, pairing_key, lobby_slot_number, team_uuid
    ) values (
        'e2a10000-0000-0000-0000-000000000002', 'A:B', 1,
        'e2a20000-0000-0000-0000-000000000001'
    )
$$, '23503', null, 'cross-tournament UUID reference is rejected');

set local request.jwt.claim.sub = 'e2a00000-0000-0000-0000-000000000002';

select lives_ok($$
    delete from public.tournaments
    where id = 'e2a10000-0000-0000-0000-000000000001'
$$, 'non-owner tournament deletion attempt is denied without mutation');

select is((
    select count(*)
    from public.tournament_team_identities
    where tournament_id = 'e2a10000-0000-0000-0000-000000000001'
), 0::bigint, 'non-owner cannot read another owner identity registry');
select is((
    select count(*)
    from public.tournament_group_pairing_team_assignments
    where tournament_id = 'e2a10000-0000-0000-0000-000000000001'
), 0::bigint, 'non-owner cannot read another owner assignments');

select throws_ok($$
    insert into public.tournament_team_identities (
        tournament_id, team_uuid, display_name, normalized_name
    ) values (
        'e2a10000-0000-0000-0000-000000000001',
        'e2a20000-0000-0000-0000-000000000004',
        'Other User Team', 'other user team'
    )
$$, '42501', null, 'non-owner cannot write another owner identity registry');

set local role anon;

select throws_ok($$
    select count(*) from public.tournament_team_identities
$$, '42501', null, 'anonymous role cannot read UUID identity registry');
select throws_ok($$
    select count(*) from public.tournament_group_pairing_team_assignments
$$, '42501', null, 'anonymous role cannot read UUID assignments');

set local role authenticated;
set local request.jwt.claim.sub = 'e2a00000-0000-0000-0000-000000000001';

select is((
    select count(*)
    from public.tournaments
    where id = 'e2a10000-0000-0000-0000-000000000001'
), 1::bigint, 'non-owner deletion attempt leaves the tournament present');
select is((
    select count(*)
    from public.tournament_team_identities
    where tournament_id = 'e2a10000-0000-0000-0000-000000000001'
), 2::bigint, 'owner data remains unchanged after denied access attempts');
select is((
    select count(*)
    from public.tournament_group_pairing_team_assignments
    where tournament_id = 'e2a10000-0000-0000-0000-000000000001'
), 2::bigint, 'owner assignments remain unchanged after denied access attempts');

delete from public.tournaments
where id = 'e2a10000-0000-0000-0000-000000000002';

select is((
    select count(*)
    from public.tournaments
    where id = 'e2a10000-0000-0000-0000-000000000002'
), 0::bigint, 'authorized owner can delete the tournament');
select is((
    select count(*)
    from public.tournament_team_identities
    where tournament_id = 'e2a10000-0000-0000-0000-000000000002'
), 0::bigint, 'tournament deletion cascades registered identities');
select is((
    select count(*)
    from public.tournament_group_pairing_team_assignments
    where tournament_id = 'e2a10000-0000-0000-0000-000000000002'
), 0::bigint, 'tournament deletion cascades pairing assignments');

select * from finish();
rollback;
