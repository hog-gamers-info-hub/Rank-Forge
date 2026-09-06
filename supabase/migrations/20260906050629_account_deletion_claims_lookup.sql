-- Read the service-role claim from the JSON claims setting used by PostgREST.
create or replace function public.begin_account_deletion(
    p_user_id uuid
)
returns table (
    state text,
    active_export_operations bigint
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_state text;
    v_active_export_operations bigint;
begin
    if coalesce(
        nullif(
            current_setting('request.jwt.claims', true),
            ''
        )::jsonb ->> 'role',
        ''
    ) <> 'service_role' then
        raise exception 'service role required'
            using errcode = '42501';
    end if;

    if p_user_id is null then
        raise exception 'invalid user id'
            using errcode = '22023';
    end if;

    select guard_row.state
    into v_state
    from private.account_deletion_guards as guard_row
    where guard_row.user_id = p_user_id
    for update;

    if not found then
        raise exception 'account deletion guard is missing'
            using errcode = '42501';
    end if;

    if v_state = 'active' then
        update private.account_deletion_guards
        set state = 'deleting'
        where user_id = p_user_id;
        v_state := 'deleting';
    end if;

    -- A claim that had not started an external write is safe to close once the
    -- barrier has won. Already write-started or uncertain operations remain
    -- visible so the coordinator can fail closed until they are terminal.
    update public.export_operations as operation_row
    set state = 'retryable_failure',
        lease_token = null,
        lease_expires_at = null,
        failure_code = 'ACCOUNT_DELETION_IN_PROGRESS',
        rows_written = null,
        exported_match_count = null,
        updated_at = clock_timestamp(),
        completed_at = null
    where operation_row.owner_id = p_user_id
        and operation_row.state = 'in_progress';

    select count(*)
    into v_active_export_operations
    from public.export_operations as operation_row
    where operation_row.owner_id = p_user_id
        and operation_row.state in ('write_started', 'outcome_uncertain');

    return query
    select v_state, v_active_export_operations;
end;
$$;

revoke execute on function public.begin_account_deletion(uuid)
from public, anon, authenticated;

grant execute on function public.begin_account_deletion(uuid)
to service_role;
