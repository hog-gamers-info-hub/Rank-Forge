begin;

select plan(56);

select has_column('public', 'matches', 'group_pairing_key', 'matches retain pairing metadata');
select ok(exists (
    select 1
    from pg_constraint
    where conrelid = 'public.matches'::regclass
        and conname = 'matches_group_pairing_fkey'
), 'matches retain the pairing foreign key');
select ok(to_regprocedure('public.write_tournament_snapshot(jsonb,jsonb,jsonb,integer)') is not null, 'tournament snapshot signature is unchanged');
select ok(to_regprocedure('public.replace_tournament_roster_snapshot(uuid,jsonb,jsonb,integer)') is not null, 'roster snapshot signature is unchanged');
select ok(to_regprocedure('public.write_match_snapshot(uuid,jsonb,jsonb,integer)') is not null, 'match snapshot signature is unchanged');
select ok(to_regprocedure('public.finalize_match_snapshot(uuid,jsonb,jsonb,integer)') is not null, 'finalization snapshot signature is unchanged');

select ok(not has_function_privilege('anon', 'public.write_tournament_snapshot(jsonb,jsonb,jsonb,integer)', 'execute'), 'anon cannot execute tournament snapshots');
select ok(has_function_privilege('authenticated', 'public.write_tournament_snapshot(jsonb,jsonb,jsonb,integer)', 'execute'), 'authenticated can execute tournament snapshots');
select ok(not has_function_privilege('anon', 'public.replace_tournament_roster_snapshot(uuid,jsonb,jsonb,integer)', 'execute'), 'anon cannot execute roster snapshots');
select ok(has_function_privilege('authenticated', 'public.replace_tournament_roster_snapshot(uuid,jsonb,jsonb,integer)', 'execute'), 'authenticated can execute roster snapshots');
select ok(not has_function_privilege('anon', 'public.write_match_snapshot(uuid,jsonb,jsonb,integer)', 'execute'), 'anon cannot execute match snapshots');
select ok(has_function_privilege('authenticated', 'public.write_match_snapshot(uuid,jsonb,jsonb,integer)', 'execute'), 'authenticated can execute match snapshots');
select ok(not has_function_privilege('anon', 'public.finalize_match_snapshot(uuid,jsonb,jsonb,integer)', 'execute'), 'anon cannot execute finalization snapshots');
select ok(has_function_privilege('authenticated', 'public.finalize_match_snapshot(uuid,jsonb,jsonb,integer)', 'execute'), 'authenticated can execute finalization snapshots');

select is((select prosecdef from pg_proc where oid = 'public.write_tournament_snapshot(jsonb,jsonb,jsonb,integer)'::regprocedure), false, 'tournament snapshot remains SECURITY INVOKER');
select is((select prosecdef from pg_proc where oid = 'public.replace_tournament_roster_snapshot(uuid,jsonb,jsonb,integer)'::regprocedure), false, 'roster snapshot remains SECURITY INVOKER');
select is((select prosecdef from pg_proc where oid = 'public.write_match_snapshot(uuid,jsonb,jsonb,integer)'::regprocedure), false, 'match snapshot remains SECURITY INVOKER');
select is((select prosecdef from pg_proc where oid = 'public.finalize_match_snapshot(uuid,jsonb,jsonb,integer)'::regprocedure), true, 'finalization snapshot remains SECURITY DEFINER');

create temporary table snapshot_payloads (
    payload_key text primary key,
    tournament_id uuid not null,
    team_slots jsonb not null,
    players jsonb not null
);

