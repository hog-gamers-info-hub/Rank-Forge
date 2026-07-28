-- v0.6.7: owner-scoped aggregate revision writes. The tournament revision is the
-- concurrency token for a tournament, its roster, and its matches/results.

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

    select revision into v_current_revision
    from public.tournaments
    where id = v_tournament_id
    for update;

    if not found then
        if p_expected_revision <> 0 or v_owner_id is distinct from auth.uid() then
            return query select 'missing_revision'::text, null::integer;
            return;
        end if;

        insert into public.tournaments (
            id, owner_id, name, tournament_date, organizer_name, organizer_contact, status, revision
        )
        values (
            v_tournament_id,
            v_owner_id,
            p_tournament ->> 'name',
            (p_tournament ->> 'tournament_date')::date,
            p_tournament ->> 'organizer_name',
            p_tournament ->> 'organizer_contact',
            p_tournament ->> 'status',
            1
        );
        v_current_revision := 1;
    else
        if p_expected_revision <> v_current_revision then
            return query select 'stale_write'::text, v_current_revision;
            return;
        end if;

        update public.tournaments
        set
            name = p_tournament ->> 'name',
            tournament_date = (p_tournament ->> 'tournament_date')::date,
            organizer_name = p_tournament ->> 'organizer_name',
            organizer_contact = p_tournament ->> 'organizer_contact',
            status = p_tournament ->> 'status',
            revision = revision + 1,
            updated_at = now()
        where id = v_tournament_id;
        v_current_revision := v_current_revision + 1;
    end if;

    insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name, status)
    select id, tournament_id, slot_number, team_name, status
    from jsonb_to_recordset(p_team_slots) as slot_row(
        id uuid,
        tournament_id uuid,
        slot_number integer,
        team_name text,
        status text
    )
    on conflict (id) do update
    set
        team_name = excluded.team_name,
        status = excluded.status,
        revision = public.tournament_team_slots.revision + 1,
        updated_at = now();

    insert into public.players (id, team_slot_id, display_name, normalized_name)
    select id, team_slot_id, display_name, normalized_name
    from jsonb_to_recordset(p_players) as player_row(
        id uuid,
        team_slot_id uuid,
        display_name text,
        normalized_name text
    )
    on conflict (id) do update
    set
        display_name = excluded.display_name,
        normalized_name = excluded.normalized_name,
        revision = public.players.revision + 1,
        updated_at = now();

    return query select 'success'::text, v_current_revision;
end;
$$;

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

    select revision into v_current_revision
    from public.tournaments
    where id = p_tournament_id
    for update;

    if not found then
        return query select 'missing_revision'::text, null::integer;
        return;
    end if;

    if p_expected_revision <> v_current_revision then
        return query select 'stale_write'::text, v_current_revision;
        return;
    end if;

    insert into public.matches (id, tournament_id, match_number, match_date, map_name, status)
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
    set
        match_number = excluded.match_number,
        match_date = excluded.match_date,
        map_name = excluded.map_name,
        status = excluded.status,
        revision = public.matches.revision + 1,
        updated_at = now();

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
    )
    on conflict (id) do update
    set
        placement = excluded.placement,
        kills = excluded.kills,
        source = excluded.source,
        review_status = excluded.review_status,
        revision = public.match_results.revision + 1,
        updated_at = now();

    update public.tournaments
    set revision = revision + 1, updated_at = now()
    where id = p_tournament_id;

    return query select 'success'::text, v_current_revision + 1;
end;
$$;

revoke all on function public.write_tournament_snapshot(jsonb, jsonb, jsonb, integer) from public;
revoke all on function public.write_match_snapshot(uuid, jsonb, jsonb, integer) from public;
grant execute on function public.write_tournament_snapshot(jsonb, jsonb, jsonb, integer) to authenticated;
grant execute on function public.write_match_snapshot(uuid, jsonb, jsonb, integer) to authenticated;
