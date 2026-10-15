begin;

select no_plan();

select has_table(
    'public',
    'group_rotation_uuid_identity_write_requests',
    'UUID identity sync has a dedicated idempotency ledger'
);
select has_column(
    'public',
    'group_rotation_uuid_identity_write_requests',
    'request_id',
    'identity sync ledger has request_id'
);
select has_column(
    'public',
    'group_rotation_uuid_identity_write_requests',
    'tournament_id',
    'identity sync ledger has tournament_id'
);
select ok(
    (select relrowsecurity
     from pg_class
     where oid = 'public.group_rotation_uuid_identity_write_requests'::regclass),
    'identity sync ledger has RLS enabled'
);
select ok(
    to_regprocedure(
        'public.write_group_rotation_uuid_identity_snapshot_v4(uuid,jsonb,jsonb,integer,uuid)'
    ) is not null,
    'UUID identity sync RPC exists with the approved contract'
);
select ok(
    has_function_privilege(
        'authenticated',
        'public.write_group_rotation_uuid_identity_snapshot_v4(uuid,jsonb,jsonb,integer,uuid)',
        'execute'
    ),
    'authenticated can execute UUID identity sync'
);
select ok(
    not has_function_privilege(
        'anon',
        'public.write_group_rotation_uuid_identity_snapshot_v4(uuid,jsonb,jsonb,integer,uuid)',
        'execute'
    ),
    'anonymous users cannot execute UUID identity sync'
);
select ok(
    not has_table_privilege(
        'authenticated',
        'public.group_rotation_uuid_identity_write_requests',
        'select'
    ),
    'authenticated cannot read the private identity sync ledger'
);

insert into auth.users (id, email)
values
    ('e3b10000-0000-0000-0000-000000000001', 'phase3d3b1-owner@example.test'),
    ('e3b10000-0000-0000-0000-000000000002', 'phase3d3b1-other@example.test');

insert into public.tournaments (
    id, owner_id, name, format, group_count, status, revision
)
values
    (
        'e3b20000-0000-0000-0000-000000000001',
        'e3b10000-0000-0000-0000-000000000001',
        'UUID identity sync tournament',
        'group_rotation',
        3,
        'draft',
        1
    ),
    (
        'e3b20000-0000-0000-0000-000000000002',
        'e3b10000-0000-0000-0000-000000000001',
        'UUID identity sync second tournament',
        'group_rotation',
        3,
        'draft',
        1
    ),
    (
        'e3b20000-0000-0000-0000-000000000003',
        'e3b10000-0000-0000-0000-000000000001',
        'UUID identity sync Standard compatibility',
        'standard',
        null,
        'draft',
        1
    );

insert into public.tournament_group_pairings (tournament_id, first_group, second_group)
values
    ('e3b20000-0000-0000-0000-000000000001', 'A', 'B'),
    ('e3b20000-0000-0000-0000-000000000001', 'A', 'C'),
    ('e3b20000-0000-0000-0000-000000000002', 'A', 'B');

insert into public.tournament_team_identities (
    tournament_id, team_uuid, display_name, normalized_name
)
values
    ('e3b20000-0000-0000-0000-000000000001', 'e3b30000-0000-0000-0000-000000000001', 'Alpha', 'alpha'),
    ('e3b20000-0000-0000-0000-000000000001', 'e3b30000-0000-0000-0000-000000000002', 'Bravo', 'bravo'),
    ('e3b20000-0000-0000-0000-000000000001', 'e3b30000-0000-0000-0000-000000000003', 'Charlie', 'charlie'),
    ('e3b20000-0000-0000-0000-000000000001', 'e3b30000-0000-0000-0000-000000000004', 'Delta', 'delta'),
    ('e3b20000-0000-0000-0000-000000000002', 'e3b30000-0000-0000-0000-000000000011', 'Foreign Team', 'foreign team');