insert into pg_temp.snapshot_payloads(payload_key, tournament_id, team_slots, players)
values
(
    'standard',
    'c2000000-0000-0000-0000-000000000003',
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('c3100000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'tournament_id', 'c2000000-0000-0000-0000-000000000003',
            'slot_number', slot_number,
            'team_name', 'Standard Team ' || slot_number,
            'status', 'draft'
        ) order by slot_number)
        from generate_series(1, 12) as slots(slot_number)
    ),
    '[]'::jsonb
),
(
    'three',
    'c2000000-0000-0000-0000-000000000001',
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('c3200000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'tournament_id', 'c2000000-0000-0000-0000-000000000001',
            'slot_number', slot_number,
            'team_name', 'Three Team ' || slot_number,
            'status', 'draft',
            'group', case
                when slot_number between 1 and 6 then 'A'
                when slot_number between 7 and 12 then 'B'
                else 'C'
            end
        ) order by slot_number)
        from generate_series(1, 18) as slots(slot_number)
    ),
    '[]'::jsonb
),
(
    'four',
    'c2000000-0000-0000-0000-000000000002',
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('c3300000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'tournament_id', 'c2000000-0000-0000-0000-000000000002',
            'slot_number', slot_number,
            'team_name', 'Four Team ' || slot_number,
            'status', 'draft',
            'group', case
                when slot_number between 1 and 6 then 'A'
                when slot_number between 7 and 12 then 'B'
                when slot_number between 13 and 18 then 'C'
                else 'D'
            end
        ) order by slot_number)
        from generate_series(1, 24) as slots(slot_number)
    ),
    '[]'::jsonb
),
(
    'four_to_three',
    'c2000000-0000-0000-0000-000000000002',
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('c3500000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'tournament_id', 'c2000000-0000-0000-0000-000000000002',
            'slot_number', slot_number,
            'team_name', 'Reduced Team ' || slot_number,
            'status', 'draft',
            'group', case
                when slot_number between 1 and 6 then 'A'
                when slot_number between 7 and 12 then 'B'
                else 'C'
            end
        ) order by slot_number)
        from generate_series(1, 18) as slots(slot_number)
    ),
    '[]'::jsonb
),
(
    'roster18',
    'c2000000-0000-0000-0000-000000000004',
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('c3400000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'tournament_id', 'c2000000-0000-0000-0000-000000000004',
            'slot_number', slot_number,
            'team_name', 'Roster Team ' || slot_number,
            'status', 'draft',
            'group', case
                when slot_number between 1 and 6 then 'A'
                when slot_number between 7 and 12 then 'B'
                else 'C'
            end
        ) order by slot_number)
        from generate_series(1, 18) as slots(slot_number)
    ),
    jsonb_build_array(jsonb_build_object(
        'id', 'c5000000-0000-0000-0000-000000000001',
        'team_slot_id', 'c3400000-0000-0000-0000-000000000001',
        'display_name', 'Roster Player',
        'normalized_name', 'Roster Player'
    ))
);

grant select on snapshot_payloads to authenticated;

insert into auth.users (id, email)
values ('c1000000-0000-0000-0000-000000000001', 'backend-hardening-owner@example.test');

set local role authenticated;
set local request.jwt.claim.sub = 'c1000000-0000-0000-0000-000000000001';

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'c2000000-0000-0000-0000-000000000003',
            'owner_id', 'c1000000-0000-0000-0000-000000000001',
            'name', 'Standard Compatibility',
            'organizer_name', 'Organizer',
            'organizer_contact', '',
            'status', 'draft'
        ),
        (select team_slots from pg_temp.snapshot_payloads where payload_key = 'standard'),
        '[]'::jsonb,
        0
    )
), 'success', 'standard snapshot remains accepted');
select is((select format from public.tournaments where id = 'c2000000-0000-0000-0000-000000000003'), 'standard', 'standard snapshot still defaults to standard');
select is((select group_count from public.tournaments where id = 'c2000000-0000-0000-0000-000000000003'), null::integer, 'standard snapshot keeps group_count null');
select is((select count(*) from public.tournament_group_pairings where tournament_id = 'c2000000-0000-0000-0000-000000000003'), 0::bigint, 'standard snapshot requires no pairings');
select is((select count(*) from public.tournament_team_slots where tournament_id = 'c2000000-0000-0000-0000-000000000003'), 12::bigint, 'standard snapshot keeps twelve slots');

insert into public.matches (id, tournament_id, match_number, status)
values ('c4000000-0000-0000-0000-000000000003', 'c2000000-0000-0000-0000-000000000003', 1, 'draft');
select is((select group_pairing_key from public.matches where id = 'c4000000-0000-0000-0000-000000000003'), null, 'standard draft match keeps a null pairing');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'c2000000-0000-0000-0000-000000000001',
            'owner_id', 'c1000000-0000-0000-0000-000000000001',
            'name', 'Three Groups',
            'organizer_name', 'Organizer',
            'organizer_contact', '',
            'status', 'draft',
            'format', 'group_rotation',
            'group_count', 3,
            'selected_group_pairings', jsonb_build_array(
                jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B'),
                jsonb_build_object('first_group', 'A', 'second_group', 'C', 'pairing_key', 'A:C')
            )
        ),
        (select team_slots from pg_temp.snapshot_payloads where payload_key = 'three'),
        '[]'::jsonb,
        0
    )
), 'success', 'three-group snapshot creates the pairing set');
select is((select count(*) from public.tournament_group_pairings where tournament_id = 'c2000000-0000-0000-0000-000000000001'), 2::bigint, 'three-group candidate pairings are persisted');

