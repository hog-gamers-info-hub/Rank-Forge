-- Enforce the account tournament limit only for new tournament rows.
-- Existing rows are intentionally untouched so grandfathered accounts retain their data.
create or replace function public.enforce_tournament_owner_limit()
returns trigger
language plpgsql
as $$
declare
    v_tournament_count bigint;
begin
    perform pg_advisory_xact_lock(hashtextextended(new.owner_id::text, 0));

    select count(*)
    into v_tournament_count
    from public.tournaments
    where owner_id = new.owner_id;

    if v_tournament_count >= 5 then
        raise exception using
            errcode = 'P0001',
            message = 'TOURNAMENT_LIMIT_REACHED',
            detail = 'An account may own at most 5 tournaments.';
    end if;

    return new;
end;
$$;

drop trigger if exists tournaments_owner_limit_before_insert on public.tournaments;

create trigger tournaments_owner_limit_before_insert
before insert on public.tournaments
for each row
execute function public.enforce_tournament_owner_limit();
