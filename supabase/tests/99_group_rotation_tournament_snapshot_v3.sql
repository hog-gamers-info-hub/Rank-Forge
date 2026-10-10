begin;

select no_plan();

select ok(
    to_regprocedure('public.write_tournament_snapshot_v3(jsonb,jsonb,jsonb,jsonb,integer)') is not null,
    'V3 tournament snapshot signature exists'
);
select is(
    (select prosecdef
     from pg_proc
     where oid = 'public.write_tournament_snapshot_v3(jsonb,jsonb,jsonb,jsonb,integer)'::regprocedure),
    false,
    'V3 tournament snapshot is SECURITY INVOKER'
);
select ok(
    not has_function_privilege(
        'anon',
        'public.write_tournament_snapshot_v3(jsonb,jsonb,jsonb,jsonb,integer)',
        'execute'
    ),
    'anon cannot execute V3 tournament snapshot'
);
select ok(
    has_function_privilege(
        'authenticated',
        'public.write_tournament_snapshot_v3(jsonb,jsonb,jsonb,jsonb,integer)',
        'execute'
    ),
    'authenticated can execute V3 tournament snapshot'
);

create temporary table v3_payloads (
    payload_key text primary key,
    tournament_id uuid not null,
    tournament jsonb not null,
    team_slots jsonb not null,
    players jsonb not null,
    pairings jsonb not null
);

insert into auth.users (id, email)
values
    ('b1000000-0000-0000-0000-000000000001', 'phase4b3a-owner@example.test'),
    ('b1000000-0000-0000-0000-000000000002', 'phase4b3a-other@example.test');

insert into pg_temp.v3_payloads(
    payload_key, tournament_id, tournament, team_slots, players, pairings
)
values (
    'main',
    'b2000000-0000-0000-0000-000000000001',
    jsonb_build_object(
        'id', 'b2000000-0000-0000-0000-000000000001',
        'owner_id', 'b1000000-0000-0000-0000-000000000001',
        'name', 'V3 Main', 'organizer_name', 'Organizer', 'organizer_contact', '',
        'status', 'draft', 'format', 'group_rotation', 'group_count', 3,
        'selected_group_pairings', jsonb_build_array(
            jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B'),
            jsonb_build_object('first_group', 'A', 'second_group', 'C', 'pairing_key', 'A:C')
        )
    ),
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('b3010000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'tournament_id', 'b2000000-0000-0000-0000-000000000001',
            'slot_number', slot_number, 'team_name', 'V3 Team ' || slot_number,
            'status', 'draft', 'group', null
        ) order by slot_number)
        from generate_series(1, 18) as slots(slot_number)
    ),
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('b5010000-0000-0000-0000-' || lpad(player_number::text, 12, '0'))::uuid,
            'team_slot_id', 'b3010000-0000-0000-0000-000000000001',
            'display_name', 'Player ' || player_number,
            'normalized_name', 'Player ' || player_number
        ) order by player_number)
        from generate_series(1, 6) as players(player_number)
    ),
    jsonb_build_array(
        jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B'),
        jsonb_build_object('first_group', 'A', 'second_group', 'C', 'pairing_key', 'A:C')
    )
), (
    'empty',
    'b2000000-0000-0000-0000-000000000002',
    jsonb_build_object(
        'id', 'b2000000-0000-0000-0000-000000000002',
        'owner_id', 'b1000000-0000-0000-0000-000000000001',
        'name', 'V3 Empty', 'organizer_name', 'Organizer', 'organizer_contact', '',
        'status', 'draft', 'format', 'group_rotation', 'group_count', 3,
        'selected_group_pairings', jsonb_build_array(
            jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B'),
            jsonb_build_object('first_group', 'A', 'second_group', 'C', 'pairing_key', 'A:C')
        )
    ),
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('b3020000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'tournament_id', 'b2000000-0000-0000-0000-000000000002',
            'slot_number', slot_number, 'team_name', 'Empty Team ' || slot_number,
            'status', 'draft', 'group', null
        ) order by slot_number)
        from generate_series(1, 18) as slots(slot_number)
    ),
    '[]'::jsonb,
    jsonb_build_array(
        jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B'),
        jsonb_build_object('first_group', 'A', 'second_group', 'C', 'pairing_key', 'A:C')
    )
), (
    'legacy',
    'b2000000-0000-0000-0000-000000000003',
    jsonb_build_object(
        'id', 'b2000000-0000-0000-0000-000000000003',
        'owner_id', 'b1000000-0000-0000-0000-000000000001',
        'name', 'V3 Legacy', 'organizer_name', 'Organizer', 'organizer_contact', '',
        'status', 'draft', 'format', 'group_rotation', 'group_count', 3,
        'selected_group_pairings', jsonb_build_array(
            jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B')
        )
    ),
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('b3030000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'tournament_id', 'b2000000-0000-0000-0000-000000000003',
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
    jsonb_build_array(
        jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B')
    )
);

grant select on v3_payloads to authenticated;
set local role authenticated;
set local request.jwt.claim.sub = 'b1000000-0000-0000-0000-000000000001';

select is((select outcome from public.write_tournament_snapshot_v3(
    (select tournament from pg_temp.v3_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_payloads where payload_key = 'main'),
    (select players from pg_temp.v3_payloads where payload_key = 'main'),
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'b2000000-0000-0000-0000-000000000001',
            'pairing_key', 'A:B', 'lobby_slot_number', lobby_slot_number,
            'team_slot_number', lobby_slot_number
        ) order by lobby_slot_number)
        from generate_series(1, 12) as slots(lobby_slot_number)
    ),
    0
)), 'success', 'new three-group V3 snapshot accepts null group metadata and A:B only');
select is((select revision from public.tournaments where id = 'b2000000-0000-0000-0000-000000000001'), 1, 'new V3 tournament starts at revision one');
select is((select count(*) from public.tournament_team_slots where tournament_id = 'b2000000-0000-0000-0000-000000000001' and "group" is null), 18::bigint, 'new V3 identity pool preserves null group metadata');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'b2000000-0000-0000-0000-000000000001' and pairing_key = 'A:B'), 12::bigint, 'new V3 snapshot stores configured A:B mappings');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'b2000000-0000-0000-0000-000000000001' and pairing_key = 'A:C'), 0::bigint, 'selected A:C remains unconfigured');
select is((select count(*) from public.players where team_slot_id = 'b3010000-0000-0000-0000-000000000001'), 6::bigint, 'new V3 snapshot stores the supplied six-player roster');

