begin;

select plan(24);

insert into auth.users (id, email)
values (
    'e7b00000-0000-0000-0000-000000000001',
    'phase-v4-draft-refresh-owner@example.test'
);

insert into public.tournaments (
    id, owner_id, name, format, group_count, status, revision
)
values (
    'e7b10000-0000-0000-0000-000000000001',
    'e7b00000-0000-0000-0000-000000000001',
    'V4 draft refresh regression',
    'group_rotation',
    3,
    'draft',
    1
);

insert into public.tournament_group_pairings (tournament_id, first_group, second_group)
values ('e7b10000-0000-0000-0000-000000000001', 'A', 'B');

insert into public.tournament_team_identities (
    tournament_id, team_uuid, display_name, normalized_name
)
select
    'e7b10000-0000-0000-0000-000000000001',
    ('e7b20000-0000-0000-0000-' || lpad(team_number::text, 12, '0'))::uuid,
    'Team ' || team_number::text,
    'team ' || team_number::text
from generate_series(1, 13) as teams(team_number);

insert into public.matches (
    id, tournament_id, match_number, status, revision, group_pairing_key
)
values (
    'e7b30000-0000-0000-0000-000000000001',
    'e7b10000-0000-0000-0000-000000000001',
    1,
    'draft',
    1,
    'A:B'
);

create temp table draft_refresh_payloads (
    payload_key text primary key,
    identity_json jsonb not null
) on commit drop;

insert into draft_refresh_payloads (payload_key, identity_json)
select 'full', jsonb_agg(
    jsonb_build_object(
        'lobby_slot_number', team_number,
        'team_uuid', ('e7b20000-0000-0000-0000-' || lpad(team_number::text, 12, '0'))
    )
    order by team_number
)
from generate_series(1, 12) as teams(team_number);

insert into draft_refresh_payloads (payload_key, identity_json)
select 'eleven', jsonb_agg(
    jsonb_build_object(
        'lobby_slot_number', team_number,
        'team_uuid', ('e7b20000-0000-0000-0000-' || lpad(team_number::text, 12, '0'))
    )
    order by team_number
)
from generate_series(1, 11) as teams(team_number);

insert into draft_refresh_payloads (payload_key, identity_json)
select 'replacement', jsonb_agg(
    jsonb_build_object(
        'lobby_slot_number', team_number,
        'team_uuid', case
            when team_number = 12
                then 'e7b20000-0000-0000-0000-000000000013'
            else 'e7b20000-0000-0000-0000-' || lpad(team_number::text, 12, '0')
        end
    )
    order by team_number
)
from generate_series(1, 12) as teams(team_number);

create temp table draft_refresh_results (
    result_json jsonb not null
) on commit drop;

insert into draft_refresh_results (result_json)
select jsonb_agg(
    case
        when team_number = 1 then jsonb_build_object(
            'team_uuid', 'e7b20000-0000-0000-0000-000000000001',
            'participation_status', 'PARTICIPATED',
            'placement', 1,
            'kills', 4,
            'point_adjustment', 2
        )
        else jsonb_build_object(
            'team_uuid', case
                when team_number = 12
                    then 'e7b20000-0000-0000-0000-000000000013'
                else 'e7b20000-0000-0000-0000-' || lpad(team_number::text, 12, '0')
            end,
            'participation_status', 'NO_SHOW',
            'placement', null::integer,
            'kills', 0,
            'point_adjustment', 0
        )
    end
    order by team_number
)
from generate_series(1, 12) as teams(team_number);

set local role authenticated;
set local request.jwt.claim.sub = 'e7b00000-0000-0000-0000-000000000001';

select is(
    (select outcome
     from public.write_match_snapshot_v4(
         'e7b10000-0000-0000-0000-000000000001',
         '{"id":"e7b30000-0000-0000-0000-000000000001","tournament_id":"e7b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
         (select identity_json from draft_refresh_payloads where payload_key = 'full'),
         '[]'::jsonb,
         1,
         'e7b40000-0000-0000-0000-000000000001'
     )),
    'success',
    'the initial twelve-team draft snapshot is written'
);

