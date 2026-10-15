-- PointIQ Group Rotation V3-B3b: additive full-roster replacement.
-- Existing V2 and V3-B3a RPCs remain unchanged.

create or replace function public.replace_tournament_roster_snapshot_v3(
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

    select tournament_row.owner_id, tournament_row.revision,
           tournament_row.group_count
    into v_owner_id, v_current_revision, v_group_count
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
        from public.matches as existing_match
        where existing_match.tournament_id = p_tournament_id
    ) then
        return query select 'matches_exist'::text, v_current_revision;
        return;
    end if;

    if v_group_count is null or v_group_count not in (3, 4) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    v_expected_slot_count := v_group_count * 6;

    if coalesce(jsonb_typeof(p_team_slots), '') <> 'array'
        or coalesce(jsonb_typeof(p_players), '') <> 'array' then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if jsonb_array_length(p_team_slots) <> v_expected_slot_count then
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
            or ((slot_row ? 'group')
                and coalesce(jsonb_typeof(slot_row -> 'group'), '') not in ('string', 'null'))
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_team_slots) as slot_row
        where (slot_row ->> 'id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (slot_row ->> 'tournament_id') <> p_tournament_id::text
            or (slot_row ->> 'slot_number') !~ '^([1-9]|1[0-9]|2[0-4])$'
            or not exists (
                select 1
                from generate_series(1, v_expected_slot_count) as expected_slot(slot_number)
                where expected_slot.slot_number::text = slot_row ->> 'slot_number'
            )
            or (slot_row ->> 'status') <> 'draft'
            or ((slot_row ->> 'group') is not null
                and (slot_row ->> 'group') not in ('A', 'B', 'C', 'D'))
            or ((slot_row ->> 'group') = 'A'
                and (slot_row ->> 'slot_number') !~ '^[1-6]$')
            or ((slot_row ->> 'group') = 'B'
                and (slot_row ->> 'slot_number') !~ '^(7|8|9|1[0-2])$')
            or ((slot_row ->> 'group') = 'C'
                and (slot_row ->> 'slot_number') !~ '^1[3-8]$')
            or ((slot_row ->> 'group') = 'D'
                and (slot_row ->> 'slot_number') !~ '^(19|2[0-4])$')
    ) then
        return query select 'validation_failure'::text, v_current_revision;
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
            where lower(btrim(regexp_replace(slot_row ->> 'team_name', E'\\s+', ' ', 'g'))) <> ''
            group by lower(btrim(regexp_replace(slot_row ->> 'team_name', E'\\s+', ' ', 'g')))
            having count(*) > 1
        ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if (
        select count(*)
        from public.tournament_team_slots as existing_slot
        where existing_slot.tournament_id = p_tournament_id
    ) <> v_expected_slot_count
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
            from public.tournament_team_slots as existing_slot
            where existing_slot.tournament_id = p_tournament_id
                and not exists (
                    select 1
                    from jsonb_array_elements(p_team_slots) as incoming_slot
                    where incoming_slot ->> 'id' = existing_slot.id::text
                        and incoming_slot ->> 'slot_number' = existing_slot.slot_number::text
                )
        )
        or exists (
            select 1
            from public.tournament_team_slots as existing_slot
            join jsonb_to_recordset(p_team_slots) as incoming_slot(
                id uuid, tournament_id uuid, slot_number integer,
                team_name text, status text, "group" text
            ) on incoming_slot.id = existing_slot.id
            where existing_slot.tournament_id = p_tournament_id
                and (
                    existing_slot."group" is distinct from incoming_slot."group"
                    or existing_slot.team_name is distinct from incoming_slot.team_name
                )
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
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_players) as player_row
        where (player_row ->> 'id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (player_row ->> 'team_slot_id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or btrim(player_row ->> 'display_name') = ''
            or (player_row ->> 'normalized_name') <> btrim(player_row ->> 'display_name')
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_players) as player_row
        where not exists (
            select 1
            from public.tournament_team_slots as existing_slot
            where existing_slot.id::text = player_row ->> 'team_slot_id'
                and existing_slot.tournament_id = p_tournament_id
                and existing_slot.slot_number between 1 and v_expected_slot_count
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

revoke all on function public.replace_tournament_roster_snapshot_v3(uuid, jsonb, jsonb, integer) from public;
revoke all on function public.replace_tournament_roster_snapshot_v3(uuid, jsonb, jsonb, integer) from anon;
grant execute on function public.replace_tournament_roster_snapshot_v3(uuid, jsonb, jsonb, integer) to authenticated;
