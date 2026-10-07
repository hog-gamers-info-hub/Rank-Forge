begin;

select no_plan();

select has_table('public', 'tournament_group_pairing_lobby_slots', 'the V2 lobby mapping table exists');
select has_column('public', 'tournament_group_pairing_lobby_slots', 'tournament_id', 'mapping stores tournament identity');
select has_column('public', 'tournament_group_pairing_lobby_slots', 'pairing_key', 'mapping stores pairing identity');
select has_column('public', 'tournament_group_pairing_lobby_slots', 'lobby_slot_number', 'mapping stores local lobby slot');
select has_column('public', 'tournament_group_pairing_lobby_slots', 'team_slot_number', 'mapping stores canonical team slot');
select has_column('public', 'tournament_group_pairing_lobby_slots', 'created_at', 'mapping stores creation time');
select ok(not exists (select 1 from information_schema.columns where table_schema = 'public' and table_name = 'tournament_group_pairing_lobby_slots' and column_name = 'team_name'), 'mapping table does not duplicate team names');
select ok((select count(*) from pg_constraint where conrelid = 'public.tournament_group_pairing_lobby_slots'::regclass and contype = 'f') = 2, 'mapping table has both composite foreign keys');
select ok((select count(*) from pg_constraint where conrelid = 'public.tournament_group_pairing_lobby_slots'::regclass and contype = 'u') = 1, 'mapping table has the per-pairing canonical uniqueness constraint');
select ok((select relrowsecurity from pg_class where oid = 'public.tournament_group_pairing_lobby_slots'::regclass), 'mapping table has RLS enabled');
select is((select count(*) from pg_policies where schemaname = 'public' and tablename = 'tournament_group_pairing_lobby_slots'), 4::bigint, 'mapping table has four owner policies');
select ok(not has_table_privilege('anon', 'public.tournament_group_pairing_lobby_slots', 'select'), 'anon cannot read mappings');
select ok(has_table_privilege('authenticated', 'public.tournament_group_pairing_lobby_slots', 'select,insert,update,delete'), 'authenticated receives only mapped CRUD table privileges');
select ok(not has_table_privilege('authenticated', 'public.tournament_group_pairing_lobby_slots', 'truncate'), 'authenticated cannot truncate mappings');
select ok(not has_table_privilege('authenticated', 'public.tournament_group_pairing_lobby_slots', 'references'), 'authenticated cannot grant references on mappings');
select ok(not has_table_privilege('authenticated', 'public.tournament_group_pairing_lobby_slots', 'trigger'), 'authenticated cannot create triggers on mappings');
select ok(to_regprocedure('public.write_tournament_snapshot_v2(jsonb,jsonb,jsonb,jsonb,integer)') is not null, 'V2 tournament snapshot signature exists');
select ok(to_regprocedure('public.replace_tournament_roster_snapshot_v2(uuid,jsonb,jsonb,integer)') is not null, 'V2 roster snapshot signature exists');
select ok(to_regprocedure('public.write_match_snapshot_v2(uuid,jsonb,jsonb,integer)') is not null, 'V2 match snapshot signature exists');
select ok(to_regprocedure('public.finalize_match_snapshot_v2(uuid,jsonb,jsonb,integer)') is not null, 'V2 finalization signature exists');
select ok(not has_function_privilege('anon', 'public.write_tournament_snapshot_v2(jsonb,jsonb,jsonb,jsonb,integer)', 'execute'), 'anon cannot execute V2 tournament snapshot');
select ok(has_function_privilege('authenticated', 'public.write_tournament_snapshot_v2(jsonb,jsonb,jsonb,jsonb,integer)', 'execute'), 'authenticated can execute V2 tournament snapshot');
select ok(not has_function_privilege('anon', 'public.replace_tournament_roster_snapshot_v2(uuid,jsonb,jsonb,integer)', 'execute'), 'anon cannot execute V2 roster snapshot');
select ok(has_function_privilege('authenticated', 'public.replace_tournament_roster_snapshot_v2(uuid,jsonb,jsonb,integer)', 'execute'), 'authenticated can execute V2 roster snapshot');
select ok(not has_function_privilege('anon', 'public.write_match_snapshot_v2(uuid,jsonb,jsonb,integer)', 'execute'), 'anon cannot execute V2 match snapshot');
select ok(has_function_privilege('authenticated', 'public.write_match_snapshot_v2(uuid,jsonb,jsonb,integer)', 'execute'), 'authenticated can execute V2 match snapshot');
select ok(not has_function_privilege('anon', 'public.finalize_match_snapshot_v2(uuid,jsonb,jsonb,integer)', 'execute'), 'anon cannot execute V2 finalization');
select ok(has_function_privilege('authenticated', 'public.finalize_match_snapshot_v2(uuid,jsonb,jsonb,integer)', 'execute'), 'authenticated can execute V2 finalization');
select is((select prosecdef from pg_proc where oid = 'public.write_tournament_snapshot_v2(jsonb,jsonb,jsonb,jsonb,integer)'::regprocedure), false, 'V2 tournament snapshot is SECURITY INVOKER');
select is((select prosecdef from pg_proc where oid = 'public.replace_tournament_roster_snapshot_v2(uuid,jsonb,jsonb,integer)'::regprocedure), false, 'V2 roster snapshot is SECURITY INVOKER');
select is((select prosecdef from pg_proc where oid = 'public.write_match_snapshot_v2(uuid,jsonb,jsonb,integer)'::regprocedure), false, 'V2 match snapshot is SECURITY INVOKER');
select is((select prosecdef from pg_proc where oid = 'public.finalize_match_snapshot_v2(uuid,jsonb,jsonb,integer)'::regprocedure), true, 'V2 finalization is SECURITY DEFINER');
select ok((select pg_get_constraintdef(oid) like '%lobby_slot_number between 1 and 12%' from pg_constraint where conname = 'tournament_group_pairing_lobby_slots_lobby_check'), 'lobby slots remain constrained to 1..12');
select ok((select pg_get_constraintdef(oid) like '%team_slot_number between 1 and 24%' from pg_constraint where conname = 'tournament_group_pairing_lobby_slots_team_check'), 'canonical team slots are constrained to 1..24');
select ok((select pg_get_constraintdef(oid) like '%between 1 and 24%' from pg_constraint where conname = 'match_ocr_row_evidence_team_slot_check'), 'row OCR identity accepts canonical slot 24');
select ok((select pg_get_constraintdef(oid) like '%between 1 and 24%' from pg_constraint where conname = 'match_ocr_correction_snapshots_team_slot_check'), 'correction OCR identity accepts canonical slot 24');
select ok((select pg_get_constraintdef(oid) like '%between 1 and 12%' from pg_constraint where conname = 'match_ocr_row_evidence_placement_check'), 'row OCR placement remains 1..12');
select ok((select pg_get_constraintdef(oid) like '%between 1 and 12%' from pg_constraint where conname = 'match_ocr_correction_snapshots_placement_check'), 'correction OCR placement remains 1..12');

