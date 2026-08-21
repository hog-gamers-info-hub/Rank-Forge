-- v0.6.12: explicit participant status in finalized snapshots and corrections.

alter table public.match_results
    add column if not exists participation_status text not null default 'PARTICIPATED';

alter table public.match_results
    drop constraint if exists match_results_participation_status_check;
alter table public.match_results
    add constraint match_results_participation_status_check
    check (participation_status in ('PARTICIPATED', 'NO_SHOW'));

alter table public.match_results
    drop constraint if exists match_results_participation_values_check;
alter table public.match_results
    add constraint match_results_participation_values_check
    check (
        (participation_status = 'PARTICIPATED' and placement is not null and kills >= 0)
        or
        (participation_status = 'NO_SHOW' and placement is null and kills = 0)
    );

alter table public.match_correction_audit_entries
    add column if not exists previous_participation_status text not null default 'PARTICIPATED',
    add column if not exists corrected_participation_status text not null default 'PARTICIPATED';

alter table public.match_correction_audit_entries
    alter column previous_placement drop not null,
    alter column corrected_placement drop not null;

alter table public.match_correction_audit_entries
    drop constraint if exists match_correction_audit_entries_participation_status_check;
alter table public.match_correction_audit_entries
    add constraint match_correction_audit_entries_participation_status_check
    check (
        previous_participation_status in ('PARTICIPATED', 'NO_SHOW')
        and corrected_participation_status in ('PARTICIPATED', 'NO_SHOW')
        and (
            (previous_participation_status = 'PARTICIPATED' and previous_placement is not null and previous_kills >= 0)
            or (previous_participation_status = 'NO_SHOW' and previous_placement is null and previous_kills = 0)
        )
        and (
            (corrected_participation_status = 'PARTICIPATED' and corrected_placement is not null and corrected_kills >= 0)
            or (corrected_participation_status = 'NO_SHOW' and corrected_placement is null and corrected_kills = 0)
        )
    );

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
    v_active_count integer;
    v_result_count integer;
    v_distinct_result_ids integer;
    v_distinct_slots integer;
    v_distinct_placements integer;
    v_participated_count integer;
    v_values_valid boolean;
    v_slots_belong_to_active boolean;
    v_all_results_match boolean;
    v_active_slot_ids uuid[];
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

    select count(*), count(*) filter (where btrim(coalesce(team_name, '')) <> ''),
           array_agg(id order by slot_number) filter (where btrim(coalesce(team_name, '')) <> '')
    into v_structural_slot_count, v_active_count, v_active_slot_ids
    from public.tournament_team_slots
    where tournament_id = p_tournament_id;
    if v_structural_slot_count <> 12 or v_active_count <= 0 then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    select count(*) filter (where coalesce(result_row.participation_status, 'PARTICIPATED') = 'PARTICIPATED')
    into v_participated_count
    from jsonb_to_recordset(p_match_results) as result_row(
        id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
        source text, review_status text, participation_status text
    );
    select count(*), count(distinct result_row.id), count(distinct result_row.team_slot_id),
           count(distinct result_row.placement) filter (where coalesce(result_row.participation_status, 'PARTICIPATED') = 'PARTICIPATED'),
           coalesce(bool_and(
               (coalesce(result_row.participation_status, 'PARTICIPATED') = 'PARTICIPATED'
                and result_row.placement is not null
                and result_row.placement between 1 and v_participated_count
                and result_row.kills >= 0)
               or (coalesce(result_row.participation_status, 'PARTICIPATED') = 'NO_SHOW'
                and result_row.placement is null
                and result_row.kills = 0)
           ), false)
    into v_result_count, v_distinct_result_ids, v_distinct_slots, v_distinct_placements,
         v_values_valid
    from jsonb_to_recordset(p_match_results) as result_row(
        id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
        source text, review_status text, participation_status text
    );

    select coalesce(bool_and(result_row.match_id = v_match_id), false)
    into v_all_results_match
    from jsonb_to_recordset(p_match_results) as result_row(
        id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
        source text, review_status text, participation_status text
    );
    select coalesce(bool_and(result_row.team_slot_id = any(v_active_slot_ids)), false)
    into v_slots_belong_to_active
    from jsonb_to_recordset(p_match_results) as result_row(
        id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
        source text, review_status text, participation_status text
    ) where result_row.match_id = v_match_id;

    if v_result_count <> v_active_count
       or v_distinct_result_ids <> v_active_count
       or v_distinct_slots <> v_active_count
       or v_participated_count <= 0
       or v_distinct_placements <> v_participated_count
       or not v_values_valid
       or not v_slots_belong_to_active
       or not v_all_results_match then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    delete from public.match_results where match_id = v_match_id;
    insert into public.match_results (
        id, match_id, team_slot_id, placement, kills, source, review_status, participation_status
    )
    select id, match_id, team_slot_id, placement, kills, source, review_status,
           coalesce(participation_status, 'PARTICIPATED')
    from jsonb_to_recordset(p_match_results) as result_row(
        id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
        source text, review_status text, participation_status text
    );

    update public.matches as finalized_match
    set status = 'finalized', finalized_at = now(), finalized_by = auth.uid(),
        revision = finalized_match.revision + 1, updated_at = now()
    where finalized_match.id = v_match_id;
    update public.tournaments as tournament_row
    set revision = tournament_row.revision + 1, updated_at = now()
    where tournament_row.id = p_tournament_id;
    return query select 'success'::text, v_current_revision + 1;
