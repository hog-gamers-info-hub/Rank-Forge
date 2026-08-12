-- CR-003 Slice 3: private cloud metadata and Storage authorization for Lobby screenshots.

create table public.match_lobby_screenshot_assets (
    owner_id uuid not null,
    tournament_id uuid not null references public.tournaments(id) on delete cascade,
    match_id uuid not null references public.matches(id) on delete cascade,
    lobby_screenshot_index integer not null,
    local_file_extension text not null,
    mime_type text not null,
    original_width integer not null,
    original_height integer not null,
    byte_size bigint not null,
    sha256 text not null,
    storage_bucket text,
    storage_object_path text,
    local_status text not null,
    upload_status text not null,
    upload_failure_code text,
    crop_profile_id text,
    crop_left double precision,
    crop_top double precision,
    crop_right double precision,
    crop_bottom double precision,
    preserved_at timestamptz not null,
    uploaded_at timestamptz,
    revision bigint not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (match_id, lobby_screenshot_index),
    constraint match_lobby_screenshot_assets_index_check
        check (lobby_screenshot_index between 1 and 3),
    constraint match_lobby_screenshot_assets_original_width_positive
        check (original_width > 0),
    constraint match_lobby_screenshot_assets_original_height_positive
        check (original_height > 0),
    constraint match_lobby_screenshot_assets_byte_size_positive
        check (byte_size > 0),
    constraint match_lobby_screenshot_assets_revision_positive
        check (revision > 0),
    constraint match_lobby_screenshot_assets_sha256_check
        check (sha256 ~ '^[0-9a-f]{64}$'),
    constraint match_lobby_screenshot_assets_local_status_check
        check (local_status in ('PRESERVED', 'MISSING', 'CLEANUP_FAILED')),
    constraint match_lobby_screenshot_assets_upload_status_check
        check (upload_status in ('PENDING', 'UPLOADED', 'FAILED')),
    constraint match_lobby_screenshot_assets_extension_check
        check (local_file_extension in ('png', 'jpg', 'webp')),
    constraint match_lobby_screenshot_assets_mime_type_check
        check (
            (local_file_extension = 'png' and mime_type = 'image/png')
            or (local_file_extension = 'jpg' and mime_type = 'image/jpeg')
            or (local_file_extension = 'webp' and mime_type = 'image/webp')
        ),
    constraint match_lobby_screenshot_assets_bucket_check
        check (storage_bucket is null or storage_bucket = 'ocr-screenshots'),
    constraint match_lobby_screenshot_assets_uploaded_fields_check
        check (
            upload_status <> 'UPLOADED'
            or (
                storage_bucket = 'ocr-screenshots'
                and storage_object_path is not null
                and uploaded_at is not null
            )
        ),
    constraint match_lobby_screenshot_assets_crop_all_or_none_check
        check (
            (
                crop_profile_id is null
                and crop_left is null
                and crop_top is null
                and crop_right is null
                and crop_bottom is null
            )
            or (
                crop_profile_id is not null
                and crop_left is not null
                and crop_top is not null
                and crop_right is not null
                and crop_bottom is not null
            )
        ),
    constraint match_lobby_screenshot_assets_crop_bounds_check
        check (
            crop_profile_id is null
            or (
                crop_left between 0 and 1
                and crop_top between 0 and 1
                and crop_right between 0 and 1
                and crop_bottom between 0 and 1
                and crop_left < crop_right
                and crop_top < crop_bottom
            )
        ),
    constraint match_lobby_screenshot_assets_storage_path_check
        check (
            storage_object_path is null
            or storage_object_path = (
                'users/' || owner_id::text ||
                '/tournaments/' || tournament_id::text ||
                '/matches/' || match_id::text ||
                '/lobby/' || lobby_screenshot_index::text ||
                '/original.' || local_file_extension
            )
        )
);

create index match_lobby_screenshot_assets_owner_id_idx
on public.match_lobby_screenshot_assets (owner_id);

create index match_lobby_screenshot_assets_tournament_id_idx
on public.match_lobby_screenshot_assets (tournament_id);

create index match_lobby_screenshot_assets_sha256_idx
on public.match_lobby_screenshot_assets (sha256);

create index match_lobby_screenshot_assets_upload_status_idx
on public.match_lobby_screenshot_assets (upload_status);

create index match_lobby_screenshot_assets_updated_at_idx
on public.match_lobby_screenshot_assets (updated_at);

alter table public.match_lobby_screenshot_assets enable row level security;

revoke all on public.match_lobby_screenshot_assets from anon;
grant select, insert, update, delete on public.match_lobby_screenshot_assets to authenticated;

