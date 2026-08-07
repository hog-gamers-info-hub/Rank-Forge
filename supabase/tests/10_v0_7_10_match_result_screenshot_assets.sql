begin;

select plan(55);

select is((
    select public
    from storage.buckets
    where id = 'ocr-screenshots'
), false, 'ocr screenshot bucket is private');
select is((
    select array_agg(value order by value)::text
    from unnest((
        select allowed_mime_types
        from storage.buckets
        where id = 'ocr-screenshots'
    )) as mime_type(value)
), '{image/jpeg,image/png,image/webp}'::text, 'ocr screenshot bucket accepts only approved MIME types');

select has_table('public', 'match_result_screenshot_assets', 'match-result screenshot asset table exists');
select is((
    select relrowsecurity
    from pg_class
    where oid = 'public.match_result_screenshot_assets'::regclass
), true, 'match-result screenshot asset RLS is enabled');
select ok(exists (
    select 1
    from pg_constraint
    where conrelid = 'public.match_result_screenshot_assets'::regclass
        and contype = 'p'
        and conkey = array[
            (
                select attnum
                from pg_attribute
                where attrelid = 'public.match_result_screenshot_assets'::regclass
                    and attname = 'match_id'
            ),
            (
                select attnum
                from pg_attribute
                where attrelid = 'public.match_result_screenshot_assets'::regclass
                    and attname = 'screenshot_role'
            )
        ]::smallint[]
), 'primary key is match_id plus screenshot_role');
select fk_ok('public', 'match_result_screenshot_assets', 'match_id', 'public', 'matches', 'id', 'match FK exists');
select fk_ok('public', 'match_result_screenshot_assets', 'tournament_id', 'public', 'tournaments', 'id', 'tournament FK exists');
select is((
    select count(*)
    from pg_attribute
    where attrelid = 'public.match_result_screenshot_assets'::regclass
        and attname in (
            'owner_id',
            'tournament_id',
            'match_id',
            'screenshot_kind',
            'screenshot_role',
            'local_file_extension',
            'mime_type',
            'original_width',
            'original_height',
            'byte_size',
            'sha256',
            'storage_bucket',
            'storage_object_path',
            'local_status',
            'upload_status',
            'upload_failure_code',
            'crop_profile_id',
            'crop_left',
            'crop_top',
            'crop_right',
            'crop_bottom',
            'preserved_at',
            'uploaded_at',
            'revision',
            'created_at',
            'updated_at'
        )
), 26::bigint, 'required columns exist');
select is((
    select count(*)
    from pg_indexes
    where schemaname = 'public'
        and tablename = 'match_result_screenshot_assets'
        and indexname in (
            'match_result_screenshot_assets_owner_id_idx',
            'match_result_screenshot_assets_tournament_id_idx',
            'match_result_screenshot_assets_sha256_idx',
            'match_result_screenshot_assets_upload_status_idx',
            'match_result_screenshot_assets_updated_at_idx'
        )
), 5::bigint, 'required indexes exist');
select ok(to_regclass('public.match_result_screenshot_assets') is not null and exists (
    select 1 from pg_constraint
    where conrelid = 'public.match_result_screenshot_assets'::regclass
        and conname = 'match_result_screenshot_assets_role_check'
), 'role constraint exists');
select ok(exists (
    select 1 from pg_constraint
    where conrelid = 'public.match_result_screenshot_assets'::regclass
        and conname = 'match_result_screenshot_assets_kind_check'
), 'kind constraint exists');
select ok(exists (
    select 1 from pg_constraint
    where conrelid = 'public.match_result_screenshot_assets'::regclass
        and conname = 'match_result_screenshot_assets_sha256_check'
), 'sha constraint exists');
select is((
    select count(*)
    from pg_constraint
    where conrelid = 'public.match_result_screenshot_assets'::regclass
        and conname in (
            'match_result_screenshot_assets_local_status_check',
            'match_result_screenshot_assets_upload_status_check'
        )
), 2::bigint, 'status constraints exist');
select is((
    select count(*)
    from pg_constraint
    where conrelid = 'public.match_result_screenshot_assets'::regclass
        and conname in (
            'match_result_screenshot_assets_original_width_positive',
            'match_result_screenshot_assets_original_height_positive',
            'match_result_screenshot_assets_byte_size_positive',
            'match_result_screenshot_assets_revision_positive'
        )
), 4::bigint, 'positive value constraints exist');
select ok(exists (
    select 1 from pg_constraint
    where conrelid = 'public.match_result_screenshot_assets'::regclass
        and conname = 'match_result_screenshot_assets_mime_type_check'
), 'extension and MIME constraint exists');
select ok(exists (
    select 1 from pg_constraint
    where conrelid = 'public.match_result_screenshot_assets'::regclass
        and conname = 'match_result_screenshot_assets_crop_all_or_none_check'
), 'crop all-or-none constraint exists');
select ok(exists (
    select 1 from pg_constraint
    where conrelid = 'public.match_result_screenshot_assets'::regclass
        and conname = 'match_result_screenshot_assets_crop_bounds_check'
), 'crop bounds constraint exists');
select ok(exists (
    select 1 from pg_constraint
    where conrelid = 'public.match_result_screenshot_assets'::regclass
        and conname = 'match_result_screenshot_assets_storage_path_check'
), 'storage path consistency constraint exists');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and tablename = 'match_result_screenshot_assets'
        and roles = array['authenticated'::name]
), 4::bigint, 'metadata policies target authenticated users');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and tablename = 'match_result_screenshot_assets'
        and 'anon'::name = any(roles)
), 0::bigint, 'anonymous users have no metadata policy');
select ok((
    select qual ~ 'tournament_row\.id = match_result_screenshot_assets\.tournament_id'
        and qual ~ 'match_row\.id = match_result_screenshot_assets\.match_id'
    from pg_policies
    where schemaname = 'public'
        and tablename = 'match_result_screenshot_assets'
        and policyname = 'match_result_screenshot_assets_select_owner'
), 'select policy fully correlates tournament and match');
select ok((
    select with_check ~ 'tournament_row\.id = match_result_screenshot_assets\.tournament_id'
        and with_check ~ 'match_row\.id = match_result_screenshot_assets\.match_id'
    from pg_policies
    where schemaname = 'public'
        and tablename = 'match_result_screenshot_assets'
        and policyname = 'match_result_screenshot_assets_insert_owner'
), 'insert policy fully correlates tournament and match');
select ok((
    select qual ~ 'tournament_row\.id = match_result_screenshot_assets\.tournament_id'
        and qual ~ 'match_row\.id = match_result_screenshot_assets\.match_id'
        and with_check ~ 'tournament_row\.id = match_result_screenshot_assets\.tournament_id'
        and with_check ~ 'match_row\.id = match_result_screenshot_assets\.match_id'
    from pg_policies
    where schemaname = 'public'
        and tablename = 'match_result_screenshot_assets'
        and policyname = 'match_result_screenshot_assets_update_owner'
), 'update policy fully correlates tournament and match in USING and WITH CHECK');
select ok((
    select qual ~ 'tournament_row\.id = match_result_screenshot_assets\.tournament_id'
        and qual ~ 'match_row\.id = match_result_screenshot_assets\.match_id'
    from pg_policies
    where schemaname = 'public'
        and tablename = 'match_result_screenshot_assets'
        and policyname = 'match_result_screenshot_assets_delete_owner'
), 'delete policy fully correlates tournament and match');

