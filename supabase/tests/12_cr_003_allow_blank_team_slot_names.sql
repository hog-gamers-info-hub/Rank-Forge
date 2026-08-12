begin;

select plan(15);

select ok(
    not exists (
        select 1
        from pg_constraint
        where conrelid = 'public.tournament_team_slots'::regclass
          and conname = 'tournament_team_slots_tournament_team_name_key'
    ),
    'old unconditional team-name constraint is removed'
);
select ok(
    exists (
        select 1
        from pg_indexes
        where schemaname = 'public'
          and tablename = 'tournament_team_slots'
          and indexname = 'tournament_team_slots_tournament_nonblank_team_name_uidx'
          and position('team_name IS NOT NULL' in indexdef) > 0
          and position('btrim(team_name)' in indexdef) > 0
    ),
    'nonblank team-name partial unique index exists'
);

insert into auth.users (id, email)
values
    ('b0000000-0000-0000-0000-000000000001', 'blank-slots-owner@example.test'),
    ('b0000000-0000-0000-0000-000000000002', 'mixed-slots-owner@example.test'),
    ('b0000000-0000-0000-0000-000000000003', 'other-tournament-owner@example.test');

insert into public.tournaments (id, owner_id, name)
values
    ('a0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'Blank Slots'),
    ('a0000000-0000-0000-0000-000000000002', 'b0000000-0000-0000-0000-000000000002', 'Mixed Slots'),
    ('a0000000-0000-0000-0000-000000000003', 'b0000000-0000-0000-0000-000000000003', 'Other Tournament'),
    ('a0000000-0000-0000-0000-000000000005', 'b0000000-0000-0000-0000-000000000001', 'Duplicate Name');

select lives_ok($$
    insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
    select ('c0000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
           'a0000000-0000-0000-0000-000000000001'::uuid,
           slot_number,
           ''
    from generate_series(1, 12) as slot_number
$$, 'all twelve blank team slots are allowed');
select is((
    select count(*)
    from public.tournament_team_slots
    where tournament_id = 'a0000000-0000-0000-0000-000000000001'
), 12::bigint, 'all twelve blank team slots are stored');

select lives_ok($$
    insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
    select ('d0000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
           'a0000000-0000-0000-0000-000000000002'::uuid,
           slot_number,
           case slot_number
               when 1 then 'Alpha'
               when 2 then 'Bravo'
               when 3 then 'Charlie'
               else ''
           end
    from generate_series(1, 12) as slot_number
$$, 'three named and nine blank team slots are allowed');
select is((
    select count(*)
    from public.tournament_team_slots
    where tournament_id = 'a0000000-0000-0000-0000-000000000002'
      and team_name = ''
), 9::bigint, 'mixed participation keeps nine blank slots');

select throws_ok($$
    insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
    values ('d0000000-0000-0000-0000-000000000013', 'a0000000-0000-0000-0000-000000000002', 13, 'Alpha')
$$, '23514', null, 'invalid slot number remains rejected');
insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
values ('e0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000005', 1, 'Alpha');
select throws_ok($$
    insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
    values ('e0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000005', 2, 'Alpha')
$$, '23505', null, 'duplicate nonblank team names remain rejected');
select lives_ok($$
    insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
    values
        ('e0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000005', 3, '   '),
        ('e0000000-0000-0000-0000-000000000005', 'a0000000-0000-0000-0000-000000000005', 4, '   ')
$$, 'repeated whitespace-only team names are allowed');
select lives_ok($$
    insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
    values ('e0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000003', 1, 'Alpha')
$$, 'the same team name is allowed in another tournament');

set local role authenticated;
set local request.jwt.claim.sub = 'b0000000-0000-0000-0000-000000000001';

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'a0000000-0000-0000-0000-000000000004'::uuid,
            'owner_id', 'b0000000-0000-0000-0000-000000000001'::uuid,
            'name', 'RPC Blank Slots',
            'tournament_date', '2026-07-24',
            'organizer_name', 'Organizer',
            'organizer_contact', '123',
            'status', 'draft'
        ),
        (
            select jsonb_agg(jsonb_build_object(
                'id', ('f0000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
                'tournament_id', 'a0000000-0000-0000-0000-000000000004'::uuid,
                'slot_number', slot_number,
                'team_name', '',
                'status', 'draft'
            ))
            from generate_series(1, 12) as slot_number
        ),
        '[]'::jsonb,
        0
    )
), 'success', 'RPC accepts an initial snapshot with twelve blank slots');
select is((select revision from public.tournaments where id = 'a0000000-0000-0000-0000-000000000004'), 1, 'blank-slot RPC snapshot creates revision one');
select is((
    select count(*)
    from public.tournament_team_slots
    where tournament_id = 'a0000000-0000-0000-0000-000000000004'
), 12::bigint, 'blank-slot RPC snapshot commits all twelve slots');

select is((
    select outcome
    from public.write_tournament_snapshot(
        jsonb_build_object(
            'id', 'a0000000-0000-0000-0000-000000000004'::uuid,
            'owner_id', 'b0000000-0000-0000-0000-000000000001'::uuid,
            'name', 'RPC Blank Slots',
            'tournament_date', '2026-07-24',
            'organizer_name', 'Organizer',
            'organizer_contact', '123',
            'status', 'draft'
        ),
        (
            select jsonb_agg(jsonb_build_object(
                'id', ('f0000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
                'tournament_id', 'a0000000-0000-0000-0000-000000000004'::uuid,
                'slot_number', slot_number,
                'team_name', case slot_number
                    when 1 then 'Alpha'
                    when 2 then 'Bravo'
                    when 3 then 'Charlie'
                    else ''
                end,
                'status', 'draft'
            ))
            from generate_series(1, 12) as slot_number
        ),
        '[]'::jsonb,
        1
    )
), 'success', 'RPC accepts a mixed named and blank snapshot');
select is((
    select count(*)
    from public.tournament_team_slots
    where tournament_id = 'a0000000-0000-0000-0000-000000000004'
      and team_name in ('Alpha', 'Bravo', 'Charlie')
), 3::bigint, 'mixed RPC snapshot stores the three active names');

select * from finish();
rollback;
