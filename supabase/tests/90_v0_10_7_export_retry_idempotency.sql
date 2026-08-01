begin;

select plan(62);

select has_table(
    'public',
    'export_operations',
    'export_operations table exists'
);

select ok(
    (select relrowsecurity from pg_class where oid = 'public.export_operations'::regclass),
    'export_operations has RLS enabled'
);

select is(
    (
        select count(*)
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'export_operations'
    ),
    16::bigint,
    'export_operations has exactly the approved 16 columns'
);

select is(
    (
        select count(*)
        from pg_policies
        where schemaname = 'public'
          and tablename = 'export_operations'
          and lower(cmd) = 'select'
          and roles = array['authenticated'::name]
          and coalesce(qual, '') ~ 'auth\.uid'
    ),
    1::bigint,
    'export_operations exposes only an authenticated owner select policy'
);

select is(
    (
        select count(*)
        from pg_policies
        where schemaname = 'public'
          and tablename = 'export_operations'
          and 'anon'::name = any(roles)
    ),
    0::bigint,
    'export_operations has no anon policy'
);

select ok(
    (
        select pg_get_indexdef(index_row.indexrelid) ~* 'nulls not distinct'
        from pg_index as index_row
        join pg_class as class_row on class_row.oid = index_row.indexrelid
        where class_row.relname = 'export_operations_logical_identity_uidx'
    ),
    'logical export identity uses one NULLS NOT DISTINCT unique index'
);

select is(
    (
        select count(*)
        from pg_proc as procedure_row
        join pg_namespace as namespace_row
          on namespace_row.oid = procedure_row.pronamespace
        where namespace_row.nspname = 'public'
          and procedure_row.proname in (
              'claim_export_operation',
              'mark_export_operation_write_started',
              'complete_export_operation_success',
              'mark_export_operation_retryable_failure',
              'mark_export_operation_outcome_uncertain'
          )
    ),
    5::bigint,
    'all five export idempotency RPCs exist'
);

select is(
    (
        select count(*)
        from pg_proc as procedure_row
        join pg_namespace as namespace_row
          on namespace_row.oid = procedure_row.pronamespace
        where namespace_row.nspname = 'public'
          and procedure_row.proname in (
              'claim_export_operation',
              'mark_export_operation_write_started',
              'complete_export_operation_success',
              'mark_export_operation_retryable_failure',
              'mark_export_operation_outcome_uncertain'
          )
          and procedure_row.prosecdef
    ),
    5::bigint,
    'all export idempotency RPCs are narrowly privileged SECURITY DEFINER functions'
);

select is(
    (
        select count(*)
        from pg_proc as procedure_row
        join pg_namespace as namespace_row
          on namespace_row.oid = procedure_row.pronamespace
        where namespace_row.nspname = 'public'
          and procedure_row.proname in (
              'claim_export_operation',
              'mark_export_operation_write_started',
              'complete_export_operation_success',
              'mark_export_operation_retryable_failure',
              'mark_export_operation_outcome_uncertain'
          )
          and coalesce(array_to_string(procedure_row.proconfig, ','), '') like '%search_path=""%'
    ),
    5::bigint,
    'all export idempotency RPCs use an empty search_path'
);

select ok(
    has_function_privilege(
        'authenticated',
        'public.claim_export_operation(text,uuid,uuid,text)',
        'EXECUTE'
    ),
    'authenticated may execute claim_export_operation'
);

select ok(
    not has_function_privilege(
        'anon',
        'public.claim_export_operation(text,uuid,uuid,text)',
        'EXECUTE'
    ),
    'anon may not execute claim_export_operation'
);

insert into auth.users (id, email)
values
    ('81000000-0000-0000-0000-000000000001', 'export-owner-a@example.test'),
    ('81000000-0000-0000-0000-000000000002', 'export-owner-b@example.test');

insert into public.tournaments (id, owner_id, name)
values
    (
        '82000000-0000-0000-0000-000000000001',
        '81000000-0000-0000-0000-000000000001',
        'Owner A Tournament'
    ),
    (
        '82000000-0000-0000-0000-000000000002',
        '81000000-0000-0000-0000-000000000002',
        'Owner B Tournament'
    );

