create table public.custom_design_templates (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null
        references auth.users(id) on delete cascade,
    image_path text not null,
    image_sha256 text not null,
    image_byte_size bigint not null,
    image_extension text not null,
    image_mime_type text not null,
    source_width integer not null,
    source_height integer not null,
    labels_json jsonb not null,
    columns_json jsonb not null,
    rows_json jsonb not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint custom_design_templates_image_byte_size_check
        check (image_byte_size > 0),
    constraint custom_design_templates_source_width_check
        check (source_width > 0),
    constraint custom_design_templates_source_height_check
        check (source_height > 0),
    constraint custom_design_templates_image_sha256_check
        check (image_sha256 ~ '^[0-9a-f]{64}$'),
    constraint custom_design_templates_image_extension_check
        check (image_extension in ('png', 'jpg', 'webp')),
    constraint custom_design_templates_image_mime_type_check
        check (
            (image_extension = 'png' and image_mime_type = 'image/png')
            or (image_extension = 'jpg' and image_mime_type = 'image/jpeg')
            or (image_extension = 'webp' and image_mime_type = 'image/webp')
        ),
    constraint custom_design_templates_image_path_check
        check (
            image_path = 'users/' || user_id::text ||
                '/custom-designs/' || id::text || '/original.' || image_extension
        ),
    constraint custom_design_templates_labels_check
        check (
            jsonb_typeof(labels_json) = 'object'
            and labels_json ? 'teamName'
            and labels_json ? 'win'
            and labels_json ? 'totalKills'
            and labels_json ? 'positionPoints'
            and labels_json ? 'totalPoints'
            and (labels_json - 'teamName' - 'win' - 'totalKills' -
                'positionPoints' - 'totalPoints') = '{}'::jsonb
            and jsonb_typeof(labels_json -> 'teamName') = 'string'
            and btrim(labels_json ->> 'teamName') <> ''
            and jsonb_typeof(labels_json -> 'win') = 'string'
            and btrim(labels_json ->> 'win') <> ''
            and jsonb_typeof(labels_json -> 'totalKills') = 'string'
            and btrim(labels_json ->> 'totalKills') <> ''
            and jsonb_typeof(labels_json -> 'positionPoints') = 'string'
            and btrim(labels_json ->> 'positionPoints') <> ''
            and jsonb_typeof(labels_json -> 'totalPoints') = 'string'
            and btrim(labels_json ->> 'totalPoints') <> ''
        ),
    constraint custom_design_templates_columns_check
        check (
            jsonb_typeof(columns_json) = 'object'
            and columns_json ? 'TEAM_NAME'
            and columns_json ? 'WIN'
            and columns_json ? 'TOTAL_KILLS'
            and columns_json ? 'POSITION_POINTS'
            and columns_json ? 'TOTAL_POINTS'
            and (columns_json - 'TEAM_NAME' - 'WIN' - 'TOTAL_KILLS' -
                'POSITION_POINTS' - 'TOTAL_POINTS') = '{}'::jsonb
            and case
                when jsonb_typeof(columns_json -> 'TEAM_NAME') = 'number'
                    and jsonb_typeof(columns_json -> 'WIN') = 'number'
                    and jsonb_typeof(columns_json -> 'TOTAL_KILLS') = 'number'
                    and jsonb_typeof(columns_json -> 'POSITION_POINTS') = 'number'
                    and jsonb_typeof(columns_json -> 'TOTAL_POINTS') = 'number'
                then
                    (columns_json ->> 'TEAM_NAME')::numeric between 0 and source_width
                    and (columns_json ->> 'WIN')::numeric between 0 and source_width
                    and (columns_json ->> 'TOTAL_KILLS')::numeric between 0 and source_width
                    and (columns_json ->> 'POSITION_POINTS')::numeric between 0 and source_width
                    and (columns_json ->> 'TOTAL_POINTS')::numeric between 0 and source_width
                else false
            end
        ),
    constraint custom_design_templates_rows_check
        check (
            jsonb_typeof(rows_json) = 'object'
            and rows_json ? '1'
            and rows_json ? '2'
            and rows_json ? '3'
            and rows_json ? '4'
            and rows_json ? '5'
            and rows_json ? '6'
            and rows_json ? '7'
            and rows_json ? '8'
            and rows_json ? '9'
            and rows_json ? '10'
            and rows_json ? '11'
            and rows_json ? '12'
            and (rows_json - '1' - '2' - '3' - '4' - '5' - '6' -
                '7' - '8' - '9' - '10' - '11' - '12') = '{}'::jsonb
            and case
                when jsonb_typeof(rows_json -> '1') = 'number'
                    and jsonb_typeof(rows_json -> '2') = 'number'
                    and jsonb_typeof(rows_json -> '3') = 'number'
                    and jsonb_typeof(rows_json -> '4') = 'number'
                    and jsonb_typeof(rows_json -> '5') = 'number'
                    and jsonb_typeof(rows_json -> '6') = 'number'
                    and jsonb_typeof(rows_json -> '7') = 'number'
                    and jsonb_typeof(rows_json -> '8') = 'number'
                    and jsonb_typeof(rows_json -> '9') = 'number'
                    and jsonb_typeof(rows_json -> '10') = 'number'
                    and jsonb_typeof(rows_json -> '11') = 'number'
                    and jsonb_typeof(rows_json -> '12') = 'number'
                then
                    (rows_json ->> '1')::numeric between 0 and source_height
                    and (rows_json ->> '2')::numeric between 0 and source_height
                    and (rows_json ->> '3')::numeric between 0 and source_height
                    and (rows_json ->> '4')::numeric between 0 and source_height
                    and (rows_json ->> '5')::numeric between 0 and source_height
                    and (rows_json ->> '6')::numeric between 0 and source_height
                    and (rows_json ->> '7')::numeric between 0 and source_height
                    and (rows_json ->> '8')::numeric between 0 and source_height
                    and (rows_json ->> '9')::numeric between 0 and source_height
                    and (rows_json ->> '10')::numeric between 0 and source_height
                    and (rows_json ->> '11')::numeric between 0 and source_height
                    and (rows_json ->> '12')::numeric between 0 and source_height
                    and (rows_json ->> '1')::numeric < (rows_json ->> '2')::numeric
                    and (rows_json ->> '2')::numeric < (rows_json ->> '3')::numeric
                    and (rows_json ->> '3')::numeric < (rows_json ->> '4')::numeric
                    and (rows_json ->> '4')::numeric < (rows_json ->> '5')::numeric
                    and (rows_json ->> '5')::numeric < (rows_json ->> '6')::numeric
                    and (rows_json ->> '6')::numeric < (rows_json ->> '7')::numeric
                    and (rows_json ->> '7')::numeric < (rows_json ->> '8')::numeric
                    and (rows_json ->> '8')::numeric < (rows_json ->> '9')::numeric
                    and (rows_json ->> '9')::numeric < (rows_json ->> '10')::numeric
                    and (rows_json ->> '10')::numeric < (rows_json ->> '11')::numeric
                    and (rows_json ->> '11')::numeric < (rows_json ->> '12')::numeric
                else false
            end
        )
);