insert into public.tournament_group_pairing_team_assignments (
    tournament_id, pairing_key, lobby_slot_number, team_uuid
)
values
    (
        'e3b20000-0000-0000-0000-000000000001',
        'A:B',
        1,
        'e3b30000-0000-0000-0000-000000000001'
    ),
    (
        'e3b20000-0000-0000-0000-000000000001',
        'A:B',
        2,
        'e3b30000-0000-0000-0000-000000000002'
    ),
    (
        'e3b20000-0000-0000-0000-000000000001',
        'A:C',
        1,
        'e3b30000-0000-0000-0000-000000000003'
    );

insert into public.matches (
    id, tournament_id, match_number, status, revision, group_pairing_key
)
values (
    'e3b40000-0000-0000-0000-000000000001',
    'e3b20000-0000-0000-0000-000000000001',
    1,
    'draft',
    1,
    'A:B'
);

set local role authenticated;

set local request.jwt.claim.sub = '';
select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3b20000-0000-0000-0000-000000000001',
         '[]'::jsonb,
         '[]'::jsonb,
         1,
         'e3b50000-0000-0000-0000-000000000001'
     )),
    'authentication_required',
    'missing auth identity is rejected'
);

set local request.jwt.claim.sub = 'e3b10000-0000-0000-0000-000000000001';

select is(
    (select outcome
     from public.write_match_snapshot_v4(
         'e3b20000-0000-0000-0000-000000000001',
         '{"id":"e3b40000-0000-0000-0000-000000000001","tournament_id":"e3b20000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
         '[{"lobby_slot_number":1,"team_uuid":"e3b30000-0000-0000-0000-000000000001"},{"lobby_slot_number":2,"team_uuid":"e3b30000-0000-0000-0000-000000000002"}]'::jsonb,
         '[{"team_uuid":"e3b30000-0000-0000-0000-000000000001","participation_status":"PARTICIPATED","placement":null,"kills":4,"point_adjustment":0},{"team_uuid":"e3b30000-0000-0000-0000-000000000002","participation_status":"NO_SHOW","placement":null,"kills":0,"point_adjustment":0}]'::jsonb,
         1,
         'e3b50000-0000-0000-0000-000000000002'
     )),
    'success',
    'existing UUID match snapshot is available for historical-preservation coverage'
);

