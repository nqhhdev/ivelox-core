create index if not exists payment_approval_user_status_decided_idx
  on payment_approval_payments (user_id, status, decided_at desc, id desc);
