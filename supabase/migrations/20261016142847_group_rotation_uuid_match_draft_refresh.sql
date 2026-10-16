create or replace function public.write_match_snapshot_v4(
    p_tournament_id uuid,
    p_match jsonb,
    p_match_identity jsonb,
    p_match_results jsonb,
    p_expected_revision integer,
    p_request_id uuid
)
returns table (outcome text, revision integer)
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
    v_owner_id uuid;
    v_current_revision integer;
    v_new_revision integer;
    v_match_id uuid;
    v_pairing_key text;
    v_payload_hash text;
    v_existing_request public.group_rotation_match_uuid_write_requests%rowtype;
    v_existing_header public.group_rotation_match_uuid_snapshots%rowtype;
begin
    if auth.uid() is null then
        return query select 'authentication_required'::text, null::integer;
        return;
    end if;

    if p_expected_revision is null or p_expected_revision < 0 or p_request_id is null then
        return query select 'missing_revision'::text, null::integer;
        return;
    end if;

    if coalesce(jsonb_typeof(p_match), '') <> 'object'
        or coalesce(jsonb_typeof(p_match_identity), '') <> 'array'
        or coalesce(jsonb_typeof(p_match_results), '') <> 'array'
        or coalesce(jsonb_typeof(p_match -> 'id'), '') <> 'string'
        or coalesce(jsonb_typeof(p_match -> 'tournament_id'), '') <> 'string'
        or coalesce(jsonb_typeof(p_match -> 'pairing_key'), '') <> 'string'
        or (p_match ->> 'id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        or (p_match ->> 'tournament_id') <> p_tournament_id::text
        or btrim(p_match ->> 'pairing_key') = '' then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    v_match_id := (p_match ->> 'id')::uuid;
    v_pairing_key := p_match ->> 'pairing_key';
    v_payload_hash := md5(
        coalesce(p_match::text, '') || '|' ||
        coalesce(p_match_identity::text, '') || '|' ||
        coalesce(p_match_results::text, '') || '|' ||
        p_expected_revision::text
    );

    select tournament_row.owner_id, tournament_row.revision
    into v_owner_id, v_current_revision
    from public.tournaments as tournament_row
    where tournament_row.id = p_tournament_id
        and tournament_row.format = 'group_rotation'
    for update;

    if not found then
        return query select 'missing_data'::text, null::integer;
        return;
    end if;
    if v_owner_id is distinct from auth.uid() then
        return query select 'unauthorized'::text, v_current_revision;
        return;
    end if;

    select *
    into v_existing_request
    from public.group_rotation_match_uuid_write_requests
    where request_id = p_request_id
    for update;

    if found then
        if v_existing_request.operation <> 'write'
            or v_existing_request.tournament_id <> p_tournament_id
            or v_existing_request.match_id <> v_match_id
            or v_existing_request.expected_revision <> p_expected_revision
            or v_existing_request.payload_hash <> v_payload_hash then
            return query select 'validation_failure'::text, v_current_revision;
            return;
        end if;
        return query select v_existing_request.outcome, v_existing_request.revision;
        return;
    end if;

    if not exists (
        select 1
        from public.matches as match_row
        where match_row.id = v_match_id
            and match_row.tournament_id = p_tournament_id
            and match_row.status = 'draft'
            and match_row.group_pairing_key = v_pairing_key
    ) then
        if exists (
            select 1
            from public.matches as match_row
            where match_row.id = v_match_id
                and match_row.tournament_id = p_tournament_id
                and match_row.status = 'finalized'
        ) then
            return query select 'already_finalized'::text, v_current_revision;
            return;
        end if;
        return query select 'missing_data'::text, v_current_revision;
        return;
    end if;

    if p_expected_revision <> v_current_revision then
        return query select 'stale_write'::text, v_current_revision;
        return;
    end if;

    v_new_revision := v_current_revision + 1;

    if jsonb_array_length(p_match_identity) > 12
        or exists (
            select 1
            from jsonb_array_elements(p_match_identity) as identity_row
            where coalesce(jsonb_typeof(identity_row), '') <> 'object'
                or coalesce(jsonb_typeof(identity_row -> 'lobby_slot_number'), '') <> 'number'
                or coalesce(jsonb_typeof(identity_row -> 'team_uuid'), '') <> 'string'
                or (identity_row ->> 'lobby_slot_number') !~ '^([1-9]|1[0-2])$'
                or (identity_row ->> 'team_uuid') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
                or not exists (
                    select 1
                    from public.tournament_team_identities as identity_registry
                    where identity_registry.tournament_id = p_tournament_id
                        and identity_registry.team_uuid::text = identity_row ->> 'team_uuid'
                )
        )
        or (
            select count(distinct identity_row ->> 'lobby_slot_number')
            from jsonb_array_elements(p_match_identity) as identity_row
        ) <> jsonb_array_length(p_match_identity)
        or (
            select count(distinct identity_row ->> 'team_uuid')
            from jsonb_array_elements(p_match_identity) as identity_row
        ) <> jsonb_array_length(p_match_identity)
    then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_match_results) as result_row
        where coalesce(jsonb_typeof(result_row), '') <> 'object'
            or coalesce(jsonb_typeof(result_row -> 'team_uuid'), '') <> 'string'
            or coalesce(jsonb_typeof(result_row -> 'participation_status'), '') <> 'string'
            or coalesce(jsonb_typeof(result_row -> 'kills'), '') <> 'number'
            or (result_row ->> 'team_uuid') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (result_row ->> 'kills') !~ '^[0-9]+$'
            or (result_row ? 'placement') and result_row -> 'placement' <> 'null'::jsonb
                and (coalesce(jsonb_typeof(result_row -> 'placement'), '') <> 'number'
                    or (result_row ->> 'placement') !~ '^[0-9]+$')
            or (result_row ? 'point_adjustment') and result_row -> 'point_adjustment' <> 'null'::jsonb
                and (coalesce(jsonb_typeof(result_row -> 'point_adjustment'), '') <> 'number'
                    or (result_row ->> 'point_adjustment') !~ '^-?[0-9]+$')
            or (result_row ->> 'participation_status') not in ('PARTICIPATED', 'NO_SHOW')
            or (result_row ->> 'team_uuid') not in (
                select identity_row ->> 'team_uuid'
                from jsonb_array_elements(p_match_identity) as identity_row
            )
            or ((result_row ->> 'participation_status') = 'PARTICIPATED'
                and ((result_row ->> 'placement') is not null
                    and ((result_row ->> 'placement')::integer < 1
                        or (result_row ->> 'placement')::integer > 12)))
            or ((result_row ->> 'participation_status') = 'NO_SHOW'
                and ((result_row ->> 'placement') is not null
                    or (result_row ->> 'kills') <> '0'))
    )
    or (
        select count(distinct result_row ->> 'team_uuid')
        from jsonb_array_elements(p_match_results) as result_row
    ) <> jsonb_array_length(p_match_results)
    or exists (
        select 1
        from jsonb_array_elements(p_match_results) as result_row
        where result_row -> 'placement' <> 'null'::jsonb
        group by result_row ->> 'placement'
        having count(*) > 1
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    select *
    into v_existing_header
    from public.group_rotation_match_uuid_snapshots
    where match_id = v_match_id
    for update;

    if found then
        if v_existing_header.tournament_id <> p_tournament_id
            or v_existing_header.pairing_key <> v_pairing_key
            or v_existing_header.status <> 'draft' then
            return query select 'validation_failure'::text, v_current_revision;
            return;
        end if;

        delete from public.group_rotation_match_uuid_results
        where match_id = v_match_id;
        delete from public.group_rotation_match_uuid_assignments
        where match_id = v_match_id;
    else
        insert into public.group_rotation_match_uuid_snapshots (
            match_id, tournament_id, pairing_key, status, revision
        ) values (
            v_match_id, p_tournament_id, v_pairing_key, 'draft', v_new_revision
        );
    end if;

    insert into public.group_rotation_match_uuid_assignments (
        match_id, tournament_id, lobby_slot_number, team_uuid
    )
    select v_match_id, p_tournament_id,
           (identity_row ->> 'lobby_slot_number')::integer,
           (identity_row ->> 'team_uuid')::uuid
    from jsonb_array_elements(p_match_identity) as identity_row;

    insert into public.group_rotation_match_uuid_results (
        match_id, tournament_id, team_uuid, participation_status,
        placement, kills, point_adjustment
    )
    select v_match_id, p_tournament_id,
           (result_row ->> 'team_uuid')::uuid,
           result_row ->> 'participation_status',
           case when result_row -> 'placement' = 'null'::jsonb
               then null else (result_row ->> 'placement')::integer end,
           (result_row ->> 'kills')::integer,
           coalesce(nullif(result_row ->> 'point_adjustment', ''), '0')::integer
    from jsonb_array_elements(p_match_results) as result_row;

    update public.group_rotation_match_uuid_snapshots
    set revision = v_new_revision, updated_at = timezone('utc'::text, now())
    where match_id = v_match_id;
    update public.matches as match_target
    set revision = match_target.revision + 1,
        updated_at = timezone('utc'::text, now())
    where match_target.id = v_match_id;
    update public.tournaments
    set revision = v_new_revision, updated_at = timezone('utc'::text, now())
    where id = p_tournament_id;

    insert into public.group_rotation_match_uuid_write_requests (
        request_id, operation, tournament_id, match_id, expected_revision,
        payload_hash, outcome, revision
    ) values (
        p_request_id, 'write', p_tournament_id, v_match_id, p_expected_revision,
        v_payload_hash, 'success', v_new_revision
    );

    return query select 'success'::text, v_new_revision;
end;
$$;
