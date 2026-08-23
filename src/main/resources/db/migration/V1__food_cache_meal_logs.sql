-- Portable schema (Postgres + H2 MODE=PostgreSQL)
create table if not exists food_cache (
  id uuid primary key,
  normalized_name text not null unique,
  aliases text not null default '',
  default_serving_qty numeric not null default 1,
  default_serving_unit text not null default 'serving',
  kcal numeric not null,
  protein_g numeric not null default 0,
  carb_g numeric not null default 0,
  fat_g numeric not null default 0,
  source text not null,
  confidence numeric not null default 0,
  updated_at timestamp with time zone not null
);

create index if not exists food_cache_updated_at_idx on food_cache (updated_at);

create table if not exists meal_logs (
  id uuid primary key,
  user_id text not null default 'owner',
  food_cache_id uuid,
  raw_input text not null default '',
  image_url text,
  quantity numeric not null,
  unit text not null,
  kcal numeric not null,
  protein_g numeric not null default 0,
  carb_g numeric not null default 0,
  fat_g numeric not null default 0,
  meal_type text,
  logged_at timestamp with time zone not null,
  created_at timestamp with time zone not null
);

create index if not exists meal_logs_user_logged_at_idx on meal_logs (user_id, logged_at);