select is((select outcome from public.write_tournament_snapshot_v3(
    (select tournament from pg_temp.v3_payloads where payload_key = 'empty'),
    (select team_slots from pg_temp.v3_payloads where payload_key = 'empty'),
    '[]'::jsonb,
    '[]'::jsonb,
    0
)), 'success', 'zero mapping rows are accepted for a new V3 tournament');
select is((select outcome from public.write_tournament_snapshot_v3(
    (select tournament from pg_temp.v3_payloads where payload_key = 'empty'),
    (select team_slots from pg_temp.v3_payloads where payload_key = 'empty'),
    '[]'::jsonb,
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'b2000000-0000-0000-0000-000000000002',
            'pairing_key', 'A:B', 'lobby_slot_number', lobby_slot_number,
            'team_slot_number', lobby_slot_number
        ) order by lobby_slot_number)
        from generate_series(1, 12) as slots(lobby_slot_number)
    ) || (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'b2000000-0000-0000-0000-000000000002',
            'pairing_key', 'A:C', 'lobby_slot_number', lobby_slot_number,
            'team_slot_number', lobby_slot_number + 6
        ) order by lobby_slot_number)
        from generate_series(1, 12) as slots(lobby_slot_number)
    ),
    1
)), 'success', 'complete pairing mappings are accepted after zero-mapping setup');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'b2000000-0000-0000-0000-000000000002'), 24::bigint, 'initial pairing setup stores both configured mappings');

