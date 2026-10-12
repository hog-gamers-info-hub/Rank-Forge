-- Phase 3D-2B: UUID-native Group Rotation match cloud persistence.
-- This migration is additive. Standard and legacy numeric match RPCs remain unchanged.

create table public.group_rotation_match_uuid_snapshots (
    match_id uuid not null,
    tournament_id uuid not null,
    pairing_key text not null,
    status text not null default 'draft',
    revision integer not null default 0,
    finalized_at timestamptz,
    finalized_by uuid,
    created_at timestamptz not null default timezone('utc'::text, now()),
    updated_at timestamptz not null default timezone('utc'::text, now()),
    constraint group_rotation_match_uuid_snapshots_pkey
        primary key (match_id),
    constraint group_rotation_match_uuid_snapshots_match_tournament_unique
        unique (match_id, tournament_id),
    constraint group_rotation_match_uuid_snapshots_match_fkey
        foreign key (match_id, tournament_id)
        references public.matches(id, tournament_id)
        on delete cascade,
    constraint group_rotation_match_uuid_snapshots_pairing_fkey
        foreign key (tournament_id, pairing_key)
        references public.tournament_group_pairings(tournament_id, pairing_key),
    constraint group_rotation_match_uuid_snapshots_status_check
        check (status in ('draft', 'finalized')),
    constraint group_rotation_match_uuid_snapshots_revision_check
        check (revision >= 0),
    constraint group_rotation_match_uuid_snapshots_finalized_fields_check
        check (
            (status = 'draft' and finalized_at is null and finalized_by is null)
            or (status = 'finalized' and finalized_at is not null and finalized_by is not null)
        )
);

create table public.group_rotation_match_uuid_assignments (
    match_id uuid not null,
    tournament_id uuid not null,
    lobby_slot_number integer not null,
    team_uuid uuid not null,
    constraint group_rotation_match_uuid_assignments_pkey
        primary key (match_id, lobby_slot_number),
    constraint group_rotation_match_uuid_assignments_match_fkey
        foreign key (match_id, tournament_id)
        references public.group_rotation_match_uuid_snapshots(match_id, tournament_id)
        on delete cascade,
    constraint group_rotation_match_uuid_assignments_team_fkey
        foreign key (tournament_id, team_uuid)
        references public.tournament_team_identities(tournament_id, team_uuid),
    constraint group_rotation_match_uuid_assignments_team_unique
        unique (match_id, team_uuid),
    constraint group_rotation_match_uuid_assignments_lobby_check
        check (lobby_slot_number between 1 and 12)
);

create table public.group_rotation_match_uuid_results (
    match_id uuid not null,
    tournament_id uuid not null,
    team_uuid uuid not null,
    participation_status text not null,
    placement integer,
    kills integer not null,
    point_adjustment integer not null default 0,
    constraint group_rotation_match_uuid_results_pkey
        primary key (match_id, team_uuid),
    constraint group_rotation_match_uuid_results_match_fkey
        foreign key (match_id, tournament_id)
        references public.group_rotation_match_uuid_snapshots(match_id, tournament_id)
        on delete cascade,
    constraint group_rotation_match_uuid_results_team_fkey
        foreign key (tournament_id, team_uuid)
        references public.tournament_team_identities(tournament_id, team_uuid),
    constraint group_rotation_match_uuid_results_assignment_fkey
        foreign key (match_id, team_uuid)
        references public.group_rotation_match_uuid_assignments(match_id, team_uuid)
        on delete cascade,
    constraint group_rotation_match_uuid_results_status_check
        check (participation_status in ('PARTICIPATED', 'NO_SHOW')),
    constraint group_rotation_match_uuid_results_kills_check
        check (kills >= 0),
    constraint group_rotation_match_uuid_results_placement_check
        check (placement is null or placement between 1 and 12),
    constraint group_rotation_match_uuid_results_values_check
        check (
            (participation_status = 'PARTICIPATED' and kills >= 0)
            or (
                participation_status = 'NO_SHOW'
                and placement is null
                and kills = 0
            )
        ),
    constraint group_rotation_match_uuid_results_placement_unique
        unique (match_id, placement)
);

