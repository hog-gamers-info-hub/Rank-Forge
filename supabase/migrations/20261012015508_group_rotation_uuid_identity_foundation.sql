-- Phase 3D-2A: additive Group Rotation UUID identity foundation.
-- Numeric Standard and legacy Group Rotation tables and RPCs remain unchanged.

create table public.tournament_team_identities (
    tournament_id uuid not null,
    team_uuid uuid not null,
    display_name text not null,
    normalized_name text not null,
    created_at timestamptz not null default timezone('utc'::text, now()),
    updated_at timestamptz not null default timezone('utc'::text, now()),
    constraint tournament_team_identities_pkey
        primary key (tournament_id, team_uuid),
    constraint tournament_team_identities_tournament_fkey
        foreign key (tournament_id)
        references public.tournaments(id)
        on delete cascade,
    constraint tournament_team_identities_display_name_check
        check (
            regexp_replace(
                display_name,
                '^[[:space:]]+|[[:space:]]+$',
                '',
                'g'
            ) <> ''
        ),
    constraint tournament_team_identities_normalized_name_check
        check (
            normalized_name = lower(
                regexp_replace(
                    regexp_replace(
                        display_name,
                        '^[[:space:]]+|[[:space:]]+$',
                        '',
                        'g'
                    ),
                    '[[:space:]]+',
                    ' ',
                    'g'
                )
            )
            and normalized_name <> ''
        ),
    constraint tournament_team_identities_normalized_name_unique
        unique (tournament_id, normalized_name)
);

create table public.tournament_group_pairing_team_assignments (
    tournament_id uuid not null,
    pairing_key text not null,
    lobby_slot_number integer not null,
    team_uuid uuid not null,
    created_at timestamptz not null default timezone('utc'::text, now()),
    constraint tournament_group_pairing_team_assignments_pkey
        primary key (tournament_id, pairing_key, lobby_slot_number),
    constraint tournament_group_pairing_team_assignments_pairing_fkey
        foreign key (tournament_id, pairing_key)
        references public.tournament_group_pairings(tournament_id, pairing_key)
        on delete cascade,
    constraint tournament_group_pairing_team_assignments_team_fkey
        foreign key (tournament_id, team_uuid)
        references public.tournament_team_identities(tournament_id, team_uuid),
    constraint tournament_group_pairing_team_assignments_team_unique
        unique (tournament_id, pairing_key, team_uuid),
    constraint tournament_group_pairing_team_assignments_lobby_check
        check (lobby_slot_number between 1 and 12)
);

create index tournament_group_pairing_team_assignments_team_idx
    on public.tournament_group_pairing_team_assignments(tournament_id, team_uuid);

create or replace function public.prevent_tournament_team_identity_key_change()
returns trigger
language plpgsql
set search_path = pg_catalog, public
as $$
begin
    if new.tournament_id is distinct from old.tournament_id
        or new.team_uuid is distinct from old.team_uuid then
        raise exception 'tournament team identity key is immutable'
            using errcode = '23514';
    end if;
    return new;
end;
$$;

create trigger tournament_team_identities_key_immutable
before update on public.tournament_team_identities
for each row
execute function public.prevent_tournament_team_identity_key_change();

revoke all on function public.prevent_tournament_team_identity_key_change() from public;
revoke all on function public.prevent_tournament_team_identity_key_change() from anon;
revoke all on function public.prevent_tournament_team_identity_key_change() from authenticated;

alter table public.tournament_team_identities enable row level security;
alter table public.tournament_group_pairing_team_assignments enable row level security;

create policy tournament_team_identities_select_owner
on public.tournament_team_identities
for select
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = tournament_team_identities.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy tournament_team_identities_insert_owner
on public.tournament_team_identities
for insert
to authenticated
with check (
    exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = tournament_team_identities.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy tournament_team_identities_update_owner
on public.tournament_team_identities
for update
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = tournament_team_identities.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
)
with check (
    exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = tournament_team_identities.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy tournament_group_pairing_team_assignments_select_owner
on public.tournament_group_pairing_team_assignments
for select
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = tournament_group_pairing_team_assignments.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy tournament_group_pairing_team_assignments_insert_owner
on public.tournament_group_pairing_team_assignments
for insert
to authenticated
with check (
    exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = tournament_group_pairing_team_assignments.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy tournament_group_pairing_team_assignments_update_owner
on public.tournament_group_pairing_team_assignments
for update
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = tournament_group_pairing_team_assignments.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
)
with check (
    exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = tournament_group_pairing_team_assignments.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy tournament_group_pairing_team_assignments_delete_owner
on public.tournament_group_pairing_team_assignments
for delete
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = tournament_group_pairing_team_assignments.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

revoke all on table public.tournament_team_identities from public;
revoke all on table public.tournament_team_identities from anon;
revoke all on table public.tournament_team_identities from authenticated;
grant select, insert, update
    on table public.tournament_team_identities to authenticated;

revoke all on table public.tournament_group_pairing_team_assignments from public;
revoke all on table public.tournament_group_pairing_team_assignments from anon;
revoke all on table public.tournament_group_pairing_team_assignments from authenticated;
grant select, insert, update, delete
    on table public.tournament_group_pairing_team_assignments to authenticated;