create temporary table v2_payloads (
    payload_key text primary key,
    tournament_id uuid not null,
    tournament jsonb not null,
    team_slots jsonb not null,
    players jsonb not null,
    pairings jsonb not null,
    mappings jsonb not null
);

insert into auth.users (id, email)
values
    ('a1000000-0000-0000-0000-000000000001', 'phase4a-owner@example.test'),
    ('a1000000-0000-0000-0000-000000000002', 'phase4a-other@example.test');

insert into public.tournaments (id, owner_id, name, format, group_count, status)
values ('a2000000-0000-0000-0000-000000000099', 'a1000000-0000-0000-0000-000000000002', 'Other Owner', 'group_rotation', 3, 'draft');
insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name, status, "group")
select ('a3990000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
       'a2000000-0000-0000-0000-000000000099', slot_number, 'Other Team ' || slot_number, 'draft',
       case when slot_number between 1 and 6 then 'A' when slot_number between 7 and 12 then 'B' else 'C' end
from generate_series(1, 18) as slots(slot_number);
insert into public.tournament_group_pairings (tournament_id, first_group, second_group)
values ('a2000000-0000-0000-0000-000000000099', 'A', 'B');

insert into pg_temp.v2_payloads(payload_key, tournament_id, tournament, team_slots, players, pairings, mappings)
values
(
    'three',
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_object(
        'id', 'a2000000-0000-0000-0000-000000000001', 'owner_id', 'a1000000-0000-0000-0000-000000000001',
        'name', 'Three Group V2', 'organizer_name', 'Organizer', 'organizer_contact', '', 'status', 'draft',
        'format', 'group_rotation', 'group_count', 3,
        'selected_group_pairings', jsonb_build_array(
            jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B'),
            jsonb_build_object('first_group', 'A', 'second_group', 'C', 'pairing_key', 'A:C')
        )
    ),
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('a3010000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'tournament_id', 'a2000000-0000-0000-0000-000000000001', 'slot_number', slot_number,
            'team_name', 'Three Team ' || slot_number, 'status', 'draft', 'group',
            case when slot_number between 1 and 6 then 'A' when slot_number between 7 and 12 then 'B' else 'C' end
        ) order by slot_number)
        from generate_series(1, 18) as slots(slot_number)
    ),
    '[]'::jsonb,
    jsonb_build_array(
        jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B'),
        jsonb_build_object('first_group', 'A', 'second_group', 'C', 'pairing_key', 'A:C')
    ),
    '[]'::jsonb
),
(
    'four',
    'a2000000-0000-0000-0000-000000000002',
    jsonb_build_object(
        'id', 'a2000000-0000-0000-0000-000000000002', 'owner_id', 'a1000000-0000-0000-0000-000000000001',
        'name', 'Four Group V2', 'organizer_name', 'Organizer', 'organizer_contact', '', 'status', 'draft',
        'format', 'group_rotation', 'group_count', 4,
        'selected_group_pairings', jsonb_build_array(
            jsonb_build_object('first_group', 'A', 'second_group', 'D', 'pairing_key', 'A:D')
        )
    ),
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('a3020000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'tournament_id', 'a2000000-0000-0000-0000-000000000002', 'slot_number', slot_number,
            'team_name', 'Four Team ' || slot_number, 'status', 'draft', 'group',
            case when slot_number between 1 and 6 then 'A' when slot_number between 7 and 12 then 'B' when slot_number between 13 and 18 then 'C' else 'D' end
        ) order by slot_number)
        from generate_series(1, 24) as slots(slot_number)
    ),
    jsonb_build_array(jsonb_build_object(
        'id', 'a5010000-0000-0000-0000-000000000001',
        'team_slot_id', 'a3020000-0000-0000-0000-000000000024',
        'display_name', 'Four Player', 'normalized_name', 'Four Player'
    )),
    jsonb_build_array(jsonb_build_object('first_group', 'A', 'second_group', 'D', 'pairing_key', 'A:D')),
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'a2000000-0000-0000-0000-000000000002', 'pairing_key', 'A:D',
            'lobby_slot_number', lobby_slot_number, 'team_slot_number', lobby_slot_number + 12
        ) order by lobby_slot_number)
        from generate_series(1, 12) as slots(lobby_slot_number)
    )
),
(
    'standard',
    'a2000000-0000-0000-0000-000000000003',
    jsonb_build_object(
        'id', 'a2000000-0000-0000-0000-000000000003', 'owner_id', 'a1000000-0000-0000-0000-000000000001',
        'name', 'Standard Compatibility', 'organizer_name', 'Organizer', 'organizer_contact', '', 'status', 'draft'
    ),
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('a3030000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'tournament_id', 'a2000000-0000-0000-0000-000000000003', 'slot_number', slot_number,
            'team_name', 'Standard Team ' || slot_number, 'status', 'draft'
        ) order by slot_number)
        from generate_series(1, 12) as slots(slot_number)
    ),
    '[]'::jsonb, '[]'::jsonb, '[]'::jsonb
);

