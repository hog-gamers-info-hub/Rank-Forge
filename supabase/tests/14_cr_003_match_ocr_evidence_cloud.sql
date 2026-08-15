begin;

select plan(30);

select ok(to_regclass('public.match_ocr_evidence') is not null, 'match-level OCR evidence table exists');
select ok(to_regclass('public.match_ocr_row_evidence') is not null, 'OCR row evidence table exists');
select ok(to_regclass('public.match_ocr_correction_snapshots') is not null, 'OCR correction snapshot table exists');
select ok(
    (select relrowsecurity from pg_class where oid = 'public.match_ocr_evidence'::regclass)
    and (select relrowsecurity from pg_class where oid = 'public.match_ocr_row_evidence'::regclass)
    and (select relrowsecurity from pg_class where oid = 'public.match_ocr_correction_snapshots'::regclass),
    'all OCR evidence tables have RLS enabled'
);
select ok(
    has_table_privilege('authenticated', 'public.match_ocr_evidence', 'select,insert,update,delete')
    and has_table_privilege('authenticated', 'public.match_ocr_row_evidence', 'select,insert,update,delete')
    and has_table_privilege('authenticated', 'public.match_ocr_correction_snapshots', 'select,insert,update,delete'),
    'authenticated has the required OCR evidence table grants'
);
select ok(
    has_table_privilege('authenticated', 'public.match_ocr_evidence', 'select')
        and has_table_privilege('authenticated', 'public.match_ocr_evidence', 'insert')
        and has_table_privilege('authenticated', 'public.match_ocr_evidence', 'update')
        and has_table_privilege('authenticated', 'public.match_ocr_evidence', 'delete')
        and not has_table_privilege('authenticated', 'public.match_ocr_evidence', 'truncate')
        and not has_table_privilege('authenticated', 'public.match_ocr_evidence', 'references')
        and not has_table_privilege('authenticated', 'public.match_ocr_evidence', 'trigger')
        and not has_table_privilege('authenticated', 'public.match_ocr_evidence', 'maintain'),
    'match-level OCR evidence grants are exact'
);
select ok(
    has_table_privilege('authenticated', 'public.match_ocr_row_evidence', 'select')
        and has_table_privilege('authenticated', 'public.match_ocr_row_evidence', 'insert')
        and has_table_privilege('authenticated', 'public.match_ocr_row_evidence', 'update')
        and has_table_privilege('authenticated', 'public.match_ocr_row_evidence', 'delete')
        and not has_table_privilege('authenticated', 'public.match_ocr_row_evidence', 'truncate')
        and not has_table_privilege('authenticated', 'public.match_ocr_row_evidence', 'references')
        and not has_table_privilege('authenticated', 'public.match_ocr_row_evidence', 'trigger')
        and not has_table_privilege('authenticated', 'public.match_ocr_row_evidence', 'maintain'),
    'OCR row evidence grants are exact'
);
select ok(
    has_table_privilege('authenticated', 'public.match_ocr_correction_snapshots', 'select')
        and has_table_privilege('authenticated', 'public.match_ocr_correction_snapshots', 'insert')
        and has_table_privilege('authenticated', 'public.match_ocr_correction_snapshots', 'update')
        and has_table_privilege('authenticated', 'public.match_ocr_correction_snapshots', 'delete')
        and not has_table_privilege('authenticated', 'public.match_ocr_correction_snapshots', 'truncate')
        and not has_table_privilege('authenticated', 'public.match_ocr_correction_snapshots', 'references')
        and not has_table_privilege('authenticated', 'public.match_ocr_correction_snapshots', 'trigger')
        and not has_table_privilege('authenticated', 'public.match_ocr_correction_snapshots', 'maintain'),
    'OCR correction snapshot grants are exact'
);
select ok(
    exists (
        select 1
        from pg_indexes
        where schemaname = 'public'
            and tablename = 'match_ocr_evidence'
            and indexname = 'match_ocr_evidence_match_tournament_idx'
            and position('(match_id, tournament_id)' in indexdef) > 0
    ),
    'match-level OCR evidence has a covering match/tournament index'
);
select ok(
    exists (
        select 1
        from pg_indexes
        where schemaname = 'public'
            and tablename = 'match_ocr_row_evidence'
            and indexname = 'match_ocr_row_evidence_match_tournament_idx'
            and position('(match_id, tournament_id)' in indexdef) > 0
    ),
    'OCR row evidence has a covering match/tournament index'
);
select ok(
    exists (
        select 1
        from pg_indexes
        where schemaname = 'public'
            and tablename = 'match_ocr_correction_snapshots'
            and indexname = 'match_ocr_correction_snapshots_match_tournament_idx'
            and position('(match_id, tournament_id)' in indexdef) > 0
    ),
    'OCR correction snapshots have a covering match/tournament index'
);

