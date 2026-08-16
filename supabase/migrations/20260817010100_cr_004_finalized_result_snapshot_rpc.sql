-- CR-004: finalize the complete team/player snapshot as one protected transaction.

create or replace function public.finalize_match_snapshot(
    p_tournament_id uuid,
    p_match jsonb,
    p_match_results jsonb,
    p_expected_revision integer
)
returns table(outcome text, revision integer)
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
    v_distinct_snapshot_slots integer;
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

    if jsonb_typeof(p_match_results) is distinct from 'array'
        or jsonb_array_length(p_match_results) <> 12 then
        return query select 'validation_failure'::text, null::integer;
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
        count(distinct result_row.team_slot_id),
        count(distinct result_row.team_slot_number_snapshot),
        count(distinct result_row.placement),
        bool_or(
            result_row.id is null
            or result_row.match_id is distinct from v_match_id
            or result_row.team_slot_id is null
            or result_row.team_slot_number_snapshot not between 1 and 12
            or result_row.team_name_snapshot is null
            or btrim(result_row.team_name_snapshot) = ''
            or result_row.placement not between 1 and 12
            or result_row.kills < 0
            or result_row.placement_points is distinct from case result_row.placement
                when 1 then 12
                when 2 then 9
                when 3 then 8
                when 4 then 7
                when 5 then 6
                when 6 then 5
                when 7 then 4
                when 8 then 3
                when 9 then 2
                when 10 then 1
                when 11 then 0
                when 12 then 0
                else null
            end
            or result_row.kill_points is distinct from result_row.kills
            or result_row.total_points is distinct from
                result_row.placement_points + result_row.kill_points
            or result_row.source not in ('manual', 'ocr_assisted')
            or result_row.review_status <> 'confirmed'
            or jsonb_typeof(result_row.players) is distinct from 'array'
        )
    into
        v_result_count,
        v_distinct_slots,
        v_distinct_snapshot_slots,
        v_distinct_placements,
        v_invalid_values
    from jsonb_to_recordset(p_match_results) as result_row(
        id uuid,
        match_id uuid,
        team_slot_id uuid,
        team_slot_number_snapshot integer,
        team_name_snapshot text,
        placement integer,
        kills integer,
        placement_points integer,
        kill_points integer,
        total_points integer,
        source text,
        review_status text,
        players jsonb
    );

    select not exists (
        select 1
        from jsonb_to_recordset(p_match_results) as result_row(
            id uuid,
            match_id uuid,
            team_slot_id uuid,
            team_slot_number_snapshot integer,
            team_name_snapshot text,
            placement integer,
            kills integer,
            placement_points integer,
            kill_points integer,
            total_points integer,
            source text,
            review_status text,
            players jsonb
        )
        left join public.tournament_team_slots as team_slot
            on team_slot.id = result_row.team_slot_id
            and team_slot.tournament_id = p_tournament_id
            and team_slot.slot_number = result_row.team_slot_number_snapshot
        where team_slot.id is null
    ) into v_slots_belong_to_tournament;

    if v_result_count <> 12
        or v_distinct_slots <> 12
        or v_distinct_snapshot_slots <> 12
        or v_distinct_placements <> 12
        or coalesce(v_invalid_values, true)
        or not v_slots_belong_to_tournament then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_to_recordset(p_match_results) as result_row(
            id uuid, match_id uuid, team_slot_id uuid,
            team_slot_number_snapshot integer, team_name_snapshot text,
            placement integer, kills integer, placement_points integer,
            kill_points integer, total_points integer, source text,
            review_status text, players jsonb
        )
        cross join lateral jsonb_to_recordset(result_row.players) as player_row(
            id uuid,
            player_id uuid,
            roster_position_snapshot integer,
            player_name_snapshot text
        )
        where player_row.id is null
            or player_row.roster_position_snapshot is null
            or player_row.roster_position_snapshot <= 0
            or player_row.player_name_snapshot is null
            or btrim(player_row.player_name_snapshot) = ''
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from (
            select
                result_row.id as match_result_id,
                count(*) as player_count,
                count(distinct player_row.id) as distinct_snapshot_ids,
                count(distinct player_row.roster_position_snapshot) as distinct_positions,
                count(player_row.player_id) as referenced_player_count,
                count(distinct player_row.player_id) as distinct_referenced_players
            from jsonb_to_recordset(p_match_results) as result_row(
                id uuid, match_id uuid, team_slot_id uuid,
                team_slot_number_snapshot integer, team_name_snapshot text,
                placement integer, kills integer, placement_points integer,
                kill_points integer, total_points integer, source text,
                review_status text, players jsonb
            )
            cross join lateral jsonb_to_recordset(result_row.players) as player_row(
                id uuid,
                player_id uuid,
                roster_position_snapshot integer,
                player_name_snapshot text
            )
            group by result_row.id
        ) as player_counts
        where player_count <> distinct_snapshot_ids
            or player_count <> distinct_positions
            or referenced_player_count <> distinct_referenced_players
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_to_recordset(p_match_results) as result_row(
            id uuid, match_id uuid, team_slot_id uuid,
            team_slot_number_snapshot integer, team_name_snapshot text,
            placement integer, kills integer, placement_points integer,
            kill_points integer, total_points integer, source text,
            review_status text, players jsonb
        )
        cross join lateral jsonb_to_recordset(result_row.players) as player_row(
            id uuid,
            player_id uuid,
            roster_position_snapshot integer,
            player_name_snapshot text
        )
        where player_row.player_id is not null
            and not exists (
                select 1
                from public.players as live_player
                where live_player.id = player_row.player_id
                    and live_player.team_slot_id = result_row.team_slot_id
            )
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    delete from public.match_results
    where match_id = v_match_id;

    insert into public.match_results (
        id, match_id, team_slot_id,
        team_slot_number_snapshot, team_name_snapshot,
        placement, kills, placement_points, kill_points, total_points,
        source, review_status
    )
    select
        id, match_id, team_slot_id,
        team_slot_number_snapshot, team_name_snapshot,
        placement, kills, placement_points, kill_points, total_points,
        source, review_status
    from jsonb_to_recordset(p_match_results) as result_row(
        id uuid, match_id uuid, team_slot_id uuid,
        team_slot_number_snapshot integer, team_name_snapshot text,
        placement integer, kills integer, placement_points integer,
        kill_points integer, total_points integer, source text,
        review_status text, players jsonb
    );

    insert into public.match_result_players (
        id, match_result_id, player_id,
        roster_position_snapshot, player_name_snapshot
    )
    select
        player_row.id,
        result_row.id,
        player_row.player_id,
        player_row.roster_position_snapshot,
        player_row.player_name_snapshot
    from jsonb_to_recordset(p_match_results) as result_row(
        id uuid, match_id uuid, team_slot_id uuid,
        team_slot_number_snapshot integer, team_name_snapshot text,
        placement integer, kills integer, placement_points integer,
        kill_points integer, total_points integer, source text,
        review_status text, players jsonb
    )
    cross join lateral jsonb_to_recordset(result_row.players) as player_row(
        id uuid,
        player_id uuid,
        roster_position_snapshot integer,
        player_name_snapshot text
    );

    update public.matches as m
    set status = 'finalized',
        finalized_at = now(),
        finalized_by = auth.uid(),
        revision = m.revision + 1,
        updated_at = now()
    where m.id = v_match_id;

    update public.tournaments as t
    set revision = t.revision + 1,
        updated_at = now()
    where t.id = p_tournament_id;

    return query select 'success'::text, v_current_revision + 1;
end;
$$;

revoke all on function public.finalize_match_snapshot(uuid, jsonb, jsonb, integer) from public;
grant execute on function public.finalize_match_snapshot(uuid, jsonb, jsonb, integer) to authenticated;
