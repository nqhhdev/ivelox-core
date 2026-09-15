create table if not exists finance_incomes (
  id uuid primary key,
  user_id text not null default 'owner',
  name text not null,
  amount_minor bigint not null,
  currency char(3) not null,
  day_of_month int not null,
  recurrence text not null,
  created_at timestamp with time zone not null,
  constraint finance_incomes_day check (day_of_month between 1 and 28),
  constraint finance_incomes_rec check (recurrence in ('none', 'monthly')),
  constraint finance_incomes_amt check (amount_minor >= 1)
);

create table if not exists finance_saving_goals (
  id uuid primary key,
  user_id text not null default 'owner',
  name text not null,
  target_amount_minor bigint not null,
  monthly_amount_minor bigint not null default 0,
  currency char(3) not null,
  day_of_month int not null,
  created_at timestamp with time zone not null,
  constraint finance_saving_day check (day_of_month between 1 and 28),
  constraint finance_saving_tgt check (target_amount_minor >= 1),
  constraint finance_saving_mo check (monthly_amount_minor >= 0)
);

create table if not exists finance_loans (
  id uuid primary key,
  user_id text not null default 'owner',
  name text not null,
  principal_minor bigint not null,
  monthly_payment_minor bigint not null,
  currency char(3) not null,
  day_of_month int not null,
  note text not null default '',
  created_at timestamp with time zone not null,
  constraint finance_loans_day check (day_of_month between 1 and 28),
  constraint finance_loans_prin check (principal_minor >= 1),
  constraint finance_loans_pay check (monthly_payment_minor >= 1)
);

create table if not exists finance_fixed_expenses (
  id uuid primary key,
  user_id text not null default 'owner',
  name text not null,
  amount_minor bigint not null,
  currency char(3) not null,
  day_of_month int not null,
  created_at timestamp with time zone not null,
  constraint finance_fixed_day check (day_of_month between 1 and 28),
  constraint finance_fixed_amt check (amount_minor >= 1)
);

create table if not exists finance_transactions (
  id uuid primary key,
  user_id text not null default 'owner',
  kind text not null,
  amount_minor bigint not null,
  currency char(3) not null,
  category text,
  note text not null default '',
  occurred_on date not null,
  income_id uuid references finance_incomes (id) on delete set null,
  saving_goal_id uuid references finance_saving_goals (id) on delete set null,
  loan_id uuid references finance_loans (id) on delete set null,
  fixed_expense_id uuid references finance_fixed_expenses (id) on delete set null,
  created_at timestamp with time zone not null,
  constraint finance_tx_kind check (kind in (
    'income', 'daily', 'fixed', 'saving', 'loan_payment', 'loan_disbursement'
  )),
  constraint finance_tx_amt check (amount_minor >= 1)
);

create index if not exists finance_tx_user_day_idx
  on finance_transactions (user_id, occurred_on desc, id desc);
create index if not exists finance_tx_user_ccy_idx
  on finance_transactions (user_id, currency, occurred_on);

create table if not exists finance_settings (
  user_id text primary key,
  home_currency char(3) not null default 'VND',
  digest_hour int not null default 21,
  digest_enabled boolean not null default true,
  over_slot_alert boolean not null default true,
  over_month_alert boolean not null default true,
  constraint finance_settings_hour check (digest_hour between 0 and 23)
);

insert into finance_settings (user_id) values ('owner');

create table if not exists finance_notify_log (
  id uuid primary key,
  user_id text not null default 'owner',
  channel text not null,
  type text not null,
  civil_day date not null,
  currency char(3) not null default 'VND',
  payload_hash text not null default '',
  sent_at timestamp with time zone not null,
  constraint finance_notify_ch check (channel in ('telegram')),
  constraint finance_notify_type check (type in ('digest', 'over_slot', 'over_month'))
);

create unique index if not exists finance_notify_dedup_idx
  on finance_notify_log (user_id, type, civil_day, currency);

create table if not exists finance_due_postings (
  id uuid primary key,
  user_id text not null default 'owner',
  kind text not null,
  source_id uuid not null,
  period char(7) not null,
  created_at timestamp with time zone not null,
  constraint finance_due_postings_kind check (kind in (
    'income', 'fixed', 'saving', 'loan_payment'
  ))
);

create unique index if not exists finance_due_postings_dedup_idx
  on finance_due_postings (user_id, kind, source_id, period);

create table if not exists finance_due_cursor (
  user_id text primary key,
  last_posted_on date
);
