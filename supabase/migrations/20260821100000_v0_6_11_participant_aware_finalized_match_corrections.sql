-- v0.6.11: participant-aware corrections for already-finalized match snapshots.
-- Finalized result identity is authoritative for correction; current tournament
-- participation is used only to validate the persisted team-slot references.

create or replace function public.correct_finalized_match_snapshot(
    p_tournament_id uuid,
    p_match_id uuid,
    p_match_results jsonb,
    p_expected_revision integer,
    p_correction_reason text default null
)
returns table (outcome text, revision integer)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_owner_id uuid;
    v_current_revision integer;
    v_match_status text;
    v_existing_result_count integer;
    v_existing_identity_coherent boolean;
    v_result_count integer;
    v_distinct_result_ids integer;
    v_distinct_slots integer;
    v_distinct_placements integer;
    v_values_are_valid boolean;
    v_complete_existing_identity boolean;
    v_values_are_unchanged boolean;
begin
    set constraints match_results_match_placement_key deferred;
    if auth.uid() is null then
        return query select 'authentication_required'::text, null::integer;
        return;
    end if;
    if p_expected_revision is null or p_expected_revision <= 0 then
        return query select 'missing_revision'::text, null::integer;
        return;
    end if;

    select t.owner_id, t.revision into v_owner_id, v_current_revision
    from public.tournaments as t where t.id = p_tournament_id for update;
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

    select m.status into v_match_status
    from public.matches as m
    where m.id = p_match_id and m.tournament_id = p_tournament_id
    for update;
    if not found then
        return query select 'missing_data'::text, v_current_revision;
        return;
    end if;
    if v_match_status <> 'finalized' then
        return query select 'match_not_finalized'::text, v_current_revision;
        return;
    end if;

    -- The persisted finalized rows define the immutable participant count and
    -- team-slot identity. Do not derive either from the current tournament names
    -- or from the incoming correction payload.
    select count(*)
    into v_existing_result_count
    from public.match_results as current_result
    where current_result.match_id = p_match_id;

    select
        count(*) = v_existing_result_count
        and count(distinct current_result.team_slot_id) = v_existing_result_count
        and count(distinct team_slot.slot_number) = v_existing_result_count
        and count(*) filter (
            where team_slot.tournament_id = p_tournament_id
              and team_slot.slot_number between 1 and v_existing_result_count
        ) = v_existing_result_count
        and not exists (
            select 1
            from public.match_results as malformed_result
            left join public.tournament_team_slots as malformed_slot
              on malformed_slot.id = malformed_result.team_slot_id
            where malformed_result.match_id = p_match_id
              and (
                  malformed_slot.id is null
                  or malformed_slot.tournament_id is distinct from p_tournament_id
                  or malformed_slot.slot_number not between 1 and 12
              )
        )
    into v_existing_identity_coherent
    from public.match_results as current_result
    left join public.tournament_team_slots as team_slot
      on team_slot.id = current_result.team_slot_id
    where current_result.match_id = p_match_id;

    if v_existing_result_count <= 0
       or v_existing_result_count > 12
       or not v_existing_identity_coherent then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    select
        count(*),
        count(distinct incoming.id),
        count(distinct incoming.team_slot_id),
        count(distinct incoming.placement),
        coalesce(bool_and(
            incoming.placement between 1 and v_existing_result_count
            and incoming.kills >= 0
        ), false)
    into v_result_count, v_distinct_result_ids, v_distinct_slots,
         v_distinct_placements, v_values_are_valid
    from jsonb_to_recordset(p_match_results) as incoming(
        id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
        source text, review_status text
    )
    where incoming.match_id = p_match_id;

    select
        v_result_count = v_existing_result_count
        and v_distinct_result_ids = v_existing_result_count
        and v_distinct_slots = v_existing_result_count
        and not exists (
            select 1
            from jsonb_to_recordset(p_match_results) as incoming(
                id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
                source text, review_status text
            )
            left join public.match_results as current_result
              on current_result.id = incoming.id
             and current_result.match_id = p_match_id
             and current_result.team_slot_id = incoming.team_slot_id
            where incoming.match_id = p_match_id and current_result.id is null
        )
        and not exists (
            select 1
            from public.match_results as current_result
            left join jsonb_to_recordset(p_match_results) as incoming(
                id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
                source text, review_status text
            )
              on incoming.id = current_result.id
             and incoming.match_id = p_match_id
            where current_result.match_id = p_match_id and incoming.id is null
        )
    into v_complete_existing_identity;

    if v_result_count <> v_existing_result_count
       or v_distinct_result_ids <> v_existing_result_count
       or v_distinct_slots <> v_existing_result_count
       or v_distinct_placements <> v_existing_result_count
       or not v_values_are_valid
       or not v_complete_existing_identity then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    select not exists (
        select 1
        from public.match_results as current_result
        join jsonb_to_recordset(p_match_results) as incoming(
            id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
            source text, review_status text
        ) on incoming.id = current_result.id
        where current_result.match_id = p_match_id
          and (current_result.placement, current_result.kills)
              is distinct from (incoming.placement, incoming.kills)
    ) into v_values_are_unchanged;

    if v_values_are_unchanged then
        return query select 'already_corrected'::text, v_current_revision;
        return;
    end if;

    insert into public.match_correction_audit_entries (
        tournament_id, match_id, match_result_id, team_slot_id,
        previous_placement, previous_kills, corrected_placement, corrected_kills,
        previous_revision, new_revision, corrected_by, correction_reason
    )
    select p_tournament_id, p_match_id, current_result.id, current_result.team_slot_id,
           current_result.placement, current_result.kills, incoming.placement, incoming.kills,
           v_current_revision, v_current_revision + 1, auth.uid(), p_correction_reason
    from public.match_results as current_result
    join jsonb_to_recordset(p_match_results) as incoming(
        id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
        source text, review_status text
    ) on incoming.id = current_result.id
    where current_result.match_id = p_match_id;

    update public.match_results as current_result
    set placement = incoming.placement,
        kills = incoming.kills,
        revision = current_result.revision + 1,
        updated_at = now()
    from jsonb_to_recordset(p_match_results) as incoming(
        id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
        source text, review_status text
    )
    where current_result.id = incoming.id and current_result.match_id = p_match_id;

    update public.matches as m
    set revision = m.revision + 1, updated_at = now()
    where m.id = p_match_id;
    update public.tournaments as t
    set revision = t.revision + 1, updated_at = now()
    where t.id = p_tournament_id;

    return query select 'success'::text, v_current_revision + 1;
end;
$$;

revoke all on function public.correct_finalized_match_snapshot(uuid, uuid, jsonb, integer, text) from public;
grant execute on function public.correct_finalized_match_snapshot(uuid, uuid, jsonb, integer, text) to authenticated;