select is((select outcome from public.write_tournament_snapshot_v3(
    (select tournament from pg_temp.v3_payloads where payload_key = 'empty'),
    (select team_slots from pg_temp.v3_payloads where payload_key = 'empty'),
    '[]'::jsonb,
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'b2000000-0000-0000-0000-000000000002',
            'pairing_key', 'A:B', 'lobby_slot_number', lobby_slot_number,
            'team_slot_number', case when lobby_slot_number = 1 then 13 else lobby_slot_number end
        ) order by lobby_slot_number)
        from generate_series(1, 12) as slots(lobby_slot_number)
    ) || (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'b2000000-0000-0000-0000-000000000002',
            'pairing_key', 'A:C', 'lobby_slot_number', lobby_slot_number,
            'team_slot_number', lobby_slot_number + 6
        ) order by lobby_slot_number)
        from generate_series(1, 12) as slots(lobby_slot_number)
    ),
    2
)), 'success', 'a configured pairing can be replaced before match history exists');
select is((select team_slot_number from public.tournament_group_pairing_lobby_slots where tournament_id = 'b2000000-0000-0000-0000-000000000002' and pairing_key = 'A:B' and lobby_slot_number = 1), 13, 'pairing replacement stores the new A:B mapping');
select is((select team_slot_number from public.tournament_group_pairing_lobby_slots where tournament_id = 'b2000000-0000-0000-0000-000000000002' and pairing_key = 'A:C' and lobby_slot_number = 1), 7, 'pairing replacement preserves unrelated A:C mappings');

select is((select outcome from public.write_tournament_snapshot_v3(
    (select tournament from pg_temp.v3_payloads where payload_key = 'legacy'),
    (select team_slots from pg_temp.v3_payloads where payload_key = 'legacy'),
    '[]'::jsonb,
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'b2000000-0000-0000-0000-000000000003',
            'pairing_key', 'A:B', 'lobby_slot_number', lobby_slot_number,
            'team_slot_number', lobby_slot_number
        ) order by lobby_slot_number)
        from generate_series(1, 12) as slots(lobby_slot_number)
    ),
    0
)), 'success', 'legacy group metadata is accepted for a new V3 tournament');
select is((select "group" from public.tournament_team_slots where tournament_id = 'b2000000-0000-0000-0000-000000000003' and slot_number = 13), 'C', 'legacy group metadata is preserved');
select is((select outcome from public.write_tournament_snapshot_v3(
    (select tournament from pg_temp.v3_payloads where payload_key = 'legacy'),
    (select team_slots from pg_temp.v3_payloads where payload_key = 'legacy'),
    '[]'::jsonb,
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'b2000000-0000-0000-0000-000000000003',
            'pairing_key', 'A:B', 'lobby_slot_number', lobby_slot_number,
            'team_slot_number', lobby_slot_number
        ) order by lobby_slot_number)
        from generate_series(1, 12) as slots(lobby_slot_number)
    ),
    1
)), 'success', 'existing legacy group metadata can be replayed unchanged');

insert into public.matches (id, tournament_id, match_number, status, group_pairing_key)
values ('b4010000-0000-0000-0000-000000000001', 'b2000000-0000-0000-0000-000000000001', 1, 'draft', 'A:B');

select is((select outcome from public.write_tournament_snapshot_v3(
    (select tournament from pg_temp.v3_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_payloads where payload_key = 'main'),
    '[]'::jsonb,
    (
        select coalesce(
            (
                select jsonb_agg(jsonb_build_object(
                    'tournament_id', 'b2000000-0000-0000-0000-000000000001',
                    'pairing_key', 'A:B', 'lobby_slot_number', lobby_slot_number,
                    'team_slot_number', lobby_slot_number
                ) order by lobby_slot_number)
                from generate_series(1, 12) as slots(lobby_slot_number)
            ), '[]'::jsonb
        ) || (
            select jsonb_agg(jsonb_build_object(
                'tournament_id', 'b2000000-0000-0000-0000-000000000001',
                'pairing_key', 'A:C', 'lobby_slot_number', lobby_slot_number,
                'team_slot_number', lobby_slot_number + 6
            ) order by lobby_slot_number)
            from generate_series(1, 12) as slots(lobby_slot_number)
        )
    ),
    1
)), 'success', 'A:C can be added later without losing A:B');
select is((select revision from public.tournaments where id = 'b2000000-0000-0000-0000-000000000001'), 2, 'adding a pairing advances the revision');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'b2000000-0000-0000-0000-000000000001' and pairing_key = 'A:B'), 12::bigint, 'A:B remains configured after A:C is added');
select is((select team_slot_number from public.tournament_group_pairing_lobby_slots where tournament_id = 'b2000000-0000-0000-0000-000000000001' and pairing_key = 'A:B' and lobby_slot_number = 1), 1, 'A:B mapping identity remains unchanged after A:C is added');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'b2000000-0000-0000-0000-000000000001' and pairing_key = 'A:C'), 12::bigint, 'A:C is added with a complete mapping');
select is((select count(*) from public.players where team_slot_id = 'b3010000-0000-0000-0000-000000000001'), 6::bigint, 'omitted players do not delete an existing roster row');

