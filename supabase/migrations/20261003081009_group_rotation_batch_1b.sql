alter table public.tournaments
    add column format text not null default 'standard',
    add column group_count integer,
    add constraint tournaments_format_check
        check (format in ('standard', 'group_rotation')),
    add constraint tournaments_group_count_check
        check (
            (format = 'standard' and group_count is null)
            or (format = 'group_rotation' and group_count in (3, 4))
        );

alter table public.tournament_team_slots
    drop constraint tournament_team_slots_slot_number_check,
    add column "group" text,
    add constraint tournament_team_slots_slot_number_check
        check (slot_number between 1 and 24),
    add constraint tournament_team_slots_group_check
        check ("group" is null or "group" in ('A', 'B', 'C', 'D'));

create table public.tournament_group_pairings (
    tournament_id uuid not null references public.tournaments(id) on delete cascade,
    first_group text not null,
    second_group text not null,
    pairing_key text generated always as (first_group || ':' || second_group) stored,
    created_at timestamp with time zone not null default timezone('utc'::text, now()),
    primary key (tournament_id, first_group, second_group),
    constraint tournament_group_pairings_first_group_check
        check (first_group in ('A', 'B', 'C', 'D')),
    constraint tournament_group_pairings_second_group_check
        check (second_group in ('A', 'B', 'C', 'D')),
    constraint tournament_group_pairings_distinct_groups_check
        check (first_group <> second_group),
    constraint tournament_group_pairings_canonical_order_check
        check (first_group < second_group),
    constraint tournament_group_pairings_key_unique
        unique (tournament_id, pairing_key)
);

create index idx_tournament_group_pairings_tournament_id
    on public.tournament_group_pairings(tournament_id);

alter table public.tournament_group_pairings enable row level security;

