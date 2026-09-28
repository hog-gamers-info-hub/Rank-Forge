begin;

select plan(14);

select hasnt_column(
    'public',
    'tournaments',
    'organization_name',
    'tournaments no longer has organization_name'
);
select ok(
    to_regprocedure('public.write_tournament_snapshot(jsonb,jsonb,jsonb,integer)') is not null,
    'write_tournament_snapshot keeps its approved signature'
);

insert into auth.users (id, email)
values ('b2000000-0000-0000-0000-000000000001', 'tournament-organization-removal@example.test');

set local role authenticated;
set local request.jwt.claim.sub = 'b2000000-0000-0000-0000-000000000001';

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'a2000000-0000-0000-0000-000000000001'::uuid,
            'owner_id', 'b2000000-0000-0000-0000-000000000001'::uuid,
            'name', 'Organization-Free Cup',
            'organizer_name', 'Stage One',
            'organizer_contact', '123',
            'status', 'draft'
        ),
        '[]'::jsonb,
        '[]'::jsonb,
        0
    )
), 'success', 'initial snapshot without organization_name succeeds');
select is((select count(*) from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 1::bigint, 'initial snapshot inserts the tournament');
select is((select name from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 'Organization-Free Cup', 'initial snapshot stores the tournament name');
select is((select organizer_name from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 'Stage One', 'initial snapshot stores the stage name');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 1, 'initial snapshot starts at revision one');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'a2000000-0000-0000-0000-000000000001'::uuid,
            'owner_id', 'b2000000-0000-0000-0000-000000000001'::uuid,
            'name', 'Organization-Free Cup Updated',
            'organizer_name', 'Stage Two',
            'organizer_contact', '456',
            'status', 'draft'
        ),
        '[]'::jsonb,
        '[]'::jsonb,
        1
    )
), 'success', 'current snapshot updates without organization_name');
select is((select name from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 'Organization-Free Cup Updated', 'update stores the tournament name');
select is((select organizer_name from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 'Stage Two', 'update stores the stage name');
select is((select revision from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 2, 'successful update advances the revision');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'a2000000-0000-0000-0000-000000000001'::uuid,
            'owner_id', 'b2000000-0000-0000-0000-000000000001'::uuid,
            'name', 'Stale Name',
            'organizer_name', 'Stale Stage',
            'organizer_contact', '789',
            'status', 'draft'
        ),
        '[]'::jsonb,
        '[]'::jsonb,
        1
    )
), 'stale_write', 'stale snapshot remains rejected');
select is((
    select revision
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'a2000000-0000-0000-0000-000000000001'::uuid,
            'owner_id', 'b2000000-0000-0000-0000-000000000001'::uuid,
            'name', 'Stale Name',
            'organizer_name', 'Stale Stage',
            'organizer_contact', '789',
            'status', 'draft'
        ),
        '[]'::jsonb,
        '[]'::jsonb,
        1
    )
), 2, 'stale snapshot returns the current revision');
select is((select name from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 'Organization-Free Cup Updated', 'stale snapshot does not mutate the tournament');

select * from finish();
rollback;