select is(
    (select outcome
     from public.write_match_snapshot_v4(
         'e7b10000-0000-0000-0000-000000000001',
         '{"id":"e7b30000-0000-0000-0000-000000000001","tournament_id":"e7b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
         (select identity_json from draft_refresh_payloads where payload_key = 'eleven'),
         '[]'::jsonb,
         2,
         'e7b40000-0000-0000-0000-000000000002'
     )),
    'success',
    'clearing one draft assignment replaces the twelve-team set with eleven'
);
select is(
    (select count(*) from public.group_rotation_match_uuid_assignments
     where match_id = 'e7b30000-0000-0000-0000-000000000001'),
    11::bigint,
    'the cleared draft assignment is removed'
);
select is(
    (select count(*) from public.group_rotation_match_uuid_assignments
     where match_id = 'e7b30000-0000-0000-0000-000000000001'
       and team_uuid = 'e7b20000-0000-0000-0000-000000000012'),
    0::bigint,
    'clearing removes the UUID assignment rather than remapping it by slot'
);

select is(
    (select outcome
     from public.write_match_snapshot_v4(
         'e7b10000-0000-0000-0000-000000000001',
         '{"id":"e7b30000-0000-0000-0000-000000000001","tournament_id":"e7b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
         (select identity_json from draft_refresh_payloads where payload_key = 'full'),
         '[]'::jsonb,
         3,
         'e7b40000-0000-0000-0000-000000000003'
     )),
    'success',
    'restoring the cleared draft assignment succeeds'
);
select is(
    (select count(*) from public.group_rotation_match_uuid_assignments
     where match_id = 'e7b30000-0000-0000-0000-000000000001'),
    12::bigint,
    'restoring the assignment returns the draft to twelve UUID assignments'
);

select is(
    (select outcome
     from public.write_match_snapshot_v4(
         'e7b10000-0000-0000-0000-000000000001',
         '{"id":"e7b30000-0000-0000-0000-000000000001","tournament_id":"e7b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
         (select identity_json from draft_refresh_payloads where payload_key = 'replacement'),
         '[]'::jsonb,
         4,
         'e7b40000-0000-0000-0000-000000000004'
     )),
    'success',
    'a draft UUID replacement succeeds'
);
select is(
    (select count(*) from public.group_rotation_match_uuid_assignments
     where match_id = 'e7b30000-0000-0000-0000-000000000001'
       and team_uuid = 'e7b20000-0000-0000-0000-000000000012'),
    0::bigint,
    'draft UUID replacement removes the previous UUID'
);
select is(
    (select count(*) from public.group_rotation_match_uuid_assignments
     where match_id = 'e7b30000-0000-0000-0000-000000000001'
       and team_uuid = 'e7b20000-0000-0000-0000-000000000013'),
    1::bigint,
    'draft UUID replacement stores the incoming UUID exactly'
);

select is(
    (select outcome
     from public.write_match_snapshot_v4(
         'e7b10000-0000-0000-0000-000000000001',
         '{"id":"e7b30000-0000-0000-0000-000000000001","tournament_id":"e7b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
         (select identity_json from draft_refresh_payloads where payload_key = 'replacement'),
         '[{"team_uuid":"e7b20000-0000-0000-0000-000000000012","participation_status":"PARTICIPATED","placement":1,"kills":1,"point_adjustment":0}]'::jsonb,
         5,
         'e7b40000-0000-0000-0000-000000000005'
     )),
    'validation_failure',
    'a result for a UUID absent from the incoming identity set is rejected'
);
select is(
    (select count(*) from public.group_rotation_match_uuid_assignments
     where match_id = 'e7b30000-0000-0000-0000-000000000001'),
    12::bigint,
    'rejected stale result input leaves the current draft assignments unchanged'
);

select is(
    (select outcome
     from public.write_match_snapshot_v4(
         'e7b10000-0000-0000-0000-000000000001',
         '{"id":"e7b30000-0000-0000-0000-000000000001","tournament_id":"e7b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
         '[]'::jsonb,
         '[]'::jsonb,
         5,
         'e7b40000-0000-0000-0000-000000000006'
     )),
    'success',
    'an intentionally empty draft identity set with empty results succeeds'
);
select is(
    (select count(*) from public.group_rotation_match_uuid_assignments
     where match_id = 'e7b30000-0000-0000-0000-000000000001'),
    0::bigint,
    'an empty draft identity set removes all prior assignments'
);

