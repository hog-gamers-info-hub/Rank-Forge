-- Stage 2: pairing-specific cleared-name state for the Group Rotation V4
-- identity contract. Existing clients may omit name_cleared; omitted values
-- are stored as false. Registered team identities remain unchanged.

alter table public.tournament_group_pairing_team_assignments
    add column name_cleared boolean not null default false;

create or replace function public.write_group_rotation_uuid_identity_snapshot_v4(
    p_tournament_id uuid,
    p_team_identities jsonb,
    p_pairing_assignments jsonb,
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
    v_payload_hash text;
    v_existing_request public.group_rotation_uuid_identity_write_requests%rowtype;
begin
    if auth.uid() is null then
        return query select 'authentication_required'::text, null::integer;
        return;
    end if;

    if p_tournament_id is null
        or p_expected_revision is null
        or p_expected_revision <= 0
        or p_request_id is null then
        return query select 'missing_revision'::text, null::integer;
        return;
    end if;

    if coalesce(jsonb_typeof(p_team_identities), '') <> 'array'
        or coalesce(jsonb_typeof(p_pairing_assignments), '') <> 'array' then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_team_identities) as identity_row
        where coalesce(jsonb_typeof(identity_row), '') <> 'object'
            or coalesce(jsonb_typeof(identity_row -> 'team_uuid'), '') <> 'string'
            or coalesce(jsonb_typeof(identity_row -> 'display_name'), '') <> 'string'
            or coalesce(jsonb_typeof(identity_row -> 'normalized_name'), '') <> 'string'
            or (identity_row ->> 'team_uuid') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or btrim(identity_row ->> 'display_name') = ''
            or (identity_row ->> 'normalized_name') <> lower(
                regexp_replace(
                    regexp_replace(
                        identity_row ->> 'display_name',
                        '^[[:space:]]+|[[:space:]]+$',
                        '',
                        'g'
                    ),
                    '[[:space:]]+',
                    ' ',
                    'g'
                )
            )
            or (identity_row ->> 'normalized_name') = ''
    ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if (
        select count(distinct (identity_row ->> 'team_uuid')::uuid)
        from jsonb_array_elements(p_team_identities) as identity_row
    ) <> jsonb_array_length(p_team_identities)
        or (
            select count(distinct identity_row ->> 'normalized_name')
            from jsonb_array_elements(p_team_identities) as identity_row
        ) <> jsonb_array_length(p_team_identities) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_pairing_assignments) as pairing_row
        where coalesce(jsonb_typeof(pairing_row), '') <> 'object'
            or coalesce(jsonb_typeof(pairing_row -> 'pairing_key'), '') <> 'string'
            or coalesce(jsonb_typeof(pairing_row -> 'assignments'), '') <> 'array'
            or btrim(pairing_row ->> 'pairing_key') = ''
            or jsonb_array_length(pairing_row -> 'assignments') > 12
    ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if (
        select count(distinct pairing_row ->> 'pairing_key')
        from jsonb_array_elements(p_pairing_assignments) as pairing_row
    ) <> jsonb_array_length(p_pairing_assignments)
        or exists (
            select 1
            from jsonb_array_elements(p_pairing_assignments) as pairing_row
            where not exists (
                select 1
                from public.tournament_group_pairings as selected_pairing
                where selected_pairing.tournament_id = p_tournament_id
                    and selected_pairing.pairing_key = pairing_row ->> 'pairing_key'
            )
        ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_pairing_assignments) as pairing_row
        cross join lateral jsonb_array_elements(pairing_row -> 'assignments') as assignment_row
        where coalesce(jsonb_typeof(assignment_row), '') <> 'object'
            or coalesce(jsonb_typeof(assignment_row -> 'lobby_slot_number'), '') <> 'number'
            or coalesce(jsonb_typeof(assignment_row -> 'team_uuid'), '') <> 'string'
            or (
                assignment_row ? 'name_cleared'
                and coalesce(jsonb_typeof(assignment_row -> 'name_cleared'), '') <> 'boolean'
            )
            or (assignment_row ->> 'lobby_slot_number') !~ '^([1-9]|1[0-2])$'
            or (assignment_row ->> 'team_uuid') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_pairing_assignments) as pairing_row
        cross join lateral jsonb_array_elements(pairing_row -> 'assignments') as assignment_row
        group by pairing_row ->> 'pairing_key', assignment_row ->> 'lobby_slot_number'
        having count(*) > 1
    )
        or exists (
            select 1
            from jsonb_array_elements(p_pairing_assignments) as pairing_row
            cross join lateral jsonb_array_elements(pairing_row -> 'assignments') as assignment_row
            group by pairing_row ->> 'pairing_key', assignment_row ->> 'team_uuid'
            having count(*) > 1
        ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if exists (
        select 1
        from jsonb_to_recordset(p_team_identities) as incoming_identity(
            team_uuid uuid, display_name text, normalized_name text
        )
        join public.tournament_team_identities as foreign_identity
            on foreign_identity.team_uuid = incoming_identity.team_uuid
           and foreign_identity.tournament_id <> p_tournament_id
    ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_pairing_assignments) as pairing_row
        cross join lateral jsonb_array_elements(pairing_row -> 'assignments') as assignment_row
        where not exists (
            select 1
            from public.tournament_team_identities as current_identity
            where current_identity.tournament_id = p_tournament_id
                and current_identity.team_uuid = (assignment_row ->> 'team_uuid')::uuid
        )
        and not exists (
            select 1
            from jsonb_to_recordset(p_team_identities) as incoming_identity(
                team_uuid uuid, display_name text, normalized_name text
            )
            where incoming_identity.team_uuid = (assignment_row ->> 'team_uuid')::uuid
        )
    ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if exists (
        with incoming_identities as (
            select team_uuid, normalized_name
            from jsonb_to_recordset(p_team_identities) as identity_row(
                team_uuid uuid, display_name text, normalized_name text
            )
        ), effective_identities as (
            select current_identity.team_uuid, current_identity.normalized_name
            from public.tournament_team_identities as current_identity
            where current_identity.tournament_id = p_tournament_id
                and not exists (
                    select 1
                    from incoming_identities
                    where incoming_identities.team_uuid = current_identity.team_uuid
                )
            union all
            select incoming_identities.team_uuid, incoming_identities.normalized_name
            from incoming_identities
        )
        select 1
        from effective_identities
        group by normalized_name
        having count(*) > 1
    ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    v_payload_hash := md5(
        coalesce(p_team_identities::text, '') || '|' ||
        coalesce(p_pairing_assignments::text, '') || '|' ||
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
    from public.group_rotation_uuid_identity_write_requests
    where request_id = p_request_id
    for update;

    if found then
        if v_existing_request.tournament_id <> p_tournament_id
            or v_existing_request.expected_revision <> p_expected_revision
            or v_existing_request.payload_hash <> v_payload_hash then
            return query select 'validation_failure'::text, v_current_revision;
            return;
        end if;
        return query select v_existing_request.outcome, v_existing_request.revision;
        return;
    end if;

    if p_expected_revision <> v_current_revision then
        return query select 'stale_write'::text, v_current_revision;
        return;
    end if;

    v_new_revision := v_current_revision + 1;

    insert into public.tournament_team_identities (
        tournament_id, team_uuid, display_name, normalized_name
    )
    select p_tournament_id, incoming_identity.team_uuid,
           incoming_identity.display_name, incoming_identity.normalized_name
    from jsonb_to_recordset(p_team_identities) as incoming_identity(
        team_uuid uuid, display_name text, normalized_name text
    )
    on conflict (tournament_id, team_uuid) do update
    set display_name = excluded.display_name,
        normalized_name = excluded.normalized_name,
        updated_at = timezone('utc'::text, now());

    if jsonb_array_length(p_pairing_assignments) > 0 then
        delete from public.tournament_group_pairing_team_assignments as existing_assignment
        where existing_assignment.tournament_id = p_tournament_id
            and exists (
                select 1
                from jsonb_array_elements(p_pairing_assignments) as pairing_row
                where pairing_row ->> 'pairing_key' = existing_assignment.pairing_key
            );

        insert into public.tournament_group_pairing_team_assignments (
            tournament_id, pairing_key, lobby_slot_number, team_uuid, name_cleared
        )
        select p_tournament_id,
               pairing_row ->> 'pairing_key',
               (assignment_row ->> 'lobby_slot_number')::integer,
               (assignment_row ->> 'team_uuid')::uuid,
               coalesce((assignment_row ->> 'name_cleared')::boolean, false)
        from jsonb_array_elements(p_pairing_assignments) as pairing_row
        cross join lateral jsonb_array_elements(pairing_row -> 'assignments') as assignment_row;
    end if;

    update public.tournaments
    set revision = v_new_revision,
        updated_at = timezone('utc'::text, now())
    where id = p_tournament_id;

    insert into public.group_rotation_uuid_identity_write_requests (
        request_id, tournament_id, expected_revision, payload_hash,
        outcome, revision
    ) values (
        p_request_id, p_tournament_id, p_expected_revision, v_payload_hash,
        'success', v_new_revision
    );

    return query select 'success'::text, v_new_revision;
end;
$$;

revoke all on function public.write_group_rotation_uuid_identity_snapshot_v4(
    uuid, jsonb, jsonb, integer, uuid
) from public;
revoke all on function public.write_group_rotation_uuid_identity_snapshot_v4(
    uuid, jsonb, jsonb, integer, uuid
) from anon;
grant execute on function public.write_group_rotation_uuid_identity_snapshot_v4(
    uuid, jsonb, jsonb, integer, uuid
) to authenticated;