create policy tournament_group_pairings_select_owner
on public.tournament_group_pairings
for select
to authenticated
using (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = tournament_group_pairings.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy tournament_group_pairings_insert_owner
on public.tournament_group_pairings
for insert
to authenticated
with check (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = tournament_group_pairings.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy tournament_group_pairings_update_owner
on public.tournament_group_pairings
for update
to authenticated
using (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = tournament_group_pairings.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
)
with check (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = tournament_group_pairings.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

create policy tournament_group_pairings_delete_owner
on public.tournament_group_pairings
for delete
to authenticated
using (
    exists (
        select 1
        from public.tournaments tournament_row
        where tournament_row.id = tournament_group_pairings.tournament_id
            and tournament_row.owner_id = auth.uid()
    )
);

grant select, insert, update, delete on public.tournament_group_pairings to authenticated;

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
    v_tournament_id uuid;
    v_owner_id uuid;
    v_format text;
    v_group_count integer;
    v_pairings jsonb;
    v_expected_slot_count integer;
    v_current_revision integer;
begin
    if p_expected_revision is null or p_expected_revision < 0 then
        return query select 'missing_revision'::text, null::integer;
        return;
    end if;

    if coalesce(jsonb_typeof(p_tournament), '') <> 'object'
        or coalesce(jsonb_typeof(p_tournament -> 'id'), '') <> 'string'
        or coalesce(jsonb_typeof(p_tournament -> 'owner_id'), '') <> 'string'
        or (p_tournament ->> 'id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        or (p_tournament ->> 'owner_id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    v_tournament_id := (p_tournament ->> 'id')::uuid;
    v_owner_id := (p_tournament ->> 'owner_id')::uuid;
    v_format := coalesce(nullif(p_tournament ->> 'format', ''), 'standard');
    v_pairings := coalesce(p_tournament -> 'selected_group_pairings', '[]'::jsonb);

    if v_format not in ('standard', 'group_rotation')
        or coalesce(jsonb_typeof(v_pairings), '') <> 'array'
        or coalesce(jsonb_typeof(p_team_slots), '') <> 'array'
        or coalesce(jsonb_typeof(p_players), '') <> 'array' then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if v_format = 'standard' then
        v_group_count := null;
        v_expected_slot_count := 12;
        if (p_tournament ? 'group_count')
            and p_tournament -> 'group_count' <> 'null' then
            return query select 'validation_failure'::text, null::integer;
            return;
        end if;
        if jsonb_array_length(v_pairings) <> 0 then
            return query select 'validation_failure'::text, null::integer;
            return;
        end if;
    else
        if coalesce(jsonb_typeof(p_tournament -> 'group_count'), '') <> 'number'
            or (p_tournament ->> 'group_count') !~ '^[34]$' then
            return query select 'validation_failure'::text, null::integer;
            return;
        end if;
        v_group_count := (p_tournament ->> 'group_count')::integer;
        v_expected_slot_count := v_group_count * 6;
        if jsonb_array_length(v_pairings) < 1 then
            return query select 'validation_failure'::text, null::integer;
            return;
        end if;
    end if;

    if jsonb_array_length(p_team_slots) <> v_expected_slot_count then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_team_slots) as slot_row
        where coalesce(jsonb_typeof(slot_row), '') <> 'object'
            or coalesce(jsonb_typeof(slot_row -> 'id'), '') <> 'string'
            or coalesce(jsonb_typeof(slot_row -> 'tournament_id'), '') <> 'string'
            or coalesce(jsonb_typeof(slot_row -> 'slot_number'), '') <> 'number'
            or coalesce(jsonb_typeof(slot_row -> 'team_name'), '') <> 'string'
            or coalesce(jsonb_typeof(slot_row -> 'status'), '') <> 'string'
            or ((slot_row ? 'group') and jsonb_typeof(slot_row -> 'group') not in ('string', 'null'))
    ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_team_slots) as slot_row
        where (slot_row ->> 'id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (slot_row ->> 'tournament_id') <> v_tournament_id::text
            or (slot_row ->> 'slot_number') !~ '^([1-9]|1[0-9]|2[0-4])$'
            or (slot_row ->> 'status') <> 'draft'
    ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if (
        select count(distinct slot_row ->> 'slot_number')
        from jsonb_array_elements(p_team_slots) as slot_row
    ) <> v_expected_slot_count
        or (
            select count(distinct slot_row ->> 'id')
            from jsonb_array_elements(p_team_slots) as slot_row
        ) <> v_expected_slot_count then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_team_slots) as slot_row
        where (v_format = 'standard' and slot_row -> 'group' is not null and slot_row -> 'group' <> 'null'::jsonb)
            or (v_format = 'group_rotation' and (slot_row ->> 'group') <> case
                when (slot_row ->> 'slot_number')::integer between 1 and 6 then 'A'
                when (slot_row ->> 'slot_number')::integer between 7 and 12 then 'B'
                when (slot_row ->> 'slot_number')::integer between 13 and 18 then 'C'
                else 'D'
            end)
    ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(v_pairings) as pairing_row
        where coalesce(jsonb_typeof(pairing_row), '') <> 'object'
            or coalesce(jsonb_typeof(pairing_row -> 'first_group'), '') <> 'string'
            or coalesce(jsonb_typeof(pairing_row -> 'second_group'), '') <> 'string'
            or coalesce(jsonb_typeof(pairing_row -> 'pairing_key'), '') <> 'string'
            or (pairing_row ->> 'first_group') not in ('A', 'B', 'C', 'D')
            or (pairing_row ->> 'second_group') not in ('A', 'B', 'C', 'D')
            or (pairing_row ->> 'first_group') >= (pairing_row ->> 'second_group')
            or (pairing_row ->> 'pairing_key') <> (pairing_row ->> 'first_group') || ':' || (pairing_row ->> 'second_group')
            or (v_group_count = 3 and (pairing_row ->> 'first_group') = 'D')
            or (v_group_count = 3 and (pairing_row ->> 'second_group') = 'D')
    ) then
        return query select 'validation_failure'::text, null::integer;
        return;
    end if;

    if (
        select count(distinct pairing_row ->> 'pairing_key')
        from jsonb_array_elements(v_pairings) as pairing_row
    ) <> jsonb_array_length(v_pairings) then
        return query select 'validation_failure'::text, null::integer;
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
            id, owner_id, name, organizer_name, organizer_contact, status,
            format, group_count, revision
        ) values (
            v_tournament_id, v_owner_id, p_tournament ->> 'name', p_tournament ->> 'organizer_name',
            p_tournament ->> 'organizer_contact', p_tournament ->> 'status',
            v_format, v_group_count, 1
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
            organizer_contact = p_tournament ->> 'organizer_contact',
            status = p_tournament ->> 'status',
            format = v_format,
            group_count = v_group_count,
            revision = t.revision + 1,
            updated_at = now()
        where t.id = v_tournament_id;
        v_current_revision := v_current_revision + 1;
    end if;

    insert into public.tournament_team_slots as team_slot_target (
        id, tournament_id, slot_number, team_name, status, "group"
    )
    select id, tournament_id, slot_number, team_name, status, "group"
    from jsonb_to_recordset(p_team_slots) as slot_row(
        id uuid, tournament_id uuid, slot_number integer, team_name text, status text, "group" text
    )
    on conflict (id) do update
    set team_name = excluded.team_name,
        status = excluded.status,
        "group" = excluded."group",
        revision = team_slot_target.revision + 1,
        updated_at = now();

    insert into public.players as player_target (id, team_slot_id, display_name, normalized_name)
    select id, team_slot_id, display_name, normalized_name
    from jsonb_to_recordset(p_players) as player_row(
        id uuid, team_slot_id uuid, display_name text, normalized_name text
    )
    on conflict (id) do update
    set display_name = excluded.display_name,
        normalized_name = excluded.normalized_name,
        revision = player_target.revision + 1,
        updated_at = now();

    delete from public.tournament_group_pairings
    where tournament_id = v_tournament_id;

    insert into public.tournament_group_pairings (tournament_id, first_group, second_group)
    select v_tournament_id, first_group, second_group
    from jsonb_to_recordset(v_pairings) as pairing_row(
        first_group text, second_group text, pairing_key text
    );

    return query select 'success'::text, v_current_revision;
end;
$$;

revoke all on function public.write_tournament_snapshot(jsonb, jsonb, jsonb, integer) from public;
grant execute on function public.write_tournament_snapshot(jsonb, jsonb, jsonb, integer) to authenticated;

create or replace function public.replace_tournament_roster_snapshot(
    p_tournament_id uuid,
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
    v_current_revision integer;
    v_format text;
    v_group_count integer;
    v_expected_slot_count integer;
begin
    if p_expected_revision is null or p_expected_revision <= 0 then
        return query select 'missing_revision'::text, null::integer;
        return;
    end if;

    select t.revision, t.format, t.group_count
    into v_current_revision, v_format, v_group_count
    from public.tournaments t
    where t.id = p_tournament_id
    for update;

    if not found then
        return query select 'missing_revision'::text, null::integer;
        return;
    end if;

    if p_expected_revision <> v_current_revision then
        return query select 'stale_write'::text, v_current_revision;
        return;
    end if;

    if exists (select 1 from public.matches m where m.tournament_id = p_tournament_id) then
        return query select 'matches_exist'::text, v_current_revision;
        return;
    end if;

    if v_format = 'standard' then
        v_expected_slot_count := 12;
    elsif v_format = 'group_rotation' and v_group_count in (3, 4) then
        v_expected_slot_count := v_group_count * 6;
    else
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if coalesce(jsonb_typeof(p_team_slots), '') <> 'array'
        or jsonb_array_length(p_team_slots) <> v_expected_slot_count
        or coalesce(jsonb_typeof(p_players), '') <> 'array' then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_team_slots) as slot_row
        where coalesce(jsonb_typeof(slot_row), '') <> 'object'
            or coalesce(jsonb_typeof(slot_row -> 'id'), '') <> 'string'
            or coalesce(jsonb_typeof(slot_row -> 'tournament_id'), '') <> 'string'
            or coalesce(jsonb_typeof(slot_row -> 'slot_number'), '') <> 'number'
            or coalesce(jsonb_typeof(slot_row -> 'team_name'), '') <> 'string'
            or coalesce(jsonb_typeof(slot_row -> 'status'), '') <> 'string'
            or ((slot_row ? 'group') and jsonb_typeof(slot_row -> 'group') not in ('string', 'null'))
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_team_slots) as slot_row
        where (slot_row ->> 'id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (slot_row ->> 'tournament_id') <> p_tournament_id::text
            or (slot_row ->> 'slot_number') !~ '^([1-9]|1[0-9]|2[0-4])$'
            or btrim(slot_row ->> 'team_name') = ''
            or (slot_row ->> 'status') <> 'draft'
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if (
        select count(distinct slot_row ->> 'slot_number')
        from jsonb_array_elements(p_team_slots) as slot_row
    ) <> v_expected_slot_count
        or (
            select count(distinct slot_row ->> 'id')
            from jsonb_array_elements(p_team_slots) as slot_row
        ) <> v_expected_slot_count then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_team_slots) as slot_row
        where (v_format = 'standard' and slot_row -> 'group' is not null and slot_row -> 'group' <> 'null'::jsonb)
            or (v_format = 'group_rotation' and (slot_row ->> 'group') <> case
                when (slot_row ->> 'slot_number')::integer between 1 and 6 then 'A'
                when (slot_row ->> 'slot_number')::integer between 7 and 12 then 'B'
                when (slot_row ->> 'slot_number')::integer between 13 and 18 then 'C'
                else 'D'
            end)
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_team_slots) as slot_row
        where not exists (
            select 1
            from public.tournament_team_slots target_slot
            where target_slot.id = (slot_row ->> 'id')::uuid
                and target_slot.tournament_id = p_tournament_id
                and target_slot.slot_number = (slot_row ->> 'slot_number')::integer
        )
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_team_slots) as slot_row
        group by slot_row ->> 'team_name'
        having count(*) > 1
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_players) as player_row
        where coalesce(jsonb_typeof(player_row), '') <> 'object'
            or coalesce(jsonb_typeof(player_row -> 'id'), '') <> 'string'
            or coalesce(jsonb_typeof(player_row -> 'team_slot_id'), '') <> 'string'
            or coalesce(jsonb_typeof(player_row -> 'display_name'), '') <> 'string'
            or coalesce(jsonb_typeof(player_row -> 'normalized_name'), '') <> 'string'
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_players) as player_row
        where (player_row ->> 'id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or (player_row ->> 'team_slot_id') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
            or btrim(player_row ->> 'display_name') = ''
            or (player_row ->> 'normalized_name') <> btrim(player_row ->> 'display_name')
    ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    if (
        select count(distinct player_row ->> 'id')
        from jsonb_array_elements(p_players) as player_row
    ) <> jsonb_array_length(p_players)
        or exists (
            select 1
            from jsonb_array_elements(p_players) as player_row
            where not exists (
                select 1
                from public.tournament_team_slots target_slot
                where target_slot.id = (player_row ->> 'team_slot_id')::uuid
                    and target_slot.tournament_id = p_tournament_id
            )
        )
        or exists (
            select 1
            from jsonb_array_elements(p_players) as player_row
            group by player_row ->> 'team_slot_id', player_row ->> 'normalized_name'
            having count(*) > 1
        )
        or exists (
            select 1
            from jsonb_array_elements(p_players) as player_row
            group by player_row ->> 'team_slot_id'
            having count(*) > 6
        ) then
        return query select 'validation_failure'::text, v_current_revision;
        return;
    end if;

    insert into public.tournament_team_slots (
        id, tournament_id, slot_number, team_name, status, "group"
    )
    select id, tournament_id, slot_number, team_name, status, "group"
    from jsonb_to_recordset(p_team_slots) as slot_row(
        id uuid, tournament_id uuid, slot_number integer, team_name text, status text, "group" text
    )
    on conflict (id) do update
    set team_name = excluded.team_name,
        status = excluded.status,
        "group" = excluded."group",
        revision = public.tournament_team_slots.revision + 1,
        updated_at = now();

    delete from public.players stale_player
    using public.tournament_team_slots target_slot
    where stale_player.team_slot_id = target_slot.id
        and target_slot.tournament_id = p_tournament_id
        and not exists (
            select 1
            from jsonb_array_elements(p_players) as player_row
            where (player_row ->> 'id')::uuid = stale_player.id
        );

    insert into public.players (id, team_slot_id, display_name, normalized_name)
    select id, team_slot_id, display_name, normalized_name
    from jsonb_to_recordset(p_players) as player_row(
        id uuid, team_slot_id uuid, display_name text, normalized_name text
    )
    on conflict (id) do update
    set team_slot_id = excluded.team_slot_id,
        display_name = excluded.display_name,
        normalized_name = excluded.normalized_name,
        revision = public.players.revision + 1,
        updated_at = now();

    update public.tournaments
    set revision = public.tournaments.revision + 1,
        updated_at = now()
    where id = p_tournament_id;

    return query select 'success'::text, v_current_revision + 1;
end;
$$;

revoke all on function public.replace_tournament_roster_snapshot(uuid, jsonb, jsonb, integer) from public;
grant execute on function public.replace_tournament_roster_snapshot(uuid, jsonb, jsonb, integer) to authenticated;
