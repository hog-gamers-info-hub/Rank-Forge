create or replace function public.delete_match_idempotent(
    p_match_id uuid
)
returns table (outcome text)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := auth.uid();
begin
    if v_owner_id is null then
        raise exception 'NOT_AUTHENTICATED' using errcode = '28000';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(
            'rank_forge:delete_match:' || v_owner_id::text || ':' || p_match_id::text,
            0
        )
    );

    if exists (
        select 1
        from public.deletion_receipts as receipt
        where receipt.owner_id = v_owner_id
            and receipt.target_type = 'MATCH'
            and receipt.target_id = p_match_id
    ) then
        return query select 'ALREADY_DELETED'::text;
        return;
    end if;

    if not exists (
        select 1
        from public.matches as match_row
        where match_row.id = p_match_id
    ) then
        return query select 'NOT_FOUND'::text;
        return;
    end if;

    if not exists (
        select 1
        from public.matches as match_row
        join public.tournaments as tournament_row
            on tournament_row.id = match_row.tournament_id
        where match_row.id = p_match_id
            and tournament_row.owner_id = v_owner_id
    ) then
        return query select 'NOT_FOUND_OR_NOT_OWNER'::text;
        return;
    end if;

    delete from public.matches
    where public.matches.id = p_match_id;

    if not found then
        return query select 'NOT_FOUND'::text;
        return;
    end if;

    insert into public.deletion_receipts (owner_id, target_type, target_id)
    values (v_owner_id, 'MATCH', p_match_id);

    return query select 'DELETED'::text;
end;
$$;

revoke execute on function public.delete_match_idempotent(uuid) from public, anon;
grant execute on function public.delete_match_idempotent(uuid) to authenticated;