create table public.group_rotation_match_uuid_write_requests (
    request_id uuid not null,
    operation text not null,
    tournament_id uuid not null,
    match_id uuid not null,
    expected_revision integer not null,
    payload_hash text not null,
    outcome text not null,
    revision integer not null,
    created_at timestamptz not null default timezone('utc'::text, now()),
    constraint group_rotation_match_uuid_write_requests_pkey
        primary key (request_id),
    constraint group_rotation_match_uuid_write_requests_match_fkey
        foreign key (match_id, tournament_id)
        references public.matches(id, tournament_id)
        on delete cascade,
    constraint group_rotation_match_uuid_write_requests_operation_check
        check (operation in ('write', 'finalize')),
    constraint group_rotation_match_uuid_write_requests_outcome_check
        check (outcome = 'success'),
    constraint group_rotation_match_uuid_write_requests_revision_check
        check (expected_revision >= 0 and revision >= 0)
);

create index group_rotation_match_uuid_snapshots_tournament_idx
    on public.group_rotation_match_uuid_snapshots(tournament_id);
create index group_rotation_match_uuid_assignments_tournament_idx
    on public.group_rotation_match_uuid_assignments(tournament_id);
create index group_rotation_match_uuid_results_tournament_idx
    on public.group_rotation_match_uuid_results(tournament_id);

alter table public.group_rotation_match_uuid_snapshots enable row level security;
alter table public.group_rotation_match_uuid_assignments enable row level security;
alter table public.group_rotation_match_uuid_results enable row level security;
alter table public.group_rotation_match_uuid_write_requests enable row level security;

create policy group_rotation_match_uuid_snapshots_select_owner
on public.group_rotation_match_uuid_snapshots
for select
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = group_rotation_match_uuid_snapshots.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy group_rotation_match_uuid_assignments_select_owner
on public.group_rotation_match_uuid_assignments
for select
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = group_rotation_match_uuid_assignments.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy group_rotation_match_uuid_results_select_owner
on public.group_rotation_match_uuid_results
for select
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = group_rotation_match_uuid_results.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

revoke all on table public.group_rotation_match_uuid_snapshots from public;
revoke all on table public.group_rotation_match_uuid_snapshots from anon;
revoke all on table public.group_rotation_match_uuid_snapshots from authenticated;
grant select on table public.group_rotation_match_uuid_snapshots to authenticated;

revoke all on table public.group_rotation_match_uuid_assignments from public;
revoke all on table public.group_rotation_match_uuid_assignments from anon;
revoke all on table public.group_rotation_match_uuid_assignments from authenticated;
grant select on table public.group_rotation_match_uuid_assignments to authenticated;

revoke all on table public.group_rotation_match_uuid_results from public;
revoke all on table public.group_rotation_match_uuid_results from anon;
revoke all on table public.group_rotation_match_uuid_results from authenticated;
grant select on table public.group_rotation_match_uuid_results to authenticated;