grant select on v2_payloads to authenticated;

set local role authenticated;
set local request.jwt.claim.sub = 'a1000000-0000-0000-0000-000000000001';

select is((select outcome from public.write_tournament_snapshot_v2(
    (select tournament from pg_temp.v2_payloads where payload_key = 'three'),
    (select team_slots from pg_temp.v2_payloads where payload_key = 'three'),
    '[]'::jsonb, '[]'::jsonb, 0
)), 'success', 'empty mapping is accepted before pairing setup');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 1, 'new V2 tournament starts at revision one');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'a2000000-0000-0000-0000-000000000001'), 0::bigint, 'empty mapping persists no rows');
select is((select count(*) from public.tournament_group_pairings where tournament_id = 'a2000000-0000-0000-0000-000000000001'), 2::bigint, 'selected pairings persist independently of empty mapping');

update public.tournament_group_pairing_lobby_slots
set team_slot_number = 12
where tournament_id = 'a2000000-0000-0000-0000-000000000001' and pairing_key = 'A:B' and lobby_slot_number = 1;
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'a2000000-0000-0000-0000-000000000001'), 0::bigint, 'owner update cannot create a mapping before insert');

select is((select outcome from public.write_tournament_snapshot_v2(
    (select tournament from pg_temp.v2_payloads where payload_key = 'three'),
    (select team_slots from pg_temp.v2_payloads where payload_key = 'three'),
    '[]'::jsonb,
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'a2000000-0000-0000-0000-000000000001', 'pairing_key', pairing_key,
            'lobby_slot_number', lobby_slot_number, 'team_slot_number', team_slot_number
        ) order by pairing_key, lobby_slot_number)
        from (
            select 'A:B'::text as pairing_key, s as lobby_slot_number,
                   case when s <= 6 then s else s + 6 end as team_slot_number
            from generate_series(1, 12) as slots(s)
            union all
            select 'A:C'::text, s, s + 6
            from generate_series(1, 12) as slots(s)
        ) as mapping_rows
    ),
    1
)), 'success', 'complete mapping is accepted for selected pairings');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 2, 'complete mapping advances the tournament revision');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'a2000000-0000-0000-0000-000000000001'), 24::bigint, 'complete mapping stores twelve rows per pairing');
select is((select team_slot_number from public.tournament_group_pairing_lobby_slots where tournament_id = 'a2000000-0000-0000-0000-000000000001' and pairing_key = 'A:B' and lobby_slot_number = 12), 18, 'A:B can map local lobby twelve to canonical slot eighteen');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'a2000000-0000-0000-0000-000000000001' and team_slot_number = 13), 2::bigint, 'the same canonical identity can be mapped in multiple pairings');
select is((select outcome from public.write_tournament_snapshot_v2(
    (select tournament from pg_temp.v2_payloads where payload_key = 'three'),
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('a3010000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'tournament_id', 'a2000000-0000-0000-0000-000000000001',
            'slot_number', case when slot_number = 18 then 19 else slot_number end,
            'team_name', 'Three Team ' || slot_number, 'status', 'draft',
            'group', case
                when slot_number between 1 and 6 then 'A'
                when slot_number between 7 and 12 then 'B'
                when slot_number between 13 and 17 then 'C'
                else 'D'
            end
        ) order by slot_number)
        from generate_series(1, 18) as slots(slot_number)
    ),
    '[]'::jsonb, '[]'::jsonb, 2
)), 'validation_failure', 'three-group structural slots reject 1..17 plus 19');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 2, 'structural slot-set rejection leaves revision unchanged');
select is((select outcome from public.write_tournament_snapshot_v2(
    (select tournament from pg_temp.v2_payloads where payload_key = 'three'),
    (select team_slots from pg_temp.v2_payloads where payload_key = 'three'),
    '[]'::jsonb,
    jsonb_build_array(jsonb_build_object(
        'tournament_id', 'a2000000-0000-0000-0000-000000000001', 'pairing_key', 'A:B',
        'lobby_slot_number', 1, 'team_slot_number', 1
    )), 2
)), 'validation_failure', 'partial mapping is rejected for a selected pairing');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 2, 'partial mapping rejection leaves revision unchanged');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'a2000000-0000-0000-0000-000000000001'), 24::bigint, 'partial mapping rejection leaves all prior mappings unchanged');
select is((select outcome from public.write_tournament_snapshot_v2(
    jsonb_set((select tournament from pg_temp.v2_payloads where payload_key = 'three'), '{name}', '"Stale Name"'::jsonb),
    (select team_slots from pg_temp.v2_payloads where payload_key = 'three'), '[]'::jsonb, '[]'::jsonb, 1
)), 'stale_write', 'stale V2 snapshot is rejected before mutation');
select is((select name from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 'Three Group V2', 'stale snapshot leaves tournament fields unchanged');
select is((select outcome from public.write_tournament_snapshot_v2(
    (select tournament from pg_temp.v2_payloads where payload_key = 'three'),
    (select team_slots from pg_temp.v2_payloads where payload_key = 'three'), '[]'::jsonb,
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', tournament_id, 'pairing_key', pairing_key,
            'lobby_slot_number', lobby_slot_number,
            'team_slot_number', case when pairing_key = 'A:B' and lobby_slot_number = 12 then 19 else team_slot_number end
        ) order by pairing_key, lobby_slot_number)
        from public.tournament_group_pairing_lobby_slots
        where tournament_id = 'a2000000-0000-0000-0000-000000000001'
    ), 2
)), 'validation_failure', 'canonical slot nineteen is rejected for an eighteen-slot tournament');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'a2000000-0000-0000-0000-000000000001'), 24::bigint, 'invalid canonical mapping leaves persisted rows unchanged');

