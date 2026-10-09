-- Phase 4D3: mapping-aware finalized Group Rotation corrections.
-- The legacy correction RPC remains the Standard-tournament path.

create or replace function public.correct_finalized_match_snapshot_v2(
    p_tournament_id uuid,
    p_match_id uuid,
    p_match_results jsonb,
    p_expected_revision integer,
    p_correction_reason text default null
)
returns table (outcome text, revision integer)
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    v_owner_id uuid;
    v_current_revision integer;
    v_format text;
    v_group_count integer;
    v_match_status text;
    v_pairing_key text;
    v_mapping_count integer;
    v_distinct_mapping_lobbies integer;
    v_distinct_mapping_slots integer;
    v_existing_result_count integer;
    v_distinct_result_ids integer;
    v_distinct_result_slots integer;
    v_result_count integer;
    v_distinct_incoming_ids integer;
    v_distinct_incoming_slots integer;
    v_participated_count integer;
    v_distinct_placements integer;
    v_values_valid boolean;
    v_all_results_match boolean;
    v_values_unchanged boolean;
begin
    if auth.uid() is null then
        return query select 'authentication_required'::text, null::integer;
        return;
    end if;
    if p_expected_revision is null or p_expected_revision <= 0 then
        return query select 'missing_revision'::text, null::integer;
        return;
    end if;

    select tournament_row.owner_id,
           tournament_row.revision,
           tournament_row.format,
           tournament_row.group_count
    into v_owner_id, v_current_revision, v_format, v_group_count
    from public.tournaments as tournament_row
    where tournament_row.id = p_tournament_id
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
    if v_format <> 'group_rotation' or v_group_count not in (3, 4) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    select match_row.status, nullif(btrim(match_row.group_pairing_key), '')
    into v_match_status, v_pairing_key
    from public.matches as match_row
    where match_row.id = p_match_id
      and match_row.tournament_id = p_tournament_id
    for update;

    if not found then
        return query select 'missing_data'::text, v_current_revision;
        return;
    end if;
    if v_match_status <> 'finalized' then
        return query select 'match_not_finalized'::text, v_current_revision;
        return;
    end if;
    if v_pairing_key is null
       or not exists (
           select 1
           from public.tournament_group_pairings as pairing_row
           where pairing_row.tournament_id = p_tournament_id
             and pairing_row.pairing_key = v_pairing_key
       ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    select count(*),
           count(distinct mapping_row.lobby_slot_number),
           count(distinct mapping_row.team_slot_number)
    into v_mapping_count, v_distinct_mapping_lobbies, v_distinct_mapping_slots
    from public.tournament_group_pairing_lobby_slots as mapping_row
    where mapping_row.tournament_id = p_tournament_id
      and mapping_row.pairing_key = v_pairing_key;

    if v_mapping_count <> 12
       or v_distinct_mapping_lobbies <> 12
       or v_distinct_mapping_slots <> 12
       or exists (
           select 1
           from public.tournament_group_pairing_lobby_slots as mapping_row
           where mapping_row.tournament_id = p_tournament_id
             and mapping_row.pairing_key = v_pairing_key
             and mapping_row.lobby_slot_number not between 1 and 12
       )
       or exists (
           select 1
           from public.tournament_group_pairing_lobby_slots as mapping_row
           left join public.tournament_team_slots as slot_row
             on slot_row.tournament_id = mapping_row.tournament_id
            and slot_row.slot_number = mapping_row.team_slot_number
           where mapping_row.tournament_id = p_tournament_id
             and mapping_row.pairing_key = v_pairing_key
             and (
                 mapping_row.team_slot_number not between 1 and (v_group_count * 6)
                 or slot_row.id is null
                 or nullif(btrim(slot_row.team_name), '') is null
             )
       ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    select count(*),
           count(distinct result_row.id),
           count(distinct result_row.team_slot_id)
    into v_existing_result_count, v_distinct_result_ids, v_distinct_result_slots
    from public.match_results as result_row
    where result_row.match_id = p_match_id;

    if v_existing_result_count <> 12
       or v_distinct_result_ids <> 12
       or v_distinct_result_slots <> 12
       or exists (
           select 1
           from public.match_results as result_row
           left join public.tournament_team_slots as slot_row
             on slot_row.id = result_row.team_slot_id
           where result_row.match_id = p_match_id
             and (
                 slot_row.id is null
                 or slot_row.tournament_id is distinct from p_tournament_id
             )
       )
       or exists (
           select 1
           from public.tournament_group_pairing_lobby_slots as mapping_row
           left join public.tournament_team_slots as slot_row
             on slot_row.tournament_id = mapping_row.tournament_id
            and slot_row.slot_number = mapping_row.team_slot_number
           left join public.match_results as result_row
             on result_row.match_id = p_match_id
            and result_row.team_slot_id = slot_row.id
           where mapping_row.tournament_id = p_tournament_id
             and mapping_row.pairing_key = v_pairing_key
             and result_row.id is null
       )
       or exists (
           select 1
           from public.match_results as result_row
           join public.tournament_team_slots as slot_row
             on slot_row.id = result_row.team_slot_id
           left join public.tournament_group_pairing_lobby_slots as mapping_row
             on mapping_row.tournament_id = p_tournament_id
            and mapping_row.pairing_key = v_pairing_key
            and mapping_row.team_slot_number = slot_row.slot_number
           where result_row.match_id = p_match_id
             and mapping_row.lobby_slot_number is null
       ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if coalesce(jsonb_typeof(p_match_results), '') <> 'array' then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
           select 1
           from jsonb_array_elements(p_match_results) as result_value
           where coalesce(jsonb_typeof(result_value), '') <> 'object'
              or coalesce(jsonb_typeof(result_value -> 'id'), '') <> 'string'
              or (result_value ->> 'id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
              or coalesce(jsonb_typeof(result_value -> 'match_id'), '') <> 'string'
              or (result_value ->> 'match_id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
              or coalesce(jsonb_typeof(result_value -> 'team_slot_id'), '') <> 'string'
              or (result_value ->> 'team_slot_id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
              or coalesce(jsonb_typeof(result_value -> 'kills'), '') <> 'number'
              or (result_value ->> 'kills') !~ '^-?[0-9]+$'
              or (
                  result_value ? 'placement'
                  and jsonb_typeof(result_value -> 'placement') not in ('null', 'number')
              )
              or (
                  jsonb_typeof(result_value -> 'placement') = 'number'
                  and (result_value ->> 'placement') !~ '^-?[0-9]+$'
              )
              or (
                  result_value ? 'participation_status'
                  and jsonb_typeof(result_value -> 'participation_status') not in ('null', 'string')
              )
              or (
                  jsonb_typeof(result_value -> 'participation_status') = 'string'
                  and coalesce(result_value ->> 'participation_status', '') not in ('PARTICIPATED', 'NO_SHOW')
              )
       ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    select count(*),
           count(distinct incoming.id),
           count(distinct incoming.team_slot_id),
           count(*) filter (
               where coalesce(incoming.participation_status, 'PARTICIPATED') = 'PARTICIPATED'
           ),
           count(distinct incoming.placement) filter (
               where coalesce(incoming.participation_status, 'PARTICIPATED') = 'PARTICIPATED'
           )
    into v_result_count,
         v_distinct_incoming_ids,
         v_distinct_incoming_slots,
         v_participated_count,
         v_distinct_placements
    from jsonb_to_recordset(p_match_results) as incoming(
        id uuid,
        match_id uuid,
        team_slot_id uuid,
        placement integer,
        kills integer,
        source text,
        review_status text,
        participation_status text
    );

    select coalesce(bool_and(
        (
            coalesce(incoming.participation_status, 'PARTICIPATED') = 'PARTICIPATED'
            and incoming.placement is not null
            and incoming.placement between 1 and v_participated_count
            and incoming.kills >= 0
        )
        or (
            coalesce(incoming.participation_status, 'PARTICIPATED') = 'NO_SHOW'
            and incoming.placement is null
            and incoming.kills = 0
        )
    ), false)
    into v_values_valid
    from jsonb_to_recordset(p_match_results) as incoming(
        id uuid,
        match_id uuid,
        team_slot_id uuid,
        placement integer,
        kills integer,
        source text,
        review_status text,
        participation_status text
    );

    select coalesce(bool_and(incoming.match_id = p_match_id), false)
    into v_all_results_match
    from jsonb_to_recordset(p_match_results) as incoming(
        id uuid,
        match_id uuid,
        team_slot_id uuid,
        placement integer,
        kills integer,
        source text,
        review_status text,
        participation_status text
    );

    if v_result_count <> 12
       or v_distinct_incoming_ids <> 12
       or v_distinct_incoming_slots <> 12
       or v_participated_count <= 0
       or v_distinct_placements <> v_participated_count
       or not v_values_valid
       or not v_all_results_match
       or exists (
           select 1
           from jsonb_to_recordset(p_match_results) as incoming(
               id uuid,
               match_id uuid,
               team_slot_id uuid,
               placement integer,
               kills integer,
               source text,
               review_status text,
               participation_status text
           )
           left join public.match_results as existing_result
             on existing_result.id = incoming.id
            and existing_result.match_id = p_match_id
            and existing_result.team_slot_id = incoming.team_slot_id
           where existing_result.id is null
       )
       or exists (
           select 1
           from public.match_results as existing_result
           left join jsonb_to_recordset(p_match_results) as incoming(
               id uuid,
               match_id uuid,
               team_slot_id uuid,
               placement integer,
               kills integer,
               source text,
               review_status text,
               participation_status text
           )
             on incoming.id = existing_result.id
            and incoming.match_id = p_match_id
            and incoming.team_slot_id = existing_result.team_slot_id
           where existing_result.match_id = p_match_id
             and incoming.id is null
       )
       or exists (
           select 1
           from jsonb_to_recordset(p_match_results) as incoming(
               id uuid,
               match_id uuid,
               team_slot_id uuid,
               placement integer,
               kills integer,
               source text,
               review_status text,
               participation_status text
           )
           left join public.tournament_team_slots as slot_row
             on slot_row.id = incoming.team_slot_id
            and slot_row.tournament_id = p_tournament_id
           left join public.tournament_group_pairing_lobby_slots as mapping_row
             on mapping_row.tournament_id = p_tournament_id
            and mapping_row.pairing_key = v_pairing_key
            and mapping_row.team_slot_number = slot_row.slot_number
           where mapping_row.lobby_slot_number is null
       ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    select not exists (
        select 1
        from public.match_results as existing_result
        join jsonb_to_recordset(p_match_results) as incoming(
            id uuid,
            match_id uuid,
            team_slot_id uuid,
            placement integer,
            kills integer,
            source text,
            review_status text,
            participation_status text
        )
          on incoming.id = existing_result.id
        where existing_result.match_id = p_match_id
          and (
              existing_result.placement,
              existing_result.kills,
              existing_result.participation_status
          ) is distinct from (
              incoming.placement,
              incoming.kills,
              coalesce(incoming.participation_status, 'PARTICIPATED')
          )
    )
    into v_values_unchanged;

    if v_values_unchanged then
        return query select 'already_corrected'::text, v_current_revision;
        return;
    end if;

    set constraints match_results_match_placement_key deferred;

    insert into public.match_correction_audit_entries (
        tournament_id,
        match_id,
        match_result_id,
        team_slot_id,
        previous_placement,
        previous_kills,
        corrected_placement,
        corrected_kills,
        previous_participation_status,
        corrected_participation_status,
        previous_revision,
        new_revision,
        corrected_by,
        correction_reason
    )
    select p_tournament_id,
           p_match_id,
           existing_result.id,
           existing_result.team_slot_id,
           existing_result.placement,
           existing_result.kills,
           incoming.placement,
           incoming.kills,
           existing_result.participation_status,
           coalesce(incoming.participation_status, 'PARTICIPATED'),
           v_current_revision,
           v_current_revision + 1,
           auth.uid(),
           p_correction_reason
    from public.match_results as existing_result
    join jsonb_to_recordset(p_match_results) as incoming(
        id uuid,
        match_id uuid,
        team_slot_id uuid,
        placement integer,
        kills integer,
        source text,
        review_status text,
        participation_status text
    )
      on incoming.id = existing_result.id
    where existing_result.match_id = p_match_id;

    update public.match_results as existing_result
    set participation_status = coalesce(incoming.participation_status, 'PARTICIPATED'),
        placement = incoming.placement,
        kills = incoming.kills,
        revision = existing_result.revision + 1,
        updated_at = now()
    from jsonb_to_recordset(p_match_results) as incoming(
        id uuid,
        match_id uuid,
        team_slot_id uuid,
        placement integer,
        kills integer,
        source text,
        review_status text,
        participation_status text
    )
    where existing_result.id = incoming.id
      and existing_result.match_id = p_match_id;

    update public.matches as match_row
    set revision = match_row.revision + 1,
        updated_at = now()
    where match_row.id = p_match_id
      and match_row.tournament_id = p_tournament_id;

    update public.tournaments as tournament_row
    set revision = tournament_row.revision + 1,
        updated_at = now()
    where tournament_row.id = p_tournament_id;

    return query select 'success'::text, v_current_revision + 1;
end;
$$;

revoke all on function public.correct_finalized_match_snapshot_v2(uuid, uuid, jsonb, integer, text)
    from public, anon;
grant execute on function public.correct_finalized_match_snapshot_v2(uuid, uuid, jsonb, integer, text)
    to authenticated;