insert into public.matches (id, tournament_id, match_number, status, group_pairing_key)
values ('c4000000-0000-0000-0000-000000000001', 'c2000000-0000-0000-0000-000000000001', 1, 'draft', 'A:B');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'c2000000-0000-0000-0000-000000000001',
            'owner_id', 'c1000000-0000-0000-0000-000000000001',
            'name', 'Three Groups',
            'organizer_name', 'Organizer',
            'organizer_contact', '',
            'status', 'draft',
            'format', 'group_rotation',
            'group_count', 3,
            'selected_group_pairings', jsonb_build_array(
                jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B'),
                jsonb_build_object('first_group', 'A', 'second_group', 'C', 'pairing_key', 'A:C')
            )
        ),
        (select team_slots from pg_temp.snapshot_payloads where payload_key = 'three'),
        '[]'::jsonb,
        1
    )
), 'success', 'unchanged referenced pairing snapshot succeeds');
select is((select revision from public.tournaments where id = 'c2000000-0000-0000-0000-000000000001'), 2, 'unchanged pairing snapshot advances revision once');
select is((select pairing_key from public.tournament_group_pairings where tournament_id = 'c2000000-0000-0000-0000-000000000001' and pairing_key = 'A:B'), 'A:B', 'referenced A:B pairing remains intact');
select is((select group_pairing_key from public.matches where id = 'c4000000-0000-0000-0000-000000000001'), 'A:B', 'match still references A:B');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'c2000000-0000-0000-0000-000000000001',
            'owner_id', 'c1000000-0000-0000-0000-000000000001',
            'name', 'Three Groups',
            'organizer_name', 'Organizer',
            'organizer_contact', '',
            'status', 'draft',
            'format', 'group_rotation',
            'group_count', 3,
            'selected_group_pairings', jsonb_build_array(
                jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B')
            )
        ),
        (select team_slots from pg_temp.snapshot_payloads where payload_key = 'three'),
        '[]'::jsonb,
        2
    )
), 'success', 'unreferenced stale pairing can be removed');
select is((select count(*) from public.tournament_group_pairings where tournament_id = 'c2000000-0000-0000-0000-000000000001' and pairing_key = 'A:C'), 0::bigint, 'unreferenced A:C pairing is deleted');
select is((select pairing_key from public.tournament_group_pairings where tournament_id = 'c2000000-0000-0000-0000-000000000001' and pairing_key = 'A:B'), 'A:B', 'referenced A:B survives stale cleanup');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'c2000000-0000-0000-0000-000000000001',
            'owner_id', 'c1000000-0000-0000-0000-000000000001',
            'name', 'Rejected Pairing Removal',
            'organizer_name', 'Organizer',
            'organizer_contact', '',
            'status', 'draft',
            'format', 'group_rotation',
            'group_count', 3,
            'selected_group_pairings', jsonb_build_array(
                jsonb_build_object('first_group', 'A', 'second_group', 'C', 'pairing_key', 'A:C')
            )
        ),
        jsonb_set((select team_slots from pg_temp.snapshot_payloads where payload_key = 'three'), '{0,team_name}', to_jsonb('Changed Team 1'::text), false),
        '[]'::jsonb,
        3
    )
), 'validation_failure', 'in-use pairing removal fails before mutation');
select is((select revision from public.tournaments where id = 'c2000000-0000-0000-0000-000000000001'), 3, 'in-use pairing rejection leaves revision unchanged');
select is((select name from public.tournaments where id = 'c2000000-0000-0000-0000-000000000001'), 'Three Groups', 'in-use pairing rejection leaves tournament name unchanged');
select is((select team_name from public.tournament_team_slots where tournament_id = 'c2000000-0000-0000-0000-000000000001' and slot_number = 1), 'Three Team 1', 'in-use pairing rejection leaves team data unchanged');
select is((select pairing_key from public.tournament_group_pairings where tournament_id = 'c2000000-0000-0000-0000-000000000001' and pairing_key = 'A:B'), 'A:B', 'in-use pairing remains persisted');
select is((select group_pairing_key from public.matches where id = 'c4000000-0000-0000-0000-000000000001'), 'A:B', 'in-use match remains unchanged');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'c2000000-0000-0000-0000-000000000002',
            'owner_id', 'c1000000-0000-0000-0000-000000000001',
            'name', 'Four Groups',
            'organizer_name', 'Organizer',
            'organizer_contact', '',
            'status', 'draft',
            'format', 'group_rotation',
            'group_count', 4,
            'selected_group_pairings', jsonb_build_array(
                jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B'),
                jsonb_build_object('first_group', 'A', 'second_group', 'C', 'pairing_key', 'A:C'),
                jsonb_build_object('first_group', 'A', 'second_group', 'D', 'pairing_key', 'A:D')
            )
        ),
        (select team_slots from pg_temp.snapshot_payloads where payload_key = 'four'),
        '[]'::jsonb,
        0
    )
), 'success', 'four-group snapshot creates a D pairing');
insert into public.matches (id, tournament_id, match_number, status, group_pairing_key)
values ('c4000000-0000-0000-0000-000000000002', 'c2000000-0000-0000-0000-000000000002', 1, 'draft', 'A:D');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'c2000000-0000-0000-0000-000000000002',
            'owner_id', 'c1000000-0000-0000-0000-000000000001',
            'name', 'Rejected Four-to-Three',
            'organizer_name', 'Organizer',
            'organizer_contact', '',
            'status', 'draft',
            'format', 'group_rotation',
            'group_count', 3,
            'selected_group_pairings', jsonb_build_array(
                jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B'),
                jsonb_build_object('first_group', 'A', 'second_group', 'C', 'pairing_key', 'A:C')
            )
        ),
        (select team_slots from pg_temp.snapshot_payloads where payload_key = 'four_to_three'),
        '[]'::jsonb,
        1
    )
), 'validation_failure', 'format change that removes an in-use D pairing fails closed');
select is((select name from public.tournaments where id = 'c2000000-0000-0000-0000-000000000002'), 'Four Groups', 'failed format change leaves tournament name unchanged');
select is((select format from public.tournaments where id = 'c2000000-0000-0000-0000-000000000002'), 'group_rotation', 'failed format change leaves format unchanged');
select is((select group_count from public.tournaments where id = 'c2000000-0000-0000-0000-000000000002'), 4, 'failed format change leaves group count unchanged');
select is((select revision from public.tournaments where id = 'c2000000-0000-0000-0000-000000000002'), 1, 'failed format change leaves revision unchanged');
select is((select pairing_key from public.tournament_group_pairings where tournament_id = 'c2000000-0000-0000-0000-000000000002' and pairing_key = 'A:D'), 'A:D', 'failed format change keeps the D pairing');
select is((select group_pairing_key from public.matches where id = 'c4000000-0000-0000-0000-000000000002'), 'A:D', 'failed format change keeps the referencing match');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'c2000000-0000-0000-0000-000000000004',
            'owner_id', 'c1000000-0000-0000-0000-000000000001',
            'name', 'Roster 18',
            'organizer_name', 'Organizer',
            'organizer_contact', '',
            'status', 'draft',
            'format', 'group_rotation',
            'group_count', 3,
            'selected_group_pairings', jsonb_build_array(
                jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B')
            )
        ),
        (select team_slots from pg_temp.snapshot_payloads where payload_key = 'roster18'),
        (select players from pg_temp.snapshot_payloads where payload_key = 'roster18'),
        0
    )
), 'success', 'Group Rotation 18-slot roster is created');

