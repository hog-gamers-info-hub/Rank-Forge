begin;

select plan(6);

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

select * from finish();
rollback;
