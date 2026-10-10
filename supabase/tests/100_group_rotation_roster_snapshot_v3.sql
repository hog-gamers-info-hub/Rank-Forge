begin;

select no_plan();

select ok(
    to_regprocedure('public.replace_tournament_roster_snapshot_v3(uuid,jsonb,jsonb,integer)') is not null,
    'V3 roster replacement signature exists'
);
select is(
    (select prosecdef
     from pg_proc
     where oid = 'public.replace_tournament_roster_snapshot_v3(uuid,jsonb,jsonb,integer)'::regprocedure),
    false,
    'V3 roster replacement is SECURITY INVOKER'
);
select ok(
    not has_function_privilege(
        'anon',
        'public.replace_tournament_roster_snapshot_v3(uuid,jsonb,jsonb,integer)',
        'execute'
    ),
    'anon cannot execute V3 roster replacement'
);
select ok(
    has_function_privilege(
        'authenticated',
        'public.replace_tournament_roster_snapshot_v3(uuid,jsonb,jsonb,integer)',
        'execute'
    ),
    'authenticated can execute V3 roster replacement'
);
select ok(
    to_regprocedure('public.replace_tournament_roster_snapshot_v2(uuid,jsonb,jsonb,integer)') is not null,
    'V2 roster replacement signature remains unchanged'
);
select ok(
    to_regprocedure('public.replace_tournament_roster_snapshot(uuid,jsonb,jsonb,integer)') is not null,
    'legacy roster replacement signature remains unchanged'
);
select ok(
    to_regprocedure('public.write_tournament_snapshot(jsonb,jsonb,jsonb,integer)') is not null,
    'legacy tournament snapshot signature remains unchanged'
);

create temporary table v3_roster_payloads (
    payload_key text primary key,
    tournament_id uuid not null,
    tournament jsonb not null,
    team_slots jsonb not null,
    initial_players jsonb not null,
    replacement_players jsonb not null,
    six_players jsonb not null,
    mappings jsonb not null
);

insert into auth.users (id, email)
values
    ('c1000000-0000-0000-0000-000000000001', 'phase4b3b-owner@example.test'),
    ('c1000000-0000-0000-0000-000000000002', 'phase4b3b-other@example.test');

insert into public.tournaments (
    id, owner_id, name, organizer_name, organizer_contact, status,
    format, group_count, revision
)
values
    ('c2000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000001', 'V3B3b Main', 'Organizer', '', 'draft', 'group_rotation', 3, 1),
    ('c2000000-0000-0000-0000-000000000002', 'c1000000-0000-0000-0000-000000000001', 'V3B3b Four', 'Organizer', '', 'draft', 'group_rotation', 4, 1),
    ('c2000000-0000-0000-0000-000000000003', 'c1000000-0000-0000-0000-000000000001', 'V3B3b Legacy', 'Organizer', '', 'draft', 'group_rotation', 3, 1),
    ('c2000000-0000-0000-0000-000000000099', 'c1000000-0000-0000-0000-000000000001', 'V3B3b Other', 'Organizer', '', 'draft', 'group_rotation', 3, 1);

