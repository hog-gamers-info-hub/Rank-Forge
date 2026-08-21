begin;

select plan(21);

select is((
    select public
    from storage.buckets
    where id = 'match-screenshots'
), false, 'match screenshot bucket is private');
select is((
    select array_agg(value order by value)::text
    from unnest((
        select allowed_mime_types
        from storage.buckets
        where id = 'match-screenshots'
    )) as mime_type(value)
), '{image/jpeg,image/png,image/webp}'::text, 'bucket accepts only approved image MIME types');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname like 'match_screenshots_%_owner'
), 4::bigint, 'storage has insert, select, update, and delete owner policies');
select ok((
    select with_check ~ 'auth\.uid'
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname = 'match_screenshots_insert_owner'
), 'insert policy checks authenticated ownership');
select ok((
    select qual ~ 'auth\.uid'
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname = 'match_screenshots_select_owner'
), 'select policy checks authenticated ownership');
select ok((
    select qual ~ 'auth\.uid' and with_check ~ 'auth\.uid'
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname = 'match_screenshots_update_owner'
), 'update policy has ownership USING and WITH CHECK');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname like 'match_screenshots_%'
        and lower(cmd) = 'delete'
), 1::bigint, 'deletion backend adds one owner delete policy');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname like 'match_screenshots_%'
        and (coalesce(qual, '') || coalesce(with_check, '')) ~ 'match-screenshots'
), 4::bigint, 'all policies are limited to the approved bucket');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname like 'match_screenshots_%'
        and (coalesce(qual, '') || coalesce(with_check, '')) ~ 'tournaments'
), 4::bigint, 'all policies require tournament-scoped paths');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname like 'match_screenshots_%'
        and (coalesce(qual, '') || coalesce(with_check, '')) ~ 'matches'
), 4::bigint, 'all policies require match-scoped paths');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname like 'match_screenshots_%'
        and roles = array['authenticated'::name]
), 4::bigint, 'all screenshot policies target authenticated users');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname like 'match_screenshots_%'
        and 'anon'::name = any(roles)
), 0::bigint, 'anonymous users have no screenshot policy');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname like 'match_screenshots_%'
        and (coalesce(qual, '') || coalesce(with_check, '')) ~* 'true'
), 0::bigint, 'screenshot policies are not unconditional');
select ok((
    select (coalesce(qual, '') || coalesce(with_check, '')) ~ 'foldername'
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname = 'match_screenshots_insert_owner'
), 'insert policy validates deterministic path segments');

insert into auth.users (id, email)
values
    ('91000000-0000-0000-0000-000000000001', 'storage-owner@example.test'),
    ('91000000-0000-0000-0000-000000000002', 'storage-other@example.test');
insert into public.tournaments (id, owner_id, name)
values
    ('92000000-0000-0000-0000-000000000001', '91000000-0000-0000-0000-000000000001', 'Storage Cup'),
    ('92000000-0000-0000-0000-000000000002', '91000000-0000-0000-0000-000000000002', 'Other Storage Cup');
insert into public.matches (id, tournament_id, match_number)
values
    ('93000000-0000-0000-0000-000000000001', '92000000-0000-0000-0000-000000000001', 1),
    ('93000000-0000-0000-0000-000000000002', '92000000-0000-0000-0000-000000000002', 1);

set local role authenticated;
set local request.jwt.claim.sub = '91000000-0000-0000-0000-000000000001';
insert into storage.objects (id, bucket_id, name, owner_id, metadata)
values (
    '94000000-0000-0000-0000-000000000001',
    'match-screenshots',
    'users/91000000-0000-0000-0000-000000000001/tournaments/92000000-0000-0000-0000-000000000001/matches/93000000-0000-0000-0000-000000000001/original.png',
    '91000000-0000-0000-0000-000000000001',
    '{}'::jsonb
);
select is((select count(*) from storage.objects where id = '94000000-0000-0000-0000-000000000001'), 1::bigint, 'owner can insert a screenshot object');
select is((select count(*) from storage.objects where id = '94000000-0000-0000-0000-000000000001'), 1::bigint, 'owner can read a screenshot object');
update storage.objects
set metadata = '{"owner": true}'::jsonb
where id = '94000000-0000-0000-0000-000000000001';
select is((select metadata from storage.objects where id = '94000000-0000-0000-0000-000000000001'), '{"owner": true}'::jsonb, 'owner can update a screenshot object');

set local request.jwt.claim.sub = '91000000-0000-0000-0000-000000000002';
select is((select count(*) from storage.objects where id = '94000000-0000-0000-0000-000000000001'), 0::bigint, 'another account cannot read a screenshot object');
update storage.objects
set metadata = '{"attacker": true}'::jsonb
where id = '94000000-0000-0000-0000-000000000001';
set local role authenticated;
set local request.jwt.claim.sub = '91000000-0000-0000-0000-000000000001';
select is((select metadata from storage.objects where id = '94000000-0000-0000-0000-000000000001'), '{"owner": true}'::jsonb, 'cross-account screenshot update is denied');

set local role anon;
set local request.jwt.claim.sub = '';
select is((select count(*) from storage.objects where id = '94000000-0000-0000-0000-000000000001'), 0::bigint, 'anonymous callers cannot read a screenshot object');
select throws_ok($$
    insert into storage.objects (id, bucket_id, name, owner_id, metadata)
    values (
        '94000000-0000-0000-0000-000000000002',
        'match-screenshots',
        'users/91000000-0000-0000-0000-000000000001/tournaments/92000000-0000-0000-0000-000000000001/matches/93000000-0000-0000-0000-000000000001/original.jpg',
        '91000000-0000-0000-0000-000000000001',
        '{}'::jsonb
    )
$$, '42501', null, 'anonymous callers cannot insert a screenshot object');

select * from finish();
rollback;
