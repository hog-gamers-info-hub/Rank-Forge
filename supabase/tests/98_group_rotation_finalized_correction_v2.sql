begin;

select no_plan();

select ok(
    to_regprocedure('public.correct_finalized_match_snapshot_v2(uuid,uuid,jsonb,integer,text)') is not null,
    'Group Rotation correction V2 signature exists'
);
select is(
    (select prosecdef
     from pg_proc
     where oid = 'public.correct_finalized_match_snapshot_v2(uuid,uuid,jsonb,integer,text)'::regprocedure),
    true,
    'Group Rotation correction V2 is SECURITY DEFINER'
);
select ok(
    not has_function_privilege(
        'anon',
        'public.correct_finalized_match_snapshot_v2(uuid,uuid,jsonb,integer,text)',
        'execute'
    ),
    'anon cannot execute Group Rotation correction V2'
);
select ok(
    has_function_privilege(
        'authenticated',
        'public.correct_finalized_match_snapshot_v2(uuid,uuid,jsonb,integer,text)',
        'execute'
    ),
    'authenticated can execute Group Rotation correction V2'
);
select ok(
    (select proconfig @> array['search_path=pg_catalog, public']
     from pg_proc
     where oid = 'public.correct_finalized_match_snapshot_v2(uuid,uuid,jsonb,integer,text)'::regprocedure),
    'Group Rotation correction V2 uses the hardened search path'
);

insert into auth.users (id, email)
values
    ('d4d30000-0000-0000-0000-000000000001', 'phase4d3-owner@example.test'),
    ('d4d30000-0000-0000-0000-000000000002', 'phase4d3-other@example.test');

