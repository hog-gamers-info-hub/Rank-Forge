begin;

select plan(39);

select ok(

    (select relrowsecurity from pg_class where oid = 'public.deletion_receipts'::regclass),

    'deletion receipts retain RLS'

);

select is(

    (

        select count(*)

        from pg_constraint constraint_row

        where constraint_row.conrelid = 'public.deletion_receipts'::regclass

            and constraint_row.confrelid in ('public.matches'::regclass, 'public.tournaments'::regclass)

    ),

    0::bigint,

    'receipts do not cascade from deleted targets'

);

select ok(

    (select prosecdef from pg_proc where oid = 'public.delete_match_idempotent(uuid)'::regprocedure),

    'match deletion RPC is security definer'

);

select ok(

    has_function_privilege('authenticated', 'public.delete_match_idempotent(uuid)', 'execute'),

    'authenticated can execute match deletion RPC'

);

select ok(

    not has_function_privilege('anon', 'public.delete_match_idempotent(uuid)', 'execute'),

    'anon cannot execute match deletion RPC'

);

select ok(

    (select prosecdef from pg_proc where oid = 'public.delete_tournament_idempotent(uuid)'::regprocedure),

    'tournament deletion RPC is security definer'

);

select ok(

    has_function_privilege('authenticated', 'public.delete_tournament_idempotent(uuid)', 'execute'),

    'authenticated can execute tournament deletion RPC'

);

select ok(

    not has_function_privilege('anon', 'public.delete_tournament_idempotent(uuid)', 'execute'),

    'anon cannot execute tournament deletion RPC'

);

insert into auth.users (id, email)

values

    ('a1000000-0000-0000-0000-000000000001', 'deletion-owner@example.test'),

    ('a1000000-0000-0000-0000-000000000002', 'deletion-other@example.test');

insert into public.tournaments (id, owner_id, name, format, group_count)

values

    ('a2000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 'Deletion tournament', default, default),

    ('a2000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001', 'Unrelated tournament', default, default),

    ('a2000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000002', 'Other owner tournament', default, default),

    ('a2000000-0000-0000-0000-000000000004', 'a1000000-0000-0000-0000-000000000001', 'Group Rotation deletion tournament', 'group_rotation', 3),

    ('a2000000-0000-0000-0000-000000000005', 'a1000000-0000-0000-0000-000000000002', 'Other owner Group Rotation tournament', 'group_rotation', 3);

insert into public.tournament_group_pairings (tournament_id, first_group, second_group)

values ('a2000000-0000-0000-0000-000000000004', 'A', 'B');

insert into public.tournament_team_identities (

    tournament_id,

    team_uuid,

    display_name,

    normalized_name

)

values (

    'a2000000-0000-0000-0000-000000000004',

    'a6000000-0000-0000-0000-000000000001',

    'V4 Deletion Team',

    'v4 deletion team'

);

insert into public.tournament_group_pairing_team_assignments (

    tournament_id,

    pairing_key,

    lobby_slot_number,

    team_uuid,

    name_cleared

)

values (

    'a2000000-0000-0000-0000-000000000004',

    'A:B',

    1,

    'a6000000-0000-0000-0000-000000000001',

    false

);

insert into public.matches (id, tournament_id, match_number, status, finalized_at, finalized_by, group_pairing_key)
values (
    'a4000000-0000-0000-0000-000000000003',
    'a2000000-0000-0000-0000-000000000004',
    1,
    'finalized',
    '2026-10-14T00:00:00Z',
    'a1000000-0000-0000-0000-000000000001',
    'A:B'
);

insert into public.group_rotation_match_uuid_snapshots (

    match_id,

    tournament_id,

    pairing_key,

    status,

    revision,

    finalized_at,

    finalized_by

)

values (

    'a4000000-0000-0000-0000-000000000003',

    'a2000000-0000-0000-0000-000000000004',

    'A:B',

    'finalized',

    1,

    '2026-10-14T00:00:00Z',

    'a1000000-0000-0000-0000-000000000001'

);

insert into public.group_rotation_match_uuid_assignments (

    match_id,

    tournament_id,

    lobby_slot_number,

    team_uuid

)

values (

    'a4000000-0000-0000-0000-000000000003',

    'a2000000-0000-0000-0000-000000000004',

    1,

    'a6000000-0000-0000-0000-000000000001'

);

insert into public.group_rotation_match_uuid_results (

    match_id,

    tournament_id,

    team_uuid,

    participation_status,

    placement,

    kills,

    point_adjustment

)

values (

    'a4000000-0000-0000-0000-000000000003',

    'a2000000-0000-0000-0000-000000000004',

    'a6000000-0000-0000-0000-000000000001',

    'PARTICIPATED',

    1,

    10,

    0

);

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)

