begin;

select plan(20);

select is(
    (
        select count(*)
        from pg_constraint
        where conrelid = 'public.export_operations'::regclass
          and conname = 'export_operations_success_metadata_check'
    ),
    1::bigint,
    'standings success metadata constraint exists'
);

select is(
    (
        select count(*)
        from pg_proc as procedure_row
        join pg_namespace as namespace_row
          on namespace_row.oid = procedure_row.pronamespace
        where namespace_row.nspname = 'public'
          and procedure_row.proname = 'complete_export_operation_success'
          and procedure_row.prosecdef
          and coalesce(array_to_string(procedure_row.proconfig, ','), '') like '%search_path=""%'
    ),
    1::bigint,
    'standings completion RPC remains SECURITY DEFINER with an empty search_path'
);

insert into auth.users (id, email)
values ('93000000-0000-4000-8000-000000000001', 'standings-operation-owner@example.test');

insert into public.tournaments (id, owner_id, name)
values ('93000000-0000-4000-8000-000000000011', '93000000-0000-4000-8000-000000000001', 'Standings Operation Cup');

set local role authenticated;
set local request.jwt.claim.sub = '93000000-0000-4000-8000-000000000001';

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_standings',
            '93000000-0000-4000-8000-000000000011',
            null,
            repeat('a', 64)
        )
    ),
    'claimed'::text,
    'ten-row standings operation is claimed'
);

select is(
    public.mark_export_operation_write_started(
        (select id from public.export_operations where payload_fingerprint = repeat('a', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('a', 64))
    ),
    'write_started'::text,
    'ten-row standings operation enters write_started'
);

select is(
    public.complete_export_operation_success(
        (select id from public.export_operations where payload_fingerprint = repeat('a', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('a', 64)),
        10,
        2
    ),
    'succeeded'::text,
    'ten-row standings operation completes successfully'
);

select is(
    (
        select jsonb_build_object(rows_written, exported_match_count)
        from public.export_operations
        where payload_fingerprint = repeat('a', 64)
    )::text,
    '{"10": 2}'::jsonb::text,
    'ten-row standings success stores both participant-aware fields'
);

select is(
    (
        select jsonb_build_object(outcome, rows_written, 'exported_match_count', exported_match_count)
        from public.claim_export_operation(
            'export_standings',
            '93000000-0000-4000-8000-000000000011',
            null,
            repeat('a', 64)
        )
    )::text,
    '{"replayed": 10, "exported_match_count": 2}'::jsonb::text,
    'replaying ten-row standings returns its stored metadata'
);

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_standings',
            '93000000-0000-4000-8000-000000000011',
            null,
            repeat('b', 64)
        )
    ),
    'claimed'::text,
    'twelve-row standings operation remains compatible'
);

select is(
    public.mark_export_operation_write_started(
        (select id from public.export_operations where payload_fingerprint = repeat('b', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('b', 64))
    ),
    'write_started'::text,
    'twelve-row standings operation enters write_started'
);

select is(
    public.complete_export_operation_success(
        (select id from public.export_operations where payload_fingerprint = repeat('b', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('b', 64)),
        12,
        1
    ),
    'succeeded'::text,
    'twelve-row standings operation completes successfully'
);

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_standings',
            '93000000-0000-4000-8000-000000000011',
            null,
            repeat('c', 64)
        )
    ),
    'claimed'::text,
    'invalid-row-count standings operation is claimed'
);

select is(
    public.mark_export_operation_write_started(
        (select id from public.export_operations where payload_fingerprint = repeat('c', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('c', 64))
    ),
    'write_started'::text,
    'invalid-row-count standings operation enters write_started'
);

select throws_ok($$
    select public.complete_export_operation_success(
        (select id from public.export_operations where payload_fingerprint = repeat('c', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('c', 64)),
        0,
        1
    )
$$, '22023', 'invalid written row count', 'standings completion rejects zero rows_written');

select throws_ok($$
    select public.complete_export_operation_success(
        (select id from public.export_operations where payload_fingerprint = repeat('c', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('c', 64)),
        13,
        1
    )
$$, '22023', 'invalid written row count', 'standings completion rejects thirteen rows_written');

select throws_ok($$
    select public.complete_export_operation_success(
        (select id from public.export_operations where payload_fingerprint = repeat('c', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('c', 64)),
        10,
        null
    )
$$, '22023', 'invalid export success metadata', 'standings completion rejects missing exported match count');

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_standings',
            '93000000-0000-4000-8000-000000000011',
            null,
            repeat('d', 64)
        )
    ),
    'claimed'::text,
    'one-row standings operation is claimed'
);

select is(
    public.mark_export_operation_write_started(
        (select id from public.export_operations where payload_fingerprint = repeat('d', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('d', 64))
    ),
    'write_started'::text,
    'one-row standings operation enters write_started'
);

select is(
    public.complete_export_operation_success(
        (select id from public.export_operations where payload_fingerprint = repeat('d', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('d', 64)),
        1,
        1
    ),
    'succeeded'::text,
    'one-row standings operation completes successfully'
);

reset role;

select throws_ok($$
    insert into public.export_operations (
        owner_id, operation_type, tournament_id,
        payload_fingerprint, state, rows_written, exported_match_count
    ) values (
        '93000000-0000-4000-8000-000000000001',
        'export_standings',
        '93000000-0000-4000-8000-000000000011',
        repeat('e', 64), 'succeeded', 0, 1
    )
$$, '23514', null, 'table constraint rejects zero standings rows');

select throws_ok($$
    insert into public.export_operations (
        owner_id, operation_type, tournament_id,
        payload_fingerprint, state, rows_written, exported_match_count
    ) values (
        '93000000-0000-4000-8000-000000000001',
        'export_standings',
        '93000000-0000-4000-8000-000000000011',
        repeat('f', 64), 'succeeded', 10, null
    )
$$, '23514', null, 'table constraint rejects missing standings match count');

select * from finish();
rollback;
