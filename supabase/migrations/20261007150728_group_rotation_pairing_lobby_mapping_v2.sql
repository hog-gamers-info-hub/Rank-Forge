-- Phase 4A: additive Group Rotation V2 cloud identity foundation.
-- The existing snapshot/match/finalization RPCs remain unchanged.

create table public.tournament_group_pairing_lobby_slots (
    tournament_id uuid not null,
    pairing_key text not null,
    lobby_slot_number integer not null,
    team_slot_number integer not null,
    created_at timestamptz not null default timezone('utc', now()),
    constraint tournament_group_pairing_lobby_slots_pkey
        primary key (tournament_id, pairing_key, lobby_slot_number),
    constraint tournament_group_pairing_lobby_slots_team_unique
        unique (tournament_id, pairing_key, team_slot_number),
    constraint tournament_group_pairing_lobby_slots_lobby_check
        check (lobby_slot_number between 1 and 12),
    constraint tournament_group_pairing_lobby_slots_team_check
        check (team_slot_number between 1 and 24),
    constraint tournament_group_pairing_lobby_slots_pairing_fkey
        foreign key (tournament_id, pairing_key)
        references public.tournament_group_pairings(tournament_id, pairing_key)
        on delete cascade,
    constraint tournament_group_pairing_lobby_slots_team_slot_fkey
        foreign key (tournament_id, team_slot_number)
        references public.tournament_team_slots(tournament_id, slot_number)
        on delete cascade
);

alter table public.tournament_group_pairing_lobby_slots enable row level security;