update public.tournament_group_pairing_lobby_slots
set team_slot_number = 12
where tournament_id = 'a2000000-0000-0000-0000-000000000001' and pairing_key = 'A:B' and lobby_slot_number = 1;
select is((select team_slot_number from public.tournament_group_pairing_lobby_slots where tournament_id = 'a2000000-0000-0000-0000-000000000001' and pairing_key = 'A:B' and lobby_slot_number = 1), 12, 'owner can update a mapping row');
delete from public.tournament_group_pairing_lobby_slots where tournament_id = 'a2000000-0000-0000-0000-000000000001' and pairing_key = 'A:B' and lobby_slot_number = 1;
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'a2000000-0000-0000-0000-000000000001' and pairing_key = 'A:B' and lobby_slot_number = 1), 0::bigint, 'owner can delete a mapping row');
insert into public.tournament_group_pairing_lobby_slots(tournament_id, pairing_key, lobby_slot_number, team_slot_number)
values ('a2000000-0000-0000-0000-000000000001', 'A:B', 1, 1);
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'a2000000-0000-0000-0000-000000000001' and pairing_key = 'A:B'), 12::bigint, 'owner can restore the mapping row');

select throws_ok($$
    insert into public.tournament_group_pairing_lobby_slots(tournament_id, pairing_key, lobby_slot_number, team_slot_number)
    values ('a2000000-0000-0000-0000-000000000001', 'A:B', 13, 12)
$$, '23514', null, 'lobby slot thirteen is rejected');
select throws_ok($$
    insert into public.tournament_group_pairing_lobby_slots(tournament_id, pairing_key, lobby_slot_number, team_slot_number)
    values ('a2000000-0000-0000-0000-000000000001', 'A:B', 0, 12)
$$, '23514', null, 'lobby slot zero is rejected');
select throws_ok($$
    insert into public.tournament_group_pairing_lobby_slots(tournament_id, pairing_key, lobby_slot_number, team_slot_number)
    values ('a2000000-0000-0000-0000-000000000001', 'A:B', 2, 25)
$$, '23514', null, 'canonical team slot twenty-five is rejected');
select throws_ok($$
    insert into public.tournament_group_pairing_lobby_slots(tournament_id, pairing_key, lobby_slot_number, team_slot_number)
    values ('a2000000-0000-0000-0000-000000000001', 'A:B', 2, 0)
$$, '23514', null, 'canonical team slot zero is rejected');
select throws_ok($$
    insert into public.tournament_group_pairing_lobby_slots(tournament_id, pairing_key, lobby_slot_number, team_slot_number)
    values ('a2000000-0000-0000-0000-000000000001', 'A:B', 1, 12)
$$, '23505', null, 'duplicate lobby slot is rejected');
select throws_ok($$
    insert into public.tournament_group_pairing_lobby_slots(tournament_id, pairing_key, lobby_slot_number, team_slot_number)
    values ('a2000000-0000-0000-0000-000000000001', 'A:B', 2, 13)
$$, '23505', null, 'duplicate canonical identity within a pairing is rejected');
select throws_ok($$
    insert into public.tournament_group_pairing_lobby_slots(tournament_id, pairing_key, lobby_slot_number, team_slot_number)
    values ('a2000000-0000-0000-0000-000000000099', 'A:B', 1, 1)
$$, '42501', null, 'cross-owner mapping insert is rejected by RLS');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'a2000000-0000-0000-0000-000000000099'), 0::bigint, 'cross-owner mapping remains unreadable');

select is((select outcome from public.write_tournament_snapshot_v2(
    (select tournament from pg_temp.v2_payloads where payload_key = 'four'),
    (select team_slots from pg_temp.v2_payloads where payload_key = 'four'),
    '[]'::jsonb,
    '[]'::jsonb, 0
)), 'success', 'four-group snapshot accepts canonical capacity through slot twenty-four');
select is((select outcome from public.write_tournament_snapshot_v2(
    (select tournament from pg_temp.v2_payloads where payload_key = 'four'),
    (select team_slots from pg_temp.v2_payloads where payload_key = 'four'),
    '[]'::jsonb,
    (select mappings from pg_temp.v2_payloads where payload_key = 'four'), 1
)), 'success', 'four-group complete mapping accepts canonical slots thirteen through twenty-four');
select is((select count(distinct slot_number) from public.tournament_team_slots where tournament_id = 'a2000000-0000-0000-0000-000000000002'), 24::bigint, 'four-group snapshot preserves the exact structural slot set 1..24');
select is((select team_slot_number from public.tournament_group_pairing_lobby_slots where tournament_id = 'a2000000-0000-0000-0000-000000000002' and lobby_slot_number = 12), 24, 'four-group mapping persists canonical slot twenty-four');
select is((select outcome from public.replace_tournament_roster_snapshot_v2(
    'a2000000-0000-0000-0000-000000000002',
    (select team_slots from pg_temp.v2_payloads where payload_key = 'four'),
    (select players from pg_temp.v2_payloads where payload_key = 'four'), 2
)), 'success', 'roster replacement preserves an existing mapping');
select is((select count(*) from public.tournament_group_pairing_lobby_slots where tournament_id = 'a2000000-0000-0000-0000-000000000002'), 12::bigint, 'roster replacement preserves all twelve mapping rows');

