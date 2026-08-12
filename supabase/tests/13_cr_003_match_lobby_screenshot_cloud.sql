begin;

select plan(16);

select ok(
    to_regclass('public.match_lobby_screenshot_assets') is not null,
    'Lobby screenshot metadata table exists'
);
select ok(
    (
        select relrowsecurity
        from pg_class
        where oid = 'public.match_lobby_screenshot_assets'::regclass
    ),
    'Lobby screenshot metadata has RLS enabled'
);

insert into auth.users (id, email)
values
    ('95000000-0000-0000-0000-000000000001', 'lobby-owner@example.test'),
    ('95000000-0000-0000-0000-000000000002', 'lobby-other@example.test');
insert into public.tournaments (id, owner_id, name)
values (
    '96000000-0000-0000-0000-000000000001',
    '95000000-0000-0000-0000-000000000001',
    'Lobby Cup'
);
insert into public.matches (id, tournament_id, match_number, status)
values (
    '97000000-0000-0000-0000-000000000001',
    '96000000-0000-0000-0000-000000000001',
    1,
    'draft'
);

set local role authenticated;
set local request.jwt.claim.sub = '95000000-0000-0000-0000-000000000001';

insert into public.match_lobby_screenshot_assets (
    match_id, owner_id, tournament_id, lobby_screenshot_index,
    local_file_extension, mime_type, original_width, original_height, byte_size,
    sha256, storage_bucket, storage_object_path, local_status, upload_status,
    preserved_at, revision
) values (
    '97000000-0000-0000-0000-000000000001',
    '95000000-0000-0000-0000-000000000001',
    '96000000-0000-0000-0000-000000000001',
    1,
    'png', 'image/png', 1600, 900, 4,
    repeat('a', 64), 'ocr-screenshots',
    'users/95000000-0000-0000-0000-000000000001/tournaments/96000000-0000-0000-0000-000000000001/matches/97000000-0000-0000-0000-000000000001/lobby/1/original.png',
    'PRESERVED', 'PENDING', now(), 1
);
select is(
    (select count(*) from public.match_lobby_screenshot_assets),
    1::bigint,
    'owner can create Lobby metadata for index 1'
);

insert into public.match_lobby_screenshot_assets (
    match_id, owner_id, tournament_id, lobby_screenshot_index,
    local_file_extension, mime_type, original_width, original_height, byte_size,
    sha256, local_status, upload_status, preserved_at, revision
) values
    ('97000000-0000-0000-0000-000000000001', '95000000-0000-0000-0000-000000000001', '96000000-0000-0000-0000-000000000001', 2, 'jpg', 'image/jpeg', 1600, 900, 4, repeat('b', 64), 'PRESERVED', 'PENDING', now(), 1),
    ('97000000-0000-0000-0000-000000000001', '95000000-0000-0000-0000-000000000001', '96000000-0000-0000-0000-000000000001', 3, 'webp', 'image/webp', 1600, 900, 4, repeat('c', 64), 'PRESERVED', 'PENDING', now(), 1);
select is(
    (select count(*) from public.match_lobby_screenshot_assets),
    3::bigint,
    'independent Lobby indexes 1, 2, and 3 are accepted'
);

select throws_ok($$
    insert into public.match_lobby_screenshot_assets (
        match_id, owner_id, tournament_id, lobby_screenshot_index,
        local_file_extension, mime_type, original_width, original_height, byte_size,
        sha256, local_status, upload_status, preserved_at
    ) values (
        '97000000-0000-0000-0000-000000000001',
        '95000000-0000-0000-0000-000000000001',
        '96000000-0000-0000-0000-000000000001',
        4, 'png', 'image/png', 1, 1, 1, repeat('d', 64), 'PRESERVED', 'PENDING', now()
    )
$$, '23514', null, 'invalid Lobby index is rejected');

