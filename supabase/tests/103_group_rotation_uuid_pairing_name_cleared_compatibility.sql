begin;

select plan(35);

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

insert into public.tournaments (
    id, owner_id, name, format, group_count, status, revision
)
values (
    'e5200000-0000-0000-0000-000000000001',
    'e3100000-0000-0000-0000-000000000001',
    'Priority 3 simultaneous names',
    'group_rotation',
    3,
    'draft',
    1
);

insert into public.tournament_group_pairings (
    tournament_id, first_group, second_group
)
values
    ('e5200000-0000-0000-0000-000000000001', 'A', 'B'),
    ('e5200000-0000-0000-0000-000000000001', 'A', 'C');

insert into public.tournament_team_identities (
    tournament_id, team_uuid, display_name, normalized_name
)
values
    (
        'e5200000-0000-0000-0000-000000000001',
        'e5300000-0000-0000-0000-000000000001',
        'Alpha',
        'alpha'
    ),
    (
        'e5200000-0000-0000-0000-000000000001',
        'e5300000-0000-0000-0000-000000000002',
        'Beta',
        'beta'
    );

insert into public.tournament_group_pairing_team_assignments (
    tournament_id, pairing_key, lobby_slot_number, team_uuid, name_cleared
)
values
    (
        'e5200000-0000-0000-0000-000000000001',
        'A:B',
        1,
        'e5300000-0000-0000-0000-000000000001',
        false
    ),
    (
        'e5200000-0000-0000-0000-000000000001',
        'A:C',
        1,
        'e5300000-0000-0000-0000-000000000002',
        false
    );

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e5200000-0000-0000-0000-000000000001',
         '[
            {"team_uuid":"e5300000-0000-0000-0000-000000000001","display_name":"Beta","normalized_name":"beta"},
            {"team_uuid":"e5300000-0000-0000-0000-000000000002","display_name":"Alpha","normalized_name":"alpha"}
          ]'::jsonb,
         '[
            {"pairing_key":"A:B","assignments":[{"lobby_slot_number":1,"team_uuid":"e5300000-0000-0000-0000-000000000001","name_cleared":false}]},
            {"pairing_key":"A:C","assignments":[{"lobby_slot_number":1,"team_uuid":"e5300000-0000-0000-0000-000000000002","name_cleared":false}]}
          ]'::jsonb,
         1,
         'e5400000-0000-0000-0000-000000000001'
     )),
    'success',
    'simultaneous active-name swaps succeed atomically'
);

select is(
    (select display_name
     from public.tournament_team_identities
     where tournament_id = 'e5200000-0000-0000-0000-000000000001'
       and team_uuid = 'e5300000-0000-0000-0000-000000000001'),
    'Beta',
    'the first UUID retains its identity while receiving the swapped name'
);

select is(
    (select display_name
     from public.tournament_team_identities
     where tournament_id = 'e5200000-0000-0000-0000-000000000001'
       and team_uuid = 'e5300000-0000-0000-0000-000000000002'),
    'Alpha',
    'the second UUID retains its identity while receiving the swapped name'
);

select is(
    (select team_uuid
     from public.tournament_group_pairing_team_assignments
     where tournament_id = 'e5200000-0000-0000-0000-000000000001'
       and pairing_key = 'A:B'
       and lobby_slot_number = 1),
    'e5300000-0000-0000-0000-000000000001'::uuid,
    'the first pairing assignment remains attached to its original UUID'
);

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e5200000-0000-0000-0000-000000000001',
         '[
            {"team_uuid":"e5300000-0000-0000-0000-000000000001","display_name":"Alpha","normalized_name":"alpha"},
            {"team_uuid":"e5300000-0000-0000-0000-000000000002","display_name":"","normalized_name":""}
          ]'::jsonb,
         '[
            {"pairing_key":"A:B","assignments":[{"lobby_slot_number":1,"team_uuid":"e5300000-0000-0000-0000-000000000001","name_cleared":false}]},
            {"pairing_key":"A:C","assignments":[{"lobby_slot_number":1,"team_uuid":"e5300000-0000-0000-0000-000000000002","name_cleared":true}]}
          ]'::jsonb,
         2,
         'e5400000-0000-0000-0000-000000000004'
     )),
    'success',
    'clearing an old active name allows that name to move to another UUID atomically'
);