insert into public.matches (id, tournament_id, match_number)
values
    (
        '83000000-0000-0000-0000-000000000001',
        '82000000-0000-0000-0000-000000000001',
        1
    ),
    (
        '83000000-0000-0000-0000-000000000002',
        '82000000-0000-0000-0000-000000000002',
        1
    );

set local role authenticated;
set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000001';

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_match',
            '82000000-0000-0000-0000-000000000001',
            '83000000-0000-0000-0000-000000000001',
            repeat('a', 64)
        )
    ),
    'claimed'::text,
    'new logical match export is claimed'
);

select is(
    (
        select count(*)
        from public.export_operations
        where payload_fingerprint = repeat('a', 64)
    ),
    1::bigint,
    'first claim creates exactly one persistent operation row'
);

select is(
    (
        select owner_id
        from public.export_operations
        where payload_fingerprint = repeat('a', 64)
    ),
    '81000000-0000-0000-0000-000000000001'::uuid,
    'operation owner is derived from auth.uid'
);

select is(
    (
        select state
        from public.export_operations
        where payload_fingerprint = repeat('a', 64)
    ),
    'in_progress'::text,
    'new operation begins in progress'
);

select ok(
    (
        select lease_token is not null
           and lease_expires_at > clock_timestamp()
        from public.export_operations
        where payload_fingerprint = repeat('a', 64)
    ),
    'new claim has an active lease'
);

select is(
    (
        select attempt_count
        from public.export_operations
        where payload_fingerprint = repeat('a', 64)
    ),
    1,
    'new claim starts at attempt one'
);

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_match',
            '82000000-0000-0000-0000-000000000001',
            '83000000-0000-0000-0000-000000000001',
            repeat('a', 64)
        )
    ),
    'in_progress'::text,
    'duplicate request sees active operation instead of acquiring a second lease'
);

select is(
    (
        select count(*)
        from public.export_operations
        where payload_fingerprint = repeat('a', 64)
    ),
    1::bigint,
    'duplicate claim cannot create a second logical operation row'
);

select is(
    (
        select attempt_count
        from public.export_operations
        where payload_fingerprint = repeat('a', 64)
    ),
    1,
    'active duplicate claim does not increment attempts'
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
    'current lease durably marks write_started'
);

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_match',
            '82000000-0000-0000-0000-000000000001',
            '83000000-0000-0000-0000-000000000001',
            repeat('a', 64)
        )
    ),
    'in_progress'::text,
    'active write_started operation cannot be reclaimed'
);

select is(
    public.mark_export_operation_retryable_failure(
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
        'GOOGLE_SHEETS_ACCESS_DENIED'
    ),
    'retryable_failure'::text,
    'approved definitive append rejection may become retryable_failure'
);

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_match',
            '82000000-0000-0000-0000-000000000001',
            '83000000-0000-0000-0000-000000000001',
            repeat('a', 64)
        )
    ),
    'claimed'::text,
    'retryable failure may be safely reclaimed'
);

select is(
    (
        select attempt_count
        from public.export_operations
        where payload_fingerprint = repeat('a', 64)
    ),
    2,
    'safe reclaim increments the attempt count'
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
    'reclaimed worker may mark write_started'
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
        12,
        null
    ),
    'succeeded'::text,
    'confirmed match append finalizes success'
);

select ok(
    (
        select state = 'succeeded'
           and rows_written = 12
           and exported_match_count is null
           and lease_token is null
           and lease_expires_at is null
           and completed_at is not null
        from public.export_operations
        where payload_fingerprint = repeat('a', 64)
    ),
    'successful match export stores exact replay metadata and clears the lease'
);

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_match',
            '82000000-0000-0000-0000-000000000001',
            '83000000-0000-0000-0000-000000000001',
            repeat('a', 64)
        )
    ),
    'replayed'::text,
    'identical successful export replays without reopening the operation'
);