insert into auth.users (id, email)
values
    ('81000000-0000-0000-0000-000000000001', 'ocr-owner@example.test'),
    ('81000000-0000-0000-0000-000000000002', 'ocr-other@example.test');
insert into public.tournaments (id, owner_id, name)
values
    ('82000000-0000-0000-0000-000000000001', '81000000-0000-0000-0000-000000000001', 'OCR Cup'),
    ('82000000-0000-0000-0000-000000000002', '81000000-0000-0000-0000-000000000002', 'Other OCR Cup');
insert into public.matches (id, tournament_id, match_number)
values
    ('83000000-0000-0000-0000-000000000001', '82000000-0000-0000-0000-000000000001', 1),
    ('83000000-0000-0000-0000-000000000002', '82000000-0000-0000-0000-000000000001', 2),
    ('83000000-0000-0000-0000-000000000003', '82000000-0000-0000-0000-000000000001', 3),
    ('83000000-0000-0000-0000-000000000004', '82000000-0000-0000-0000-000000000001', 4),
    ('83000000-0000-0000-0000-000000000005', '82000000-0000-0000-0000-000000000001', 5),
    ('83000000-0000-0000-0000-000000000006', '82000000-0000-0000-0000-000000000001', 6),
    ('83000000-0000-0000-0000-000000000007', '82000000-0000-0000-0000-000000000001', 7),
    ('83000000-0000-0000-0000-000000000008', '82000000-0000-0000-0000-000000000002', 1);