create policy match_lobby_screenshot_assets_select_owner
on public.match_lobby_screenshot_assets
for select
to authenticated
using (
    owner_id = (select auth.uid())
    and exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_lobby_screenshot_assets.tournament_id
            and match_row.id = match_lobby_screenshot_assets.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

create policy match_lobby_screenshot_assets_insert_owner
on public.match_lobby_screenshot_assets
for insert
to authenticated
with check (
    owner_id = (select auth.uid())
    and exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_lobby_screenshot_assets.tournament_id
            and match_row.id = match_lobby_screenshot_assets.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

create policy match_lobby_screenshot_assets_update_owner
on public.match_lobby_screenshot_assets
for update
to authenticated
using (
    owner_id = (select auth.uid())
    and exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_lobby_screenshot_assets.tournament_id
            and match_row.id = match_lobby_screenshot_assets.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
)
with check (
    owner_id = (select auth.uid())
    and exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_lobby_screenshot_assets.tournament_id
            and match_row.id = match_lobby_screenshot_assets.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

create policy match_lobby_screenshot_assets_delete_owner
on public.match_lobby_screenshot_assets
for delete
to authenticated
using (
    owner_id = (select auth.uid())
    and exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id = match_lobby_screenshot_assets.tournament_id
            and match_row.id = match_lobby_screenshot_assets.match_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

create policy ocr_screenshots_lobby_insert_owner
on storage.objects
for insert
to authenticated
with check (
    bucket_id = 'ocr-screenshots'
    and array_length(storage.foldername(storage.objects.name), 1) = 8
    and (storage.foldername(storage.objects.name))[1] = 'users'
    and (storage.foldername(storage.objects.name))[2] = (select auth.uid()::text)
    and (storage.foldername(storage.objects.name))[3] = 'tournaments'
    and (storage.foldername(storage.objects.name))[5] = 'matches'
    and (storage.foldername(storage.objects.name))[7] = 'lobby'
    and (storage.foldername(storage.objects.name))[8] in ('1', '2', '3')
    and lower(storage.filename(storage.objects.name)) in ('original.png', 'original.jpg', 'original.webp')
    and (owner_id is null or owner_id = (select auth.uid()::text))
    and exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.owner_id = (select auth.uid())
            and tournament_row.id::text = (storage.foldername(storage.objects.name))[4]
            and match_row.id::text = (storage.foldername(storage.objects.name))[6]
    )
);

create policy ocr_screenshots_lobby_select_owner
on storage.objects
for select
to authenticated
using (
    bucket_id = 'ocr-screenshots'
    and array_length(storage.foldername(storage.objects.name), 1) = 8
    and (storage.foldername(storage.objects.name))[1] = 'users'
    and (storage.foldername(storage.objects.name))[2] = (select auth.uid()::text)
    and (storage.foldername(storage.objects.name))[3] = 'tournaments'
    and (storage.foldername(storage.objects.name))[5] = 'matches'
    and (storage.foldername(storage.objects.name))[7] = 'lobby'
    and (storage.foldername(storage.objects.name))[8] in ('1', '2', '3')
    and lower(storage.filename(storage.objects.name)) in ('original.png', 'original.jpg', 'original.webp')
    and (owner_id is null or owner_id = (select auth.uid()::text))
    and exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.owner_id = (select auth.uid())
            and tournament_row.id::text = (storage.foldername(storage.objects.name))[4]
            and match_row.id::text = (storage.foldername(storage.objects.name))[6]
    )
);

create policy ocr_screenshots_lobby_update_owner
on storage.objects
for update
to authenticated
using (
    bucket_id = 'ocr-screenshots'
    and array_length(storage.foldername(storage.objects.name), 1) = 8
    and (storage.foldername(storage.objects.name))[1] = 'users'
    and (storage.foldername(storage.objects.name))[2] = (select auth.uid()::text)
    and (storage.foldername(storage.objects.name))[3] = 'tournaments'
    and (storage.foldername(storage.objects.name))[5] = 'matches'
    and (storage.foldername(storage.objects.name))[7] = 'lobby'
    and (storage.foldername(storage.objects.name))[8] in ('1', '2', '3')
    and lower(storage.filename(storage.objects.name)) in ('original.png', 'original.jpg', 'original.webp')
    and (owner_id is null or owner_id = (select auth.uid()::text))
    and exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.owner_id = (select auth.uid())
            and tournament_row.id::text = (storage.foldername(storage.objects.name))[4]
            and match_row.id::text = (storage.foldername(storage.objects.name))[6]
    )
)
with check (
    bucket_id = 'ocr-screenshots'
    and array_length(storage.foldername(storage.objects.name), 1) = 8
    and (storage.foldername(storage.objects.name))[1] = 'users'
    and (storage.foldername(storage.objects.name))[2] = (select auth.uid()::text)
    and (storage.foldername(storage.objects.name))[3] = 'tournaments'
    and (storage.foldername(storage.objects.name))[5] = 'matches'
    and (storage.foldername(storage.objects.name))[7] = 'lobby'
    and (storage.foldername(storage.objects.name))[8] in ('1', '2', '3')
    and lower(storage.filename(storage.objects.name)) in ('original.png', 'original.jpg', 'original.webp')
    and (owner_id is null or owner_id = (select auth.uid()::text))
    and exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.owner_id = (select auth.uid())
            and tournament_row.id::text = (storage.foldername(storage.objects.name))[4]
            and match_row.id::text = (storage.foldername(storage.objects.name))[6]
    )
);