select is((select outcome from public.write_tournament_snapshot_v3(
    (select tournament from pg_temp.v3_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_payloads where payload_key = 'main'),
    jsonb_build_array(jsonb_build_object(
        'id', 'b5010000-0000-0000-0000-000000000007',
        'team_slot_id', 'b3010000-0000-0000-0000-000000000001',
        'display_name', 'Player 7', 'normalized_name', 'Player 7'
    )),
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', tournament_id,
            'pairing_key', pairing_key,
            'lobby_slot_number', lobby_slot_number,
            'team_slot_number', team_slot_number
        ) order by pairing_key, lobby_slot_number)
        from public.tournament_group_pairing_lobby_slots
        where tournament_id = 'b2000000-0000-0000-0000-000000000001'
    ),
    2
)), 'validation_failure', 'effective roster rejects a seventh player when six omitted players remain stored');
select is((select count(*) from public.players where team_slot_id = 'b3010000-0000-0000-0000-000000000001'), 6::bigint, 'seventh-player rejection preserves all six existing players');
select is((select string_agg(display_name, ',' order by display_name) from public.players where team_slot_id = 'b3010000-0000-0000-0000-000000000001'), 'Player 1,Player 2,Player 3,Player 4,Player 5,Player 6', 'seventh-player rejection preserves existing player names');
select is((select count(*) from public.players where id = 'b5010000-0000-0000-0000-000000000007'), 0::bigint, 'seventh-player rejection inserts no new player');
select is((select revision from public.tournaments where id = 'b2000000-0000-0000-0000-000000000001'), 2, 'seventh-player rejection preserves revision');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'b2000000-0000-0000-0000-000000000001'), 24::bigint, 'seventh-player rejection preserves existing mappings');
select is((select count(*) from public.tournament_group_pairing_lobby_slots
    where tournament_id = 'b2000000-0000-0000-0000-000000000001'
      and ((pairing_key = 'A:B' and team_slot_number <> lobby_slot_number)
        or (pairing_key = 'A:C' and team_slot_number <> lobby_slot_number + 6))), 0::bigint,
    'seventh-player rejection preserves every current pairing mapping');

select is((select outcome from public.write_tournament_snapshot_v3(
    (select tournament from pg_temp.v3_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_payloads where payload_key = 'main'),
    jsonb_build_array(jsonb_build_object(
        'id', 'b5010000-0000-0000-0000-000000000001',
        'team_slot_id', 'b3010000-0000-0000-0000-000000000001',
        'display_name', 'Player 2', 'normalized_name', 'Player 2'
    )),
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', tournament_id,
            'pairing_key', pairing_key,
            'lobby_slot_number', lobby_slot_number,
            'team_slot_number', team_slot_number
        ) order by pairing_key, lobby_slot_number)
        from public.tournament_group_pairing_lobby_slots
        where tournament_id = 'b2000000-0000-0000-0000-000000000001'
    ),
    2
)), 'validation_failure', 'effective roster rejects a normalized duplicate after replacing one existing player');
select is((select count(*) from public.players where team_slot_id = 'b3010000-0000-0000-0000-000000000001'), 6::bigint, 'normalized duplicate rejection preserves the existing roster');
select is((select string_agg(display_name, ',' order by display_name) from public.players where team_slot_id = 'b3010000-0000-0000-0000-000000000001'), 'Player 1,Player 2,Player 3,Player 4,Player 5,Player 6', 'normalized duplicate rejection preserves existing player names');
select is((select revision from public.tournaments where id = 'b2000000-0000-0000-0000-000000000001'), 2, 'normalized duplicate rejection preserves revision');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'b2000000-0000-0000-0000-000000000001'), 24::bigint, 'normalized duplicate rejection preserves existing mappings');
select is((select count(*) from public.tournament_group_pairing_lobby_slots
    where tournament_id = 'b2000000-0000-0000-0000-000000000001'
      and ((pairing_key = 'A:B' and team_slot_number <> lobby_slot_number)
        or (pairing_key = 'A:C' and team_slot_number <> lobby_slot_number + 6))), 0::bigint,
    'normalized duplicate rejection preserves every current pairing mapping');

