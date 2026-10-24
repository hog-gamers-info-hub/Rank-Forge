begin;

select plan(29);

select has_table(
    'public',
    'activity_events',
    'activity event table exists'
);
select has_column('public', 'activity_events', 'event_type', 'event type is stored');
select has_column('public', 'activity_events', 'occurred_at', 'server timestamp is stored');
select has_column('public', 'activity_events', 'owner_id', 'authenticated owner is stored');
select ok((select relrowsecurity from pg_class where oid = 'public.activity_events'::regclass),
    'activity events have RLS enabled');
select ok((
    select count(*) = 1
    from pg_constraint
    where conrelid = 'public.activity_events'::regclass
        and conname = 'activity_events_event_type_check'
), 'activity event type is allow-listed');
select ok((
    select count(*) = 1
    from pg_constraint
    where conrelid = 'public.activity_events'::regclass
        and contype = 'f'
        and confrelid = 'auth.users'::regclass
), 'activity owner references auth users');
select ok((
    select count(*) = 1
    from pg_indexes
    where schemaname = 'public'
        and tablename = 'activity_events'
        and indexname = 'activity_events_occurred_at_event_type_idx'
), 'activity events have a reporting index');
select ok(
    has_column_privilege(
        'authenticated',
        'public.activity_events',
        'event_type',
        'INSERT'
    ),
    'authenticated clients can insert events');
select ok((
    select count(*) = 1
    from pg_policies
    where schemaname = 'public'
        and tablename = 'activity_events'
        and policyname = 'activity_events_insert_authenticated'
), 'activity events have one authenticated insert policy');
select ok(not has_table_privilege('authenticated', 'public.activity_events', 'SELECT'),
    'authenticated clients cannot read event rows');
select ok(not has_table_privilege('anon', 'public.activity_events', 'INSERT'),
    'anonymous clients cannot insert events');
select ok(not has_table_privilege('anon', 'public.activity_events', 'SELECT'),
    'anonymous clients cannot read event rows');
select ok((
    select count(*) = 1
    from pg_class
    where oid = 'private.daily_activity_statistics'::regclass
        and relkind = 'v'
), 'private daily activity view exists');
select is((
    select count(*)
    from information_schema.columns
    where table_schema = 'private'
        and table_name = 'daily_activity_statistics'
), 4::bigint, 'daily activity view exposes only date and three counters');
select ok((
    select pg_get_viewdef('private.daily_activity_statistics'::regclass, true)
        like '%Asia/Kolkata%'
), 'daily activity dates use Asia/Kolkata');
select ok(has_table_privilege(
    'service_role',
    'private.daily_activity_statistics',
    'SELECT'
), 'service role can read only the aggregate view');
select ok(not has_table_privilege(
    'authenticated',
    'private.daily_activity_statistics',
    'SELECT'
), 'authenticated clients cannot read the aggregate view');

insert into auth.users (id, email)
values ('a5100000-0000-0000-0000-000000000001', 'activity-a@example.test');

set local role authenticated;
set local request.jwt.claim.sub = 'a5100000-0000-0000-0000-000000000001';
select lives_ok($$
    insert into public.activity_events (event_type)
    values ('result_downloaded')
$$, 'authenticated owner can record an allow-listed event');
select throws_ok($$
    insert into public.activity_events (event_type)
    values ('account_viewed')
$$, '23514', null, 'unapproved event types are rejected');
select throws_ok($$
    insert into public.activity_events (event_type, owner_id)
    values ('custom_design_saved', 'a5100000-0000-0000-0000-000000000099')
$$, '42501', null, 'clients cannot override the server owner identity');
select throws_ok($$
    select count(*) from public.activity_events
$$, '42501', null, 'authenticated clients cannot read activity rows');
reset role;

select is((
    select count(*)
    from public.activity_events
    where owner_id = 'a5100000-0000-0000-0000-000000000001'
        and event_type = 'result_downloaded'
        and occurred_at is not null
), 1::bigint, 'event owner and timestamp are generated server-side');

set local role anon;
select throws_ok($$
    insert into public.activity_events (event_type)
    values ('standings_share_initiated')
$$, '42501', null, 'anonymous clients cannot record events');
reset role;

insert into public.activity_events (event_type, owner_id, occurred_at)
values
    ('result_downloaded', 'a5100000-0000-0000-0000-000000000001', '2026-01-01 19:00:00+00'),
    ('standings_share_initiated', 'a5100000-0000-0000-0000-000000000001', '2026-01-01 19:00:00+00'),
    ('custom_design_saved', 'a5100000-0000-0000-0000-000000000001', '2026-01-01 19:00:00+00'),
    ('custom_design_saved', 'a5100000-0000-0000-0000-000000000001', '2026-01-01 00:30:00+00');

set local role service_role;
select is((
    select result_downloaded_count
    from private.daily_activity_statistics
    where activity_date = '2026-01-02'
), 1::bigint, 'daily view counts result downloads in India time');
select is((
    select standings_share_initiated_count
    from private.daily_activity_statistics
    where activity_date = '2026-01-02'
), 1::bigint, 'daily view counts standings shares in India time');
select is((
    select custom_design_saved_count
    from private.daily_activity_statistics
    where activity_date = '2026-01-02'
), 1::bigint, 'daily view counts custom designs in India time');
select is((
    select custom_design_saved_count
    from private.daily_activity_statistics
    where activity_date = '2026-01-01'
), 1::bigint, 'daily view converts UTC midnight into the prior India date');
reset role;

set role authenticated;

select throws_ok($$
    select count(*) from private.daily_activity_statistics
$$, '42501', null, 'ordinary callers cannot read the admin aggregate view');

reset role;

select * from finish();
rollback;
