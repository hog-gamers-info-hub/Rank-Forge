-- Account-deletion lifecycle barrier.
--
-- The guard row is deliberately kept outside the exposed API schemas.  Public
-- mutations acquire a FOR SHARE lock through the trigger/helper below, while
-- the service-role deletion coordinator acquires FOR UPDATE before changing
-- the lifecycle state to deleting.

create schema if not exists private;

create table private.account_deletion_guards (
    user_id uuid primary key references auth.users(id) on delete cascade,
    state text not null default 'active',
    constraint account_deletion_guards_state_check
        check (state in ('active', 'deleting'))
);

alter table private.account_deletion_guards enable row level security;

create index account_deletion_guards_user_id_idx
    on private.account_deletion_guards (user_id);

insert into private.account_deletion_guards (user_id, state)
select id, 'active'
from auth.users
on conflict (user_id) do nothing;

create or replace function private.initialize_account_deletion_guard()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into private.account_deletion_guards (user_id, state)
    values (new.id, 'active');
    return new;
end;
$$;

create trigger auth_users_account_deletion_guard_after_insert
after insert on auth.users
for each row
execute function private.initialize_account_deletion_guard();

create or replace function private.account_deletion_guard_is_active(
    p_user_id uuid
)
returns boolean
language plpgsql
volatile
security definer
set search_path = ''
as $$
declare
    v_state text;
begin
    if p_user_id is null then
        return false;
    end if;

    select guard_row.state
    into v_state
    from private.account_deletion_guards as guard_row
    where guard_row.user_id = p_user_id
    for share;

    return found and v_state = 'active';
end;
$$;

create or replace function private.enforce_account_mutation_guard()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    -- Auth and trusted service-role maintenance operations do not carry an
    -- end-user auth.uid(). Their public mutation entry points are separately
    -- secured and are intentionally not blocked by this end-user barrier.
    if auth.uid() is not null
       and not private.account_deletion_guard_is_active(auth.uid()) then
        raise exception 'account mutation blocked by deletion barrier'
            using errcode = '42501';
    end if;

    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$;

create or replace function private.enforce_export_operation_guard()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if auth.uid() is null then
        return new;
    end if;

    if private.account_deletion_guard_is_active(auth.uid()) then
        return new;
    end if;

    -- An export that already reached the external-write phase must be able to
    -- settle to a terminal state while deletion fails closed on it. No new
    -- claim or write-start is permitted after the barrier.
    if tg_op = 'UPDATE'
       and old.owner_id = auth.uid()
       and (
           (
               old.state in ('in_progress', 'write_started')
               and new.state in ('succeeded', 'retryable_failure', 'outcome_uncertain')
           )
           or (
               old.state = 'outcome_uncertain'
               and new.state = 'succeeded'
           )
       ) then
        return new;
    end if;

    raise exception 'export operation blocked by deletion barrier'
        using errcode = '42501';
end;
$$;

-- Every authenticated mutation of these account-owned tables takes the shared
-- guard lock in the same transaction as its row operation. Existing ownership
-- policies remain authoritative; these restrictive policies are not used so
-- that SECURITY DEFINER RPCs are covered by the trigger as well.
do $$
declare
    table_name text;
begin
    foreach table_name in array array[
        'tournaments',
        'tournament_team_slots',
        'players',
        'matches',
        'match_results',
        'match_correction_audit_entries',
        'match_lobby_screenshot_assets',
        'match_result_screenshot_assets',
        'match_screenshot_metadata',
        'match_ocr_evidence',
        'match_ocr_row_evidence',
        'match_ocr_correction_snapshots',
        'custom_design_templates',
        'deletion_receipts',
        'tournament_standings_shares'
    ] loop
        execute format(
            'create trigger %I
             before insert or update or delete on public.%I
             for each row execute function private.enforce_account_mutation_guard()',
            'account_deletion_guard_' || table_name,
            table_name
        );
    end loop;
end;
$$;

-- Export-operation claims are SECURITY DEFINER RPCs, so they use a dedicated
-- trigger. Terminal transitions remain possible for an operation that was
-- already admitted before deletion, while new claims/write starts are blocked.
create trigger account_deletion_guard_export_operations
before insert or update on public.export_operations
for each row
execute function private.enforce_export_operation_guard();

-- Storage metadata is managed by Supabase, so its existing detailed ownership
-- policies are preserved and the same shared lock is acquired by a restrictive
-- policy instead of adding a trigger to storage.objects.
create policy account_deletion_guard_storage_mutation
on storage.objects
as restrictive
for all
to authenticated
using (private.account_deletion_guard_is_active((select auth.uid())))
with check (private.account_deletion_guard_is_active((select auth.uid())));

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
    if coalesce(current_setting('request.jwt.claim.role', true), '') <> 'service_role' then
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

revoke all on schema private from public, anon, authenticated, service_role;
revoke all on table private.account_deletion_guards
from public, anon, authenticated, service_role;
revoke all on function private.initialize_account_deletion_guard() from public, anon, authenticated, service_role;
revoke all on function private.account_deletion_guard_is_active(uuid) from public, anon, authenticated, service_role;
grant execute on function private.account_deletion_guard_is_active(uuid) to authenticated;
revoke all on function private.enforce_account_mutation_guard() from public, anon, authenticated, service_role;
revoke all on function private.enforce_export_operation_guard() from public, anon, authenticated, service_role;

revoke all on function public.begin_account_deletion(uuid)
from public, anon, authenticated;
grant execute on function public.begin_account_deletion(uuid) to service_role;