set local role authenticated;
set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000001';
insert into public.match_result_screenshot_assets (
    match_id, owner_id, tournament_id, screenshot_kind, screenshot_role,
    local_file_extension, mime_type, original_width, original_height, byte_size,
    sha256, storage_bucket, storage_object_path, local_status, upload_status,
    preserved_at, revision
) values (
    '83000000-0000-0000-0000-000000000001',
    '81000000-0000-0000-0000-000000000001',
    '82000000-0000-0000-0000-000000000001',
    'MATCH_RESULT',
    'MATCH_RESULT_UPPER',
    'png',
    'image/png',
    1600,
    720,
    4,
    repeat('a', 64),
    'ocr-screenshots',
    'users/81000000-0000-0000-0000-000000000001/tournaments/82000000-0000-0000-0000-000000000001/matches/83000000-0000-0000-0000-000000000001/result/upper/original.png',
    'PRESERVED',
    'PENDING',
    now(),
    1
);
select is((select count(*) from public.match_result_screenshot_assets where screenshot_role = 'MATCH_RESULT_UPPER'), 1::bigint, 'owner can insert upper metadata');
insert into public.match_result_screenshot_assets (
    match_id, owner_id, tournament_id, screenshot_kind, screenshot_role,
    local_file_extension, mime_type, original_width, original_height, byte_size,
    sha256, storage_bucket, storage_object_path, local_status, upload_status,
    crop_profile_id, crop_left, crop_top, crop_right, crop_bottom, preserved_at, revision
) values (
    '83000000-0000-0000-0000-000000000001',
    '81000000-0000-0000-0000-000000000001',
    '82000000-0000-0000-0000-000000000001',
    'MATCH_RESULT',
    'MATCH_RESULT_LOWER',
    'jpg',
    'image/jpeg',
    1600,
    720,
    4,
    repeat('b', 64),
    'ocr-screenshots',
    'users/81000000-0000-0000-0000-000000000001/tournaments/82000000-0000-0000-0000-000000000001/matches/83000000-0000-0000-0000-000000000001/result/lower/original.jpg',
    'PRESERVED',
    'PENDING',
    'match-result',
    0.1,
    0.1,
    0.9,
    0.9,
    now(),
    1
);
select is((select count(*) from public.match_result_screenshot_assets where match_id = '83000000-0000-0000-0000-000000000001'), 2::bigint, 'owner can insert lower metadata for the same match');
select is((select count(*) from public.match_result_screenshot_assets), 2::bigint, 'owner can select both role rows');
update public.match_result_screenshot_assets
set original_width = 1700
where match_id = '83000000-0000-0000-0000-000000000001'
    and screenshot_role = 'MATCH_RESULT_UPPER';
select is((select original_width from public.match_result_screenshot_assets where screenshot_role = 'MATCH_RESULT_UPPER'), 1700, 'owner can update upper metadata');
select is((select original_width from public.match_result_screenshot_assets where screenshot_role = 'MATCH_RESULT_LOWER'), 1600, 'updating upper does not change lower');
insert into public.match_result_screenshot_assets (
    match_id, owner_id, tournament_id, screenshot_kind, screenshot_role,
    local_file_extension, mime_type, original_width, original_height, byte_size,
    sha256, storage_bucket, storage_object_path, local_status, upload_status,
    preserved_at, revision
) values (
    '83000000-0000-0000-0000-000000000001',
    '81000000-0000-0000-0000-000000000001',
    '82000000-0000-0000-0000-000000000001',
    'MATCH_RESULT',
    'MATCH_RESULT_UPPER',
    'png',
    'image/png',
    1700,
    720,
    5,
    repeat('c', 64),
    'ocr-screenshots',
    'users/81000000-0000-0000-0000-000000000001/tournaments/82000000-0000-0000-0000-000000000001/matches/83000000-0000-0000-0000-000000000001/result/upper/original.png',
    'PRESERVED',
    'PENDING',
    now(),
    2
)
on conflict (match_id, screenshot_role) do update
set sha256 = excluded.sha256,
    revision = excluded.revision;
