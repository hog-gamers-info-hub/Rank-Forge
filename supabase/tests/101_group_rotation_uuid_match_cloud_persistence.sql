begin;

select no_plan();

select has_table(
    'public',
    'group_rotation_match_uuid_snapshots',
    'UUID match snapshot table exists'
);
select has_table(
    'public',
    'group_rotation_match_uuid_assignments',
    'UUID match assignment table exists'
);
select has_table(
    'public',
    'group_rotation_match_uuid_results',
    'UUID match result table exists'
);
select has_table(
    'public',
    'group_rotation_match_uuid_write_requests',
    'UUID match request ledger exists'
);

select has_column('public', 'group_rotation_match_uuid_snapshots', 'match_id', 'snapshot stores match UUID');
select has_column('public', 'group_rotation_match_uuid_snapshots', 'tournament_id', 'snapshot stores tournament UUID');
select has_column('public', 'group_rotation_match_uuid_snapshots', 'pairing_key', 'snapshot stores pairing identity');
select has_column('public', 'group_rotation_match_uuid_snapshots', 'status', 'snapshot stores draft/finalized state');
select has_column('public', 'group_rotation_match_uuid_snapshots', 'revision', 'snapshot stores revision');
select has_column('public', 'group_rotation_match_uuid_assignments', 'lobby_slot_number', 'match assignments store lobby position');
select has_column('public', 'group_rotation_match_uuid_assignments', 'team_uuid', 'match assignments store team UUID');
select has_column('public', 'group_rotation_match_uuid_results', 'participation_status', 'UUID results store participation status');
select has_column('public', 'group_rotation_match_uuid_results', 'placement', 'UUID results preserve nullable placement');
select has_column('public', 'group_rotation_match_uuid_results', 'kills', 'UUID results store kills');
select has_column('public', 'group_rotation_match_uuid_results', 'point_adjustment', 'UUID results store point adjustment');

select ok(
    (select relrowsecurity from pg_class where oid = 'public.group_rotation_match_uuid_snapshots'::regclass),
    'UUID match snapshots have RLS enabled'
);
select ok(
    (select relrowsecurity from pg_class where oid = 'public.group_rotation_match_uuid_assignments'::regclass),
    'UUID match assignments have RLS enabled'
);
select ok(
    (select relrowsecurity from pg_class where oid = 'public.group_rotation_match_uuid_results'::regclass),
    'UUID match results have RLS enabled'
);

select ok(exists (
    select 1 from pg_constraint
    where conrelid = 'public.group_rotation_match_uuid_snapshots'::regclass
        and conname = 'group_rotation_match_uuid_snapshots_match_fkey'
        and contype = 'f' and convalidated
), 'UUID match snapshots are bound to the existing match and tournament');
select ok(exists (
    select 1 from pg_constraint
    where conrelid = 'public.group_rotation_match_uuid_snapshots'::regclass
        and conname = 'group_rotation_match_uuid_snapshots_pairing_fkey'
        and contype = 'f' and convalidated
), 'UUID match snapshots are bound to a configured pairing');
select ok(exists (
    select 1 from pg_constraint
    where conrelid = 'public.group_rotation_match_uuid_assignments'::regclass
        and conname = 'group_rotation_match_uuid_assignments_team_fkey'
        and contype = 'f' and convalidated
), 'UUID match assignments reference the permanent identity registry');
select ok(exists (
    select 1 from pg_constraint
    where conrelid = 'public.group_rotation_match_uuid_assignments'::regclass
        and conname = 'group_rotation_match_uuid_assignments_team_unique'
        and contype = 'u' and convalidated
), 'UUID match assignments reject duplicate teams in one match');
select ok(exists (
    select 1 from pg_constraint
    where conrelid = 'public.group_rotation_match_uuid_assignments'::regclass
        and conname = 'group_rotation_match_uuid_assignments_lobby_check'
        and contype = 'c' and convalidated
), 'UUID match assignments restrict lobby positions to 1..12');
select ok(exists (
    select 1 from pg_constraint
    where conrelid = 'public.group_rotation_match_uuid_results'::regclass
        and conname = 'group_rotation_match_uuid_results_assignment_fkey'
        and contype = 'f' and convalidated
), 'UUID results reference the immutable match assignment');