select is((
    select outcome
    from public.replace_tournament_roster_snapshot(
        'c2000000-0000-0000-0000-000000000004',
        (select team_slots from pg_temp.snapshot_payloads where payload_key = 'roster18'),
        jsonb_build_array(jsonb_build_object(
            'id', 'c5000000-0000-0000-0000-000000000001',
            'team_slot_id', 'c3400000-0000-0000-0000-000000000002',
            'display_name', 'Roster Player Moved',
            'normalized_name', 'Roster Player Moved'
        )),
        1
    )
), 'validation_failure', 'existing player UUID cannot move to another team slot');
select is((select team_slot_id from public.players where id = 'c5000000-0000-0000-0000-000000000001'), 'c3400000-0000-0000-0000-000000000001'::uuid, 'rejected player move leaves the original slot');
select is((select revision from public.tournaments where id = 'c2000000-0000-0000-0000-000000000004'), 1, 'rejected player move leaves roster revision unchanged');

select is((
    select outcome
    from public.replace_tournament_roster_snapshot(
        'c2000000-0000-0000-0000-000000000004',
        (select team_slots from pg_temp.snapshot_payloads where payload_key = 'roster18'),
        jsonb_build_array(jsonb_build_object(
            'id', 'c5000000-0000-0000-0000-000000000001',
            'team_slot_id', 'c3400000-0000-0000-0000-000000000001',
            'display_name', 'Roster Player Updated',
            'normalized_name', 'Roster Player Updated'
        )),
        1
    )
), 'success', 'same-slot player update remains allowed');
select is((select display_name from public.players where id = 'c5000000-0000-0000-0000-000000000001'), 'Roster Player Updated', 'same-slot player update is persisted');
select is((select revision from public.tournaments where id = 'c2000000-0000-0000-0000-000000000004'), 2, 'same-slot roster update advances revision');
select is((select count(*) from public.tournament_team_slots where tournament_id = 'c2000000-0000-0000-0000-000000000004'), 18::bigint, 'roster replacement retains Group Rotation 18-slot shape');

select ok(exists (
    select 1
    from pg_constraint
    where conrelid = 'public.matches'::regclass
        and conname = 'matches_match_number_check'
        and pg_get_constraintdef(oid) like '%18%'
), 'the eighteen-match constraint remains unchanged');

select * from finish();
rollback;