select is((select outcome from public.write_match_snapshot_v2(
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_array(jsonb_build_object(
        'id', 'a4000000-0000-0000-0000-000000000001', 'tournament_id', 'a2000000-0000-0000-0000-000000000001',
        'match_number', 1, 'status', 'draft', 'group_pairing_key', 'A:B'
    )),
    jsonb_build_array(jsonb_build_object(
        'id', 'a4100000-0000-0000-0000-000000000001', 'match_id', 'a4000000-0000-0000-0000-000000000001',
        'team_slot_id', 'a3010000-0000-0000-0000-000000000018', 'placement', 1, 'kills', 4,
        'source', 'manual', 'review_status', 'draft'
    )), 2
)), 'success', 'match snapshot accepts a result subset for a mapped canonical slot');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 3, 'match snapshot advances revision');
select is((select team_slot_number from public.tournament_group_pairing_lobby_slots where tournament_id = 'a2000000-0000-0000-0000-000000000001' and pairing_key = 'A:B' and lobby_slot_number = 12), 18, 'match identity remains canonical slot eighteen rather than lobby position');
select is((select outcome from public.write_match_snapshot_v2(
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_array(jsonb_build_object(
        'id', 'a4000000-0000-0000-0000-000000000001', 'tournament_id', 'a2000000-0000-0000-0000-000000000001',
        'match_number', 1, 'status', 'draft', 'group_pairing_key', 'A:B'
    )),
    jsonb_build_array(jsonb_build_object(
        'id', 'a4100000-0000-0000-0000-000000000002', 'match_id', 'a4000000-0000-0000-0000-000000000001',
        'team_slot_id', 'a3010000-0000-0000-0000-000000000007', 'placement', 2, 'kills', 0,
        'source', 'manual', 'review_status', 'draft'
    )), 3
)), 'validation_failure', 'match snapshot rejects a canonical slot outside the selected pairing mapping');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 3, 'rejected result leaves revision unchanged');
select is((select count(*) from public.match_results where match_id = 'a4000000-0000-0000-0000-000000000001' and team_slot_id = 'a3010000-0000-0000-0000-000000000018'), 1::bigint, 'validation failure preserves the existing canonical result');
select is((select placement from public.match_results where match_id = 'a4000000-0000-0000-0000-000000000001' and team_slot_id = 'a3010000-0000-0000-0000-000000000018'), 1, 'validation failure preserves the existing result values');

select is((select outcome from public.write_match_snapshot_v2(
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_array(jsonb_build_object(
        'id', 'a4000000-0000-0000-0000-000000000002', 'tournament_id', 'a2000000-0000-0000-0000-000000000001',
        'match_number', 2, 'status', 'draft', 'group_pairing_key', 'A:C'
    )),
    jsonb_build_array(jsonb_build_object(
        'id', 'a4100000-0000-0000-0000-000000000003', 'match_id', 'a4000000-0000-0000-0000-000000000002',
        'team_slot_id', 'a3010000-0000-0000-0000-000000000007', 'placement', 1, 'kills', 1,
        'source', 'manual', 'review_status', 'draft'
    )), 3
)), 'success', 'mapped identity is accepted without deriving eligibility from TeamSlot.group');