select ok(has_table_privilege(
    'authenticated', 'public.group_rotation_match_uuid_snapshots', 'select'
), 'authenticated can read owned UUID match snapshots');
select ok(not has_table_privilege(
    'authenticated', 'public.group_rotation_match_uuid_snapshots', 'insert,update,delete'
), 'authenticated cannot mutate UUID match snapshots directly');
select ok(not has_table_privilege(
    'anon', 'public.group_rotation_match_uuid_snapshots', 'select'
), 'anon cannot read UUID match snapshots');
select ok(has_function_privilege(
    'authenticated',
    'public.write_match_snapshot_v4(uuid,jsonb,jsonb,jsonb,integer,uuid)',
    'execute'
), 'authenticated can execute the UUID draft writer');
select ok(has_function_privilege(
    'authenticated',
    'public.finalize_match_snapshot_v4(uuid,uuid,jsonb,integer,uuid)',
    'execute'
), 'authenticated can execute the UUID finalizer');
select ok(not has_function_privilege(
    'anon',
    'public.write_match_snapshot_v4(uuid,jsonb,jsonb,jsonb,integer,uuid)',
    'execute'
), 'anon cannot execute the UUID draft writer');
select ok(not has_function_privilege(
    'anon',
    'public.finalize_match_snapshot_v4(uuid,uuid,jsonb,integer,uuid)',
    'execute'
), 'anon cannot execute the UUID finalizer');

insert into auth.users (id, email)
values
    ('e2b00000-0000-0000-0000-000000000001', 'phase3d2b-owner@example.test'),
    ('e2b00000-0000-0000-0000-000000000002', 'phase3d2b-other@example.test');

insert into public.tournaments (
    id, owner_id, name, format, group_count, status, revision
)
values
    ('e2b10000-0000-0000-0000-000000000001', 'e2b00000-0000-0000-0000-000000000001', 'UUID Match Tournament', 'group_rotation', 3, 'draft', 1),
    ('e2b10000-0000-0000-0000-000000000002', 'e2b00000-0000-0000-0000-000000000001', 'UUID Match Other Tournament', 'group_rotation', 3, 'draft', 1),
    ('e2b10000-0000-0000-0000-000000000003', 'e2b00000-0000-0000-0000-000000000002', 'UUID Match Other Owner', 'group_rotation', 3, 'draft', 1),
    ('e2b10000-0000-0000-0000-000000000004', 'e2b00000-0000-0000-0000-000000000001', 'UUID Match Standard', 'standard', null, 'draft', 1);

insert into public.tournament_group_pairings (tournament_id, first_group, second_group)
values
    ('e2b10000-0000-0000-0000-000000000001', 'A', 'B'),
    ('e2b10000-0000-0000-0000-000000000001', 'A', 'C'),
    ('e2b10000-0000-0000-0000-000000000002', 'A', 'B'),
    ('e2b10000-0000-0000-0000-000000000003', 'A', 'B');

insert into public.tournament_team_identities (
    tournament_id, team_uuid, display_name, normalized_name
)
values
    ('e2b10000-0000-0000-0000-000000000001', 'e2b20000-0000-0000-0000-000000000001', 'Alpha', 'alpha'),
    ('e2b10000-0000-0000-0000-000000000001', 'e2b20000-0000-0000-0000-000000000002', 'Bravo', 'bravo'),
    ('e2b10000-0000-0000-0000-000000000001', 'e2b20000-0000-0000-0000-000000000003', 'Charlie', 'charlie'),
    ('e2b10000-0000-0000-0000-000000000001', 'e2b20000-0000-0000-0000-000000000004', 'Delta', 'delta'),
    ('e2b10000-0000-0000-0000-000000000002', 'e2b20000-0000-0000-0000-000000000011', 'Other Tournament Team', 'other tournament team');