end;
$$;

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
    v_result_count integer;
    v_distinct_result_ids integer;
    v_distinct_slots integer;
    v_distinct_placements integer;
    v_participated_count integer;
    v_values_valid boolean;
    v_all_results_match boolean;
    v_complete_existing_identity boolean;
    v_values_unchanged boolean;
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

    select m.status into v_match_status from public.matches as m
    where m.id = p_match_id and m.tournament_id = p_tournament_id for update;
    if not found then
        return query select 'missing_data'::text, v_current_revision;
        return;
    end if;
    if v_match_status <> 'finalized' then
        return query select 'match_not_finalized'::text, v_current_revision;
        return;
    end if;

    select count(*) into v_existing_result_count
    from public.match_results where match_id = p_match_id;
    if v_existing_result_count <= 0 or v_existing_result_count > 12 then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    select count(*) = v_existing_result_count
       and count(distinct team_slot_id) = v_existing_result_count
       and not exists (
           select 1 from public.match_results as current_result
           left join public.tournament_team_slots as slot on slot.id = current_result.team_slot_id
           where current_result.match_id = p_match_id
             and (slot.id is null or slot.tournament_id is distinct from p_tournament_id)
       )
    into v_complete_existing_identity
    from public.match_results
    where match_id = p_match_id;
    if not v_complete_existing_identity then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    select count(*) filter (where coalesce(incoming.participation_status, 'PARTICIPATED') = 'PARTICIPATED')
    into v_participated_count
    from jsonb_to_recordset(p_match_results) as incoming(
        id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
        source text, review_status text, participation_status text
    );
    select count(*), count(distinct incoming.id), count(distinct incoming.team_slot_id),
           count(distinct incoming.placement) filter (where coalesce(incoming.participation_status, 'PARTICIPATED') = 'PARTICIPATED'),
           coalesce(bool_and(
               (coalesce(incoming.participation_status, 'PARTICIPATED') = 'PARTICIPATED'
                and incoming.placement is not null and incoming.placement between 1 and v_participated_count
                and incoming.kills >= 0)
               or (coalesce(incoming.participation_status, 'PARTICIPATED') = 'NO_SHOW'
                and incoming.placement is null and incoming.kills = 0)
           ), false)
    into v_result_count, v_distinct_result_ids, v_distinct_slots, v_distinct_placements,
         v_values_valid
    from jsonb_to_recordset(p_match_results) as incoming(
        id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
        source text, review_status text, participation_status text
    );
    select coalesce(bool_and(incoming.match_id = p_match_id), false)
    into v_all_results_match
    from jsonb_to_recordset(p_match_results) as incoming(
        id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
        source text, review_status text, participation_status text
    );
    if v_result_count <> v_existing_result_count
       or v_distinct_result_ids <> v_existing_result_count
       or v_distinct_slots <> v_existing_result_count
       or v_participated_count <= 0
       or v_distinct_placements <> v_participated_count
       or not v_values_valid
       or not v_all_results_match
       or exists (
           select 1 from jsonb_to_recordset(p_match_results) as incoming(
               id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
               source text, review_status text, participation_status text
           )
           left join public.match_results as current_result
             on current_result.id = incoming.id
            and current_result.match_id = p_match_id
            and current_result.team_slot_id = incoming.team_slot_id
           where incoming.match_id = p_match_id and current_result.id is null
       )
       or exists (
           select 1 from public.match_results as current_result
           left join jsonb_to_recordset(p_match_results) as incoming(
               id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
               source text, review_status text, participation_status text
           ) on incoming.id = current_result.id and incoming.match_id = p_match_id
           where current_result.match_id = p_match_id and incoming.id is null
       ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    select not exists (
        select 1 from public.match_results as current_result
        join jsonb_to_recordset(p_match_results) as incoming(
            id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
            source text, review_status text, participation_status text
        ) on incoming.id = current_result.id
        where current_result.match_id = p_match_id
          and (current_result.placement, current_result.kills, current_result.participation_status)
              is distinct from (incoming.placement, incoming.kills, coalesce(incoming.participation_status, 'PARTICIPATED'))
    ) into v_values_unchanged;
    if v_values_unchanged then
        return query select 'already_corrected'::text, v_current_revision;
        return;
    end if;

    insert into public.match_correction_audit_entries (
        tournament_id, match_id, match_result_id, team_slot_id,
        previous_placement, previous_kills, corrected_placement, corrected_kills,
        previous_participation_status, corrected_participation_status,
        previous_revision, new_revision, corrected_by, correction_reason
    )
    select p_tournament_id, p_match_id, current_result.id, current_result.team_slot_id,
           current_result.placement, current_result.kills, incoming.placement, incoming.kills,
           current_result.participation_status, coalesce(incoming.participation_status, 'PARTICIPATED'),
           v_current_revision, v_current_revision + 1, auth.uid(), p_correction_reason
    from public.match_results as current_result
    join jsonb_to_recordset(p_match_results) as incoming(
        id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
        source text, review_status text, participation_status text
    ) on incoming.id = current_result.id
    where current_result.match_id = p_match_id;

    update public.match_results as current_result
    set participation_status = coalesce(incoming.participation_status, 'PARTICIPATED'),
        placement = incoming.placement,
        kills = incoming.kills,
        revision = current_result.revision + 1,
        updated_at = now()
    from jsonb_to_recordset(p_match_results) as incoming(
        id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
        source text, review_status text, participation_status text
    )
    where current_result.id = incoming.id and current_result.match_id = p_match_id;

    update public.matches as corrected_match
    set revision = corrected_match.revision + 1, updated_at = now()
    where corrected_match.id = p_match_id;
    update public.tournaments as tournament_row
    set revision = tournament_row.revision + 1, updated_at = now()
    where tournament_row.id = p_tournament_id;
    return query select 'success'::text, v_current_revision + 1;
end;
$$;

revoke all on function public.finalize_match_snapshot(uuid, jsonb, jsonb, integer) from public;
grant execute on function public.finalize_match_snapshot(uuid, jsonb, jsonb, integer) to authenticated;
revoke all on function public.correct_finalized_match_snapshot(uuid, uuid, jsonb, integer, text) from public;
grant execute on function public.correct_finalized_match_snapshot(uuid, uuid, jsonb, integer, text) to authenticated;
