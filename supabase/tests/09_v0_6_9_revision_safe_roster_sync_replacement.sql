begin;

select plan(35);

select ok(
    to_regprocedure('public.replace_tournament_roster_snapshot(uuid,jsonb,jsonb,integer)') is not null,
    'roster replacement RPC exists with the approved signature'
);
select ok(
    not has_function_privilege(
        'anon'::name,
        'public.replace_tournament_roster_snapshot(uuid,jsonb,jsonb,integer)'::regprocedure,
        'execute'::text
    ),
    'anonymous callers cannot invoke roster replacement'
);

insert into auth.users (id, email)
values
    ('96000000-0000-0000-0000-000000000001', 'roster-owner@example.test'),
    ('96000000-0000-0000-0000-000000000002', 'roster-other@example.test');

insert into public.tournaments (id, owner_id, name, revision)
values
    ('97000000-0000-0000-0000-000000000001', '96000000-0000-0000-0000-000000000001', 'Roster Cup', 1),
    ('97000000-0000-0000-0000-000000000002', '96000000-0000-0000-0000-000000000002', 'Other Roster Cup', 1);

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
select
    ('98000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
    '97000000-0000-0000-0000-000000000001'::uuid,
    slot_number,
    'Initial Team ' || slot_number
from generate_series(1, 12) as slot_number;

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
select
    ('98100000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
    '97000000-0000-0000-0000-000000000002'::uuid,
    slot_number,
    'Other Team ' || slot_number
from generate_series(1, 12) as slot_number;

insert into public.players (id, team_slot_id, display_name, normalized_name)
values
    ('99000000-0000-0000-0000-000000000001', '98000000-0000-0000-0000-000000000001', 'Old Player', 'Old Player'),
    ('99000000-0000-0000-0000-000000000002', '98000000-0000-0000-0000-000000000002', 'Keep Player', 'Keep Player');

set local role authenticated;
set local request.jwt.claim.sub = '96000000-0000-0000-0000-000000000001';

select is((
    select outcome
    from public.replace_tournament_roster_snapshot(
        '97000000-0000-0000-0000-000000000001',
        (
            select jsonb_agg(jsonb_build_object(
                'id', ('98000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
                'tournament_id', '97000000-0000-0000-0000-000000000001'::uuid,
                'slot_number', slot_number,
                'team_name', 'Replaced Team ' || slot_number,
                'status', 'draft'
            ) order by slot_number)
            from generate_series(1, 12) as slot_number
        ),
        jsonb_build_array(
            jsonb_build_object(
                'id', '99100000-0000-0000-0000-000000000001'::uuid,
                'team_slot_id', '98000000-0000-0000-0000-000000000001'::uuid,
                'display_name', 'New Player',
                'normalized_name', 'New Player'
            ),
            jsonb_build_object(
                'id', '99100000-0000-0000-0000-000000000002'::uuid,
                'team_slot_id', '98000000-0000-0000-0000-000000000002'::uuid,
                'display_name', 'Keep Player',
                'normalized_name', 'Keep Player'
            )
        ),
        1
    )
), 'success', 'owner can replace an existing roster at the current revision');
select is((select count(*) from public.tournament_team_slots where tournament_id = '97000000-0000-0000-0000-000000000001'), 12::bigint, 'replacement keeps exactly twelve target slots');
select is((select count(*) from public.tournament_team_slots where tournament_id = '97000000-0000-0000-0000-000000000001' and team_name like 'Replaced Team %'), 12::bigint, 'replacement updates all target team names');
select is((select count(*) from public.players p join public.tournament_team_slots s on s.id = p.team_slot_id where s.tournament_id = '97000000-0000-0000-0000-000000000001'), 2::bigint, 'replacement stores the supplied players');
select is((select count(*) from public.players where id = '99000000-0000-0000-0000-000000000001'), 0::bigint, 'replacement deletes stale players');
select is((select count(*) from public.players where id in ('99100000-0000-0000-0000-000000000001', '99100000-0000-0000-0000-000000000002')), 2::bigint, 'replacement preserves distinct supplied player identities');
set local request.jwt.claim.sub = '96000000-0000-0000-0000-000000000002';
select is((select count(*) from public.tournament_team_slots where tournament_id = '97000000-0000-0000-0000-000000000002' and team_name like 'Other Team %'), 12::bigint, 'replacement does not touch another tournament');
set local request.jwt.claim.sub = '96000000-0000-0000-0000-000000000001';
select is((select revision from public.tournaments where id = '97000000-0000-0000-0000-000000000001'), 2, 'successful replacement advances the tournament revision once');

select is((
    select outcome
    from public.replace_tournament_roster_snapshot(
        '97000000-0000-0000-0000-000000000001',
        '[]'::jsonb,
        '[]'::jsonb,
        1
    )
), 'stale_write', 'stale replacement is rejected');
select is((select team_name from public.tournament_team_slots where tournament_id = '97000000-0000-0000-0000-000000000001' and slot_number = 1), 'Replaced Team 1', 'stale replacement makes no mutation');

select is((
    select outcome
    from public.replace_tournament_roster_snapshot(
        '97000000-0000-0000-0000-000000000001', '[]'::jsonb, '[]'::jsonb, 0
    )
), 'missing_revision', 'zero expected revision is rejected');
select is((
    select outcome
    from public.replace_tournament_roster_snapshot(
        '97000000-0000-0000-0000-000000000099', '[]'::jsonb, '[]'::jsonb, 1
    )
), 'missing_revision', 'missing tournaments are never created');
select is((select count(*) from public.tournaments where id = '97000000-0000-0000-0000-000000000099'), 0::bigint, 'missing tournament rejection leaves no row');

select is((
    select outcome
    from public.replace_tournament_roster_snapshot(
        '97000000-0000-0000-0000-000000000001',
        (
            select jsonb_agg(jsonb_build_object(
                'id', ('98000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
                'tournament_id', '97000000-0000-0000-0000-000000000001'::uuid,
                'slot_number', slot_number,
                'team_name', 'Incomplete Team ' || slot_number,
                'status', 'draft'
            ) order by slot_number)
            from generate_series(1, 11) as slot_number
        ),
        '[]'::jsonb,
        2
    )
), 'validation_failure', 'incomplete slot payload is rejected');
select is((
    select outcome
    from public.replace_tournament_roster_snapshot(
        '97000000-0000-0000-0000-000000000001',
        (
            select jsonb_agg(jsonb_build_object(
                'id', ('98000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
                'tournament_id', '97000000-0000-0000-0000-000000000001'::uuid,
                'slot_number', case when slot_number = 12 then 1 else slot_number end,
                'team_name', 'Duplicate Team ' || slot_number,
                'status', 'draft'
            ) order by slot_number)
            from generate_series(1, 12) as slot_number
        ),
        '[]'::jsonb,
        2
    )
), 'validation_failure', 'duplicate slot numbers are rejected');
select is((
    select outcome
    from public.replace_tournament_roster_snapshot(
        '97000000-0000-0000-0000-000000000001',
        jsonb_build_array(jsonb_build_object(
            'id', '98100000-0000-0000-0000-000000000001'::uuid,
            'tournament_id', '97000000-0000-0000-0000-000000000002'::uuid,
            'slot_number', 1,
            'team_name', 'Cross Account Team',
            'status', 'draft'
        )),
        '[]'::jsonb,
        2
    )
), 'validation_failure', 'cross-tournament slots are rejected');

insert into public.matches (id, tournament_id, match_number, status)
values ('9a000000-0000-0000-0000-000000000001', '97000000-0000-0000-0000-000000000001', 1, 'draft');
select is((
    select outcome
    from public.replace_tournament_roster_snapshot(
        '97000000-0000-0000-0000-000000000001', '[]'::jsonb, '[]'::jsonb, 2
    )
), 'matches_exist', 'draft matches block replacement');
reset role;
update public.matches set status = 'finalized' where id = '9a000000-0000-0000-0000-000000000001';
set local role authenticated;
set local request.jwt.claim.sub = '96000000-0000-0000-0000-000000000001';
select is((
    select outcome
    from public.replace_tournament_roster_snapshot(
        '97000000-0000-0000-0000-000000000001', '[]'::jsonb, '[]'::jsonb, 2
    )
), 'matches_exist', 'finalized matches also block replacement');
select is((select revision from public.tournaments where id = '97000000-0000-0000-0000-000000000001'), 2, 'match blocking leaves the revision unchanged');

set local request.jwt.claim.sub = '96000000-0000-0000-0000-000000000002';
select is((
    select outcome
    from public.replace_tournament_roster_snapshot(
        '97000000-0000-0000-0000-000000000001', '[]'::jsonb, '[]'::jsonb, 2
    )
), 'missing_revision', 'another account cannot replace the owner roster');

set local request.jwt.claim.sub = '96000000-0000-0000-0000-000000000001';
reset role;
delete from public.matches where id = '9a000000-0000-0000-0000-000000000001';
set local role authenticated;
set local request.jwt.claim.sub = '96000000-0000-0000-0000-000000000001';
select is((
    select outcome
    from public.replace_tournament_roster_snapshot(
        '97000000-0000-0000-0000-000000000001',
        (
            select jsonb_agg(jsonb_build_object(
                'id', ('98000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
                'tournament_id', '97000000-0000-0000-0000-000000000001'::uuid,
                'slot_number', slot_number,
                'team_name', 'Final Team ' || slot_number,
                'status', 'draft'
            ) order by slot_number)
            from generate_series(1, 12) as slot_number
        ),
        jsonb_build_array(jsonb_build_object(
            'id', '99100000-0000-0000-0000-000000000003'::uuid,
            'team_slot_id', '98100000-0000-0000-0000-000000000001'::uuid,
            'display_name', 'Invalid Player',
            'normalized_name', 'Invalid Player'
        )),
        2
    )
), 'validation_failure', 'invalid player ownership is rejected');
select is((select revision from public.tournaments where id = '97000000-0000-0000-0000-000000000001'), 2, 'invalid player rejection rolls back without advancing revision');
select is((
    select count(*)
    from public.tournament_team_slots s
    join generate_series(1, 12) as expected(slot_number)
        on expected.slot_number = s.slot_number
    where s.tournament_id = '97000000-0000-0000-0000-000000000001'
      and s.team_name = 'Replaced Team ' || expected.slot_number
), 12::bigint, 'invalid player rejection preserves all existing team-slot names');
select is((
    select count(*)
    from public.players p
    join public.tournament_team_slots s on s.id = p.team_slot_id
    where s.tournament_id = '97000000-0000-0000-0000-000000000001'
), 2::bigint, 'invalid player rejection preserves the existing player row count');
select is((
    select count(*)
    from public.players
    where id in (
        '99100000-0000-0000-0000-000000000001',
        '99100000-0000-0000-0000-000000000002'
    )
), 2::bigint, 'invalid player rejection does not delete pre-call player rows');
select is((
    select count(*)
    from public.players p
    join public.tournament_team_slots s on s.id = p.team_slot_id
    where s.tournament_id = '97000000-0000-0000-0000-000000000001'
      and (
          (s.slot_number = 1
              and p.id = '99100000-0000-0000-0000-000000000001'::uuid
              and p.display_name = 'New Player'
              and p.normalized_name = 'New Player')
          or (s.slot_number = 2
              and p.id = '99100000-0000-0000-0000-000000000002'::uuid
              and p.display_name = 'Keep Player'
              and p.normalized_name = 'Keep Player')
      )
), 2::bigint, 'invalid player rejection preserves player IDs, names, and slot associations');
select is((select count(*) from public.players where id = '99100000-0000-0000-0000-000000000003'), 0::bigint, 'invalid player rejection inserts no invalid player row');
select is((
    select outcome
    from public.replace_tournament_roster_snapshot(
        '97000000-0000-0000-0000-000000000001',
        (
            select jsonb_agg(jsonb_build_object(
                'id', ('98000000-0000-0000-0000-' || lpad(slot_number::text, 12, '0'))::uuid,
                'tournament_id', '97000000-0000-0000-0000-000000000001'::uuid,
                'slot_number', slot_number,
                'team_name', 'Replaced Team ' || slot_number,
                'status', 'draft'
            ) order by slot_number)
            from generate_series(1, 12) as slot_number
        ),
        jsonb_build_array(
            jsonb_build_object(
                'id', '99100000-0000-0000-0000-000000000001'::uuid,
                'team_slot_id', '98000000-0000-0000-0000-000000000001'::uuid,
                'display_name', 'New Player',
                'normalized_name', 'New Player'
            ),
            jsonb_build_object(
                'id', '99100000-0000-0000-0000-000000000002'::uuid,
                'team_slot_id', '98000000-0000-0000-0000-000000000002'::uuid,
                'display_name', 'Keep Player',
                'normalized_name', 'Keep Player'
            )
        ),
        2
    )
), 'success', 'repeating the current revision with the same roster remains deterministic');
select is((select revision from public.tournaments where id = '97000000-0000-0000-0000-000000000001'), 3, 'repeated current-revision replacement advances exactly once');
select is((
    select count(*)
    from public.tournament_team_slots s
    join generate_series(1, 12) as expected(slot_number)
        on expected.slot_number = s.slot_number
    where s.tournament_id = '97000000-0000-0000-0000-000000000001'
      and s.team_name = 'Replaced Team ' || expected.slot_number
), 12::bigint, 'same-roster replacement preserves the complete team-slot state');
select is((
    select count(*)
    from public.players p
    join public.tournament_team_slots s on s.id = p.team_slot_id
    where s.tournament_id = '97000000-0000-0000-0000-000000000001'
), 2::bigint, 'same-roster replacement keeps exactly the intended player row count');
select is((
    select count(*)
    from public.players p
    join public.tournament_team_slots s on s.id = p.team_slot_id
    where s.tournament_id = '97000000-0000-0000-0000-000000000001'
      and (
          (s.slot_number = 1
              and p.id = '99100000-0000-0000-0000-000000000001'::uuid
              and p.display_name = 'New Player'
              and p.normalized_name = 'New Player')
          or (s.slot_number = 2
              and p.id = '99100000-0000-0000-0000-000000000002'::uuid
              and p.display_name = 'Keep Player'
              and p.normalized_name = 'Keep Player')
      )
), 2::bigint, 'same-roster replacement preserves deterministic player IDs and associations');
select is((
    select count(*)
    from (
        select p.team_slot_id, p.normalized_name
        from public.players p
        join public.tournament_team_slots s on s.id = p.team_slot_id
        where s.tournament_id = '97000000-0000-0000-0000-000000000001'
        group by p.team_slot_id, p.normalized_name
        having count(*) > 1
    ) duplicates
), 0::bigint, 'same-roster replacement creates no duplicate player rows');

select * from finish();
rollback;