select is((select sha256 from public.match_result_screenshot_assets where screenshot_role = 'MATCH_RESULT_UPPER'), repeat('c', 64), 'same-role upsert updates only that role');

select throws_ok($$
    insert into public.match_result_screenshot_assets (
        match_id, owner_id, tournament_id, screenshot_kind, screenshot_role,
        local_file_extension, mime_type, original_width, original_height, byte_size,
        sha256, local_status, upload_status, preserved_at
    ) values (
        '83000000-0000-0000-0000-000000000002',
        '81000000-0000-0000-0000-000000000001',
        '82000000-0000-0000-0000-000000000001',
        'MATCH_RESULT', 'BAD_ROLE', 'png', 'image/png', 1, 1, 1,
        repeat('d', 64), 'PRESERVED', 'PENDING', now()
    )
$$, null, null, 'invalid role is rejected');
select throws_ok($$
    insert into public.match_result_screenshot_assets (
        match_id, owner_id, tournament_id, screenshot_kind, screenshot_role,
        local_file_extension, mime_type, original_width, original_height, byte_size,
        sha256, local_status, upload_status, preserved_at
    ) values (
        '83000000-0000-0000-0000-000000000003',
        '81000000-0000-0000-0000-000000000001',
        '82000000-0000-0000-0000-000000000001',
        'ROSTER', 'MATCH_RESULT_UPPER', 'png', 'image/png', 1, 1, 1,
        repeat('e', 64), 'PRESERVED', 'PENDING', now()
    )
$$, null, null, 'invalid screenshot kind is rejected');
select throws_ok($$
    insert into public.match_result_screenshot_assets (
        match_id, owner_id, tournament_id, screenshot_kind, screenshot_role,
        local_file_extension, mime_type, original_width, original_height, byte_size,
        sha256, local_status, upload_status, crop_profile_id, crop_left, preserved_at
    ) values (
        '83000000-0000-0000-0000-000000000004',
        '81000000-0000-0000-0000-000000000001',
        '82000000-0000-0000-0000-000000000001',
        'MATCH_RESULT', 'MATCH_RESULT_UPPER', 'png', 'image/png', 1, 1, 1,
        repeat('f', 64), 'PRESERVED', 'PENDING', 'match-result', 0.1, now()
    )
$$, null, null, 'partial crop metadata is rejected');
select throws_ok($$
    insert into public.match_result_screenshot_assets (
        match_id, owner_id, tournament_id, screenshot_kind, screenshot_role,
        local_file_extension, mime_type, original_width, original_height, byte_size,
        sha256, local_status, upload_status, crop_profile_id, crop_left, crop_top, crop_right, crop_bottom, preserved_at
    ) values (
        '83000000-0000-0000-0000-000000000005',
        '81000000-0000-0000-0000-000000000001',
        '82000000-0000-0000-0000-000000000001',
        'MATCH_RESULT', 'MATCH_RESULT_UPPER', 'png', 'image/png', 1, 1, 1,
        repeat('0', 64), 'PRESERVED', 'PENDING', 'match-result', 0.9, 0.1, 0.1, 0.9, now()
    )
$$, null, null, 'invalid crop bounds are rejected');
select throws_ok($$
    insert into public.match_result_screenshot_assets (
        match_id, owner_id, tournament_id, screenshot_kind, screenshot_role,
        local_file_extension, mime_type, original_width, original_height, byte_size,
        sha256, storage_bucket, storage_object_path, local_status, upload_status, preserved_at
    ) values (
        '83000000-0000-0000-0000-000000000006',
        '81000000-0000-0000-0000-000000000001',
        '82000000-0000-0000-0000-000000000001',
        'MATCH_RESULT', 'MATCH_RESULT_UPPER', 'png', 'image/png', 1, 1, 1,
        repeat('1', 64), 'ocr-screenshots',
        'users/81000000-0000-0000-0000-000000000001/tournaments/82000000-0000-0000-0000-000000000001/matches/83000000-0000-0000-0000-000000000006/result/lower/original.png',
        'PRESERVED', 'PENDING', now()
    )
$$, null, null, 'role and storage path mismatch is rejected');
select throws_ok($$
    insert into public.match_result_screenshot_assets (
        match_id, owner_id, tournament_id, screenshot_kind, screenshot_role,
        local_file_extension, mime_type, original_width, original_height, byte_size,
        sha256, storage_bucket, storage_object_path, local_status, upload_status, uploaded_at, preserved_at
    ) values (
        '83000000-0000-0000-0000-000000000007',
        '81000000-0000-0000-0000-000000000001',
        '82000000-0000-0000-0000-000000000001',
        'MATCH_RESULT', 'MATCH_RESULT_UPPER', 'png', 'image/png', 1, 1, 1,
        repeat('2', 64), 'match-screenshots',
        'users/81000000-0000-0000-0000-000000000001/tournaments/82000000-0000-0000-0000-000000000001/matches/83000000-0000-0000-0000-000000000007/result/upper/original.png',
        'PRESERVED', 'UPLOADED', now(), now()
    )
$$, null, null, 'wrong bucket is rejected');

