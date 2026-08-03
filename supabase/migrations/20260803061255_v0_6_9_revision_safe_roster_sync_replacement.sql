-- v0.6.9: replace a tournament roster only against an existing, owned cloud
-- tournament revision. This function intentionally remains SECURITY INVOKER so
-- the existing owner RLS policies authorize every read and write.
create or replace function public.replace_tournament_roster_snapshot(
    p_tournament_id uuid,
    p_team_slots jsonb,
    p_players jsonb,
    p_expected_revision integer
)
returns table (outcome text, revision integer)
language plpgsql
security invoker
set search_path = public
as $$
declare
    v_current_revision integer;
begin
    if p_expected_revision is null or p_expected_revision <= 0 then
        return query select 'missing_revision'::text, null::integer;
        return;
    end if;

    select t.revision
    into v_current_revision
    from public.tournaments t
    where t.id = p_tournament_id
    for update;

    if not found then
        return query select 'missing_revision'::text, null::integer;
        return;
    end if;

    if p_expected_revision <> v_current_revision then
        return query select 'stale_write'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from public.matches m
        where m.tournament_id = p_tournament_id
    ) then
        return query select 'matches_exist'::text, v_current_revision;
        return;
    end if;

    if coalesce(jsonb_typeof(p_team_slots), '') <> 'array' then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if jsonb_array_length(p_team_slots) <> 12 then
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
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_team_slots) as slot_row
        where (slot_row ->> 'id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (slot_row ->> 'tournament_id') <> p_tournament_id::text
            or (slot_row ->> 'slot_number') !~ '^([1-9]|1[0-2])$'
            or btrim(slot_row ->> 'team_name') = ''
            or (slot_row ->> 'status') <> 'draft'
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if (
        select count(distinct slot_row ->> 'slot_number')
        from jsonb_array_elements(p_team_slots) as slot_row
    ) <> 12
        or (
            select count(distinct slot_row ->> 'id')
            from jsonb_array_elements(p_team_slots) as slot_row
        ) <> 12 then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_team_slots) as slot_row
        where not exists (
            select 1
            from public.tournament_team_slots target_slot
            where target_slot.id = (slot_row ->> 'id')::uuid
                and target_slot.tournament_id = p_tournament_id
                and target_slot.slot_number = (slot_row ->> 'slot_number')::integer
        )
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_team_slots) as slot_row
        group by slot_row ->> 'team_name'
        having count(*) > 1
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if coalesce(jsonb_typeof(p_players), '') <> 'array' then
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

    if (
        select count(distinct player_row ->> 'id')
        from jsonb_array_elements(p_players) as player_row
    ) <> jsonb_array_length(p_players) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_players) as player_row
        where not exists (
            select 1
            from public.tournament_team_slots target_slot
            where target_slot.id = (player_row ->> 'team_slot_id')::uuid
                and target_slot.tournament_id = p_tournament_id
        )
            or exists (
                select 1
                from public.players existing_player
                where existing_player.id = (player_row ->> 'id')::uuid
                    and existing_player.team_slot_id <> (player_row ->> 'team_slot_id')::uuid
            )
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_players) as player_row
        group by player_row ->> 'team_slot_id', player_row ->> 'normalized_name'
        having count(*) > 1
    ) or exists (
        select 1
        from jsonb_array_elements(p_players) as player_row
        group by player_row ->> 'team_slot_id'
        having count(*) > 6
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    insert into public.tournament_team_slots (
        id, tournament_id, slot_number, team_name, status
    )
    select id, tournament_id, slot_number, team_name, status
    from jsonb_to_recordset(p_team_slots) as slot_row(
        id uuid,
        tournament_id uuid,
        slot_number integer,
        team_name text,
        status text
    )
    on conflict (id) do update
    set team_name = excluded.team_name,
        status = excluded.status,
        revision = public.tournament_team_slots.revision + 1,
        updated_at = now();

    delete from public.players stale_player
    using public.tournament_team_slots target_slot
    where stale_player.team_slot_id = target_slot.id
        and target_slot.tournament_id = p_tournament_id
        and not exists (
            select 1
            from jsonb_array_elements(p_players) as player_row
            where (player_row ->> 'id')::uuid = stale_player.id
        );

    insert into public.players (id, team_slot_id, display_name, normalized_name)
    select id, team_slot_id, display_name, normalized_name
    from jsonb_to_recordset(p_players) as player_row(
        id uuid,
        team_slot_id uuid,
        display_name text,
        normalized_name text
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

revoke all on function public.replace_tournament_roster_snapshot(uuid, jsonb, jsonb, integer) from public;
grant execute on function public.replace_tournament_roster_snapshot(uuid, jsonb, jsonb, integer) to authenticated;
