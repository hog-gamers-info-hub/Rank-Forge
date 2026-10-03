-- Batch 2: persist the selected pairing on every match and make the pairing
-- the single source of truth for eligible participants.
alter table public.matches
    add column if not exists group_pairing_key text;

create index if not exists matches_tournament_group_pairing_idx
    on public.matches (tournament_id, group_pairing_key);

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conrelid = 'public.matches'::regclass
          and conname = 'matches_group_pairing_fkey'
    ) then
        alter table public.matches
            add constraint matches_group_pairing_fkey
            foreign key (tournament_id, group_pairing_key)
            references public.tournament_group_pairings (tournament_id, pairing_key);
    end if;
end;
$$;

create or replace function public.validate_match_group_pairing()
returns trigger
language plpgsql
set search_path = public
as $$
declare
    v_format text;
begin
    if tg_op = 'UPDATE'
       and (old.tournament_id, old.group_pairing_key)
           is distinct from (new.tournament_id, new.group_pairing_key)
       and old.group_pairing_key is not null then
        raise exception 'Match group pairing is immutable';
    end if;

    select format into v_format
    from public.tournaments
    where id = new.tournament_id;

    if v_format = 'standard' and new.group_pairing_key is not null then
        raise exception 'Standard matches cannot specify a group pairing';
    end if;
    if v_format = 'group_rotation' and new.group_pairing_key is null then
        raise exception 'Group Rotation matches require a group pairing';
    end if;
    return new;
end;
$$;

drop trigger if exists matches_group_pairing_guard on public.matches;
create trigger matches_group_pairing_guard
before insert or update of tournament_id, group_pairing_key on public.matches
for each row execute function public.validate_match_group_pairing();

create or replace function public.write_match_snapshot(
    p_tournament_id uuid,
    p_matches jsonb,
    p_match_results jsonb,
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

    select t.revision into v_current_revision
    from public.tournaments as t
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
        from public.matches existing_match
        join jsonb_to_recordset(p_matches) as incoming_match(
            id uuid, tournament_id uuid, match_number integer, match_date date,
            map_name text, status text, group_pairing_key text
        ) on incoming_match.id = existing_match.id
        where existing_match.status = 'finalized'
    ) then
        return query select 'finalized_protected'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from public.matches existing_match
        join jsonb_to_recordset(p_match_results) as incoming_result(
            id uuid, match_id uuid, team_slot_id uuid, placement integer,
            kills integer, source text, review_status text
        ) on incoming_result.match_id = existing_match.id
        where existing_match.status = 'finalized'
    ) then
        return query select 'finalized_protected'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_to_recordset(p_matches) as incoming_match(
            id uuid, tournament_id uuid, match_number integer, match_date date,
            map_name text, status text, group_pairing_key text
        )
        where incoming_match.tournament_id <> p_tournament_id
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

    insert into public.match_results as match_result_target (
        id, match_id, team_slot_id, placement, kills, source, review_status
    )
    select id, match_id, team_slot_id, placement, kills, source, review_status
    from jsonb_to_recordset(p_match_results) as result_row(
        id uuid, match_id uuid, team_slot_id uuid, placement integer,
        kills integer, source text, review_status text
    )
    on conflict (id) do update
    set placement = excluded.placement,
        kills = excluded.kills,
        source = excluded.source,
        review_status = excluded.review_status,
        revision = match_result_target.revision + 1,
        updated_at = now();

    update public.tournaments as t
    set revision = t.revision + 1, updated_at = now()
    where t.id = p_tournament_id;
    return query select 'success'::text, v_current_revision + 1;
end;
$$;

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
    v_group_pairing_key text;
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

    select m.status, m.group_pairing_key
    into v_match_status, v_group_pairing_key
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
    if v_group_pairing_key is distinct from nullif(p_match ->> 'group_pairing_key', '') then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    select count(*), count(*) filter (where btrim(coalesce(slot.team_name, '')) <> ''),
           array_agg(slot.id order by slot.slot_number)
    into v_structural_slot_count, v_active_count, v_active_slot_ids
    from public.tournament_team_slots as slot
    where slot.tournament_id = p_tournament_id
      and (
          v_group_pairing_key is null
          or slot."group" in (
              select pairing.first_group from public.tournament_group_pairings as pairing
              where pairing.tournament_id = p_tournament_id
                and pairing.pairing_key = v_group_pairing_key
              union
              select pairing.second_group from public.tournament_group_pairings as pairing
              where pairing.tournament_id = p_tournament_id
                and pairing.pairing_key = v_group_pairing_key
          )
      );
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
                and result_row.placement is null and result_row.kills = 0)
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

revoke all on function public.write_match_snapshot(uuid, jsonb, jsonb, integer) from public;
grant execute on function public.write_match_snapshot(uuid, jsonb, jsonb, integer) to authenticated;
revoke all on function public.finalize_match_snapshot(uuid, jsonb, jsonb, integer) from public;
grant execute on function public.finalize_match_snapshot(uuid, jsonb, jsonb, integer) to authenticated;
