begin;

select plan(26);

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

insert into public.tournaments (id, owner_id, name)
values
    ('a2000000-0000-0000-0000-000000000001', 'a1000000-0000-0000-0000-000000000001', 'Deletion tournament'),
    ('a2000000-0000-0000-0000-000000000002', 'a1000000-0000-0000-0000-000000000001', 'Unrelated tournament'),
    ('a2000000-0000-0000-0000-000000000003', 'a1000000-0000-0000-0000-000000000002', 'Other owner tournament');

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
    'NOT_FOUND_OR_NOT_OWNER',
    'absent match without receipt fails closed'
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
    'NOT_FOUND_OR_NOT_OWNER',
    'another owners receipt cannot authorize this caller'
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

set local role anon;
select throws_ok($$
    select * from public.delete_tournament_idempotent('a2000000-0000-0000-0000-000000000002')
$$, '42501', null, 'unauthenticated caller cannot invoke tournament deletion');

rollback;