select is(
    (
        select rows_written
        from public.claim_export_operation(
            'export_match',
            '82000000-0000-0000-0000-000000000001',
            '83000000-0000-0000-0000-000000000001',
            repeat('a', 64)
        )
    ),
    12,
    'successful replay returns the persisted row count'
);

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_match',
            '82000000-0000-0000-0000-000000000001',
            '83000000-0000-0000-0000-000000000001',
            repeat('b', 64)
        )
    ),
    'claimed'::text,
    'second logical export can be claimed independently'
);

reset role;

update public.export_operations
set
    lease_token = '84000000-0000-0000-0000-000000000001',
    lease_expires_at = clock_timestamp() - interval '1 second'
where payload_fingerprint = repeat('b', 64);

set local role authenticated;
set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000001';

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_match',
            '82000000-0000-0000-0000-000000000001',
            '83000000-0000-0000-0000-000000000001',
            repeat('b', 64)
        )
    ),
    'claimed'::text,
    'expired pre-write lease can be safely reclaimed'
);

select ok(
    (
        select attempt_count = 2
           and lease_token <> '84000000-0000-0000-0000-000000000001'::uuid
        from public.export_operations
        where payload_fingerprint = repeat('b', 64)
    ),
    'pre-write reclaim increments attempts and issues a new lease token'
);

select throws_ok(
    $$
        select public.mark_export_operation_write_started(
            (
                select id
                from public.export_operations
                where payload_fingerprint = repeat('b', 64)
            ),
            '84000000-0000-0000-0000-000000000001'::uuid
        )
    $$,
    'P0001',
    null,
    'stale lease token cannot mark a reclaimed operation write_started'
);

select is(
    public.mark_export_operation_write_started(
        (
            select id
            from public.export_operations
            where payload_fingerprint = repeat('b', 64)
        ),
        (
            select lease_token
            from public.export_operations
            where payload_fingerprint = repeat('b', 64)
        )
    ),
    'write_started'::text,
    'new lease token controls the reclaimed operation'
);

select throws_ok(
    $$
        select public.mark_export_operation_retryable_failure(
            (
                select id
                from public.export_operations
                where payload_fingerprint = repeat('b', 64)
            ),
            (
                select lease_token
                from public.export_operations
                where payload_fingerprint = repeat('b', 64)
            ),
            'GOOGLE_MATCH_EXPORT_FAILURE'
        )
    $$,
    '22023',
    null,
    'ambiguous write-started Google failure cannot be classified retryable'
);

select is(
    public.mark_export_operation_outcome_uncertain(
        (
            select id
            from public.export_operations
            where payload_fingerprint = repeat('b', 64)
        ),
        (
            select lease_token
            from public.export_operations
            where payload_fingerprint = repeat('b', 64)
        ),
        'GOOGLE_MATCH_EXPORT_FAILURE'
    ),
    'outcome_uncertain'::text,
    'ambiguous write-started failure becomes outcome_uncertain'
);

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_match',
            '82000000-0000-0000-0000-000000000001',
            '83000000-0000-0000-0000-000000000001',
            repeat('b', 64)
        )
    ),
    'outcome_uncertain'::text,
    'uncertain operation cannot be reclaimed'
);

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_match',
            '82000000-0000-0000-0000-000000000001',
            '83000000-0000-0000-0000-000000000001',
            repeat('c', 64)
        )
    ),
    'claimed'::text,
    'third logical export is claimable'
);

select is(
    public.mark_export_operation_write_started(
        (
            select id
            from public.export_operations
            where payload_fingerprint = repeat('c', 64)
        ),
        (
            select lease_token
            from public.export_operations
            where payload_fingerprint = repeat('c', 64)
        )
    ),
    'write_started'::text,
    'third operation reaches write_started'
);

reset role;

update public.export_operations
set lease_expires_at = clock_timestamp() - interval '1 second'
where payload_fingerprint = repeat('c', 64);

