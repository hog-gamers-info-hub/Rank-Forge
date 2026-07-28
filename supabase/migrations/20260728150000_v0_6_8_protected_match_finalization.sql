-- v0.6.8: forward-compatible protected finalization and revision-safe RPC fixes.
-- This function deliberately uses a narrowly scoped security definer so it can return a
-- deterministic ownership result while still enforcing auth.uid() for every mutation.

-- These privileges reach the existing RLS ownership policies; they do not bypass them.
grant select, insert, update, delete on table public.tournaments to authenticated;
grant select, insert, update, delete on table public.tournament_team_slots to authenticated;
grant select, insert, update, delete on table public.players to authenticated;
grant select, insert, update, delete on table public.matches to authenticated;
grant select, insert, update, delete on table public.match_results to authenticated;
grant select on table public.tournaments to anon;
grant select on table public.tournament_team_slots to anon;
grant select on table public.players to anon;
grant select on table public.matches to anon;
grant select on table public.match_results to anon;

create or replace function public.write_tournament_snapshot(
    p_tournament jsonb,
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
    v_tournament_id uuid := (p_tournament ->> 'id')::uuid;
    v_owner_id uuid := (p_tournament ->> 'owner_id')::uuid;
    v_current_revision integer;
begin
    if p_expected_revision is null or p_expected_revision < 0 then
        return query select 'missing_revision'::text, null::integer;
        return;
    end if;

    select t.revision into v_current_revision
    from public.tournaments as t
    where t.id = v_tournament_id
    for update;

    if not found then
        if p_expected_revision <> 0 or v_owner_id is distinct from auth.uid() then
            return query select 'missing_revision'::text, null::integer;
            return;
        end if;
        insert into public.tournaments (
            id, owner_id, name, tournament_date, organizer_name, organizer_contact, status, revision
        ) values (
            v_tournament_id, v_owner_id, p_tournament ->> 'name',
            (p_tournament ->> 'tournament_date')::date, p_tournament ->> 'organizer_name',
            p_tournament ->> 'organizer_contact', p_tournament ->> 'status', 1
        );
        v_current_revision := 1;
    else
        if p_expected_revision <> v_current_revision then
            return query select 'stale_write'::text, v_current_revision;
            return;
        end if;
        update public.tournaments as t
        set name = p_tournament ->> 'name',
            tournament_date = (p_tournament ->> 'tournament_date')::date,
            organizer_name = p_tournament ->> 'organizer_name',
            organizer_contact = p_tournament ->> 'organizer_contact',
            status = p_tournament ->> 'status', revision = t.revision + 1, updated_at = now()
        where t.id = v_tournament_id;
        v_current_revision := v_current_revision + 1;
    end if;

    insert into public.tournament_team_slots as team_slot_target (id, tournament_id, slot_number, team_name, status)
    select id, tournament_id, slot_number, team_name, status
    from jsonb_to_recordset(p_team_slots) as slot_row(
        id uuid, tournament_id uuid, slot_number integer, team_name text, status text
    )
    on conflict (id) do update
    set team_name = excluded.team_name, status = excluded.status,
        revision = team_slot_target.revision + 1, updated_at = now();

    insert into public.players as player_target (id, team_slot_id, display_name, normalized_name)
    select id, team_slot_id, display_name, normalized_name
    from jsonb_to_recordset(p_players) as player_row(
        id uuid, team_slot_id uuid, display_name text, normalized_name text
    )
    on conflict (id) do update
    set display_name = excluded.display_name, normalized_name = excluded.normalized_name,
        revision = player_target.revision + 1, updated_at = now();

    return query select 'success'::text, v_current_revision;
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
    v_result_count integer;
    v_distinct_slots integer;
    v_distinct_placements integer;
    v_invalid_values boolean;
    v_slots_belong_to_tournament boolean;
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

    select
        count(*),
        count(distinct team_slot_id),
        count(distinct placement),
        bool_or(placement not between 1 and 12 or kills < 0)
    into v_result_count, v_distinct_slots, v_distinct_placements, v_invalid_values
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

    select coalesce(bool_and(team_slot_id in (
        select id from public.tournament_team_slots where tournament_id = p_tournament_id
    )), false)
    into v_slots_belong_to_tournament
    from jsonb_to_recordset(p_match_results) as result_row(
        id uuid,
        match_id uuid,
        team_slot_id uuid,
        placement integer,
        kills integer,
        source text,
        review_status text
    );

    if v_result_count <> 12
        or v_distinct_slots <> 12
        or v_distinct_placements <> 12
        or coalesce(v_invalid_values, true)
        or not v_slots_belong_to_tournament then
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

-- Draft and restoration writes are never allowed to replace an already-finalized cloud match.
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
            id uuid,
            tournament_id uuid,
            match_number integer,
            match_date date,
            map_name text,
            status text
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
            id uuid,
            match_id uuid,
            team_slot_id uuid,
            placement integer,
            kills integer,
            source text,
            review_status text
        ) on incoming_result.match_id = existing_match.id
        where existing_match.status = 'finalized'
    ) then
        return query select 'finalized_protected'::text, v_current_revision;
        return;
    end if;

    insert into public.matches as match_target (id, tournament_id, match_number, match_date, map_name, status)
    select id, tournament_id, match_number, match_date, map_name, status
    from jsonb_to_recordset(p_matches) as match_row(
        id uuid,
        tournament_id uuid,
        match_number integer,
        match_date date,
        map_name text,
        status text
    )
    on conflict (id) do update
    set match_number = excluded.match_number, match_date = excluded.match_date,
        map_name = excluded.map_name, status = excluded.status,
        revision = match_target.revision + 1, updated_at = now();

    insert into public.match_results as match_result_target (id, match_id, team_slot_id, placement, kills, source, review_status)
    select id, match_id, team_slot_id, placement, kills, source, review_status
    from jsonb_to_recordset(p_match_results) as result_row(
        id uuid, match_id uuid, team_slot_id uuid, placement integer, kills integer,
        source text, review_status text
    )
    on conflict (id) do update
    set placement = excluded.placement, kills = excluded.kills, source = excluded.source,
        review_status = excluded.review_status,
        revision = match_result_target.revision + 1, updated_at = now();

    update public.tournaments as t set revision = t.revision + 1, updated_at = now()
    where t.id = p_tournament_id;
    return query select 'success'::text, v_current_revision + 1;
end;
$$;

revoke all on function public.finalize_match_snapshot(uuid, jsonb, jsonb, integer) from public;
grant execute on function public.finalize_match_snapshot(uuid, jsonb, jsonb, integer) to authenticated;