insert into public.tournament_team_slots (
    id, tournament_id, slot_number, team_name, status, "group"
)
select
    ('c3010000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
    'c2000000-0000-0000-0000-000000000001'::uuid,
    slot_number, 'Main Team ' || slot_number, 'draft', null
from generate_series(1, 18) as slots(slot_number);

insert into public.tournament_team_slots (
    id, tournament_id, slot_number, team_name, status, "group"
)
select
    ('c3020000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
    'c2000000-0000-0000-0000-000000000002'::uuid,
    slot_number, 'Four Team ' || slot_number, 'draft', null
from generate_series(1, 24) as slots(slot_number);

insert into public.tournament_team_slots (
    id, tournament_id, slot_number, team_name, status, "group"
)
select
    ('c3030000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
    'c2000000-0000-0000-0000-000000000003'::uuid,
    slot_number, 'Legacy Team ' || slot_number, 'draft', case
        when slot_number between 1 and 6 then 'A'
        when slot_number between 7 and 12 then 'B'
        else 'C'
    end
from generate_series(1, 18) as slots(slot_number);

insert into public.tournament_team_slots (
    id, tournament_id, slot_number, team_name, status, "group"
)
values (
    'c3990000-0000-0000-0000-000000000001',
    'c2000000-0000-0000-0000-000000000099',
    1, 'Other Team 1', 'draft', null
);

insert into public.tournament_group_pairings (tournament_id, first_group, second_group)
values
    ('c2000000-0000-0000-0000-000000000001', 'A', 'B'),
    ('c2000000-0000-0000-0000-000000000001', 'A', 'C'),
    ('c2000000-0000-0000-0000-000000000001', 'B', 'C'),
    ('c2000000-0000-0000-0000-000000000002', 'A', 'B'),
    ('c2000000-0000-0000-0000-000000000003', 'A', 'B');

insert into public.tournament_group_pairing_lobby_slots (
    tournament_id, pairing_key, lobby_slot_number, team_slot_number
)
select 'c2000000-0000-0000-0000-000000000001'::uuid, 'A:B', lobby_slot_number, lobby_slot_number
from generate_series(1, 12) as slots(lobby_slot_number)
union all
select 'c2000000-0000-0000-0000-000000000001'::uuid, 'A:C', lobby_slot_number, lobby_slot_number + 6
from generate_series(1, 12) as slots(lobby_slot_number)
union all
select 'c2000000-0000-0000-0000-000000000002'::uuid, 'A:B', lobby_slot_number, lobby_slot_number + 12
from generate_series(1, 12) as slots(lobby_slot_number)
union all
select 'c2000000-0000-0000-0000-000000000003'::uuid, 'A:B', lobby_slot_number, lobby_slot_number
from generate_series(1, 12) as slots(lobby_slot_number);

insert into public.players (id, team_slot_id, display_name, normalized_name)
values
    ('c5010000-0000-0000-0000-000000000001', 'c3010000-0000-0000-0000-000000000001', 'Initial Player', 'Initial Player'),
    ('c5010000-0000-0000-0000-000000000002', 'c3010000-0000-0000-0000-000000000002', 'Keep Player', 'Keep Player'),
    ('c5020000-0000-0000-0000-000000000001', 'c3020000-0000-0000-0000-000000000001', 'Four Initial Player', 'Four Initial Player'),
    ('c6010000-0000-0000-0000-000000000001', 'c3990000-0000-0000-0000-000000000001', 'Other Player', 'Other Player');

insert into pg_temp.v3_roster_payloads (
    payload_key, tournament_id, tournament, team_slots,
    initial_players, replacement_players, six_players, mappings
)
values (
    'main',
    'c2000000-0000-0000-0000-000000000001',
    jsonb_build_object(
        'id', 'c2000000-0000-0000-0000-000000000001',
        'owner_id', 'c1000000-0000-0000-0000-000000000001',
        'name', 'V3B3b Main', 'organizer_name', 'Organizer', 'organizer_contact', '',
        'status', 'draft', 'format', 'group_rotation', 'group_count', 3,
        'selected_group_pairings', jsonb_build_array(
            jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B'),
            jsonb_build_object('first_group', 'A', 'second_group', 'C', 'pairing_key', 'A:C'),
            jsonb_build_object('first_group', 'B', 'second_group', 'C', 'pairing_key', 'B:C')
        )
    ),
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('c3010000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'tournament_id', 'c2000000-0000-0000-0000-000000000001'::uuid,
            'slot_number', slot_number, 'team_name', 'Main Team ' || slot_number,
            'status', 'draft', 'group', null
        ) order by slot_number)
        from generate_series(1, 18) as slots(slot_number)
    ),
    jsonb_build_array(
        jsonb_build_object(
            'id', 'c5010000-0000-0000-0000-000000000001'::uuid,
            'team_slot_id', 'c3010000-0000-0000-0000-000000000001'::uuid,
            'display_name', 'Initial Player', 'normalized_name', 'Initial Player'
        ),
        jsonb_build_object(
            'id', 'c5010000-0000-0000-0000-000000000002'::uuid,
            'team_slot_id', 'c3010000-0000-0000-0000-000000000002'::uuid,
            'display_name', 'Keep Player', 'normalized_name', 'Keep Player'
        )
    ),
    jsonb_build_array(
        jsonb_build_object(
            'id', 'c5010000-0000-0000-0000-000000000001'::uuid,
            'team_slot_id', 'c3010000-0000-0000-0000-000000000001'::uuid,
            'display_name', 'Replacement Player', 'normalized_name', 'Replacement Player'
        ),
        jsonb_build_object(
            'id', 'c5010000-0000-0000-0000-000000000002'::uuid,
            'team_slot_id', 'c3010000-0000-0000-0000-000000000002'::uuid,
            'display_name', 'Keep Player', 'normalized_name', 'Keep Player'
        )
    ),
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('c5010000-0000-0000-0000-' || lpad(player_number::text, 12, '0'))::uuid,
            'team_slot_id', 'c3010000-0000-0000-0000-000000000001'::uuid,
            'display_name', 'Six Player ' || player_number,
            'normalized_name', 'Six Player ' || player_number
        ) order by player_number)
        from generate_series(1, 6) as players(player_number)
    ),
    (
        select jsonb_agg(row_payload order by pairing_key, lobby_slot_number)
        from (
            select 'A:B'::text as pairing_key, lobby_slot_number,
                   jsonb_build_object(
                       'tournament_id', 'c2000000-0000-0000-0000-000000000001'::uuid,
                       'pairing_key', 'A:B', 'lobby_slot_number', lobby_slot_number,
                       'team_slot_number', lobby_slot_number
                   ) as row_payload
            from generate_series(1, 12) as slots(lobby_slot_number)
            union all
            select 'A:C'::text, lobby_slot_number,
                   jsonb_build_object(
                       'tournament_id', 'c2000000-0000-0000-0000-000000000001'::uuid,
                       'pairing_key', 'A:C', 'lobby_slot_number', lobby_slot_number,
                       'team_slot_number', lobby_slot_number + 6
                   )
            from generate_series(1, 12) as slots(lobby_slot_number)
        ) as mapping_rows
    )
), (
    'four',
    'c2000000-0000-0000-0000-000000000002',
    jsonb_build_object(
        'id', 'c2000000-0000-0000-0000-000000000002',
        'owner_id', 'c1000000-0000-0000-0000-000000000001',
        'name', 'V3B3b Four', 'organizer_name', 'Organizer', 'organizer_contact', '',
        'status', 'draft', 'format', 'group_rotation', 'group_count', 4,
        'selected_group_pairings', jsonb_build_array(
            jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B')
        )
    ),
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('c3020000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'tournament_id', 'c2000000-0000-0000-0000-000000000002'::uuid,
            'slot_number', slot_number, 'team_name', 'Four Team ' || slot_number,
            'status', 'draft', 'group', null
        ) order by slot_number)
        from generate_series(1, 24) as slots(slot_number)
    ),
    jsonb_build_array(jsonb_build_object(
        'id', 'c5020000-0000-0000-0000-000000000001'::uuid,
        'team_slot_id', 'c3020000-0000-0000-0000-000000000001'::uuid,
        'display_name', 'Four Initial Player', 'normalized_name', 'Four Initial Player'
    )),
    jsonb_build_array(jsonb_build_object(
        'id', 'c5020000-0000-0000-0000-000000000002'::uuid,
        'team_slot_id', 'c3020000-0000-0000-0000-000000000024'::uuid,
        'display_name', 'Four Replacement Player', 'normalized_name', 'Four Replacement Player'
    )),
    '[]'::jsonb,
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'c2000000-0000-0000-0000-000000000002'::uuid,
            'pairing_key', 'A:B', 'lobby_slot_number', lobby_slot_number,
            'team_slot_number', lobby_slot_number + 12
        ) order by lobby_slot_number)
        from generate_series(1, 12) as slots(lobby_slot_number)
    )
), (
    'legacy',
    'c2000000-0000-0000-0000-000000000003',
    jsonb_build_object(
        'id', 'c2000000-0000-0000-0000-000000000003',
        'owner_id', 'c1000000-0000-0000-0000-000000000001',
        'name', 'V3B3b Legacy', 'organizer_name', 'Organizer', 'organizer_contact', '',
        'status', 'draft', 'format', 'group_rotation', 'group_count', 3,
        'selected_group_pairings', jsonb_build_array(
            jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B')
        )
    ),
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('c3030000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'tournament_id', 'c2000000-0000-0000-0000-000000000003'::uuid,
            'slot_number', slot_number, 'team_name', 'Legacy Team ' || slot_number,
            'status', 'draft', 'group', case
                when slot_number between 1 and 6 then 'A'
                when slot_number between 7 and 12 then 'B'
                else 'C'
            end
        ) order by slot_number)
        from generate_series(1, 18) as slots(slot_number)
    ),
    '[]'::jsonb,
    '[]'::jsonb,
    '[]'::jsonb,
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'c2000000-0000-0000-0000-000000000003'::uuid,
            'pairing_key', 'A:B', 'lobby_slot_number', lobby_slot_number,
            'team_slot_number', lobby_slot_number
        ) order by lobby_slot_number)
        from generate_series(1, 12) as slots(lobby_slot_number)
    )
);