select is(
    (select revision from public.tournaments
     where id = 'e3b20000-0000-0000-0000-000000000001'),
    2,
    'the historical UUID match write advances the parent revision once'
);

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3b20000-0000-0000-0000-000000000001',
         '[{"team_uuid":"e3b30000-0000-0000-0000-000000000001","display_name":"Alpha Prime","normalized_name":"alpha prime"},{"team_uuid":"e3b30000-0000-0000-0000-000000000002","display_name":"Bravo","normalized_name":"bravo"},{"team_uuid":"e3b30000-0000-0000-0000-000000000003","display_name":"Charlie","normalized_name":"charlie"},{"team_uuid":"e3b30000-0000-0000-0000-000000000004","display_name":"Delta","normalized_name":"delta"},{"team_uuid":"e3b30000-0000-0000-0000-000000000005","display_name":"Echo","normalized_name":"echo"}]'::jsonb,
         '[{"pairing_key":"A:B","assignments":[{"lobby_slot_number":3,"team_uuid":"e3b30000-0000-0000-0000-000000000003"},{"lobby_slot_number":7,"team_uuid":"e3b30000-0000-0000-0000-000000000005"}]}]'::jsonb,
         2,
         'e3b50000-0000-0000-0000-000000000003'
     )),
    'success',
    'owner can add a new UUID, rename by UUID, and replace one pairing atomically'
);
select is(
    (select revision from public.tournaments
     where id = 'e3b20000-0000-0000-0000-000000000001'),
    3,
    'identity and pairing synchronization advances the revision exactly once'
);
select is(
    (select display_name from public.tournament_team_identities
     where tournament_id = 'e3b20000-0000-0000-0000-000000000001'
       and team_uuid = 'e3b30000-0000-0000-0000-000000000001'),
    'Alpha Prime',
    'rename updates the current display name'
);
select is(
    (select count(*) from public.tournament_team_identities
     where tournament_id = 'e3b20000-0000-0000-0000-000000000001'),
    5::bigint,
    'new UUID identity is inserted without regenerating existing identities'
);
select is(
    (select team_uuid from public.tournament_group_pairing_team_assignments
     where tournament_id = 'e3b20000-0000-0000-0000-000000000001'
       and pairing_key = 'A:B' and lobby_slot_number = 3),
    'e3b30000-0000-0000-0000-000000000003'::uuid,
    'submitted pairing assignment preserves its exact UUID'
);
select is(
    (select team_uuid from public.tournament_group_pairing_team_assignments
     where tournament_id = 'e3b20000-0000-0000-0000-000000000001'
       and pairing_key = 'A:C' and lobby_slot_number = 1),
    'e3b30000-0000-0000-0000-000000000003'::uuid,
    'an identity can be reused in another pairing'
);
select is(
    (select count(*) from public.group_rotation_match_uuid_assignments
     where match_id = 'e3b40000-0000-0000-0000-000000000001'),
    2::bigint,
    'historical match assignments remain unchanged after pairing replacement'
);
select is(
    (select team_uuid from public.group_rotation_match_uuid_assignments
     where match_id = 'e3b40000-0000-0000-0000-000000000001'
       and lobby_slot_number = 1),
    'e3b30000-0000-0000-0000-000000000001'::uuid,
    'historical lobby one identity remains unchanged'
);
select is(
    (select count(*) from public.group_rotation_match_uuid_results
     where match_id = 'e3b40000-0000-0000-0000-000000000001'),
    2::bigint,
    'historical UUID match results remain unchanged'
);

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3b20000-0000-0000-0000-000000000001',
         '[{"team_uuid":"e3b30000-0000-0000-0000-000000000001","display_name":"Alpha Prime","normalized_name":"alpha prime"},{"team_uuid":"e3b30000-0000-0000-0000-000000000002","display_name":"Bravo","normalized_name":"bravo"},{"team_uuid":"e3b30000-0000-0000-0000-000000000003","display_name":"Charlie","normalized_name":"charlie"},{"team_uuid":"e3b30000-0000-0000-0000-000000000004","display_name":"Delta","normalized_name":"delta"},{"team_uuid":"e3b30000-0000-0000-0000-000000000005","display_name":"Echo","normalized_name":"echo"}]'::jsonb,
         '[{"pairing_key":"A:B","assignments":[{"lobby_slot_number":3,"team_uuid":"e3b30000-0000-0000-0000-000000000003"},{"lobby_slot_number":7,"team_uuid":"e3b30000-0000-0000-0000-000000000005"}]}]'::jsonb,
         2,
         'e3b50000-0000-0000-0000-000000000003'
     )),
    'success',
    'repeating the same request is idempotent'
);
select is(
    (select revision from public.tournaments
     where id = 'e3b20000-0000-0000-0000-000000000001'),
    3,
    'idempotent retry does not advance the revision twice'
);

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3b20000-0000-0000-0000-000000000001',
         '[{"team_uuid":"e3b30000-0000-0000-0000-000000000001","display_name":"Renamed Again","normalized_name":"renamed again"}]'::jsonb,
         '[]'::jsonb,
         2,
         'e3b50000-0000-0000-0000-000000000003'
     )),
    'validation_failure',
    'reusing a request UUID with a different payload is rejected'
);
select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3b20000-0000-0000-0000-000000000001',
         '[]'::jsonb,
         '[]'::jsonb,
         2,
         'e3b50000-0000-0000-0000-000000000004'
     )),
    'stale_write',
    'a stale concurrent identity write is rejected'
);

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3b20000-0000-0000-0000-000000000001',
         '[{"team_uuid":"e3b30000-0000-0000-0000-000000000001","display_name":"Alpha Prime","normalized_name":"alpha prime"},{"team_uuid":"e3b30000-0000-0000-0000-000000000002","display_name":" alpha   prime ","normalized_name":"alpha prime"}]'::jsonb,
         '[{"pairing_key":"A:B","assignments":[{"lobby_slot_number":4,"team_uuid":"e3b30000-0000-0000-0000-000000000004"}]}]'::jsonb,
         3,
         'e3b50000-0000-0000-0000-000000000005'
     )),
    'validation_failure',
    'duplicate normalized identities are rejected before pairing mutation'
);
select is(
    (select team_uuid from public.tournament_group_pairing_team_assignments
     where tournament_id = 'e3b20000-0000-0000-0000-000000000001'
       and pairing_key = 'A:B' and lobby_slot_number = 3),
    'e3b30000-0000-0000-0000-000000000003'::uuid,
    'duplicate identity rejection preserves the existing pairing assignment'
);
select is(
    (select revision from public.tournaments
     where id = 'e3b20000-0000-0000-0000-000000000001'),
    3,
    'duplicate identity rejection preserves the revision'
);

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3b20000-0000-0000-0000-000000000001',
         '[{"team_uuid":"e3b30000-0000-0000-0000-000000000011","display_name":"Foreign Team","normalized_name":"foreign team"}]'::jsonb,
         '[]'::jsonb,
         3,
         'e3b50000-0000-0000-0000-000000000006'
     )),
    'validation_failure',
    'a UUID registered to another tournament is rejected'
);
select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3b20000-0000-0000-0000-000000000001',
         '[]'::jsonb,
         '[{"pairing_key":"A:B","assignments":[{"lobby_slot_number":3,"team_uuid":"e3b30000-0000-0000-0000-000000000003"},{"lobby_slot_number":3,"team_uuid":"e3b30000-0000-0000-0000-000000000004"}]}]'::jsonb,
         3,
         'e3b50000-0000-0000-0000-000000000007'
     )),
    'validation_failure',
    'duplicate lobby assignment is rejected'
);
select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3b20000-0000-0000-0000-000000000001',
         '[]'::jsonb,
         '[{"pairing_key":"A:B","assignments":[{"lobby_slot_number":4,"team_uuid":"e3b30000-0000-0000-0000-000000000003"},{"lobby_slot_number":5,"team_uuid":"e3b30000-0000-0000-0000-000000000003"}]}]'::jsonb,
         3,
         'e3b50000-0000-0000-0000-000000000014'
     )),
    'validation_failure',
    'the same UUID cannot be reassigned twice within one pairing'
);
select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3b20000-0000-0000-0000-000000000001',
         '[]'::jsonb,
         '[{"pairing_key":"A:B","assignments":[{"lobby_slot_number":0,"team_uuid":"e3b30000-0000-0000-0000-000000000003"}]}]'::jsonb,
         3,
         'e3b50000-0000-0000-0000-000000000015'
     )),
    'validation_failure',
    'lobby slot zero is rejected'
);
select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3b20000-0000-0000-0000-000000000001',
         '[]'::jsonb,
         '[{"pairing_key":"A:B","assignments":[{"lobby_slot_number":13,"team_uuid":"e3b30000-0000-0000-0000-000000000003"}]}]'::jsonb,
         3,
         'e3b50000-0000-0000-0000-000000000016'
     )),
    'validation_failure',
    'lobby slot thirteen is rejected'
);
select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3b20000-0000-0000-0000-000000000001',
         '[]'::jsonb,
         '[{"pairing_key":"B:C","assignments":[]}]'::jsonb,
         3,
         'e3b50000-0000-0000-0000-000000000008'
     )),
    'validation_failure',
    'unselected pairing assignments are rejected'
);

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3b20000-0000-0000-0000-000000000001',
         '[]'::jsonb,
         '[{"pairing_key":"A:C","assignments":[{"lobby_slot_number":12,"team_uuid":"e3b30000-0000-0000-0000-000000000002"}]}]'::jsonb,
         3,
         'e3b50000-0000-0000-0000-000000000009'
     )),
    'success',
    'sparse pairing state is accepted'
);
select is(
    (select count(*) from public.tournament_group_pairing_team_assignments
     where tournament_id = 'e3b20000-0000-0000-0000-000000000001'
       and pairing_key = 'A:B'),
    2::bigint,
    'omitted pairing state remains untouched by a sparse update'
);
select is(
    (select count(*) from public.tournament_group_pairing_team_assignments
     where tournament_id = 'e3b20000-0000-0000-0000-000000000001'
       and pairing_key = 'A:C'),
    1::bigint,
    'sparse pairing stores exactly its submitted assignment'
);

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3b20000-0000-0000-0000-000000000001',
         '[]'::jsonb,
         '[{"pairing_key":"A:C","assignments":[]}]'::jsonb,
         4,
         'e3b50000-0000-0000-0000-000000000010'
     )),
    'success',
    'an explicitly empty pairing state is accepted'
);
select is(
    (select count(*) from public.tournament_group_pairing_team_assignments
     where tournament_id = 'e3b20000-0000-0000-0000-000000000001'
       and pairing_key = 'A:C'),
    0::bigint,
    'explicit empty pairing state clears only that pairing'
);

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3b20000-0000-0000-0000-000000000001',
         '[]'::jsonb,
         '[{"pairing_key":"A:B","assignments":[{"lobby_slot_number":10,"team_uuid":"e3b30000-0000-0000-0000-000000000004"},{"lobby_slot_number":11,"team_uuid":"e3b30000-0000-0000-0000-000000000003"}]}]'::jsonb,
         5,
         'e3b50000-0000-0000-0000-000000000011'
     )),
    'success',
    'a valid pairing reassignment is accepted for future matches'
);
select is(
    (select team_uuid from public.tournament_group_pairing_team_assignments
     where tournament_id = 'e3b20000-0000-0000-0000-000000000001'
       and pairing_key = 'A:B' and lobby_slot_number = 10),
    'e3b30000-0000-0000-0000-000000000004'::uuid,
    'valid pairing reassignment stores the requested UUID'
);
select is(
    (select team_uuid from public.group_rotation_match_uuid_assignments
     where match_id = 'e3b40000-0000-0000-0000-000000000001'
       and lobby_slot_number = 2),
    'e3b30000-0000-0000-0000-000000000002'::uuid,
    'future pairing reassignment does not rewrite historical match identity'
);

