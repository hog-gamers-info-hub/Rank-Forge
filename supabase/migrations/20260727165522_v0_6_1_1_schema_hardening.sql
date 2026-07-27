alter table public.tournaments
    add column revision integer not null default 1,
    add constraint tournaments_revision_check check (revision > 0);

alter table public.tournament_team_slots
    add column revision integer not null default 1,
    add constraint tournament_team_slots_revision_check check (revision > 0),
    add constraint tournament_team_slots_tournament_slot_number_key
        unique (tournament_id, slot_number),
    add constraint tournament_team_slots_tournament_team_name_key
        unique (tournament_id, team_name);

alter table public.players
    add column revision integer not null default 1,
    add constraint players_revision_check check (revision > 0),
    add constraint players_team_slot_normalized_name_key
        unique (team_slot_id, normalized_name);

alter table public.matches
    add column revision integer not null default 1,
    add constraint matches_revision_check check (revision > 0),
    add constraint matches_tournament_match_number_key
        unique (tournament_id, match_number);

alter table public.match_results
    add column revision integer not null default 1,
    add constraint match_results_revision_check check (revision > 0),
    add constraint match_results_match_team_slot_key
        unique (match_id, team_slot_id),
    add constraint match_results_match_placement_key
        unique (match_id, placement);

create index idx_tournaments_owner_id on public.tournaments (owner_id);
create index idx_match_results_team_slot_id on public.match_results (team_slot_id);