grant select on v3_roster_payloads to authenticated;
set local role authenticated;
set local request.jwt.claim.sub = 'c1000000-0000-0000-0000-000000000001';

select is((select outcome from public.replace_tournament_roster_snapshot_v3(
    (select tournament_id from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (select replacement_players from pg_temp.v3_roster_payloads where payload_key = 'main'),
    1
)), 'success', 'three-group null-group roster replacement succeeds');
select is((select revision from public.tournaments where id = 'c2000000-0000-0000-0000-000000000001'), 2, 'successful replacement advances the revision once');
select is((select count(*) from public.tournament_team_slots where tournament_id = 'c2000000-0000-0000-0000-000000000001' and "group" is null), 18::bigint, 'three-group replacement preserves null group metadata');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'c2000000-0000-0000-0000-000000000001'), 24::bigint, 'replacement preserves all configured mappings');
select is((select count(*) from public.tournament_group_pairing_lobby_slots
    where tournament_id = 'c2000000-0000-0000-0000-000000000001'
      and ((pairing_key = 'A:B' and team_slot_number <> lobby_slot_number)
        or (pairing_key = 'A:C' and team_slot_number <> lobby_slot_number + 6))), 0::bigint,
    'replacement preserves every existing A:B and A:C mapping');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'c2000000-0000-0000-0000-000000000001' and pairing_key = 'B:C'), 0::bigint, 'selected but unconfigured pairing remains unconfigured');