select is((select outcome from public.write_match_snapshot_v2(
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_array(jsonb_build_object(
        'id', 'a4000000-0000-0000-0000-000000000002', 'tournament_id', 'a2000000-0000-0000-0000-000000000001',
        'match_number', 2, 'status', 'draft', 'group_pairing_key', 'A:C'
    )),
    jsonb_build_array(jsonb_build_object(
        'id', 'a4100000-0000-0000-0000-000000000004', 'match_id', 'a4000000-0000-0000-0000-000000000002',
        'team_slot_id', 'a3010000-0000-0000-0000-000000000018', 'placement', null, 'kills', 4,
        'source', 'manual', 'review_status', 'draft'
    )), 4
)), 'success', 'draft PARTICIPATED rows may omit placement while retaining nonnegative kills');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 5, 'partial draft result advances the revision');
select is((select count(*) from public.match_results where match_id = 'a4000000-0000-0000-0000-000000000002' and team_slot_id = 'a3010000-0000-0000-0000-000000000018'), 1::bigint, 'authoritative draft snapshot keeps incoming canonical team eighteen');
select is((select count(*) from public.match_results where match_id = 'a4000000-0000-0000-0000-000000000002' and team_slot_id = 'a3010000-0000-0000-0000-000000000007'), 0::bigint, 'authoritative draft snapshot removes omitted canonical team seven');
select is((select count(*) from public.match_results where match_id = 'a4000000-0000-0000-0000-000000000002'), 1::bigint, 'authoritative draft snapshot leaves exactly one result row');
insert into public.match_results(
    id, match_id, team_slot_id, placement, kills, source, review_status, participation_status
)
values (
    'a4400000-0000-0000-0000-000000000001', 'a4000000-0000-0000-0000-000000000002',
    'a3010000-0000-0000-0000-000000000017', null, 4, 'manual', 'draft', 'PARTICIPATED'
);
select is((select count(*) from public.match_results where id = 'a4400000-0000-0000-0000-000000000001'), 1::bigint, 'draft table invariant accepts PARTICIPATED with null placement and nonnegative kills');
delete from public.match_results where id = 'a4400000-0000-0000-0000-000000000001';
insert into public.match_results(
    id, match_id, team_slot_id, placement, kills, source, review_status, participation_status
)
values (
    'a4400000-0000-0000-0000-000000000002', 'a4000000-0000-0000-0000-000000000002',
    'a3010000-0000-0000-0000-000000000016', null, 0, 'manual', 'draft', 'NO_SHOW'
);
select is((select count(*) from public.match_results where id = 'a4400000-0000-0000-0000-000000000002'), 1::bigint, 'draft table invariant accepts NO_SHOW with null placement and zero kills');
delete from public.match_results where id = 'a4400000-0000-0000-0000-000000000002';
select throws_ok($$
    insert into public.match_results(
        id, match_id, team_slot_id, placement, kills, source, review_status, participation_status
    ) values (
        'a4400000-0000-0000-0000-000000000003', 'a4000000-0000-0000-0000-000000000002',
        'a3010000-0000-0000-0000-000000000015', 2, 0, 'manual', 'draft', 'NO_SHOW'
    )
$$, '23514', null, 'NO_SHOW with a placement remains rejected');
select throws_ok($$
    insert into public.match_results(
        id, match_id, team_slot_id, placement, kills, source, review_status, participation_status
    ) values (
        'a4400000-0000-0000-0000-000000000004', 'a4000000-0000-0000-0000-000000000002',
        'a3010000-0000-0000-0000-000000000014', null, 1, 'manual', 'draft', 'NO_SHOW'
    )
$$, '23514', null, 'NO_SHOW with positive kills remains rejected');
select throws_ok($$
    insert into public.match_results(
        id, match_id, team_slot_id, placement, kills, source, review_status, participation_status
    ) values (
        'a4400000-0000-0000-0000-000000000005', 'a4000000-0000-0000-0000-000000000002',
        'a3010000-0000-0000-0000-000000000013', 13, 0, 'manual', 'draft', 'PARTICIPATED'
    )
$$, '23514', null, 'placement thirteen remains rejected by the placement check');
select is((select outcome from public.write_match_snapshot_v2(
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_array(jsonb_build_object(
        'id', 'a4000000-0000-0000-0000-000000000002', 'tournament_id', 'a2000000-0000-0000-0000-000000000001',
        'match_number', 2, 'status', 'draft', 'group_pairing_key', 'A:C'
    )),
    jsonb_build_array(
        jsonb_build_object(
            'id', 'a4100000-0000-0000-0000-000000000005', 'match_id', 'a4000000-0000-0000-0000-000000000002',
            'team_slot_id', 'a3010000-0000-0000-0000-000000000007', 'placement', 1, 'kills', 1,
            'source', 'manual', 'review_status', 'draft'
        ),
        jsonb_build_object(
            'id', 'a4100000-0000-0000-0000-000000000006', 'match_id', 'a4000000-0000-0000-0000-000000000002',
            'team_slot_id', 'a3010000-0000-0000-0000-000000000008', 'placement', 1, 'kills', 2,
            'source', 'manual', 'review_status', 'draft'
        )
    ), 5
)), 'validation_failure', 'duplicate non-null draft placements remain rejected');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 5, 'duplicate draft placements leave revision unchanged');