insert into public.matches (
    id, tournament_id, match_number, status, revision, group_pairing_key
)
values
    ('e2b30000-0000-0000-0000-000000000001', 'e2b10000-0000-0000-0000-000000000001', 1, 'draft', 1, 'A:B'),
    ('e2b30000-0000-0000-0000-000000000002', 'e2b10000-0000-0000-0000-000000000001', 2, 'draft', 1, 'A:C'),
    ('e2b30000-0000-0000-0000-000000000003', 'e2b10000-0000-0000-0000-000000000002', 1, 'draft', 1, 'A:B'),
    ('e2b30000-0000-0000-0000-000000000004', 'e2b10000-0000-0000-0000-000000000003', 1, 'draft', 1, 'A:B');

set local role authenticated;
set local request.jwt.claim.sub = 'e2b00000-0000-0000-0000-000000000001';

select is((
    select outcome
    from public.write_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        '{"id":"e2b30000-0000-0000-0000-000000000001","tournament_id":"e2b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
        '[{"lobby_slot_number":2,"team_uuid":"e2b20000-0000-0000-0000-000000000001"},{"lobby_slot_number":9,"team_uuid":"e2b20000-0000-0000-0000-000000000002"}]'::jsonb,
        '[{"team_uuid":"e2b20000-0000-0000-0000-000000000001","participation_status":"PARTICIPATED","placement":null,"kills":4,"point_adjustment":0},{"team_uuid":"e2b20000-0000-0000-0000-000000000002","participation_status":"NO_SHOW","placement":null,"kills":0,"point_adjustment":0}]'::jsonb,
        1,
        'e2b40000-0000-0000-0000-000000000001'
    )
), 'success', 'sparse UUID draft snapshot is saved');
select is((
    select revision
    from public.write_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        '{"id":"e2b30000-0000-0000-0000-000000000001","tournament_id":"e2b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
        '[{"lobby_slot_number":2,"team_uuid":"e2b20000-0000-0000-0000-000000000001"},{"lobby_slot_number":9,"team_uuid":"e2b20000-0000-0000-0000-000000000002"}]'::jsonb,
        '[{"team_uuid":"e2b20000-0000-0000-0000-000000000001","participation_status":"PARTICIPATED","placement":null,"kills":4,"point_adjustment":0},{"team_uuid":"e2b20000-0000-0000-0000-000000000002","participation_status":"NO_SHOW","placement":null,"kills":0,"point_adjustment":0}]'::jsonb,
        1,
        'e2b40000-0000-0000-0000-000000000001'
    )
), 2, 'successful draft returns the new tournament revision');
select is((
    select count(*) from public.group_rotation_match_uuid_assignments
    where match_id = 'e2b30000-0000-0000-0000-000000000001'
), 2::bigint, 'sparse draft stores exactly the submitted UUID assignments');
select is((
    select count(*) from public.group_rotation_match_uuid_results
    where match_id = 'e2b30000-0000-0000-0000-000000000001'
), 2::bigint, 'draft stores both participated and no-show rows');
select is((
    select placement from public.group_rotation_match_uuid_results
    where match_id = 'e2b30000-0000-0000-0000-000000000001'
        and team_uuid = 'e2b20000-0000-0000-0000-000000000001'
), null::integer, 'draft preserves a null placement');
select is((
    select lobby_slot_number from public.group_rotation_match_uuid_assignments
    where match_id = 'e2b30000-0000-0000-0000-000000000001'
        and team_uuid = 'e2b20000-0000-0000-0000-000000000002'
), 9, 'local lobby position is stored independently of UUID identity');