set local role authenticated;
set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000001';

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_match',
            '82000000-0000-0000-0000-000000000001',
            '83000000-0000-0000-0000-000000000001',
            repeat('c', 64)
        )
    ),
    'outcome_uncertain'::text,
    'expired write_started operation is classified uncertain instead of reclaimed'
);

select ok(
    (
        select state = 'outcome_uncertain'
           and lease_token is null
           and lease_expires_at is null
        from public.export_operations
        where payload_fingerprint = repeat('c', 64)
    ),
    'expired write_started classification clears the active lease'
);

select is(
    (
        select outcome
        from public.claim_export_operation(
            'export_standings',
            '82000000-0000-0000-0000-000000000001',
            null,
            repeat('d', 64)
        )
    ),
    'claimed'::text,
    'standings export uses the same persistent claim boundary'
);

select throws_ok(
    $$
        select public.complete_export_operation_success(
            (
                select id
                from public.export_operations
                where payload_fingerprint = repeat('d', 64)
            ),
            (
                select lease_token
                from public.export_operations
                where payload_fingerprint = repeat('d', 64)
            ),
            12,
            3
        )
    $$,
    'P0001',
    null,
    'success cannot be recorded before write_started'
);

select throws_ok(
    $$
        select public.mark_export_operation_outcome_uncertain(
            (
                select id
                from public.export_operations
                where payload_fingerprint = repeat('d', 64)
            ),
            (
                select lease_token
                from public.export_operations
                where payload_fingerprint = repeat('d', 64)
            ),
            'UPSTREAM_TIMEOUT'
        )
    $$,
    'P0001',
    null,
    'uncertain outcome cannot be recorded before write_started'
);

select is(
    public.mark_export_operation_write_started(
        (
            select id
            from public.export_operations
            where payload_fingerprint = repeat('d', 64)
        ),
        (
            select lease_token
            from public.export_operations
            where payload_fingerprint = repeat('d', 64)
        )
    ),
    'write_started'::text,
    'standings operation records write_started before completion'
);

select is(
    public.complete_export_operation_success(
        (
            select id
            from public.export_operations
            where payload_fingerprint = repeat('d', 64)
        ),
        (
            select lease_token
            from public.export_operations
            where payload_fingerprint = repeat('d', 64)
        ),
        12,
        3
    ),
    'succeeded'::text,
    'standings success stores official finalized-match count'
);

select ok(
    (
        select rows_written = 12
           and exported_match_count = 3
        from public.export_operations
        where payload_fingerprint = repeat('d', 64)
    ),
    'standings success persists exactly the approved replay metadata'
);

select throws_ok(
    $$
        select *
        from public.claim_export_operation(
            'export_match',
            '82000000-0000-0000-0000-000000000001',
            null,
            repeat('e', 64)
        )
    $$,
    '22023',
    null,
    'match claim requires a match ID'
);

select throws_ok(
    $$
        select *
        from public.claim_export_operation(
            'export_standings',
            '82000000-0000-0000-0000-000000000001',
            '83000000-0000-0000-0000-000000000001',
            repeat('e', 64)
        )
    $$,
    '22023',
    null,
    'standings claim forbids a match ID'
);

select throws_ok(
    $$
        select *
        from public.claim_export_operation(
            'export_match',
            '82000000-0000-0000-0000-000000000001',
            '83000000-0000-0000-0000-000000000001',
            'INVALID'
        )
    $$,
    '22023',
    null,
    'claim rejects malformed payload fingerprints'
);

select throws_ok(
    $$
        select *
        from public.claim_export_operation(
            'export_match',
            '82000000-0000-0000-0000-000000000001',
            '83000000-0000-0000-0000-000000000002',
            repeat('e', 64)
        )
    $$,
    '42501',
    null,
    'claim rejects a match outside the owned tournament'
);

set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000002';

select is(
    (
        select count(*)
        from public.export_operations
    ),
    0::bigint,
    'another authenticated owner cannot read operation rows'
);

