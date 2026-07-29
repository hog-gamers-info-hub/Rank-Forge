begin;

select plan(20);

select has_table('public', 'match_screenshot_metadata', 'metadata table exists');
select col_is_pk('public', 'match_screenshot_metadata', 'match_id', 'match_id is the primary key');
select fk_ok('public', 'match_screenshot_metadata', 'match_id', 'public', 'matches', 'id', 'match FK exists');
select fk_ok('public', 'match_screenshot_metadata', 'tournament_id', 'public', 'tournaments', 'id', 'tournament FK exists');

select has_column('public', 'match_screenshot_metadata', 'owner_id', 'owner column exists');
select has_column('public', 'match_screenshot_metadata', 'local_file_extension', 'extension column exists');
select has_column('public', 'match_screenshot_metadata', 'mime_type', 'MIME column exists');
select has_column('public', 'match_screenshot_metadata', 'sha256', 'sha256 column exists');
select has_column('public', 'match_screenshot_metadata', 'storage_object_path', 'storage path column exists');

select is((
    select count(*)
    from pg_indexes
    where schemaname = 'public'
        and tablename = 'match_screenshot_metadata'
        and indexname in (
            'match_screenshot_metadata_owner_id_idx',
            'match_screenshot_metadata_tournament_id_idx',
            'match_screenshot_metadata_sha256_idx',
            'match_screenshot_metadata_upload_status_idx',
            'match_screenshot_metadata_updated_at_idx'
        )
), 5::bigint, 'required indexes exist');

select is((
    select relrowsecurity
    from pg_class
    where oid = 'public.match_screenshot_metadata'::regclass
), true, 'RLS is enabled');

select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and tablename = 'match_screenshot_metadata'
        and roles = array['authenticated'::name]
), 4::bigint, 'all policies target authenticated users');

select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and tablename = 'match_screenshot_metadata'
        and 'anon'::name = any(roles)
), 0::bigint, 'anonymous users have no policy');

select ok((
    select with_check ~ 'auth\.uid'
    from pg_policies
    where schemaname = 'public'
        and tablename = 'match_screenshot_metadata'
        and policyname = 'match_screenshot_metadata_insert_owner'
), 'insert validates owner');

select ok((
    select qual ~ 'auth\.uid' and with_check ~ 'auth\.uid'
    from pg_policies
    where schemaname = 'public'
        and tablename = 'match_screenshot_metadata'
        and policyname = 'match_screenshot_metadata_update_owner'
), 'update has owner USING and WITH CHECK');

select throws_ok(
    $$ insert into public.match_screenshot_metadata (
        match_id, owner_id, tournament_id, local_file_extension, mime_type, width, height,
        byte_size, sha256, local_status, upload_status, preserved_at
    ) values (
        gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), 'png', 'image/png', 1, 1,
        1, 'ABC', 'PRESERVED', 'PENDING', now()
    ) $$,
    null,
    null,
    'invalid SHA-256 is rejected'
);

select throws_ok(
    $$ insert into public.match_screenshot_metadata (
        match_id, owner_id, tournament_id, local_file_extension, mime_type, width, height,
        byte_size, sha256, local_status, upload_status, preserved_at
    ) values (
        gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), 'png', 'image/png', 0, 1,
        1, repeat('a', 64), 'PRESERVED', 'PENDING', now()
    ) $$,
    null,
    null,
    'invalid dimensions are rejected'
);

select throws_ok(
    $$ insert into public.match_screenshot_metadata (
        match_id, owner_id, tournament_id, local_file_extension, mime_type, width, height,
        byte_size, sha256, local_status, upload_status, preserved_at
    ) values (
        gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), 'png', 'image/png', 1, 1,
        0, repeat('a', 64), 'PRESERVED', 'PENDING', now()
    ) $$,
    null,
    null,
    'invalid byte size is rejected'
);

select throws_ok(
    $$ insert into public.match_screenshot_metadata (
        match_id, owner_id, tournament_id, local_file_extension, mime_type, width, height,
        byte_size, sha256, local_status, upload_status, preserved_at
    ) values (
        gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), 'png', 'image/png', 1, 1,
        1, repeat('a', 64), 'PROCESSING', 'PENDING', now()
    ) $$,
    null,
    null,
    'invalid local status is rejected'
);

select throws_ok(
    $$ insert into public.match_screenshot_metadata (
        match_id, owner_id, tournament_id, local_file_extension, mime_type, width, height,
        byte_size, sha256, local_status, upload_status, preserved_at
    ) values (
        gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), 'png', 'image/png', 1, 1,
        1, repeat('a', 64), 'PRESERVED', 'UPLOADED', now()
    ) $$,
    null,
    null,
    'uploaded state requires storage fields'
);

select * from finish();
rollback;
