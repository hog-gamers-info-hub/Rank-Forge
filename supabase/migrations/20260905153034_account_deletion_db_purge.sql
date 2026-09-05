create or replace function public.purge_account_data(
    p_user_id uuid
)
returns table (
    deleted_tournaments bigint,
    deleted_custom_designs bigint,
    deleted_deletion_receipts bigint,
    deleted_export_operations bigint
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_deleted_tournaments bigint := 0;
    v_deleted_custom_designs bigint := 0;
    v_deleted_deletion_receipts bigint := 0;
    v_deleted_export_operations bigint := 0;
begin
    if p_user_id is null then
        raise exception 'INVALID_USER_ID' using errcode = '22023';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(
            'rank_forge:purge_account_data:' || p_user_id::text,
            0
        )
    );

    delete from public.export_operations
    where owner_id = p_user_id;
    get diagnostics v_deleted_export_operations = row_count;

    delete from public.deletion_receipts
    where owner_id = p_user_id;
    get diagnostics v_deleted_deletion_receipts = row_count;

    delete from public.custom_design_templates
    where user_id = p_user_id;
    get diagnostics v_deleted_custom_designs = row_count;

    delete from public.tournaments
    where owner_id = p_user_id;
    get diagnostics v_deleted_tournaments = row_count;

    if exists (
        select 1
        from public.tournaments
        where owner_id = p_user_id
    )
        or exists (
            select 1
            from public.custom_design_templates
            where user_id = p_user_id
        )
        or exists (
            select 1
            from public.deletion_receipts
            where owner_id = p_user_id
        )
        or exists (
            select 1
            from public.export_operations
            where owner_id = p_user_id
        )
        or exists (
            select 1
            from public.matches
            where finalized_by = p_user_id
        )
        or exists (
            select 1
            from public.match_correction_audit_entries
            where corrected_by = p_user_id
        )
        or exists (
            select 1
            from public.match_lobby_screenshot_assets
            where owner_id = p_user_id
        )
        or exists (
            select 1
            from public.match_result_screenshot_assets
            where owner_id = p_user_id
        )
        or exists (
            select 1
            from public.match_screenshot_metadata
            where owner_id = p_user_id
        )
    then
        raise exception 'ACCOUNT_PURGE_RESIDUAL_USER_REFERENCE';
    end if;

    return query
    select
        v_deleted_tournaments,
        v_deleted_custom_designs,
        v_deleted_deletion_receipts,
        v_deleted_export_operations;
end;
$$;

revoke execute on function public.purge_account_data(uuid)
from public, anon, authenticated;

grant execute on function public.purge_account_data(uuid)
to service_role;