create index custom_design_templates_user_id_idx
on public.custom_design_templates (user_id);

create index custom_design_templates_created_at_idx
on public.custom_design_templates (created_at);

alter table public.custom_design_templates enable row level security;

revoke all on table public.custom_design_templates from public, anon, authenticated;
grant select, insert, delete
on table public.custom_design_templates
to authenticated;

create policy custom_design_templates_select_owner
on public.custom_design_templates
for select
to authenticated
using (user_id = (select auth.uid()));

create policy custom_design_templates_insert_owner
on public.custom_design_templates
for insert
to authenticated
with check (user_id = (select auth.uid()));

create policy custom_design_templates_delete_owner
on public.custom_design_templates
for delete
to authenticated
using (user_id = (select auth.uid()));

insert into storage.buckets (id, name, public, allowed_mime_types)
values (
    'custom-designs',
    'custom-designs',
    false,
    array['image/png', 'image/jpeg', 'image/webp']::text[]
)
on conflict (id) do update
set name = excluded.name,
    public = false,
    allowed_mime_types = excluded.allowed_mime_types;

create policy custom_designs_insert_owner
on storage.objects
for insert
to authenticated
with check (
    bucket_id = 'custom-designs'
    and cardinality(storage.foldername(storage.objects.name)) = 4
    and (storage.foldername(storage.objects.name))[1] = 'users'
    and (storage.foldername(storage.objects.name))[2] = (select auth.uid()::text)
    and (storage.foldername(storage.objects.name))[3] = 'custom-designs'
    and (storage.foldername(storage.objects.name))[4] ~
        '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    and storage.filename(storage.objects.name) in
        ('original.png', 'original.jpg', 'original.webp')
    and (owner_id is null or owner_id = (select auth.uid()::text))
);

create policy custom_designs_select_owner
on storage.objects
for select
to authenticated
using (
    bucket_id = 'custom-designs'
    and cardinality(storage.foldername(storage.objects.name)) = 4
    and (storage.foldername(storage.objects.name))[1] = 'users'
    and (storage.foldername(storage.objects.name))[2] = (select auth.uid()::text)
    and (storage.foldername(storage.objects.name))[3] = 'custom-designs'
    and (storage.foldername(storage.objects.name))[4] ~
        '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    and storage.filename(storage.objects.name) in
        ('original.png', 'original.jpg', 'original.webp')
    and (owner_id is null or owner_id = (select auth.uid()::text))
);

create policy custom_designs_delete_owner
on storage.objects
for delete
to authenticated
using (
    bucket_id = 'custom-designs'
    and cardinality(storage.foldername(storage.objects.name)) = 4
    and (storage.foldername(storage.objects.name))[1] = 'users'
    and (storage.foldername(storage.objects.name))[2] = (select auth.uid()::text)
    and (storage.foldername(storage.objects.name))[3] = 'custom-designs'
    and (storage.foldername(storage.objects.name))[4] ~
        '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
    and storage.filename(storage.objects.name) in
        ('original.png', 'original.jpg', 'original.webp')
    and (owner_id is null or owner_id = (select auth.uid()::text))
);
