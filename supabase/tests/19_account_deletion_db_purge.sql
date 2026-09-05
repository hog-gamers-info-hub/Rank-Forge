begin;

select plan(49);

select ok(
    to_regprocedure('public.purge_account_data(uuid)') is not null,
    'account purge function exists'
);
select ok(
    (select prosecdef
     from pg_proc
     where oid = 'public.purge_account_data(uuid)'::regprocedure),
    'account purge function is SECURITY DEFINER'
);
select ok(
    (
        select coalesce(array_to_string(proconfig, ','), '') like '%search_path=""%'
        from pg_proc
        where oid = 'public.purge_account_data(uuid)'::regprocedure
    ),
    'account purge function uses an empty search_path'
);
select ok(
    not has_function_privilege(
        'anon',
        'public.purge_account_data(uuid)',
        'execute'
    ),
    'anon cannot execute account purge function'
);
select ok(
    not has_function_privilege(
        'authenticated',
        'public.purge_account_data(uuid)',
        'execute'
    ),
    'authenticated cannot execute account purge function'
);
select ok(
    has_function_privilege(
        'service_role',
        'public.purge_account_data(uuid)',
        'execute'
    ),
    'service_role can execute account purge function'
);

select throws_ok(
    $$select * from public.purge_account_data(null)$$,
    '22023',
    'INVALID_USER_ID',
    'null user id is rejected explicitly'
);

insert into auth.users (id, email)
values
    ('a9000000-0000-0000-0000-000000000001', 'purge-a@example.test'),
    ('a9000000-0000-0000-0000-000000000002', 'purge-b@example.test'),
    ('a9000000-0000-0000-0000-000000000003', 'purge-c@example.test');

insert into public.tournaments (id, owner_id, name)
values
    ('b9000000-0000-0000-0000-000000000001', 'a9000000-0000-0000-0000-000000000001', 'Purge A Cup'),
    ('b9000000-0000-0000-0000-000000000002', 'a9000000-0000-0000-0000-000000000002', 'Purge B Cup'),
    ('b9000000-0000-0000-0000-000000000003', 'a9000000-0000-0000-0000-000000000003', 'Purge C Cup');

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
values
    ('c9000000-0000-0000-0000-000000000001', 'b9000000-0000-0000-0000-000000000001', 1, 'Purge A Team'),
    ('c9000000-0000-0000-0000-000000000002', 'b9000000-0000-0000-0000-000000000002', 1, 'Purge B Team'),
    ('c9000000-0000-0000-0000-000000000003', 'b9000000-0000-0000-0000-000000000003', 1, 'Purge C Team');

insert into public.players (id, team_slot_id, display_name, normalized_name)
values (
    'd9000000-0000-0000-0000-000000000001',
    'c9000000-0000-0000-0000-000000000001',
    'Purge A Player',
    'purge a player'
);

insert into public.matches (id, tournament_id, match_number, status, finalized_at, finalized_by)
values
    ('e9000000-0000-0000-0000-000000000001', 'b9000000-0000-0000-0000-000000000001', 1, 'finalized', now(), 'a9000000-0000-0000-0000-000000000001'),
    ('e9000000-0000-0000-0000-000000000002', 'b9000000-0000-0000-0000-000000000002', 1, 'finalized', now(), 'a9000000-0000-0000-0000-000000000002'),
    ('e9000000-0000-0000-0000-000000000003', 'b9000000-0000-0000-0000-000000000003', 1, 'finalized', now(), 'a9000000-0000-0000-0000-000000000003');

insert into public.match_results (id, match_id, team_slot_id, placement, kills, source, review_status)
values (
    'f9000000-0000-0000-0000-000000000001',
    'e9000000-0000-0000-0000-000000000001',
    'c9000000-0000-0000-0000-000000000001',
    1,
    4,
    'manual',
    'confirmed'
);

insert into public.match_correction_audit_entries (
    id,
    tournament_id,
    match_id,
    match_result_id,
    team_slot_id,
    previous_placement,
    previous_kills,
    corrected_placement,
    corrected_kills,
    previous_revision,
    new_revision,
    corrected_by,
    correction_reason
)
values (
    'a9100000-0000-0000-0000-000000000001',
    'b9000000-0000-0000-0000-000000000001',
    'e9000000-0000-0000-0000-000000000001',
    'f9000000-0000-0000-0000-000000000001',
    'c9000000-0000-0000-0000-000000000001',
    1,
    3,
    1,
    4,
    1,
    2,
    'a9000000-0000-0000-0000-000000000001',
    'purge test'
);

