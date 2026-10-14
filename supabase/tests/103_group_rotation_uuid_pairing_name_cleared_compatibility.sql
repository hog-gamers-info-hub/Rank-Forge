begin;

select no_plan();

select has_column(
    'public',
    'tournament_group_pairing_team_assignments',
    'name_cleared',
    'pairing-specific cleared-name state exists'
);

select ok(
    to_regprocedure(
        'public.write_group_rotation_uuid_identity_snapshot_v4(uuid,jsonb,jsonb,integer,uuid)'
    ) is not null,
    'UUID identity RPC retains its existing signature'
);

insert into auth.users (id, email)
values ('e3100000-0000-0000-0000-000000000001', 'priority3-name-cleared@example.test');

insert into public.tournaments (
    id, owner_id, name, format, group_count, status, revision
)
values (
    'e3200000-0000-0000-0000-000000000001',
    'e3100000-0000-0000-0000-000000000001',
    'Priority 3 name-cleared compatibility',
    'group_rotation',
    3,
    'draft',
    1
);

insert into public.tournament_group_pairings (
    tournament_id, first_group, second_group
)
values
    ('e3200000-0000-0000-0000-000000000001', 'A', 'B'),
    ('e3200000-0000-0000-0000-000000000001', 'A', 'C');

insert into public.tournament_team_identities (
    tournament_id, team_uuid, display_name, normalized_name
)
values (
    'e3200000-0000-0000-0000-000000000001',
    'e3300000-0000-0000-0000-000000000001',
    'Retained Team',
    'retained team'
);

insert into public.tournament_group_pairing_team_assignments (
    tournament_id, pairing_key, lobby_slot_number, team_uuid, name_cleared
)
values
    (
        'e3200000-0000-0000-0000-000000000001',
        'A:B',
        1,
        'e3300000-0000-0000-0000-000000000001',
        true
    ),
    (
        'e3200000-0000-0000-0000-000000000001',
        'A:C',
        1,
        'e3300000-0000-0000-0000-000000000001',
        false
    );

set local role authenticated;
set local request.jwt.claim.sub = 'e3100000-0000-0000-0000-000000000001';

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3200000-0000-0000-0000-000000000001',
         '[]'::jsonb,
         '[{"pairing_key":"A:B","assignments":[{"lobby_slot_number":1,"team_uuid":"e3300000-0000-0000-0000-000000000001"}]}]'::jsonb,
         1,
         'e3400000-0000-0000-0000-000000000001'
     )),
    'success',
    'omitted name_cleared preserves an existing true flag for the same assignment'
);

select is(
    (select name_cleared
     from public.tournament_group_pairing_team_assignments
     where tournament_id = 'e3200000-0000-0000-0000-000000000001'
       and pairing_key = 'A:B'
       and lobby_slot_number = 1),
    true,
    'omitted name_cleared does not reset the retained cleared state'
);

select is(
    (select name_cleared
     from public.tournament_group_pairing_team_assignments
     where tournament_id = 'e3200000-0000-0000-0000-000000000001'
       and pairing_key = 'A:C'
       and lobby_slot_number = 1),
    false,
    'an omitted-field update leaves another pairing unchanged'
);

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3200000-0000-0000-0000-000000000001',
         '[]'::jsonb,
         '[{"pairing_key":"A:B","assignments":[{"lobby_slot_number":1,"team_uuid":"e3300000-0000-0000-0000-000000000001","name_cleared":false}]}]'::jsonb,
         2,
         'e3400000-0000-0000-0000-000000000002'
     )),
    'success',
    'explicit false remains an intentional current-client clear'
);

select is(
    (select name_cleared
     from public.tournament_group_pairing_team_assignments
     where tournament_id = 'e3200000-0000-0000-0000-000000000001'
       and pairing_key = 'A:B'
       and lobby_slot_number = 1),
    false,
    'explicit false clears only the selected pairing assignment flag'
);

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e3200000-0000-0000-0000-000000000001',
         '[]'::jsonb,
         '[{"pairing_key":"A:B","assignments":[]}]'::jsonb,
         3,
         'e3400000-0000-0000-0000-000000000003'
     )),
    'success',
    'an explicitly empty pairing replacement is accepted'
);

select is(
    (select count(*)
     from public.tournament_group_pairing_team_assignments
     where tournament_id = 'e3200000-0000-0000-0000-000000000001'
       and pairing_key = 'A:B'),
    0::bigint,
    'empty replacement removes only the selected pairing assignments'
);

select is(
    (select count(*)
     from public.tournament_group_pairing_team_assignments
     where tournament_id = 'e3200000-0000-0000-0000-000000000001'
       and pairing_key = 'A:C'),
    1::bigint,
    'empty replacement preserves another pairing assignment'
);

select is(
    (select team_uuid
     from public.tournament_group_pairing_team_assignments
     where tournament_id = 'e3200000-0000-0000-0000-000000000001'
       and pairing_key = 'A:C'
       and lobby_slot_number = 1),
    'e3300000-0000-0000-0000-000000000001'::uuid,
    'empty replacement never deletes the retained global UUID identity'
);

select is(
    (select count(*)
     from public.tournament_team_identities
     where tournament_id = 'e3200000-0000-0000-0000-000000000001'),
    1::bigint,
    'pairing replacement leaves the registered identity intact'
);

select is(
    (select revision
     from public.tournaments
     where id = 'e3200000-0000-0000-0000-000000000001'),
    4,
    'each accepted compatibility operation advances the tournament revision once'
);

rollback;

