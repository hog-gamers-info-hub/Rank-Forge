drop policy if exists matches_delete_owner on public.matches;
create policy matches_delete_owner
on public.matches
for delete
to authenticated
using (
    exists (
        select 1
        from public.tournaments as tournament_row
        where tournament_row.id = public.matches.tournament_id
            and tournament_row.owner_id = (select auth.uid())
    )
);

drop policy if exists match_screenshots_delete_owner on storage.objects;
create policy match_screenshots_delete_owner
on storage.objects
for delete
to authenticated
using (
    bucket_id = 'match-screenshots'
    and (storage.foldername(storage.objects.name))[1] = 'users'
    and (storage.foldername(storage.objects.name))[2] = (select auth.uid()::text)
    and (storage.foldername(storage.objects.name))[3] = 'tournaments'
    and (storage.foldername(storage.objects.name))[5] = 'matches'
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

drop policy if exists ocr_screenshots_delete_owner on storage.objects;
create policy ocr_screenshots_delete_owner
on storage.objects
for delete
to authenticated
using (
    bucket_id = 'ocr-screenshots'
    and array_length(storage.foldername(storage.objects.name), 1) = 8
    and (storage.foldername(storage.objects.name))[1] = 'users'
    and (storage.foldername(storage.objects.name))[2] = (select auth.uid()::text)
    and (storage.foldername(storage.objects.name))[3] = 'tournaments'
    and (storage.foldername(storage.objects.name))[5] = 'matches'
    and (storage.foldername(storage.objects.name))[7] = 'result'
    and (storage.foldername(storage.objects.name))[8] in ('upper', 'lower')
    and lower(storage.filename(storage.objects.name)) in ('original.png', 'original.jpg', 'original.webp')
    and (owner_id is null or owner_id = (select auth.uid()::text))
    and exists (
        select 1
        from public.tournaments as tournament_row
        join public.matches as match_row on match_row.tournament_id = tournament_row.id
        where tournament_row.id::text = (storage.foldername(storage.objects.name))[4]
            and match_row.id::text = (storage.foldername(storage.objects.name))[6]
            and tournament_row.owner_id = (select auth.uid())
    )
);

drop policy if exists ocr_screenshots_lobby_delete_owner on storage.objects;
create policy ocr_screenshots_lobby_delete_owner
on storage.objects
for delete
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