select is((select outcome from public.finalize_match_snapshot_v2(
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_object('id', 'a4000000-0000-0000-0000-000000000002', 'status', 'finalized', 'group_pairing_key', 'A:C'),
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('a4200000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'match_id', 'a4000000-0000-0000-0000-000000000002',
            'team_slot_id', ('a3010000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'placement', case when slot_number <= 12 then slot_number - 6 else null end,
            'kills', case when slot_number <= 12 then 2 else 0 end,
            'source', 'manual', 'review_status', 'confirmed',
            'participation_status', case when slot_number <= 12 then 'PARTICIPATED' else 'NO_SHOW' end
        ) order by slot_number)
        from generate_series(7, 18) as slots(slot_number)
    ), 5
)), 'success', 'finalization accepts all mapped canonical identities with no-show rows');
select is((select status from public.matches where id = 'a4000000-0000-0000-0000-000000000002'), 'finalized', 'V2 finalization marks the match finalized');
select is((select count(*) from public.match_results where match_id = 'a4000000-0000-0000-0000-000000000002' and participation_status = 'NO_SHOW'), 6::bigint, 'V2 finalization persists six no-show rows');
select is((select count(*) from public.match_results where match_id = 'a4000000-0000-0000-0000-000000000002' and placement between 1 and 6), 6::bigint, 'V2 finalization keeps placements gap-free for participants');
select is((select outcome from public.finalize_match_snapshot_v2(
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_object('id', 'a4000000-0000-0000-0000-000000000001', 'status', 'finalized', 'group_pairing_key', 'A:B'),
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('a4300000-0000-0000-0000-' || lpad(lobby_slot_number::text, 12, '0'))::uuid,
            'match_id', 'a4000000-0000-0000-0000-000000000001',
            'team_slot_id', ('a3010000-0000-0000-0000-' || lpad(team_slot_number::text, 12, '0'))::uuid,
            'placement', case when lobby_slot_number = 1 then null else lobby_slot_number - 1 end,
            'kills', 1, 'source', 'manual', 'review_status', 'confirmed',
            'participation_status', 'PARTICIPATED'
        ) order by lobby_slot_number)
        from (
            select lobby_slot_number,
                   case when lobby_slot_number <= 6 then lobby_slot_number else lobby_slot_number + 6 end as team_slot_number
            from generate_series(1, 12) as slots(lobby_slot_number)
        ) as mapped_slots
    ), 6
)), 'validation_failure', 'finalization remains strict when a participated placement is missing');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 6, 'strict finalization rejection leaves revision unchanged');
select is((select outcome from public.write_match_snapshot_v2(
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_array(jsonb_build_object(
        'id', 'a4000000-0000-0000-0000-000000000004', 'tournament_id', 'a2000000-0000-0000-0000-000000000001',
        'match_number', 3, 'status', 'draft', 'group_pairing_key', 'A:B'
    )),
    jsonb_build_array(jsonb_build_object(
        'id', 'a4100000-0000-0000-0000-000000000007', 'match_id', 'a4000000-0000-0000-0000-000000000004',
        'team_slot_id', 'a3010000-0000-0000-0000-000000000001', 'placement', 1, 'kills', 2,
        'source', 'manual', 'review_status', 'draft'
    )), 6
)), 'success', 'draft match result snapshot seeds the stale placement regression');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 7, 'stale placement seed advances the revision');
select is((select outcome from public.write_match_snapshot_v2(
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_array(jsonb_build_object(
        'id', 'a4000000-0000-0000-0000-000000000004', 'tournament_id', 'a2000000-0000-0000-0000-000000000001',
        'match_number', 3, 'status', 'draft', 'group_pairing_key', 'A:B'
    )),
    jsonb_build_array(jsonb_build_object(
        'id', 'a4100000-0000-0000-0000-000000000008', 'match_id', 'a4000000-0000-0000-0000-000000000004',
        'team_slot_id', 'a3010000-0000-0000-0000-000000000002', 'placement', 1, 'kills', 3,
        'source', 'manual', 'review_status', 'draft'
    )), 7
)), 'success', 'authoritative replacement removes a stale placement before upsert');
select is((select count(*) from public.match_results where match_id = 'a4000000-0000-0000-0000-000000000004'), 1::bigint, 'replacement leaves one result row');
select is((select count(*) from public.match_results where match_id = 'a4000000-0000-0000-0000-000000000004' and team_slot_id = 'a3010000-0000-0000-0000-000000000001'), 0::bigint, 'replacement removes the old canonical team');
select is((select count(*) from public.match_results where match_id = 'a4000000-0000-0000-0000-000000000004' and team_slot_id = 'a3010000-0000-0000-0000-000000000002' and placement = 1), 1::bigint, 'replacement persists the new canonical team at placement one');
select is((select outcome from public.write_match_snapshot_v2(
    'a2000000-0000-0000-0000-000000000001',
    jsonb_build_array(jsonb_build_object(
        'id', 'a4000000-0000-0000-0000-000000000001', 'tournament_id', 'a2000000-0000-0000-0000-000000000001',
        'match_number', 1, 'status', 'draft', 'group_pairing_key', 'A:B'
    )),
    '[]'::jsonb, 8
)), 'success', 'empty authoritative snapshot clears an included draft match');
select is((select count(*) from public.match_results where match_id = 'a4000000-0000-0000-0000-000000000001'), 0::bigint, 'empty authoritative snapshot removes all included draft results');
select is((select count(*) from public.match_results where match_id = 'a4000000-0000-0000-0000-000000000004' and team_slot_id = 'a3010000-0000-0000-0000-000000000002'), 1::bigint, 'draft results for a match omitted from the snapshot remain untouched');
select is((select outcome from public.write_tournament_snapshot_v2(
    (select tournament from pg_temp.v2_payloads where payload_key = 'four'),
    (select team_slots from pg_temp.v2_payloads where payload_key = 'four'),
    (select players from pg_temp.v2_payloads where payload_key = 'four'),
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'a2000000-0000-0000-0000-000000000002', 'pairing_key', 'A:D',
            'lobby_slot_number', lobby_slot_number, 'team_slot_number', team_slot_number
        ) order by lobby_slot_number)
        from (
            select row_number() over ()::integer as lobby_slot_number, team_slot_number
            from unnest(array[12,14,15,16,17,18,19,20,21,22,23,24]) as mapped(team_slot_number)
        ) as changed_mapping
    ), 3
)), 'validation_failure', 'mapping identity cannot change after roster player data exists');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000002'), 3, 'locked mapping failure leaves roster revision unchanged');

select is((select outcome from public.write_tournament_snapshot_v2(
    (select tournament from pg_temp.v2_payloads where payload_key = 'three'),
    (select team_slots from pg_temp.v2_payloads where payload_key = 'three'),
    '[]'::jsonb,
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'a2000000-0000-0000-0000-000000000001', 'pairing_key', 'A:B',
            'lobby_slot_number', lobby_slot_number, 'team_slot_number', team_slot_number
        ) order by lobby_slot_number)
        from (
            select s as lobby_slot_number, case when s <= 5 then s else s + 6 end as team_slot_number
            from generate_series(1, 12) as slots(s)
        ) as changed_mapping
    ), 9
)), 'validation_failure', 'mapping identity cannot change after a match exists');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 9, 'match-locked mapping failure leaves revision unchanged');
select is((select outcome from public.write_tournament_snapshot_v2(
    (select tournament from pg_temp.v2_payloads where payload_key = 'three'),
    (select team_slots from pg_temp.v2_payloads where payload_key = 'three'),
    '[]'::jsonb,
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'a2000000-0000-0000-0000-000000000001', 'pairing_key', 'A:B',
            'lobby_slot_number', lobby_slot_number, 'team_slot_number', team_slot_number
        ) order by lobby_slot_number)
        from generate_series(1, 12) as slots(lobby_slot_number)
        cross join lateral (select case when lobby_slot_number <= 6 then lobby_slot_number else lobby_slot_number + 6 end as team_slot_number) as mapping_row
    ), 9
)), 'validation_failure', 'pairing removal is blocked when a match references the pairing');
select is((select outcome from public.write_tournament_snapshot_v2(
    jsonb_set((select tournament from pg_temp.v2_payloads where payload_key = 'three'), '{selected_group_pairings}', jsonb_build_array(
        jsonb_build_object('first_group', 'A', 'second_group', 'B', 'pairing_key', 'A:B')
    )),
    (select team_slots from pg_temp.v2_payloads where payload_key = 'three'),
    '[]'::jsonb,
    (
        select jsonb_agg(jsonb_build_object(
            'tournament_id', 'a2000000-0000-0000-0000-000000000001', 'pairing_key', 'A:B',
            'lobby_slot_number', lobby_slot_number, 'team_slot_number', team_slot_number
        ) order by lobby_slot_number)
        from generate_series(1, 12) as slots(lobby_slot_number)
        cross join lateral (select case when lobby_slot_number <= 6 then lobby_slot_number else lobby_slot_number + 6 end as team_slot_number) as mapping_row
    ), 9
)), 'validation_failure', 'referenced pairing cannot be removed even with a complete remaining mapping');

