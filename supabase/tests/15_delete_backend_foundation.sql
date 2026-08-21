begin;

select plan(16);

select is(
    (
        select count(*)
        from pg_policies
        where schemaname = 'public'
            and tablename = 'matches'
            and policyname = 'matches_delete_owner'
            and cmd = 'DELETE'
            and qual not like '%status%'
    ),
    1::bigint,
    'owner match DELETE policy is not limited to draft status'
);

select is(
    (
        select count(*)
        from pg_policies
        where schemaname = 'storage'
            and tablename = 'objects'
            and policyname = 'match_screenshots_delete_owner'
            and cmd = 'DELETE'
    ),
    1::bigint,
    'legacy match screenshot DELETE policy exists'
);

select is(
    (
        select count(*)
        from pg_policies
        where schemaname = 'storage'
            and tablename = 'objects'
            and policyname = 'ocr_screenshots_delete_owner'
            and cmd = 'DELETE'
    ),
    1::bigint,
    'OCR result screenshot DELETE policy exists'
);

select is(
    (
        select count(*)
        from pg_policies
        where schemaname = 'storage'
            and tablename = 'objects'
            and policyname = 'ocr_screenshots_lobby_delete_owner'
            and cmd = 'DELETE'
    ),
    1::bigint,
    'OCR lobby screenshot DELETE policy exists'
);

select is(
    (
        select count(*)
        from pg_constraint
        where conname in (
            'match_results_team_slot_id_fkey',
            'match_correction_audit_entries_match_result_id_fkey',
            'match_correction_audit_entries_team_slot_id_fkey'
        )
            and condeferrable
            and condeferred
    ),
    3::bigint,
    'cross-branch cascade references are initially deferred'
);

insert into auth.users (id, email)
values
    ('96000000-0000-0000-0000-000000000001', 'delete-owner@example.test'),
    ('96000000-0000-0000-0000-000000000002', 'delete-other@example.test');

insert into public.tournaments (id, owner_id, name)
values
    ('97000000-0000-0000-0000-000000000001', '96000000-0000-0000-0000-000000000001', 'Delete Match Cup'),
    ('97000000-0000-0000-0000-000000000002', '96000000-0000-0000-0000-000000000001', 'Delete Tournament Cup');

insert into public.tournament_team_slots (id, tournament_id, slot_number, team_name)
values
    ('98000000-0000-0000-0000-000000000001', '97000000-0000-0000-0000-000000000001', 1, 'Team One'),
    ('98000000-0000-0000-0000-000000000002', '97000000-0000-0000-0000-000000000002', 1, 'Team Two');

insert into public.matches (id, tournament_id, match_number, status, finalized_at, finalized_by)
values
    ('99000000-0000-0000-0000-000000000001', '97000000-0000-0000-0000-000000000001', 1, 'finalized', now(), '96000000-0000-0000-0000-000000000001'),
    ('99000000-0000-0000-0000-000000000002', '97000000-0000-0000-0000-000000000002', 1, 'finalized', now(), '96000000-0000-0000-0000-000000000001');

insert into public.match_results (id, match_id, team_slot_id, placement, kills, source, review_status)
values
    ('9a000000-0000-0000-0000-000000000001', '99000000-0000-0000-0000-000000000001', '98000000-0000-0000-0000-000000000001', 1, 0, 'manual', 'confirmed'),
    ('9a000000-0000-0000-0000-000000000002', '99000000-0000-0000-0000-000000000002', '98000000-0000-0000-0000-000000000002', 1, 0, 'manual', 'confirmed');

insert into public.match_ocr_evidence (match_id, tournament_id, preserved_at, provenance)
values
    ('99000000-0000-0000-0000-000000000001', '97000000-0000-0000-0000-000000000001', now(), 'delete-test'),
    ('99000000-0000-0000-0000-000000000002', '97000000-0000-0000-0000-000000000002', now(), 'delete-test');

