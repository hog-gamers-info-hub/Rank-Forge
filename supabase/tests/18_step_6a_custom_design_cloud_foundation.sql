begin;

select plan(61);

select has_table(
    'public',
    'custom_design_templates',
    'custom design templates table exists'
);
select is((
    select relrowsecurity
    from pg_class
    where oid = 'public.custom_design_templates'::regclass
), true, 'custom design templates RLS is enabled');
select ok(exists (
    select 1
    from storage.buckets
    where id = 'custom-designs'
), 'custom designs bucket exists');
select is((
    select public
    from storage.buckets
    where id = 'custom-designs'
), false, 'custom designs bucket is private');
select is((
    select array_agg(value order by value)::text
    from unnest((
        select allowed_mime_types
        from storage.buckets
        where id = 'custom-designs'
    )) as mime_type(value)
), '{image/jpeg,image/png,image/webp}'::text, 'custom designs bucket accepts only approved image MIME types');

select has_column('public', 'custom_design_templates', 'id', 'id column exists');
select has_column('public', 'custom_design_templates', 'user_id', 'user_id column exists');
select has_column('public', 'custom_design_templates', 'image_path', 'image_path column exists');
select has_column('public', 'custom_design_templates', 'image_sha256', 'image_sha256 column exists');
select has_column('public', 'custom_design_templates', 'image_byte_size', 'image_byte_size column exists');
select has_column('public', 'custom_design_templates', 'image_extension', 'image_extension column exists');
select has_column('public', 'custom_design_templates', 'image_mime_type', 'image_mime_type column exists');
select has_column('public', 'custom_design_templates', 'source_width', 'source_width column exists');
select has_column('public', 'custom_design_templates', 'source_height', 'source_height column exists');
select has_column('public', 'custom_design_templates', 'labels_json', 'labels_json column exists');
select has_column('public', 'custom_design_templates', 'columns_json', 'columns_json column exists');
select has_column('public', 'custom_design_templates', 'rows_json', 'rows_json column exists');
select has_column('public', 'custom_design_templates', 'created_at', 'created_at column exists');
select has_column('public', 'custom_design_templates', 'updated_at', 'updated_at column exists');
select is((
    select count(*)
    from pg_indexes
    where schemaname = 'public'
        and tablename = 'custom_design_templates'
        and indexname in (
            'custom_design_templates_user_id_idx',
            'custom_design_templates_created_at_idx'
        )
), 2::bigint, 'custom design templates indexes exist');

select ok(has_table_privilege(
    'authenticated',
    'public.custom_design_templates',
    'SELECT'
), 'authenticated can select custom designs');
select ok(has_table_privilege(
    'authenticated',
    'public.custom_design_templates',
    'INSERT'
), 'authenticated can insert custom designs');
select ok(has_table_privilege(
    'authenticated',
    'public.custom_design_templates',
    'DELETE'
), 'authenticated can delete custom designs');
select ok(not has_table_privilege(
    'authenticated',
    'public.custom_design_templates',
    'UPDATE'
), 'authenticated cannot update custom designs');
select ok(not has_table_privilege(
    'anon',
    'public.custom_design_templates',
    'SELECT'
), 'anonymous users have no custom design table access');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and tablename = 'custom_design_templates'
        and policyname like 'custom_design_templates_%_owner'
), 3::bigint, 'custom design templates has exactly three owner policies');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'public'
        and tablename = 'custom_design_templates'
        and lower(cmd) = 'update'
), 0::bigint, 'custom design templates has no update policy');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname like 'custom_designs_%_owner'
), 3::bigint, 'custom designs storage has exactly three owner policies');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname = 'custom_designs_update_owner'
), 0::bigint, 'custom designs storage has no update policy');
select is((
    select count(*)
    from pg_policies
    where schemaname = 'storage'
        and tablename = 'objects'
        and policyname like 'custom_designs_%_owner'
        and roles = array['authenticated'::name]
), 3::bigint, 'all custom designs storage policies target authenticated users');

insert into auth.users (id, email)
values
    ('a1000000-0000-0000-0000-000000000001', 'custom-design-owner@example.test'),
    ('a1000000-0000-0000-0000-000000000002', 'custom-design-other@example.test');

set local role authenticated;
set local request.jwt.claim.sub = 'a1000000-0000-0000-0000-000000000001';

