begin;

select plan(6);

select ok(
    has_function_privilege(
        'service_role'::name,
        'public.signup_email_is_registered(text)'::regprocedure,
        'execute'::text
    ),
    'service_role can invoke confirmed-email registration RPC'
);
select ok(
    not has_function_privilege(
        'anon'::name,
        'public.signup_email_is_registered(text)'::regprocedure,
        'execute'::text
    ),
    'anonymous callers cannot invoke confirmed-email registration RPC'
);
select ok(
    not has_function_privilege(
        'authenticated'::name,
        'public.signup_email_is_registered(text)'::regprocedure,
        'execute'::text
    ),
    'authenticated callers cannot invoke confirmed-email registration RPC'
);

insert into auth.users (id, email, email_confirmed_at)
values
    ('96000000-0000-0000-0000-000000000001', 'confirmed@example.test', now()),
    ('96000000-0000-0000-0000-000000000002', 'unconfirmed@example.test', null);

set local role service_role;

select is(
    public.signup_email_is_registered('  CONFIRMED@EXAMPLE.TEST  '),
    true,
    'confirmed email is reported as registered after normalization'
);
select is(
    public.signup_email_is_registered('unconfirmed@example.test'),
    true,
    'unconfirmed email is reported as registered'
);
select is(
    public.signup_email_is_registered('unknown@example.test'),
    false,
    'unknown email is reported as not registered'
);

select * from finish();

rollback;