select is((
    select outcome
    from public.write_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        '{"id":"e2b30000-0000-0000-0000-000000000001","tournament_id":"e2b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
        '[{"lobby_slot_number":2,"team_uuid":"e2b20000-0000-0000-0000-000000000001"},{"lobby_slot_number":9,"team_uuid":"e2b20000-0000-0000-0000-000000000002"}]'::jsonb,
        '[{"team_uuid":"e2b20000-0000-0000-0000-000000000001","participation_status":"PARTICIPATED","placement":null,"kills":4,"point_adjustment":0},{"team_uuid":"e2b20000-0000-0000-0000-000000000002","participation_status":"NO_SHOW","placement":null,"kills":0,"point_adjustment":0}]'::jsonb,
        1,
        'e2b40000-0000-0000-0000-000000000001'
    )
), 'success', 'repeating the same request is idempotent');
select is((
    select revision from public.tournaments
    where id = 'e2b10000-0000-0000-0000-000000000001'
), 2, 'idempotent retry does not advance revision twice');

select is((
    select outcome
    from public.write_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        '{"id":"e2b30000-0000-0000-0000-000000000001","tournament_id":"e2b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
        '[{"lobby_slot_number":2,"team_uuid":"e2b20000-0000-0000-0000-000000000001"},{"lobby_slot_number":9,"team_uuid":"e2b20000-0000-0000-0000-000000000002"}]'::jsonb,
        '[]'::jsonb,
        1,
        'e2b40000-0000-0000-0000-000000000002'
    )
), 'stale_write', 'stale draft revision is rejected');
select is((
    select count(*) from public.group_rotation_match_uuid_results
    where match_id = 'e2b30000-0000-0000-0000-000000000001'
), 2::bigint, 'stale draft leaves existing results unchanged');

select is((
    select outcome
    from public.write_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        '{"id":"e2b30000-0000-0000-0000-000000000001","tournament_id":"e2b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
        '[{"lobby_slot_number":2,"team_uuid":"e2b20000-0000-0000-0000-000000000001"},{"lobby_slot_number":9,"team_uuid":"e2b20000-0000-0000-0000-000000000002"}]'::jsonb,
        '[{"team_uuid":"e2b20000-0000-0000-0000-000000000001","participation_status":"PARTICIPATED","placement":1,"kills":5,"point_adjustment":1},{"team_uuid":"e2b20000-0000-0000-0000-000000000002","participation_status":"NO_SHOW","placement":null,"kills":0,"point_adjustment":0}]'::jsonb,
        2,
        'e2b40000-0000-0000-0000-000000000003'
    )
), 'success', 'draft results can be updated without changing identity');
select is((
    select revision from public.tournaments
    where id = 'e2b10000-0000-0000-0000-000000000001'
), 3, 'draft update advances the tournament revision once');

select is((
    select outcome
    from public.write_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        '{"id":"e2b30000-0000-0000-0000-000000000001","tournament_id":"e2b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
        '[{"lobby_slot_number":2,"team_uuid":"e2b20000-0000-0000-0000-000000000003"},{"lobby_slot_number":9,"team_uuid":"e2b20000-0000-0000-0000-000000000002"}]'::jsonb,
        '[{"team_uuid":"e2b20000-0000-0000-0000-000000000003","participation_status":"PARTICIPATED","placement":1,"kills":5,"point_adjustment":1},{"team_uuid":"e2b20000-0000-0000-0000-000000000002","participation_status":"NO_SHOW","placement":null,"kills":0,"point_adjustment":0}]'::jsonb,
        3,
        'e2b40000-0000-0000-0000-000000000004'
    )
), 'success', 'draft match lobby identity can be replaced before finalization');
select is((
    select team_uuid from public.group_rotation_match_uuid_assignments
    where match_id = 'e2b30000-0000-0000-0000-000000000001'
        and lobby_slot_number = 2
), 'e2b20000-0000-0000-0000-000000000003'::uuid, 'draft replacement stores the incoming UUID');