select is(
    (select display_name
     from public.tournament_team_identities
     where tournament_id = 'e5200000-0000-0000-0000-000000000001'
       and team_uuid = 'e5300000-0000-0000-0000-000000000001'),
    'Alpha',
    'the receiving UUID owns the reused active name'
);

select is(
    (select display_name
     from public.tournament_team_identities
     where tournament_id = 'e5200000-0000-0000-0000-000000000001'
       and team_uuid = 'e5300000-0000-0000-0000-000000000002'),
    '',
    'the cleared UUID no longer owns the previous active name'
);

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e5200000-0000-0000-0000-000000000001',
         '[
            {"team_uuid":"e5300000-0000-0000-0000-000000000001","display_name":"Duplicate","normalized_name":"duplicate"},
            {"team_uuid":"e5300000-0000-0000-0000-000000000002","display_name":"duplicate","normalized_name":"duplicate"}
          ]'::jsonb,
         '[]'::jsonb,
         3,
         'e5400000-0000-0000-0000-000000000002'
     )),
    'validation_failure',
    'duplicate active names are rejected before any cloud data changes'
);

select is(
    (select display_name
     from public.tournament_team_identities
     where tournament_id = 'e5200000-0000-0000-0000-000000000001'
       and team_uuid = 'e5300000-0000-0000-0000-000000000001'),
    'Alpha',
    'a rejected duplicate-name save leaves the first UUID unchanged'
);

select is(
    (select display_name
     from public.tournament_team_identities
     where tournament_id = 'e5200000-0000-0000-0000-000000000001'
       and team_uuid = 'e5300000-0000-0000-0000-000000000002'),
    '',
    'a rejected duplicate-name save leaves the second UUID unchanged'
);

select is(
    (select revision
     from public.tournaments
     where id = 'e5200000-0000-0000-0000-000000000001'),
    3,
    'a rejected duplicate-name save leaves the revision unchanged'
);

select is(
    (select count(*)
     from public.tournament_group_pairing_team_assignments
     where tournament_id = 'e5200000-0000-0000-0000-000000000001'
       and pairing_key in ('A:B', 'A:C')),
    2::bigint,
    'a rejected duplicate-name save leaves all pairing assignments unchanged'
);

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e5200000-0000-0000-0000-000000000001',
         '[
            {"team_uuid":"e5300000-0000-0000-0000-000000000001","display_name":"Alpha","normalized_name":"alpha"},
            {"team_uuid":"e5300000-0000-0000-0000-000000000002","display_name":"","normalized_name":""}
          ]'::jsonb,
         '[
            {"pairing_key":"A:B","assignments":[{"lobby_slot_number":1,"team_uuid":"e5300000-0000-0000-0000-000000000001","name_cleared":false}]},
            {"pairing_key":"A:C","assignments":[{"lobby_slot_number":1,"team_uuid":"e5300000-0000-0000-0000-000000000002","name_cleared":false}]}
          ]'::jsonb,
         3,
         'e5400000-0000-0000-0000-000000000005'
     )),
    'validation_failure',
    'an uncleared assignment cannot reference a UUID whose final active name is blank'
);

select is(
    (select revision
     from public.tournaments
     where id = 'e5200000-0000-0000-0000-000000000001'),
    3,
    'blank final-name rejection leaves the revision unchanged'
);

select is(
    (select name_cleared
     from public.tournament_group_pairing_team_assignments
     where tournament_id = 'e5200000-0000-0000-0000-000000000001'
       and pairing_key = 'A:C'
       and lobby_slot_number = 1),
    true,
    'blank final-name rejection preserves the existing cleared assignment'
);

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e5200000-0000-0000-0000-000000000001',
         '[
            {"team_uuid":"e5300000-0000-0000-0000-000000000001","display_name":"","normalized_name":""},
            {"team_uuid":"e5300000-0000-0000-0000-000000000002","display_name":"","normalized_name":""}
          ]'::jsonb,
         '[
            {"pairing_key":"A:B","assignments":[{"lobby_slot_number":1,"team_uuid":"e5300000-0000-0000-0000-000000000001","name_cleared":true}]},
            {"pairing_key":"A:C","assignments":[{"lobby_slot_number":1,"team_uuid":"e5300000-0000-0000-0000-000000000002","name_cleared":true}]}
          ]'::jsonb,
         3,
         'e5400000-0000-0000-0000-000000000003'
     )),
    'success',
    'multiple cleared identities remain valid when their UUID assignments are retained'
);

