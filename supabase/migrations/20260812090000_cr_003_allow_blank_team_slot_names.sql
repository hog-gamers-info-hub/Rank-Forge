alter table public.tournament_team_slots
    drop constraint if exists tournament_team_slots_tournament_team_name_key;

create unique index tournament_team_slots_tournament_nonblank_team_name_uidx
    on public.tournament_team_slots (tournament_id, team_name)
    where team_name is not null
      and btrim(team_name) <> '';