select is((select display_name from public.players where id = 'c5010000-0000-0000-0000-000000000001'), 'Replacement Player', 'replacement upserts the supplied existing player');
select is((select count(*) from public.players where id = 'c5010000-0000-0000-0000-000000000002'), 1::bigint, 'replacement preserves the second supplied player');
select is((select team_name from public.tournament_team_slots where id = 'c3990000-0000-0000-0000-000000000001'), 'Other Team 1', 'successful replacement preserves the unrelated team slot');
select is((select count(*) from public.players where id = 'c6010000-0000-0000-0000-000000000001' and team_slot_id = 'c3990000-0000-0000-0000-000000000001' and display_name = 'Other Player'), 1::bigint, 'successful replacement preserves the unrelated player');

select is((select outcome from public.replace_tournament_roster_snapshot_v3(
    (select tournament_id from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_roster_payloads where payload_key = 'main'),
    '[]'::jsonb,
    2
)), 'success', 'empty roster snapshot intentionally clears the tournament roster');
select is((select count(*) from public.players p join public.tournament_team_slots s on s.id = p.team_slot_id where s.tournament_id = 'c2000000-0000-0000-0000-000000000001'), 0::bigint, 'empty roster replacement removes only target roster rows');
select is((select revision from public.tournaments where id = 'c2000000-0000-0000-0000-000000000001'), 3, 'empty roster replacement advances the revision once');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'c2000000-0000-0000-0000-000000000001'), 24::bigint, 'empty roster replacement preserves mappings');
select is((select team_name from public.tournament_team_slots where id = 'c3990000-0000-0000-0000-000000000001'), 'Other Team 1', 'empty roster replacement preserves the unrelated team slot');
select is((select count(*) from public.players where id = 'c6010000-0000-0000-0000-000000000001' and team_slot_id = 'c3990000-0000-0000-0000-000000000001' and display_name = 'Other Player'), 1::bigint, 'empty roster replacement preserves the unrelated player');

select is((select outcome from public.replace_tournament_roster_snapshot_v3(
    (select tournament_id from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (select six_players from pg_temp.v3_roster_payloads where payload_key = 'main'),
    3
)), 'success', 'six players on one permanent team are accepted');
select is((select count(*) from public.players where team_slot_id = 'c3010000-0000-0000-0000-000000000001'), 6::bigint, 'maximum six-player roster is stored');
select is((select revision from public.tournaments where id = 'c2000000-0000-0000-0000-000000000001'), 4, 'six-player replacement advances revision once');

select is((select outcome from public.replace_tournament_roster_snapshot_v3(
    (select tournament_id from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (
        select jsonb_agg(case
            when player_row ->> 'id' = 'c5010000-0000-0000-0000-000000000001' then
                jsonb_set(jsonb_set(player_row, '{display_name}', to_jsonb('Six Player 2'::text)), '{normalized_name}', to_jsonb('Six Player 2'::text))
            else player_row
        end order by player_row ->> 'id')
        from jsonb_array_elements((select six_players from pg_temp.v3_roster_payloads where payload_key = 'main')) as rows(player_row)
    ),
    4
)), 'validation_failure', 'duplicate normalized player names are rejected');
select is((select string_agg(display_name, ',' order by display_name) from public.players where team_slot_id = 'c3010000-0000-0000-0000-000000000001'), 'Six Player 1,Six Player 2,Six Player 3,Six Player 4,Six Player 5,Six Player 6', 'duplicate player rejection preserves all names');
select is((select revision from public.tournaments where id = 'c2000000-0000-0000-0000-000000000001'), 4, 'duplicate player rejection preserves revision');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'c2000000-0000-0000-0000-000000000001'), 24::bigint, 'duplicate player rejection preserves mappings');

