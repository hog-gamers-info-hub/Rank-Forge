alter table public.tournaments
    drop column tournament_date;

create or replace function public.write_tournament_snapshot(
    p_tournament jsonb,
    p_team_slots jsonb,
    p_players jsonb,
    p_expected_revision integer
)
returns table (outcome text, revision integer)
language plpgsql
security invoker
set search_path = public
as $$
declare
    v_tournament_id uuid := (p_tournament ->> 'id')::uuid;
    v_owner_id uuid := (p_tournament ->> 'owner_id')::uuid;
    v_current_revision integer;
begin
    if p_expected_revision is null or p_expected_revision < 0 then
        return query select 'missing_revision'::text, null::integer;
        return;
    end if;

    select t.revision into v_current_revision
    from public.tournaments as t
    where t.id = v_tournament_id
    for update;

    if not found then
        if p_expected_revision <> 0 or v_owner_id is distinct from auth.uid() then
            return query select 'missing_revision'::text, null::integer;
            return;
        end if;
        insert into public.tournaments (
            id, owner_id, name, organizer_name, organization_name, organizer_contact, status, revision
        ) values (
            v_tournament_id, v_owner_id, p_tournament ->> 'name', p_tournament ->> 'organizer_name',
            p_tournament ->> 'organization_name', p_tournament ->> 'organizer_contact', p_tournament ->> 'status', 1
        );
        v_current_revision := 1;
    else
        if p_expected_revision <> v_current_revision then
            return query select 'stale_write'::text, v_current_revision;
            return;
        end if;
        update public.tournaments as t
        set name = p_tournament ->> 'name',
            organizer_name = p_tournament ->> 'organizer_name',
            organization_name = p_tournament ->> 'organization_name',
            organizer_contact = p_tournament ->> 'organizer_contact',
            status = p_tournament ->> 'status', revision = t.revision + 1, updated_at = now()
        where t.id = v_tournament_id;
        v_current_revision := v_current_revision + 1;
    end if;

    insert into public.tournament_team_slots as team_slot_target (id, tournament_id, slot_number, team_name, status)
    select id, tournament_id, slot_number, team_name, status
    from jsonb_to_recordset(p_team_slots) as slot_row(
        id uuid, tournament_id uuid, slot_number integer, team_name text, status text
    )
    on conflict (id) do update
    set team_name = excluded.team_name, status = excluded.status,
        revision = team_slot_target.revision + 1, updated_at = now();

    insert into public.players as player_target (id, team_slot_id, display_name, normalized_name)
    select id, team_slot_id, display_name, normalized_name
    from jsonb_to_recordset(p_players) as player_row(
        id uuid, team_slot_id uuid, display_name text, normalized_name text
    )
    on conflict (id) do update
    set display_name = excluded.display_name, normalized_name = excluded.normalized_name,
        revision = player_target.revision + 1, updated_at = now();

    return query select 'success'::text, v_current_revision;
end;
$$;