select is((select outcome from public.write_tournament_snapshot_v3(
    (select tournament from pg_temp.v3_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_payloads where payload_key = 'main'),
    '[]'::jsonb,
    (
        (
            select jsonb_agg(jsonb_build_object(
                'tournament_id', 'b2000000-0000-0000-0000-000000000001',
                'pairing_key', 'A:B', 'lobby_slot_number', lobby_slot_number,
                'team_slot_number', case when lobby_slot_number = 1 then 13 else lobby_slot_number end
            ) order by lobby_slot_number)
            from generate_series(1, 12) as slots(lobby_slot_number)
        ) || (
            select jsonb_agg(jsonb_build_object(
                'tournament_id', 'b2000000-0000-0000-0000-000000000001',
                'pairing_key', 'A:C', 'lobby_slot_number', lobby_slot_number,
                'team_slot_number', lobby_slot_number + 6
            ) order by lobby_slot_number)
            from generate_series(1, 12) as slots(lobby_slot_number)
        )
    ),
    2
)), 'validation_failure', 'A:B remapping is rejected after A:B match history');
select is((select revision from public.tournaments where id = 'b2000000-0000-0000-0000-000000000001'), 2, 'historical mapping rejection preserves revision');
select is((select team_slot_number from public.tournament_group_pairing_lobby_slots where tournament_id = 'b2000000-0000-0000-0000-000000000001' and pairing_key = 'A:B' and lobby_slot_number = 1), 1, 'historical mapping rejection preserves A:B mapping');
select is((select team_name from public.tournament_team_slots where tournament_id = 'b2000000-0000-0000-0000-000000000001' and slot_number = 1), 'V3 Team 1', 'historical mapping rejection preserves team slots');
select is((select count(*) from public.players where team_slot_id = 'b3010000-0000-0000-0000-000000000001'), 6::bigint, 'historical mapping rejection preserves roster rows');

select is((select outcome from public.write_tournament_snapshot_v3(
    (select tournament from pg_temp.v3_payloads where payload_key = 'main'),
    (
        select jsonb_agg(
            case when slot_row ->> 'slot_number' = '1'
                then jsonb_set(slot_row, '{team_name}', to_jsonb('Changed Team'::text))
                else slot_row
            end order by slot_row ->> 'slot_number'
        )
        from jsonb_array_elements((select team_slots from pg_temp.v3_payloads where payload_key = 'main')) as rows(slot_row)
    ),
    '[]'::jsonb,
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'b2000000-0000-0000-0000-000000000001',
            'pairing_key', 'A:B', 'lobby_slot_number', lobby_slot_number,
            'team_slot_number', lobby_slot_number
        ) order by lobby_slot_number) || jsonb_agg(jsonb_build_object(
            'tournament_id', 'b2000000-0000-0000-0000-000000000001',
            'pairing_key', 'A:C', 'lobby_slot_number', lobby_slot_number,
            'team_slot_number', lobby_slot_number + 6
        ) order by lobby_slot_number)
        from generate_series(1, 12) as slots(lobby_slot_number)
    ),
    2
)), 'validation_failure', 'occupied team identity cannot be overwritten');

select is((select outcome from public.write_tournament_snapshot_v3(
    jsonb_build_object(
        'id', 'b2000000-0000-0000-0000-000000000004',
        'owner_id', 'b1000000-0000-0000-0000-000000000001',
        'name', 'V3 Duplicate', 'organizer_name', 'Organizer', 'organizer_contact', '',
        'status', 'draft', 'format', 'group_rotation', 'group_count', 3,
        'selected_group_pairings', jsonb_build_array(
            jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B')
        )
    ),
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('b3040000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'tournament_id', 'b2000000-0000-0000-0000-000000000004',
            'slot_number', slot_number,
            'team_name', case
                when slot_number = 1 then 'Duplicate Team'
                when slot_number = 2 then 'duplicate   team'
                else 'Duplicate Team ' || slot_number
            end,
            'status', 'draft', 'group', null
        ) order by slot_number)
        from generate_series(1, 18) as slots(slot_number)
    ),
    '[]'::jsonb,
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'b2000000-0000-0000-0000-000000000004',
            'pairing_key', 'A:B', 'lobby_slot_number', lobby_slot_number,
            'team_slot_number', lobby_slot_number
        ) order by lobby_slot_number)
        from generate_series(1, 12) as slots(lobby_slot_number)
    ),
    0
)), 'validation_failure', 'normalized duplicate team identities are rejected on a new valid payload');
select is((select count(*) from public.tournaments where id = 'b2000000-0000-0000-0000-000000000004'), 0::bigint, 'duplicate identity validation performs no new-tournament mutation');

