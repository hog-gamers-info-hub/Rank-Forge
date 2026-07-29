begin;

select plan(14);

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
), 3::bigint, 'storage has only insert, select, and update owner policies');
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
), 0::bigint, 'cloud delete remains outside v0.7.5');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname like 'match_screenshots_%'
        and (coalesce(qual, '') || coalesce(with_check, '')) ~ 'match-screenshots'
), 3::bigint, 'all policies are limited to the approved bucket');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname like 'match_screenshots_%'
        and (coalesce(qual, '') || coalesce(with_check, '')) ~ 'tournaments'
), 3::bigint, 'all policies require tournament-scoped paths');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname like 'match_screenshots_%'
        and (coalesce(qual, '') || coalesce(with_check, '')) ~ 'matches'
), 3::bigint, 'all policies require match-scoped paths');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname like 'match_screenshots_%'
        and roles = array['authenticated'::name]
), 3::bigint, 'all screenshot policies target authenticated users');
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

select * from finish();
rollback;