insert into public.match_lobby_screenshot_assets (
    owner_id,
    tournament_id,
    match_id,
    lobby_screenshot_index,
    local_file_extension,
    mime_type,
    original_width,
    original_height,
    byte_size,
    sha256,
    local_status,
    upload_status,
    preserved_at
)
values (
    'a9000000-0000-0000-0000-000000000001',
    'b9000000-0000-0000-0000-000000000001',
    'e9000000-0000-0000-0000-000000000001',
    1,
    'png',
    'image/png',
    100,
    100,
    10,
    repeat('a', 64),
    'PRESERVED',
    'PENDING',
    now()
);

insert into public.match_result_screenshot_assets (
    owner_id,
    tournament_id,
    match_id,
    screenshot_kind,
    screenshot_role,
    local_file_extension,
    mime_type,
    original_width,
    original_height,
    byte_size,
    sha256,
    local_status,
    upload_status,
    preserved_at
)
values (
    'a9000000-0000-0000-0000-000000000001',
    'b9000000-0000-0000-0000-000000000001',
    'e9000000-0000-0000-0000-000000000001',
    'MATCH_RESULT',
    'MATCH_RESULT_UPPER',
    'png',
    'image/png',
    100,
    100,
    10,
    repeat('b', 64),
    'PRESERVED',
    'PENDING',
    now()
);

insert into public.match_screenshot_metadata (
    match_id,
    owner_id,
    tournament_id,
    local_file_extension,
    mime_type,
    width,
    height,
    byte_size,
    sha256,
    local_status,
    upload_status,
    preserved_at
)
values (
    'e9000000-0000-0000-0000-000000000001',
    'a9000000-0000-0000-0000-000000000001',
    'b9000000-0000-0000-0000-000000000001',
    'png',
    'image/png',
    100,
    100,
    10,
    repeat('c', 64),
    'PRESERVED',
    'PENDING',
    now()
);

insert into public.match_ocr_evidence (match_id, tournament_id, preserved_at, provenance)
values (
    'e9000000-0000-0000-0000-000000000001',
    'b9000000-0000-0000-0000-000000000001',
    now(),
    'purge test'
);

insert into public.match_ocr_row_evidence (
    match_id,
    tournament_id,
    row_index,
    manual_review_required
)
values (
    'e9000000-0000-0000-0000-000000000001',
    'b9000000-0000-0000-0000-000000000001',
    0,
    false
);

insert into public.match_ocr_correction_snapshots (
    match_id,
    tournament_id,
    row_index,
    corrected_placement,
    corrected_kills,
    corrected_team_slot,
    placement_changed,
    kills_changed,
    team_slot_changed,
    preserved_at,
    provenance
)
values (
    'e9000000-0000-0000-0000-000000000001',
    'b9000000-0000-0000-0000-000000000001',
    0,
    1,
    4,
    1,
    false,
    true,
    false,
    now(),
    'purge test'
);

insert into public.tournament_standings_shares (tournament_id, standings)
values (
    'b9000000-0000-0000-0000-000000000001',
    '[]'::jsonb
);

