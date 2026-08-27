create table public.tournament_standings_shares (
    tournament_id uuid primary key
        references public.tournaments(id) on delete cascade,
    share_token uuid not null default gen_random_uuid(),
    standings jsonb not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint tournament_standings_shares_share_token_key unique (share_token),
    constraint tournament_standings_shares_standings_array_check check (
        case
            when jsonb_typeof(standings) = 'array'
                then jsonb_array_length(standings) between 0 and 12
            else false
        end
    )
);

alter table public.tournament_standings_shares enable row level security;

revoke all on table public.tournament_standings_shares from public;
revoke all on table public.tournament_standings_shares from anon;
grant select on table public.tournament_standings_shares to authenticated;
grant insert (tournament_id, standings)
    on table public.tournament_standings_shares to authenticated;
grant update (standings, updated_at)
    on table public.tournament_standings_shares to authenticated;

create policy tournament_standings_shares_select_owner
on public.tournament_standings_shares
for select
to authenticated
using (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = tournament_standings_shares.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy tournament_standings_shares_insert_owner
on public.tournament_standings_shares
for insert
to authenticated
with check (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = tournament_standings_shares.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy tournament_standings_shares_update_owner
on public.tournament_standings_shares
for update
to authenticated
using (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = tournament_standings_shares.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
)
with check (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = tournament_standings_shares.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);
