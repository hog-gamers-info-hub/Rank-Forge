begin;

select plan(23);

select is(
    (
        select count(*)
        from pg_constraint
        where conrelid = 'public.export_operations'::regclass
          and conname = 'export_operations_rows_written_check'
          and pg_get_constraintdef(oid) ~* 'rows_written'
          and pg_get_constraintdef(oid) ~* '1'
          and pg_get_constraintdef(oid) ~* '12'
    ),
    1::bigint,
    'rows_written has the generic one-through-twelve domain constraint'
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
    'completion RPC remains SECURITY DEFINER with an empty search_path'
);

insert into auth.users (id, email)
values ('92000000-0000-4000-8000-000000000001', 'export-operation-owner@example.test');

insert into public.tournaments (id, owner_id, name)
values ('92000000-0000-4000-8000-000000000011', '92000000-0000-4000-8000-000000000001', 'Export Operation Cup');

insert into public.matches (id, tournament_id, match_number)
values ('92000000-0000-4000-8000-000000000021', '92000000-0000-4000-8000-000000000011', 1);

set local role authenticated;
set local request.jwt.claim.sub = '92000000-0000-4000-8000-000000000001';

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_match',
            '92000000-0000-4000-8000-000000000011',
            '92000000-0000-4000-8000-000000000021',
            repeat('a', 64)
        )
    ),
    'claimed'::text,
    'ten-row match operation is claimed'
);

select is(
    public.mark_export_operation_write_started(
        (
            select id
            from public.export_operations
            where payload_fingerprint = repeat('a', 64)
        ),
        (
            select lease_token
            from public.export_operations
            where payload_fingerprint = repeat('a', 64)
        )
    ),
    'write_started'::text,
    'ten-row match operation enters write_started'
);

select is(
    public.complete_export_operation_success(
        (
            select id
            from public.export_operations
            where payload_fingerprint = repeat('a', 64)
        ),
        (
            select lease_token
            from public.export_operations
            where payload_fingerprint = repeat('a', 64)
        ),
        10,
        null
    ),
    'succeeded'::text,
    'ten-row match operation completes successfully'
);

select is(
    (
        select jsonb_build_object(
            'operation_type', operation_type,
            'state', state,
            'rows_written', rows_written,
            'exported_match_count', exported_match_count
        )
        from public.export_operations
        where payload_fingerprint = repeat('a', 64)
    )::text,
    '{"operation_type": "export_match", "state": "succeeded", "rows_written": 10, "exported_match_count": null}'::jsonb::text,
    'ten-row match success stores participant-aware metadata'
);

select is(
    (
        select jsonb_build_object(outcome, rows_written)
        from public.claim_export_operation(
            'export_match',
            '92000000-0000-4000-8000-000000000011',
            '92000000-0000-4000-8000-000000000021',
            repeat('a', 64)
        )
    )::text,
    '{"replayed": 10}'::jsonb::text,
    'replaying the ten-row match returns rows_written ten'
);

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_match',
            '92000000-0000-4000-8000-000000000011',
            '92000000-0000-4000-8000-000000000021',
            repeat('b', 64)
        )
    ),
    'claimed'::text,
    'twelve-row match operation is claimed'
);

select is(
    public.mark_export_operation_write_started(
        (select id from public.export_operations where payload_fingerprint = repeat('b', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('b', 64))
    ),
    'write_started'::text,
    'twelve-row match operation enters write_started'
);

select is(
    public.complete_export_operation_success(
        (select id from public.export_operations where payload_fingerprint = repeat('b', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('b', 64)),
        12,
        null
    ),
    'succeeded'::text,
    'twelve-row match operation remains compatible'
);

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_match',
            '92000000-0000-4000-8000-000000000011',
            '92000000-0000-4000-8000-000000000021',
            repeat('c', 64)
        )
    ),
    'claimed'::text,
    'one-row match operation is claimed'
);

select is(
    public.mark_export_operation_write_started(
        (select id from public.export_operations where payload_fingerprint = repeat('c', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('c', 64))
    ),
    'write_started'::text,
    'one-row match operation enters write_started'
);

select is(
    public.complete_export_operation_success(
        (select id from public.export_operations where payload_fingerprint = repeat('c', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('c', 64)),
        1,
        null
    ),
    'succeeded'::text,
    'one-row match operation completes successfully'
);

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_match',
            '92000000-0000-4000-8000-000000000011',
            '92000000-0000-4000-8000-000000000021',
            repeat('d', 64)
        )
    ),
    'claimed'::text,
    'invalid-row-count match operation is claimed'
);

select is(
    public.mark_export_operation_write_started(
        (select id from public.export_operations where payload_fingerprint = repeat('d', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('d', 64))
    ),
    'write_started'::text,
    'invalid-row-count match operation enters write_started'
);

select throws_ok($$
    select public.complete_export_operation_success(
        (select id from public.export_operations where payload_fingerprint = repeat('d', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('d', 64)),
        0,
        null
    )
$$, '22023', 'invalid written row count', 'match completion rejects zero rows_written');

select throws_ok($$
    select public.complete_export_operation_success(
        (select id from public.export_operations where payload_fingerprint = repeat('d', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('d', 64)),
        13,
        null
    )
$$, '22023', 'invalid written row count', 'match completion rejects thirteen rows_written');

select throws_ok($$
    select public.complete_export_operation_success(
        (select id from public.export_operations where payload_fingerprint = repeat('d', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('d', 64)),
        10,
        1
    )
$$, '22023', 'invalid export success metadata', 'match completion rejects exported match count metadata');

reset role;

select throws_ok($$
    insert into public.export_operations (
        owner_id, operation_type, tournament_id, match_id,
        payload_fingerprint, state, rows_written
    ) values (
        '92000000-0000-4000-8000-000000000001',
        'export_match',
        '92000000-0000-4000-8000-000000000011',
        '92000000-0000-4000-8000-000000000021',
        repeat('d', 64),
        'retryable_failure',
        0
    )
$$, '23514', null, 'table constraint rejects zero rows_written');

select throws_ok($$
    insert into public.export_operations (
        owner_id, operation_type, tournament_id, match_id,
        payload_fingerprint, state, rows_written
    ) values (
        '92000000-0000-4000-8000-000000000001',
        'export_match',
        '92000000-0000-4000-8000-000000000011',
        '92000000-0000-4000-8000-000000000021',
        repeat('e', 64),
        'retryable_failure',
        13
    )
$$, '23514', null, 'table constraint rejects thirteen rows_written');

set local role authenticated;
set local request.jwt.claim.sub = '92000000-0000-4000-8000-000000000001';

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_standings',
            '92000000-0000-4000-8000-000000000011',
            null,
            repeat('f', 64)
        )
    ),
    'claimed'::text,
    'twelve-row standings operation is claimed'
);

select is(
    public.mark_export_operation_write_started(
        (select id from public.export_operations where payload_fingerprint = repeat('f', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('f', 64))
    ),
    'write_started'::text,
    'standings operation enters write_started'
);

select is(
    public.complete_export_operation_success(
        (select id from public.export_operations where payload_fingerprint = repeat('f', 64)),
        (select lease_token from public.export_operations where payload_fingerprint = repeat('f', 64)),
        12,
        1
    ),
    'succeeded'::text,
    'twelve-row standings operation remains compatible'
);

select * from finish();
rollback;
