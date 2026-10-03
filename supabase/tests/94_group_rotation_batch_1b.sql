begin;

select plan(39);

select has_table('public', 'tournament_group_pairings', 'group pairings table exists');
select has_column('public', 'tournaments', 'format', 'tournaments stores the tournament format');
select has_column('public', 'tournaments', 'group_count', 'tournaments stores the group count');
select has_column('public', 'tournament_team_slots', 'group', 'team slots store their group identity');
select ok(exists (
    select 1
    from pg_constraint
    where conrelid = 'public.tournament_group_pairings'::regclass
        and contype = 'f'
        and confrelid = 'public.tournaments'::regclass
        and confdeltype = 'c'
), 'pairings cascade with their tournament');
select ok((select relrowsecurity from pg_class where oid = 'public.tournament_group_pairings'::regclass), 'pairings have RLS enabled');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and tablename = 'tournament_group_pairings'
), 4::bigint, 'pairings have separate CRUD ownership policies');
select ok(exists (
    select 1
    from pg_attribute
    where attrelid = 'public.tournament_group_pairings'::regclass
        and attname = 'pairing_key'
        and attgenerated = 's'
), 'pairing keys are generated from canonical group columns');

insert into auth.users (id, email)
values
    ('a1000000-0000-0000-0000-000000000001', 'rotation-owner@example.test'),
    ('a1000000-0000-0000-0000-000000000002', 'rotation-other@example.test');