set local request.jwt.claim.sub = '95000000-0000-0000-0000-000000000002';
select is(
    (select count(*) from public.match_lobby_screenshot_assets),
    0::bigint,
    'another user cannot select owner Lobby metadata'
);
select throws_ok($$
    insert into public.match_lobby_screenshot_assets (
        match_id, owner_id, tournament_id, lobby_screenshot_index,
        local_file_extension, mime_type, original_width, original_height, byte_size,
        sha256, local_status, upload_status, preserved_at
    ) values (
        '97000000-0000-0000-0000-000000000001',
        '95000000-0000-0000-0000-000000000002',
        '96000000-0000-0000-0000-000000000001',
        1, 'png', 'image/png', 1, 1, 1, repeat('e', 64), 'PRESERVED', 'PENDING', now()
    )
$$, '42501', null, 'another user cannot insert owner Lobby metadata');
update public.match_lobby_screenshot_assets
set original_width = 99
where lobby_screenshot_index = 1;
select is(
    (select original_width from public.match_lobby_screenshot_assets where lobby_screenshot_index = 1),
    null,
    'another user cannot update owner Lobby metadata'
);
delete from public.match_lobby_screenshot_assets
where lobby_screenshot_index = 1;
set local request.jwt.claim.sub = '95000000-0000-0000-0000-000000000001';
select is(
    (select count(*) from public.match_lobby_screenshot_assets),
    3::bigint,
    'another user cannot delete owner Lobby metadata'
);

select is((
    select count(*)
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname like 'ocr_screenshots_lobby_%_owner'
), 3::bigint, 'Lobby Storage has insert, select, and update owner policies');

insert into storage.objects (id, bucket_id, name, owner_id, metadata)
values
    ('98000000-0000-0000-0000-000000000001', 'ocr-screenshots', 'users/95000000-0000-0000-0000-000000000001/tournaments/96000000-0000-0000-0000-000000000001/matches/97000000-0000-0000-0000-000000000001/lobby/1/original.png', '95000000-0000-0000-0000-000000000001', '{"mimetype":"image/png"}'::jsonb),
    ('98000000-0000-0000-0000-000000000002', 'ocr-screenshots', 'users/95000000-0000-0000-0000-000000000001/tournaments/96000000-0000-0000-0000-000000000001/matches/97000000-0000-0000-0000-000000000001/lobby/2/original.jpg', '95000000-0000-0000-0000-000000000001', '{"mimetype":"image/jpeg"}'::jsonb),
    ('98000000-0000-0000-0000-000000000003', 'ocr-screenshots', 'users/95000000-0000-0000-0000-000000000001/tournaments/96000000-0000-0000-0000-000000000001/matches/97000000-0000-0000-0000-000000000001/lobby/3/original.webp', '95000000-0000-0000-0000-000000000001', '{"mimetype":"image/webp"}'::jsonb);
select is(
    (select count(*) from storage.objects where name like '%/lobby/%'),
    3::bigint,
    'owner can create Lobby Storage objects for indexes 1, 2, and 3'
);

select throws_ok($$
    insert into storage.objects (id, bucket_id, name, owner_id, metadata)
    values ('98000000-0000-0000-0000-000000000004', 'ocr-screenshots', 'users/95000000-0000-0000-0000-000000000001/tournaments/96000000-0000-0000-0000-000000000001/matches/97000000-0000-0000-0000-000000000001/lobby/0/original.png', '95000000-0000-0000-0000-000000000001', '{}'::jsonb)
$$, '42501', null, 'Lobby Storage index 0 is rejected');
select throws_ok($$
    insert into storage.objects (id, bucket_id, name, owner_id, metadata)
    values ('98000000-0000-0000-0000-000000000005', 'ocr-screenshots', 'users/95000000-0000-0000-0000-000000000001/tournaments/96000000-0000-0000-0000-000000000001/matches/97000000-0000-0000-0000-000000000001/lobby/4/original.png', '95000000-0000-0000-0000-000000000001', '{}'::jsonb)
$$, '42501', null, 'Lobby Storage index 4 is rejected');
select throws_ok($$
    insert into storage.objects (id, bucket_id, name, owner_id, metadata)
    values ('98000000-0000-0000-0000-000000000006', 'ocr-screenshots', 'users/95000000-0000-0000-0000-000000000002/tournaments/96000000-0000-0000-0000-000000000001/matches/97000000-0000-0000-0000-000000000001/lobby/1/original.png', '95000000-0000-0000-0000-000000000002', '{}'::jsonb)
$$, '42501', null, 'another user Lobby Storage path is rejected');

select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and tablename = 'match_result_screenshot_assets'
        and policyname like 'match_result_screenshot_assets_%_owner'
), 4::bigint, 'Result screenshot policies remain present and unaffected');

delete from public.matches where id = '97000000-0000-0000-0000-000000000001';
select is(
    (select count(*) from public.match_lobby_screenshot_assets),
    0::bigint,
    'deleting the parent match cascades Lobby metadata'
);

select * from finish();
rollback;
