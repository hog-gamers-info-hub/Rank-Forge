begin;

select plan(16);

select hasnt_column(
    'public',
    'tournaments',
    'tournament_date',
    'tournaments no longer has tournament_date'
);
select ok(
    to_regprocedure('public.write_tournament_snapshot(jsonb,jsonb,jsonb,integer)') is not null,
    'write_tournament_snapshot keeps its approved signature'
);

insert into auth.users (id, email)
values ('b1000000-0000-0000-0000-000000000001', 'tournament-date-removal@example.test');

set local role authenticated;
set local request.jwt.claim.sub = 'b1000000-0000-0000-0000-000000000001';

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'a1000000-0000-0000-0000-000000000001'::uuid,
            'owner_id', 'b1000000-0000-0000-0000-000000000001'::uuid,
            'name', 'Date-Free Cup',
            'organizer_name', 'Stage One',
            'organization_name', 'PointIQ',
            'organizer_contact', '123',
            'status', 'draft'
        ),
        jsonb_build_array(jsonb_build_object(
            'id', 'c1000000-0000-0000-0000-000000000001'::uuid,
            'tournament_id', 'a1000000-0000-0000-0000-000000000001'::uuid,
            'slot_number', 1,
            'team_name', 'Alpha',
            'status', 'draft'
        )),
        '[]'::jsonb,
        0
    )
), 'success', 'initial snapshot without tournament_date succeeds');
select is((select count(*) from public.tournaments where id = 'a1000000-0000-0000-0000-000000000001'), 1::bigint, 'initial snapshot inserts the tournament');
select is((select name from public.tournaments where id = 'a1000000-0000-0000-0000-000000000001'), 'Date-Free Cup', 'initial snapshot stores the tournament name');
select is((select organizer_name from public.tournaments where id = 'a1000000-0000-0000-0000-000000000001'), 'Stage One', 'initial snapshot stores the stage name');
select is((select organization_name from public.tournaments where id = 'a1000000-0000-0000-0000-000000000001'), 'PointIQ', 'initial snapshot stores the organization name');
select is((select revision from public.tournaments where id = 'a1000000-0000-0000-0000-000000000001'), 1, 'initial snapshot starts at revision one');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'a1000000-0000-0000-0000-000000000001'::uuid,
            'owner_id', 'b1000000-0000-0000-0000-000000000001'::uuid,
            'name', 'Date-Free Cup Updated',
            'organizer_name', 'Stage Two',
            'organization_name', 'PointIQ Events',
            'organizer_contact', '456',
            'status', 'draft'
        ),
        '[]'::jsonb,
        '[]'::jsonb,
        1
    )
), 'success', 'current snapshot updates without tournament_date');
select is((select name from public.tournaments where id = 'a1000000-0000-0000-0000-000000000001'), 'Date-Free Cup Updated', 'update stores the tournament name');
select is((select organizer_name from public.tournaments where id = 'a1000000-0000-0000-0000-000000000001'), 'Stage Two', 'update stores the stage name');
select is((select organization_name from public.tournaments where id = 'a1000000-0000-0000-0000-000000000001'), 'PointIQ Events', 'update stores the organization name');
select is((select revision from public.tournaments where id = 'a1000000-0000-0000-0000-000000000001'), 2, 'successful update advances the revision');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'a1000000-0000-0000-0000-000000000001'::uuid,
            'owner_id', 'b1000000-0000-0000-0000-000000000001'::uuid,
            'name', 'Stale Name',
            'organizer_name', 'Stale Stage',
            'organization_name', 'Stale Organization',
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
            'id', 'a1000000-0000-0000-0000-000000000001'::uuid,
            'owner_id', 'b1000000-0000-0000-0000-000000000001'::uuid,
            'name', 'Stale Name',
            'organizer_name', 'Stale Stage',
            'organization_name', 'Stale Organization',
            'organizer_contact', '789',
            'status', 'draft'
        ),
        '[]'::jsonb,
        '[]'::jsonb,
        1
    )
), 2, 'stale snapshot returns the current revision');
select is((select name from public.tournaments where id = 'a1000000-0000-0000-0000-000000000001'), 'Date-Free Cup Updated', 'stale snapshot does not mutate the tournament');

select * from finish();
rollback;
