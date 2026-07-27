create extension if not exists pgcrypto;

create table public.tournaments (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id),
    name text not null,
    tournament_date date,
    organizer_name text,
    organizer_contact text,
    status text not null default 'draft',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint tournaments_status_check
        check (status in ('draft', 'active', 'completed', 'archived'))
);

create table public.tournament_team_slots (
    id uuid primary key default gen_random_uuid(),
    tournament_id uuid not null references public.tournaments(id) on delete cascade,
    slot_number integer not null,
    team_name text,
    status text not null default 'draft',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint tournament_team_slots_slot_number_check
        check (slot_number between 1 and 12),
    constraint tournament_team_slots_status_check
        check (status in ('draft', 'complete'))
);

create table public.players (
    id uuid primary key default gen_random_uuid(),
    team_slot_id uuid not null references public.tournament_team_slots(id) on delete cascade,
    display_name text not null,
    normalized_name text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.matches (
    id uuid primary key default gen_random_uuid(),
    tournament_id uuid not null references public.tournaments(id) on delete cascade,
    match_number integer not null,
    match_date date,
    map_name text,
    status text not null default 'draft',
    finalized_at timestamptz,
    finalized_by uuid references auth.users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint matches_match_number_check
        check (match_number between 1 and 10),
    constraint matches_status_check
        check (status in ('draft', 'finalized'))
);

create table public.match_results (
    id uuid primary key default gen_random_uuid(),
    match_id uuid not null references public.matches(id) on delete cascade,
    team_slot_id uuid not null references public.tournament_team_slots(id),
    placement integer,
    kills integer not null default 0,
    source text not null default 'manual',
    review_status text not null default 'draft',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint match_results_placement_check
        check (placement is null or placement between 1 and 12),
    constraint match_results_kills_check
        check (kills >= 0),
    constraint match_results_source_check
        check (source in ('manual', 'ocr_assisted')),
    constraint match_results_review_status_check
        check (review_status in ('draft', 'confirmed'))
);

alter table public.tournaments enable row level security;
alter table public.tournament_team_slots enable row level security;
alter table public.players enable row level security;
alter table public.matches enable row level security;
alter table public.match_results enable row level security;
