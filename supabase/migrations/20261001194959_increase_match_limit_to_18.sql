-- PointIQ supports up to 18 matches per tournament.

alter table public.matches
    drop constraint matches_match_number_check;

alter table public.matches
    add constraint matches_match_number_check
    check (match_number between 1 and 18);

-- Standings export metadata tracks the same tournament match limit.
alter table public.export_operations
    drop constraint export_operations_exported_match_count_check;

alter table public.export_operations
    add constraint export_operations_exported_match_count_check
    check (
        exported_match_count is null
        or exported_match_count between 1 and 18
    );

alter table public.export_operations
    drop constraint export_operations_success_metadata_check;

alter table public.export_operations
    add constraint export_operations_success_metadata_check
    check (
        state <> 'succeeded'
        or (
            rows_written is not null
            and (
                (
                    operation_type = 'export_match'
                    and rows_written between 1 and 12
                    and exported_match_count is null
                )
                or
                (
                    operation_type = 'export_standings'
                    and rows_written between 1 and 12
                    and exported_match_count is not null
                    and exported_match_count between 1 and 18
                )
            )
        )
    );

create or replace function public.resolve_export_operation_verified_success(
    p_operation_id uuid,
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
      and operation_row.state = 'outcome_uncertain'
    for update;

    if not found then
        raise exception 'export operation transition rejected'
            using errcode = 'P0001';
    end if;

    if (
        v_operation_type = 'export_match'
        and p_exported_match_count is not null
    ) or (
        v_operation_type = 'export_standings'
        and (
            p_exported_match_count is null
            or p_exported_match_count not between 1 and 18
        )
    ) then
        raise exception 'invalid verified success metadata'
            using errcode = '22023';
    end if;

    update public.export_operations as operation_row
    set
        state = 'succeeded',
        lease_token = null,
        lease_expires_at = null,
        failure_code = null,
        rows_written = 12,
        exported_match_count = p_exported_match_count,
        updated_at = clock_timestamp(),
        completed_at = clock_timestamp()
    where operation_row.id = p_operation_id
      and operation_row.owner_id = v_owner_id
      and operation_row.state = 'outcome_uncertain';

    if not found then
        raise exception 'export operation transition rejected'
            using errcode = 'P0001';
    end if;

    return 'succeeded';
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

    if p_rows_written is null or p_rows_written not between 1 and 12 then
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
            or p_exported_match_count not between 1 and 18
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
