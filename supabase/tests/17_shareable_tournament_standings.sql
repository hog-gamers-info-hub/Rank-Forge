begin;

select plan(26);

select has_table(
    'public',
    'tournament_standings_shares',
    'tournament standings shares table exists'
);
select ok(
    (select relrowsecurity
     from pg_class
     where oid = 'public.tournament_standings_shares'::regclass),
    'tournament standings shares has RLS enabled'
);
select is(
    (
        select count(*)
        from pg_constraint constraint_row
        where constraint_row.conrelid = 'public.tournament_standings_shares'::regclass
            and constraint_row.contype = 'u'
            and constraint_row.conname = 'tournament_standings_shares_share_token_key'
    ),
    1::bigint,
    'share token has a unique constraint'
);
select ok(
    exists (
        select 1
        from pg_constraint constraint_row
        join pg_attribute source_column
            on source_column.attrelid = constraint_row.conrelid
            and source_column.attnum = any(constraint_row.conkey)
        join pg_attribute target_column
            on target_column.attrelid = constraint_row.confrelid
            and target_column.attnum = any(constraint_row.confkey)
        where constraint_row.conrelid = 'public.tournament_standings_shares'::regclass
            and constraint_row.contype = 'f'
            and constraint_row.confrelid = 'public.tournaments'::regclass
            and constraint_row.confdeltype = 'c'
            and source_column.attname = 'tournament_id'
            and target_column.attname = 'id'
    ),
    'tournament_id references tournaments with cascade delete'
);
select is(
    (
        select count(*)
        from pg_policies
        where schemaname = 'public'
            and tablename = 'tournament_standings_shares'
    ),
    3::bigint,
    'shares table has only owner select, insert, and update policies'
);
select is(
    (
        select count(*)
        from pg_policies
        where schemaname = 'public'
            and tablename = 'tournament_standings_shares'
            and 'anon'::name = any(roles)
    ),
    0::bigint,
    'shares table has no anonymous policies'
);
select is(
    (
        select count(*)
        from pg_policies
        where schemaname = 'public'
            and tablename = 'tournament_standings_shares'
            and lower(cmd) = 'delete'
    ),
    0::bigint,
    'shares table has no delete policy'
);
select ok(
    has_table_privilege('authenticated', 'public.tournament_standings_shares', 'select')
        and has_column_privilege('authenticated', 'public.tournament_standings_shares', 'tournament_id', 'insert')
        and has_column_privilege('authenticated', 'public.tournament_standings_shares', 'standings', 'insert')
        and not has_column_privilege('authenticated', 'public.tournament_standings_shares', 'share_token', 'insert')
        and has_column_privilege('authenticated', 'public.tournament_standings_shares', 'standings', 'update')
        and has_column_privilege('authenticated', 'public.tournament_standings_shares', 'updated_at', 'update')
        and not has_column_privilege('authenticated', 'public.tournament_standings_shares', 'share_token', 'update')
        and not has_column_privilege('authenticated', 'public.tournament_standings_shares', 'tournament_id', 'update')
        and not has_table_privilege('authenticated', 'public.tournament_standings_shares', 'delete'),
    'authenticated has only the required column and table privileges'
);
select ok(
    not has_table_privilege('anon', 'public.tournament_standings_shares', 'select')
        and not has_table_privilege('anon', 'public.tournament_standings_shares', 'insert')
        and not has_table_privilege('anon', 'public.tournament_standings_shares', 'update')
        and not has_table_privilege('anon', 'public.tournament_standings_shares', 'delete'),
    'anonymous has no direct table privileges'
);
select ok(
    exists (
        select 1
        from pg_attribute
        where attrelid = 'public.tournament_standings_shares'::regclass
            and attname = 'standings'
            and attnotnull
    ),
    'standings snapshot is not nullable'
);
select ok(
    exists (
        select 1
        from pg_attribute
        where attrelid = 'public.tournament_standings_shares'::regclass
            and attname = 'share_token'
            and attnotnull
    ),
    'share token is not nullable'
);

insert into auth.users (id, email)
values
    ('c1000000-0000-0000-0000-000000000001', 'share-owner-a@example.test'),
    ('c1000000-0000-0000-0000-000000000002', 'share-owner-b@example.test');

insert into public.tournaments (id, owner_id, name)
values
    ('c2000000-0000-0000-0000-000000000001', 'c1000000-0000-0000-0000-000000000001', 'Private Owner A Tournament'),
    ('c2000000-0000-0000-0000-000000000002', 'c1000000-0000-0000-0000-000000000002', 'Private Owner B Tournament'),
    ('c2000000-0000-0000-0000-000000000003', 'c1000000-0000-0000-0000-000000000001', 'Private Owner A Second Tournament');

