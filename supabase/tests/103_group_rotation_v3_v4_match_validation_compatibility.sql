begin;

select no_plan();

select ok(
    to_regprocedure('public.write_tournament_snapshot_v3(jsonb,jsonb,jsonb,jsonb,integer)') is not null,
    'V3 compatibility test sees the existing function signature'
);
select is(
    (select prosecdef
     from pg_proc
     where oid = 'public.write_tournament_snapshot_v3(jsonb,jsonb,jsonb,jsonb,integer)'::regprocedure),
    false,
    'V3 compatibility keeps SECURITY INVOKER'
);

insert into auth.users (id, email)
values
    ('f1000000-0000-0000-0000-000000000001', 'v3-v4-compat-owner@example.test');

insert into public.tournaments (
    id, owner_id, name, status, format, group_count, revision
)
values
    ('f2000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000001', 'V4 sequence', 'draft', 'group_rotation', 3, 1),
    ('f2000000-0000-0000-0000-000000000002', 'f1000000-0000-0000-0000-000000000001', 'Missing identity evidence', 'draft', 'group_rotation', 3, 1),
    ('f2000000-0000-0000-0000-000000000003', 'f1000000-0000-0000-0000-000000000001', 'Invalid UUID assignment', 'draft', 'group_rotation', 3, 1),
    ('f2000000-0000-0000-0000-000000000004', 'f1000000-0000-0000-0000-000000000001', 'Incomplete finalized snapshot', 'draft', 'group_rotation', 3, 1),
    ('f2000000-0000-0000-0000-000000000005', 'f1000000-0000-0000-0000-000000000001', 'Legacy mapping', 'draft', 'group_rotation', 3, 1);

insert into public.tournament_group_pairings (tournament_id, first_group, second_group)
select tournament_id, 'A', 'B'
from (values
    ('f2000000-0000-0000-0000-000000000001'::uuid),
    ('f2000000-0000-0000-0000-000000000002'::uuid),
    ('f2000000-0000-0000-0000-000000000003'::uuid),
    ('f2000000-0000-0000-0000-000000000004'::uuid),
    ('f2000000-0000-0000-0000-000000000005'::uuid)
) as tournaments(tournament_id);

