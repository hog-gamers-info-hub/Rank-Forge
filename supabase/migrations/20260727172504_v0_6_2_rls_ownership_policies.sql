create policy tournaments_select_owner
on public.tournaments
for select
to authenticated
using (owner_id = auth.uid());

create policy tournaments_insert_owner
on public.tournaments
for insert
to authenticated
with check (owner_id = auth.uid());

create policy tournaments_update_owner
on public.tournaments
for update
to authenticated
using (owner_id = auth.uid())
with check (owner_id = auth.uid());

create policy tournaments_delete_owner
on public.tournaments
for delete
to authenticated
using (owner_id = auth.uid());

create policy tournament_team_slots_select_owner
on public.tournament_team_slots
for select
to authenticated
using (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = tournament_team_slots.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy tournament_team_slots_insert_owner
on public.tournament_team_slots
for insert
to authenticated
with check (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = tournament_team_slots.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy tournament_team_slots_update_owner
on public.tournament_team_slots
for update
to authenticated
using (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = tournament_team_slots.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
)
with check (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = tournament_team_slots.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy tournament_team_slots_delete_owner
on public.tournament_team_slots
for delete
to authenticated
using (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = tournament_team_slots.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy players_select_owner
on public.players
for select
to authenticated
using (
    exists (
        select 1
        from public.tournament_team_slots team_slot
        join public.tournaments tournament_row on tournament_row.id = team_slot.tournament_id
        where team_slot.id = players.team_slot_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy players_insert_owner
on public.players
for insert
to authenticated
with check (
    exists (
        select 1
        from public.tournament_team_slots team_slot
        join public.tournaments tournament_row on tournament_row.id = team_slot.tournament_id
        where team_slot.id = players.team_slot_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy players_update_owner
on public.players
for update
to authenticated
using (
    exists (
        select 1
        from public.tournament_team_slots team_slot
        join public.tournaments tournament_row on tournament_row.id = team_slot.tournament_id
        where team_slot.id = players.team_slot_id
            and tournament_row.owner_id = auth.uid()
    )
)
with check (
    exists (
        select 1
        from public.tournament_team_slots team_slot
        join public.tournaments tournament_row on tournament_row.id = team_slot.tournament_id
        where team_slot.id = players.team_slot_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy players_delete_owner
on public.players
for delete
to authenticated
using (
    exists (
        select 1
        from public.tournament_team_slots team_slot
        join public.tournaments tournament_row on tournament_row.id = team_slot.tournament_id
        where team_slot.id = players.team_slot_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy matches_select_owner
on public.matches
for select
to authenticated
using (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = matches.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy matches_insert_owner
on public.matches
for insert
to authenticated
with check (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = matches.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy matches_update_owner
on public.matches
for update
to authenticated
using (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = matches.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
)
with check (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = matches.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy matches_delete_owner
on public.matches
for delete
to authenticated
using (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = matches.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy match_results_select_owner
on public.match_results
for select
to authenticated
using (
    exists (
        select 1
        from public.matches match_row
        join public.tournament_team_slots team_slot
            on team_slot.tournament_id = match_row.tournament_id
        join public.tournaments tournament_row on tournament_row.id = match_row.tournament_id
        where match_row.id = match_results.match_id
            and team_slot.id = match_results.team_slot_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy match_results_insert_owner
on public.match_results
for insert
to authenticated
with check (
    exists (
        select 1
        from public.matches match_row
        join public.tournament_team_slots team_slot
            on team_slot.tournament_id = match_row.tournament_id
        join public.tournaments tournament_row on tournament_row.id = match_row.tournament_id
        where match_row.id = match_results.match_id
            and team_slot.id = match_results.team_slot_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy match_results_update_owner
on public.match_results
for update
to authenticated
using (
    exists (
        select 1
        from public.matches match_row
        join public.tournament_team_slots team_slot
            on team_slot.tournament_id = match_row.tournament_id
        join public.tournaments tournament_row on tournament_row.id = match_row.tournament_id
        where match_row.id = match_results.match_id
            and team_slot.id = match_results.team_slot_id
            and tournament_row.owner_id = auth.uid()
    )
)
with check (
    exists (
        select 1
        from public.matches match_row
        join public.tournament_team_slots team_slot
            on team_slot.tournament_id = match_row.tournament_id
        join public.tournaments tournament_row on tournament_row.id = match_row.tournament_id
        where match_row.id = match_results.match_id
            and team_slot.id = match_results.team_slot_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy match_results_delete_owner
on public.match_results
for delete
to authenticated
using (
    exists (
        select 1
        from public.matches match_row
        join public.tournament_team_slots team_slot
            on team_slot.tournament_id = match_row.tournament_id
        join public.tournaments tournament_row on tournament_row.id = match_row.tournament_id
        where match_row.id = match_results.match_id
            and team_slot.id = match_results.team_slot_id
            and tournament_row.owner_id = auth.uid()
    )
);
