create or replace function public.finalize_match_snapshot(
    p_tournament_id uuid,
    p_match jsonb,
    p_match_results jsonb,
    p_expected_revision integer
)
returns table (outcome text, revision integer)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_match_id uuid := (p_match ->> 'id')::uuid;
    v_owner_id uuid;
    v_current_revision integer;
    v_match_status text;
    v_structural_slot_count integer;
    v_active_count integer := 0;
    v_seen_blank boolean := false;
    v_has_gap boolean := false;
    v_active_slot_ids uuid[] := '{}'::uuid[];
    v_slot record;
    v_result_count integer;
    v_distinct_slots integer;
    v_distinct_placements integer;
    v_values_valid boolean;
    v_slots_belong_to_active boolean;
    v_all_results_match boolean;
begin
    if auth.uid() is null then
        return query select 'authentication_required'::text, null::integer;
        return;
    end if;

    if p_expected_revision is null or p_expected_revision <= 0 then
        return query select 'missing_revision'::text, null::integer;
        return;
    end if;

    select t.owner_id, t.revision into v_owner_id, v_current_revision
    from public.tournaments as t
    where t.id = p_tournament_id
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

    select m.status into v_match_status
    from public.matches as m
    where m.id = v_match_id and m.tournament_id = p_tournament_id
    for update;

    if not found then
        return query select 'missing_data'::text, v_current_revision;
        return;
    end if;

    if v_match_status = 'finalized' then
        return query select 'already_finalized'::text, v_current_revision;
        return;
    end if;

    if v_match_status <> 'draft' or p_match ->> 'status' <> 'finalized' then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    select count(*)
    into v_structural_slot_count
    from public.tournament_team_slots
    where tournament_id = p_tournament_id;

    for v_slot in
        select id, slot_number, team_name
        from public.tournament_team_slots
        where tournament_id = p_tournament_id
        order by slot_number
    loop
        if btrim(coalesce(v_slot.team_name, '')) = '' then
            v_seen_blank := true;
        else
            if v_seen_blank then
                v_has_gap := true;
            end if;
            v_active_count := v_active_count + 1;
            v_active_slot_ids := array_append(v_active_slot_ids, v_slot.id);
        end if;
    end loop;

    if v_structural_slot_count <> 12 or v_active_count <= 0 or v_has_gap then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    select
        count(*),
        count(distinct team_slot_id),
        count(distinct placement),
        coalesce(bool_and(
            placement is not null
            and placement between 1 and v_active_count
            and kills is not null
            and kills >= 0
        ), false)
    into v_result_count, v_distinct_slots, v_distinct_placements, v_values_valid
    from jsonb_to_recordset(p_match_results) as result_row(
        id uuid,
        match_id uuid,
        team_slot_id uuid,
        placement integer,
        kills integer,
        source text,
        review_status text
    )
    where result_row.match_id = v_match_id;

    select coalesce(bool_and(result_row.match_id = v_match_id), false)
    into v_all_results_match
    from jsonb_to_recordset(p_match_results) as result_row(
        id uuid,
        match_id uuid,
        team_slot_id uuid,
        placement integer,
        kills integer,
        source text,
        review_status text
    );

    select coalesce(bool_and(result_row.team_slot_id = any(v_active_slot_ids)), false)
    into v_slots_belong_to_active
    from jsonb_to_recordset(p_match_results) as result_row(
        id uuid,
        match_id uuid,
        team_slot_id uuid,
        placement integer,
        kills integer,
        source text,
        review_status text
    )
    where result_row.match_id = v_match_id;

    if v_result_count <> v_active_count
        or v_distinct_slots <> v_active_count
        or v_distinct_placements <> v_active_count
        or not v_values_valid
        or not v_slots_belong_to_active
        or not v_all_results_match then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    delete from public.match_results where match_id = v_match_id;

    insert into public.match_results (
        id, match_id, team_slot_id, placement, kills, source, review_status
    )
    select id, match_id, team_slot_id, placement, kills, source, review_status
    from jsonb_to_recordset(p_match_results) as result_row(
        id uuid,
        match_id uuid,
        team_slot_id uuid,
        placement integer,
        kills integer,
        source text,
        review_status text
    );

    update public.matches as m
    set
        status = 'finalized',
        finalized_at = now(),
        finalized_by = auth.uid(),
        revision = m.revision + 1,
        updated_at = now()
    where m.id = v_match_id;

    update public.tournaments as t
    set revision = t.revision + 1, updated_at = now()
    where t.id = p_tournament_id;

    return query select 'success'::text, v_current_revision + 1;
end;
$$;

revoke all on function public.finalize_match_snapshot(uuid, jsonb, jsonb, integer) from public;
grant execute on function public.finalize_match_snapshot(uuid, jsonb, jsonb, integer) to authenticated;