insert into public.match_correction_audit_entries (
    id,
    tournament_id,
    match_id,
    match_result_id,
    team_slot_id,
    previous_placement,
    previous_kills,
    corrected_placement,
    corrected_kills,
    previous_revision,
    new_revision,
    corrected_by,
    correction_reason
)
values
    ('9b000000-0000-0000-0000-000000000001', '97000000-0000-0000-0000-000000000001', '99000000-0000-0000-0000-000000000001', '9a000000-0000-0000-0000-000000000001', '98000000-0000-0000-0000-000000000001', 1, 0, 1, 1, 1, 2, '96000000-0000-0000-0000-000000000001', 'delete-test'),
    ('9b000000-0000-0000-0000-000000000002', '97000000-0000-0000-0000-000000000002', '99000000-0000-0000-0000-000000000002', '9a000000-0000-0000-0000-000000000002', '98000000-0000-0000-0000-000000000002', 1, 0, 1, 1, 1, 2, '96000000-0000-0000-0000-000000000001', 'delete-test');

insert into public.export_operations (
    id,
    owner_id,
    operation_type,
    tournament_id,
    match_id,
    payload_fingerprint,
    state,
    failure_code
)
values
    ('9c000000-0000-0000-0000-000000000001', '96000000-0000-0000-0000-000000000001', 'export_match', '97000000-0000-0000-0000-000000000001', '99000000-0000-0000-0000-000000000001', repeat('a', 64), 'retryable_failure', 'TEST'),
    ('9c000000-0000-0000-0000-000000000002', '96000000-0000-0000-0000-000000000001', 'export_match', '97000000-0000-0000-0000-000000000002', '99000000-0000-0000-0000-000000000002', repeat('b', 64), 'retryable_failure', 'TEST');

set local role authenticated;
set local request.jwt.claim.sub = '96000000-0000-0000-0000-000000000002';

delete from public.matches
where id = '99000000-0000-0000-0000-000000000001';

reset role;

select is(
    (select count(*) from public.matches where id = '99000000-0000-0000-0000-000000000001'),
    1::bigint,
    'non-owner cannot delete another account finalized match'
);

set local role authenticated;
set local request.jwt.claim.sub = '96000000-0000-0000-0000-000000000001';

delete from public.matches
where id = '99000000-0000-0000-0000-000000000001';

reset role;

select is((select count(*) from public.matches where id = '99000000-0000-0000-0000-000000000001'), 0::bigint, 'owner can delete finalized match');
select is((select count(*) from public.match_results where match_id = '99000000-0000-0000-0000-000000000001'), 0::bigint, 'match delete cascades results');
select is((select count(*) from public.match_ocr_evidence where match_id = '99000000-0000-0000-0000-000000000001'), 0::bigint, 'match delete cascades OCR evidence');
select is((select count(*) from public.match_correction_audit_entries where match_id = '99000000-0000-0000-0000-000000000001'), 0::bigint, 'match delete cascades correction audit rows');
select is((select count(*) from public.export_operations where match_id = '99000000-0000-0000-0000-000000000001'), 0::bigint, 'match delete cascades match export operations');

set local role authenticated;
set local request.jwt.claim.sub = '96000000-0000-0000-0000-000000000001';

delete from public.tournaments
where id = '97000000-0000-0000-0000-000000000002';

reset role;

select is((select count(*) from public.tournaments where id = '97000000-0000-0000-0000-000000000002'), 0::bigint, 'owner can delete tournament parent');
select is((select count(*) from public.matches where tournament_id = '97000000-0000-0000-0000-000000000002'), 0::bigint, 'tournament delete cascades matches');
select is((select count(*) from public.match_results where match_id = '99000000-0000-0000-0000-000000000002'), 0::bigint, 'tournament delete cascades nested match results');
select is((select count(*) from public.match_correction_audit_entries where tournament_id = '97000000-0000-0000-0000-000000000002'), 0::bigint, 'tournament delete cascades nested correction audit rows');
select is((select count(*) from public.export_operations where tournament_id = '97000000-0000-0000-0000-000000000002'), 0::bigint, 'tournament delete cascades export operations');

select * from finish();
rollback;