values

    ('a3000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000001', 1, 'Deletion team'),

    ('a3000000-0000-0000-0000-000000000002', 'a2000000-0000-0000-0000-000000000003', 1, 'Other team');

insert into public.matches (id, tournament_id, match_number)

values

    ('a4000000-0000-0000-0000-000000000001', 'a2000000-0000-0000-0000-000000000001', 1),

    ('a4000000-0000-0000-0000-000000000002', 'a2000000-0000-0000-0000-000000000003', 1);

insert into public.match_results (id, match_id, team_slot_id, placement)

values

    ('a5000000-0000-0000-0000-000000000001', 'a4000000-0000-0000-0000-000000000001', 'a3000000-0000-0000-0000-000000000001', 1);

set local role authenticated;

set local request.jwt.claim.sub = 'a1000000-0000-0000-0000-000000000001';

select is(

    (select outcome from public.delete_match_idempotent('a4000000-0000-0000-0000-000000000001')),

    'DELETED',

    'owner match delete returns DELETED'

);

select is((select count(*) from public.matches where id = 'a4000000-0000-0000-0000-000000000001'), 0::bigint, 'match parent is removed');

select is((select count(*) from public.match_results where match_id = 'a4000000-0000-0000-0000-000000000001'), 0::bigint, 'match children cascade');

set local role postgres;

select is((select count(*) from public.deletion_receipts where target_type = 'MATCH' and target_id = 'a4000000-0000-0000-0000-000000000001'), 1::bigint, 'match receipt survives parent deletion');

set local role authenticated;

select is(

    (select outcome from public.delete_match_idempotent('a4000000-0000-0000-0000-000000000001')),

    'ALREADY_DELETED',

    'same owner match retry returns ALREADY_DELETED'

);

select is(

    (select outcome from public.delete_match_idempotent('a4000000-0000-0000-0000-000000000099')),

    'NOT_FOUND',

    'absent match without receipt reports not found'

);

select is(

    (select outcome from public.delete_match_idempotent('a4000000-0000-0000-0000-000000000002')),

    'NOT_FOUND_OR_NOT_OWNER',

    'non-owner cannot delete another owners match'

);

set local request.jwt.claim.sub = 'a1000000-0000-0000-0000-000000000002';

select is(

    (select outcome from public.delete_match_idempotent('a4000000-0000-0000-0000-000000000002')),

    'DELETED',

    'other owner can delete their own match'

);

set local request.jwt.claim.sub = 'a1000000-0000-0000-0000-000000000001';

select is(

    (select outcome from public.delete_match_idempotent('a4000000-0000-0000-0000-000000000002')),

    'NOT_FOUND',

    'another owners receipt cannot authorize this caller after deletion'

);

set local request.jwt.claim.sub = 'a1000000-0000-0000-0000-000000000002';

select is(

    (select outcome from public.delete_match_idempotent('a4000000-0000-0000-0000-000000000002')),

    'ALREADY_DELETED',

    'receipt is scoped to its original owner'

);

set local request.jwt.claim.sub = 'a1000000-0000-0000-0000-000000000001';

select throws_ok($$

    insert into public.deletion_receipts (owner_id, target_type, target_id)

    values ('a1000000-0000-0000-0000-000000000001', 'TOURNAMENT', 'a2000000-0000-0000-0000-000000000099')

$$, '42501', null, 'authenticated cannot forge a completed receipt');

