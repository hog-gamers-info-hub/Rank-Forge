-- v0.7.6 private, owner-scoped screenshot metadata.
create table public.match_screenshot_metadata (
    match_id uuid primary key references public.matches(id) on delete cascade,
    owner_id uuid not null,
    tournament_id uuid not null references public.tournaments(id) on delete cascade,
    local_file_extension text not null,
    mime_type text not null,
    width integer not null,
    height integer not null,
    byte_size bigint not null,
    sha256 text not null,
    storage_bucket text,
    storage_object_path text,
    local_status text not null,
    upload_status text not null,
    upload_failure_code text,
    preserved_at timestamptz not null,
    uploaded_at timestamptz,
    revision bigint not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint match_screenshot_metadata_width_positive check (width > 0),
    constraint match_screenshot_metadata_height_positive check (height > 0),
    constraint match_screenshot_metadata_byte_size_positive check (byte_size > 0),
    constraint match_screenshot_metadata_sha256_check check (sha256 ~ '^[0-9a-f]{64}$'),
    constraint match_screenshot_metadata_local_status_check
        check (local_status in ('PRESERVED', 'MISSING', 'CLEANUP_FAILED')),
    constraint match_screenshot_metadata_upload_status_check
        check (upload_status in ('PENDING', 'UPLOADED', 'FAILED')),
    constraint match_screenshot_metadata_revision_positive check (revision > 0),
    constraint match_screenshot_metadata_bucket_check
        check (storage_bucket is null or storage_bucket = 'match-screenshots'),
    constraint match_screenshot_metadata_uploaded_fields_check
        check (
            upload_status <> 'UPLOADED'
            or (
                storage_bucket = 'match-screenshots'
                and storage_object_path is not null
                and uploaded_at is not null
            )
        ),
    constraint match_screenshot_metadata_storage_path_check
        check (
            storage_object_path is null
            or storage_object_path = (
                'users/' || owner_id::text ||
                '/tournaments/' || tournament_id::text ||
                '/matches/' || match_id::text ||
                '/original.' || local_file_extension
            )
        ),
    constraint match_screenshot_metadata_extension_check
        check (local_file_extension in ('png', 'jpg', 'webp')),
    constraint match_screenshot_metadata_mime_type_check
        check (
            (local_file_extension = 'png' and mime_type = 'image/png')
            or (local_file_extension = 'jpg' and mime_type = 'image/jpeg')
            or (local_file_extension = 'webp' and mime_type = 'image/webp')
        )
);

create index match_screenshot_metadata_owner_id_idx
on public.match_screenshot_metadata (owner_id);

create index match_screenshot_metadata_tournament_id_idx
on public.match_screenshot_metadata (tournament_id);

create index match_screenshot_metadata_sha256_idx
on public.match_screenshot_metadata (sha256);

create index match_screenshot_metadata_upload_status_idx
on public.match_screenshot_metadata (upload_status);

create index match_screenshot_metadata_updated_at_idx
on public.match_screenshot_metadata (updated_at);

alter table public.match_screenshot_metadata enable row level security;

grant select, insert, update, delete on public.match_screenshot_metadata to authenticated;

create policy match_screenshot_metadata_select_owner
on public.match_screenshot_metadata
for select
to authenticated
using (
    owner_id = (select auth.uid())
    and exists (
        select 1
        from public.tournaments tournament_row
        join public.matches match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_screenshot_metadata.tournament_id
            and match_row.id = match_screenshot_metadata.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

create policy match_screenshot_metadata_insert_owner
on public.match_screenshot_metadata
for insert
to authenticated
with check (
    owner_id = (select auth.uid())
    and exists (
        select 1
        from public.tournaments tournament_row
        join public.matches match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_screenshot_metadata.tournament_id
            and match_row.id = match_screenshot_metadata.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

create policy match_screenshot_metadata_update_owner
on public.match_screenshot_metadata
for update
to authenticated
using (
    owner_id = (select auth.uid())
    and exists (
        select 1
        from public.tournaments tournament_row
        join public.matches match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_screenshot_metadata.tournament_id
            and match_row.id = match_screenshot_metadata.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
)
with check (
    owner_id = (select auth.uid())
    and exists (
        select 1
        from public.tournaments tournament_row
        join public.matches match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_screenshot_metadata.tournament_id
            and match_row.id = match_screenshot_metadata.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

create policy match_screenshot_metadata_delete_owner
on public.match_screenshot_metadata
for delete
to authenticated
using (
    owner_id = (select auth.uid())
    and exists (
        select 1
        from public.tournaments tournament_row
        join public.matches match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_screenshot_metadata.tournament_id
            and match_row.id = match_screenshot_metadata.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);
