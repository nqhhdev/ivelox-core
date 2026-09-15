create table if not exists orders (
  id uuid primary key,
  customer_name text not null,
  total_amount numeric(19, 2) not null,
  status text not null,
  created_at timestamp with time zone not null,
  constraint orders_status check (status in ('CREATED')),
  constraint orders_amount check (total_amount > 0)
);
