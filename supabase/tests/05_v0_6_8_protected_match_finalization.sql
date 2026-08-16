begin;

select plan(34);

select ok(
    (to_regprocedure('public.finalize_match_snapshot(uuid,jsonb,jsonb,integer)') is not null)::boolean,
    'protected finalization RPC exists'
);
select ok(
    (select prosecdef from pg_proc where oid = 'public.finalize_match_snapshot(uuid,jsonb,jsonb,integer)'::regprocedure)::boolean,
    'protected finalization RPC uses its documented narrowly scoped ownership check'
);
select ok(
    has_function_privilege(
        'authenticated'::name,
        'public.finalize_match_snapshot(uuid,jsonb,jsonb,integer)'::regprocedure,
        'execute'::text
    )::boolean,
    'authenticated can invoke protected finalization RPC'
);
select ok(
    not has_function_privilege(
        'anon'::name,
        'public.finalize_match_snapshot(uuid,jsonb,jsonb,integer)'::regprocedure,
        'execute'::text
    )::boolean,
    'anonymous callers cannot invoke protected finalization RPC'
);
select ok(
    position(
        'already_finalized'::text in
        (select prosrc::text from pg_proc where oid = 'public.finalize_match_snapshot(uuid,jsonb,jsonb,integer)'::regprocedure)::text
    ) > 0,
    'protected finalization has an idempotent already-finalized outcome'
);
select ok(
    position(
        'finalized_protected'::text in
        (select prosrc::text from pg_proc where oid = 'public.write_match_snapshot(uuid,jsonb,jsonb,integer)'::regprocedure)::text
    ) > 0,
    'generic match writes reject attempts to overwrite finalized data'
);
select ok(
    (to_regprocedure('public.correct_finalized_match_snapshot(uuid,uuid,jsonb,integer,text)') is not null)::boolean,
    'protected correction RPC exists'
);
select ok(
    (select prosecdef from pg_proc where oid = 'public.correct_finalized_match_snapshot(uuid,uuid,jsonb,integer,text)'::regprocedure)::boolean,
    'protected correction RPC uses its documented narrowly scoped ownership check'
);
select ok(
    has_function_privilege(
        'authenticated'::name,
        'public.correct_finalized_match_snapshot(uuid,uuid,jsonb,integer,text)'::regprocedure,
        'execute'::text
    )::boolean,
    'authenticated can invoke protected correction RPC'
);
select ok(
    not has_function_privilege(
        'anon'::name,
        'public.correct_finalized_match_snapshot(uuid,uuid,jsonb,integer,text)'::regprocedure,
        'execute'::text
    )::boolean,
    'anonymous callers cannot invoke protected correction RPC'
);
select ok(
    position(
        'already_corrected'::text in
        (select prosrc::text from pg_proc where oid = 'public.correct_finalized_match_snapshot(uuid,uuid,jsonb,integer,text)'::regprocedure)::text
    ) > 0,
    'protected correction has an idempotent already-corrected outcome'
);

insert into auth.users (id, email)
values
    ('81000000-0000-0000-0000-000000000001', 'snapshot-owner@example.test'),
    ('81000000-0000-0000-0000-000000000002', 'snapshot-other@example.test');

insert into public.tournaments (id, owner_id, name, revision)
values
    ('82000000-0000-0000-0000-000000000003', '81000000-0000-0000-0000-000000000001', 'Snapshot Cup', 1),
    ('82000000-0000-0000-0000-000000000004', '81000000-0000-0000-0000-000000000002', 'Other Cup', 1),
    ('82000000-0000-0000-0000-000000000005', '81000000-0000-0000-0000-000000000001', 'Finalization Cup', 1);

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
select ('86000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
       '82000000-0000-0000-0000-000000000005'::uuid,
       slot_number,
       'Final Team ' || slot_number
from generate_series(1, 12) as slot_number;

insert into public.matches (id, tournament_id, match_number, status)
values ('87000000-0000-0000-0000-000000000001', '82000000-0000-0000-0000-000000000005', 1, 'draft');

set local role authenticated;
set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000001';

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', '82000000-0000-0000-0000-000000000006'::uuid,
            'owner_id', '81000000-0000-0000-0000-000000000001'::uuid,
            'name', 'Initial Snapshot',
            'status', 'draft'
        ),
        jsonb_build_array(jsonb_build_object(
            'id', '83000000-0000-0000-0000-000000000006'::uuid,
            'tournament_id', '82000000-0000-0000-0000-000000000006'::uuid,
            'slot_number', 1,
            'team_name', 'Snapshot Team',
            'status', 'draft'
        )),
        '[]'::jsonb,
        0
    )
), 'success', 'owner can write an initial tournament snapshot');
select is((select revision from public.tournaments where id = '82000000-0000-0000-0000-000000000006'), 1, 'initial tournament snapshot starts at revision one');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', '82000000-0000-0000-0000-000000000006'::uuid,
            'owner_id', '81000000-0000-0000-0000-000000000001'::uuid,
            'name', 'Updated Snapshot',
            'status', 'active'
        ),
        '[]'::jsonb,
        '[]'::jsonb,
        1
    )
), 'success', 'owner can update a tournament snapshot');
select is((select revision from public.tournaments where id = '82000000-0000-0000-0000-000000000006'), 2, 'tournament snapshot update advances revision');
select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', '82000000-0000-0000-0000-000000000006'::uuid,
            'owner_id', '81000000-0000-0000-0000-000000000001'::uuid,
            'name', 'Stale Snapshot',
            'status', 'active'
        ),
        '[]'::jsonb,
        '[]'::jsonb,
        1
    )
), 'stale_write', 'stale tournament snapshot is rejected');
select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', '82000000-0000-0000-0000-000000000004'::uuid,
            'owner_id', '81000000-0000-0000-0000-000000000002'::uuid,
            'name', 'Forged Snapshot',
            'status', 'draft'
        ),
        '[]'::jsonb,
        '[]'::jsonb,
        1
    )
), 'missing_revision', 'cross-account tournament snapshot is rejected');

