-- v0.6.8.1: server-authoritative finalized-result corrections with immutable audit rows.

create table public.match_correction_audit_entries (
    id uuid primary key default gen_random_uuid(),
    tournament_id uuid not null references public.tournaments(id) on delete cascade,
    match_id uuid not null references public.matches(id) on delete cascade,
    match_result_id uuid not null references public.match_results(id),
    team_slot_id uuid not null references public.tournament_team_slots(id),
    previous_placement integer not null,
    previous_kills integer not null,
    corrected_placement integer not null,
    corrected_kills integer not null,
    previous_revision integer not null,
    new_revision integer not null,
    corrected_by uuid not null references auth.users(id),
    corrected_at timestamptz not null default now(),
    correction_reason text
);

create index match_correction_audit_entries_match_id_corrected_at_idx
    on public.match_correction_audit_entries (match_id, corrected_at);

alter table public.match_correction_audit_entries enable row level security;

create policy match_correction_audit_entries_select_owner
on public.match_correction_audit_entries
for select to authenticated
using (
    exists (
        select 1 from public.tournaments as t
        where t.id = match_correction_audit_entries.tournament_id
          and t.owner_id = auth.uid()
    )
);

grant select on table public.match_correction_audit_entries to authenticated;

-- A valid correction may swap two placements. Deferring this existing
-- uniqueness rule until the transaction ends preserves the invariant while
-- allowing the atomic multi-row replacement.
alter table public.match_results
    drop constraint if exists match_results_match_placement_key;
alter table public.match_results
    add constraint match_results_match_placement_key
    unique (match_id, placement) deferrable initially immediate;

-- Direct authenticated writes may alter drafts only. The two narrowly scoped
-- SECURITY DEFINER RPCs retain responsibility for finalization and correction.
drop policy if exists matches_insert_owner on public.matches;
create policy matches_insert_owner
on public.matches
for insert to authenticated
with check (
    status = 'draft'
    and exists (
        select 1 from public.tournaments as t
        where t.id = matches.tournament_id and t.owner_id = auth.uid()
    )
);

drop policy if exists matches_update_owner on public.matches;
create policy matches_update_owner
on public.matches
for update to authenticated
using (
    status = 'draft'
    and exists (
        select 1 from public.tournaments as t
        where t.id = matches.tournament_id and t.owner_id = auth.uid()
    )
)
with check (
    status = 'draft'
    and exists (
        select 1 from public.tournaments as t
        where t.id = matches.tournament_id and t.owner_id = auth.uid()
    )
);

drop policy if exists matches_delete_owner on public.matches;
create policy matches_delete_owner
on public.matches
for delete to authenticated
using (
    status = 'draft'
    and exists (
        select 1 from public.tournaments as t
        where t.id = matches.tournament_id and t.owner_id = auth.uid()
    )
);

drop policy if exists match_results_update_owner on public.match_results;
create policy match_results_update_owner
on public.match_results
for update to authenticated
using (
    exists (
        select 1
        from public.matches as m
        join public.tournaments as t on t.id = m.tournament_id
        join public.tournament_team_slots as s on s.id = match_results.team_slot_id
        where m.id = match_results.match_id
          and m.status = 'draft'
          and s.tournament_id = m.tournament_id
          and t.owner_id = auth.uid()
    )
)
with check (
    exists (
        select 1
        from public.matches as m
        join public.tournaments as t on t.id = m.tournament_id
        join public.tournament_team_slots as s on s.id = match_results.team_slot_id
        where m.id = match_results.match_id
          and m.status = 'draft'
          and s.tournament_id = m.tournament_id
          and t.owner_id = auth.uid()
    )
);

drop policy if exists match_results_insert_owner on public.match_results;
create policy match_results_insert_owner
on public.match_results
for insert to authenticated
with check (
    exists (
        select 1 from public.matches as m
        join public.tournaments as t on t.id = m.tournament_id
        join public.tournament_team_slots as s on s.id = match_results.team_slot_id
        where m.id = match_results.match_id
          and m.status = 'draft'
          and s.tournament_id = m.tournament_id
          and t.owner_id = auth.uid()
    )
);

drop policy if exists match_results_delete_owner on public.match_results;
create policy match_results_delete_owner
on public.match_results
for delete to authenticated
using (
    exists (
        select 1 from public.matches as m
        join public.tournaments as t on t.id = m.tournament_id
        join public.tournament_team_slots as s on s.id = match_results.team_slot_id
        where m.id = match_results.match_id
          and m.status = 'draft'
          and s.tournament_id = m.tournament_id
          and t.owner_id = auth.uid()
    )
);

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
    v_result_count integer;
    v_distinct_slots integer;
    v_distinct_placements integer;
    v_invalid_values boolean;
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

    select count(*), count(distinct incoming.team_slot_id), count(distinct incoming.placement),
           bool_or(incoming.placement not between 1 and 12 or incoming.kills < 0)
    into v_result_count, v_distinct_slots, v_distinct_placements, v_invalid_values
    from jsonb_to_recordset(p_match_results) as incoming(
        id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
        source text, review_status text
    ) where incoming.match_id = p_match_id;

    select count(*) = 12 and not exists (
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
    ) into v_complete_existing_identity
    from public.match_results as existing_result
    where existing_result.match_id = p_match_id;

    if v_result_count <> 12 or v_distinct_slots <> 12 or v_distinct_placements <> 12
       or coalesce(v_invalid_values, true) or not v_complete_existing_identity then
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