insert into public.tournaments (id, owner_id, name, format, group_count, status)
values
    ('d4d30000-0000-0000-0000-000000000101',
     'd4d30000-0000-0000-0000-000000000001',
     'Group Rotation Correction V2',
     'group_rotation',
     4,
     'active'),
    ('d4d30000-0000-0000-0000-000000000102',
     'd4d30000-0000-0000-0000-000000000001',
     'Standard Correction V2 Rejection',
     'standard',
     null,
     'active');

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name, status, "group")
select
    ('d4d30100-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
    'd4d30000-0000-0000-0000-000000000101',
    slot_number,
    'Group Team ' || slot_number,
    'complete',
    case
        when slot_number between 1 and 6 then 'A'
        when slot_number between 7 and 12 then 'B'
        when slot_number between 13 and 18 then 'C'
        else 'D'
    end
from generate_series(1, 24) as slots(slot_number);

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name, status)
select
    ('d4d30200-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
    'd4d30000-0000-0000-0000-000000000102',
    slot_number,
    'Standard Team ' || slot_number,
    'complete'
from generate_series(1, 12) as slots(slot_number);

insert into public.tournament_group_pairings (tournament_id, first_group, second_group)
values
    ('d4d30000-0000-0000-0000-000000000101', 'A', 'B'),
    ('d4d30000-0000-0000-0000-000000000101', 'A', 'C');

insert into public.tournament_group_pairing_lobby_slots (
    tournament_id,
    pairing_key,
    lobby_slot_number,
    team_slot_number
)
select
    'd4d30000-0000-0000-0000-000000000101',
    'A:C',
    lobby_slot_number,
    case when lobby_slot_number <= 6 then lobby_slot_number else lobby_slot_number + 6 end
from generate_series(1, 12) as slots(lobby_slot_number);

insert into public.matches (
    id,
    tournament_id,
    match_number,
    status,
    group_pairing_key
)
values
    ('d4d30300-0000-0000-0000-000000000001', 'd4d30000-0000-0000-0000-000000000101', 1, 'finalized', 'A:C'),
    ('d4d30300-0000-0000-0000-000000000002', 'd4d30000-0000-0000-0000-000000000101', 2, 'finalized', 'A:B'),
    ('d4d30300-0000-0000-0000-000000000003', 'd4d30000-0000-0000-0000-000000000101', 3, 'finalized', 'A:B'),
    ('d4d30300-0000-0000-0000-000000000004', 'd4d30000-0000-0000-0000-000000000101', 4, 'finalized', 'A:C'),
    ('d4d30300-0000-0000-0000-000000000005', 'd4d30000-0000-0000-0000-000000000101', 5, 'finalized', 'A:C');

insert into public.match_results (
    id,
    match_id,
    team_slot_id,
    placement,
    kills,
    source,
    review_status,
    participation_status
)
select
    ('d4d30400-0000-0000-0000-' || lpad((match_number * 100 + row_number() over (partition by match_number order by slot_number))::text, 12, '0'))::uuid,
    match_id,
    ('d4d30100-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
    row_number() over (partition by match_number order by slot_number),
    row_number() over (partition by match_number order by slot_number) - 1,
    'manual',
    'confirmed',
    'PARTICIPATED'
from (
    select 1 as match_number, 'd4d30300-0000-0000-0000-000000000001'::uuid as match_id, slot_number
    from unnest(array[1, 2, 3, 4, 5, 6, 13, 14, 15, 16, 17, 18]) as slots(slot_number)
    union all
    select 2, 'd4d30300-0000-0000-0000-000000000002'::uuid, slot_number
    from generate_series(1, 12) as slots(slot_number)
    union all
    select 3, 'd4d30300-0000-0000-0000-000000000003'::uuid, slot_number
    from generate_series(1, 12) as slots(slot_number)
    union all
    select 4, 'd4d30300-0000-0000-0000-000000000004'::uuid, slot_number
    from generate_series(1, 12) as slots(slot_number)
    union all
    select 5, 'd4d30300-0000-0000-0000-000000000005'::uuid, slot_number
    from unnest(array[1, 2, 3, 4, 5, 6, 13, 14, 15, 16, 17, 18]) as slots(slot_number)
) as result_rows(match_number, match_id, slot_number);

set local role authenticated;
set local request.jwt.claim.sub = 'd4d30000-0000-0000-0000-000000000001';

select is(
    (select outcome
     from public.correct_finalized_match_snapshot_v2(
         'd4d30000-0000-0000-0000-000000000101',
         'd4d30300-0000-0000-0000-000000000001',
         (
             select jsonb_agg(jsonb_build_object(
                 'id', result_row.id,
                 'match_id', result_row.match_id,
                 'team_slot_id', result_row.team_slot_id,
                 'placement', result_row.placement,
                 'kills', case when slot_row.slot_number = 18 then result_row.kills + 1 else result_row.kills end,
                 'participation_status', result_row.participation_status
             ) order by result_row.id)
             from public.match_results as result_row
             join public.tournament_team_slots as slot_row on slot_row.id = result_row.team_slot_id
             where result_row.match_id = 'd4d30300-0000-0000-0000-000000000001'
         ),
         1,
         'canonical slot 18 correction'
     )
    ),
    'success',
    'a valid Group Rotation correction preserves canonical identity 18'
);
select is(
    (select corrected_kills
     from public.match_correction_audit_entries
     where match_id = 'd4d30300-0000-0000-0000-000000000001'
       and team_slot_id = 'd4d30100-0000-0000-0000-000000000018'),
    12,
    'the canonical slot 18 correction is audited'
);

select is(
    (select outcome
     from public.correct_finalized_match_snapshot_v2(
         'd4d30000-0000-0000-0000-000000000101',
         'd4d30300-0000-0000-0000-000000000001',
         (
             select jsonb_agg(jsonb_build_object(
                 'id', result_row.id,
                 'match_id', result_row.match_id,
                 'team_slot_id', result_row.team_slot_id,
                 'placement', result_row.placement,
                 'kills', result_row.kills,
                 'participation_status', result_row.participation_status
             ) order by result_row.id)
             from public.match_results as result_row
             where result_row.match_id = 'd4d30300-0000-0000-0000-000000000001'
         ),
         2
     )
    ),
    'already_corrected',
    'an unchanged V2 correction is idempotent'
);
select is(
    (select outcome
     from public.correct_finalized_match_snapshot_v2(
         'd4d30000-0000-0000-0000-000000000101',
         'd4d30300-0000-0000-0000-000000000001',
         '[]'::jsonb,
         1
     )
    ),
    'stale_write',
    'a stale V2 correction is rejected before payload mutation'
);

set local request.jwt.claim.sub = 'd4d30000-0000-0000-0000-000000000002';
select is(
    (select outcome
     from public.correct_finalized_match_snapshot_v2(
         'd4d30000-0000-0000-0000-000000000101',
         'd4d30300-0000-0000-0000-000000000001',
         '[]'::jsonb,
         2
     )
    ),
    'unauthorized',
    'a non-owner cannot correct a Group Rotation match'
);
set local request.jwt.claim.sub = 'd4d30000-0000-0000-0000-000000000001';

select is(
    (select outcome
     from public.correct_finalized_match_snapshot_v2(
         'd4d30000-0000-0000-0000-000000000101',
         'd4d30300-0000-0000-0000-000000000002',
         (
             select jsonb_agg(to_jsonb(result_row) - 'created_at' - 'updated_at' order by result_row.id)
             from public.match_results as result_row
             where result_row.match_id = 'd4d30300-0000-0000-0000-000000000002'
         ),
         2
     )
    ),
    'validation_failure',
    'a finalized match without an authoritative mapping is rejected'
);

insert into public.tournament_group_pairing_lobby_slots (
    tournament_id,
    pairing_key,
    lobby_slot_number,
    team_slot_number
)
select
    'd4d30000-0000-0000-0000-000000000101',
    'A:B',
    lobby_slot_number,
    lobby_slot_number
from generate_series(1, 11) as slots(lobby_slot_number);

select is(
    (select outcome
     from public.correct_finalized_match_snapshot_v2(
         'd4d30000-0000-0000-0000-000000000101',
         'd4d30300-0000-0000-0000-000000000003',
         (
             select jsonb_agg(to_jsonb(result_row) - 'created_at' - 'updated_at' order by result_row.id)
             from public.match_results as result_row
             where result_row.match_id = 'd4d30300-0000-0000-0000-000000000003'
         ),
         2
     )
    ),
    'validation_failure',
    'a partial pairing mapping is rejected'
);

select is(
    (select outcome
     from public.correct_finalized_match_snapshot_v2(
         'd4d30000-0000-0000-0000-000000000101',
         'd4d30300-0000-0000-0000-000000000004',
         (
             select jsonb_agg(to_jsonb(result_row) - 'created_at' - 'updated_at' order by result_row.id)
             from public.match_results as result_row
             where result_row.match_id = 'd4d30300-0000-0000-0000-000000000004'
         ),
         2
     )
    ),
    'validation_failure',
    'existing finalized identities outside the selected mapping are rejected'
);

select is(
    (select outcome
     from public.correct_finalized_match_snapshot_v2(
         'd4d30000-0000-0000-0000-000000000101',
         'd4d30300-0000-0000-0000-000000000001',
         (
             select jsonb_agg(to_jsonb(result_row) - 'created_at' - 'updated_at' order by result_row.id)
             from public.match_results as result_row
             where result_row.match_id = 'd4d30300-0000-0000-0000-000000000001'
               and result_row.team_slot_id <> 'd4d30100-0000-0000-0000-000000000018'
         ),
         2
     )
    ),
    'validation_failure',
    'an incoming snapshot with fewer than twelve rows is rejected'
);

select is(
    (select outcome
     from public.correct_finalized_match_snapshot_v2(
         'd4d30000-0000-0000-0000-000000000101',
         'd4d30300-0000-0000-0000-000000000001',
         (
             select jsonb_agg(jsonb_build_object(
                 'id', result_row.id,
                 'match_id', result_row.match_id,
                 'team_slot_id', case
                     when result_row.team_slot_id = 'd4d30100-0000-0000-0000-000000000018'
                     then 'd4d30100-0000-0000-0000-000000000017'::uuid
                     else result_row.team_slot_id
                 end,
                 'placement', result_row.placement,
                 'kills', result_row.kills,
                 'participation_status', result_row.participation_status
             ) order by result_row.id)
             from public.match_results as result_row
             where result_row.match_id = 'd4d30300-0000-0000-0000-000000000001'
         ),
         2
     )
    ),
    'validation_failure',
    'duplicate incoming canonical identities are rejected'
);

select is(
    (select outcome
     from public.correct_finalized_match_snapshot_v2(
         'd4d30000-0000-0000-0000-000000000101',
         'd4d30300-0000-0000-0000-000000000001',
         (
             select jsonb_agg(jsonb_build_object(
                 'id', result_row.id,
                 'match_id', result_row.match_id,
                 'team_slot_id', case
                     when result_row.team_slot_id = 'd4d30100-0000-0000-0000-000000000001'
                     then 'd4d30100-0000-0000-0000-000000000013'::uuid
                     else result_row.team_slot_id
                 end,
                 'placement', result_row.placement,
                 'kills', result_row.kills,
                 'participation_status', result_row.participation_status
             ) order by result_row.id)
             from public.match_results as result_row
             where result_row.match_id = 'd4d30300-0000-0000-0000-000000000001'
         ),
         2
     )
    ),
    'validation_failure',
    'changing an existing result identity is rejected'
);

select is(
    (select outcome
     from public.correct_finalized_match_snapshot_v2(
         'd4d30000-0000-0000-0000-000000000101',
         'd4d30300-0000-0000-0000-000000000005',
         (
             select jsonb_agg(jsonb_build_object(
                 'id', result_row.id,
                 'match_id', result_row.match_id,
                 'team_slot_id', result_row.team_slot_id,
                 'placement', case
                     when slot_row.slot_number = 18 then null
                     when slot_row.slot_number <= 6 then slot_row.slot_number
                     else slot_row.slot_number - 6
                 end,
                 'kills', case when slot_row.slot_number = 18 then 0 else result_row.kills end,
                 'participation_status', case when slot_row.slot_number = 18 then 'NO_SHOW' else 'PARTICIPATED' end
             ) order by result_row.id)
             from public.match_results as result_row
             join public.tournament_team_slots as slot_row on slot_row.id = result_row.team_slot_id
             where result_row.match_id = 'd4d30300-0000-0000-0000-000000000005'
         ),
         2,
         'canonical slot 18 became a no-show'
     )
    ),
    'success',
    'a valid Group Rotation no-show correction succeeds'
);
select is(
    (select corrected_participation_status
     from public.match_correction_audit_entries
     where match_id = 'd4d30300-0000-0000-0000-000000000005'
       and team_slot_id = 'd4d30100-0000-0000-0000-000000000018'),
    'NO_SHOW',
    'participant status transitions are audited'
);
select is(
    (select corrected_placement
     from public.match_correction_audit_entries
     where match_id = 'd4d30300-0000-0000-0000-000000000005'
       and team_slot_id = 'd4d30100-0000-0000-0000-000000000018'),
    null::integer,
    'no-show correction audits a null placement'
);

select is(
    (select outcome
     from public.correct_finalized_match_snapshot_v2(
         'd4d30000-0000-0000-0000-000000000102',
         'd4d30300-0000-0000-0000-000000000001',
         '[]'::jsonb,
         1
     )
    ),
    'validation_failure',
    'the Group Rotation V2 RPC rejects Standard tournaments'
);

select is(
    (select outcome
     from public.correct_finalized_match_snapshot_v2(
         'd4d30000-0000-0000-0000-000000000101',
         'd4d30300-0000-0000-0000-000000000001',
         '{}'::jsonb,
         3
     )
    ),
    'validation_failure',
    'a non-array result payload returns validation_failure'
);

select is(
    (select outcome
     from public.correct_finalized_match_snapshot_v2(
         'd4d30000-0000-0000-0000-000000000101',
         'd4d30300-0000-0000-0000-000000000001',
         (
             select jsonb_agg(jsonb_strip_nulls(jsonb_build_object(
                 'id', result_row.id,
                 'match_id', result_row.match_id,
                 'team_slot_id', result_row.team_slot_id,
                 'kills', case
                     when result_row.team_slot_id = 'd4d30100-0000-0000-0000-000000000001'
                     then null
                     else result_row.kills
                 end,
                 'placement', result_row.placement,
                 'participation_status', result_row.participation_status
             )) order by result_row.id)
             from public.match_results as result_row
             where result_row.match_id = 'd4d30300-0000-0000-0000-000000000001'
         ),
         3
     )
    ),
    'validation_failure',
    'a twelve-row payload missing required kills returns validation_failure'
);

select is(
    (select count(*)
     from public.match_results
     where match_id = 'd4d30300-0000-0000-0000-000000000001'),
    12::bigint,
    'successful and rejected corrections preserve the twelve-result identity set'
);
select is(
    (select revision from public.tournaments where id = 'd4d30000-0000-0000-0000-000000000101'),
    3,
    'only the two successful corrections advance the Group Rotation tournament revision'
);

select * from finish();
rollback;
