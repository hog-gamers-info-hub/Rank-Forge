-- CR-003: immutable historical OCR evidence associated with finalized matches.

alter table public.matches
    add constraint matches_id_tournament_id_key
        unique (id, tournament_id);

create table public.match_ocr_evidence (
    match_id uuid primary key,
    tournament_id uuid not null,
    source_screenshot_id text,
    preserved_at timestamptz not null,
    provenance text not null,
    constraint match_ocr_evidence_match_tournament_fkey
        foreign key (match_id, tournament_id)
        references public.matches(id, tournament_id)
        on delete cascade,
    constraint match_ocr_evidence_provenance_check
        check (btrim(provenance) <> '')
);

create table public.match_ocr_row_evidence (
    match_id uuid not null,
    tournament_id uuid not null,
    row_index integer not null,
    original_ocr_text text,
    original_placement integer,
    original_kills integer,
    original_suggested_team_slot integer,
    confidence_summary text,
    safety_summary text,
    manual_review_required boolean not null,
    primary key (match_id, row_index),
    constraint match_ocr_row_evidence_match_tournament_fkey
        foreign key (match_id, tournament_id)
        references public.matches(id, tournament_id)
        on delete cascade,
    constraint match_ocr_row_evidence_index_check
        check (row_index >= 0),
    constraint match_ocr_row_evidence_placement_check
        check (original_placement is null or original_placement between 1 and 12),
    constraint match_ocr_row_evidence_kills_check
        check (original_kills is null or original_kills >= 0),
    constraint match_ocr_row_evidence_team_slot_check
        check (original_suggested_team_slot is null or original_suggested_team_slot between 1 and 12)
);

create table public.match_ocr_correction_snapshots (
    match_id uuid not null,
    tournament_id uuid not null,
    row_index integer not null,
    corrected_placement integer not null,
    corrected_kills integer not null,
    corrected_team_slot integer not null,
    placement_changed boolean not null,
    kills_changed boolean not null,
    team_slot_changed boolean not null,
    preserved_at timestamptz not null,
    provenance text not null,
    primary key (match_id, row_index),
    constraint match_ocr_correction_snapshots_match_tournament_fkey
        foreign key (match_id, tournament_id)
        references public.matches(id, tournament_id)
        on delete cascade,
    constraint match_ocr_correction_snapshots_index_check
        check (row_index >= 0),
    constraint match_ocr_correction_snapshots_placement_check
        check (corrected_placement between 1 and 12),
    constraint match_ocr_correction_snapshots_kills_check
        check (corrected_kills >= 0),
    constraint match_ocr_correction_snapshots_team_slot_check
        check (corrected_team_slot between 1 and 12),
    constraint match_ocr_correction_snapshots_provenance_check
        check (btrim(provenance) <> '')
);

create index match_ocr_evidence_tournament_id_idx
on public.match_ocr_evidence (tournament_id);

create index match_ocr_row_evidence_tournament_id_idx
on public.match_ocr_row_evidence (tournament_id);

create index match_ocr_correction_snapshots_tournament_id_idx
on public.match_ocr_correction_snapshots (tournament_id);

create index match_ocr_evidence_match_tournament_idx
on public.match_ocr_evidence (match_id, tournament_id);

create index match_ocr_row_evidence_match_tournament_idx
on public.match_ocr_row_evidence (match_id, tournament_id);

create index match_ocr_correction_snapshots_match_tournament_idx
on public.match_ocr_correction_snapshots (match_id, tournament_id);

alter table public.match_ocr_evidence enable row level security;
alter table public.match_ocr_row_evidence enable row level security;
alter table public.match_ocr_correction_snapshots enable row level security;

revoke all on public.match_ocr_evidence from anon;
revoke all on public.match_ocr_row_evidence from anon;
revoke all on public.match_ocr_correction_snapshots from anon;
grant select, insert, update, delete on public.match_ocr_evidence to authenticated;
grant select, insert, update, delete on public.match_ocr_row_evidence to authenticated;
grant select, insert, update, delete on public.match_ocr_correction_snapshots to authenticated;

create policy match_ocr_evidence_select_owner
on public.match_ocr_evidence
for select
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_ocr_evidence.tournament_id
            and match_row.id = match_ocr_evidence.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

create policy match_ocr_evidence_insert_owner
on public.match_ocr_evidence
for insert
to authenticated
with check (
    exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_ocr_evidence.tournament_id
            and match_row.id = match_ocr_evidence.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

create policy match_ocr_evidence_update_owner
on public.match_ocr_evidence
for update
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_ocr_evidence.tournament_id
            and match_row.id = match_ocr_evidence.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
)
with check (
    exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_ocr_evidence.tournament_id
            and match_row.id = match_ocr_evidence.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

create policy match_ocr_evidence_delete_owner
on public.match_ocr_evidence
for delete
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_ocr_evidence.tournament_id
            and match_row.id = match_ocr_evidence.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

create policy match_ocr_row_evidence_select_owner
on public.match_ocr_row_evidence
for select
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_ocr_row_evidence.tournament_id
            and match_row.id = match_ocr_row_evidence.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

create policy match_ocr_row_evidence_insert_owner
on public.match_ocr_row_evidence
for insert
to authenticated
with check (
    exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_ocr_row_evidence.tournament_id
            and match_row.id = match_ocr_row_evidence.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

create policy match_ocr_row_evidence_update_owner
on public.match_ocr_row_evidence
for update
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_ocr_row_evidence.tournament_id
            and match_row.id = match_ocr_row_evidence.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
)
with check (
    exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_ocr_row_evidence.tournament_id
            and match_row.id = match_ocr_row_evidence.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

create policy match_ocr_row_evidence_delete_owner
on public.match_ocr_row_evidence
for delete
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_ocr_row_evidence.tournament_id
            and match_row.id = match_ocr_row_evidence.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

create policy match_ocr_correction_snapshots_select_owner
on public.match_ocr_correction_snapshots
for select
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_ocr_correction_snapshots.tournament_id
            and match_row.id = match_ocr_correction_snapshots.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

create policy match_ocr_correction_snapshots_insert_owner
on public.match_ocr_correction_snapshots
for insert
to authenticated
with check (
    exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_ocr_correction_snapshots.tournament_id
            and match_row.id = match_ocr_correction_snapshots.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

create policy match_ocr_correction_snapshots_update_owner
on public.match_ocr_correction_snapshots
for update
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_ocr_correction_snapshots.tournament_id
            and match_row.id = match_ocr_correction_snapshots.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
)
with check (
    exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_ocr_correction_snapshots.tournament_id
            and match_row.id = match_ocr_correction_snapshots.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

create policy match_ocr_correction_snapshots_delete_owner
on public.match_ocr_correction_snapshots
for delete
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_ocr_correction_snapshots.tournament_id
            and match_row.id = match_ocr_correction_snapshots.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);
