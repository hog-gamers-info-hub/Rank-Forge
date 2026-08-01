begin;

select plan(14);

select is((
    select count(*)
    from pg_proc procedure_row
    join pg_namespace namespace_row on namespace_row.oid = procedure_row.pronamespace
    where namespace_row.nspname = 'public'
      and procedure_row.proname = 'resolve_export_operation_verified_success'
), 1::bigint, 'verified-success reconciliation RPC exists');

select is((
    select count(*)
    from pg_proc procedure_row
    join pg_namespace namespace_row on namespace_row.oid = procedure_row.pronamespace
    where namespace_row.nspname = 'public'
      and procedure_row.proname = 'resolve_export_operation_verified_absent'
), 0::bigint, 'verified-absent reconciliation RPC does not exist');

select is((
    select count(*)
    from pg_proc procedure_row
    join pg_namespace namespace_row on namespace_row.oid = procedure_row.pronamespace
    where namespace_row.nspname = 'public'
      and procedure_row.proname = 'resolve_export_operation_verified_success'
      and procedure_row.prosecdef
      and coalesce(array_to_string(procedure_row.proconfig, ','), '') like '%search_path=""%'
), 1::bigint, 'verified-success RPC is security definer with empty search_path');

select is((
    select count(*)
    from information_schema.routine_privileges
    where specific_schema = 'public'
      and routine_name = 'resolve_export_operation_verified_success'
      and grantee = 'authenticated'
      and privilege_type = 'EXECUTE'
), 1::bigint, 'authenticated can execute verified-success RPC');

select is((
    select count(*)
    from information_schema.routine_privileges
    where specific_schema = 'public'
      and routine_name = 'resolve_export_operation_verified_success'
      and grantee in ('anon', 'PUBLIC')
      and privilege_type = 'EXECUTE'
), 0::bigint, 'anon and public cannot execute verified-success RPC');

insert into auth.users (id, email)
values
    ('91000000-0000-4000-8000-000000000001', 'export-owner@example.test'),
    ('91000000-0000-4000-8000-000000000002', 'export-other@example.test');

insert into public.tournaments (id, owner_id, name)
values
    ('91000000-0000-4000-8000-000000000011', '91000000-0000-4000-8000-000000000001', 'Owned export tournament'),
    ('91000000-0000-4000-8000-000000000012', '91000000-0000-4000-8000-000000000002', 'Other export tournament');

insert into public.matches (id, tournament_id, match_number, status)
values
    ('91000000-0000-4000-8000-000000000021', '91000000-0000-4000-8000-000000000011', 1, 'finalized');

insert into public.export_operations (
    id,
    owner_id,
    operation_type,
    tournament_id,
    match_id,
    payload_fingerprint,
    state,
    failure_code,
    rows_written,
    exported_match_count,
    completed_at
)
values
    (
        '91000000-0000-4000-8000-000000000101',
        '91000000-0000-4000-8000-000000000001',
        'export_match',
        '91000000-0000-4000-8000-000000000011',
        '91000000-0000-4000-8000-000000000021',
        repeat('a', 64),
        'outcome_uncertain',
        'EXPORT_VERIFICATION_FAILURE',
        null,
        null,
        clock_timestamp()
    ),
    (
        '91000000-0000-4000-8000-000000000102',
        '91000000-0000-4000-8000-000000000001',
        'export_standings',
        '91000000-0000-4000-8000-000000000011',
        null,
        repeat('b', 64),
        'outcome_uncertain',
        'EXPORT_VERIFICATION_FAILURE',
        null,
        null,
        clock_timestamp()
    ),
    (
        '91000000-0000-4000-8000-000000000103',
        '91000000-0000-4000-8000-000000000001',
        'export_match',
        '91000000-0000-4000-8000-000000000011',
        '91000000-0000-4000-8000-000000000021',
        repeat('c', 64),
        'outcome_uncertain',
        'EXPORT_VERIFICATION_FAILURE',
        null,
        null,
        clock_timestamp()
    ),
    (
        '91000000-0000-4000-8000-000000000104',
        '91000000-0000-4000-8000-000000000002',
        'export_standings',
        '91000000-0000-4000-8000-000000000012',
        null,
        repeat('d', 64),
        'outcome_uncertain',
        'EXPORT_VERIFICATION_FAILURE',
        null,
        null,
        clock_timestamp()
    );

set local role authenticated;
set local request.jwt.claim.sub = '91000000-0000-4000-8000-000000000001';

select is(
    public.resolve_export_operation_verified_success(
        '91000000-0000-4000-8000-000000000101',
        null
    ),
    'succeeded',
    'owned uncertain match operation resolves to succeeded'
);

select is((
    select jsonb_build_object(
        'state', state,
        'rows_written', rows_written,
        'exported_match_count', exported_match_count,
        'failure_code', failure_code,
        'lease_token', lease_token is null,
        'completed_at', completed_at is not null
    )
    from public.export_operations
    where id = '91000000-0000-4000-8000-000000000101'
)::text, '{"state": "succeeded", "lease_token": true, "failure_code": null, "completed_at": true, "rows_written": 12, "exported_match_count": null}'::jsonb::text, 'verified match success metadata is minimal and correct');

select is(
    public.resolve_export_operation_verified_success(
        '91000000-0000-4000-8000-000000000102',
        3
    ),
    'succeeded',
    'owned uncertain standings operation resolves to succeeded'
);

select is((
    select exported_match_count
    from public.export_operations
    where id = '91000000-0000-4000-8000-000000000102'
), 3, 'verified standings success stores exported match count');

select throws_ok($$
    select public.resolve_export_operation_verified_success(
        '91000000-0000-4000-8000-000000000103',
        1
    )
$$, '22023', null, 'match success rejects exported match count');

select throws_ok($$
    select public.resolve_export_operation_verified_success(
        '91000000-0000-4000-8000-000000000101',
        null
    )
$$, 'P0001', null, 'wrong current state is rejected');

select throws_ok($$
    select public.resolve_export_operation_verified_success(
        '91000000-0000-4000-8000-000000000104',
        1
    )
$$, 'P0001', null, 'cross-owner success reconciliation is rejected');

select throws_ok($$
    update public.export_operations
    set state = 'retryable_failure'
    where id = '91000000-0000-4000-8000-000000000103'
$$, '42501', null, 'direct authenticated ledger mutation remains prohibited');

set local role anon;
set local request.jwt.claim.sub = '';

select throws_ok($$
    select public.resolve_export_operation_verified_success(
        '91000000-0000-4000-8000-000000000104',
        1
    )
$$, '42501', null, 'anonymous success reconciliation is rejected');

select * from finish();
rollback;
