-- v0.10.8: verified reconciliation for uncertain Google Sheets exports.

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
            or p_exported_match_count not between 1 and 10
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

revoke all on function public.resolve_export_operation_verified_success(uuid, integer) from public;
revoke all on function public.resolve_export_operation_verified_success(uuid, integer) from anon;

grant execute on function public.resolve_export_operation_verified_success(uuid, integer) to authenticated;