insert into public.custom_design_templates (
    id,
    user_id,
    image_path,
    image_sha256,
    image_byte_size,
    image_extension,
    image_mime_type,
    source_width,
    source_height,
    labels_json,
    columns_json,
    rows_json
)
values
    (
        'a9200000-0000-0000-0000-000000000001',
        'a9000000-0000-0000-0000-000000000001',
        'users/a9000000-0000-0000-0000-000000000001/custom-designs/a9200000-0000-0000-0000-000000000001/original.png',
        repeat('d', 64),
        10,
        'png',
        'image/png',
        1000,
        1300,
        '{"teamName":"TEAM","win":"WIN","totalKills":"KILLS","positionPoints":"POSITION","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":100,"WIN":300,"TOTAL_KILLS":500,"POSITION_POINTS":700,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    ),
    (
        'a9200000-0000-0000-0000-000000000002',
        'a9000000-0000-0000-0000-000000000002',
        'users/a9000000-0000-0000-0000-000000000002/custom-designs/a9200000-0000-0000-0000-000000000002/original.png',
        repeat('e', 64),
        10,
        'png',
        'image/png',
        1000,
        1300,
        '{"teamName":"TEAM","win":"WIN","totalKills":"KILLS","positionPoints":"POSITION","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":100,"WIN":300,"TOTAL_KILLS":500,"POSITION_POINTS":700,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    ),
    (
        'a9200000-0000-0000-0000-000000000003',
        'a9000000-0000-0000-0000-000000000003',
        'users/a9000000-0000-0000-0000-000000000003/custom-designs/a9200000-0000-0000-0000-000000000003/original.png',
        repeat('f', 64),
        10,
        'png',
        'image/png',
        1000,
        1300,
        '{"teamName":"TEAM","win":"WIN","totalKills":"KILLS","positionPoints":"POSITION","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":100,"WIN":300,"TOTAL_KILLS":500,"POSITION_POINTS":700,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    );

insert into public.deletion_receipts (owner_id, target_type, target_id)
values
    ('a9000000-0000-0000-0000-000000000001', 'MATCH', 'e9000000-0000-0000-0000-000000000001'),
    ('a9000000-0000-0000-0000-000000000002', 'MATCH', 'e9000000-0000-0000-0000-000000000002'),
    ('a9000000-0000-0000-0000-000000000003', 'MATCH', 'e9000000-0000-0000-0000-000000000003');

insert into public.export_operations (
    id,
    owner_id,
    operation_type,
    tournament_id,
    match_id,
    payload_fingerprint,
    state,
    failure_code
)
values
    ('a9300000-0000-0000-0000-000000000001', 'a9000000-0000-0000-0000-000000000001', 'export_match', 'b9000000-0000-0000-0000-000000000001', 'e9000000-0000-0000-0000-000000000001', repeat('1', 64), 'retryable_failure', 'TEST'),
    ('a9300000-0000-0000-0000-000000000002', 'a9000000-0000-0000-0000-000000000002', 'export_match', 'b9000000-0000-0000-0000-000000000002', 'e9000000-0000-0000-0000-000000000002', repeat('2', 64), 'retryable_failure', 'TEST'),
    ('a9300000-0000-0000-0000-000000000003', 'a9000000-0000-0000-0000-000000000003', 'export_match', 'b9000000-0000-0000-0000-000000000003', 'e9000000-0000-0000-0000-000000000003', repeat('3', 64), 'retryable_failure', 'TEST');

select is(
    (select count(*) from auth.users where id in (
        'a9000000-0000-0000-0000-000000000001',
        'a9000000-0000-0000-0000-000000000002',
        'a9000000-0000-0000-0000-000000000003'
    )),
    3::bigint,
    'fixture auth users exist before purge'
);

set local role service_role;
create temporary table purge_a_result as
select *
from public.purge_account_data('a9000000-0000-0000-0000-000000000001');
reset role;

select is((select deleted_tournaments from purge_a_result), 1::bigint, 'purge returns one deleted tournament');
select is((select deleted_custom_designs from purge_a_result), 1::bigint, 'purge returns one deleted custom design');
select is((select deleted_deletion_receipts from purge_a_result), 1::bigint, 'purge returns one deleted receipt');
select is((select deleted_export_operations from purge_a_result), 1::bigint, 'purge returns one directly deleted export operation');

select is((select count(*) from public.tournaments where owner_id = 'a9000000-0000-0000-0000-000000000001'), 0::bigint, 'owner A tournaments are deleted');
select is((select count(*) from public.custom_design_templates where user_id = 'a9000000-0000-0000-0000-000000000001'), 0::bigint, 'owner A custom designs are deleted');
select is((select count(*) from public.deletion_receipts where owner_id = 'a9000000-0000-0000-0000-000000000001'), 0::bigint, 'owner A deletion receipts are deleted');
select is((select count(*) from public.export_operations where owner_id = 'a9000000-0000-0000-0000-000000000001'), 0::bigint, 'owner A export operations are deleted');

select is((select count(*) from public.tournament_team_slots where tournament_id = 'b9000000-0000-0000-0000-000000000001'), 0::bigint, 'tournament delete cascades team slots');
select is((select count(*) from public.players where team_slot_id = 'c9000000-0000-0000-0000-000000000001'), 0::bigint, 'tournament delete cascades players');
select is((select count(*) from public.matches where tournament_id = 'b9000000-0000-0000-0000-000000000001'), 0::bigint, 'tournament delete cascades matches');
select is((select count(*) from public.match_results where match_id = 'e9000000-0000-0000-0000-000000000001'), 0::bigint, 'tournament delete cascades match results');
select is((select count(*) from public.match_correction_audit_entries where match_id = 'e9000000-0000-0000-0000-000000000001'), 0::bigint, 'tournament delete cascades correction audit entries');
select is((select count(*) from public.match_lobby_screenshot_assets where match_id = 'e9000000-0000-0000-0000-000000000001'), 0::bigint, 'tournament delete cascades lobby screenshot assets');
select is((select count(*) from public.match_result_screenshot_assets where match_id = 'e9000000-0000-0000-0000-000000000001'), 0::bigint, 'tournament delete cascades result screenshot assets');
select is((select count(*) from public.match_screenshot_metadata where match_id = 'e9000000-0000-0000-0000-000000000001'), 0::bigint, 'tournament delete cascades screenshot metadata');
select is((select count(*) from public.match_ocr_evidence where match_id = 'e9000000-0000-0000-0000-000000000001'), 0::bigint, 'tournament delete cascades OCR evidence');
select is((select count(*) from public.match_ocr_row_evidence where match_id = 'e9000000-0000-0000-0000-000000000001'), 0::bigint, 'tournament delete cascades OCR row evidence');
select is((select count(*) from public.match_ocr_correction_snapshots where match_id = 'e9000000-0000-0000-0000-000000000001'), 0::bigint, 'tournament delete cascades OCR correction snapshots');
select is((select count(*) from public.tournament_standings_shares where tournament_id = 'b9000000-0000-0000-0000-000000000001'), 0::bigint, 'tournament delete cascades standings shares');

select is((select count(*) from public.tournaments where owner_id = 'a9000000-0000-0000-0000-000000000002'), 1::bigint, 'owner B tournament is unchanged');
select is((select count(*) from public.custom_design_templates where user_id = 'a9000000-0000-0000-0000-000000000002'), 1::bigint, 'owner B custom design is unchanged');
select is((select count(*) from public.matches where tournament_id = 'b9000000-0000-0000-0000-000000000002'), 1::bigint, 'owner B match is unchanged');
select is((select count(*) from public.export_operations where owner_id = 'a9000000-0000-0000-0000-000000000002'), 1::bigint, 'owner B export operation is unchanged');
select is((select count(*) from auth.users where id in ('a9000000-0000-0000-0000-000000000001', 'a9000000-0000-0000-0000-000000000002')), 2::bigint, 'purge leaves Auth users A and B intact');

set local role service_role;
create temporary table purge_none_result as
select *
from public.purge_account_data('a9000000-0000-0000-0000-000000000099');
reset role;

select is((select deleted_tournaments from purge_none_result), 0::bigint, 'nonexistent user purge deletes zero tournaments');
select is((select deleted_custom_designs from purge_none_result), 0::bigint, 'nonexistent user purge deletes zero custom designs');
select is((select deleted_deletion_receipts from purge_none_result), 0::bigint, 'nonexistent user purge deletes zero receipts');
select is((select deleted_export_operations from purge_none_result), 0::bigint, 'nonexistent user purge deletes zero export operations');

update public.matches
set finalized_by = 'a9000000-0000-0000-0000-000000000003'
where id = 'e9000000-0000-0000-0000-000000000002';

set local role service_role;
select throws_ok(
    $$select * from public.purge_account_data('a9000000-0000-0000-0000-000000000003')$$,
    'P0001',
    'ACCOUNT_PURGE_RESIDUAL_USER_REFERENCE',
    'cross-owner residual reference fails the purge closed'
);
reset role;

select is((select count(*) from public.tournaments where owner_id = 'a9000000-0000-0000-0000-000000000003'), 1::bigint, 'rollback preserves owner C tournament');
select is((select count(*) from public.custom_design_templates where user_id = 'a9000000-0000-0000-0000-000000000003'), 1::bigint, 'rollback preserves owner C custom design');
select is((select count(*) from public.deletion_receipts where owner_id = 'a9000000-0000-0000-0000-000000000003'), 1::bigint, 'rollback preserves owner C deletion receipt');
select is((select count(*) from public.export_operations where owner_id = 'a9000000-0000-0000-0000-000000000003'), 1::bigint, 'rollback preserves owner C export operation');
select is((select count(*) from public.tournaments where owner_id = 'a9000000-0000-0000-0000-000000000002'), 1::bigint, 'cross-owner failure preserves owner B tournament');
select is((select count(*) from public.matches where id = 'e9000000-0000-0000-0000-000000000002' and finalized_by = 'a9000000-0000-0000-0000-000000000003'), 1::bigint, 'cross-owner reference remains unchanged after rollback');

set local role service_role;
create temporary table purge_a_again_result as
select *
from public.purge_account_data('a9000000-0000-0000-0000-000000000001');
reset role;

select is((select deleted_tournaments from purge_a_again_result), 0::bigint, 'repeated purge returns zero tournaments');
select is((select deleted_custom_designs from purge_a_again_result), 0::bigint, 'repeated purge returns zero custom designs');
select is((select deleted_deletion_receipts from purge_a_again_result), 0::bigint, 'repeated purge returns zero receipts');
select is((select deleted_export_operations from purge_a_again_result), 0::bigint, 'repeated purge returns zero export operations');
select is((select count(*) from auth.users where id = 'a9000000-0000-0000-0000-000000000001'), 1::bigint, 'repeated purge still leaves owner A Auth user intact');

select * from finish();
rollback;
