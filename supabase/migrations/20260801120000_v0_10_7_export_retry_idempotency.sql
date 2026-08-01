-- v0.10.7: persistent export retry and idempotency state machine.

create table public.export_operations (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    operation_type text not null,
    tournament_id uuid not null references public.tournaments(id) on delete cascade,
    match_id uuid references public.matches(id) on delete cascade,
    payload_fingerprint text not null,
    state text not null,
    lease_token uuid,
    lease_expires_at timestamptz,
    attempt_count integer not null default 1,
    failure_code text,
    rows_written integer,
    exported_match_count integer,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    completed_at timestamptz,
    constraint export_operations_operation_type_check
        check (operation_type in ('export_match', 'export_standings')),
    constraint export_operations_state_check
        check (
            state in (
                'in_progress',
                'write_started',
                'succeeded',
                'retryable_failure',
                'outcome_uncertain'
            )
        ),
    constraint export_operations_payload_fingerprint_check
        check (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    constraint export_operations_attempt_count_check
        check (attempt_count >= 1),
    constraint export_operations_failure_code_check
        check (
            failure_code is null
            or failure_code ~ '^[A-Z][A-Z0-9_]{0,79}$'
        ),
    constraint export_operations_rows_written_check
        check (rows_written is null or rows_written = 12),
    constraint export_operations_exported_match_count_check
        check (
            exported_match_count is null
            or exported_match_count between 1 and 10
        ),
    constraint export_operations_target_check
        check (
            (operation_type = 'export_match' and match_id is not null)
            or
            (operation_type = 'export_standings' and match_id is null)
        ),
    constraint export_operations_success_metadata_check
        check (
            state <> 'succeeded'
            or (
                rows_written is not null
                and rows_written = 12
                and (
                    (
                        operation_type = 'export_match'
                        and exported_match_count is null
                    )
                    or
                    (
                        operation_type = 'export_standings'
                        and exported_match_count is not null
                        and exported_match_count between 1 and 10
                    )
                )
            )
        ),
    constraint export_operations_lease_state_check
        check (
            (
                state in ('in_progress', 'write_started')
                and lease_token is not null
                and lease_expires_at is not null
            )
            or
            (
                state not in ('in_progress', 'write_started')
                and lease_token is null
                and lease_expires_at is null
            )
        )
);

create unique index export_operations_logical_identity_uidx
    on public.export_operations (
        owner_id,
        operation_type,
        tournament_id,
        match_id,
        payload_fingerprint
    )
    nulls not distinct;

create index export_operations_owner_updated_at_idx
    on public.export_operations (owner_id, updated_at desc);

alter table public.export_operations enable row level security;

create policy export_operations_select_owner
on public.export_operations
for select
to authenticated
using (owner_id = auth.uid());

revoke all on table public.export_operations from anon, authenticated;
grant select on table public.export_operations to authenticated;

create or replace function public.claim_export_operation(
    p_operation_type text,
    p_tournament_id uuid,
    p_match_id uuid,
    p_payload_fingerprint text
)
returns table (
    outcome text,
    operation_id uuid,
    lease_token uuid,
    state text,
    attempt_count integer,
    rows_written integer,
    exported_match_count integer
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid;
    v_operation_id uuid;
    v_lease_token uuid;
    v_state text;
    v_attempt_count integer;
    v_rows_written integer;
    v_exported_match_count integer;
    v_lease_expires_at timestamptz;
begin
    v_owner_id := auth.uid();

    if v_owner_id is null then
        raise exception 'authentication required'
            using errcode = '42501';
    end if;

    if p_operation_type not in ('export_match', 'export_standings') then
        raise exception 'invalid export operation type'
            using errcode = '22023';
    end if;

    if p_payload_fingerprint is null
       or p_payload_fingerprint !~ '^[0-9a-f]{64}$' then
        raise exception 'invalid export payload fingerprint'
            using errcode = '22023';
    end if;

    if (
        p_operation_type = 'export_match'
        and p_match_id is null
    ) or (
        p_operation_type = 'export_standings'
        and p_match_id is not null
    ) then
        raise exception 'invalid export target'
            using errcode = '22023';
    end if;

    perform 1
    from public.tournaments as tournament_row
    where tournament_row.id = p_tournament_id
      and tournament_row.owner_id = v_owner_id;

    if not found then
        raise exception 'export target unavailable'
            using errcode = '42501';
    end if;

    if p_operation_type = 'export_match' then
        perform 1
        from public.matches as match_row
        where match_row.id = p_match_id
          and match_row.tournament_id = p_tournament_id;

        if not found then
            raise exception 'export match unavailable'
                using errcode = '42501';
        end if;
    end if;

    v_lease_token := gen_random_uuid();

    insert into public.export_operations (
        owner_id,
        operation_type,
        tournament_id,
        match_id,
        payload_fingerprint,
        state,
        lease_token,
        lease_expires_at,
        attempt_count
    )
    values (
        v_owner_id,
        p_operation_type,
        p_tournament_id,
        p_match_id,
        p_payload_fingerprint,
        'in_progress',
        v_lease_token,
        clock_timestamp() + interval '90 seconds',
        1
    )
    on conflict do nothing
    returning
        id,
        export_operations.attempt_count
    into
        v_operation_id,
        v_attempt_count;

    if found then
        return query
        select
            'claimed'::text,
            v_operation_id,
            v_lease_token,
            'in_progress'::text,
            v_attempt_count,
            null::integer,
            null::integer;
        return;
    end if;

    select
        operation_row.id,
        operation_row.state,
        operation_row.attempt_count,
        operation_row.rows_written,
        operation_row.exported_match_count,
        operation_row.lease_expires_at
    into
        v_operation_id,
        v_state,
        v_attempt_count,
        v_rows_written,
        v_exported_match_count,
        v_lease_expires_at
    from public.export_operations as operation_row
    where operation_row.owner_id = v_owner_id
      and operation_row.operation_type = p_operation_type
      and operation_row.tournament_id = p_tournament_id
      and operation_row.match_id is not distinct from p_match_id
      and operation_row.payload_fingerprint = p_payload_fingerprint
    for update;

    if not found then
        raise exception 'export operation identity could not be resolved'
            using errcode = '40001';
    end if;

    if v_state = 'succeeded' then
        return query
        select
            'replayed'::text,
            v_operation_id,
            null::uuid,
            v_state,
            v_attempt_count,
            v_rows_written,
            v_exported_match_count;
        return;
    end if;

    if v_state = 'outcome_uncertain' then
        return query
        select
            'outcome_uncertain'::text,
            v_operation_id,
            null::uuid,
            v_state,
            v_attempt_count,
            v_rows_written,
            v_exported_match_count;
        return;
    end if;

    if v_state = 'write_started' then
        if v_lease_expires_at > clock_timestamp() then
            return query
            select
                'in_progress'::text,
                v_operation_id,
                null::uuid,
                v_state,
                v_attempt_count,
                v_rows_written,
                v_exported_match_count;
            return;
        end if;

        update public.export_operations as operation_row
        set
            state = 'outcome_uncertain',
            lease_token = null,
            lease_expires_at = null,
            failure_code = 'EXPORT_OUTCOME_UNCERTAIN',
            updated_at = clock_timestamp(),
            completed_at = clock_timestamp()
        where operation_row.id = v_operation_id;

        return query
        select
            'outcome_uncertain'::text,
            v_operation_id,
            null::uuid,
            'outcome_uncertain'::text,
            v_attempt_count,
            null::integer,
            null::integer;
        return;
    end if;

    if v_state = 'in_progress'
       and v_lease_expires_at > clock_timestamp() then
        return query
        select
            'in_progress'::text,
            v_operation_id,
            null::uuid,
            v_state,
            v_attempt_count,
            v_rows_written,
            v_exported_match_count;
        return;
    end if;

    if v_state in ('in_progress', 'retryable_failure') then
        v_lease_token := gen_random_uuid();

        update public.export_operations as operation_row
        set
            state = 'in_progress',
            lease_token = v_lease_token,
            lease_expires_at = clock_timestamp() + interval '90 seconds',
            attempt_count = operation_row.attempt_count + 1,
            failure_code = null,
            rows_written = null,
            exported_match_count = null,
            updated_at = clock_timestamp(),
            completed_at = null
        where operation_row.id = v_operation_id
        returning operation_row.attempt_count
        into v_attempt_count;

        return query
        select
            'claimed'::text,
            v_operation_id,
            v_lease_token,
            'in_progress'::text,
            v_attempt_count,
            null::integer,
            null::integer;
        return;
    end if;

    raise exception 'unsupported export operation state'
        using errcode = 'P0001';
end;
$$;

create or replace function public.mark_export_operation_write_started(
    p_operation_id uuid,
    p_lease_token uuid
)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid;
begin
    v_owner_id := auth.uid();

    if v_owner_id is null then
        raise exception 'authentication required'
            using errcode = '42501';
    end if;

    update public.export_operations as operation_row
    set
        state = 'write_started',
        updated_at = clock_timestamp()
    where operation_row.id = p_operation_id
      and operation_row.owner_id = v_owner_id
      and operation_row.state = 'in_progress'
      and operation_row.lease_token = p_lease_token
      and operation_row.lease_expires_at > clock_timestamp();

    if not found then
        raise exception 'export operation transition rejected'
            using errcode = 'P0001';
    end if;

    return 'write_started';
end;
$$;

create or replace function public.complete_export_operation_success(
    p_operation_id uuid,
    p_lease_token uuid,
    p_rows_written integer,
    p_exported_match_count integer default null
)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid;
    v_operation_type text;
begin
    v_owner_id := auth.uid();

    if v_owner_id is null then
        raise exception 'authentication required'
            using errcode = '42501';
    end if;

    select operation_row.operation_type
    into v_operation_type
    from public.export_operations as operation_row
    where operation_row.id = p_operation_id
      and operation_row.owner_id = v_owner_id
      and operation_row.state = 'write_started'
      and operation_row.lease_token = p_lease_token
    for update;

    if not found then
        raise exception 'export operation transition rejected'
            using errcode = 'P0001';
    end if;

    if p_rows_written <> 12 then
        raise exception 'invalid written row count'
            using errcode = '22023';
    end if;

    if (
        v_operation_type = 'export_match'
        and p_exported_match_count is not null
    ) or (
        v_operation_type = 'export_standings'
        and (
            p_exported_match_count is null
            or p_exported_match_count not between 1 and 10
        )
    ) then
        raise exception 'invalid export success metadata'
            using errcode = '22023';
    end if;

    update public.export_operations as operation_row
    set
        state = 'succeeded',
        lease_token = null,
        lease_expires_at = null,
        failure_code = null,
        rows_written = p_rows_written,
        exported_match_count = p_exported_match_count,
        updated_at = clock_timestamp(),
        completed_at = clock_timestamp()
    where operation_row.id = p_operation_id;

    return 'succeeded';
end;
$$;

create or replace function public.mark_export_operation_retryable_failure(
    p_operation_id uuid,
    p_lease_token uuid,
    p_failure_code text
)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid;
    v_state text;
begin
    v_owner_id := auth.uid();

    if v_owner_id is null then
        raise exception 'authentication required'
            using errcode = '42501';
    end if;

    if p_failure_code is null
       or p_failure_code !~ '^[A-Z][A-Z0-9_]{0,79}$' then
        raise exception 'invalid export failure code'
            using errcode = '22023';
    end if;

    select operation_row.state
    into v_state
    from public.export_operations as operation_row
    where operation_row.id = p_operation_id
      and operation_row.owner_id = v_owner_id
      and operation_row.lease_token = p_lease_token
      and operation_row.state in ('in_progress', 'write_started')
    for update;

    if not found then
        raise exception 'export operation transition rejected'
            using errcode = 'P0001';
    end if;

    if v_state = 'write_started'
       and p_failure_code not in (
           'GOOGLE_SHEETS_ACCESS_DENIED',
           'GOOGLE_SHEETS_NOT_FOUND',
           'GOOGLE_API_RATE_LIMITED'
       ) then
        raise exception 'write-started failure is not retry-safe'
            using errcode = '22023';
    end if;

    update public.export_operations as operation_row
    set
        state = 'retryable_failure',
        lease_token = null,
        lease_expires_at = null,
        failure_code = p_failure_code,
        rows_written = null,
        exported_match_count = null,
        updated_at = clock_timestamp(),
        completed_at = null
    where operation_row.id = p_operation_id;

    return 'retryable_failure';
end;
$$;

create or replace function public.mark_export_operation_outcome_uncertain(
    p_operation_id uuid,
    p_lease_token uuid,
    p_failure_code text
)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid;
begin
    v_owner_id := auth.uid();

    if v_owner_id is null then
        raise exception 'authentication required'
            using errcode = '42501';
    end if;

    if p_failure_code is null
       or p_failure_code !~ '^[A-Z][A-Z0-9_]{0,79}$' then
        raise exception 'invalid export failure code'
            using errcode = '22023';
    end if;

    update public.export_operations as operation_row
    set
        state = 'outcome_uncertain',
        lease_token = null,
        lease_expires_at = null,
        failure_code = p_failure_code,
        rows_written = null,
        exported_match_count = null,
        updated_at = clock_timestamp(),
        completed_at = clock_timestamp()
    where operation_row.id = p_operation_id
      and operation_row.owner_id = v_owner_id
      and operation_row.state = 'write_started'
      and operation_row.lease_token = p_lease_token;

    if not found then
        raise exception 'export operation transition rejected'
            using errcode = 'P0001';
    end if;

    return 'outcome_uncertain';
end;
$$;

revoke all on function public.claim_export_operation(text, uuid, uuid, text) from public;
revoke all on function public.mark_export_operation_write_started(uuid, uuid) from public;
revoke all on function public.complete_export_operation_success(uuid, uuid, integer, integer) from public;
revoke all on function public.mark_export_operation_retryable_failure(uuid, uuid, text) from public;
revoke all on function public.mark_export_operation_outcome_uncertain(uuid, uuid, text) from public;

grant execute on function public.claim_export_operation(text, uuid, uuid, text) to authenticated;
grant execute on function public.mark_export_operation_write_started(uuid, uuid) to authenticated;
grant execute on function public.complete_export_operation_success(uuid, uuid, integer, integer) to authenticated;
grant execute on function public.mark_export_operation_retryable_failure(uuid, uuid, text) to authenticated;
grant execute on function public.mark_export_operation_outcome_uncertain(uuid, uuid, text) to authenticated;