select is(
    (select outcome
     from public.write_match_snapshot_v4(
         'e7b10000-0000-0000-0000-000000000001',
         '{"id":"e7b30000-0000-0000-0000-000000000001","tournament_id":"e7b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
         '[]'::jsonb,
         '[]'::jsonb,
         5,
         'e7b40000-0000-0000-0000-000000000007'
     )),
    'stale_write',
    'a stale revision remains rejected after draft refresh'
);

select is(
    (select outcome
     from public.write_match_snapshot_v4(
         'e7b10000-0000-0000-0000-000000000001',
         '{"id":"e7b30000-0000-0000-0000-000000000001","tournament_id":"e7b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
         '[]'::jsonb,
         '[]'::jsonb,
         5,
         'e7b40000-0000-0000-0000-000000000006'
     )),
    'success',
    'replaying the successful empty draft request is idempotent'
);
select is(
    (select revision from public.tournaments
     where id = 'e7b10000-0000-0000-0000-000000000001'),
    6,
    'idempotent replay does not advance the tournament revision'
);

select is(
    (select outcome
     from public.write_match_snapshot_v4(
         'e7b10000-0000-0000-0000-000000000001',
         '{"id":"e7b30000-0000-0000-0000-000000000001","tournament_id":"e7b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
         (select identity_json from draft_refresh_payloads where payload_key = 'replacement'),
         (select result_json from draft_refresh_results),
         6,
         'e7b40000-0000-0000-0000-000000000008'
     )),
    'success',
    'a non-empty finalized-result candidate can be restored before finalization'
);
select is(
    (select count(*) from public.group_rotation_match_uuid_assignments
     where match_id = 'e7b30000-0000-0000-0000-000000000001'),
    12::bigint,
    'the finalized-result candidate has twelve UUID assignments'
);
select is(
    (select outcome
     from public.finalize_match_snapshot_v4(
         'e7b10000-0000-0000-0000-000000000001',
         'e7b30000-0000-0000-0000-000000000001',
         (select result_json from draft_refresh_results),
         7,
         'e7b40000-0000-0000-0000-000000000009'
     )),
    'success',
    'the restored draft is finalized through the V4 finalizer'
);

select is(
    (select outcome
     from public.write_match_snapshot_v4(
         'e7b10000-0000-0000-0000-000000000001',
         '{"id":"e7b30000-0000-0000-0000-000000000001","tournament_id":"e7b10000-0000-0000-0000-000000000001","pairing_key":"A:B"}'::jsonb,
         (select identity_json from draft_refresh_payloads where payload_key = 'eleven'),
         '[]'::jsonb,
         8,
         'e7b40000-0000-0000-0000-000000000010'
     )),
    'already_finalized',
    'a finalized match rejects assignment mutation'
);
select is(
    (select count(*) from public.group_rotation_match_uuid_assignments
     where match_id = 'e7b30000-0000-0000-0000-000000000001'),
    12::bigint,
    'finalized assignment rows remain unchanged by a rejected draft write'
);
select is(
    (select team_uuid from public.group_rotation_match_uuid_assignments
     where match_id = 'e7b30000-0000-0000-0000-000000000001'
       and lobby_slot_number = 1),
    'e7b20000-0000-0000-0000-000000000001'::uuid,
    'finalized lobby one keeps its original UUID mapping'
);
select is(
    (select team_uuid from public.group_rotation_match_uuid_assignments
     where match_id = 'e7b30000-0000-0000-0000-000000000001'
       and lobby_slot_number = 12),
    'e7b20000-0000-0000-0000-000000000013'::uuid,
    'finalized lobby twelve keeps its original replacement UUID mapping'
);
select is(
    (select (team_uuid, participation_status, placement, kills, point_adjustment)::text
     from public.group_rotation_match_uuid_results
     where match_id = 'e7b30000-0000-0000-0000-000000000001'
       and team_uuid = 'e7b20000-0000-0000-0000-000000000001'),
    '(e7b20000-0000-0000-0000-000000000001,PARTICIPATED,1,4,2)',
    'finalized participant results remain unchanged by a rejected draft write'
);

select * from finish();
rollback;