insert into public.tournaments (id, owner_id, name)
values ('a2000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 'Standard Direct');
insert into public.tournaments (id, owner_id, name, format, group_count)
values
    ('a2000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000001', 'Three Groups', 'group_rotation', 3),
    ('a2000000-0000-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000001', 'Four Groups', 'group_rotation', 4);

set local role authenticated;
set local request.jwt.claim.sub = 'a1000000-0000-0000-0000-000000000001';

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
values ('a3000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000001', 24, 'Slot 24');
select is((select slot_number from public.tournament_team_slots where id = 'a3000000-0000-0000-0000-000000000001'), 24, 'slot 24 is accepted');
select throws_ok($$
    update public.tournament_team_slots set slot_number = 25
    where id = 'a3000000-0000-0000-0000-000000000001'
$$, '23514', null, 'slot 25 is rejected');

select throws_ok($$
    insert into public.tournament_group_pairings (tournament_id, first_group, second_group)
    values ('a2000000-0000-0000-0000-000000000003', 'A', 'A')
$$, '23514', null, 'same-group pairings are rejected');
select throws_ok($$
    insert into public.tournament_group_pairings (tournament_id, first_group, second_group)
    values ('a2000000-0000-0000-0000-000000000003', 'B', 'A')
$$, '23514', null, 'reverse pairings are rejected');
insert into public.tournament_group_pairings (tournament_id, first_group, second_group)
values ('a2000000-0000-0000-0000-000000000003', 'A', 'B');
select is((select pairing_key from public.tournament_group_pairings where tournament_id = 'a2000000-0000-0000-0000-000000000003'), 'A:B', 'canonical pairing key is stored');

set local request.jwt.claim.sub = 'a1000000-0000-0000-0000-000000000002';
select is((select count(*) from public.tournament_group_pairings where tournament_id = 'a2000000-0000-0000-0000-000000000003'), 0::bigint, 'another owner cannot read pairings');
select throws_ok($$
    insert into public.tournament_group_pairings (tournament_id, first_group, second_group)
    values ('a2000000-0000-0000-0000-000000000003', 'A', 'C')
$$, '42501', null, 'another owner cannot write pairings');

set local request.jwt.claim.sub = 'a1000000-0000-0000-0000-000000000001';
select ok(to_regprocedure('public.write_tournament_snapshot(jsonb,jsonb,jsonb,integer)') is not null, 'snapshot RPC retains the approved signature');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'a2000000-0000-0000-0000-000000000002',
            'owner_id', 'a1000000-0000-0000-0000-000000000001',
            'name', 'Old Standard',
            'organizer_name', 'Organizer',
            'organizer_contact', '',
            'status', 'draft'
        ),
        (
            select jsonb_agg(jsonb_build_object(
                'id', ('a4000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
                'tournament_id', 'a2000000-0000-0000-0000-000000000002',
                'slot_number', slot_number,
                'team_name', 'Standard Team ' || slot_number,
                'status', 'draft'
            ) order by slot_number)
            from generate_series(1, 12) as slot_number
        ),
        '[]'::jsonb,
        0
    )
), 'success', 'old standard snapshot payload remains valid');
select is((select format from public.tournaments where id = 'a2000000-0000-0000-0000-000000000002'), 'standard', 'old snapshot defaults to standard format');
select is((select group_count from public.tournaments where id = 'a2000000-0000-0000-0000-000000000002'), null::integer, 'old snapshot defaults to no group count');
select is((select count(*) from public.tournament_group_pairings where tournament_id = 'a2000000-0000-0000-0000-000000000002'), 0::bigint, 'old snapshot stores no pairings');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'a2000000-0000-0000-0000-000000000003',
            'owner_id', 'a1000000-0000-0000-0000-000000000001',
            'name', 'Three Groups',
            'organizer_name', 'Organizer',
            'organizer_contact', '',
            'status', 'draft',
            'format', 'group_rotation',
            'group_count', 3,
            'selected_group_pairings', jsonb_build_array(
                jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B'),
                jsonb_build_object('first_group', 'B', 'second_group', 'C', 'pairing_key', 'B:C'),
                jsonb_build_object('first_group', 'A', 'second_group', 'C', 'pairing_key', 'A:C')
            )
        ),
        (
            select jsonb_agg(jsonb_build_object(
                'id', ('a5000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
                'tournament_id', 'a2000000-0000-0000-0000-000000000003',
                'slot_number', slot_number,
                'team_name', 'Three Team ' || slot_number,
                'status', 'draft',
                'group', case
                    when slot_number between 1 and 6 then 'A'
                    when slot_number between 7 and 12 then 'B'
                    else 'C'
                end
            ) order by slot_number)
            from generate_series(1, 18) as slot_number
        ),
        '[]'::jsonb,
        1
    )
), 'success', 'three-group snapshot is accepted');
select is((select count(*) from public.tournament_team_slots where tournament_id = 'a2000000-0000-0000-0000-000000000003'), 18::bigint, 'three-group snapshot stores 18 slots');
select is((select "group" from public.tournament_team_slots where tournament_id = 'a2000000-0000-0000-0000-000000000003' and slot_number = 13), 'C', 'slot 13 maps to Group C');
select is((select count(*) from public.tournament_group_pairings where tournament_id = 'a2000000-0000-0000-0000-000000000003'), 3::bigint, 'three-group snapshot stores all selected pairings');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'a2000000-0000-0000-0000-000000000003',
            'owner_id', 'a1000000-0000-0000-0000-000000000001',
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
        (
            select jsonb_agg(jsonb_build_object(
                'id', ('a5000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
                'tournament_id', 'a2000000-0000-0000-0000-000000000003',
                'slot_number', slot_number,
                'team_name', 'Three Team ' || slot_number,
                'status', 'draft',
                'group', case
                    when slot_number between 1 and 6 then 'A'
                    when slot_number between 7 and 12 then 'B'
                    else 'C'
                end
            ) order by slot_number)
            from generate_series(1, 18) as slot_number
        ),
        '[]'::jsonb,
        1
    )
), 'success', 'group pairing replacement is revision safe');
select is((select count(*) from public.tournament_group_pairings where tournament_id = 'a2000000-0000-0000-0000-000000000003'), 1::bigint, 'stale group pairings are removed atomically');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000003'), 2, 'group pairing replacement advances revision once');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'a2000000-0000-0000-0000-000000000005',
            'owner_id', 'a1000000-0000-0000-0000-000000000001',
            'name', 'Invalid Rotation',
            'organizer_name', 'Organizer',
            'organizer_contact', '',
            'status', 'draft',
            'format', 'group_rotation',
            'group_count', 3,
            'selected_group_pairings', '[]'::jsonb
        ),
        '[]'::jsonb,
        '[]'::jsonb,
        0
    )
), 'validation_failure', 'zero selected pairings are rejected');
select is((select count(*) from public.tournaments where id = 'a2000000-0000-0000-0000-000000000005'), 0::bigint, 'invalid group configuration creates no tournament');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'a2000000-0000-0000-0000-000000000003',
            'owner_id', 'a1000000-0000-0000-0000-000000000001',
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
        (
            select jsonb_agg(jsonb_build_object(
                'id', ('a5000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
                'tournament_id', 'a2000000-0000-0000-0000-000000000003',
                'slot_number', slot_number,
                'team_name', 'Three Team ' || slot_number,
                'status', 'draft',
                'group', case
                    when slot_number between 1 and 6 then 'A'
                    when slot_number between 7 and 12 then 'B'
                    else 'D'
                end
            ) order by slot_number)
            from generate_series(1, 18) as slot_number
        ),
        '[]'::jsonb,
        2
    )
), 'validation_failure', 'invalid group membership is rejected');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000003'), 2, 'invalid group membership leaves revision unchanged');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'a2000000-0000-0000-0000-000000000004',
            'owner_id', 'a1000000-0000-0000-0000-000000000001',
            'name', 'Four Groups',
            'organizer_name', 'Organizer',
            'organizer_contact', '',
            'status', 'draft',
            'format', 'group_rotation',
            'group_count', 4,
            'selected_group_pairings', jsonb_build_array(
                jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B'),
                jsonb_build_object('first_group', 'B', 'second_group', 'C', 'pairing_key', 'B:C'),
                jsonb_build_object('first_group', 'C', 'second_group', 'D', 'pairing_key', 'C:D'),
                jsonb_build_object('first_group', 'A', 'second_group', 'D', 'pairing_key', 'A:D')
            )
        ),
        (
            select jsonb_agg(jsonb_build_object(
                'id', ('a6000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
                'tournament_id', 'a2000000-0000-0000-0000-000000000004',
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
            from generate_series(1, 24) as slot_number
        ),
        '[]'::jsonb,
        0
    )
), 'success', 'four-group snapshot is accepted');
select is((select count(*) from public.tournament_team_slots where tournament_id = 'a2000000-0000-0000-0000-000000000004'), 24::bigint, 'four-group snapshot stores 24 slots');
select is((select count(*) from public.tournament_group_pairings where tournament_id = 'a2000000-0000-0000-0000-000000000004'), 4::bigint, 'four-group snapshot stores selected pairings');