set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000002';
select is((select count(*) from public.match_result_screenshot_assets), 0::bigint, 'second account cannot select owner metadata');
select throws_ok($$
    insert into public.match_result_screenshot_assets (
        match_id, owner_id, tournament_id, screenshot_kind, screenshot_role,
        local_file_extension, mime_type, original_width, original_height, byte_size,
        sha256, local_status, upload_status, preserved_at
    ) values (
        '83000000-0000-0000-0000-000000000001',
        '81000000-0000-0000-0000-000000000002',
        '82000000-0000-0000-0000-000000000001',
        'MATCH_RESULT', 'MATCH_RESULT_UPPER', 'png', 'image/png', 1, 1, 1,
        repeat('3', 64), 'PRESERVED', 'PENDING', now()
    )
$$, '42501', null, 'second account cannot insert against owner tournament and match');
update public.match_result_screenshot_assets
set original_width = 99
where match_id = '83000000-0000-0000-0000-000000000001'
    and screenshot_role = 'MATCH_RESULT_UPPER';
set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000001';
select is((select original_width from public.match_result_screenshot_assets where screenshot_role = 'MATCH_RESULT_UPPER'), 1700, 'second account cannot update owner metadata');
set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000002';
delete from public.match_result_screenshot_assets
where match_id = '83000000-0000-0000-0000-000000000001'
    and screenshot_role = 'MATCH_RESULT_LOWER';
set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000001';
select is((select count(*) from public.match_result_screenshot_assets where screenshot_role = 'MATCH_RESULT_LOWER'), 1::bigint, 'second account cannot delete owner metadata');

set local role anon;
set local request.jwt.claim.sub = '';
select throws_ok($$
    select count(*) from public.match_result_screenshot_assets
$$, '42501', null, 'anonymous metadata select is rejected');
select throws_ok($$
    insert into public.match_result_screenshot_assets (
        match_id, owner_id, tournament_id, screenshot_kind, screenshot_role,
        local_file_extension, mime_type, original_width, original_height, byte_size,
        sha256, local_status, upload_status, preserved_at
    ) values (
        '83000000-0000-0000-0000-000000000008',
        '81000000-0000-0000-0000-000000000002',
        '82000000-0000-0000-0000-000000000002',
        'MATCH_RESULT', 'MATCH_RESULT_UPPER', 'png', 'image/png', 1, 1, 1,
        repeat('4', 64), 'PRESERVED', 'PENDING', now()
    )
$$, '42501', null, 'anonymous metadata insert is rejected');

select is((
    select count(*)
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname like 'ocr_screenshots_%_owner'
), 3::bigint, 'storage has insert, select, and update owner policies');

set local role authenticated;
set local request.jwt.claim.sub = '81000000-0000-0000-0000-000000000001';
insert into storage.objects (id, bucket_id, name, owner_id, metadata)
values (
    '84000000-0000-0000-0000-000000000001',
    'ocr-screenshots',
    'users/81000000-0000-0000-0000-000000000001/tournaments/82000000-0000-0000-0000-000000000001/matches/83000000-0000-0000-0000-000000000001/result/upper/original.png',
    '81000000-0000-0000-0000-000000000001',
    '{"mimetype":"image/png"}'::jsonb
);
select is((select count(*) from storage.objects where id = '84000000-0000-0000-0000-000000000001'), 1::bigint, 'owner can insert upper storage object');
select is((select count(*) from storage.objects where id = '84000000-0000-0000-0000-000000000001'), 1::bigint, 'owner can select upper storage object');
update storage.objects
set metadata = '{"mimetype":"image/png","updated":true}'::jsonb
where id = '84000000-0000-0000-0000-000000000001';
select is((select metadata from storage.objects where id = '84000000-0000-0000-0000-000000000001'), '{"updated": true, "mimetype": "image/png"}'::jsonb, 'owner can update upper storage object');
insert into storage.objects (id, bucket_id, name, owner_id, metadata)
values (
    '84000000-0000-0000-0000-000000000002',
    'ocr-screenshots',
    'users/81000000-0000-0000-0000-000000000001/tournaments/82000000-0000-0000-0000-000000000001/matches/83000000-0000-0000-0000-000000000001/result/lower/original.jpg',
    '81000000-0000-0000-0000-000000000001',
    '{"mimetype":"image/jpeg"}'::jsonb
);
select is((select count(*) from storage.objects where id = '84000000-0000-0000-0000-000000000002'), 1::bigint, 'owner can insert lower storage object');
select is((select count(*) from storage.objects where name like '%/result/lower/%'), 1::bigint, 'owner can select lower storage object');