insert into public.tournament_team_slots (
    id, tournament_id, slot_number, team_name, status, "group"
)
select
    ('f3010000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
    'f2000000-0000-0000-0000-000000000001'::uuid,
    slot_number,
    'V4 Full Team ' || slot_number,
    'draft',
    null
from generate_series(1, 18) as slots(slot_number)
union all
select
    ('f3020000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
    'f2000000-0000-0000-0000-000000000002'::uuid,
    slot_number,
    'Missing Team ' || slot_number,
    'draft',
    null
from generate_series(1, 18) as slots(slot_number)
union all
select
    ('f3030000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
    'f2000000-0000-0000-0000-000000000003'::uuid,
    slot_number,
    'Invalid Team ' || slot_number,
    'draft',
    null
from generate_series(1, 18) as slots(slot_number)
union all
select
    ('f3040000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
    'f2000000-0000-0000-0000-000000000004'::uuid,
    slot_number,
    'Incomplete Team ' || slot_number,
    'draft',
    null
from generate_series(1, 18) as slots(slot_number)
union all
select
    ('f3050000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
    'f2000000-0000-0000-0000-000000000005'::uuid,
    slot_number,
    'Legacy Team ' || slot_number,
    'draft',
    case
        when slot_number between 1 and 6 then 'A'
        when slot_number between 7 and 12 then 'B'
        else 'C'
    end
from generate_series(1, 18) as slots(slot_number);

insert into public.tournament_team_identities (
    tournament_id, team_uuid, display_name, normalized_name
)
select
    'f2000000-0000-0000-0000-000000000001'::uuid,
    ('f4010000-0000-0000-0000-' || lpad(team_number::text, 12, '0'))::uuid,
    'UUID Team ' || team_number,
    'uuid team ' || team_number
from generate_series(1, 12) as teams(team_number)
union all
select
    'f2000000-0000-0000-0000-000000000003'::uuid,
    ('f4020000-0000-0000-0000-' || lpad(team_number::text, 12, '0'))::uuid,
    'Invalid Team UUID ' || team_number,
    'invalid team uuid ' || team_number
from generate_series(1, 2) as teams(team_number)
union all
select
    'f2000000-0000-0000-0000-000000000004'::uuid,
    'f4030000-0000-0000-0000-000000000001',
    'Incomplete UUID Team',
    'incomplete uuid team';

insert into public.tournament_group_pairing_team_assignments (
    tournament_id, pairing_key, lobby_slot_number, team_uuid
)
select
    'f2000000-0000-0000-0000-000000000001'::uuid,
    'A:B',
    lobby_slot_number,
    ('f4010000-0000-0000-0000-' || lpad(lobby_slot_number::text, 12, '0'))::uuid
from generate_series(1, 12) as slots(lobby_slot_number)
union all
select
    'f2000000-0000-0000-0000-000000000003'::uuid,
    'A:B',
    1,
    'f4020000-0000-0000-0000-000000000001'
union all
select
    'f2000000-0000-0000-0000-000000000004'::uuid,
    'A:B',
    1,
    'f4030000-0000-0000-0000-000000000001';

insert into public.matches (
    id, tournament_id, match_number, status, revision, group_pairing_key
)
values
    ('f5010000-0000-0000-0000-000000000001', 'f2000000-0000-0000-0000-000000000001', 1, 'draft', 1, 'A:B'),
    ('f5010000-0000-0000-0000-000000000101', 'f2000000-0000-0000-0000-000000000002', 1, 'draft', 1, 'A:B'),
    ('f5010000-0000-0000-0000-000000000201', 'f2000000-0000-0000-0000-000000000003', 1, 'draft', 1, 'A:B'),
    ('f5010000-0000-0000-0000-000000000301', 'f2000000-0000-0000-0000-000000000004', 1, 'finalized', 1, 'A:B'),
    ('f5010000-0000-0000-0000-000000000401', 'f2000000-0000-0000-0000-000000000005', 1, 'draft', 1, 'A:B'),
    ('f5010000-0000-0000-0000-000000000402', 'f2000000-0000-0000-0000-000000000005', 2, 'draft', 1, 'A:B');

insert into public.group_rotation_match_uuid_snapshots (
    match_id, tournament_id, pairing_key, status, revision, finalized_at, finalized_by
)
values (
    'f5010000-0000-0000-0000-000000000201',
    'f2000000-0000-0000-0000-000000000003',
    'A:B',
    'draft',
    0,
    null,
    null
), (
    'f5010000-0000-0000-0000-000000000301',
    'f2000000-0000-0000-0000-000000000004',
    'A:B',
    'finalized',
    0,
    timezone('utc'::text, now()),
    'f1000000-0000-0000-0000-000000000001'
);

insert into public.group_rotation_match_uuid_assignments (
    match_id, tournament_id, lobby_slot_number, team_uuid
)
values
    ('f5010000-0000-0000-0000-000000000201', 'f2000000-0000-0000-0000-000000000003', 1, 'f4020000-0000-0000-0000-000000000002'),
    ('f5010000-0000-0000-0000-000000000301', 'f2000000-0000-0000-0000-000000000004', 1, 'f4030000-0000-0000-0000-000000000001');

update public.matches
set finalized_at = timezone('utc'::text, now()),
    finalized_by = 'f1000000-0000-0000-0000-000000000001'
where id = 'f5010000-0000-0000-0000-000000000301';

insert into public.tournament_group_pairing_lobby_slots (
    tournament_id, pairing_key, lobby_slot_number, team_slot_number
)
select
    'f2000000-0000-0000-0000-000000000005',
    'A:B',
    lobby_slot_number,
    lobby_slot_number
from generate_series(1, 12) as slots(lobby_slot_number);

create temporary table v3_v4_compat_payloads (
    payload_key text primary key,
    tournament jsonb not null,
    team_slots jsonb not null
);

insert into pg_temp.v3_v4_compat_payloads (
    payload_key, tournament, team_slots
)
select
    payload_key,
    jsonb_build_object(
        'id', tournament_id,
        'owner_id', 'f1000000-0000-0000-0000-000000000001'::uuid,
        'name', payload_key,
        'status', 'draft',
        'format', 'group_rotation',
        'group_count', 3,
        'selected_group_pairings',
        jsonb_build_array(
            jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B')
        )
    ),
    (
        select jsonb_agg(
            jsonb_build_object(
                'id', slot.id,
                'tournament_id', slot.tournament_id,
                'slot_number', slot.slot_number,
                'team_name', slot.team_name,
                'status', slot.status,
                'group', slot."group"
            )
            order by slot.slot_number
        )
        from public.tournament_team_slots as slot
        where slot.tournament_id = payloads.tournament_id
    )
from (values
    ('v4-sequence', 'f2000000-0000-0000-0000-000000000001'::uuid),
    ('missing-header', 'f2000000-0000-0000-0000-000000000002'::uuid),
    ('invalid-assignment', 'f2000000-0000-0000-0000-000000000003'::uuid),
    ('incomplete-finalized', 'f2000000-0000-0000-0000-000000000004'::uuid),
    ('legacy', 'f2000000-0000-0000-0000-000000000005'::uuid)
) as payloads(payload_key, tournament_id);

grant select on pg_temp.v3_v4_compat_payloads to authenticated;

set local role authenticated;
set local request.jwt.claim.sub = 'f1000000-0000-0000-0000-000000000001';

select is((
    select outcome
    from public.write_match_snapshot_v4(
        'f2000000-0000-0000-0000-000000000001',
        '{"id":"f5010000-0000-0000-0000-000000000001","tournament_id":"f2000000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
        (
            select jsonb_agg(
                jsonb_build_object(
                    'lobby_slot_number', lobby_slot_number,
                    'team_uuid', ('f4010000-0000-0000-0000-' || lpad(lobby_slot_number::text, 12, '0'))::uuid
                )
                order by lobby_slot_number
            )
            from generate_series(1, 12) as slots(lobby_slot_number)
        ),
        '[]'::jsonb,
        1,
        'f6010000-0000-0000-0000-000000000001'
    )
), 'success', 'Match 1 full UUID V4 snapshot is written');

select is((
    select revision
    from public.write_match_snapshot_v4(
        'f2000000-0000-0000-0000-000000000001',
        '{"id":"f5010000-0000-0000-0000-000000000001","tournament_id":"f2000000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
        (
            select jsonb_agg(
                jsonb_build_object(
                    'lobby_slot_number', lobby_slot_number,
                    'team_uuid', ('f4010000-0000-0000-0000-' || lpad(lobby_slot_number::text, 12, '0'))::uuid
                )
                order by lobby_slot_number
            )
            from generate_series(1, 12) as slots(lobby_slot_number)
        ),
        '[]'::jsonb,
        1,
        'f6010000-0000-0000-0000-000000000001'
    )
), 2, 'idempotent Match 1 V4 retry returns the persisted revision');

select is((
    select outcome
    from public.write_tournament_snapshot_v3(
        (select tournament from pg_temp.v3_v4_compat_payloads where payload_key = 'v4-sequence'),
        (select team_slots from pg_temp.v3_v4_compat_payloads where payload_key = 'v4-sequence'),
        '[]'::jsonb,
        '[]'::jsonb,
        2
    )
), 'success', 'V3 parent sync accepts an existing full UUID V4 match without legacy mappings');

select is((select revision from public.tournaments where id = 'f2000000-0000-0000-0000-000000000001'), 3, 'V3 parent sync advances the revision after Match 1');

set local role postgres;
insert into public.matches (
    id, tournament_id, match_number, status, revision, group_pairing_key
)
values
    ('f5010000-0000-0000-0000-000000000002', 'f2000000-0000-0000-0000-000000000001', 2, 'draft', 1, 'A:B'),
    ('f5010000-0000-0000-0000-000000000003', 'f2000000-0000-0000-0000-000000000001', 3, 'draft', 1, 'A:B');
set local role authenticated;
set local request.jwt.claim.sub = 'f1000000-0000-0000-0000-000000000001';

select is((
    select outcome
    from public.write_match_snapshot_v4(
        'f2000000-0000-0000-0000-000000000001',
        '{"id":"f5010000-0000-0000-0000-000000000002","tournament_id":"f2000000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
        '[{"lobby_slot_number":2,"team_uuid":"f4010000-0000-0000-0000-000000000002"},{"lobby_slot_number":9,"team_uuid":"f4010000-0000-0000-0000-000000000009"}]'::jsonb,
        '[]'::jsonb,
        3,
        'f6010000-0000-0000-0000-000000000002'
    )
), 'success', 'Match 2 sparse UUID V4 snapshot is written after V3 parent sync');

select is((select revision from public.tournaments where id = 'f2000000-0000-0000-0000-000000000001'), 4, 'Match 2 advances the post-sync revision');

select is((
    select count(*)
    from public.group_rotation_match_uuid_snapshots
    where tournament_id = 'f2000000-0000-0000-0000-000000000001'
), 2::bigint, 'both V4 match headers are present');

select is((
    select count(*)
    from public.group_rotation_match_uuid_assignments
    where tournament_id = 'f2000000-0000-0000-0000-000000000001'
), 14::bigint, 'full and sparse V4 assignment sets are both preserved');

select is((
    select count(*)
    from public.tournament_group_pairing_lobby_slots
    where tournament_id = 'f2000000-0000-0000-0000-000000000001'
), 0::bigint, 'the UUID sequence does not require legacy mapping rows');

select is((
    select outcome
    from public.write_match_snapshot_v4(
        'f2000000-0000-0000-0000-000000000001',
        '{"id":"f5010000-0000-0000-0000-000000000003","tournament_id":"f2000000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
        '[{"lobby_slot_number":1,"team_uuid":"f4010000-0000-0000-0000-000000000001"}]'::jsonb,
        '[{"team_uuid":"f4010000-0000-0000-0000-000000000001","participation_status":"PARTICIPATED","placement":1,"kills":4,"point_adjustment":2}]'::jsonb,
        4,
        'f6010000-0000-0000-0000-000000000003'
    )
), 'success', 'a finalized-result candidate can be written as a V4 draft');

select is((
    select outcome
    from public.finalize_match_snapshot_v4(
        'f2000000-0000-0000-0000-000000000001',
        'f5010000-0000-0000-0000-000000000003',
        '[{"team_uuid":"f4010000-0000-0000-0000-000000000001","participation_status":"PARTICIPATED","placement":1,"kills":4,"point_adjustment":2}]'::jsonb,
        5,
        'f6010000-0000-0000-0000-000000000004'
    )
), 'success', 'a complete UUID result snapshot finalizes successfully');

select is((
    select status
    from public.group_rotation_match_uuid_snapshots
    where match_id = 'f5010000-0000-0000-0000-000000000003'
), 'finalized', 'the valid finalized UUID snapshot remains finalized');

select is((
    select (team_uuid, placement, kills, point_adjustment)::text
    from public.group_rotation_match_uuid_results
    where match_id = 'f5010000-0000-0000-0000-000000000003'
), '(f4010000-0000-0000-0000-000000000001,1,4,2)', 'finalized UUID result values remain exact');

select is((
    select outcome
    from public.write_tournament_snapshot_v3(
        (select tournament from pg_temp.v3_v4_compat_payloads where payload_key = 'v4-sequence'),
        (select team_slots from pg_temp.v3_v4_compat_payloads where payload_key = 'v4-sequence'),
        '[]'::jsonb,
        '[]'::jsonb,
        6
    )
), 'success', 'V3 parent sync accepts a complete finalized UUID V4 match');

select is((select revision from public.tournaments where id = 'f2000000-0000-0000-0000-000000000001'), 7, 'finalized V4 compatibility sync advances revision');

select is((
    select outcome
    from public.write_tournament_snapshot_v3(
        (select tournament from pg_temp.v3_v4_compat_payloads where payload_key = 'missing-header'),
        (select team_slots from pg_temp.v3_v4_compat_payloads where payload_key = 'missing-header'),
        '[]'::jsonb,
        '[]'::jsonb,
        1
    )
), 'validation_failure', 'a headerless existing match is rejected without legacy evidence');

select is((
    select outcome
    from public.write_tournament_snapshot_v3(
        (select tournament from pg_temp.v3_v4_compat_payloads where payload_key = 'invalid-assignment'),
        (select team_slots from pg_temp.v3_v4_compat_payloads where payload_key = 'invalid-assignment'),
        '[]'::jsonb,
        '[]'::jsonb,
        1
    )
), 'validation_failure', 'a UUID match assignment outside the selected pairing is rejected');

select is((
    select outcome
    from public.write_tournament_snapshot_v3(
        (select tournament from pg_temp.v3_v4_compat_payloads where payload_key = 'incomplete-finalized'),
        (select team_slots from pg_temp.v3_v4_compat_payloads where payload_key = 'incomplete-finalized'),
        '[]'::jsonb,
        '[]'::jsonb,
        1
    )
), 'validation_failure', 'a finalized UUID snapshot without complete results is rejected');

select is((
    select outcome
    from public.write_tournament_snapshot_v3(
        (select tournament from pg_temp.v3_v4_compat_payloads where payload_key = 'legacy'),
        (select team_slots from pg_temp.v3_v4_compat_payloads where payload_key = 'legacy'),
        '[]'::jsonb,
        (
            select jsonb_agg(
                jsonb_build_object(
                    'tournament_id', 'f2000000-0000-0000-0000-000000000005'::uuid,
                    'pairing_key', 'A:B',
                    'lobby_slot_number', lobby_slot_number,
                    'team_slot_number', lobby_slot_number
                )
                order by lobby_slot_number
            )
            from generate_series(1, 12) as slots(lobby_slot_number)
        ),
        1
    )
), 'success', 'genuine legacy Group Rotation mappings remain accepted');

select is((
    select count(*)
    from public.tournament_group_pairing_lobby_slots
    where tournament_id = 'f2000000-0000-0000-0000-000000000005'
), 12::bigint, 'legacy mappings remain stored after the compatibility check');

set local role postgres;
insert into public.group_rotation_match_uuid_snapshots (
    match_id, tournament_id, pairing_key, status, revision, finalized_at, finalized_by
)
values (
    'f5010000-0000-0000-0000-000000000402',
    'f2000000-0000-0000-0000-000000000005',
    'A:B',
    'draft',
    0,
    null,
    null
);
set local role authenticated;
set local request.jwt.claim.sub = 'f1000000-0000-0000-0000-000000000001';

select is((
    select outcome
    from public.write_tournament_snapshot_v3(
        (select tournament from pg_temp.v3_v4_compat_payloads where payload_key = 'legacy'),
        (select team_slots from pg_temp.v3_v4_compat_payloads where payload_key = 'legacy'),
        '[]'::jsonb,
        (
            select jsonb_agg(
                jsonb_build_object(
                    'tournament_id', 'f2000000-0000-0000-0000-000000000005'::uuid,
                    'pairing_key', 'A:B',
                    'lobby_slot_number', lobby_slot_number,
                    'team_slot_number', lobby_slot_number
                )
                order by lobby_slot_number
            )
            from generate_series(1, 12) as slots(lobby_slot_number)
        ),
        2
    )
), 'validation_failure', 'a malformed V4 header cannot be masked by valid legacy mappings');

select is((select revision from public.tournaments where id = 'f2000000-0000-0000-0000-000000000005'), 2, 'malformed V4 rejection preserves the legacy tournament revision');

select * from finish();
rollback;