select is((
    select outcome
    from public.replace_tournament_roster_snapshot(
        'a2000000-0000-0000-0000-000000000003',
        (
            select jsonb_agg(jsonb_build_object(
                'id', ('a5000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
                'tournament_id', 'a2000000-0000-0000-0000-000000000003',
                'slot_number', slot_number,
                'team_name', 'Replaced Three Team ' || slot_number,
                'status', 'draft',
                'group', case
                    when slot_number between 1 and 6 then 'A'
                    when slot_number between 7 and 12 then 'B'
                    else 'C'
                end
            ) order by slot_number)
            from generate_series(1, 18) as slot_number
        ),
        '[]'::jsonb,
        2
    )
), 'success', 'roster replacement accepts the configured 18-slot tournament');
select is((select count(*) from public.tournament_team_slots where tournament_id = 'a2000000-0000-0000-0000-000000000003' and team_name like 'Replaced Three Team %'), 18::bigint, '18-slot replacement updates every team');
select is((select "group" from public.tournament_team_slots where tournament_id = 'a2000000-0000-0000-0000-000000000003' and slot_number = 18), 'C', '18-slot replacement preserves group membership');
select is((
    select outcome
    from public.replace_tournament_roster_snapshot(
        'a2000000-0000-0000-0000-000000000003',
        (
            select jsonb_agg(jsonb_build_object(
                'id', ('a5000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
                'tournament_id', 'a2000000-0000-0000-0000-000000000003',
                'slot_number', slot_number,
                'team_name', 'Bad Team ' || slot_number,
                'status', 'draft',
                'group', case
                    when slot_number between 1 and 6 then 'A'
                    when slot_number between 7 and 12 then 'B'
                    else 'C'
                end
            ) order by slot_number)
            from generate_series(1, 12) as slot_number
        ),
        '[]'::jsonb,
        3
    )
), 'validation_failure', 'replacement rejects a non-configured slot count');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000003'), 3, 'invalid replacement leaves revision unchanged');

select * from finish();
rollback;