set local role authenticated;
set local request.jwt.claim.sub = 'c1000000-0000-0000-0000-000000000001';

select lives_ok($$
    insert into public.tournament_standings_shares (
        tournament_id,
        share_token,
        standings
    )
    values (
        'c2000000-0000-0000-0000-000000000001',
        'c3000000-0000-0000-0000-000000000001',
        '[]'::jsonb
    )
$$, 'owner can insert an empty standings snapshot');
select ok(
    (
        select share_token is not null
        from public.tournament_standings_shares
        where tournament_id = 'c2000000-0000-0000-0000-000000000001'
    ),
    'owner receives a database-generated share token when it is omitted'
);
select is(
    (
        select count(*)
        from public.tournament_standings_shares
        where tournament_id = 'c2000000-0000-0000-0000-000000000001'
    ),
    1::bigint,
    'owner can select the owned share record'
);
select lives_ok($$
    update public.tournament_standings_shares
    set standings = '[{"displayOrder":1}]'::jsonb
    where tournament_id = 'c2000000-0000-0000-0000-000000000001'
$$, 'owner can update the owned share record');
select throws_ok($$
    insert into public.tournament_standings_shares (
        tournament_id,
        standings
    )
    values (
        'c2000000-0000-0000-0000-000000000002',
        '[]'::jsonb
    )
$$, '42501', null, 'owner cannot insert a share record for another owner');
select throws_ok($$
    insert into public.tournament_standings_shares (
        tournament_id,
        share_token,
        standings
    )
    values (
        'c2000000-0000-0000-0000-000000000003',
        'c3000000-0000-0000-0000-000000000002',
        '[]'::jsonb
    )
$$, '42501', null, 'owner cannot explicitly insert a share token');
select throws_ok($$
    update public.tournament_standings_shares
    set share_token = 'c3000000-0000-0000-0000-000000000006'
    where tournament_id = 'c2000000-0000-0000-0000-000000000001'
$$, '42501', null, 'owner cannot update the share token');
select throws_ok($$
    update public.tournament_standings_shares
    set tournament_id = 'c2000000-0000-0000-0000-000000000002'
    where tournament_id = 'c2000000-0000-0000-0000-000000000001'
$$, '42501', null, 'owner cannot reparent the share record');

set local request.jwt.claim.sub = 'c1000000-0000-0000-0000-000000000002';
select is(
    (
        select count(*)
        from public.tournament_standings_shares
        where tournament_id = 'c2000000-0000-0000-0000-000000000001'
    ),
    0::bigint,
    'another authenticated user cannot select the share record'
);
with attempted as (
    update public.tournament_standings_shares
    set standings = '[]'::jsonb
    where tournament_id = 'c2000000-0000-0000-0000-000000000001'
    returning 1
)
select is(
    (select count(*) from attempted),
    0::bigint,
    'another authenticated user cannot update the share record'
);

set local role anon;
set local request.jwt.claim.sub = '';
select throws_ok($$
    select count(*)
    from public.tournament_standings_shares
$$, '42501', null, 'anonymous callers cannot read share records');
select is(
    (
        select count(*)
        from public.tournaments
    ),
    0::bigint,
    'anonymous callers cannot read private tournaments'
);
select throws_ok($$
    insert into public.tournament_standings_shares (
        tournament_id,
        standings
    )
    values (
        'c2000000-0000-0000-0000-000000000002',
        '[]'::jsonb
    )
$$, '42501', null, 'anonymous callers cannot write share records');

reset role;

select throws_ok($$
    insert into public.tournament_standings_shares (
        tournament_id,
        share_token,
        standings
    )
    values (
        'c2000000-0000-0000-0000-000000000002',
        'c3000000-0000-0000-0000-000000000004',
        '{}'::jsonb
    )
$$, '23514', null, 'standings must be a JSON array');
select throws_ok($$
    insert into public.tournament_standings_shares (
        tournament_id,
        share_token,
        standings
    )
    values (
        'c2000000-0000-0000-0000-000000000002',
        'c3000000-0000-0000-0000-000000000005',
        jsonb_build_array(
            1, 2, 3, 4, 5, 6, 7,
            8, 9, 10, 11, 12, 13
        )
    )
$$, '23514', null, 'standings cannot contain more than 12 rows');

select * from finish();
rollback;