select throws_ok($$
    insert into storage.objects (id, bucket_id, name, owner_id, metadata)
    values (
        '84000000-0000-0000-0000-000000000003',
        'ocr-screenshots',
        'users/81000000-0000-0000-0000-000000000001/tournaments/82000000-0000-0000-0000-000000000001/matches/83000000-0000-0000-0000-000000000001/result/middle/original.png',
        '81000000-0000-0000-0000-000000000001',
        '{}'::jsonb
    )
$$, '42501', null, 'malformed role path is rejected');
select throws_ok($$
    insert into storage.objects (id, bucket_id, name, owner_id, metadata)
    values (
        '84000000-0000-0000-0000-000000000004',
        'ocr-screenshots',
        'users/81000000-0000-0000-0000-000000000001/tournaments/82000000-0000-0000-0000-000000000001/matches/83000000-0000-0000-0000-000000000001/result/upper/crop.png',
        '81000000-0000-0000-0000-000000000001',
        '{}'::jsonb
    )
$$, '42501', null, 'wrong filename is rejected');
select throws_ok($$
    insert into storage.objects (id, bucket_id, name, owner_id, metadata)
    values (
        '84000000-0000-0000-0000-000000000005',
        'ocr-screenshots',
        'users/81000000-0000-0000-0000-000000000002/tournaments/82000000-0000-0000-0000-000000000001/matches/83000000-0000-0000-0000-000000000001/result/upper/original.png',
        '81000000-0000-0000-0000-000000000001',
        '{}'::jsonb
    )
$$, '42501', null, 'path with another user id is rejected');
select throws_ok($$
    insert into storage.objects (id, bucket_id, name, owner_id, metadata)
    values (
        '84000000-0000-0000-0000-000000000006',
        'ocr-screenshots',
        'users/81000000-0000-0000-0000-000000000001/tournaments/82000000-0000-0000-0000-000000000002/matches/83000000-0000-0000-0000-000000000008/result/upper/original.png',
        '81000000-0000-0000-0000-000000000001',
        '{}'::jsonb
    )
$$, '42501', null, 'path with another user tournament is rejected');
select throws_ok($$
    insert into storage.objects (id, bucket_id, name, owner_id, metadata)
    values (
        '84000000-0000-0000-0000-000000000007',
        'ocr-screenshots',
        'users/81000000-0000-0000-0000-000000000001/tournaments/82000000-0000-0000-0000-000000000001/matches/83000000-0000-0000-0000-000000000008/result/upper/original.png',
        '81000000-0000-0000-0000-000000000001',
        '{}'::jsonb
    )
$$, '42501', null, 'path with mismatched match and tournament is rejected');

set local role anon;
set local request.jwt.claim.sub = '';
select is((select count(*) from storage.objects where bucket_id = 'ocr-screenshots'), 0::bigint, 'anonymous storage select is rejected by RLS');
select throws_ok($$
    insert into storage.objects (id, bucket_id, name, owner_id, metadata)
    values (
        '84000000-0000-0000-0000-000000000008',
        'ocr-screenshots',
        'users/81000000-0000-0000-0000-000000000001/tournaments/82000000-0000-0000-0000-000000000001/matches/83000000-0000-0000-0000-000000000001/result/upper/original.png',
        '81000000-0000-0000-0000-000000000001',
        '{}'::jsonb
    )
$$, '42501', null, 'anonymous storage insert is rejected');

select * from finish();
rollback;
