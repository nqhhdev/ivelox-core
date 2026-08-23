-- migrations/002_health_rls.sql
-- Additive for installs that already applied 001. Service/pooler roles
-- (postgres / service_role) bypass RLS; anon/authenticated are denied.

alter table public.food_cache enable row level security;
alter table public.meal_logs enable row level security;

revoke all on table public.food_cache from anon, authenticated;
revoke all on table public.meal_logs from anon, authenticated;

do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'meal_logs_protein_g_nonneg') then
    alter table public.meal_logs add constraint meal_logs_protein_g_nonneg check (protein_g >= 0);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'meal_logs_carb_g_nonneg') then
    alter table public.meal_logs add constraint meal_logs_carb_g_nonneg check (carb_g >= 0);
  end if;
  if not exists (select 1 from pg_constraint where conname = 'meal_logs_fat_g_nonneg') then
    alter table public.meal_logs add constraint meal_logs_fat_g_nonneg check (fat_g >= 0);
  end if;
end $$;
