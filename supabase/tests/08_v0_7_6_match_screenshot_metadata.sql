begin;

select plan(31);

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

insert into auth.users (id, email)
values
    ('95000000-0000-0000-0000-000000000001', 'metadata-owner@example.test'),
    ('95000000-0000-0000-0000-000000000002', 'metadata-other@example.test');
insert into public.tournaments (id, owner_id, name)
values
    ('96000000-0000-0000-0000-000000000001', '95000000-0000-0000-0000-000000000001', 'Metadata Cup'),
    ('96000000-0000-0000-0000-000000000002', '95000000-0000-0000-0000-000000000002', 'Other Metadata Cup');
insert into public.matches (id, tournament_id, match_number)
values
    ('97000000-0000-0000-0000-000000000001', '96000000-0000-0000-0000-000000000001', 1),
    ('97000000-0000-0000-0000-000000000002', '96000000-0000-0000-0000-000000000001', 2),
    ('97000000-0000-0000-0000-000000000003', '96000000-0000-0000-0000-000000000001', 3),
    ('97000000-0000-0000-0000-000000000004', '96000000-0000-0000-0000-000000000002', 1);
insert into public.match_screenshot_metadata (
    match_id, owner_id, tournament_id, local_file_extension, mime_type, width, height,
    byte_size, sha256, local_status, upload_status, preserved_at
)
values (
    '97000000-0000-0000-0000-000000000001',
    '95000000-0000-0000-0000-000000000001',
    '96000000-0000-0000-0000-000000000001',
    'png', 'image/png', 1, 1, 1, repeat('a', 64), 'PRESERVED', 'PENDING', now()
), (
    '97000000-0000-0000-0000-000000000003',
    '95000000-0000-0000-0000-000000000001',
    '96000000-0000-0000-0000-000000000001',
    'png', 'image/png', 1, 1, 1, repeat('c', 64), 'PRESERVED', 'PENDING', now()
);

set local role authenticated;
set local request.jwt.claim.sub = '95000000-0000-0000-0000-000000000001';
select is((select count(*) from public.match_screenshot_metadata where match_id = '97000000-0000-0000-0000-000000000001'), 1::bigint, 'owner can read screenshot metadata');
update public.match_screenshot_metadata
set width = 2
where match_id = '97000000-0000-0000-0000-000000000001';
select is((select width from public.match_screenshot_metadata where match_id = '97000000-0000-0000-0000-000000000001'), 2, 'owner can update screenshot metadata');
insert into public.match_screenshot_metadata (
    match_id, owner_id, tournament_id, local_file_extension, mime_type, width, height,
    byte_size, sha256, local_status, upload_status, preserved_at
)
values (
    '97000000-0000-0000-0000-000000000002',
    '95000000-0000-0000-0000-000000000001',
    '96000000-0000-0000-0000-000000000001',
    'jpg', 'image/jpeg', 1, 1, 1, repeat('b', 64), 'PRESERVED', 'PENDING', now()
);
select is((select count(*) from public.match_screenshot_metadata where match_id = '97000000-0000-0000-0000-000000000002'), 1::bigint, 'owner can insert screenshot metadata');
delete from public.match_screenshot_metadata where match_id = '97000000-0000-0000-0000-000000000002';
select is((select count(*) from public.match_screenshot_metadata where match_id = '97000000-0000-0000-0000-000000000002'), 0::bigint, 'owner can delete screenshot metadata');

set local request.jwt.claim.sub = '95000000-0000-0000-0000-000000000002';
select is((select count(*) from public.match_screenshot_metadata where match_id = '97000000-0000-0000-0000-000000000001'), 0::bigint, 'another account cannot read screenshot metadata');
update public.match_screenshot_metadata
set width = 99
where match_id = '97000000-0000-0000-0000-000000000001';
set local role authenticated;
set local request.jwt.claim.sub = '95000000-0000-0000-0000-000000000001';
select is((select width from public.match_screenshot_metadata where match_id = '97000000-0000-0000-0000-000000000001'), 2, 'cross-account screenshot metadata update is denied');

set local role anon;
set local request.jwt.claim.sub = '';
select throws_ok($$
    select count(*)
    from public.match_screenshot_metadata
    where match_id = '97000000-0000-0000-0000-000000000001'
$$, '42501', null, 'anonymous callers cannot read screenshot metadata');
select throws_ok($$
    insert into public.match_screenshot_metadata (
        match_id, owner_id, tournament_id, local_file_extension, mime_type, width, height,
        byte_size, sha256, local_status, upload_status, preserved_at
    ) values (
        '97000000-0000-0000-0000-000000000004',
        '95000000-0000-0000-0000-000000000002',
        '96000000-0000-0000-0000-000000000002',
        'png', 'image/png', 1, 1, 1, repeat('d', 64), 'PRESERVED', 'PENDING', now()
    )
$$, '42501', null, 'anonymous callers cannot insert screenshot metadata');
select throws_ok($$
    update public.match_screenshot_metadata
    set width = 3
    where match_id = '97000000-0000-0000-0000-000000000001'
$$, '42501', null, 'anonymous callers cannot update screenshot metadata');
select throws_ok($$
    delete from public.match_screenshot_metadata
    where match_id = '97000000-0000-0000-0000-000000000001'
$$, '42501', null, 'anonymous callers cannot delete screenshot metadata');

reset role;
delete from public.matches where id = '97000000-0000-0000-0000-000000000003';
select is((select count(*) from public.match_screenshot_metadata where match_id = '97000000-0000-0000-0000-000000000003'), 0::bigint, 'match deletion cascades to screenshot metadata');

select * from finish();
rollback;