insert into auth.users (id, email)
values
    ('99000000-0000-0000-0000-000000000001', 'ocr-owner@example.test'),
    ('99000000-0000-0000-0000-000000000002', 'ocr-other@example.test');
insert into public.tournaments (id, owner_id, name)
values (
    '99100000-0000-0000-0000-000000000001',
    '99000000-0000-0000-0000-000000000001',
    'OCR Evidence Cup'
);
insert into public.matches (id, tournament_id, match_number, status)
values (
    '99200000-0000-0000-0000-000000000001',
    '99100000-0000-0000-0000-000000000001',
    1,
    'finalized'
);
insert into public.tournaments (id, owner_id, name)
values (
    '99100000-0000-0000-0000-000000000002',
    '99000000-0000-0000-0000-000000000002',
    'Other OCR Evidence Cup'
);
insert into public.matches (id, tournament_id, match_number, status)
values (
    '99200000-0000-0000-0000-000000000002',
    '99100000-0000-0000-0000-000000000002',
    1,
    'finalized'
);

set local role authenticated;
set local request.jwt.claim.sub = '99000000-0000-0000-0000-000000000001';

set local role postgres;
select throws_ok($$
    insert into public.match_ocr_evidence (
        match_id, tournament_id, preserved_at, provenance
    ) values (
        '99200000-0000-0000-0000-000000000001',
        '99100000-0000-0000-0000-000000000002',
        now(),
        'MISMATCHED_PARENT'
    )
$$, '23503', null, 'match-level evidence rejects mismatched match and tournament');
select throws_ok($$
    insert into public.match_ocr_row_evidence (
        match_id, tournament_id, row_index, manual_review_required
    ) values (
        '99200000-0000-0000-0000-000000000001',
        '99100000-0000-0000-0000-000000000002',
        1,
        false
    )
$$, '23503', null, 'row evidence rejects mismatched match and tournament');
select throws_ok($$
    insert into public.match_ocr_correction_snapshots (
        match_id, tournament_id, row_index, corrected_placement, corrected_kills,
        corrected_team_slot, placement_changed, kills_changed, team_slot_changed,
        preserved_at, provenance
    ) values (
        '99200000-0000-0000-0000-000000000001',
        '99100000-0000-0000-0000-000000000002',
        1, 1, 0, 1, false, false, false, now(), 'MISMATCHED_PARENT'
    )
$$, '23503', null, 'correction snapshots reject mismatched match and tournament');
set local role authenticated;
set local request.jwt.claim.sub = '99000000-0000-0000-0000-000000000001';

insert into public.match_ocr_evidence (
    match_id, tournament_id, source_screenshot_id, preserved_at, provenance
) values (
    '99200000-0000-0000-0000-000000000001',
    '99100000-0000-0000-0000-000000000001',
    'MATCH_RESULT_UPPER',
    '2026-08-14T09:00:00Z',
    'OCR_REVIEW_FINALIZATION'
);
select is((select count(*) from public.match_ocr_evidence), 1::bigint, 'owner can insert match-level OCR evidence');

insert into public.match_ocr_row_evidence (
    match_id, tournament_id, row_index, original_ocr_text, original_placement,
    original_kills, original_suggested_team_slot, confidence_summary,
    safety_summary, manual_review_required
) values (
    '99200000-0000-0000-0000-000000000001',
    '99100000-0000-0000-0000-000000000001',
    0, 'Alpha 1', 1, 2, 1, 'HIGH', 'SAFE', false
);
select is((select count(*) from public.match_ocr_row_evidence), 1::bigint, 'owner can insert OCR row evidence');

insert into public.match_ocr_correction_snapshots (
    match_id, tournament_id, row_index, corrected_placement, corrected_kills,
    corrected_team_slot, placement_changed, kills_changed, team_slot_changed,
    preserved_at, provenance
) values (
    '99200000-0000-0000-0000-000000000001',
    '99100000-0000-0000-0000-000000000001',
    0, 1, 2, 1, false, false, false, '2026-08-14T09:00:00Z', 'OCR_REVIEW_FINALIZATION'
);
select is((select count(*) from public.match_ocr_correction_snapshots), 1::bigint, 'owner can insert correction snapshots');

