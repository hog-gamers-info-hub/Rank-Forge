-- V4 Group Rotation deletion ordering.
-- Standard tournament deletion retains the existing direct-delete path.

create or replace function public.delete_tournament_idempotent(
    p_tournament_id uuid
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
            'rank_forge:delete_tournament:' || v_owner_id::text || ':' || p_tournament_id::text,
            0
        )
    );

    if exists (
        select 1
        from public.deletion_receipts as receipt
        where receipt.owner_id = v_owner_id
            and receipt.target_type = 'TOURNAMENT'
            and receipt.target_id = p_tournament_id
    ) then
        return query select 'ALREADY_DELETED'::text;
        return;
    end if;

    if not exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = p_tournament_id
            and tournament_row.owner_id = v_owner_id
    ) then
        return query select 'NOT_FOUND_OR_NOT_OWNER'::text;
        return;
    end if;

    if exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = p_tournament_id
            and tournament_row.owner_id = v_owner_id
            and tournament_row.format = 'group_rotation'
    ) then
        delete from public.matches
        where public.matches.tournament_id = p_tournament_id;

        delete from public.tournament_group_pairings
        where public.tournament_group_pairings.tournament_id = p_tournament_id;
    end if;

    delete from public.tournaments
    where public.tournaments.id = p_tournament_id;

    if not found then
        return query select 'NOT_FOUND_OR_NOT_OWNER'::text;
        return;
    end if;

    insert into public.deletion_receipts (owner_id, target_type, target_id)
    values (v_owner_id, 'TOURNAMENT', p_tournament_id);

    return query select 'DELETED'::text;
end;
$$;

revoke execute on function public.delete_tournament_idempotent(uuid) from public, anon;
grant execute on function public.delete_tournament_idempotent(uuid) to authenticated;
