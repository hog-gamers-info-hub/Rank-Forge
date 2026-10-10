-- PointIQ Group Rotation V3-B3a: additive, identity-preserving tournament snapshot.
-- The V2 and legacy snapshot, match, finalization, and correction RPCs remain unchanged.

create or replace function public.write_tournament_snapshot_v3(
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
    v_existing_owner_id uuid;
    v_current_revision integer;
    v_format text;
    v_group_count integer;
    v_pairings jsonb;
    v_mappings jsonb;
    v_expected_slot_count integer;
    v_pairing_key text;
    v_tournament_exists boolean;
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
        or coalesce(jsonb_typeof(p_tournament -> 'name'), '') <> 'string'
        or coalesce(jsonb_typeof(p_tournament -> 'status'), '') <> 'string'
        or coalesce(jsonb_typeof(p_tournament -> 'format'), '') <> 'string'
        or coalesce(jsonb_typeof(p_tournament -> 'group_count'), '') <> 'number'
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
        or coalesce(jsonb_typeof(v_pairings), '') <> 'array'
        or jsonb_array_length(v_pairings) < 1
        or coalesce(jsonb_typeof(p_team_slots), '') <> 'array'
        or coalesce(jsonb_typeof(p_players), '') <> 'array'
        or coalesce(jsonb_typeof(v_mappings), '') <> 'array'
        or (p_tournament ->> 'status') not in ('draft', 'active', 'completed', 'archived')
        or (p_tournament ->> 'group_count') !~ '^[34]$' then
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
            or ((slot_row ? 'group')
                and coalesce(jsonb_typeof(slot_row -> 'group'), '') not in ('string', 'null'))
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
            where lower(btrim(regexp_replace(slot_row ->> 'team_name', E'\\s+', ' ', 'g'))) <> ''
            group by lower(btrim(regexp_replace(slot_row ->> 'team_name', E'\\s+', ' ', 'g')))
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
            or (v_group_count = 3 and (
                (pairing_row ->> 'first_group') = 'D'
                or (pairing_row ->> 'second_group') = 'D'
            ))
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

    if exists (
        select 1
        from jsonb_array_elements(v_mappings) as mapping_row
        where coalesce(jsonb_typeof(mapping_row), '') <> 'object'
            or coalesce(jsonb_typeof(mapping_row -> 'tournament_id'), '') <> 'string'
            or coalesce(jsonb_typeof(mapping_row -> 'pairing_key'), '') <> 'string'
            or coalesce(jsonb_typeof(mapping_row -> 'lobby_slot_number'), '') <> 'number'
            or coalesce(jsonb_typeof(mapping_row -> 'team_slot_number'), '') <> 'number'
            or (mapping_row ->> 'tournament_id') <> v_tournament_id::text
            or (mapping_row ->> 'pairing_key') not in (
                select pairing_row ->> 'pairing_key'
                from jsonb_array_elements(v_pairings) as pairing_row
            )
            or (mapping_row ->> 'lobby_slot_number') !~ '^(1[0-2]|[1-9])$'
            or (mapping_row ->> 'team_slot_number') !~ '^([1-9]|1[0-9]|2[0-4])$'
            or not exists (
                select 1
                from generate_series(1, v_expected_slot_count) as expected_slot(slot_number)
                where expected_slot.slot_number::text = mapping_row ->> 'team_slot_number'
            )
    ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if jsonb_array_length(v_mappings) > 0 and (
        exists (
            select 1
            from (
                select mapping_row ->> 'pairing_key' as pairing_key
                from jsonb_array_elements(v_mappings) as mapping_row
                group by mapping_row ->> 'pairing_key'
            ) as configured_pairing
            where (
                select count(*)
                from jsonb_array_elements(v_mappings) as mapping_row
                where mapping_row ->> 'pairing_key' = configured_pairing.pairing_key
            ) <> 12
        )
        or exists (
            select 1
            from jsonb_array_elements(v_mappings) as mapping_row
            group by mapping_row ->> 'pairing_key', mapping_row ->> 'lobby_slot_number'
            having count(*) > 1
        )
        or exists (
            select 1
            from jsonb_array_elements(v_mappings) as mapping_row
            group by mapping_row ->> 'pairing_key', mapping_row ->> 'team_slot_number'
            having count(*) > 1
        )
        or exists (
            select 1
            from jsonb_array_elements(v_mappings) as mapping_row
            where not exists (
                select 1
                from jsonb_array_elements(p_team_slots) as slot_row
                where slot_row ->> 'slot_number' = mapping_row ->> 'team_slot_number'
                    and btrim(slot_row ->> 'team_name') <> ''
            )
        )
    ) then
        return query select 'validation_failure'::text, null::integer;
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
                from jsonb_array_elements(p_team_slots) as slot_row
                where slot_row ->> 'id' = player_row ->> 'team_slot_id'
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
            where existing_player.team_slot_id::text <> incoming_player ->> 'team_slot_id'
                or not exists (
                    select 1
                    from public.tournament_team_slots as existing_slot
                    where existing_slot.id = existing_player.team_slot_id
                        and existing_slot.tournament_id = v_tournament_id
                )
        ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    select tournament_row.owner_id, tournament_row.revision,
           tournament_row.format, tournament_row.group_count
    into v_existing_owner_id, v_current_revision, v_format, v_group_count
    from public.tournaments as tournament_row
    where tournament_row.id = v_tournament_id
    for update;
    v_tournament_exists := found;

    if exists (
        select 1
        from jsonb_to_recordset(p_team_slots) as incoming_slot(
            id uuid, tournament_id uuid, slot_number integer,
            team_name text, status text, "group" text
        )
        join public.tournament_team_slots as existing_slot
            on existing_slot.id = incoming_slot.id
        where existing_slot.tournament_id <> v_tournament_id
            or existing_slot.slot_number <> incoming_slot.slot_number
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        with effective_players as (
            select existing_player.team_slot_id::text as team_slot_id,
                   existing_player.normalized_name
            from public.players as existing_player
            join public.tournament_team_slots as existing_slot
                on existing_slot.id = existing_player.team_slot_id
                and existing_slot.tournament_id = v_tournament_id
            where not exists (
                select 1
                from jsonb_array_elements(p_players) as incoming_player
                where incoming_player ->> 'id' = existing_player.id::text
            )
            union all
            select incoming_player ->> 'team_slot_id' as team_slot_id,
                   incoming_player ->> 'normalized_name' as normalized_name
            from jsonb_array_elements(p_players) as incoming_player
        )
        select 1
        from effective_players
        group by team_slot_id
        having count(*) > 6
    )
        or exists (
            with effective_players as (
                select existing_player.team_slot_id::text as team_slot_id,
                       existing_player.normalized_name
                from public.players as existing_player
                join public.tournament_team_slots as existing_slot
                    on existing_slot.id = existing_player.team_slot_id
                    and existing_slot.tournament_id = v_tournament_id
                where not exists (
                    select 1
                    from jsonb_array_elements(p_players) as incoming_player
                    where incoming_player ->> 'id' = existing_player.id::text
                )
                union all
                select incoming_player ->> 'team_slot_id' as team_slot_id,
                       incoming_player ->> 'normalized_name' as normalized_name
                from jsonb_array_elements(p_players) as incoming_player
            )
            select 1
            from effective_players
            group by team_slot_id, normalized_name
            having count(*) > 1
        ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if not v_tournament_exists then
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
            p_tournament ->> 'status', 'group_rotation', (p_tournament ->> 'group_count')::integer, 1
        );
        v_current_revision := 1;
    else
        if v_existing_owner_id is distinct from auth.uid()
            or v_owner_id is distinct from v_existing_owner_id
            or v_format <> 'group_rotation'
            or v_group_count <> (p_tournament ->> 'group_count')::integer then
            return query select 'unauthorized'::text, v_current_revision;
            return;
        end if;
        if p_expected_revision <> v_current_revision then
            return query select 'stale_write'::text, v_current_revision;
            return;
        end if;

        if (
            select count(*)
            from public.tournament_group_pairings as existing_pairing
            where existing_pairing.tournament_id = v_tournament_id
        ) <> jsonb_array_length(v_pairings)
            or exists (
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
            )
            or exists (
                select 1
                from jsonb_to_recordset(v_pairings) as incoming_pairing(
                    first_group text, second_group text, pairing_key text
                )
                where not exists (
                    select 1
                    from public.tournament_group_pairings as existing_pairing
                    where existing_pairing.tournament_id = v_tournament_id
                        and existing_pairing.first_group = incoming_pairing.first_group
                        and existing_pairing.second_group = incoming_pairing.second_group
                )
            ) then
            return query select 'validation_failure'::text, v_current_revision;
            return;
        end if;

        if exists (
            select 1
            from jsonb_to_recordset(p_team_slots) as incoming_slot(
                id uuid, tournament_id uuid, slot_number integer,
                team_name text, status text, "group" text
            )
            where not exists (
                select 1
                from public.tournament_team_slots as existing_slot
                where existing_slot.id = incoming_slot.id
                    and existing_slot.tournament_id = v_tournament_id
                    and existing_slot.slot_number = incoming_slot.slot_number
            )
        )
            or exists (
                select 1
                from jsonb_to_recordset(p_team_slots) as incoming_slot(
                    id uuid, tournament_id uuid, slot_number integer,
                    team_name text, status text, "group" text
                )
                join public.tournament_team_slots as existing_slot
                    on existing_slot.id = incoming_slot.id
                where existing_slot.tournament_id <> v_tournament_id
                    or existing_slot.slot_number <> incoming_slot.slot_number
            )
            or exists (
                select 1
                from public.tournament_team_slots as existing_slot
                where existing_slot.tournament_id = v_tournament_id
                    and not exists (
                        select 1
                        from jsonb_to_recordset(p_team_slots) as incoming_slot(
                            id uuid, tournament_id uuid, slot_number integer,
                            team_name text, status text, "group" text
                        )
                        where incoming_slot.id = existing_slot.id
                            and incoming_slot.slot_number = existing_slot.slot_number
                    )
            )
            or exists (
                select 1
                from jsonb_to_recordset(p_team_slots) as incoming_slot(
                    id uuid, tournament_id uuid, slot_number integer,
                    team_name text, status text, "group" text
                )
                join public.tournament_team_slots as existing_slot
                    on existing_slot.id = incoming_slot.id
                    and existing_slot.tournament_id = v_tournament_id
                    and existing_slot.slot_number = incoming_slot.slot_number
                where existing_slot."group" is distinct from incoming_slot."group"
                    or (
                        btrim(coalesce(existing_slot.team_name, '')) <> ''
                        and existing_slot.team_name is distinct from incoming_slot.team_name
                    )
            ) then
            return query select 'validation_failure'::text, v_current_revision;
            return;
        end if;

        if exists (
            select 1
            from public.tournament_group_pairing_lobby_slots as existing_mapping
            where existing_mapping.tournament_id = v_tournament_id
                and (
                    not exists (
                        select 1
                        from public.tournament_group_pairings as existing_pairing
                        where existing_pairing.tournament_id = v_tournament_id
                            and existing_pairing.pairing_key = existing_mapping.pairing_key
                    )
                    or not exists (
                        select 1
                        from jsonb_array_elements(v_mappings) as incoming_mapping
                        where incoming_mapping ->> 'pairing_key' = existing_mapping.pairing_key
                    )
                )
        )
            or exists (
                select 1
                from public.tournament_group_pairing_lobby_slots as existing_mapping
                where existing_mapping.tournament_id = v_tournament_id
                group by existing_mapping.pairing_key
                having count(*) <> 12
                    or count(distinct existing_mapping.lobby_slot_number) <> 12
                    or count(distinct existing_mapping.team_slot_number) <> 12
            )
            or exists (
                select 1
                from public.tournament_group_pairing_lobby_slots as existing_mapping
                left join public.tournament_team_slots as existing_slot
                    on existing_slot.tournament_id = existing_mapping.tournament_id
                    and existing_slot.slot_number = existing_mapping.team_slot_number
                where existing_mapping.tournament_id = v_tournament_id
                    and (
                        existing_slot.id is null
                        or btrim(coalesce(existing_slot.team_name, '')) = ''
                    )
            )
            or exists (
                select 1
                from public.matches as existing_match
                where existing_match.tournament_id = v_tournament_id
                    and (
                        existing_match.group_pairing_key is null
                        or not exists (
                            select 1
                            from public.tournament_group_pairings as existing_pairing
                            where existing_pairing.tournament_id = v_tournament_id
                                and existing_pairing.pairing_key = existing_match.group_pairing_key
                        )
                        or not exists (
                            select 1
                            from public.tournament_group_pairing_lobby_slots as existing_mapping
                            where existing_mapping.tournament_id = v_tournament_id
                                and existing_mapping.pairing_key = existing_match.group_pairing_key
                        )
                    )
        ) then
            return query select 'validation_failure'::text, v_current_revision;
            return;
        end if;

        if exists (
            select 1
            from public.matches as existing_match
            where existing_match.tournament_id = v_tournament_id
                and exists (
                    select 1
                    from public.tournament_group_pairing_lobby_slots as stored_mapping
                    where stored_mapping.tournament_id = v_tournament_id
                        and stored_mapping.pairing_key = existing_match.group_pairing_key
                        and (
                            not exists (
                                select 1
                                from jsonb_array_elements(v_mappings) as incoming_mapping
                                where incoming_mapping ->> 'pairing_key' = stored_mapping.pairing_key
                                    and (incoming_mapping ->> 'lobby_slot_number')::integer = stored_mapping.lobby_slot_number
                                    and (incoming_mapping ->> 'team_slot_number')::integer = stored_mapping.team_slot_number
                            )
                            or exists (
                                select 1
                                from jsonb_array_elements(v_mappings) as incoming_mapping
                                where incoming_mapping ->> 'pairing_key' = stored_mapping.pairing_key
                                    and not exists (
                                        select 1
                                        from public.tournament_group_pairing_lobby_slots as stored_mapping_same_key
                                        where stored_mapping_same_key.tournament_id = v_tournament_id
                                            and stored_mapping_same_key.pairing_key = incoming_mapping ->> 'pairing_key'
                                            and stored_mapping_same_key.lobby_slot_number = (incoming_mapping ->> 'lobby_slot_number')::integer
                                            and stored_mapping_same_key.team_slot_number = (incoming_mapping ->> 'team_slot_number')::integer
                                    )
                            )
                        )
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
        id uuid, tournament_id uuid, slot_number integer,
        team_name text, status text, "group" text
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
    set team_slot_id = excluded.team_slot_id,
        display_name = excluded.display_name,
        normalized_name = excluded.normalized_name,
        revision = player_target.revision + 1,
        updated_at = now();

    insert into public.tournament_group_pairings (tournament_id, first_group, second_group)
    select v_tournament_id, first_group, second_group
    from jsonb_to_recordset(v_pairings) as pairing_row(
        first_group text, second_group text, pairing_key text
    )
    on conflict (tournament_id, first_group, second_group) do nothing;

    for v_pairing_key in
        select distinct mapping_row.pairing_key
        from jsonb_to_recordset(v_mappings) as mapping_row(
            tournament_id uuid, pairing_key text,
            lobby_slot_number integer, team_slot_number integer
        )
    loop
        if exists (
            select 1
            from public.tournament_group_pairing_lobby_slots as existing_mapping
            where existing_mapping.tournament_id = v_tournament_id
                and existing_mapping.pairing_key = v_pairing_key
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
                where incoming_mapping.pairing_key = v_pairing_key
                    and not exists (
                        select 1
                        from public.tournament_group_pairing_lobby_slots as existing_mapping
                        where existing_mapping.tournament_id = v_tournament_id
                            and existing_mapping.pairing_key = incoming_mapping.pairing_key
                            and existing_mapping.lobby_slot_number = incoming_mapping.lobby_slot_number
                            and existing_mapping.team_slot_number = incoming_mapping.team_slot_number
                    )
            ) then
            delete from public.tournament_group_pairing_lobby_slots
            where tournament_id = v_tournament_id
                and pairing_key = v_pairing_key;

            insert into public.tournament_group_pairing_lobby_slots (
                tournament_id, pairing_key, lobby_slot_number, team_slot_number
            )
            select tournament_id, pairing_key, lobby_slot_number, team_slot_number
            from jsonb_to_recordset(v_mappings) as incoming_mapping(
                tournament_id uuid, pairing_key text,
                lobby_slot_number integer, team_slot_number integer
            )
            where incoming_mapping.pairing_key = v_pairing_key;
        end if;
    end loop;

    return query select 'success'::text, v_current_revision;
end;
$$;

revoke all on function public.write_tournament_snapshot_v3(jsonb, jsonb, jsonb, jsonb, integer) from public;
revoke all on function public.write_tournament_snapshot_v3(jsonb, jsonb, jsonb, jsonb, integer) from anon;
grant execute on function public.write_tournament_snapshot_v3(jsonb, jsonb, jsonb, jsonb, integer) to authenticated;
