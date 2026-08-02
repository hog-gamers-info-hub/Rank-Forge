begin;

select plan(3);

insert into auth.users (id, email)
values ('91000000-0000-0000-0000-000000000001', 'storage-owner@example.test');
insert into public.tournaments (id, owner_id, name)
values (
    '92000000-0000-0000-0000-000000000001',
    '91000000-0000-0000-0000-000000000001',
    'Storage Policy Tournament'
);
insert into public.matches (id, tournament_id, match_number, status)
values (
    '93000000-0000-0000-0000-000000000001',
    '92000000-0000-0000-0000-000000000001',
    1,
    'draft'
);

set local role authenticated;
set local request.jwt.claim.sub = '91000000-0000-0000-0000-000000000001';

insert into storage.objects (id, bucket_id, name, owner_id, metadata)
values (
    '94000000-0000-0000-0000-000000000001',
    'match-screenshots',
    'users/91000000-0000-0000-0000-000000000001/tournaments/92000000-0000-0000-0000-000000000001/matches/93000000-0000-0000-0000-000000000001/original.png',
    '91000000-0000-0000-0000-000000000001',
    '{"mimetype":"image/png"}'::jsonb
);

select is((
    select count(*)
    from storage.objects
    where id = '94000000-0000-0000-0000-000000000001'
), 1::bigint, 'authenticated owner can insert a correctly scoped screenshot object');

select is((
    select count(*)
    from storage.objects
    where id = '94000000-0000-0000-0000-000000000001'
), 1::bigint, 'authenticated owner can select the screenshot object');

update storage.objects
set name = 'users/91000000-0000-0000-0000-000000000001/tournaments/92000000-0000-0000-0000-000000000001/matches/93000000-0000-0000-0000-000000000001/original.webp',
    metadata = '{"mimetype":"image/webp"}'::jsonb
where id = '94000000-0000-0000-0000-000000000001';

select is((
    select count(*)
    from storage.objects
    where id = '94000000-0000-0000-0000-000000000001'
        and name like '%/original.webp'
        and metadata = '{"mimetype":"image/webp"}'::jsonb
), 1::bigint, 'authenticated owner can update the screenshot within the allowed scope');

select * from finish();
rollback;