select is(

    (select outcome from public.delete_tournament_idempotent('a2000000-0000-0000-0000-000000000001')),

    'DELETED',

    'owner tournament delete returns DELETED'

);

select is((select count(*) from public.tournaments where id = 'a2000000-0000-0000-0000-000000000001'), 0::bigint, 'tournament parent is removed');

select is((select count(*) from public.tournament_team_slots where tournament_id = 'a2000000-0000-0000-0000-000000000001'), 0::bigint, 'tournament children cascade');

set local role postgres;

select is((select count(*) from public.deletion_receipts where target_type = 'TOURNAMENT' and target_id = 'a2000000-0000-0000-0000-000000000001'), 1::bigint, 'tournament receipt survives parent deletion');

set local role authenticated;

select is(

    (select outcome from public.delete_tournament_idempotent('a2000000-0000-0000-0000-000000000001')),

    'ALREADY_DELETED',

    'same owner tournament retry returns ALREADY_DELETED'

);

select is((select count(*) from public.tournaments where id = 'a2000000-0000-0000-0000-000000000002'), 1::bigint, 'unrelated tournament remains intact');

select is(

    (select outcome from public.delete_tournament_idempotent('a2000000-0000-0000-0000-000000000004')),

    'DELETED',

    'owner Group Rotation tournament delete returns DELETED'

);

set local role postgres;

select is((select count(*) from public.tournaments where id = 'a2000000-0000-0000-0000-000000000004'), 0::bigint, 'Group Rotation tournament parent is removed');

select is((select count(*) from public.matches where tournament_id = 'a2000000-0000-0000-0000-000000000004'), 0::bigint, 'Group Rotation matches are removed first');

select is((select count(*) from public.group_rotation_match_uuid_snapshots where tournament_id = 'a2000000-0000-0000-0000-000000000004'), 0::bigint, 'V4 match snapshots cascade from match deletion');

select is((select count(*) from public.group_rotation_match_uuid_assignments where tournament_id = 'a2000000-0000-0000-0000-000000000004'), 0::bigint, 'V4 match assignments cascade from snapshot deletion');

select is((select count(*) from public.group_rotation_match_uuid_results where tournament_id = 'a2000000-0000-0000-0000-000000000004'), 0::bigint, 'V4 match results cascade from snapshot deletion');

select is((select count(*) from public.tournament_group_pairings where tournament_id = 'a2000000-0000-0000-0000-000000000004'), 0::bigint, 'Group Rotation pairings are removed after matches');

select is((select count(*) from public.tournament_group_pairing_team_assignments where tournament_id = 'a2000000-0000-0000-0000-000000000004'), 0::bigint, 'pairing UUID assignments cascade from pairing deletion');

select is((select count(*) from public.tournament_team_identities where tournament_id = 'a2000000-0000-0000-0000-000000000004'), 0::bigint, 'registered identities cascade from tournament deletion');

select is((select count(*) from public.deletion_receipts where target_type = 'TOURNAMENT' and target_id = 'a2000000-0000-0000-0000-000000000004'), 1::bigint, 'Group Rotation deletion receipt survives parent deletion');

set local role authenticated;

select is(

    (select outcome from public.delete_tournament_idempotent('a2000000-0000-0000-0000-000000000004')),

    'ALREADY_DELETED',

    'same owner Group Rotation retry returns ALREADY_DELETED'

);

select is(

    (select outcome from public.delete_tournament_idempotent('a2000000-0000-0000-0000-000000000005')),

    'NOT_FOUND_OR_NOT_OWNER',

    'owner cannot delete another owners Group Rotation tournament'

);

set local role postgres;

select is((select count(*) from public.tournaments where id = 'a2000000-0000-0000-0000-000000000005'), 1::bigint, 'unauthorized Group Rotation delete leaves tournament intact');

set local role authenticated;

set local role anon;

select throws_ok($$

    select * from public.delete_tournament_idempotent('a2000000-0000-0000-0000-000000000002')

$$, '42501', null, 'unauthenticated caller cannot invoke tournament deletion');

rollback;
