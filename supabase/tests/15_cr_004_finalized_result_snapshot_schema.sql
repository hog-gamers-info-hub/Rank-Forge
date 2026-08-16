begin;

select plan(20);

select ok(
    exists (
        select 1
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'match_results'
          and column_name = 'team_slot_number_snapshot'
    )
    and exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'match_results'
          and column_name = 'team_name_snapshot'
    )
    and exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'match_results'
          and column_name = 'placement_points'
    )
    and exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'match_results'
          and column_name = 'kill_points'
    )
    and exists (
        select 1 from information_schema.columns
        where table_schema = 'public' and table_name = 'match_results'
          and column_name = 'total_points'
    ),
    'match_results has all five CR-004 snapshot columns'
);

select is(
    (
        select count(*)
        from information_schema.columns
        where table_schema = 'public'
          and table_name = 'match_results'
          and column_name in (
              'team_slot_number_snapshot',
              'team_name_snapshot',
              'placement_points',
              'kill_points',
              'total_points'
          )
          and is_nullable = 'YES'
    ),
    5::bigint,
    'all CR-004 match_results columns are nullable for legacy compatibility'
);

select ok(to_regclass('public.match_result_players') is not null, 'match_result_players table exists');
select ok(
    (select relrowsecurity from pg_class where oid = 'public.match_result_players'::regclass),
    'match_result_players has RLS enabled'
);
select ok(
    has_table_privilege('authenticated', 'public.match_result_players', 'select'),
    'authenticated can select match result player snapshots'
);
select ok(
    not has_table_privilege('authenticated', 'public.match_result_players', 'insert'),
    'authenticated cannot directly insert match result player snapshots'
);
select ok(
    not has_table_privilege('authenticated', 'public.match_result_players', 'update'),
    'authenticated cannot directly update match result player snapshots'
);
select ok(
    not has_table_privilege('authenticated', 'public.match_result_players', 'delete'),
    'authenticated cannot directly delete match result player snapshots'
);
select ok(
    not has_table_privilege('anon', 'public.match_result_players', 'select'),
    'anonymous callers cannot select match result player snapshots'
);
select ok(
    position(
        'ON DELETE CASCADE' in (
            select pg_get_constraintdef(oid)
            from pg_constraint
            where conname = 'match_result_players_match_result_id_fkey'
        )
    ) > 0,
    'match_result_players cascades when its match result is deleted'
);
select ok(
    position(
        'ON DELETE SET NULL' in (
            select pg_get_constraintdef(oid)
            from pg_constraint
            where conname = 'match_result_players_player_id_fkey'
        )
    ) > 0,
    'deleting a live roster player preserves the historical player snapshot'
);
select ok(
    exists (
        select 1 from pg_constraint
        where conname = 'match_result_players_result_roster_position_key'
    ),
    'one roster position is unique within each match result snapshot'
);

insert into auth.users (id, email)
values
    ('95000000-0000-0000-0000-000000000001', 'cr004-schema-owner@example.test'),
    ('95000000-0000-0000-0000-000000000002', 'cr004-schema-other@example.test');

insert into public.tournaments (id, owner_id, name, revision)
values (
    '95100000-0000-0000-0000-000000000001',
    '95000000-0000-0000-0000-000000000001',
    'CR004 Schema Cup',
    1
);

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
values (
    '95200000-0000-0000-0000-000000000001',
    '95100000-0000-0000-0000-000000000001',
    1,
    'Legacy Team'
);

insert into public.players (id, team_slot_id, display_name, normalized_name)
values (
    '95300000-0000-0000-0000-000000000001',
    '95200000-0000-0000-0000-000000000001',
    'Legacy Player',
    'legacy player'
);

insert into public.matches (id, tournament_id, match_number, status)
values (
    '95400000-0000-0000-0000-000000000001',
    '95100000-0000-0000-0000-000000000001',
    1,
    'finalized'
);

insert into public.match_results (
    id, match_id, team_slot_id, placement, kills, source, review_status
) values (
    '95500000-0000-0000-0000-000000000001',
    '95400000-0000-0000-0000-000000000001',
    '95200000-0000-0000-0000-000000000001',
    1,
    0,
    'manual',
    'confirmed'
);

select ok(
    (
        select team_slot_number_snapshot is null
           and team_name_snapshot is null
           and placement_points is null
           and kill_points is null
           and total_points is null
        from public.match_results
        where id = '95500000-0000-0000-0000-000000000001'
    ),
    'legacy finalized result rows remain valid without fabricated CR-004 snapshots'
);

insert into public.match_result_players (
    id, match_result_id, player_id, roster_position_snapshot, player_name_snapshot
) values (
    '95600000-0000-0000-0000-000000000001',
    '95500000-0000-0000-0000-000000000001',
    '95300000-0000-0000-0000-000000000001',
    1,
    'Legacy Player'
);

select throws_ok($$
    insert into public.match_result_players (
        id, match_result_id, roster_position_snapshot, player_name_snapshot
    ) values (
        '95600000-0000-0000-0000-000000000002',
        '95500000-0000-0000-0000-000000000001',
        1,
        'Duplicate Position'
    )
$$, '23505', null, 'duplicate roster positions are rejected per match result');

delete from public.players
where id = '95300000-0000-0000-0000-000000000001';
select ok(
    (select player_id is null from public.match_result_players where id = '95600000-0000-0000-0000-000000000001'),
    'live player deletion nulls only the optional player reference'
);
select is(
    (select player_name_snapshot from public.match_result_players where id = '95600000-0000-0000-0000-000000000001'),
    'Legacy Player',
    'live player deletion preserves the frozen player name'
);

set local role authenticated;
set local request.jwt.claim.sub = '95000000-0000-0000-0000-000000000001';
select is(
    (select count(*) from public.match_result_players),
    1::bigint,
    'owner can read own player snapshots through RLS'
);

select throws_ok($$
    insert into public.match_result_players (
        id, match_result_id, roster_position_snapshot, player_name_snapshot
    ) values (
        '95600000-0000-0000-0000-000000000003',
        '95500000-0000-0000-0000-000000000001',
        2,
        'Blocked Direct Write'
    )
$$, '42501', null, 'authenticated owner cannot bypass protected RPC with a direct insert');

set local request.jwt.claim.sub = '95000000-0000-0000-0000-000000000002';
select is(
    (select count(*) from public.match_result_players),
    0::bigint,
    'different user cannot read another owner player snapshots'
);

set local role postgres;
delete from public.match_results
where id = '95500000-0000-0000-0000-000000000001';
select is(
    (select count(*) from public.match_result_players where id = '95600000-0000-0000-0000-000000000001'),
    0::bigint,
    'deleting the parent match result cascades player snapshots'
);

select * from finish();
rollback;
