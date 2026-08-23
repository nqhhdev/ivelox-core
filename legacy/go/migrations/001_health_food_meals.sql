-- migrations/001_health_food_meals.sql
create extension if not exists "pgcrypto";

create table if not exists public.food_cache (
  id uuid primary key default gen_random_uuid(),
  normalized_name text not null unique,
  aliases text[] not null default '{}',
  default_serving_qty numeric not null default 1,
  default_serving_unit text not null default 'serving'
    check (default_serving_unit in ('g', 'ml', 'serving', 'piece')),
  kcal numeric not null check (kcal >= 0),
  protein_g numeric not null default 0 check (protein_g >= 0),
  carb_g numeric not null default 0 check (carb_g >= 0),
  fat_g numeric not null default 0 check (fat_g >= 0),
  source text not null check (source in ('ai', 'manual')),
  confidence numeric not null default 0 check (confidence >= 0 and confidence <= 1),
  updated_at timestamptz not null default now()
);

create index if not exists food_cache_updated_at_idx on public.food_cache (updated_at);

create table if not exists public.meal_logs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  food_cache_id uuid references public.food_cache (id) on delete set null,
  raw_input text not null default '',
  image_url text,
  quantity numeric not null check (quantity > 0),
  unit text not null check (unit in ('g', 'ml', 'serving', 'piece')),
  kcal numeric not null check (kcal >= 0),
  protein_g numeric not null default 0 check (protein_g >= 0),
  carb_g numeric not null default 0 check (carb_g >= 0),
  fat_g numeric not null default 0 check (fat_g >= 0),
  meal_type text check (meal_type is null or meal_type in ('breakfast', 'lunch', 'dinner', 'snack')),
  logged_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

create index if not exists meal_logs_user_logged_at_idx
  on public.meal_logs (user_id, logged_at desc);

-- RLS: no policies = deny for roles subject to RLS.
-- postgres / service_role (BE pooler) bypass RLS.
alter table public.food_cache enable row level security;
alter table public.meal_logs enable row level security;
revoke all on table public.food_cache from anon, authenticated;
revoke all on table public.meal_logs from anon, authenticated;