set local request.jwt.claim.sub = 'e3b10000-0000-0000-0000-000000000002';
select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3b20000-0000-0000-0000-000000000001',
         '[]'::jsonb,
         '[]'::jsonb,
         6,
         'e3b50000-0000-0000-0000-000000000012'
     )),
    'unauthorized',
    'a different authenticated owner cannot synchronize the tournament'
);
set local request.jwt.claim.sub = 'e3b10000-0000-0000-0000-000000000001';

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3b20000-0000-0000-0000-000000000003',
         '[]'::jsonb,
         '[]'::jsonb,
         1,
         'e3b50000-0000-0000-0000-000000000013'
     )),
    'missing_data',
    'Standard tournaments are not routed through the Group Rotation UUID RPC'
);
select is(
    (select revision from public.tournaments
     where id = 'e3b20000-0000-0000-0000-000000000003'),
    1,
    'Standard tournament revision remains unchanged'
);
select ok(
    to_regprocedure('public.write_tournament_snapshot(jsonb,jsonb,jsonb,integer)') is not null
        and to_regprocedure('public.write_tournament_snapshot_v2(jsonb,jsonb,jsonb,jsonb,integer)') is not null
        and to_regprocedure('public.write_match_snapshot_v4(uuid,jsonb,jsonb,jsonb,integer,uuid)') is not null,
    'Standard, legacy Group Rotation, and V4 match RPCs remain present'
);

select * from finish();
rollback;