revoke all on table public.group_rotation_match_uuid_write_requests from public;
revoke all on table public.group_rotation_match_uuid_write_requests from anon;
revoke all on table public.group_rotation_match_uuid_write_requests from authenticated;

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

    if jsonb_array_length(p_match_identity) < 1
        or jsonb_array_length(p_match_identity) > 12
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
            or v_existing_header.status <> 'draft'
            or (select count(*) from public.group_rotation_match_uuid_assignments where match_id = v_match_id)
                <> jsonb_array_length(p_match_identity)
            or exists (
                select 1
                from public.group_rotation_match_uuid_assignments as saved_assignment
                where saved_assignment.match_id = v_match_id
                    and not exists (
                        select 1
                        from jsonb_array_elements(p_match_identity) as identity_row
                        where (identity_row ->> 'lobby_slot_number')::integer = saved_assignment.lobby_slot_number
                            and (identity_row ->> 'team_uuid')::uuid = saved_assignment.team_uuid
                    )
            ) then
            return query select 'validation_failure'::text, v_current_revision;
            return;
        end if;
    else
        insert into public.group_rotation_match_uuid_snapshots (
            match_id, tournament_id, pairing_key, status, revision
        ) values (
            v_match_id, p_tournament_id, v_pairing_key, 'draft', v_new_revision
        );
        insert into public.group_rotation_match_uuid_assignments (
            match_id, tournament_id, lobby_slot_number, team_uuid
        )
        select v_match_id, p_tournament_id,
               (identity_row ->> 'lobby_slot_number')::integer,
               (identity_row ->> 'team_uuid')::uuid
        from jsonb_array_elements(p_match_identity) as identity_row;
    end if;

    delete from public.group_rotation_match_uuid_results
    where match_id = v_match_id;
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

