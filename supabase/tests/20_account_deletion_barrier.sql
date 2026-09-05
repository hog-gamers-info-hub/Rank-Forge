begin;

select plan(54);

select has_table(
    'private',
    'account_deletion_guards',
    'private account deletion guard table exists'
);
select has_column(
    'private',
    'account_deletion_guards',
    'user_id',
    'guard has user_id column'
);
select has_column(
    'private',
    'account_deletion_guards',
    'state',
    'guard has state column'
);
select is((
    select count(*)
    from pg_constraint
    where conrelid = 'private.account_deletion_guards'::regclass
        and conname = 'account_deletion_guards_state_check'
), 1::bigint, 'guard has the active/deleting state check');
select is((
    select count(*)
    from pg_indexes
    where schemaname = 'private'
        and tablename = 'account_deletion_guards'
        and indexname = 'account_deletion_guards_user_id_idx'
), 1::bigint, 'guard user_id is indexed');
select ok(
    to_regprocedure('public.begin_account_deletion(uuid)') is not null,
    'begin deletion function exists'
);
select ok((select prosecdef
    from pg_proc
    where oid = 'public.begin_account_deletion(uuid)'::regprocedure),
    'begin deletion function is SECURITY DEFINER'
);
select ok((
    select coalesce(array_to_string(proconfig, ','), '') like '%search_path=""%'
    from pg_proc
    where oid = 'public.begin_account_deletion(uuid)'::regprocedure
), 'begin deletion function uses an empty search_path');
select ok((select prosecdef
    from pg_proc
    where oid = 'private.account_deletion_guard_is_active(uuid)'::regprocedure),
    'guard lock helper is SECURITY DEFINER'
);
select ok((select relrowsecurity
    from pg_class
    where oid = 'private.account_deletion_guards'::regclass),
    'guard table has RLS enabled as defense in depth'
);

insert into auth.users (id, email)
values
    ('a4000000-0000-0000-0000-000000000001', 'barrier-a@example.test'),
    ('a4000000-0000-0000-0000-000000000002', 'barrier-b@example.test'),
    ('a4000000-0000-0000-0000-000000000003', 'barrier-c@example.test');

select is((
    select count(*)
    from private.account_deletion_guards
    where user_id in (
        'a4000000-0000-0000-0000-000000000001',
        'a4000000-0000-0000-0000-000000000002',
        'a4000000-0000-0000-0000-000000000003'
    )
), 3::bigint, 'new Auth users receive guard rows');
select is((
    select count(*)
    from private.account_deletion_guards as guard_row
    join auth.users as auth_row on auth_row.id = guard_row.user_id
), (select count(*) from auth.users)::bigint,
    'every existing Auth user has a backfilled guard row');
select is((
    select count(*)
    from private.account_deletion_guards
    where state = 'active'
), 3::bigint, 'new and backfilled guard rows start active');
select ok(not has_table_privilege(
    'authenticated',
    'private.account_deletion_guards',
    'SELECT'
), 'authenticated cannot read the private guard table');
select ok(not has_table_privilege(
    'authenticated',
    'private.account_deletion_guards',
    'UPDATE'
), 'authenticated cannot update the private guard table');
select ok(not has_function_privilege(
    'anon',
    'public.begin_account_deletion(uuid)',
    'execute'
), 'anon cannot execute begin deletion');
select ok(not has_function_privilege(
    'authenticated',
    'public.begin_account_deletion(uuid)',
    'execute'
), 'authenticated cannot execute begin deletion');
select ok(has_function_privilege(
    'service_role',
    'public.begin_account_deletion(uuid)',
    'execute'
), 'service_role can execute begin deletion');
select ok(has_function_privilege(
    'authenticated',
    'private.account_deletion_guard_is_active(uuid)',
    'execute'
), 'authenticated can invoke only the boolean guard helper needed by RLS');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and tablename = 'tournaments'
), 4::bigint, 'tournament ownership policies remain intact');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and tablename = 'custom_design_templates'
        and policyname like 'custom_design_templates_%_owner'
), 3::bigint, 'custom design ownership policies remain intact');

