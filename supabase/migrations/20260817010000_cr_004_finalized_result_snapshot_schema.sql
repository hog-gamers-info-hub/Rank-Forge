-- CR-004: additive finalized result snapshot schema.
-- Existing match_results rows remain valid with NULL snapshot columns.

alter table public.match_results
    add column team_slot_number_snapshot integer,
    add column team_name_snapshot text,
    add column placement_points integer,
    add column kill_points integer,
    add column total_points integer;

alter table public.match_results
    add constraint match_results_team_slot_number_snapshot_check
        check (team_slot_number_snapshot is null or team_slot_number_snapshot between 1 and 12),
    add constraint match_results_team_name_snapshot_check
        check (team_name_snapshot is null or btrim(team_name_snapshot) <> ''),
    add constraint match_results_placement_points_check
        check (placement_points is null or placement_points >= 0),
    add constraint match_results_kill_points_check
        check (kill_points is null or kill_points >= 0),
    add constraint match_results_total_points_check
        check (total_points is null or total_points >= 0),
    add constraint match_results_total_points_sum_check
        check (
            total_points is null
            or placement_points is null
            or kill_points is null
            or total_points = placement_points + kill_points
        );

create table public.match_result_players (
    id uuid primary key,
    match_result_id uuid not null references public.match_results(id) on delete cascade,
    player_id uuid references public.players(id) on delete set null,
    roster_position_snapshot integer not null,
    player_name_snapshot text not null,
    created_at timestamptz not null default now(),
    constraint match_result_players_roster_position_snapshot_check
        check (roster_position_snapshot > 0),
    constraint match_result_players_player_name_snapshot_check
        check (btrim(player_name_snapshot) <> ''),
    constraint match_result_players_result_roster_position_key
        unique (match_result_id, roster_position_snapshot)
);

create index match_result_players_player_id_idx
    on public.match_result_players (player_id);

create unique index match_result_players_result_player_id_uidx
    on public.match_result_players (match_result_id, player_id)
    where player_id is not null;

alter table public.match_result_players enable row level security;

revoke all privileges on table public.match_result_players from anon;
revoke all privileges on table public.match_result_players from authenticated;
grant select on table public.match_result_players to authenticated;

create policy match_result_players_select_owner
on public.match_result_players
for select
to authenticated
using (
    exists (
        select 1
        from public.match_results as result_row
        join public.matches as match_row on match_row.id = result_row.match_id
        join public.tournaments as tournament_row on tournament_row.id = match_row.tournament_id
        where result_row.id = match_result_players.match_result_id
          and tournament_row.owner_id = (select auth.uid())
    )
);
