revoke all privileges
on table public.tournament_standings_shares
from anon, authenticated;

grant select
on table public.tournament_standings_shares
to authenticated;

grant insert (tournament_id, standings)
on public.tournament_standings_shares
to authenticated;

grant update (standings, updated_at)
on public.tournament_standings_shares
to authenticated;