select is((
    select outcome
    from public.write_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        '{"id":"e2b30000-0000-0000-0000-000000000002","tournament_id":"e2b10000-0000-0000-0000-000000000001","pairing_key":"A:C"}'::jsonb,
        '[{"lobby_slot_number":7,"team_uuid":"e2b20000-0000-0000-0000-000000000003"}]'::jsonb,
        '[{"team_uuid":"e2b20000-0000-0000-0000-000000000003","participation_status":"PARTICIPATED","placement":null,"kills":0,"point_adjustment":0}]'::jsonb,
        4,
        'e2b40000-0000-0000-0000-000000000005'
    )
), 'success', 'a second match is persisted independently');
select is((
    select count(*) from public.group_rotation_match_uuid_results
    where match_id = 'e2b30000-0000-0000-0000-000000000001'
), 2::bigint, 'writing another match does not delete the first match results');

select is((
    select outcome
    from public.finalize_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        'e2b30000-0000-0000-0000-000000000001',
        '[{"team_uuid":"e2b20000-0000-0000-0000-000000000003","participation_status":"PARTICIPATED","placement":1,"kills":5,"point_adjustment":1},{"team_uuid":"e2b20000-0000-0000-0000-000000000002","participation_status":"NO_SHOW","placement":null,"kills":0,"point_adjustment":0}]'::jsonb,
        5,
        'e2b40000-0000-0000-0000-000000000006'
    )
), 'success', 'complete UUID result snapshot finalizes');
select is((
    select status from public.group_rotation_match_uuid_snapshots
    where match_id = 'e2b30000-0000-0000-0000-000000000001'
), 'finalized', 'UUID match snapshot is finalized atomically');
select is((
    select status from public.matches
    where id = 'e2b30000-0000-0000-0000-000000000001'
), 'finalized', 'existing match state is finalized with the UUID snapshot');
select is((
    select outcome
    from public.finalize_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        'e2b30000-0000-0000-0000-000000000001',
        '[{"team_uuid":"e2b20000-0000-0000-0000-000000000001","participation_status":"PARTICIPATED","placement":1,"kills":5,"point_adjustment":1},{"team_uuid":"e2b20000-0000-0000-0000-000000000002","participation_status":"NO_SHOW","placement":null,"kills":0,"point_adjustment":0}]'::jsonb,
        6,
        'e2b40000-0000-0000-0000-000000000007'
    )
), 'already_finalized', 'repeat finalization is rejected');
select is((
    select outcome
    from public.write_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        '{"id":"e2b30000-0000-0000-0000-000000000001","tournament_id":"e2b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
        '[{"lobby_slot_number":2,"team_uuid":"e2b20000-0000-0000-0000-000000000001"},{"lobby_slot_number":9,"team_uuid":"e2b20000-0000-0000-0000-000000000002"}]'::jsonb,
        '[{"team_uuid":"e2b20000-0000-0000-0000-000000000001","participation_status":"PARTICIPATED","placement":null,"kills":4,"point_adjustment":0},{"team_uuid":"e2b20000-0000-0000-0000-000000000002","participation_status":"NO_SHOW","placement":null,"kills":0,"point_adjustment":0}]'::jsonb,
        1,
        'e2b40000-0000-0000-0000-000000000001'
    )
), 'success', 'a successful draft request remains safely retryable after later finalization');

select is((
    select outcome
    from public.write_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        '{"id":"e2b30000-0000-0000-0000-000000000001","tournament_id":"e2b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
        '[{"lobby_slot_number":2,"team_uuid":"e2b20000-0000-0000-0000-000000000001"},{"lobby_slot_number":9,"team_uuid":"e2b20000-0000-0000-0000-000000000002"}]'::jsonb,
        '[]'::jsonb,
        5,
        'e2b40000-0000-0000-0000-000000000008'
    )
), 'already_finalized', 'finalized UUID match cannot accept draft writes');

select is((
    select outcome
    from public.finalize_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        'e2b30000-0000-0000-0000-000000000002',
        '[{"team_uuid":"e2b20000-0000-0000-0000-000000000003","participation_status":"PARTICIPATED","placement":null,"kills":2,"point_adjustment":0}]'::jsonb,
        6,
        'e2b40000-0000-0000-0000-000000000009'
    )
), 'validation_failure', 'finalization rejects a participated row without placement');
select is((
    select status from public.group_rotation_match_uuid_snapshots
    where match_id = 'e2b30000-0000-0000-0000-000000000002'
), 'draft', 'failed finalization preserves draft state');

