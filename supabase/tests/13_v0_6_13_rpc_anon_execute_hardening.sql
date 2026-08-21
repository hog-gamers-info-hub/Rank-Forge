begin;

select plan(16);

select ok(
    not has_function_privilege(
        'anon',
        'public.finalize_match_snapshot(uuid,jsonb,jsonb,integer)',
        'EXECUTE'
    ),
    'anon cannot execute finalize_match_snapshot'
);

select ok(
    not has_function_privilege(
        'anon',
        'public.correct_finalized_match_snapshot(uuid,uuid,jsonb,integer,text)',
        'EXECUTE'
    ),
    'anon cannot execute correct_finalized_match_snapshot'
);

select ok(
    not has_function_privilege(
        'anon',
        'public.claim_export_operation(text,uuid,uuid,text)',
        'EXECUTE'
    ),
    'anon cannot execute claim_export_operation'
);

select ok(
    not has_function_privilege(
        'anon',
        'public.mark_export_operation_write_started(uuid,uuid)',
        'EXECUTE'
    ),
    'anon cannot execute mark_export_operation_write_started'
);

select ok(
    not has_function_privilege(
        'anon',
        'public.complete_export_operation_success(uuid,uuid,integer,integer)',
        'EXECUTE'
    ),
    'anon cannot execute complete_export_operation_success'
);

select ok(
    not has_function_privilege(
        'anon',
        'public.mark_export_operation_retryable_failure(uuid,uuid,text)',
        'EXECUTE'
    ),
    'anon cannot execute mark_export_operation_retryable_failure'
);

select ok(
    not has_function_privilege(
        'anon',
        'public.mark_export_operation_outcome_uncertain(uuid,uuid,text)',
        'EXECUTE'
    ),
    'anon cannot execute mark_export_operation_outcome_uncertain'
);

select ok(
    not has_function_privilege(
        'anon',
        'public.resolve_export_operation_verified_success(uuid,integer)',
        'EXECUTE'
    ),
    'anon cannot execute resolve_export_operation_verified_success'
);

select ok(
    has_function_privilege(
        'authenticated',
        'public.finalize_match_snapshot(uuid,jsonb,jsonb,integer)',
        'EXECUTE'
    ),
    'authenticated can execute finalize_match_snapshot'
);

select ok(
    has_function_privilege(
        'authenticated',
        'public.correct_finalized_match_snapshot(uuid,uuid,jsonb,integer,text)',
        'EXECUTE'
    ),
    'authenticated can execute correct_finalized_match_snapshot'
);

select ok(
    has_function_privilege(
        'authenticated',
        'public.claim_export_operation(text,uuid,uuid,text)',
        'EXECUTE'
    ),
    'authenticated can execute claim_export_operation'
);

select ok(
    has_function_privilege(
        'authenticated',
        'public.mark_export_operation_write_started(uuid,uuid)',
        'EXECUTE'
    ),
    'authenticated can execute mark_export_operation_write_started'
);

select ok(
    has_function_privilege(
        'authenticated',
        'public.complete_export_operation_success(uuid,uuid,integer,integer)',
        'EXECUTE'
    ),
    'authenticated can execute complete_export_operation_success'
);

select ok(
    has_function_privilege(
        'authenticated',
        'public.mark_export_operation_retryable_failure(uuid,uuid,text)',
        'EXECUTE'
    ),
    'authenticated can execute mark_export_operation_retryable_failure'
);

select ok(
    has_function_privilege(
        'authenticated',
        'public.mark_export_operation_outcome_uncertain(uuid,uuid,text)',
        'EXECUTE'
    ),
    'authenticated can execute mark_export_operation_outcome_uncertain'
);

select ok(
    has_function_privilege(
        'authenticated',
        'public.resolve_export_operation_verified_success(uuid,integer)',
        'EXECUTE'
    ),
    'authenticated can execute resolve_export_operation_verified_success'
);

select * from finish();

rollback;