create policy tournament_group_pairing_lobby_slots_select_owner
on public.tournament_group_pairing_lobby_slots
for select
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = tournament_group_pairing_lobby_slots.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy tournament_group_pairing_lobby_slots_insert_owner
on public.tournament_group_pairing_lobby_slots
for insert
to authenticated
with check (
    exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = tournament_group_pairing_lobby_slots.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy tournament_group_pairing_lobby_slots_update_owner
on public.tournament_group_pairing_lobby_slots
for update
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = tournament_group_pairing_lobby_slots.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
)
with check (
    exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = tournament_group_pairing_lobby_slots.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy tournament_group_pairing_lobby_slots_delete_owner
on public.tournament_group_pairing_lobby_slots
for delete
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = tournament_group_pairing_lobby_slots.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

revoke all on public.tournament_group_pairing_lobby_slots from public;
revoke all on public.tournament_group_pairing_lobby_slots from anon;
revoke all on public.tournament_group_pairing_lobby_slots from authenticated;
grant select, insert, update, delete on public.tournament_group_pairing_lobby_slots to authenticated;

alter table public.match_ocr_row_evidence
    drop constraint if exists match_ocr_row_evidence_team_slot_check;
alter table public.match_ocr_row_evidence
    add constraint match_ocr_row_evidence_team_slot_check
        check (original_suggested_team_slot is null or original_suggested_team_slot between 1 and 24);

alter table public.match_ocr_correction_snapshots
    drop constraint if exists match_ocr_correction_snapshots_team_slot_check;
alter table public.match_ocr_correction_snapshots
    add constraint match_ocr_correction_snapshots_team_slot_check
        check (corrected_team_slot between 1 and 24);

alter table public.match_results
    drop constraint if exists match_results_participation_values_check;
alter table public.match_results
    add constraint match_results_participation_values_check
    check (
        (participation_status = 'PARTICIPATED' and kills >= 0)
        or
        (participation_status = 'NO_SHOW' and placement is null and kills = 0)
    );

create or replace function public.write_tournament_snapshot_v2(
    p_tournament jsonb,
    p_team_slots jsonb,
    p_players jsonb,
    p_pairing_lobby_slots jsonb,
    p_expected_revision integer
)
returns table (outcome text, revision integer)
language plpgsql
security invoker
set search_path = pg_catalog, public
as $$
declare
    v_tournament_id uuid;
    v_owner_id uuid;
    v_current_owner_id uuid;
    v_format text;
    v_group_count integer;
    v_pairings jsonb;
    v_mappings jsonb;
    v_expected_slot_count integer;
    v_current_revision integer;
    v_existing_format text;
    v_existing_group_count integer;
    v_mapping_count integer;
    v_mapping_changed boolean := false;
begin
    if auth.uid() is null then
        return query select 'authentication_required'::text, null::integer;
        return;
    end if;

    if p_expected_revision is null or p_expected_revision < 0 then
        return query select 'missing_revision'::text, null::integer;
        return;
    end if;

    if coalesce(jsonb_typeof(p_tournament), '') <> 'object'
        or coalesce(jsonb_typeof(p_tournament -> 'id'), '') <> 'string'
        or coalesce(jsonb_typeof(p_tournament -> 'owner_id'), '') <> 'string'
        or (p_tournament ->> 'id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        or (p_tournament ->> 'owner_id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    v_tournament_id := (p_tournament ->> 'id')::uuid;
    v_owner_id := (p_tournament ->> 'owner_id')::uuid;
    v_format := p_tournament ->> 'format';
    v_pairings := coalesce(p_tournament -> 'selected_group_pairings', '[]'::jsonb);
    v_mappings := coalesce(p_pairing_lobby_slots, '[]'::jsonb);

    if v_owner_id is distinct from auth.uid()
        or v_format <> 'group_rotation'
        or coalesce(jsonb_typeof(p_tournament -> 'group_count'), '') <> 'number'
        or (p_tournament ->> 'group_count') !~ '^[34]$'
        or coalesce(jsonb_typeof(v_pairings), '') <> 'array'
        or jsonb_array_length(v_pairings) < 1
        or coalesce(jsonb_typeof(p_team_slots), '') <> 'array'
        or coalesce(jsonb_typeof(p_players), '') <> 'array'
        or coalesce(jsonb_typeof(v_mappings), '') <> 'array' then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    v_group_count := (p_tournament ->> 'group_count')::integer;

    v_expected_slot_count := v_group_count * 6;

    if jsonb_array_length(p_team_slots) <> v_expected_slot_count then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_team_slots) as slot_row
        where coalesce(jsonb_typeof(slot_row), '') <> 'object'
            or coalesce(jsonb_typeof(slot_row -> 'id'), '') <> 'string'
            or coalesce(jsonb_typeof(slot_row -> 'tournament_id'), '') <> 'string'
            or coalesce(jsonb_typeof(slot_row -> 'slot_number'), '') <> 'number'
            or coalesce(jsonb_typeof(slot_row -> 'team_name'), '') <> 'string'
            or coalesce(jsonb_typeof(slot_row -> 'status'), '') <> 'string'
            or ((slot_row ? 'group') and jsonb_typeof(slot_row -> 'group') not in ('string', 'null'))
    ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_team_slots) as slot_row
        where (slot_row ->> 'id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (slot_row ->> 'tournament_id') <> v_tournament_id::text
            or (slot_row ->> 'slot_number') !~ '^([1-9]|1[0-9]|2[0-4])$'
            or (slot_row ->> 'status') <> 'draft'
            or (slot_row ->> 'group') is distinct from case
                when (slot_row ->> 'slot_number')::integer between 1 and 6 then 'A'
                when (slot_row ->> 'slot_number')::integer between 7 and 12 then 'B'
                when (slot_row ->> 'slot_number')::integer between 13 and 18 then 'C'
                else 'D'
            end
    ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if (
        select count(distinct slot_row ->> 'slot_number')
        from jsonb_array_elements(p_team_slots) as slot_row
    ) <> v_expected_slot_count
        or exists (
            select 1
            from generate_series(1, v_expected_slot_count) as expected_slot(slot_number)
            where not exists (
                select 1
                from jsonb_array_elements(p_team_slots) as slot_row
                where slot_row ->> 'slot_number' = expected_slot.slot_number::text
            )
        )
        or (
            select count(distinct slot_row ->> 'id')
            from jsonb_array_elements(p_team_slots) as slot_row
        ) <> v_expected_slot_count
        or exists (
            select 1
            from jsonb_array_elements(p_team_slots) as slot_row
            where btrim(slot_row ->> 'team_name') <> ''
            group by btrim(slot_row ->> 'team_name')
            having count(*) > 1
        ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(v_pairings) as pairing_row
        where coalesce(jsonb_typeof(pairing_row), '') <> 'object'
            or coalesce(jsonb_typeof(pairing_row -> 'first_group'), '') <> 'string'
            or coalesce(jsonb_typeof(pairing_row -> 'second_group'), '') <> 'string'
            or coalesce(jsonb_typeof(pairing_row -> 'pairing_key'), '') <> 'string'
            or (pairing_row ->> 'first_group') not in ('A', 'B', 'C', 'D')
            or (pairing_row ->> 'second_group') not in ('A', 'B', 'C', 'D')
            or (pairing_row ->> 'first_group') >= (pairing_row ->> 'second_group')
            or (pairing_row ->> 'pairing_key') <> (pairing_row ->> 'first_group') || ':' || (pairing_row ->> 'second_group')
            or (v_group_count = 3 and ((pairing_row ->> 'first_group') = 'D' or (pairing_row ->> 'second_group') = 'D'))
    ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if (
        select count(distinct pairing_row ->> 'pairing_key')
        from jsonb_array_elements(v_pairings) as pairing_row
    ) <> jsonb_array_length(v_pairings) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if coalesce(jsonb_array_length(v_mappings), 0) > 0
        and jsonb_array_length(v_mappings) <> jsonb_array_length(v_pairings) * 12 then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(v_mappings) as mapping_row
        where coalesce(jsonb_typeof(mapping_row), '') <> 'object'
            or coalesce(jsonb_typeof(mapping_row -> 'tournament_id'), '') <> 'string'
            or coalesce(jsonb_typeof(mapping_row -> 'pairing_key'), '') <> 'string'
            or coalesce(jsonb_typeof(mapping_row -> 'lobby_slot_number'), '') <> 'number'
            or coalesce(jsonb_typeof(mapping_row -> 'team_slot_number'), '') <> 'number'
            or (mapping_row ->> 'tournament_id') <> v_tournament_id::text
            or (mapping_row ->> 'lobby_slot_number') !~ '^(1[0-2]|[1-9])$'
            or (mapping_row ->> 'team_slot_number') !~ '^([1-9]|1[0-9]|2[0-4])$'
            or not exists (
                select 1
                from generate_series(1, v_expected_slot_count) as expected_slot(slot_number)
                where mapping_row ->> 'team_slot_number' = expected_slot.slot_number::text
            )
            or not exists (
                select 1
                from jsonb_to_recordset(v_pairings) as selected_pairing(
                    first_group text, second_group text, pairing_key text
                )
                where selected_pairing.pairing_key = mapping_row ->> 'pairing_key'
            )
    ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    v_mapping_count := jsonb_array_length(v_mappings);
    if v_mapping_count > 0 then
        if exists (
            select 1
            from jsonb_to_recordset(v_pairings) as selected_pairing(
                first_group text, second_group text, pairing_key text
            )
            where (
                select count(*)
                from jsonb_to_recordset(v_mappings) as mapping_row(
                    tournament_id uuid, pairing_key text, lobby_slot_number integer, team_slot_number integer
                )
                where mapping_row.pairing_key = selected_pairing.pairing_key
            ) <> 12
            or exists (
                select 1
                from generate_series(1, 12) as lobby_slot(lobby_slot_number)
                where not exists (
                    select 1
                    from jsonb_to_recordset(v_mappings) as mapping_row(
                        tournament_id uuid, pairing_key text, lobby_slot_number integer, team_slot_number integer
                    )
                    where mapping_row.pairing_key = selected_pairing.pairing_key
                        and mapping_row.lobby_slot_number = lobby_slot.lobby_slot_number
                )
            )
        )
        or exists (
            select 1
            from jsonb_to_recordset(v_mappings) as mapping_row(
                tournament_id uuid, pairing_key text, lobby_slot_number integer, team_slot_number integer
            )
            group by mapping_row.pairing_key, mapping_row.lobby_slot_number
            having count(*) > 1
        )
        or exists (
            select 1
            from jsonb_to_recordset(v_mappings) as mapping_row(
                tournament_id uuid, pairing_key text, lobby_slot_number integer, team_slot_number integer
            )
            group by mapping_row.pairing_key, mapping_row.team_slot_number
            having count(*) > 1
        )
        or exists (
            select 1
            from jsonb_to_recordset(v_mappings) as mapping_row(
                tournament_id uuid, pairing_key text, lobby_slot_number integer, team_slot_number integer
            )
            left join jsonb_to_recordset(p_team_slots) as slot_row(
                id uuid, tournament_id uuid, slot_number integer, team_name text, status text, "group" text
            ) on slot_row.tournament_id = v_tournament_id
                and slot_row.slot_number = mapping_row.team_slot_number
            where slot_row.slot_number is null
                or btrim(coalesce(slot_row.team_name, '')) = ''
        ) then
            return query select 'validation_failure'::text, null::integer;
            return;
        end if;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_players) as player_row
        where coalesce(jsonb_typeof(player_row), '') <> 'object'
            or coalesce(jsonb_typeof(player_row -> 'id'), '') <> 'string'
            or coalesce(jsonb_typeof(player_row -> 'team_slot_id'), '') <> 'string'
            or coalesce(jsonb_typeof(player_row -> 'display_name'), '') <> 'string'
            or coalesce(jsonb_typeof(player_row -> 'normalized_name'), '') <> 'string'
            or (player_row ->> 'id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (player_row ->> 'team_slot_id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or btrim(player_row ->> 'display_name') = ''
            or (player_row ->> 'normalized_name') <> btrim(player_row ->> 'display_name')
            or not exists (
                select 1
                from jsonb_to_recordset(p_team_slots) as slot_row(
                    id uuid, tournament_id uuid, slot_number integer, team_name text, status text, "group" text
                )
                where slot_row.id::text = player_row ->> 'team_slot_id'
            )
    ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if (
        select count(distinct player_row ->> 'id')
        from jsonb_array_elements(p_players) as player_row
    ) <> jsonb_array_length(p_players)
        or exists (
            select 1
            from jsonb_array_elements(p_players) as player_row
            group by player_row ->> 'team_slot_id', player_row ->> 'normalized_name'
            having count(*) > 1
        )
        or exists (
            select 1
            from jsonb_array_elements(p_players) as player_row
            group by player_row ->> 'team_slot_id'
            having count(*) > 6
    )
    or exists (
        select 1
        from jsonb_array_elements(p_players) as incoming_player
        join public.players as existing_player
            on existing_player.id::text = incoming_player ->> 'id'
        join public.tournament_team_slots as existing_slot
            on existing_slot.id = existing_player.team_slot_id
        where existing_slot.tournament_id <> v_tournament_id
        ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    select t.owner_id, t.revision, t.format, t.group_count
    into v_current_owner_id, v_current_revision, v_existing_format, v_existing_group_count
    from public.tournaments as t
    where t.id = v_tournament_id
    for update;

    if not found then
        if p_expected_revision <> 0 then
            return query select 'missing_revision'::text, null::integer;
            return;
        end if;
        insert into public.tournaments (
            id, owner_id, name, organizer_name, organizer_contact, status,
            format, group_count, revision
        ) values (
            v_tournament_id, v_owner_id, p_tournament ->> 'name',
            p_tournament ->> 'organizer_name', p_tournament ->> 'organizer_contact',
            p_tournament ->> 'status', v_format, v_group_count, 1
        );
        v_current_revision := 1;
    else
        if v_current_owner_id is distinct from auth.uid()
            or v_existing_format <> 'group_rotation'
            or v_existing_group_count <> v_group_count then
            return query select 'unauthorized'::text, v_current_revision;
            return;
        end if;
        if p_expected_revision <> v_current_revision then
            return query select 'stale_write'::text, v_current_revision;
            return;
        end if;

        if exists (
            select 1
            from public.tournament_group_pairings as existing_pairing
            where existing_pairing.tournament_id = v_tournament_id
                and not exists (
                    select 1
                    from jsonb_to_recordset(v_pairings) as incoming_pairing(
                        first_group text, second_group text, pairing_key text
                    )
                    where incoming_pairing.first_group = existing_pairing.first_group
                        and incoming_pairing.second_group = existing_pairing.second_group
                )
                and exists (
                    select 1
                    from public.matches as existing_match
                    where existing_match.tournament_id = v_tournament_id
                        and existing_match.group_pairing_key = existing_pairing.pairing_key
                )
        ) then
            return query select 'validation_failure'::text, v_current_revision;
            return;
        end if;

        v_mapping_changed :=
            exists (
                select 1
                from public.tournament_group_pairing_lobby_slots as existing_mapping
                where existing_mapping.tournament_id = v_tournament_id
                    and not exists (
                        select 1
                        from jsonb_to_recordset(v_mappings) as incoming_mapping(
                            tournament_id uuid, pairing_key text,
                            lobby_slot_number integer, team_slot_number integer
                        )
                        where incoming_mapping.pairing_key = existing_mapping.pairing_key
                            and incoming_mapping.lobby_slot_number = existing_mapping.lobby_slot_number
                            and incoming_mapping.team_slot_number = existing_mapping.team_slot_number
                    )
            )
            or exists (
                select 1
                from jsonb_to_recordset(v_mappings) as incoming_mapping(
                    tournament_id uuid, pairing_key text,
                    lobby_slot_number integer, team_slot_number integer
                )
                where not exists (
                    select 1
                    from public.tournament_group_pairing_lobby_slots as existing_mapping
                    where existing_mapping.tournament_id = v_tournament_id
                        and existing_mapping.pairing_key = incoming_mapping.pairing_key
                        and existing_mapping.lobby_slot_number = incoming_mapping.lobby_slot_number
                        and existing_mapping.team_slot_number = incoming_mapping.team_slot_number
                )
            );

        if v_mapping_changed
            and (
                exists (
                    select 1
                    from public.matches
                    where tournament_id = v_tournament_id
                )
                or exists (
                    select 1
                    from public.players as player_row
                    join public.tournament_team_slots as slot_row
                        on slot_row.id = player_row.team_slot_id
                    where slot_row.tournament_id = v_tournament_id
                )
            ) then
            return query select 'validation_failure'::text, v_current_revision;
            return;
        end if;

        update public.tournaments as tournament_row
        set name = p_tournament ->> 'name',
            organizer_name = p_tournament ->> 'organizer_name',
            organizer_contact = p_tournament ->> 'organizer_contact',
            status = p_tournament ->> 'status',
            revision = tournament_row.revision + 1,
            updated_at = now()
        where tournament_row.id = v_tournament_id;
        v_current_revision := v_current_revision + 1;
    end if;

    insert into public.tournament_team_slots as team_slot_target (
        id, tournament_id, slot_number, team_name, status, "group"
    )
    select id, tournament_id, slot_number, team_name, status, "group"
    from jsonb_to_recordset(p_team_slots) as slot_row(
        id uuid, tournament_id uuid, slot_number integer, team_name text, status text, "group" text
    )
    on conflict (id) do update
    set team_name = excluded.team_name,
        status = excluded.status,
        "group" = excluded."group",
        revision = team_slot_target.revision + 1,
        updated_at = now();

    insert into public.players as player_target (
        id, team_slot_id, display_name, normalized_name
    )
    select id, team_slot_id, display_name, normalized_name
    from jsonb_to_recordset(p_players) as player_row(
        id uuid, team_slot_id uuid, display_name text, normalized_name text
    )
    on conflict (id) do update
    set display_name = excluded.display_name,
        normalized_name = excluded.normalized_name,
        revision = player_target.revision + 1,
        updated_at = now();

    delete from public.tournament_group_pairing_lobby_slots
    where tournament_id = v_tournament_id;

    insert into public.tournament_group_pairings (tournament_id, first_group, second_group)
    select v_tournament_id, first_group, second_group
    from jsonb_to_recordset(v_pairings) as pairing_row(
        first_group text, second_group text, pairing_key text
    )
    on conflict (tournament_id, first_group, second_group) do nothing;

    delete from public.tournament_group_pairings as stale_pairing
    where stale_pairing.tournament_id = v_tournament_id
        and not exists (
            select 1
            from jsonb_to_recordset(v_pairings) as incoming_pairing(
                first_group text, second_group text, pairing_key text
            )
            where incoming_pairing.first_group = stale_pairing.first_group
                and incoming_pairing.second_group = stale_pairing.second_group
        );

    insert into public.tournament_group_pairing_lobby_slots (
        tournament_id, pairing_key, lobby_slot_number, team_slot_number
    )
    select tournament_id, pairing_key, lobby_slot_number, team_slot_number
    from jsonb_to_recordset(v_mappings) as mapping_row(
        tournament_id uuid, pairing_key text, lobby_slot_number integer, team_slot_number integer
    );

    return query select 'success'::text, v_current_revision;
end;
$$;

create or replace function public.write_match_snapshot_v2(
    p_tournament_id uuid,
    p_matches jsonb,
    p_match_results jsonb,
    p_expected_revision integer
)
returns table (outcome text, revision integer)
language plpgsql
security invoker
set search_path = pg_catalog, public
as $$
declare
    v_owner_id uuid;
    v_current_revision integer;
begin
    if auth.uid() is null then
        return query select 'authentication_required'::text, null::integer;
        return;
    end if;

    if p_expected_revision is null or p_expected_revision <= 0 then
        return query select 'missing_revision'::text, null::integer;
        return;
    end if;

    if coalesce(jsonb_typeof(p_matches), '') <> 'array'
        or coalesce(jsonb_typeof(p_match_results), '') <> 'array' then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    select tournament_row.owner_id, tournament_row.revision
    into v_owner_id, v_current_revision
    from public.tournaments as tournament_row
    where tournament_row.id = p_tournament_id
        and tournament_row.format = 'group_rotation'
    for update;

    if not found then
        return query select 'missing_revision'::text, null::integer;
        return;
    end if;
    if v_owner_id is distinct from auth.uid() then
        return query select 'unauthorized'::text, v_current_revision;
        return;
    end if;
    if p_expected_revision <> v_current_revision then
        return query select 'stale_write'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_matches) as match_row
        where coalesce(jsonb_typeof(match_row), '') <> 'object'
            or coalesce(jsonb_typeof(match_row -> 'id'), '') <> 'string'
            or coalesce(jsonb_typeof(match_row -> 'tournament_id'), '') <> 'string'
            or coalesce(jsonb_typeof(match_row -> 'match_number'), '') <> 'number'
            or coalesce(jsonb_typeof(match_row -> 'status'), '') <> 'string'
            or coalesce(jsonb_typeof(match_row -> 'group_pairing_key'), '') <> 'string'
            or (match_row ->> 'id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (match_row ->> 'tournament_id') <> p_tournament_id::text
            or (match_row ->> 'match_number') !~ '^([1-9]|1[0-8])$'
            or (match_row ->> 'status') <> 'draft'
            or btrim(match_row ->> 'group_pairing_key') = ''
            or exists (
                select 1
                from public.tournament_group_pairings as pairing_row
                where pairing_row.tournament_id = p_tournament_id
                    and pairing_row.pairing_key = match_row ->> 'group_pairing_key'
            ) is not true
    )
    or (
        select count(distinct match_row ->> 'id')
        from jsonb_array_elements(p_matches) as match_row
    ) <> jsonb_array_length(p_matches)
    or (
        select count(distinct match_row ->> 'match_number')
        from jsonb_array_elements(p_matches) as match_row
    ) <> jsonb_array_length(p_matches)
    or exists (
        select 1
        from jsonb_array_elements(p_matches) as match_row
        join public.matches as existing_match
            on existing_match.id::text = match_row ->> 'id'
        where existing_match.tournament_id <> p_tournament_id
            or existing_match.status = 'finalized'
            or existing_match.group_pairing_key is distinct from match_row ->> 'group_pairing_key'
    )
    or exists (
        select 1
        from jsonb_array_elements(p_matches) as match_row
        join public.matches as conflicting_match
            on conflicting_match.tournament_id = p_tournament_id
            and conflicting_match.match_number::text = match_row ->> 'match_number'
            and conflicting_match.id::text <> match_row ->> 'id'
    )
    or exists (
        select 1
        from jsonb_array_elements(p_matches) as match_row
        where not exists (
            select 1
            from public.tournament_group_pairing_lobby_slots as mapping_row
            where mapping_row.tournament_id = p_tournament_id
                and mapping_row.pairing_key = match_row ->> 'group_pairing_key'
        )
        or (
            select count(*)
            from public.tournament_group_pairing_lobby_slots as mapping_row
            where mapping_row.tournament_id = p_tournament_id
                and mapping_row.pairing_key = match_row ->> 'group_pairing_key'
        ) <> 12
        or (
            select count(distinct mapping_row.team_slot_number)
            from public.tournament_group_pairing_lobby_slots as mapping_row
            where mapping_row.tournament_id = p_tournament_id
                and mapping_row.pairing_key = match_row ->> 'group_pairing_key'
        ) <> 12
        or exists (
            select 1
            from public.tournament_group_pairing_lobby_slots as mapping_row
            join public.tournament_team_slots as team_slot
                on team_slot.tournament_id = mapping_row.tournament_id
                and team_slot.slot_number = mapping_row.team_slot_number
            where mapping_row.tournament_id = p_tournament_id
                and mapping_row.pairing_key = match_row ->> 'group_pairing_key'
                and btrim(coalesce(team_slot.team_name, '')) = ''
        )
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_match_results) as result_row
        where coalesce(jsonb_typeof(result_row), '') <> 'object'
            or coalesce(jsonb_typeof(result_row -> 'id'), '') <> 'string'
            or coalesce(jsonb_typeof(result_row -> 'match_id'), '') <> 'string'
            or coalesce(jsonb_typeof(result_row -> 'team_slot_id'), '') <> 'string'
            or coalesce(jsonb_typeof(result_row -> 'kills'), '') <> 'number'
            or coalesce(jsonb_typeof(result_row -> 'source'), '') <> 'string'
            or coalesce(jsonb_typeof(result_row -> 'review_status'), '') <> 'string'
            or ((result_row ? 'placement') and result_row -> 'placement' <> 'null'::jsonb
                and jsonb_typeof(result_row -> 'placement') <> 'number')
            or ((result_row ? 'participation_status')
                and result_row -> 'participation_status' <> 'null'::jsonb
                and jsonb_typeof(result_row -> 'participation_status') <> 'string')
            or (result_row ->> 'id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (result_row ->> 'match_id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (result_row ->> 'team_slot_id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (result_row ->> 'source') not in ('manual', 'ocr_assisted')
            or (result_row ->> 'review_status') not in ('draft', 'confirmed')
            or coalesce(nullif(result_row ->> 'participation_status', ''), 'PARTICIPATED')
                not in ('PARTICIPATED', 'NO_SHOW')
            or ((result_row ->> 'placement') is not null
                and (result_row ->> 'placement') !~ '^([1-9]|1[0-2])$')
            or (result_row ->> 'kills') !~ '^[0-9]+$'
            or (result_row ->> 'match_id') not in (
                select match_row ->> 'id'
                from jsonb_array_elements(p_matches) as match_row
            )
            or not exists (
                select 1
                from public.tournament_team_slots as team_slot
                where team_slot.id::text = result_row ->> 'team_slot_id'
                    and team_slot.tournament_id = p_tournament_id
            )
            or not exists (
                select 1
                from jsonb_array_elements(p_matches) as match_row
                join public.tournament_team_slots as team_slot
                    on team_slot.id::text = result_row ->> 'team_slot_id'
                    and team_slot.tournament_id = p_tournament_id
                join public.tournament_group_pairing_lobby_slots as mapping_row
                    on mapping_row.tournament_id = p_tournament_id
                    and mapping_row.pairing_key = match_row ->> 'group_pairing_key'
                    and mapping_row.team_slot_number = team_slot.slot_number
                where match_row ->> 'id' = result_row ->> 'match_id'
            )
            or (
                coalesce(nullif(result_row ->> 'participation_status', ''), 'PARTICIPATED') = 'NO_SHOW'
                and ((result_row ->> 'placement') is not null or (result_row ->> 'kills') <> '0')
            )
    )
    or (
        select count(distinct result_row ->> 'id')
        from jsonb_array_elements(p_match_results) as result_row
    ) <> jsonb_array_length(p_match_results)
    or exists (
        select 1
        from jsonb_array_elements(p_match_results) as result_row
        group by result_row ->> 'match_id', result_row ->> 'team_slot_id'
        having count(*) > 1
    )
    or exists (
        select 1
        from jsonb_array_elements(p_match_results) as result_row
        where result_row ->> 'placement' is not null
        group by result_row ->> 'match_id', result_row ->> 'placement'
        having count(*) > 1
    )
    or exists (
        select 1
        from jsonb_array_elements(p_match_results) as result_row
        join public.match_results as existing_result
            on existing_result.id::text = result_row ->> 'id'
        where existing_result.match_id::text <> result_row ->> 'match_id'
            or existing_result.team_slot_id::text <> result_row ->> 'team_slot_id'
    )
    or exists (
        select 1
        from public.matches as existing_match
        join jsonb_array_elements(p_match_results) as result_row
            on existing_match.id::text = result_row ->> 'match_id'
        where existing_match.tournament_id <> p_tournament_id
            or existing_match.status = 'finalized'
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    insert into public.matches as match_target (
        id, tournament_id, match_number, match_date, map_name, status, group_pairing_key
    )
    select id, tournament_id, match_number, match_date, map_name, status, group_pairing_key
    from jsonb_to_recordset(p_matches) as match_row(
        id uuid, tournament_id uuid, match_number integer, match_date date,
        map_name text, status text, group_pairing_key text
    )
    on conflict (id) do update
    set match_number = excluded.match_number,
        match_date = excluded.match_date,
        map_name = excluded.map_name,
        status = excluded.status,
        group_pairing_key = excluded.group_pairing_key,
        revision = match_target.revision + 1,
        updated_at = now();

    delete from public.match_results as stale_result
    using public.matches as included_match
    where stale_result.match_id = included_match.id
        and included_match.tournament_id = p_tournament_id
        and included_match.status = 'draft'
        and exists (
            select 1
            from jsonb_array_elements(p_matches) as match_row
            where match_row ->> 'id' = included_match.id::text
        )
        and not exists (
            select 1
            from jsonb_array_elements(p_match_results) as incoming_result
            where incoming_result ->> 'match_id' = stale_result.match_id::text
                and incoming_result ->> 'id' = stale_result.id::text
                and incoming_result ->> 'team_slot_id' = stale_result.team_slot_id::text
        );

    insert into public.match_results as match_result_target (
        id, match_id, team_slot_id, placement, kills, source, review_status,
        participation_status
    )
    select id, match_id, team_slot_id, placement, kills, source, review_status,
           coalesce(participation_status, 'PARTICIPATED')
    from jsonb_to_recordset(p_match_results) as result_row(
        id uuid, match_id uuid, team_slot_id uuid, placement integer,
        kills integer, source text, review_status text, participation_status text
    )
    on conflict (id) do update
    set placement = excluded.placement,
        kills = excluded.kills,
        source = excluded.source,
        review_status = excluded.review_status,
        participation_status = excluded.participation_status,
        revision = match_result_target.revision + 1,
        updated_at = now();

    update public.tournaments as tournament_row
    set revision = tournament_row.revision + 1, updated_at = now()
    where tournament_row.id = p_tournament_id;

    return query select 'success'::text, v_current_revision + 1;
end;
$$;

create or replace function public.finalize_match_snapshot_v2(
    p_tournament_id uuid,
    p_match jsonb,
    p_match_results jsonb,
    p_expected_revision integer
)
returns table (outcome text, revision integer)
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    v_match_id uuid;
    v_owner_id uuid;
    v_current_revision integer;
    v_match_status text;
    v_pairing_key text;
    v_result_count integer;
    v_distinct_result_ids integer;
    v_distinct_team_slots integer;
    v_participated_count integer;
    v_distinct_placements integer;
    v_values_valid boolean;
begin
    if auth.uid() is null then
        return query select 'authentication_required'::text, null::integer;
        return;
    end if;

    if p_expected_revision is null or p_expected_revision <= 0
        or coalesce(jsonb_typeof(p_match), '') <> 'object'
        or coalesce(jsonb_typeof(p_match_results), '') <> 'array'
        or coalesce(jsonb_typeof(p_match -> 'id'), '') <> 'string'
        or (p_match ->> 'id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    v_match_id := (p_match ->> 'id')::uuid;

    select tournament_row.owner_id, tournament_row.revision
    into v_owner_id, v_current_revision
    from public.tournaments as tournament_row
    where tournament_row.id = p_tournament_id
        and tournament_row.format = 'group_rotation'
    for update;
    if not found then
        return query select 'missing_data'::text, null::integer;
        return;
    end if;
    if v_owner_id is distinct from auth.uid() then
        return query select 'unauthorized'::text, null::integer;
        return;
    end if;
    if p_expected_revision <> v_current_revision then
        return query select 'stale_write'::text, v_current_revision;
        return;
    end if;

    select match_row.status, match_row.group_pairing_key
    into v_match_status, v_pairing_key
    from public.matches as match_row
    where match_row.id = v_match_id
        and match_row.tournament_id = p_tournament_id
    for update;
    if not found then
        return query select 'missing_data'::text, v_current_revision;
        return;
    end if;
    if v_match_status = 'finalized' then
        return query select 'already_finalized'::text, v_current_revision;
        return;
    end if;

    if v_match_status <> 'draft'
        or (p_match ->> 'status') <> 'finalized'
        or (p_match ? 'tournament_id' and (p_match ->> 'tournament_id') <> p_tournament_id::text)
        or (p_match ->> 'group_pairing_key') is distinct from v_pairing_key
        or not exists (
            select 1
            from public.tournament_group_pairings as pairing_row
            where pairing_row.tournament_id = p_tournament_id
                and pairing_row.pairing_key = v_pairing_key
        )
        or (
            select count(*)
            from public.tournament_group_pairing_lobby_slots as mapping_row
            where mapping_row.tournament_id = p_tournament_id
                and mapping_row.pairing_key = v_pairing_key
        ) <> 12
        or (
            select count(distinct mapping_row.team_slot_number)
            from public.tournament_group_pairing_lobby_slots as mapping_row
            where mapping_row.tournament_id = p_tournament_id
                and mapping_row.pairing_key = v_pairing_key
        ) <> 12
        or exists (
            select 1
            from public.tournament_group_pairing_lobby_slots as mapping_row
            join public.tournament_team_slots as team_slot
                on team_slot.tournament_id = mapping_row.tournament_id
                and team_slot.slot_number = mapping_row.team_slot_number
            where mapping_row.tournament_id = p_tournament_id
                and mapping_row.pairing_key = v_pairing_key
                and btrim(coalesce(team_slot.team_name, '')) = ''
        ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_match_results) as result_row
        where coalesce(jsonb_typeof(result_row), '') <> 'object'
            or coalesce(jsonb_typeof(result_row -> 'id'), '') <> 'string'
            or coalesce(jsonb_typeof(result_row -> 'match_id'), '') <> 'string'
            or coalesce(jsonb_typeof(result_row -> 'team_slot_id'), '') <> 'string'
            or coalesce(jsonb_typeof(result_row -> 'kills'), '') <> 'number'
            or coalesce(jsonb_typeof(result_row -> 'source'), '') <> 'string'
            or coalesce(jsonb_typeof(result_row -> 'review_status'), '') <> 'string'
            or ((result_row ? 'placement') and result_row -> 'placement' <> 'null'::jsonb
                and jsonb_typeof(result_row -> 'placement') <> 'number')
            or ((result_row ? 'participation_status')
                and result_row -> 'participation_status' <> 'null'::jsonb
                and jsonb_typeof(result_row -> 'participation_status') <> 'string')
            or (result_row ->> 'id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (result_row ->> 'match_id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (result_row ->> 'team_slot_id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (result_row ->> 'source') not in ('manual', 'ocr_assisted')
            or (result_row ->> 'review_status') not in ('draft', 'confirmed')
            or coalesce(nullif(result_row ->> 'participation_status', ''), 'PARTICIPATED')
                not in ('PARTICIPATED', 'NO_SHOW')
            or (result_row ->> 'match_id') <> v_match_id::text
            or (result_row ->> 'team_slot_id') is null
            or not exists (
                select 1
                from public.tournament_group_pairing_lobby_slots as mapping_row
                join public.tournament_team_slots as team_slot
                    on team_slot.tournament_id = mapping_row.tournament_id
                    and team_slot.slot_number = mapping_row.team_slot_number
                where mapping_row.tournament_id = p_tournament_id
                    and mapping_row.pairing_key = v_pairing_key
                    and team_slot.id::text = result_row ->> 'team_slot_id'
            )
    )
    or (
        jsonb_array_length(p_match_results) <> 12
    )
    or (
        select count(distinct result_row ->> 'id')
        from jsonb_array_elements(p_match_results) as result_row
    ) <> 12
    or (
        select count(distinct result_row ->> 'team_slot_id')
        from jsonb_array_elements(p_match_results) as result_row
    ) <> 12
    or exists (
        select 1
        from jsonb_array_elements(p_match_results) as result_row
        join public.match_results as existing_result
            on existing_result.id::text = result_row ->> 'id'
        where existing_result.match_id <> v_match_id
    )
    or (
        select count(distinct result_row ->> 'placement')
        from jsonb_array_elements(p_match_results) as result_row
        where coalesce(nullif(result_row ->> 'participation_status', ''), 'PARTICIPATED') = 'PARTICIPATED'
    ) <> (
        select count(*)
        from jsonb_array_elements(p_match_results) as result_row
        where coalesce(nullif(result_row ->> 'participation_status', ''), 'PARTICIPATED') = 'PARTICIPATED'
    )
    then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    select count(*) filter (
               where coalesce(nullif(result_row ->> 'participation_status', ''), 'PARTICIPATED') = 'PARTICIPATED'
           ),
           count(distinct result_row ->> 'placement') filter (
               where coalesce(nullif(result_row ->> 'participation_status', ''), 'PARTICIPATED') = 'PARTICIPATED'
           ),
           coalesce(bool_and(
               (
                   coalesce(nullif(result_row ->> 'participation_status', ''), 'PARTICIPATED') = 'PARTICIPATED'
                   and (result_row ->> 'placement') ~ '^([1-9]|1[0-9]|2[0-9])$'
                   and (result_row ->> 'kills') ~ '^[0-9]+$'
               )
               or (
                   coalesce(nullif(result_row ->> 'participation_status', ''), 'PARTICIPATED') = 'NO_SHOW'
                   and (result_row ->> 'placement') is null
                   and (result_row ->> 'kills') = '0'
               )
           ), false)
    into v_participated_count, v_distinct_placements, v_values_valid
    from jsonb_array_elements(p_match_results) as result_row;

    if v_participated_count <= 0
        or v_distinct_placements <> v_participated_count
        or not v_values_valid
        or exists (
            select 1
            from jsonb_array_elements(p_match_results) as result_row
            where coalesce(nullif(result_row ->> 'participation_status', ''), 'PARTICIPATED') = 'PARTICIPATED'
                and (
                    case
                        when (result_row ->> 'placement') ~ '^[0-9]+$'
                            then ((result_row ->> 'placement')::integer < 1
                                or (result_row ->> 'placement')::integer > v_participated_count)
                        else false
                    end
                )
        ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    delete from public.match_results where match_id = v_match_id;
    insert into public.match_results (
        id, match_id, team_slot_id, placement, kills, source, review_status,
        participation_status
    )
    select id, match_id, team_slot_id, placement, kills, source, review_status,
           coalesce(participation_status, 'PARTICIPATED')
    from jsonb_to_recordset(p_match_results) as result_row(
        id uuid, match_id uuid, team_slot_id uuid, placement integer,
        kills integer, source text, review_status text, participation_status text
    );

    update public.matches as match_target
    set status = 'finalized', finalized_at = now(), finalized_by = auth.uid(),
        revision = match_target.revision + 1, updated_at = now()
    where match_target.id = v_match_id;
    update public.tournaments as tournament_row
    set revision = tournament_row.revision + 1, updated_at = now()
    where tournament_row.id = p_tournament_id;

    return query select 'success'::text, v_current_revision + 1;
end;
$$;

create or replace function public.replace_tournament_roster_snapshot_v2(
    p_tournament_id uuid,
    p_team_slots jsonb,
    p_players jsonb,
    p_expected_revision integer
)
returns table (outcome text, revision integer)
language plpgsql
security invoker
set search_path = pg_catalog, public
as $$
declare
    v_current_revision integer;
    v_owner_id uuid;
    v_group_count integer;
    v_expected_slot_count integer;
begin
    if auth.uid() is null then
        return query select 'authentication_required'::text, null::integer;
        return;
    end if;
    if p_expected_revision is null or p_expected_revision <= 0 then
        return query select 'missing_revision'::text, null::integer;
        return;
    end if;

    select t.owner_id, t.revision, t.group_count
    into v_owner_id, v_current_revision, v_group_count
    from public.tournaments as t
    where t.id = p_tournament_id
        and t.format = 'group_rotation'
    for update;
    if not found then
        return query select 'missing_revision'::text, null::integer;
        return;
    end if;
    if v_owner_id is distinct from auth.uid() then
        return query select 'unauthorized'::text, v_current_revision;
        return;
    end if;
    if p_expected_revision <> v_current_revision then
        return query select 'stale_write'::text, v_current_revision;
        return;
    end if;
    if exists (select 1 from public.matches where tournament_id = p_tournament_id) then
        return query select 'matches_exist'::text, v_current_revision;
        return;
    end if;

    v_expected_slot_count := v_group_count * 6;
    if coalesce(jsonb_typeof(p_team_slots), '') <> 'array'
        or jsonb_array_length(p_team_slots) <> v_expected_slot_count
        or coalesce(jsonb_typeof(p_players), '') <> 'array' then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_team_slots) as slot_row
        where coalesce(jsonb_typeof(slot_row), '') <> 'object'
            or coalesce(jsonb_typeof(slot_row -> 'id'), '') <> 'string'
            or coalesce(jsonb_typeof(slot_row -> 'tournament_id'), '') <> 'string'
            or coalesce(jsonb_typeof(slot_row -> 'slot_number'), '') <> 'number'
            or coalesce(jsonb_typeof(slot_row -> 'team_name'), '') <> 'string'
            or coalesce(jsonb_typeof(slot_row -> 'status'), '') <> 'string'
            or (slot_row ->> 'id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (slot_row ->> 'tournament_id') <> p_tournament_id::text
            or (slot_row ->> 'slot_number') !~ '^([1-9]|1[0-9]|2[0-4])$'
            or (slot_row ->> 'status') <> 'draft'
            or (slot_row ->> 'group') is distinct from case
                when (slot_row ->> 'slot_number')::integer between 1 and 6 then 'A'
                when (slot_row ->> 'slot_number')::integer between 7 and 12 then 'B'
                when (slot_row ->> 'slot_number')::integer between 13 and 18 then 'C'
                else 'D'
            end
    )
    or (
        select count(distinct slot_row ->> 'slot_number')
        from jsonb_array_elements(p_team_slots) as slot_row
    ) <> v_expected_slot_count
    or exists (
        select 1
        from generate_series(1, v_expected_slot_count) as expected_slot(slot_number)
        where not exists (
            select 1
            from jsonb_array_elements(p_team_slots) as slot_row
            where slot_row ->> 'slot_number' = expected_slot.slot_number::text
        )
    )
    or (
        select count(distinct slot_row ->> 'id')
        from jsonb_array_elements(p_team_slots) as slot_row
    ) <> v_expected_slot_count
    or exists (
        select 1
        from jsonb_array_elements(p_team_slots) as slot_row
        where btrim(slot_row ->> 'team_name') <> ''
        group by btrim(slot_row ->> 'team_name')
        having count(*) > 1
    )
    or exists (
        select 1
        from jsonb_array_elements(p_team_slots) as incoming_slot
        where not exists (
            select 1
            from public.tournament_team_slots as existing_slot
            where existing_slot.id::text = incoming_slot ->> 'id'
                and existing_slot.tournament_id = p_tournament_id
                and existing_slot.slot_number::text = incoming_slot ->> 'slot_number'
        )
    )
    or exists (
        select 1
        from public.tournament_group_pairing_lobby_slots as mapping_row
        where mapping_row.tournament_id = p_tournament_id
            and (
                mapping_row.team_slot_number < 1
                or mapping_row.team_slot_number > v_expected_slot_count
            )
    )
    or exists (
        select 1
        from public.tournament_group_pairing_lobby_slots as mapping_row
        join jsonb_to_recordset(p_team_slots) as incoming_slot(
            id uuid, tournament_id uuid, slot_number integer, team_name text, status text, "group" text
        )
            on incoming_slot.slot_number = mapping_row.team_slot_number
        where mapping_row.tournament_id = p_tournament_id
            and btrim(coalesce(incoming_slot.team_name, '')) = ''
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_players) as player_row
        where coalesce(jsonb_typeof(player_row), '') <> 'object'
            or coalesce(jsonb_typeof(player_row -> 'id'), '') <> 'string'
            or coalesce(jsonb_typeof(player_row -> 'team_slot_id'), '') <> 'string'
            or coalesce(jsonb_typeof(player_row -> 'display_name'), '') <> 'string'
            or coalesce(jsonb_typeof(player_row -> 'normalized_name'), '') <> 'string'
            or (player_row ->> 'id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (player_row ->> 'team_slot_id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or btrim(player_row ->> 'display_name') = ''
            or (player_row ->> 'normalized_name') <> btrim(player_row ->> 'display_name')
            or not exists (
                select 1
                from public.tournament_team_slots as slot_row
                where slot_row.id::text = player_row ->> 'team_slot_id'
                    and slot_row.tournament_id = p_tournament_id
            )
    )
    or (
        select count(distinct player_row ->> 'id')
        from jsonb_array_elements(p_players) as player_row
    ) <> jsonb_array_length(p_players)
    or exists (
        select 1
        from jsonb_array_elements(p_players) as player_row
        group by player_row ->> 'team_slot_id', player_row ->> 'normalized_name'
        having count(*) > 1
    )
    or exists (
        select 1
        from jsonb_array_elements(p_players) as player_row
        group by player_row ->> 'team_slot_id'
        having count(*) > 6
    )
    or exists (
        select 1
        from jsonb_array_elements(p_players) as incoming_player
        join public.players as existing_player
            on existing_player.id::text = incoming_player ->> 'id'
        where existing_player.team_slot_id::text <> incoming_player ->> 'team_slot_id'
            or not exists (
                select 1
                from public.tournament_team_slots as existing_slot
                where existing_slot.id = existing_player.team_slot_id
                    and existing_slot.tournament_id = p_tournament_id
            )
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    insert into public.tournament_team_slots (
        id, tournament_id, slot_number, team_name, status, "group"
    )
    select id, tournament_id, slot_number, team_name, status, "group"
    from jsonb_to_recordset(p_team_slots) as slot_row(
        id uuid, tournament_id uuid, slot_number integer, team_name text, status text, "group" text
    )
    on conflict (id) do update
    set team_name = excluded.team_name,
        status = excluded.status,
        "group" = excluded."group",
        revision = public.tournament_team_slots.revision + 1,
        updated_at = now();

    delete from public.players as stale_player
    using public.tournament_team_slots as target_slot
    where stale_player.team_slot_id = target_slot.id
        and target_slot.tournament_id = p_tournament_id
        and not exists (
            select 1
            from jsonb_array_elements(p_players) as player_row
            where (player_row ->> 'id')::uuid = stale_player.id
        );

    insert into public.players (
        id, team_slot_id, display_name, normalized_name
    )
    select id, team_slot_id, display_name, normalized_name
    from jsonb_to_recordset(p_players) as player_row(
        id uuid, team_slot_id uuid, display_name text, normalized_name text
    )
    on conflict (id) do update
    set team_slot_id = excluded.team_slot_id,
        display_name = excluded.display_name,
        normalized_name = excluded.normalized_name,
        revision = public.players.revision + 1,
        updated_at = now();

    update public.tournaments
    set revision = public.tournaments.revision + 1,
        updated_at = now()
    where id = p_tournament_id;

    return query select 'success'::text, v_current_revision + 1;
end;
$$;

revoke all on function public.write_tournament_snapshot_v2(jsonb, jsonb, jsonb, jsonb, integer) from public;
revoke all on function public.write_tournament_snapshot_v2(jsonb, jsonb, jsonb, jsonb, integer) from anon;
grant execute on function public.write_tournament_snapshot_v2(jsonb, jsonb, jsonb, jsonb, integer) to authenticated;

revoke all on function public.replace_tournament_roster_snapshot_v2(uuid, jsonb, jsonb, integer) from public;
revoke all on function public.replace_tournament_roster_snapshot_v2(uuid, jsonb, jsonb, integer) from anon;
grant execute on function public.replace_tournament_roster_snapshot_v2(uuid, jsonb, jsonb, integer) to authenticated;

revoke all on function public.write_match_snapshot_v2(uuid, jsonb, jsonb, integer) from public;
revoke all on function public.write_match_snapshot_v2(uuid, jsonb, jsonb, integer) from anon;
grant execute on function public.write_match_snapshot_v2(uuid, jsonb, jsonb, integer) to authenticated;

revoke all on function public.finalize_match_snapshot_v2(uuid, jsonb, jsonb, integer) from public;
revoke all on function public.finalize_match_snapshot_v2(uuid, jsonb, jsonb, integer) from anon;
grant execute on function public.finalize_match_snapshot_v2(uuid, jsonb, jsonb, integer) to authenticated;