select is((
    select outcome
    from public.write_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        '{"id":"e2b30000-0000-0000-0000-000000000002","tournament_id":"e2b10000-0000-0000-0000-000000000001","pairing_key":"A:C"}'::jsonb,
        '[{"lobby_slot_number":7,"team_uuid":"e2b20000-0000-0000-0000-000000000003"}]'::jsonb,
        '[{"team_uuid":"e2b20000-0000-0000-0000-000000000003","participation_status":"PARTICIPATED","placement":1,"kills":2,"point_adjustment":0}]'::jsonb,
        4,
        'e2b40000-0000-0000-0000-000000000010'
    )
), 'stale_write', 'the draft match revision is fenced after another match finalizes');

select is((
    select outcome
    from public.write_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        '{"id":"e2b30000-0000-0000-0000-000000000001","tournament_id":"e2b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
        '[{"lobby_slot_number":2,"team_uuid":"e2b20000-0000-0000-0000-000000000001"},{"lobby_slot_number":9,"team_uuid":"e2b20000-0000-0000-0000-000000000002"}]'::jsonb,
        '[]'::jsonb,
        5,
        'e2b40000-0000-0000-0000-000000000011'
    )
), 'already_finalized', 'finalized match remains protected after unrelated draft validation');

select is((
    select outcome
    from public.write_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        '{"id":"e2b30000-0000-0000-0000-000000000002","tournament_id":"e2b10000-0000-0000-0000-000000000001","pairing_key":"A:C"}'::jsonb,
        '[{"lobby_slot_number":7,"team_uuid":"e2b20000-0000-0000-0000-000000000011"}]'::jsonb,
        '[]'::jsonb,
        6,
        'e2b40000-0000-0000-0000-000000000012'
    )
), 'validation_failure', 'a UUID from another tournament is rejected');
select is((
    select count(*) from public.group_rotation_match_uuid_assignments
    where match_id = 'e2b30000-0000-0000-0000-000000000002'
), 1::bigint, 'foreign UUID rejection does not alter saved assignments');

select is((
    select outcome
    from public.write_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        '{"id":"e2b30000-0000-0000-0000-000000000002","tournament_id":"e2b10000-0000-0000-0000-000000000001","pairing_key":"A:C"}'::jsonb,
        '[{"lobby_slot_number":7,"team_uuid":"e2b20000-0000-0000-0000-000000000003"},{"lobby_slot_number":8,"team_uuid":"e2b20000-0000-0000-0000-000000000003"}]'::jsonb,
        '[]'::jsonb,
        6,
        'e2b40000-0000-0000-0000-000000000015'
    )
), 'validation_failure', 'duplicate UUIDs within one match identity are rejected');

select is((
    select outcome
    from public.write_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        '{"id":"e2b30000-0000-0000-0000-000000000002","tournament_id":"e2b10000-0000-0000-0000-000000000001","pairing_key":"A:C"}'::jsonb,
        '[{"lobby_slot_number":7,"team_uuid":"e2b20000-0000-0000-0000-000000000003"}]'::jsonb,
        '[{"team_uuid":"e2b20000-0000-0000-0000-000000000003","participation_status":"PARTICIPATED","placement":null,"kills":-1,"point_adjustment":0}]'::jsonb,
        6,
        'e2b40000-0000-0000-0000-000000000016'
    )
), 'validation_failure', 'negative draft kills are rejected');