select is((select outcome from public.write_tournament_snapshot_v3(
    jsonb_build_object(
        'id', 'b2000000-0000-0000-0000-000000000005',
        'owner_id', 'b1000000-0000-0000-0000-000000000001',
        'name', 'V3 Four Groups', 'organizer_name', 'Organizer', 'organizer_contact', '',
        'status', 'draft', 'format', 'group_rotation', 'group_count', 4,
        'selected_group_pairings', jsonb_build_array(
            jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B')
        )
    ),
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('b3050000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'tournament_id', 'b2000000-0000-0000-0000-000000000005',
            'slot_number', slot_number, 'team_name', 'Four Group Team ' || slot_number,
            'status', 'draft', 'group', null
        ) order by slot_number)
        from generate_series(1, 24) as slots(slot_number)
    ),
    '[]'::jsonb,
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'b2000000-0000-0000-0000-000000000005',
            'pairing_key', 'A:B', 'lobby_slot_number', lobby_slot_number,
            'team_slot_number', lobby_slot_number
        ) order by lobby_slot_number)
        from generate_series(1, 12) as slots(lobby_slot_number)
    ),
    0
)), 'success', 'new four-group V3 snapshot accepts the complete 24-slot pool');
select is((select count(*) from public.tournament_team_slots where tournament_id = 'b2000000-0000-0000-0000-000000000005'), 24::bigint, 'four-group V3 snapshot stores all 24 identities');

select is((select outcome from public.write_tournament_snapshot_v3(
    (select tournament from pg_temp.v3_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_payloads where payload_key = 'main'),
    '[]'::jsonb,
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'b2000000-0000-0000-0000-000000000001',
            'pairing_key', 'A:B', 'lobby_slot_number', lobby_slot_number,
            'team_slot_number', lobby_slot_number
        ) order by lobby_slot_number)
        from generate_series(1, 11) as slots(lobby_slot_number)
    ) || (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'b2000000-0000-0000-0000-000000000001',
            'pairing_key', 'A:C', 'lobby_slot_number', lobby_slot_number,
            'team_slot_number', lobby_slot_number + 6
        ) order by lobby_slot_number)
        from generate_series(1, 12) as slots(lobby_slot_number)
    ),
    2
)), 'validation_failure', 'partial 1..11 mapping rows are rejected');

select is((select outcome from public.write_tournament_snapshot_v3(
    (select tournament from pg_temp.v3_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_payloads where payload_key = 'main'),
    '[]'::jsonb,
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'b2000000-0000-0000-0000-000000000001',
            'pairing_key', 'A:B', 'lobby_slot_number', lobby_slot_number,
            'team_slot_number', lobby_slot_number
        ) order by lobby_slot_number) || jsonb_build_array(jsonb_build_object(
            'tournament_id', 'b2000000-0000-0000-0000-000000000001',
            'pairing_key', 'B:C', 'lobby_slot_number', 1, 'team_slot_number', 13
        ))
        from generate_series(1, 12) as slots(lobby_slot_number)
    ),
    2
)), 'validation_failure', 'unselected pairing mappings are rejected');

select is((select outcome from public.write_tournament_snapshot_v3(
    (select tournament from pg_temp.v3_payloads where payload_key = 'main'),
    (
        select jsonb_agg(slot_row order by slot_row ->> 'slot_number')
        from jsonb_array_elements((select team_slots from pg_temp.v3_payloads where payload_key = 'main')) as rows(slot_row)
        where slot_row ->> 'slot_number' <> '18'
    ),
    '[]'::jsonb, '[]'::jsonb, 2
)), 'validation_failure', 'missing permanent slot identities are rejected');

