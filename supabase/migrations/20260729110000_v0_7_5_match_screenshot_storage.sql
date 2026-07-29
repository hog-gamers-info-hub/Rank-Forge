-- v0.7.5 private, owner-scoped screenshot storage.
insert into storage.buckets (id, name, public, allowed_mime_types)
values (
    'match-screenshots',
    'match-screenshots',
    false,
    array['image/png', 'image/jpeg', 'image/webp']::text[]
)
on conflict (id) do update
set public = false,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists match_screenshots_insert_owner on storage.objects;
create policy match_screenshots_insert_owner
on storage.objects
for insert
to authenticated
with check (
    bucket_id = 'match-screenshots'
    and (storage.foldername(name))[1] = 'users'
    and (storage.foldername(name))[2] = (select auth.uid()::text)
    and (storage.foldername(name))[3] = 'tournaments'
    and (storage.foldername(name))[5] = 'matches'
    and lower((storage.filename(name))) in ('original.png', 'original.jpg', 'original.webp')
    and (owner_id is null or owner_id = (select auth.uid()::text))
    and exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.owner_id = (select auth.uid())
            and tournament_row.id::text = (storage.foldername(name))[4]
            and match_row.id::text = (storage.foldername(name))[6]
    )
);

drop policy if exists match_screenshots_select_owner on storage.objects;
create policy match_screenshots_select_owner
on storage.objects
for select
to authenticated
using (
    bucket_id = 'match-screenshots'
    and (storage.foldername(name))[1] = 'users'
    and (storage.foldername(name))[2] = (select auth.uid()::text)
    and (storage.foldername(name))[3] = 'tournaments'
    and (storage.foldername(name))[5] = 'matches'
    and lower((storage.filename(name))) in ('original.png', 'original.jpg', 'original.webp')
    and (owner_id is null or owner_id = (select auth.uid()::text))
    and exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.owner_id = (select auth.uid())
            and tournament_row.id::text = (storage.foldername(name))[4]
            and match_row.id::text = (storage.foldername(name))[6]
    )
);

drop policy if exists match_screenshots_update_owner on storage.objects;
create policy match_screenshots_update_owner
on storage.objects
for update
to authenticated
using (
    bucket_id = 'match-screenshots'
    and (storage.foldername(name))[1] = 'users'
    and (storage.foldername(name))[2] = (select auth.uid()::text)
    and (storage.foldername(name))[3] = 'tournaments'
    and (storage.foldername(name))[5] = 'matches'
    and lower((storage.filename(name))) in ('original.png', 'original.jpg', 'original.webp')
    and (owner_id is null or owner_id = (select auth.uid()::text))
    and exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.owner_id = (select auth.uid())
            and tournament_row.id::text = (storage.foldername(name))[4]
            and match_row.id::text = (storage.foldername(name))[6]
    )
)
with check (
    bucket_id = 'match-screenshots'
    and (storage.foldername(name))[1] = 'users'
    and (storage.foldername(name))[2] = (select auth.uid()::text)
    and (storage.foldername(name))[3] = 'tournaments'
    and (storage.foldername(name))[5] = 'matches'
    and lower((storage.filename(name))) in ('original.png', 'original.jpg', 'original.webp')
    and (owner_id is null or owner_id = (select auth.uid()::text))
    and exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.owner_id = (select auth.uid())
            and tournament_row.id::text = (storage.foldername(name))[4]
            and match_row.id::text = (storage.foldername(name))[6]
    )
);