select is((
    select outcome
    from public.write_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        '{"id":"e2b30000-0000-0000-0000-000000000002","tournament_id":"e2b10000-0000-0000-0000-000000000001","pairing_key":"A:C"}'::jsonb,
        '[{"lobby_slot_number":7,"team_uuid":"e2b20000-0000-0000-0000-000000000003"}]'::jsonb,
        '[{"team_uuid":"e2b20000-0000-0000-0000-000000000003","participation_status":"UNKNOWN","placement":null,"kills":0,"point_adjustment":0}]'::jsonb,
        6,
        'e2b40000-0000-0000-0000-000000000017'
    )
), 'validation_failure', 'unknown draft participation status is rejected');

select is((
    select outcome
    from public.write_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        '{"id":"e2b30000-0000-0000-0000-000000000002","tournament_id":"e2b10000-0000-0000-0000-000000000001","pairing_key":"A:C"}'::jsonb,
        '[{"lobby_slot_number":7,"team_uuid":"e2b20000-0000-0000-0000-000000000003"}]'::jsonb,
        '{}'::jsonb,
        6,
        'e2b40000-0000-0000-0000-000000000018'
    )
), 'validation_failure', 'non-array draft results fail closed');

select is((
    select outcome
    from public.finalize_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        'e2b30000-0000-0000-0000-000000000002',
        '[{"team_uuid":"e2b20000-0000-0000-0000-000000000003","participation_status":"PARTICIPATED","placement":13,"kills":2,"point_adjustment":0}]'::jsonb,
        6,
        'e2b40000-0000-0000-0000-000000000019'
    )
), 'validation_failure', 'finalization rejects placement thirteen');
select is((
    select count(*) from public.group_rotation_match_uuid_results
    where match_id = 'e2b30000-0000-0000-0000-000000000002'
), 1::bigint, 'invalid UUID match writes leave the other match results unchanged');

select is((
    select outcome
    from public.write_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        '{"id":"e2b30000-0000-0000-0000-000000000002","tournament_id":"e2b10000-0000-0000-0000-000000000001","pairing_key":"A:C"}'::jsonb,
        '[{"lobby_slot_number":7,"team_uuid":"e2b20000-0000-0000-0000-000000000003"},{"lobby_slot_number":7,"team_uuid":"e2b20000-0000-0000-0000-000000000004"}]'::jsonb,
        '[]'::jsonb,
        4,
        'e2b40000-0000-0000-0000-000000000013'
    )
), 'stale_write', 'stale revision is reported before a duplicate identity mutation');

set local request.jwt.claim.sub = 'e2b00000-0000-0000-0000-000000000002';
select is((
    select outcome
    from public.write_match_snapshot_v4(
        'e2b10000-0000-0000-0000-000000000001',
        '{"id":"e2b30000-0000-0000-0000-000000000002","tournament_id":"e2b10000-0000-0000-0000-000000000001","pairing_key":"A:C"}'::jsonb,
        '[{"lobby_slot_number":7,"team_uuid":"e2b20000-0000-0000-0000-000000000003"}]'::jsonb,
        '[]'::jsonb,
        5,
        'e2b40000-0000-0000-0000-000000000014'
    )
), 'unauthorized', 'a different authenticated owner cannot write the tournament');
set local request.jwt.claim.sub = 'e2b00000-0000-0000-0000-000000000001';

set local role anon;
select throws_ok($$select count(*) from public.group_rotation_match_uuid_results$$, '42501', null, 'anonymous cannot read UUID match results');
set local role authenticated;

select ok(to_regprocedure('public.write_match_snapshot(uuid,jsonb,jsonb,integer)') is not null, 'legacy Standard match writer remains present');
select ok(to_regprocedure('public.write_match_snapshot_v2(uuid,jsonb,jsonb,integer)') is not null, 'legacy numeric Group Rotation match writer remains present');
select ok(to_regprocedure('public.finalize_match_snapshot_v2(uuid,jsonb,jsonb,integer)') is not null, 'legacy numeric Group Rotation finalizer remains present');
select is((
    select count(*) from public.group_rotation_match_uuid_results
    where match_id = 'e2b30000-0000-0000-0000-000000000001'
), 2::bigint, 'finalized UUID results remain isolated from legacy tables');

select * from finish();
rollback;
