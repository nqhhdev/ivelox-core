-- Health board: macros on goals, slot states, daily weight, day closings, meal images
-- Avoid reserved word "day" (H2); use log_day
alter table health_goals add column if not exists protein_g_target int;
alter table health_goals add column if not exists carb_g_target int;
alter table health_goals add column if not exists fat_g_target int;
alter table health_goals add column if not exists meal_types_json text;

create table if not exists meal_slot_states (
  user_id text not null,
  log_day date not null,
  meal_type text not null,
  status text not null default 'planned',
  base_kcal int,
  adjusted_kcal int,
  updated_at timestamp with time zone not null,
  primary key (user_id, log_day, meal_type)
);

create table if not exists daily_weight_logs (
  user_id text not null,
  log_day date not null,
  weight_kg numeric not null,
  recorded_at timestamp with time zone not null,
  primary key (user_id, log_day)
);

create table if not exists day_closings (
  user_id text not null,
  log_day date not null,
  eaten_kcal numeric not null default 0,
  burned_kcal numeric not null default 0,
  net_kcal numeric not null default 0,
  protein_g numeric not null default 0,
  carb_g numeric not null default 0,
  fat_g numeric not null default 0,
  kcal_target int,
  protein_g_target int,
  carb_g_target int,
  fat_g_target int,
  tips_json text,
  closed_at timestamp with time zone not null,
  primary key (user_id, log_day)
);

create table if not exists meal_images (
  meal_log_id uuid primary key,
  user_id text not null,
  mime text not null,
  bytes bytea not null,
  created_at timestamp with time zone not null
);

create index if not exists meal_images_user_idx on meal_images (user_id);
