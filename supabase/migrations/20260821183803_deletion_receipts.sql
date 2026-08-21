create table public.deletion_receipts (
    owner_id uuid not null references auth.users(id),
    target_type text not null,
    target_id uuid not null,
    completed_at timestamptz not null default now(),
    constraint deletion_receipts_target_type_check
        check (target_type in ('MATCH', 'TOURNAMENT')),
    primary key (owner_id, target_type, target_id)
);

comment on table public.deletion_receipts is
    'Durable, server-created receipts for authorized destructive deletion idempotency.';

alter table public.deletion_receipts enable row level security;

revoke all on table public.deletion_receipts from public, anon, authenticated;

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
        return query select 'NOT_FOUND_OR_NOT_OWNER'::text;
        return;
    end if;

    insert into public.deletion_receipts (owner_id, target_type, target_id)
    values (v_owner_id, 'MATCH', p_match_id);

    return query select 'DELETED'::text;
end;
$$;

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

revoke execute on function public.delete_match_idempotent(uuid) from public, anon;
grant execute on function public.delete_match_idempotent(uuid) to authenticated;

revoke execute on function public.delete_tournament_idempotent(uuid) from public, anon;
grant execute on function public.delete_tournament_idempotent(uuid) to authenticated;
