create table if not exists payment_approval_payments (
  id uuid primary key,
  user_id text not null default 'owner',
  recipient_name text not null,
  amount_minor bigint not null,
  currency char(3) not null default 'AED',
  status text not null,
  reference text not null,
  note text not null default '',
  created_at timestamp with time zone not null,
  decided_at timestamp with time zone,
  constraint payment_approval_amount check (amount_minor >= 1),
  constraint payment_approval_currency check (currency = 'AED'),
  constraint payment_approval_status check (status in ('PENDING', 'APPROVED', 'REJECTED'))
);

create unique index if not exists payment_approval_reference_idx
  on payment_approval_payments (reference);

create index if not exists payment_approval_user_status_created_idx
  on payment_approval_payments (user_id, status, created_at desc, id desc);

create index if not exists payment_approval_user_created_idx
  on payment_approval_payments (user_id, created_at desc, id desc);