select is((
    select outcome
    from public.write_match_snapshot(
        '82000000-0000-0000-0000-000000000006',
        jsonb_build_array(jsonb_build_object(
            'id', '84000000-0000-0000-0000-000000000006'::uuid,
            'tournament_id', '82000000-0000-0000-0000-000000000006'::uuid,
            'match_number', 1,
            'status', 'draft'
        )),
        jsonb_build_array(jsonb_build_object(
            'id', '85000000-0000-0000-0000-000000000006'::uuid,
            'match_id', '84000000-0000-0000-0000-000000000006'::uuid,
            'team_slot_id', '83000000-0000-0000-0000-000000000006'::uuid,
            'placement', 1,
            'kills', 0,
            'source', 'manual',
            'review_status', 'draft'
        )),
        2
    )
), 'success', 'owner can write an initial match snapshot');
select is((select revision from public.tournaments where id = '82000000-0000-0000-0000-000000000006'), 3, 'initial match snapshot advances tournament revision');
select is((
    select outcome
    from public.write_match_snapshot(
        '82000000-0000-0000-0000-000000000006',
        jsonb_build_array(jsonb_build_object(
            'id', '84000000-0000-0000-0000-000000000006'::uuid,
            'tournament_id', '82000000-0000-0000-0000-000000000006'::uuid,
            'match_number', 1,
            'map_name', 'Updated Map',
            'status', 'draft'
        )),
        jsonb_build_array(jsonb_build_object(
            'id', '85000000-0000-0000-0000-000000000006'::uuid,
            'match_id', '84000000-0000-0000-0000-000000000006'::uuid,
            'team_slot_id', '83000000-0000-0000-0000-000000000006'::uuid,
            'placement', 1,
            'kills', 1,
            'source', 'manual',
            'review_status', 'draft'
        )),
        3
    )
), 'success', 'owner can update a match snapshot');
select is((select revision from public.tournaments where id = '82000000-0000-0000-0000-000000000006'), 4, 'match snapshot update advances tournament revision');
select is((
    select outcome
    from public.write_match_snapshot(
        '82000000-0000-0000-0000-000000000006',
        '[]'::jsonb,
        '[]'::jsonb,
        3
    )
), 'stale_write', 'stale match snapshot is rejected');

set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000002';
select is((
    select outcome
    from public.write_match_snapshot(
        '82000000-0000-0000-0000-000000000006',
        '[]'::jsonb,
        '[]'::jsonb,
        4
    )
), 'missing_revision', 'cross-account match snapshot is rejected');