select throws_ok(
    $$
        select *
        from public.claim_export_operation(
            'export_standings',
            '82000000-0000-0000-0000-000000000001',
            null,
            repeat('f', 64)
        )
    $$,
    '42501',
    null,
    'another authenticated owner cannot claim an owned tournament'
);

select throws_ok(
    $$
        select public.mark_export_operation_write_started(
            (
                select id
                from public.export_operations
                where false
            ),
            '85000000-0000-0000-0000-000000000001'::uuid
        )
    $$,
    'P0001',
    null,
    'another owner cannot transition a hidden operation'
);

set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000001';

select throws_ok(
    $$
        insert into public.export_operations (
            owner_id,
            operation_type,
            tournament_id,
            match_id,
            payload_fingerprint,
            state,
            lease_token,
            lease_expires_at
        )
        values (
            '81000000-0000-0000-0000-000000000001',
            'export_match',
            '82000000-0000-0000-0000-000000000001',
            '83000000-0000-0000-0000-000000000001',
            repeat('9', 64),
            'in_progress',
            gen_random_uuid(),
            clock_timestamp() + interval '90 seconds'
        )
    $$,
    '42501',
    null,
    'authenticated clients cannot directly insert operation rows'
);

reset role;

select throws_ok(
    $$
        insert into public.export_operations (
            owner_id,
            operation_type,
            tournament_id,
            match_id,
            payload_fingerprint,
            state
        )
        values (
            '81000000-0000-0000-0000-000000000001',
            'export_standings',
            '82000000-0000-0000-0000-000000000001',
            null,
            'INVALID',
            'retryable_failure'
        )
    $$,
    '23514',
    null,
    'table constraint rejects malformed fingerprints'
);

select throws_ok(
    $$
        insert into public.export_operations (
            owner_id,
            operation_type,
            tournament_id,
            match_id,
            payload_fingerprint,
            state
        )
        values (
            '81000000-0000-0000-0000-000000000001',
            'export_match',
            '82000000-0000-0000-0000-000000000001',
            null,
            repeat('8', 64),
            'retryable_failure'
        )
    $$,
    '23514',
    null,
    'table constraint enforces match target shape'
);

select throws_ok(
    $$
        insert into public.export_operations (
            owner_id,
            operation_type,
            tournament_id,
            match_id,
            payload_fingerprint,
            state,
            rows_written
        )
        values (
            '81000000-0000-0000-0000-000000000001',
            'export_match',
            '82000000-0000-0000-0000-000000000001',
            '83000000-0000-0000-0000-000000000001',
            repeat('7', 64),
            'succeeded',
            null
        )
    $$,
    '23514',
    null,
    'table constraint enforces succeeded row-count metadata'
);

select throws_ok(
    $$
        insert into public.export_operations (
            owner_id,
            operation_type,
            tournament_id,
            match_id,
            payload_fingerprint,
            state,
            rows_written
        )
        values (
            '81000000-0000-0000-0000-000000000001',
            'export_standings',
            '82000000-0000-0000-0000-000000000001',
            null,
            repeat('0', 64),
            'succeeded',
            12
        )
    $$,
    '23514',
    null,
    'table constraint requires succeeded standings match-count metadata'
);
select throws_ok(
    $$
        insert into public.export_operations (
            owner_id,
            operation_type,
            tournament_id,
            match_id,
            payload_fingerprint,
            state
        )
        values (
            '81000000-0000-0000-0000-000000000001',
            'export_match',
            '82000000-0000-0000-0000-000000000001',
            '83000000-0000-0000-0000-000000000001',
            repeat('6', 64),
            'in_progress'
        )
    $$,
    '23514',
    null,
    'table constraint requires active states to hold a lease'
);

set local role anon;
set local request.jwt.claim.sub = '';

select throws_ok(
    $$
        select *
        from public.claim_export_operation(
            'export_standings',
            '82000000-0000-0000-0000-000000000002',
            null,
            repeat('5', 64)
        )
    $$,
    '42501',
    null,
    'anon cannot execute the claim RPC'
);

reset role;

select * from finish();
rollback;