insert into public.tournaments (id, owner_id, name)
values
    ('b4000000-0000-0000-0000-000000000001', 'a4000000-0000-0000-0000-000000000001', 'Barrier A Storage Tournament'),
    ('b4000000-0000-0000-0000-000000000002', 'a4000000-0000-0000-0000-000000000002', 'Barrier B Tournament');
insert into public.matches (id, tournament_id, match_number)
values ('c4000000-0000-0000-0000-000000000001', 'b4000000-0000-0000-0000-000000000001', 1);

set local role authenticated;
set local request.jwt.claim.sub = 'a4000000-0000-0000-0000-000000000001';
select lives_ok($$
    insert into public.tournaments (id, owner_id, name)
    values ('b4100000-0000-0000-0000-000000000001', 'a4000000-0000-0000-0000-000000000001', 'Barrier A Active Tournament')
$$, 'active user can create a tournament');
select lives_ok($$
    insert into public.custom_design_templates (
        id, user_id, image_path, image_sha256, image_byte_size,
        image_extension, image_mime_type, source_width, source_height,
        labels_json, columns_json, rows_json
    ) values (
        'a4200000-0000-0000-0000-000000000001',
        'a4000000-0000-0000-0000-000000000001',
        'users/a4000000-0000-0000-0000-000000000001/custom-designs/a4200000-0000-0000-0000-000000000001/original.png',
        repeat('a', 64), 10, 'png', 'image/png', 1000, 1300,
        '{"teamName":"TEAM","win":"WIN","totalKills":"KILLS","positionPoints":"POSITION","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":100,"WIN":300,"TOTAL_KILLS":500,"POSITION_POINTS":700,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    )
$$, 'active user can create a custom design');
select lives_ok($$
    insert into storage.objects (id, bucket_id, name, owner_id, metadata)
    values (
        'a4300000-0000-0000-0000-000000000001',
        'custom-designs',
        'users/a4000000-0000-0000-0000-000000000001/custom-designs/a4200000-0000-0000-0000-000000000001/original.png',
        'a4000000-0000-0000-0000-000000000001',
        '{"mimetype":"image/png"}'::jsonb
    )
$$, 'active user can upload a custom design object');
select lives_ok($$
    insert into storage.objects (id, bucket_id, name, owner_id, metadata)
    values (
        'a4300000-0000-0000-0000-000000000002',
        'match-screenshots',
        'users/a4000000-0000-0000-0000-000000000001/tournaments/b4000000-0000-0000-0000-000000000001/matches/c4000000-0000-0000-0000-000000000001/original.png',
        'a4000000-0000-0000-0000-000000000001',
        '{"mimetype":"image/png"}'::jsonb
    )
$$, 'active user can upload a match screenshot object');
select lives_ok($$
    insert into storage.objects (id, bucket_id, name, owner_id, metadata)
    values (
        'a4300000-0000-0000-0000-000000000003',
        'ocr-screenshots',
        'users/a4000000-0000-0000-0000-000000000001/tournaments/b4000000-0000-0000-0000-000000000001/matches/c4000000-0000-0000-0000-000000000001/result/upper/original.png',
        'a4000000-0000-0000-0000-000000000001',
        '{"mimetype":"image/png"}'::jsonb
    )
$$, 'active user can upload an OCR screenshot object');
set local request.jwt.claim.sub = 'a4000000-0000-0000-0000-000000000002';
select lives_ok($$
    insert into public.tournaments (id, owner_id, name)
    values ('b4100000-0000-0000-0000-000000000002', 'a4000000-0000-0000-0000-000000000002', 'Barrier B Active Tournament')
$$, 'account B mutation is unaffected while account A is active');
reset role;

set local role service_role;
set local request.jwt.claim.role = 'service_role';
create temporary table barrier_a_result as
select * from public.begin_account_deletion('a4000000-0000-0000-0000-000000000001');
reset role;
select is((select state from barrier_a_result), 'deleting'::text, 'begin deletion transitions account A to deleting');

set local role authenticated;
set local request.jwt.claim.sub = 'a4000000-0000-0000-0000-000000000001';
select throws_ok($$
    insert into public.tournaments (id, owner_id, name)
    values ('b4100000-0000-0000-0000-000000000011', 'a4000000-0000-0000-0000-000000000001', 'Blocked')
$$, '42501', null, 'deleting user cannot create a tournament');
select throws_ok($$
    insert into public.custom_design_templates (
        id, user_id, image_path, image_sha256, image_byte_size,
        image_extension, image_mime_type, source_width, source_height,
        labels_json, columns_json, rows_json
    ) values (
        'a4200000-0000-0000-0000-000000000011',
        'a4000000-0000-0000-0000-000000000001',
        'users/a4000000-0000-0000-0000-000000000001/custom-designs/a4200000-0000-0000-0000-000000000011/original.png',
        repeat('b', 64), 10, 'png', 'image/png', 1000, 1300,
        '{"teamName":"TEAM","win":"WIN","totalKills":"KILLS","positionPoints":"POSITION","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":100,"WIN":300,"TOTAL_KILLS":500,"POSITION_POINTS":700,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    )
$$, '42501', null, 'deleting user cannot create a custom design');
select throws_ok($$
    insert into storage.objects (id, bucket_id, name, owner_id, metadata)
    values (
        'a4300000-0000-0000-0000-000000000011', 'custom-designs',
        'users/a4000000-0000-0000-0000-000000000001/custom-designs/a4200000-0000-0000-0000-000000000011/original.png',
        'a4000000-0000-0000-0000-000000000001', '{"mimetype":"image/png"}'::jsonb
    )
$$, '42501', null, 'deleting user cannot upload a custom design object');
select throws_ok($$
    insert into storage.objects (id, bucket_id, name, owner_id, metadata)
    values (
        'a4300000-0000-0000-0000-000000000012', 'match-screenshots',
        'users/a4000000-0000-0000-0000-000000000001/tournaments/b4000000-0000-0000-0000-000000000001/matches/c4000000-0000-0000-0000-000000000001/new.png',
        'a4000000-0000-0000-0000-000000000001', '{"mimetype":"image/png"}'::jsonb
    )
$$, '42501', null, 'deleting user cannot upload a match screenshot');
select throws_ok($$
    insert into storage.objects (id, bucket_id, name, owner_id, metadata)
    values (
        'a4300000-0000-0000-0000-000000000013', 'ocr-screenshots',
        'users/a4000000-0000-0000-0000-000000000001/tournaments/b4000000-0000-0000-0000-000000000001/matches/c4000000-0000-0000-0000-000000000001/result/upper/new.png',
        'a4000000-0000-0000-0000-000000000001', '{"mimetype":"image/png"}'::jsonb
    )
$$, '42501', null, 'deleting user cannot upload an OCR screenshot');
reset role;

set local role authenticated;
set local request.jwt.claim.sub = 'a4000000-0000-0000-0000-000000000002';
select lives_ok($$
    insert into public.tournaments (id, owner_id, name)
    values ('b4100000-0000-0000-0000-000000000012', 'a4000000-0000-0000-0000-000000000002', 'Barrier B Still Active')
$$, 'account B remains writable after account A enters deletion');
select throws_ok($$
    update private.account_deletion_guards
    set state = 'deleting'
    where user_id = 'a4000000-0000-0000-0000-000000000002'
$$, '42501', null, 'authenticated cannot modify lifecycle state directly');
select throws_ok($$
    select * from public.begin_account_deletion('a4000000-0000-0000-0000-000000000001')
$$, '42501', null, 'authenticated cannot target another account guard');
reset role;

set local role service_role;
set local request.jwt.claim.role = 'service_role';
create temporary table barrier_a_again as
select * from public.begin_account_deletion('a4000000-0000-0000-0000-000000000001');
reset role;
select is((select state || ':' || active_export_operations from barrier_a_again), 'deleting:0', 'begin deletion is idempotent for account A');

reset request.jwt.claim.sub;
insert into public.export_operations (
    id, owner_id, operation_type, tournament_id, match_id,
    payload_fingerprint, state, lease_token, lease_expires_at
)
values (
    'a4400000-0000-0000-0000-000000000001',
    'a4000000-0000-0000-0000-000000000002',
    'export_standings', 'b4000000-0000-0000-0000-000000000002', null,
    repeat('c', 64), 'in_progress',
    'a4500000-0000-0000-0000-000000000001', now() + interval '90 seconds'
);
set local request.jwt.claim.role = 'service_role';
create temporary table barrier_b_result as
select * from public.begin_account_deletion('a4000000-0000-0000-0000-000000000002');
reset role;
select is((select active_export_operations from barrier_b_result), 0::bigint, 'pre-write export claim is closed by the deletion barrier');
select is((select state from public.export_operations where id = 'a4400000-0000-0000-0000-000000000001'), 'retryable_failure'::text, 'pre-write export claim becomes terminal retryable failure');

insert into auth.users (id, email)
values ('a4000000-0000-0000-0000-000000000004', 'barrier-d@example.test');
insert into public.tournaments (id, owner_id, name)
values ('b4000000-0000-0000-0000-000000000004', 'a4000000-0000-0000-0000-000000000004', 'Barrier D Tournament');
insert into public.export_operations (
    id, owner_id, operation_type, tournament_id, match_id,
    payload_fingerprint, state, lease_token, lease_expires_at
)
values (
    'a4400000-0000-0000-0000-000000000004',
    'a4000000-0000-0000-0000-000000000004',
    'export_standings', 'b4000000-0000-0000-0000-000000000004', null,
    repeat('d', 64), 'write_started',
    'a4500000-0000-0000-0000-000000000004', now() + interval '90 seconds'
);
set local request.jwt.claim.role = 'service_role';
create temporary table barrier_d_result as
select * from public.begin_account_deletion('a4000000-0000-0000-0000-000000000004');
reset role;
select is((select active_export_operations from barrier_d_result), 1::bigint, 'write-started export is detected and blocks cleanup');
select is((select state from private.account_deletion_guards where user_id = 'a4000000-0000-0000-0000-000000000004'), 'deleting'::text, 'account D remains deleting while external export is active');
set local role authenticated;
set local request.jwt.claim.sub = 'a4000000-0000-0000-0000-000000000004';
select is(
    public.complete_export_operation_success(
        'a4400000-0000-0000-0000-000000000004',
        'a4500000-0000-0000-0000-000000000004',
        12,
        2
    ),
    'succeeded'::text,
    'write-started export can still settle after the deletion barrier'
);
reset role;

reset request.jwt.claim.sub;
insert into auth.users (id, email)
values ('a4000000-0000-0000-0000-000000000005', 'barrier-e@example.test');
insert into public.tournaments (id, owner_id, name)
values ('b4000000-0000-0000-0000-000000000005', 'a4000000-0000-0000-0000-000000000005', 'Barrier E Tournament');
insert into public.export_operations (
    id, owner_id, operation_type, tournament_id, match_id,
    payload_fingerprint, state
)
values (
    'a4400000-0000-0000-0000-000000000005',
    'a4000000-0000-0000-0000-000000000005',
    'export_standings', 'b4000000-0000-0000-0000-000000000005', null,
    repeat('e', 64), 'outcome_uncertain'
);
set local request.jwt.claim.role = 'service_role';
create temporary table barrier_e_result as
select * from public.begin_account_deletion('a4000000-0000-0000-0000-000000000005');
reset role;
select is((select state from barrier_e_result), 'deleting'::text, 'outcome-uncertain export enters the deleting barrier');
select is((select active_export_operations from barrier_e_result), 1::bigint, 'outcome-uncertain export is counted as active');
set local role authenticated;
set local request.jwt.claim.sub = 'a4000000-0000-0000-0000-000000000005';
select is(
    public.resolve_export_operation_verified_success(
        'a4400000-0000-0000-0000-000000000005',
        2
    ),
    'succeeded'::text,
    'outcome-uncertain export can settle through verified reconciliation'
);
reset role;
set local request.jwt.claim.role = 'service_role';
create temporary table barrier_e_after_settlement as
select * from public.begin_account_deletion('a4000000-0000-0000-0000-000000000005');
reset role;
select is((select active_export_operations from barrier_e_after_settlement), 0::bigint, 'settled outcome-uncertain export clears the active check');

set local role authenticated;
set local request.jwt.claim.sub = 'a4000000-0000-0000-0000-000000000005';
select throws_ok($$
    select * from public.claim_export_operation(
        'export_standings',
        'b4000000-0000-0000-0000-000000000005',
        null,
        repeat('f', 64)
    )
$$, '42501', null, 'deleting account cannot claim a new export');
select throws_ok($$
    insert into public.export_operations (
        id, owner_id, operation_type, tournament_id, match_id,
        payload_fingerprint, state, lease_token, lease_expires_at
    ) values (
        'a4400000-0000-0000-0000-000000000006',
        'a4000000-0000-0000-0000-000000000005',
        'export_standings', 'b4000000-0000-0000-0000-000000000005', null,
        repeat('f', 64), 'in_progress',
        'a4500000-0000-0000-0000-000000000006', now() + interval '90 seconds'
    )
$$, '42501', null, 'deleting account cannot create a new export operation');
reset role;

reset request.jwt.claim.sub;
insert into public.export_operations (
    id, owner_id, operation_type, tournament_id, match_id,
    payload_fingerprint, state, lease_token, lease_expires_at
)
values (
    'a4400000-0000-0000-0000-000000000006',
    'a4000000-0000-0000-0000-000000000005',
    'export_standings', 'b4000000-0000-0000-0000-000000000005', null,
    repeat('f', 64), 'in_progress',
    'a4500000-0000-0000-0000-000000000006', now() + interval '90 seconds'
);
set local role authenticated;
set local request.jwt.claim.sub = 'a4000000-0000-0000-0000-000000000005';
select throws_ok($$
    select public.mark_export_operation_write_started(
        'a4400000-0000-0000-0000-000000000006',
        'a4500000-0000-0000-0000-000000000006'
    )
$$, '42501', null, 'deleting account cannot start a new external write');
reset role;

reset request.jwt.claim.sub;
insert into public.tournaments (id, owner_id, name)
values ('b4000000-0000-0000-0000-000000000003', 'a4000000-0000-0000-0000-000000000003', 'Barrier C Active Tournament');
set local role authenticated;
set local request.jwt.claim.sub = 'a4000000-0000-0000-0000-000000000003';
select is((
    select outcome
    from public.claim_export_operation(
        'export_standings',
        'b4000000-0000-0000-0000-000000000003',
        null,
        repeat('b', 64)
    )
), 'claimed'::text, 'active account can claim an export normally');
reset role;

select lives_ok($$select * from public.purge_account_data('a4000000-0000-0000-0000-000000000001')$$, 'database purge can run without removing the lifecycle guard');
select is((select count(*) from private.account_deletion_guards where user_id = 'a4000000-0000-0000-0000-000000000001'), 1::bigint, 'lifecycle guard survives database purge');

delete from auth.users
where id = 'a4000000-0000-0000-0000-000000000001';
select is((select count(*) from private.account_deletion_guards where user_id = 'a4000000-0000-0000-0000-000000000001'), 0::bigint, 'Auth deletion removes the lifecycle guard');

set local role authenticated;
set local request.jwt.claim.sub = 'a4000000-0000-0000-0000-000000000001';
select throws_ok($$
    insert into public.tournaments (id, owner_id, name)
    values ('b4100000-0000-0000-0000-000000000099', 'a4000000-0000-0000-0000-000000000001', 'Stale JWT')
$$, '42501', null, 'stale JWT with missing guard cannot create data');
reset role;

select * from finish();
rollback;
