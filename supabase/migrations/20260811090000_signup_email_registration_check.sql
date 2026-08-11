create or replace function public.signup_email_is_registered(p_email text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from auth.users
        where email_confirmed_at is not null
          and pg_catalog.lower(email) = pg_catalog.lower(pg_catalog.btrim(p_email))
    );
$$;

revoke execute on function public.signup_email_is_registered(text)
    from public, anon, authenticated;
grant execute on function public.signup_email_is_registered(text)
    to service_role;