select is((select outcome from public.write_tournament_snapshot_v3(
    (select tournament from pg_temp.v3_payloads where payload_key = 'main'),
    (
        select jsonb_agg(
            case when slot_row ->> 'slot_number' = '18'
                then jsonb_set(slot_row, '{slot_number}', '19'::jsonb)
                else slot_row
            end order by slot_row ->> 'slot_number'
        )
        from jsonb_array_elements((select team_slots from pg_temp.v3_payloads where payload_key = 'main')) as rows(slot_row)
    ),
    '[]'::jsonb, '[]'::jsonb, 2
)), 'validation_failure', 'out-of-range permanent slot identities are rejected');

select is((select outcome from public.write_tournament_snapshot_v3(
    (select tournament from pg_temp.v3_payloads where payload_key = 'main'),
    (
        select jsonb_agg(
            case when slot_row ->> 'slot_number' = '1'
                then jsonb_set(slot_row, '{id}', to_jsonb('b3990000-0000-0000-0000-000000000001'::text))
                else slot_row
            end order by slot_row ->> 'slot_number'
        )
        from jsonb_array_elements((select team_slots from pg_temp.v3_payloads where payload_key = 'main')) as rows(slot_row)
    ),
    '[]'::jsonb, '[]'::jsonb, 2
)), 'validation_failure', 'permanent slot UUID reassignment is rejected');

select is((select outcome from public.write_tournament_snapshot_v3(
    (select tournament from pg_temp.v3_payloads where payload_key = 'main'),
    (
        select jsonb_agg(
            case when slot_row ->> 'slot_number' = '1'
                then jsonb_set(slot_row, '{tournament_id}', to_jsonb('b2000000-0000-0000-0000-000000000099'::text))
                else slot_row
            end order by slot_row ->> 'slot_number'
        )
        from jsonb_array_elements((select team_slots from pg_temp.v3_payloads where payload_key = 'main')) as rows(slot_row)
    ),
    '[]'::jsonb, '[]'::jsonb, 2
)), 'validation_failure', 'foreign tournament slot identities are rejected');

select is((select outcome from public.write_tournament_snapshot_v3(
    jsonb_set(
        (select tournament from pg_temp.v3_payloads where payload_key = 'main'),
        '{owner_id}', to_jsonb('b1000000-0000-0000-0000-000000000002'::text)
    ),
    (select team_slots from pg_temp.v3_payloads where payload_key = 'main'),
    '[]'::jsonb, '[]'::jsonb, 2
)), 'validation_failure', 'incoming owner different from authenticated identity is rejected as validation failure');
set local request.jwt.claim.sub = 'b1000000-0000-0000-0000-000000000002';
select is((select outcome from public.write_tournament_snapshot_v3(
    jsonb_set(
        (select tournament from pg_temp.v3_payloads where payload_key = 'main'),
        '{owner_id}', to_jsonb('b1000000-0000-0000-0000-000000000002'::text)
    ),
    (select team_slots from pg_temp.v3_payloads where payload_key = 'main'),
    '[]'::jsonb, '[]'::jsonb, 2
)), 'missing_revision', 'second authenticated user cannot see another user''s tournament through owner-only SELECT RLS');
set local request.jwt.claim.sub = 'b1000000-0000-0000-0000-000000000001';
select is((select outcome from public.write_tournament_snapshot_v3(
    (select tournament from pg_temp.v3_payloads where payload_key = 'main'),
    (select team_slots from pg_temp.v3_payloads where payload_key = 'main'),
    '[]'::jsonb, '[]'::jsonb, 1
)), 'stale_write', 'stale V3 writes are rejected');

select is((select revision from public.tournaments where id = 'b2000000-0000-0000-0000-000000000001'), 2, 'rejected V3 writes preserve revision');
select is((select team_name from public.tournament_team_slots where tournament_id = 'b2000000-0000-0000-0000-000000000001' and slot_number = 1), 'V3 Team 1', 'rejected V3 writes preserve occupied identity');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'b2000000-0000-0000-0000-000000000001'), 24::bigint, 'rejected V3 writes preserve all mapping rows');
select is((select count(*) from public.players where team_slot_id = 'b3010000-0000-0000-0000-000000000001'), 6::bigint, 'rejected V3 writes preserve roster rows');

select * from finish();
rollback;