select is(
    (select provenance from public.match_ocr_evidence where match_id = '99200000-0000-0000-0000-000000000001'),
    'OCR_REVIEW_FINALIZATION',
    'owner can select own match-level evidence'
);
update public.match_ocr_evidence
set provenance = 'OCR_REVIEW_FINALIZATION_RETRY'
where match_id = '99200000-0000-0000-0000-000000000001';
select is(
    (select provenance from public.match_ocr_evidence where match_id = '99200000-0000-0000-0000-000000000001'),
    'OCR_REVIEW_FINALIZATION_RETRY',
    'owner can update own evidence'
);

set local request.jwt.claim.sub = '99000000-0000-0000-0000-000000000002';
select is((select count(*) from public.match_ocr_evidence), 0::bigint, 'different user cannot select owner evidence');
select throws_ok($$
    insert into public.match_ocr_evidence (
        match_id, tournament_id, preserved_at, provenance
    ) values (
        '99200000-0000-0000-0000-000000000001',
        '99100000-0000-0000-0000-000000000001',
        now(),
        'UNAUTHORIZED'
    )
$$, '42501', null, 'different user cannot insert owner evidence');
update public.match_ocr_evidence set provenance = 'OTHER_UPDATE';
delete from public.match_ocr_evidence;
set local request.jwt.claim.sub = '99000000-0000-0000-0000-000000000001';
select is(
    (select provenance from public.match_ocr_evidence where match_id = '99200000-0000-0000-0000-000000000001'),
    'OCR_REVIEW_FINALIZATION_RETRY',
    'different user cannot update or delete owner evidence'
);

set local role postgres;
select throws_ok($$
    insert into public.match_ocr_row_evidence (
        match_id, tournament_id, row_index, manual_review_required
    ) values (
        '99300000-0000-0000-0000-000000000001',
        '99100000-0000-0000-0000-000000000001',
        1,
        false
    )
$$, '23503', null, 'OCR evidence cannot reference an invalid match');
set local role authenticated;
set local request.jwt.claim.sub = '99000000-0000-0000-0000-000000000001';
select throws_ok($$
    insert into public.match_ocr_row_evidence (
        match_id, tournament_id, row_index, manual_review_required
    ) values (
        '99200000-0000-0000-0000-000000000001',
        '99100000-0000-0000-0000-000000000001',
        0,
        false
    )
$$, '23505', null, 'OCR row identity is unique per match');

delete from public.match_ocr_row_evidence where match_id = '99200000-0000-0000-0000-000000000001';
select is((select count(*) from public.match_ocr_row_evidence), 0::bigint, 'owner can delete OCR row evidence');
delete from public.match_ocr_correction_snapshots where match_id = '99200000-0000-0000-0000-000000000001';
select is((select count(*) from public.match_ocr_correction_snapshots), 0::bigint, 'owner can delete correction snapshots');
delete from public.match_ocr_evidence where match_id = '99200000-0000-0000-0000-000000000001';
select is((select count(*) from public.match_ocr_evidence), 0::bigint, 'owner can delete match-level evidence');

insert into public.match_ocr_evidence (
    match_id, tournament_id, preserved_at, provenance
) values (
    '99200000-0000-0000-0000-000000000001',
    '99100000-0000-0000-0000-000000000001',
    now(),
    'CASCADE_TEST'
);
insert into public.match_ocr_row_evidence (
    match_id, tournament_id, row_index, manual_review_required
) values (
    '99200000-0000-0000-0000-000000000001',
    '99100000-0000-0000-0000-000000000001',
    0,
    false
);
insert into public.match_ocr_correction_snapshots (
    match_id, tournament_id, row_index, corrected_placement, corrected_kills,
    corrected_team_slot, placement_changed, kills_changed, team_slot_changed,
    preserved_at, provenance
) values (
    '99200000-0000-0000-0000-000000000001',
    '99100000-0000-0000-0000-000000000001',
    0, 1, 0, 1, false, false, false, now(), 'CASCADE_TEST'
);
set local role postgres;
delete from public.matches where id = '99200000-0000-0000-0000-000000000001';
select is(
    (select count(*) from public.matches where id = '99200000-0000-0000-0000-000000000001')
        + (select count(*) from public.match_ocr_evidence where match_id = '99200000-0000-0000-0000-000000000001'),
    0::bigint,
    'privileged parent deletion cascades match-level OCR evidence'
);
select is((select count(*) from public.match_ocr_row_evidence), 0::bigint, 'match deletion cascades OCR row evidence');
select is((select count(*) from public.match_ocr_correction_snapshots), 0::bigint, 'match deletion cascades correction snapshots');

select * from finish();
rollback;