create or replace function public.finalize_match_snapshot_v4(
    p_tournament_id uuid,
    p_match_id uuid,
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
    v_payload_hash text;
    v_participated_count integer;
    v_saved_header public.group_rotation_match_uuid_snapshots%rowtype;
    v_existing_request public.group_rotation_match_uuid_write_requests%rowtype;
begin
    if auth.uid() is null then
        return query select 'authentication_required'::text, null::integer;
        return;
    end if;

    if p_expected_revision is null or p_expected_revision < 0 or p_request_id is null
        or coalesce(jsonb_typeof(p_match_results), '') <> 'array' then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    v_payload_hash := md5(
        p_match_id::text || '|' || coalesce(p_match_results::text, '') || '|' ||
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
        if v_existing_request.operation <> 'finalize'
            or v_existing_request.tournament_id <> p_tournament_id
            or v_existing_request.match_id <> p_match_id
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

    select *
    into v_saved_header
    from public.group_rotation_match_uuid_snapshots
    where match_id = p_match_id
        and tournament_id = p_tournament_id
    for update;
    if not found then
        return query select 'missing_data'::text, v_current_revision;
        return;
    end if;
    if v_saved_header.status = 'finalized' then
        return query select 'already_finalized'::text, v_current_revision;
        return;
    end if;
    if not exists (
        select 1
        from public.matches as match_row
        where match_row.id = p_match_id
            and match_row.tournament_id = p_tournament_id
            and match_row.status = 'draft'
            and match_row.group_pairing_key = v_saved_header.pairing_key
    ) then
        return query select 'missing_data'::text, v_current_revision;
        return;
    end if;

    if jsonb_array_length(p_match_results) <> (
        select count(*) from public.group_rotation_match_uuid_assignments
        where match_id = p_match_id
    )
    or exists (
        select 1
        from jsonb_array_elements(p_match_results) as result_row
        where coalesce(jsonb_typeof(result_row), '') <> 'object'
            or coalesce(jsonb_typeof(result_row -> 'team_uuid'), '') <> 'string'
            or coalesce(jsonb_typeof(result_row -> 'participation_status'), '') <> 'string'
            or coalesce(jsonb_typeof(result_row -> 'kills'), '') <> 'number'
            or (result_row ->> 'team_uuid') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (result_row ->> 'kills') !~ '^[0-9]+$'
            or not (result_row ? 'placement')
            or (result_row -> 'placement' <> 'null'::jsonb
                and (coalesce(jsonb_typeof(result_row -> 'placement'), '') <> 'number'
                    or (result_row ->> 'placement') !~ '^[0-9]+$'))
            or (result_row ? 'point_adjustment') and result_row -> 'point_adjustment' <> 'null'::jsonb
                and (coalesce(jsonb_typeof(result_row -> 'point_adjustment'), '') <> 'number'
                    or (result_row ->> 'point_adjustment') !~ '^-?[0-9]+$')
            or (result_row ->> 'participation_status') not in ('PARTICIPATED', 'NO_SHOW')
            or not exists (
                select 1
                from public.group_rotation_match_uuid_assignments as assignment_row
                where assignment_row.match_id = p_match_id
                    and assignment_row.team_uuid::text = result_row ->> 'team_uuid'
            )
            or ((result_row ->> 'participation_status') = 'NO_SHOW'
                and ((result_row ->> 'placement') is not null
                    or (result_row ->> 'kills') <> '0'))
            or ((result_row ->> 'participation_status') = 'PARTICIPATED'
                and ((result_row -> 'placement') = 'null'::jsonb
                    or (result_row ->> 'placement') !~ '^([1-9]|1[0-2])$'))
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

    select count(*)
    into v_participated_count
    from jsonb_array_elements(p_match_results) as result_row
    where result_row ->> 'participation_status' = 'PARTICIPATED';

    if v_participated_count <= 0
        or (
            select count(distinct (result_row ->> 'placement')::integer)
            from jsonb_array_elements(p_match_results) as result_row
            where result_row ->> 'participation_status' = 'PARTICIPATED'
        ) <> v_participated_count
        or exists (
            select 1
            from generate_series(1, v_participated_count) as expected_position
            where not exists (
                select 1
                from jsonb_array_elements(p_match_results) as result_row
                where result_row ->> 'participation_status' = 'PARTICIPATED'
                    and (result_row ->> 'placement')::integer = expected_position
            )
        ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    v_new_revision := v_current_revision + 1;
    delete from public.group_rotation_match_uuid_results
    where match_id = p_match_id;
    insert into public.group_rotation_match_uuid_results (
        match_id, tournament_id, team_uuid, participation_status,
        placement, kills, point_adjustment
    )
    select p_match_id, p_tournament_id,
           (result_row ->> 'team_uuid')::uuid,
           result_row ->> 'participation_status',
           (result_row ->> 'placement')::integer,
           (result_row ->> 'kills')::integer,
           coalesce(nullif(result_row ->> 'point_adjustment', ''), '0')::integer
    from jsonb_array_elements(p_match_results) as result_row;

    update public.group_rotation_match_uuid_snapshots
    set status = 'finalized', revision = v_new_revision,
        finalized_at = timezone('utc'::text, now()), finalized_by = auth.uid(),
        updated_at = timezone('utc'::text, now())
    where match_id = p_match_id;
    update public.matches as match_target
    set status = 'finalized', finalized_at = timezone('utc'::text, now()),
        finalized_by = auth.uid(), revision = match_target.revision + 1,
        updated_at = timezone('utc'::text, now())
    where match_target.id = p_match_id
        and match_target.tournament_id = p_tournament_id;
    update public.tournaments
    set revision = v_new_revision, updated_at = timezone('utc'::text, now())
    where id = p_tournament_id;

    insert into public.group_rotation_match_uuid_write_requests (
        request_id, operation, tournament_id, match_id, expected_revision,
        payload_hash, outcome, revision
    ) values (
        p_request_id, 'finalize', p_tournament_id, p_match_id, p_expected_revision,
        v_payload_hash, 'success', v_new_revision
    );

    return query select 'success'::text, v_new_revision;
end;
$$;

revoke all on function public.write_match_snapshot_v4(uuid, jsonb, jsonb, jsonb, integer, uuid) from public;
revoke all on function public.write_match_snapshot_v4(uuid, jsonb, jsonb, jsonb, integer, uuid) from anon;
grant execute on function public.write_match_snapshot_v4(uuid, jsonb, jsonb, jsonb, integer, uuid) to authenticated;

revoke all on function public.finalize_match_snapshot_v4(uuid, uuid, jsonb, integer, uuid) from public;
revoke all on function public.finalize_match_snapshot_v4(uuid, uuid, jsonb, integer, uuid) from anon;
grant execute on function public.finalize_match_snapshot_v4(uuid, uuid, jsonb, integer, uuid) to authenticated;