insert into public.match_ocr_evidence(match_id, tournament_id, preserved_at, provenance)
values ('a4000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000001', now(), 'phase4a-test');
insert into public.match_ocr_row_evidence(match_id, tournament_id, row_index, original_placement, original_kills, original_suggested_team_slot, manual_review_required)
values ('a4000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000001', 0, 12, 0, 24, false);
select is((select original_suggested_team_slot from public.match_ocr_row_evidence where match_id = 'a4000000-0000-0000-0000-000000000001' and row_index = 0), 24, 'OCR row evidence accepts canonical slot 24');
insert into public.match_ocr_correction_snapshots(match_id, tournament_id, row_index, corrected_placement, corrected_kills, corrected_team_slot, placement_changed, kills_changed, team_slot_changed, preserved_at, provenance)
values ('a4000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000001', 0, 12, 0, 24, false, false, true, now(), 'phase4a-test');
select is((select corrected_team_slot from public.match_ocr_correction_snapshots where match_id = 'a4000000-0000-0000-0000-000000000001' and row_index = 0), 24, 'OCR correction accepts canonical slot 24');
select throws_ok($$
    insert into public.match_ocr_row_evidence(match_id, tournament_id, row_index, original_placement, original_kills, original_suggested_team_slot, manual_review_required)
    values ('a4000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000001', 1, 12, 0, 25, false)
$$, '23514', null, 'OCR row identity 25 remains rejected');
select throws_ok($$
    insert into public.match_ocr_correction_snapshots(match_id, tournament_id, row_index, corrected_placement, corrected_kills, corrected_team_slot, placement_changed, kills_changed, team_slot_changed, preserved_at, provenance)
    values ('a4000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000001', 1, 12, 0, 25, false, false, true, now(), 'phase4a-test')
$$, '23514', null, 'OCR correction identity 25 remains rejected');
select throws_ok($$
    insert into public.match_ocr_correction_snapshots(match_id, tournament_id, row_index, corrected_placement, corrected_kills, corrected_team_slot, placement_changed, kills_changed, team_slot_changed, preserved_at, provenance)
    values ('a4000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000001', 1, 13, 0, 24, true, false, true, now(), 'phase4a-test')
$$, '23514', null, 'OCR correction placement 13 remains rejected');
select throws_ok($$
    insert into public.match_ocr_row_evidence(match_id, tournament_id, row_index, original_placement, original_kills, original_suggested_team_slot, manual_review_required)
    values ('a4000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000001', 2, 13, 0, 24, false)
$$, '23514', null, 'OCR placement 13 remains rejected');

select is((select outcome from public.write_tournament_snapshot(
    (select tournament from pg_temp.v2_payloads where payload_key = 'standard'),
    (select team_slots from pg_temp.v2_payloads where payload_key = 'standard'),
    '[]'::jsonb, 0
)), 'success', 'legacy Standard snapshot remains accepted');
select is((select count(*) from public.tournament_team_slots where tournament_id = 'a2000000-0000-0000-0000-000000000003'), 12::bigint, 'legacy Standard snapshot still stores twelve slots');
insert into public.matches (id, tournament_id, match_number, status)
values ('a4000000-0000-0000-0000-000000000003', 'a2000000-0000-0000-0000-000000000003', 1, 'draft');
select is((select outcome from public.finalize_match_snapshot(
    'a2000000-0000-0000-0000-000000000003',
    jsonb_build_object('id', 'a4000000-0000-0000-0000-000000000003', 'status', 'finalized'),
    (
        select jsonb_agg(jsonb_build_object(
            'id', ('a4500000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'match_id', 'a4000000-0000-0000-0000-000000000003',
            'team_slot_id', ('a3030000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
            'placement', case when slot_number = 1 then null else slot_number end,
            'kills', 1, 'source', 'manual', 'review_status', 'confirmed'
        ) order by slot_number)
        from generate_series(1, 12) as slots(slot_number)
    ), 1
)), 'validation_failure', 'legacy Standard finalization remains strict when a participated placement is missing');
select is((select status from public.matches where id = 'a4000000-0000-0000-0000-000000000003'), 'draft', 'legacy strict finalization leaves the Standard match draft');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000003'), 1, 'legacy strict finalization rejection leaves the Standard revision unchanged');
select ok(to_regprocedure('public.write_tournament_snapshot(jsonb,jsonb,jsonb,integer)') is not null, 'legacy tournament snapshot signature remains unchanged');
select ok(to_regprocedure('public.write_match_snapshot(uuid,jsonb,jsonb,integer)') is not null, 'legacy match snapshot signature remains unchanged');
select ok(to_regprocedure('public.finalize_match_snapshot(uuid,jsonb,jsonb,integer)') is not null, 'legacy finalization signature remains unchanged');

select * from finish();
rollback;