select is((select outcome from public.replace_tournament_roster_snapshot_v3(
    (select tournament_id from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (
        select (select six_players from pg_temp.v3_roster_payloads where payload_key = 'main') || jsonb_build_array(jsonb_build_object(
            'id', 'c5010000-0000-0000-0000-000000000007'::uuid,
            'team_slot_id', 'c3010000-0000-0000-0000-000000000001'::uuid,
            'display_name', 'Six Player 7', 'normalized_name', 'Six Player 7'
        ))
    ),
    4
)), 'validation_failure', 'more than six players per permanent team are rejected');
select is((select count(*) from public.players where team_slot_id = 'c3010000-0000-0000-0000-000000000001'), 6::bigint, 'maximum-player rejection preserves six players');
select is((select count(*) from public.players where id = 'c5010000-0000-0000-0000-000000000007'), 0::bigint, 'maximum-player rejection inserts no seventh player');
select is((select revision from public.tournaments where id = 'c2000000-0000-0000-0000-000000000001'), 4, 'maximum-player rejection preserves revision');

select is((select outcome from public.replace_tournament_roster_snapshot_v3(
    (select tournament_id from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_roster_payloads where payload_key = 'main'),
    jsonb_build_array(jsonb_build_object(
        'id', 'c6010000-0000-0000-0000-000000000001'::uuid,
        'team_slot_id', 'c3010000-0000-0000-0000-000000000001'::uuid,
        'display_name', 'Other Player', 'normalized_name', 'Other Player'
    )),
    4
)), 'validation_failure', 'foreign-tournament player UUIDs are rejected');

select is((select outcome from public.replace_tournament_roster_snapshot_v3(
    (select tournament_id from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_roster_payloads where payload_key = 'main'),
    jsonb_build_array(jsonb_build_object(
        'id', 'c5010000-0000-0000-0000-000000000001'::uuid,
        'team_slot_id', 'c3010000-0000-0000-0000-000000000002'::uuid,
        'display_name', 'Six Player 1', 'normalized_name', 'Six Player 1'
    )),
    4
)), 'validation_failure', 'reassigned existing player UUIDs are rejected');

select is((select outcome from public.replace_tournament_roster_snapshot_v3(
    (select tournament_id from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (
        select jsonb_agg(slot_row order by slot_row ->> 'slot_number')
        from jsonb_array_elements((select team_slots from pg_temp.v3_roster_payloads where payload_key = 'main')) as rows(slot_row)
        where slot_row ->> 'slot_number' <> '18'
    ),
    (select six_players from pg_temp.v3_roster_payloads where payload_key = 'main'),
    4
)), 'validation_failure', 'missing permanent TeamSlot identities are rejected');

select is((select outcome from public.replace_tournament_roster_snapshot_v3(
    (select tournament_id from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (
        select jsonb_agg(case
            when slot_row ->> 'slot_number' = '18' then
                jsonb_set(slot_row, '{id}', to_jsonb('c3980000-0000-0000-0000-000000000018'::text))
            else slot_row
        end order by slot_row ->> 'slot_number')
        from jsonb_array_elements((select team_slots from pg_temp.v3_roster_payloads where payload_key = 'main')) as rows(slot_row)
    ),
    (select six_players from pg_temp.v3_roster_payloads where payload_key = 'main'),
    4
)), 'validation_failure', 'changed permanent TeamSlot identities are rejected');

select is((select outcome from public.replace_tournament_roster_snapshot_v3(
    (select tournament_id from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (
        select jsonb_agg(case
            when slot_row ->> 'slot_number' = '1' then
                jsonb_set(slot_row, '{team_name}', to_jsonb('Renamed Team'::text))
            else slot_row
        end order by slot_row ->> 'slot_number')
        from jsonb_array_elements((select team_slots from pg_temp.v3_roster_payloads where payload_key = 'main')) as rows(slot_row)
    ),
    (select six_players from pg_temp.v3_roster_payloads where payload_key = 'main'),
    4
)), 'validation_failure', 'occupied permanent team names cannot be changed');
select is((select revision from public.tournaments where id = 'c2000000-0000-0000-0000-000000000001'), 4, 'identity validation failures preserve revision');
select is((select count(*) from public.players where team_slot_id = 'c3010000-0000-0000-0000-000000000001'), 6::bigint, 'identity validation failures preserve roster rows');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'c2000000-0000-0000-0000-000000000001'), 24::bigint, 'identity validation failures preserve mappings');

insert into public.matches (id, tournament_id, match_number, status, group_pairing_key)
values ('c4010000-0000-0000-0000-000000000001', 'c2000000-0000-0000-0000-000000000001', 1, 'draft', 'A:B');

select is((select outcome from public.replace_tournament_roster_snapshot_v3(
    (select tournament_id from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (select six_players from pg_temp.v3_roster_payloads where payload_key = 'main'),
    4
)), 'matches_exist', 'draft matches block full roster replacement');

reset role;
update public.matches set status = 'finalized' where id = 'c4010000-0000-0000-0000-000000000001';
set local role authenticated;
set local request.jwt.claim.sub = 'c1000000-0000-0000-0000-000000000001';
select is((select outcome from public.replace_tournament_roster_snapshot_v3(
    (select tournament_id from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (select six_players from pg_temp.v3_roster_payloads where payload_key = 'main'),
    4
)), 'matches_exist', 'finalized matches also block full roster replacement');
select is((select revision from public.tournaments where id = 'c2000000-0000-0000-0000-000000000001'), 4, 'history protection preserves revision');

reset role;
delete from public.matches where id = 'c4010000-0000-0000-0000-000000000001';
set local role authenticated;
set local request.jwt.claim.sub = 'c1000000-0000-0000-0000-000000000001';

select is((select outcome from public.replace_tournament_roster_snapshot_v3(
    (select tournament_id from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (select six_players from pg_temp.v3_roster_payloads where payload_key = 'main'),
    3
)), 'stale_write', 'stale roster replacement is rejected');
select is((select revision from public.tournaments where id = 'c2000000-0000-0000-0000-000000000001'), 4, 'stale replacement preserves revision');
select is((select count(*) from public.players where team_slot_id = 'c3010000-0000-0000-0000-000000000001'), 6::bigint, 'stale replacement preserves roster rows');

set local request.jwt.claim.sub = 'c1000000-0000-0000-0000-000000000002';
select is((select outcome from public.replace_tournament_roster_snapshot_v3(
    (select tournament_id from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_roster_payloads where payload_key = 'main'),
    (select six_players from pg_temp.v3_roster_payloads where payload_key = 'main'),
    4
)), 'missing_revision', 'invisible owner tournament returns missing_revision under RLS');
set local request.jwt.claim.sub = 'c1000000-0000-0000-0000-000000000001';

select is((select outcome from public.replace_tournament_roster_snapshot_v3(
    (select tournament_id from pg_temp.v3_roster_payloads where payload_key = 'four'),
    (select team_slots from pg_temp.v3_roster_payloads where payload_key = 'four'),
    (select replacement_players from pg_temp.v3_roster_payloads where payload_key = 'four'),
    1
)), 'success', 'four-group roster replacement accepts the complete 24-slot pool');
select is((select count(*) from public.tournament_team_slots where tournament_id = 'c2000000-0000-0000-0000-000000000002'), 24::bigint, 'four-group replacement preserves all 24 permanent slots');
select is((select team_slot_number from public.tournament_group_pairing_lobby_slots where tournament_id = 'c2000000-0000-0000-0000-000000000002' and pairing_key = 'A:B' and lobby_slot_number = 12), 24, 'four-group mapping remains canonical slot twenty-four');

select is((select outcome from public.replace_tournament_roster_snapshot_v3(
    (select tournament_id from pg_temp.v3_roster_payloads where payload_key = 'legacy'),
    (select team_slots from pg_temp.v3_roster_payloads where payload_key = 'legacy'),
    '[]'::jsonb,
    1
)), 'success', 'legacy group metadata is accepted and preserved');
select is((select "group" from public.tournament_team_slots where tournament_id = 'c2000000-0000-0000-0000-000000000003' and slot_number = 13), 'C', 'legacy group metadata remains unchanged');

select * from finish();
rollback;