select is(
    (select count(*)
     from public.tournament_team_identities
     where tournament_id = 'e5200000-0000-0000-0000-000000000001'
       and normalized_name = ''),
    2::bigint,
    'multiple cleared active names coexist without a uniqueness conflict'
);

select is(
    (select count(*)
     from public.tournament_group_pairing_team_assignments
     where tournament_id = 'e5200000-0000-0000-0000-000000000001'),
    2::bigint,
    'clearing active names retains both pairing UUID assignments'
);

insert into public.tournaments (
    id, owner_id, name, format, group_count, status, revision
)
values (
    'e6200000-0000-0000-0000-000000000001',
    'e3100000-0000-0000-0000-000000000001',
    'Priority 3 three-group identity capacity',
    'group_rotation',
    3,
    'draft',
    1
);

insert into public.tournament_team_identities (
    tournament_id, team_uuid, display_name, normalized_name
)
select
    'e6200000-0000-0000-0000-000000000001',
    ('e6300000-0000-0000-0000-' || lpad(slots.slot::text, 12, '0'))::uuid,
    'Three Capacity ' || slots.slot,
    'three capacity ' || slots.slot
from generate_series(1, 18) as slots(slot);

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e6200000-0000-0000-0000-000000000001',
         (
             select jsonb_agg(
                 jsonb_build_object(
                     'team_uuid', ('e6300000-0000-0000-0000-' || lpad(slots.slot::text, 12, '0'))::uuid,
                     'display_name', 'Three Capacity ' || slots.slot,
                     'normalized_name', 'three capacity ' || slots.slot
                 )
                 order by slots.slot
             )
             from generate_series(1, 19) as slots(slot)
         ),
         '[]'::jsonb,
         1,
         'e6400000-0000-0000-0000-000000000001'
     )),
    'validation_failure',
    'three-group tournaments reject a nineteenth permanent UUID identity'
);

select is(
    (select count(*)
     from public.tournament_team_identities
     where tournament_id = 'e6200000-0000-0000-0000-000000000001'),
    18::bigint,
    'a rejected nineteenth identity leaves the three-group pool unchanged'
);

insert into public.tournaments (
    id, owner_id, name, format, group_count, status, revision
)
values (
    'e7200000-0000-0000-0000-000000000001',
    'e3100000-0000-0000-0000-000000000001',
    'Priority 3 four-group identity capacity',
    'group_rotation',
    4,
    'draft',
    1
);

insert into public.tournament_team_identities (
    tournament_id, team_uuid, display_name, normalized_name
)
select
    'e7200000-0000-0000-0000-000000000001',
    ('e7300000-0000-0000-0000-' || lpad(slots.slot::text, 12, '0'))::uuid,
    'Four Capacity ' || slots.slot,
    'four capacity ' || slots.slot
from generate_series(1, 24) as slots(slot);

select is(
    (select outcome
     from public.write_group_rotation_uuid_identity_snapshot_v4(
         'e7200000-0000-0000-0000-000000000001',
         (
             select jsonb_agg(
                 jsonb_build_object(
                     'team_uuid', ('e7300000-0000-0000-0000-' || lpad(slots.slot::text, 12, '0'))::uuid,
                     'display_name', 'Four Capacity ' || slots.slot,
                     'normalized_name', 'four capacity ' || slots.slot
                 )
                 order by slots.slot
             )
             from generate_series(1, 25) as slots(slot)
         ),
         '[]'::jsonb,
         1,
         'e7400000-0000-0000-0000-000000000001'
     )),
    'validation_failure',
    'four-group tournaments reject a twenty-fifth permanent UUID identity'
);

select is(
    (select count(*)
     from public.tournament_team_identities
     where tournament_id = 'e7200000-0000-0000-0000-000000000001'),
    24::bigint,
    'a rejected twenty-fifth identity leaves the four-group pool unchanged'
);

select * from finish();

rollback;

