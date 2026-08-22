begin;

select plan(15);

select is(
    (
        select count(*)
        from pg_trigger
        where tgrelid = 'public.tournaments'::regclass
            and tgname = 'tournaments_owner_limit_before_insert'
            and tgenabled <> 'D'
    ),
    1::bigint,
    'tournament owner limit trigger exists and is enabled'
);
select ok(
    position('pg_advisory_xact_lock' in pg_get_functiondef('public.enforce_tournament_owner_limit()'::regprocedure)) > 0,
    'tournament owner limit uses a transaction-scoped owner lock'
);
select ok(
    not (select prosecdef from pg_proc where oid = 'public.enforce_tournament_owner_limit()'::regprocedure),
    'tournament owner limit keeps invoker security semantics'
);

insert into auth.users (id, email)
values
    ('b1000000-0000-0000-0000-000000000001', 'limit-owner-a@example.test'),
    ('b1000000-0000-0000-0000-000000000002', 'limit-owner-b@example.test'),
    ('b1000000-0000-0000-0000-000000000003', 'limit-grandfathered@example.test');

-- This fixture represents rows that existed before the migration was applied.
-- It is only setup for the grandfathering assertion; the live trigger remains enabled.
set local session_replication_role = replica;
insert into public.tournaments (id, owner_id, name)
values
    ('b2000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000003', 'Grandfathered 1'),
    ('b2000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000003', 'Grandfathered 2'),
    ('b2000000-0000-0000-0000-000000000003', 'b1000000-0000-0000-0000-000000000003', 'Grandfathered 3'),
    ('b2000000-0000-0000-0000-000000000004', 'b1000000-0000-0000-0000-000000000003', 'Grandfathered 4'),
    ('b2000000-0000-0000-0000-000000000005', 'b1000000-0000-0000-0000-000000000003', 'Grandfathered 5'),
    ('b2000000-0000-0000-0000-000000000006', 'b1000000-0000-0000-0000-000000000003', 'Grandfathered 6');
set local session_replication_role = origin;

set local role authenticated;
set local request.jwt.claim.sub = 'b1000000-0000-0000-0000-000000000001';

select lives_ok($$
    insert into public.tournaments (id, owner_id, name)
    values
        ('b3000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000001', 'Owner A 1'),
        ('b3000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000001', 'Owner A 2'),
        ('b3000000-0000-0000-0000-000000000003', 'b1000000-0000-0000-0000-000000000001', 'Owner A 3'),
        ('b3000000-0000-0000-0000-000000000004', 'b1000000-0000-0000-0000-000000000001', 'Owner A 4'),
        ('b3000000-0000-0000-0000-000000000005', 'b1000000-0000-0000-0000-000000000001', 'Owner A 5')
$$, 'owner A can insert five tournaments');
select is(
    (select count(*) from public.tournaments where owner_id = 'b1000000-0000-0000-0000-000000000001'),
    5::bigint,
    'owner A reaches five tournaments'
);
select throws_ok($$
    insert into public.tournaments (id, owner_id, name)
    values ('b3000000-0000-0000-0000-000000000006', 'b1000000-0000-0000-0000-000000000001', 'Owner A 6')
$$, 'P0001', 'TOURNAMENT_LIMIT_REACHED', 'owner A sixth insert is rejected with stable limit error');
select lives_ok($$
    update public.tournaments
    set name = 'Owner A 5 updated'
    where id = 'b3000000-0000-0000-0000-000000000005'
$$, 'owner A can update an existing tournament at the limit');

set local request.jwt.claim.sub = 'b1000000-0000-0000-0000-000000000002';
select lives_ok($$
    insert into public.tournaments (id, owner_id, name)
    values
        ('b4000000-0000-0000-0000-000000000001', 'b1000000-0000-0000-0000-000000000002', 'Owner B 1'),
        ('b4000000-0000-0000-0000-000000000002', 'b1000000-0000-0000-0000-000000000002', 'Owner B 2'),
        ('b4000000-0000-0000-0000-000000000003', 'b1000000-0000-0000-0000-000000000002', 'Owner B 3'),
        ('b4000000-0000-0000-0000-000000000004', 'b1000000-0000-0000-0000-000000000002', 'Owner B 4'),
        ('b4000000-0000-0000-0000-000000000005', 'b1000000-0000-0000-0000-000000000002', 'Owner B 5')
$$, 'owner B independently can insert five tournaments');
select throws_ok($$
    insert into public.tournaments (id, owner_id, name)
    values ('b4000000-0000-0000-0000-000000000006', 'b1000000-0000-0000-0000-000000000002', 'Owner B 6')
$$, 'P0001', 'TOURNAMENT_LIMIT_REACHED', 'owner B sixth insert is rejected independently');

set local request.jwt.claim.sub = 'b1000000-0000-0000-0000-000000000001';
delete from public.tournaments
where id = 'b3000000-0000-0000-0000-000000000005';
select lives_ok($$
    insert into public.tournaments (id, owner_id, name)
    values ('b3000000-0000-0000-0000-000000000007', 'b1000000-0000-0000-0000-000000000001', 'Owner A replacement')
$$, 'deleting a tournament frees one creation slot');
select is(
    (select count(*) from public.tournaments where owner_id = 'b1000000-0000-0000-0000-000000000001'),
    5::bigint,
    'owner A remains at the maximum after replacement'
);

set local request.jwt.claim.sub = 'b1000000-0000-0000-0000-000000000003';
select is(
    (select count(*) from public.tournaments where owner_id = 'b1000000-0000-0000-0000-000000000003'),
    6::bigint,
    'grandfathered account retains all existing tournaments'
);
select lives_ok($$
    update public.tournaments
    set name = 'Grandfathered 1 updated'
    where id = 'b2000000-0000-0000-0000-000000000001'
$$, 'grandfathered account can update existing tournaments above the limit');
delete from public.tournaments
where id = 'b2000000-0000-0000-0000-000000000006';
select is(
    (select count(*) from public.tournaments where owner_id = 'b1000000-0000-0000-0000-000000000003'),
    5::bigint,
    'grandfathered account can delete and reduce its count'
);

set local role postgres;
select throws_ok($$
    insert into public.tournaments (id, owner_id, name)
    values ('b3000000-0000-0000-0000-000000000008', 'b1000000-0000-0000-0000-000000000001', 'Direct bypass attempt')
$$, 'P0001', 'TOURNAMENT_LIMIT_REACHED', 'direct inserts cannot bypass the trigger');

select * from finish();
rollback;