select lives_ok($$
    insert into public.custom_design_templates (
        id,
        user_id,
        image_path,
        image_sha256,
        image_byte_size,
        image_extension,
        image_mime_type,
        source_width,
        source_height,
        labels_json,
        columns_json,
        rows_json
    ) values (
        'a2000000-0000-0000-0000-000000000001',
        'a1000000-0000-0000-0000-000000000001',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000001/original.png',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        12345,
        'png',
        'image/png',
        1080,
        1350,
        '{"teamName":"Squad","win":"WIN","totalKills":"ELIM.","positionPoints":"POS.","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":700,"WIN":200,"TOTAL_KILLS":600,"POSITION_POINTS":400,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    )
$$, 'owner A can insert a valid custom design');
select is((
    select count(*)
    from public.custom_design_templates
    where id = 'a2000000-0000-0000-0000-000000000001'
), 1::bigint, 'owner A can select its custom design');

select lives_ok($$
    insert into storage.objects (id, bucket_id, name, owner_id, metadata)
    values (
        'a3000000-0000-0000-0000-000000000001',
        'custom-designs',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000001/original.png',
        'a1000000-0000-0000-0000-000000000001',
        '{"mimetype":"image/png"}'::jsonb
    )
$$, 'owner A can insert a custom design storage object');
select is((
    select count(*)
    from storage.objects
    where id = 'a3000000-0000-0000-0000-000000000001'
), 1::bigint, 'owner A can select its custom design storage object');

set local request.jwt.claim.sub = 'a1000000-0000-0000-0000-000000000002';
select is((
    select count(*)
    from public.custom_design_templates
    where id = 'a2000000-0000-0000-0000-000000000001'
), 0::bigint, 'owner B cannot see owner A custom design');
select throws_ok($$
    update public.custom_design_templates
    set image_byte_size = 99999
    where id = 'a2000000-0000-0000-0000-000000000001'
$$, '42501', null, 'owner B cannot update owner A custom design');
delete from public.custom_design_templates
where id = 'a2000000-0000-0000-0000-000000000001';

set local request.jwt.claim.sub = 'a1000000-0000-0000-0000-000000000001';
select is((
    select count(*)
    from public.custom_design_templates
    where id = 'a2000000-0000-0000-0000-000000000001'
), 1::bigint, 'owner B cannot delete owner A custom design');

set local request.jwt.claim.sub = 'a1000000-0000-0000-0000-000000000002';
select throws_ok($$
    insert into public.custom_design_templates (
        id,
        user_id,
        image_path,
        image_sha256,
        image_byte_size,
        image_extension,
        image_mime_type,
        source_width,
        source_height,
        labels_json,
        columns_json,
        rows_json
    ) values (
        'a2000000-0000-0000-0000-000000000002',
        'a1000000-0000-0000-0000-000000000001',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000002/original.png',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        12345,
        'png',
        'image/png',
        1080,
        1350,
        '{"teamName":"Squad","win":"WIN","totalKills":"ELIM.","positionPoints":"POS.","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":700,"WIN":200,"TOTAL_KILLS":600,"POSITION_POINTS":400,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    )
$$, '42501', null, 'owner B cannot claim owner A table ownership');
select is((
    select count(*)
    from storage.objects
    where id = 'a3000000-0000-0000-0000-000000000001'
), 0::bigint, 'owner B cannot see owner A storage object');
select throws_ok($$
    insert into storage.objects (id, bucket_id, name, owner_id, metadata)
    values (
        'a3000000-0000-0000-0000-000000000002',
        'custom-designs',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000001/original.png',
        'a1000000-0000-0000-0000-000000000002',
        '{"mimetype":"image/png"}'::jsonb
    )
$$, '42501', null, 'owner B cannot insert into owner A storage path');
set local storage.allow_delete_query = 'true';
delete from storage.objects
where id = 'a3000000-0000-0000-0000-000000000001';

set local request.jwt.claim.sub = 'a1000000-0000-0000-0000-000000000001';
select is((
    select count(*)
    from storage.objects
    where id = 'a3000000-0000-0000-0000-000000000001'
), 1::bigint, 'owner B cannot delete owner A storage object');
select is((
    select count(*)
    from public.custom_design_templates
    where id = 'a2000000-0000-0000-0000-000000000001'
), 1::bigint, 'owner A row still exists after owner B attempts');
select is((
    select count(*)
    from storage.objects
    where id = 'a3000000-0000-0000-0000-000000000001'
), 1::bigint, 'owner A storage object still exists after owner B attempts');

with deleted as (
    delete from public.custom_design_templates
    where id = 'a2000000-0000-0000-0000-000000000001'
    returning 1
)
select is(
    (select count(*) from deleted),
    1::bigint,
    'owner A can delete its custom design row'
);
with deleted as (
    delete from storage.objects
    where id = 'a3000000-0000-0000-0000-000000000001'
    returning 1
)
select is(
    (select count(*) from deleted),
    1::bigint,
    'owner A can delete its storage object after row deletion'
);

select throws_ok($$
    insert into public.custom_design_templates (
        id, user_id, image_path, image_sha256, image_byte_size,
        image_extension, image_mime_type, source_width, source_height,
        labels_json, columns_json, rows_json
    ) values (
        'a2000000-0000-0000-0000-000000000010',
        'a1000000-0000-0000-0000-000000000001',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000010/original.png',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        12345, 'png', 'image/png', 0, 1350,
        '{"teamName":"Squad","win":"WIN","totalKills":"ELIM.","positionPoints":"POS.","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":700,"WIN":200,"TOTAL_KILLS":600,"POSITION_POINTS":400,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    )
$$, '23514', null, 'source width must be positive');
select throws_ok($$
    insert into public.custom_design_templates (
        id, user_id, image_path, image_sha256, image_byte_size,
        image_extension, image_mime_type, source_width, source_height,
        labels_json, columns_json, rows_json
    ) values (
        'a2000000-0000-0000-0000-000000000011',
        'a1000000-0000-0000-0000-000000000001',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000011/original.png',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        12345, 'png', 'image/png', 1080, 0,
        '{"teamName":"Squad","win":"WIN","totalKills":"ELIM.","positionPoints":"POS.","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":700,"WIN":200,"TOTAL_KILLS":600,"POSITION_POINTS":400,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    )
$$, '23514', null, 'source height must be positive');
select throws_ok($$
    insert into public.custom_design_templates (
        id, user_id, image_path, image_sha256, image_byte_size,
        image_extension, image_mime_type, source_width, source_height,
        labels_json, columns_json, rows_json
    ) values (
        'a2000000-0000-0000-0000-000000000012',
        'a1000000-0000-0000-0000-000000000001',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000012/original.png',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        0, 'png', 'image/png', 1080, 1350,
        '{"teamName":"Squad","win":"WIN","totalKills":"ELIM.","positionPoints":"POS.","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":700,"WIN":200,"TOTAL_KILLS":600,"POSITION_POINTS":400,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    )
$$, '23514', null, 'image byte size must be positive');
select throws_ok($$
    insert into public.custom_design_templates (
        id, user_id, image_path, image_sha256, image_byte_size,
        image_extension, image_mime_type, source_width, source_height,
        labels_json, columns_json, rows_json
    ) values (
        'a2000000-0000-0000-0000-000000000013',
        'a1000000-0000-0000-0000-000000000001',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000013/original.png',
        '0123456789abcdef',
        12345, 'png', 'image/png', 1080, 1350,
        '{"teamName":"Squad","win":"WIN","totalKills":"ELIM.","positionPoints":"POS.","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":700,"WIN":200,"TOTAL_KILLS":600,"POSITION_POINTS":400,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    )
$$, '23514', null, 'image SHA-256 must be lowercase hexadecimal');
select throws_ok($$
    insert into public.custom_design_templates (
        id, user_id, image_path, image_sha256, image_byte_size,
        image_extension, image_mime_type, source_width, source_height,
        labels_json, columns_json, rows_json
    ) values (
        'a2000000-0000-0000-0000-000000000014',
        'a1000000-0000-0000-0000-000000000001',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000014/original.png',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        12345, 'png', 'image/jpeg', 1080, 1350,
        '{"teamName":"Squad","win":"WIN","totalKills":"ELIM.","positionPoints":"POS.","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":700,"WIN":200,"TOTAL_KILLS":600,"POSITION_POINTS":400,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    )
$$, '23514', null, 'image extension and MIME type must match');
select throws_ok($$
    insert into public.custom_design_templates (
        id, user_id, image_path, image_sha256, image_byte_size,
        image_extension, image_mime_type, source_width, source_height,
        labels_json, columns_json, rows_json
    ) values (
        'a2000000-0000-0000-0000-000000000015',
        'a1000000-0000-0000-0000-000000000001',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/wrong-id/original.png',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        12345, 'png', 'image/png', 1080, 1350,
        '{"teamName":"Squad","win":"WIN","totalKills":"ELIM.","positionPoints":"POS.","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":700,"WIN":200,"TOTAL_KILLS":600,"POSITION_POINTS":400,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    )
$$, '23514', null, 'image path must match the immutable record identity');
select throws_ok($$
    insert into public.custom_design_templates (
        id, user_id, image_path, image_sha256, image_byte_size,
        image_extension, image_mime_type, source_width, source_height,
        labels_json, columns_json, rows_json
    ) values (
        'a2000000-0000-0000-0000-000000000016',
        'a1000000-0000-0000-0000-000000000001',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000016/original.png',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        12345, 'png', 'image/png', 1080, 1350,
        '{"teamName":"Squad","win":"WIN","totalKills":"ELIM.","positionPoints":"POS."}'::jsonb,
        '{"TEAM_NAME":700,"WIN":200,"TOTAL_KILLS":600,"POSITION_POINTS":400,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    )
$$, '23514', null, 'labels must contain exactly the five required keys');
select throws_ok($$
    insert into public.custom_design_templates (
        id, user_id, image_path, image_sha256, image_byte_size,
        image_extension, image_mime_type, source_width, source_height,
        labels_json, columns_json, rows_json
    ) values (
        'a2000000-0000-0000-0000-000000000017',
        'a1000000-0000-0000-0000-000000000001',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000017/original.png',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        12345, 'png', 'image/png', 1080, 1350,
        '{"teamName":"Squad","win":"WIN","totalKills":"ELIM.","positionPoints":"POS.","totalPoints":"TOTAL","extra":"ignored"}'::jsonb,
        '{"TEAM_NAME":700,"WIN":200,"TOTAL_KILLS":600,"POSITION_POINTS":400,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    )
$$, '23514', null, 'labels cannot contain extra keys');
select throws_ok($$
    insert into public.custom_design_templates (
        id, user_id, image_path, image_sha256, image_byte_size,
        image_extension, image_mime_type, source_width, source_height,
        labels_json, columns_json, rows_json
    ) values (
        'a2000000-0000-0000-0000-000000000018',
        'a1000000-0000-0000-0000-000000000001',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000018/original.png',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        12345, 'png', 'image/png', 1080, 1350,
        '{"teamName":"Squad","win":"WIN","totalKills":"ELIM.","positionPoints":"POS.","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":700,"WIN":200,"TOTAL_KILLS":600,"POSITION_POINTS":400}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    )
$$, '23514', null, 'columns must contain exactly the five required keys');
select throws_ok($$
    insert into public.custom_design_templates (
        id, user_id, image_path, image_sha256, image_byte_size,
        image_extension, image_mime_type, source_width, source_height,
        labels_json, columns_json, rows_json
    ) values (
        'a2000000-0000-0000-0000-000000000019',
        'a1000000-0000-0000-0000-000000000001',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000019/original.png',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        12345, 'png', 'image/png', 1080, 1350,
        '{"teamName":"Squad","win":"WIN","totalKills":"ELIM.","positionPoints":"POS.","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":700,"WIN":200,"TOTAL_KILLS":600,"POSITION_POINTS":400,"TOTAL_POINTS":900,"EXTRA":100}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    )
$$, '23514', null, 'columns cannot contain extra keys');
select throws_ok($$
    insert into public.custom_design_templates (
        id, user_id, image_path, image_sha256, image_byte_size,
        image_extension, image_mime_type, source_width, source_height,
        labels_json, columns_json, rows_json
    ) values (
        'a2000000-0000-0000-0000-000000000020',
        'a1000000-0000-0000-0000-000000000001',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000020/original.png',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        12345, 'png', 'image/png', 1080, 1350,
        '{"teamName":"Squad","win":"WIN","totalKills":"ELIM.","positionPoints":"POS.","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":1081,"WIN":200,"TOTAL_KILLS":600,"POSITION_POINTS":400,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    )
$$, '23514', null, 'column coordinates must be inside the source width');
select throws_ok($$
    insert into public.custom_design_templates (
        id, user_id, image_path, image_sha256, image_byte_size,
        image_extension, image_mime_type, source_width, source_height,
        labels_json, columns_json, rows_json
    ) values (
        'a2000000-0000-0000-0000-000000000021',
        'a1000000-0000-0000-0000-000000000001',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000021/original.png',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        12345, 'png', 'image/png', 1080, 1350,
        '{"teamName":"Squad","win":"WIN","totalKills":"ELIM.","positionPoints":"POS.","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":700,"WIN":200,"TOTAL_KILLS":600,"POSITION_POINTS":400,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100}'::jsonb
    )
$$, '23514', null, 'rows must contain exactly ranks 1 through 12');
select throws_ok($$
    insert into public.custom_design_templates (
        id, user_id, image_path, image_sha256, image_byte_size,
        image_extension, image_mime_type, source_width, source_height,
        labels_json, columns_json, rows_json
    ) values (
        'a2000000-0000-0000-0000-000000000022',
        'a1000000-0000-0000-0000-000000000001',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000022/original.png',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        12345, 'png', 'image/png', 1080, 1350,
        '{"teamName":"Squad","win":"WIN","totalKills":"ELIM.","positionPoints":"POS.","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":700,"WIN":200,"TOTAL_KILLS":600,"POSITION_POINTS":400,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200,"13":1300}'::jsonb
    )
$$, '23514', null, 'rows cannot contain extra ranks');
select throws_ok($$
    insert into public.custom_design_templates (
        id, user_id, image_path, image_sha256, image_byte_size,
        image_extension, image_mime_type, source_width, source_height,
        labels_json, columns_json, rows_json
    ) values (
        'a2000000-0000-0000-0000-000000000023',
        'a1000000-0000-0000-0000-000000000001',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000023/original.png',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        12345, 'png', 'image/png', 1080, 1350,
        '{"teamName":"Squad","win":"WIN","totalKills":"ELIM.","positionPoints":"POS.","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":700,"WIN":200,"TOTAL_KILLS":600,"POSITION_POINTS":400,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1400}'::jsonb
    )
$$, '23514', null, 'row coordinates must be inside the source height');
select throws_ok($$
    insert into public.custom_design_templates (
        id, user_id, image_path, image_sha256, image_byte_size,
        image_extension, image_mime_type, source_width, source_height,
        labels_json, columns_json, rows_json
    ) values (
        'a2000000-0000-0000-0000-000000000024',
        'a1000000-0000-0000-0000-000000000001',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000024/original.png',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        12345, 'png', 'image/png', 1080, 1350,
        '{"teamName":"Squad","win":"WIN","totalKills":"ELIM.","positionPoints":"POS.","totalPoints":"TOTAL"}'::jsonb,
        '{"TEAM_NAME":700,"WIN":200,"TOTAL_KILLS":600,"POSITION_POINTS":400,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":450,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    )
$$, '23514', null, 'rows must remain strictly increasing');
select throws_ok($$
    insert into public.custom_design_templates (
        id, user_id, image_path, image_sha256, image_byte_size,
        image_extension, image_mime_type, source_width, source_height,
        labels_json, columns_json, rows_json
    ) values (
        'a2000000-0000-0000-0000-000000000025',
        'a1000000-0000-0000-0000-000000000001',
        'users/a1000000-0000-0000-0000-000000000001/custom-designs/a2000000-0000-0000-0000-000000000025/original.png',
        '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
        12345, 'png', 'image/png', 1080, 1350,
        '{"teamName":"Squad","win":"WIN","totalKills":"ELIM.","positionPoints":"POS.","totalPoints":"   "}'::jsonb,
        '{"TEAM_NAME":700,"WIN":200,"TOTAL_KILLS":600,"POSITION_POINTS":400,"TOTAL_POINTS":900}'::jsonb,
        '{"1":100,"2":200,"3":300,"4":400,"5":500,"6":600,"7":700,"8":800,"9":900,"10":1000,"11":1100,"12":1200}'::jsonb
    )
$$, '23514', null, 'labels cannot be blank');

select * from finish();
rollback;