set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000001';
select is((
    select outcome
    from public.finalize_match_snapshot(
        '82000000-0000-0000-0000-000000000005',
        jsonb_build_object(
            'id', '87000000-0000-0000-0000-000000000001'::uuid,
            'status', 'finalized'
        ),
        '[]'::jsonb,
        1
    )
), 'validation_failure', 'finalization rejects an incomplete result set');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('88000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', '87000000-0000-0000-0000-000000000001'::uuid,
        'team_slot_id', ('86000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'team_slot_number_snapshot', slot_number,
        'team_name_snapshot', 'Final Team ' || slot_number,
        'placement', slot_number,
        'kills', slot_number - 1,
        'placement_points', case slot_number
            when 1 then 12 when 2 then 9 when 3 then 8 when 4 then 7
            when 5 then 6 when 6 then 5 when 7 then 4 when 8 then 3
            when 9 then 2 when 10 then 1 when 11 then 0 when 12 then 0
        end,
        'kill_points', slot_number - 1,
        'total_points', (case slot_number
            when 1 then 12 when 2 then 9 when 3 then 8 when 4 then 7
            when 5 then 6 when 6 then 5 when 7 then 4 when 8 then 3
            when 9 then 2 when 10 then 1 when 11 then 0 when 12 then 0
        end) + slot_number - 1,
        'source', 'manual',
        'review_status', 'confirmed',
        'players', '[]'::jsonb
    )) as value
    from generate_series(1, 12) as slot_number
)
select is((
    select outcome
    from public.finalize_match_snapshot(
        '82000000-0000-0000-0000-000000000005',
        jsonb_build_object(
            'id', '87000000-0000-0000-0000-000000000001'::uuid,
            'status', 'finalized'
        ),
        (select value from payload),
        1
    )
), 'success', 'owner can finalize a valid match');
select is((select revision from public.tournaments where id = '82000000-0000-0000-0000-000000000005'), 2, 'successful finalization advances tournament revision');
select is((select status from public.matches where id = '87000000-0000-0000-0000-000000000001'), 'finalized', 'successful finalization protects the match status');
select is((select count(*) from public.match_results where match_id = '87000000-0000-0000-0000-000000000001'), 12::bigint, 'successful finalization stores all match results');
select is((
    select outcome
    from public.write_match_snapshot(
        '82000000-0000-0000-0000-000000000005',
        jsonb_build_array(jsonb_build_object(
            'id', '87000000-0000-0000-0000-000000000001'::uuid,
            'tournament_id', '82000000-0000-0000-0000-000000000005'::uuid,
            'match_number', 1,
            'status', 'draft'
        )),
        '[]'::jsonb,
        2
    )
), 'finalized_protected', 'generic match snapshot cannot overwrite finalized data');

with payload as (
    select jsonb_agg(jsonb_build_object(
        'id', ('88000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'match_id', '87000000-0000-0000-0000-000000000001'::uuid,
        'team_slot_id', ('86000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
        'team_slot_number_snapshot', slot_number,
        'team_name_snapshot', 'Final Team ' || slot_number,
        'placement', slot_number,
        'kills', slot_number - 1,
        'placement_points', case slot_number
            when 1 then 12 when 2 then 9 when 3 then 8 when 4 then 7
            when 5 then 6 when 6 then 5 when 7 then 4 when 8 then 3
            when 9 then 2 when 10 then 1 when 11 then 0 when 12 then 0
        end,
        'kill_points', slot_number - 1,
        'total_points', (case slot_number
            when 1 then 12 when 2 then 9 when 3 then 8 when 4 then 7
            when 5 then 6 when 6 then 5 when 7 then 4 when 8 then 3
            when 9 then 2 when 10 then 1 when 11 then 0 when 12 then 0
        end) + slot_number - 1,
        'source', 'manual',
        'review_status', 'confirmed',
        'players', '[]'::jsonb
    )) as value
    from generate_series(1, 12) as slot_number
)
select is((
    select outcome
    from public.finalize_match_snapshot(
        '82000000-0000-0000-0000-000000000005',
        jsonb_build_object(
            'id', '87000000-0000-0000-0000-000000000001'::uuid,
            'status', 'finalized'
        ),
        (select value from payload),
        2
    )
), 'already_finalized', 'repeating finalization is idempotent');

set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000002';
select is((
    select outcome
    from public.finalize_match_snapshot(
        '82000000-0000-0000-0000-000000000005',
        jsonb_build_object('id', '87000000-0000-0000-0000-000000000001'::uuid, 'status', 'finalized'),
        '[]'::jsonb,
        2
    )
), 'unauthorized', 'wrong user cannot finalize another account match');

set local role anon;
set local request.jwt.claim.sub = '';
select throws_ok($$
    select *
    from public.finalize_match_snapshot(
        '82000000-0000-0000-0000-000000000005',
        jsonb_build_object('id', '87000000-0000-0000-0000-000000000001'::uuid, 'status', 'finalized'),
        '[]'::jsonb,
        2
    )
$$, '42501', null, 'anonymous caller cannot execute finalization');

set local role authenticated;
set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000001';
update public.matches
set status = 'draft'
where id = '87000000-0000-0000-0000-000000000001';
select is((select status from public.matches where id = '87000000-0000-0000-0000-000000000001'), 'finalized', 'direct finalized match mutation is denied');
update public.match_results
set kills = 99
where match_id = '87000000-0000-0000-0000-000000000001'
  and team_slot_id = '86000000-0000-0000-0000-000000000001';
select is((select kills from public.match_results where match_id = '87000000-0000-0000-0000-000000000001' and team_slot_id = '86000000-0000-0000-0000-000000000001'), 0, 'direct finalized result mutation is denied');

select * from finish();
rollback;
