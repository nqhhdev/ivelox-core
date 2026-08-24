-- P2–P4: body, burns, goals, snapshots
create table if not exists body_metrics (
  id uuid primary key,
  user_id text not null,
  height_cm numeric not null,
  weight_kg numeric not null,
  bmi numeric not null,
  recorded_at timestamp with time zone not null,
  created_at timestamp with time zone not null
);

create index if not exists body_metrics_user_recorded_idx
  on body_metrics (user_id, recorded_at desc);

create table if not exists burn_logs (
  id uuid primary key,
  user_id text not null,
  activity_name text not null,
  duration_min int not null,
  kcal_burned numeric not null,
  source text not null,
  logged_at timestamp with time zone not null,
  created_at timestamp with time zone not null
);

create index if not exists burn_logs_user_logged_idx
  on burn_logs (user_id, logged_at desc);

create table if not exists health_goals (
  user_id text primary key,
  height_cm numeric,
  weight_kg numeric,
  sex text,
  age_years int,
  activity_level text,
  weight_change_pct numeric,
  weeks int,
  target_weight_kg numeric,
  daily_kcal_target int,
  daily_burn_target int,
  start_at date,
  target_at date,
  meal_plan_json text,
  updated_at timestamp with time zone not null
);

create table if not exists health_snapshots (
  user_id text not null,
  snapshot_day date not null,
  eaten_kcal numeric not null default 0,
  burned_kcal numeric not null default 0,
  net_kcal numeric not null default 0,
  ai_score int,
  ai_tips text,
  computed_at timestamp with time zone not null,
  primary key (user_id, snapshot_day)
);
