# Finance — Personal spend ledger

**Date:** 2026-09-12  
**Status:** Full spec for review (product direction approved 2026-09-11; multi-currency added 2026-09-12)  
**Author:** nqhhdev  
**Repos:** `ivelox-core` + `ivelox-app`  
**Timezone:** `Asia/Ho_Chi_Minh` (same civil day as Health)  
**Language:** English everywhere — UI copy, Telegram, API, this document.

This document is the review surface. Do not implement until this file is marked **Approved**.

---

## 0. One-screen product

After OTP login, nav has **Health | Money**. Money is a private cashflow book for one owner.

The dashboard answers one question first:

> How much can I still spend today?

Everything else (income, saving, loans, fixed costs, history) exists to make that number honest.

Math runs **per currency**. The hero is always one ISO 4217 code (the selected / home currency). There is **no FX conversion in v1**.

```
pool this month     = income − fixed − saving − loan payments   (same currency only)
today_slot          = (pool − daily spend before today) / days left including today
today_left          = today_slot − daily spend today
```

---

## 1. Locked decisions

| Decision | Value |
|----------|--------|
| Who | Owner-only (`user_id = 'owner'`), same JWT as Health |
| Model | **One ledger**. Saving / loan / fixed are logical buckets, not bank accounts |
| Currency | **Multi-currency, ISO 4217.** Closed list. Amounts stored as integer **minor units**. Dashboard aggregates **one currency at a time**. No live FX in v1 |
| Daily spend | Manual log only |
| Notify v1 | **Telegram only** (existing OTP bot + `TELEGRAM_CHAT_ID`) |
| Notify cadence | 21:00 digest + over-slot (1/day) + over-month (1/month). No per-transaction spam |
| Email | **Out of v1** |
| Post-login home | Still `/health`. Money is a sibling tab |
| UI | Reuse 3dviz cream/green `grg-*` tokens. **English labels**, English JSON |
| Flag | `ivelox.finance-enabled` / `FINANCE_ENABLED` |

---

## 2. Goals / non-goals

**v1 ships**
- Month dashboard + “left today” **for the selected currency**
- Manual daily log (amount + category + note + currency)
- Income / saving goals / loans / fixed expenses CRUD (each row has a currency)
- Auto-post monthly dues on the due day (in that row’s currency)
- Full history with filters (including currency) + delete
- Telegram digest + over-budget alerts **in the home currency**
- Currency picker (home + per-transaction override from the closed list)

**v1 does not ship**
- Bank / cash / e-wallet account balances
- OCR receipts, CSV import, split bills
- Multi-user
- **FX rates, converted totals, “everything in USD” rollups**
- SMTP / email
- Interest compounding, amortization beyond remaining = principal − payments
- Anything on the public portfolio

---

## 3. Architecture

```
Browser (ivelox-app)
  /money            dashboard + embedded daily log + setup sections
  /money/history    full ledger
  /login            unchanged OTP
                    JWT → Spring

ivelox-core
  FinanceController     /api/v1/finance/**
  FinanceCatalog        ISO currency table (code, minor digits, symbol)
  FinanceService        math + CRUD + post (always per currency)
  FinanceDuePoster      00:10 ICT, monthly dues
  FinanceDigestJob      21:00 ICT
  TelegramClient        reuse
  Flyway V4             finance_* tables
```

**Auth:** `/api/v1/finance/**` authenticated (existing JWT filter `anyRequest().authenticated()`). If `finance-enabled=false`, controllers return **404** `{ "error": "finance feature disabled" }` (Health pattern).

**Features payload** (`GET /api/v1/features`, public):

```json
{
  "health": { "enabled": true, "auth_required": true },
  "finance": { "enabled": true, "auth_required": true }
}
```

FE: `usePlatformFeatures()` grows a `finance` field. Hide the Money nav and redirect `/money/*` to `/` when `finance.enabled === false`.

---

## 4. Currency

### 4.1 Closed catalog (v1)

| code | Minor digits | Symbol | Example major `1` |
|------|--------------|--------|-------------------|
| VND | 0 | ₫ | `1` → 1₫ |
| USD | 2 | $ | `1.00` → 100 minor |
| EUR | 2 | € | |
| GBP | 2 | £ | |
| JPY | 0 | ¥ | |
| KRW | 0 | ₩ | |
| SGD | 2 | S$ | |
| AUD | 2 | A$ | |
| CAD | 2 | C$ | |
| CNY | 2 | ¥ | |
| THB | 2 | ฿ | |
| MYR | 2 | RM | |
| IDR | 0 | Rp | |
| PHP | 2 | ₱ | |
| INR | 2 | ₹ | |
| CHF | 2 | CHF | |
| HKD | 2 | HK$ | |
| TWD | 0 | NT$ | |
| NZD | 2 | NZ$ | |

Unknown code → `400 { "error": "invalid_currency" }`. Adding a currency later is a code change (catalog constant), not a DB migration.

### 4.2 Storage vs API

| Layer | Field | Meaning |
|-------|--------|---------|
| DB | `amount_minor bigint` | Integer minor units (cents, dong, yen, …) |
| DB | `currency char(3)` | ISO 4217, always stored next to an amount |
| JSON | `amount` | **Major units as a JSON number** (45.5 USD, 45000 VND) |
| JSON | `currency` | ISO code |

Conversion: `amount_minor = round(amount * 10^minor_digits)` with **exact scale**. Extra fractional digits (USD `45.123`) → `invalid_amount`. VND `45.5` → `invalid_amount`.

JSON responses include both `amount` and `amount_minor` plus `currency` so the FE never guesses scale.

Display: `Intl.NumberFormat('en-US', { style: 'currency', currency })` e.g. `$1,250.50`, `₫1,250,000`.

### 4.3 Home currency vs mixed ledger

- `finance_settings.home_currency` default **`VND`**. Used when the client omits `currency` on POST, and for Telegram / default dashboard.
- Every setup row and every transaction **has its own `currency`**.
- Dashboard `GET /dashboard?currency=USD` (default `home_currency`). **Only rows in that code enter the pool.** A VND salary does not affect the USD “left today”.
- UI: currency select on the dashboard (codes that already have activity, plus home). Quick log defaults to the selected dashboard currency.
- History filter includes `currency`.
- **No conversion.** Two numbers in two currencies are never added.

Changing `home_currency` does not rewrite history.

---

## 5. Domain

### 5.1 Ledger is source of truth

Setup rows describe *what should happen*. **Transactions** are what happened. Balances are **always recomputed** from transactions. No cached `balance` column.

### 5.2 Transaction `kind`

| kind | Cashflow | Written by |
|------|----------|------------|
| `income` | + | income create (one-off) or due poster (monthly) or manual POST |
| `loan_disbursement` | + | loan create with `disburse_now: true` |
| `daily` | − | user log |
| `fixed` | − | due poster or manual POST linked to a fixed expense |
| `saving` | − | due poster or manual contribute |
| `loan_payment` | − | due poster or manual payment |

Signs are implied by kind. The client always sends a **positive** `amount`.

A linked POST (`saving`, `loan_*`, `fixed`) **must use the same currency as the setup row**. Mismatch → `400 { "error": "currency_mismatch" }`.

### 5.3 Setup entities

Each setup row has `currency` (required on create; default home if omitted).

**Income** (`finance_incomes`)  
`name`, `amount`, `currency`, `day_of_month` (1–28), `recurrence`: `none` | `monthly`.  
- `none`: creating the row **immediately** posts one `income` transaction (today, same currency).  
- `monthly`: due poster posts once per calendar month on the due civil day.

**Saving goal** (`finance_saving_goals`)  
`name`, `target_amount`, `monthly_amount` (0 = no auto), `currency`, `day_of_month`.  
Progress = `sum(saving tx for goal)` in that currency (cap the bar at target; do not reject over-contribute).

**Loan** (`finance_loans`)  
`name`, `principal`, `monthly_payment`, `currency`, `day_of_month`, `note` (free text, e.g. “18% APR” — **not computed**).  
Remaining = `max(0, principal − sum(loan_payment))` in that currency.  
Auto payment amount = `min(monthly_payment, remaining)`.  
`disburse_now: true` on create → extra `loan_disbursement` = principal. Default `false`.

**Fixed expense** (`finance_fixed_expenses`)  
`name`, `amount`, `currency`, `day_of_month`. Always monthly.

**Daily** — no setup row; `currency` on the transaction (default home / dashboard selection).

### 5.4 Due day

`day_of_month` is 1–28 so February always works. Day 29–31 is **not allowed in v1**.

### 5.5 Daily categories (closed)

`food` | `coffee` | `transport` | `grocery` | `health` | `fun` | `other`

| id | Label |
|----|--------|
| food | Food |
| coffee | Coffee |
| transport | Transport |
| grocery | Grocery |
| health | Health |
| fun | Fun |
| other | Other |

### 5.6 Amounts

- Reject `amount` that is not strictly positive after conversion to minor units (`amount_minor < 1`)
- `amount_minor` max `10^15 − 1`
- Never persist floating cash; only `bigint` minor units

---

## 6. Dashboard math (normative, per currency `C`)

Inputs: civil month `YYYY-MM`, civil today `D`, currency `C`.  
If viewing a **past** month, `D` = last day of that month. The FE shows the “left today” hero only for the **current** month.

All sums below include **only** transactions with `currency = C`.

```
pool            = Σ income + Σ loan_disbursement − Σ fixed − Σ saving − Σ loan_payment
daily_month     = Σ daily in month
daily_today     = Σ daily with occurred_on = D
daily_before    = daily_month − daily_today
rest            = pool − daily_before
days_incl_today = max(1, last_day(month) − D + 1)
today_slot      = floor(rest / days_incl_today)     // integer minor units
today_left      = today_slot − daily_today
remaining_month = pool − daily_month
```

Worked example (September 2026, today = 12, **C = VND**, minor digits 0 so major = minor):

| kind | amount |
|------|--------|
| income | 20_000_000 |
| fixed | 6_000_000 |
| saving | 2_000_000 |
| loan_payment | 3_000_000 |
| daily before 12 | 1_800_000 |
| daily on 12 | 220_000 |

```
pool              = 9_000_000
rest              = 7_200_000
days_incl_today   = 19
today_slot        = floor(7_200_000 / 19) = 378_947
today_left        = 158_947
remaining_month   = 6_980_000
```

Same month with a `$4.50` USD coffee **does not change** these VND figures.

Negative `today_left` / `remaining_month` is valid. UI: rose (`#9b2c2c` / `#fdecec`), not an error.

Last-month compare: previous civil month, **same `C`**.

---

## 7. SQL (Flyway `V4__finance.sql`)

Portable Postgres + H2 `MODE=PostgreSQL`.

```sql
create table if not exists finance_incomes (
  id uuid primary key,
  user_id text not null default 'owner',
  name text not null,
  amount_minor bigint not null,
  currency char(3) not null,
  day_of_month int not null,
  recurrence text not null,
  created_at timestamptz not null,
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
  created_at timestamptz not null,
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
  created_at timestamptz not null,
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
  created_at timestamptz not null,
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
  created_at timestamptz not null,
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

insert into finance_settings (user_id) values ('owner')
  on conflict (user_id) do nothing;

create table if not exists finance_notify_log (
  id uuid primary key,
  user_id text not null default 'owner',
  channel text not null,
  type text not null,
  civil_day date not null,
  currency char(3) not null default 'VND',
  payload_hash text not null default '',
  sent_at timestamptz not null,
  constraint finance_notify_ch check (channel in ('telegram')),
  constraint finance_notify_type check (type in ('digest', 'over_slot', 'over_month'))
);

create unique index if not exists finance_notify_dedup_idx
  on finance_notify_log (user_id, type, civil_day, currency);
```

Alerts and digest are **per currency** (a USD over-slot does not suppress a VND over-slot the same day).

**Delete setup:** `ON DELETE SET NULL` — history remains, unlinked.

---

## 8. API contracts

All paths prefix `/api/v1/finance`. JSON snake_case. Dates `YYYY-MM-DD`. Month `YYYY-MM`.

Money fields on the wire:

```json
{
  "amount": 45.5,
  "amount_minor": 4550,
  "currency": "USD"
}
```

On **request**, `amount` + `currency` are required (or `amount` + implied home currency). `amount_minor` on request is ignored if `amount` is present.

Errors:

| status | body | when |
|--------|------|------|
| 400 | `{ "error": "invalid_amount" }` | non-positive, extra fraction digits |
| 400 | `{ "error": "invalid_currency" }` | not in catalog |
| 400 | `{ "error": "currency_mismatch" }` | tx currency ≠ linked setup row |
| 400 | `{ "error": "invalid_kind" }` / `invalid_category` / `invalid_day` | enum/range |
| 400 | `{ "error": "missing_link" }` | saving/loan/fixed kind without id |
| 404 | `{ "error": "finance feature disabled" }` | flag off |
| 404 | `{ "error": "not_found" }` | id |
| 401 | `{ "error": "unauthorized" }` | existing JWT |

### 8.1 `GET /currencies`

Public-to-JWT catalog for the picker:

```json
{
  "items": [
    { "code": "VND", "minor_digits": 0, "symbol": "₫", "name": "Vietnamese dong" },
    { "code": "USD", "minor_digits": 2, "symbol": "$", "name": "US dollar" }
  ]
}
```

### 8.2 `GET /dashboard?month=YYYY-MM&currency=USD`

Default month = current civil month. Default `currency` = `home_currency`.

```json
{
  "month": "2026-09",
  "today": "2026-09-12",
  "is_current_month": true,
  "currency": "VND",
  "home_currency": "VND",
  "currencies_in_use": ["VND", "USD"],
  "pool": { "amount": 9000000, "amount_minor": 9000000, "currency": "VND" },
  "income": { "amount": 20000000, "amount_minor": 20000000, "currency": "VND" },
  "fixed": { "amount": 6000000, "amount_minor": 6000000, "currency": "VND" },
  "saving": { "amount": 2000000, "amount_minor": 2000000, "currency": "VND" },
  "loan_payment": { "amount": 3000000, "amount_minor": 3000000, "currency": "VND" },
  "loan_disbursement": { "amount": 0, "amount_minor": 0, "currency": "VND" },
  "daily_month": { "amount": 2020000, "amount_minor": 2020000, "currency": "VND" },
  "daily_today": { "amount": 220000, "amount_minor": 220000, "currency": "VND" },
  "today_slot": { "amount": 378947, "amount_minor": 378947, "currency": "VND" },
  "today_left": { "amount": 158947, "amount_minor": 158947, "currency": "VND" },
  "remaining_month": { "amount": 6980000, "amount_minor": 6980000, "currency": "VND" },
  "last_month_remaining": { "amount": 5100000, "amount_minor": 5100000, "currency": "VND" },
  "savings": [
    {
      "id": "…",
      "name": "Emergency fund",
      "currency": "VND",
      "target": { "amount": 50000000, "amount_minor": 50000000, "currency": "VND" },
      "contributed": { "amount": 8000000, "amount_minor": 8000000, "currency": "VND" },
      "monthly": { "amount": 2000000, "amount_minor": 2000000, "currency": "VND" },
      "day_of_month": 5
    }
  ],
  "loans": [
    {
      "id": "…",
      "name": "Bike installment",
      "currency": "VND",
      "principal": { "amount": 40000000, "amount_minor": 40000000, "currency": "VND" },
      "remaining": { "amount": 28000000, "amount_minor": 28000000, "currency": "VND" },
      "monthly_payment": { "amount": 3000000, "amount_minor": 3000000, "currency": "VND" },
      "day_of_month": 10
    }
  ],
  "upcoming": [
    {
      "kind": "fixed",
      "name": "Electricity",
      "due_on": "2026-09-15",
      "amount": { "amount": 800000, "amount_minor": 800000, "currency": "VND" }
    }
  ]
}
```

`savings` / `loans` / `upcoming` on the dashboard are **filtered to `C`**. Other-currency goals appear when the picker switches.

`currencies_in_use`: distinct codes from tx + setup + home.

### 8.3 Transactions

**`POST /transactions`**

```json
{
  "kind": "daily",
  "amount": 4.5,
  "currency": "USD",
  "category": "coffee",
  "note": "coffee",
  "occurred_on": "2026-09-12"
}
```

Rules:

| kind | required extra |
|------|----------------|
| `daily` | `category` in closed list |
| `saving` | `saving_goal_id` (currency must match goal) |
| `loan_payment` / `loan_disbursement` | `loan_id` |
| `fixed` | `fixed_expense_id` |
| `income` | `income_id` optional |

Omitted `currency` → `home_currency`. `occurred_on` default = civil today.

Over-slot / over-month alerts fire for **that transaction’s currency** only.

**`GET /transactions?from=&to=&kind=&category=&currency=&limit=&cursor=`**

Defaults: current month through today, `limit` 50 (max 200). `currency` optional (all codes if omitted).

```json
{
  "items": [
    {
      "id": "…",
      "kind": "daily",
      "amount": 4.5,
      "amount_minor": 450,
      "currency": "USD",
      "category": "coffee",
      "note": "coffee",
      "occurred_on": "2026-09-12",
      "income_id": null,
      "saving_goal_id": null,
      "loan_id": null,
      "fixed_expense_id": null,
      "created_at": "2026-09-12T08:11:00+07:00"
    }
  ],
  "next_cursor": "base64(occurred_on|id)"
}
```

**`DELETE /transactions/{id}`** → 204. Deleting an auto-posted due **re-opens** that source for the month.

### 8.4 Setup CRUD

List/create/patch/delete: `/incomes`, `/savings`, `/loans`, `/fixed`.

Create income example:

```json
{
  "name": "Salary",
  "amount": 20000000,
  "currency": "VND",
  "day_of_month": 1,
  "recurrence": "monthly"
}
```

Patch may change `amount` / `day_of_month` / `name`. **`currency` is immutable after create** (avoid silently mixing a goal’s history). To change currency, delete (history kept) and recreate.

`/loans` extra: `principal`, `monthly_payment`, `note?`, `disburse_now?`.

### 8.5 Settings

`GET/PUT /settings`

```json
{
  "home_currency": "VND",
  "digest_hour": 21,
  "digest_enabled": true,
  "over_slot_alert": true,
  "over_month_alert": true
}
```

`home_currency` must be in the catalog. `digest_hour` stored but **v1 job is fixed 21:00 ICT**; UI: “Digest 21:00 (fixed in v1)”.

---

## 9. Scheduler + Telegram

### 9.1 Due poster — `00:10` `Asia/Ho_Chi_Minh` daily

For each monthly income / fixed / saving (`monthly_amount > 0`) / loan due today: if no tx this calendar month with that source id + matching kind, insert one **in the setup row’s currency**.

Also run at the **start of `GET /dashboard`** (catch-up). Idempotent.

### 9.2 Digest — `21:00` ICT

One message **per home currency** (v1: a single digest in `home_currency` only — keep Telegram quiet). Other currencies are not summarized unless `home_currency` is switched.

```
Money 12/09 VND
Spent today: ₫220,000 / slot ₫378,947
Month left: ₫6,980,000
Due in 7 days: Electricity ₫800,000 (15/09)
```

If `today_left < 0` for home currency: `over slot`.

Dedup: `(owner, digest, today, home_currency)`.

### 9.3 Alerts

| type | trigger | dedup |
|------|---------|--------|
| `over_slot` | daily POST in `C`, `today_left(C) < 0` | `(owner, over_slot, today, C)` |
| `over_month` | any POST in `C`, `remaining_month(C) < 0` | `(owner, over_month, month_start, C)` |

Telegram down: log warn, HTTP still 200. No email.

---

## 10. Frontend

### 10.1 Routes

| path | page |
|------|------|
| `/money` | Dashboard |
| `/money/history` | History |
| `/money/log` | Redirect `/money` |

### 10.2 Nav

`Board` · `Money` · Sign out. Portfolio Sign in unchanged.

### 10.3 Dashboard

1. Currency select (home + in-use codes) next to the hero  
2. **Hero:** `Left today` · formatted `today_left` · sub `slot … · spent …`  
3. **Tiles:** Income · Fixed · Daily · Saving · Loans remaining  
4. **vs last month** one line, same currency  
5. **Quick log:** amount, currency (default = dashboard selection), category, note, `Log`  
6. **Due in 7 days** (that currency)  
7. Saving / loan bars for that currency  
8. Setup accordion: each add-form has a currency select  
9. `History →`

Amount inputs: 0 decimals for VND/JPY/KRW/IDR/TWD; 2 decimals for the rest.

### 10.4 History

Filters: month, kind, category, **currency**.  
Amount column uses that row’s formatter. Mixed list is allowed.

Kind labels: Income, Daily, Fixed, Saving, Loan payment, Loan in.

### 10.5 Files (planned)

**Core:** `V4__finance.sql`, `FinanceCatalog.java`, `FinanceMoney.java` (parse/format minor units), `FinanceMath.java`, repositories, service, due poster, digest job, controller, tests.

**App:** `financeApi.ts`, `types.ts`, `finance.schemas.ts`, hooks, `MoneyDashboardPage.tsx`, `MoneyHistoryPage.tsx`, `DailyLogForm.tsx`, `MoneyTiles.tsx`, `SetupSection.tsx`, `formatMoney.ts`, router + features flag.

---

## 11. Config

```yaml
ivelox:
  finance-enabled: ${FINANCE_ENABLED:true}
```

No FX API keys. `@Scheduled` zone `Asia/Ho_Chi_Minh`.

---

## 12. Testing

**FinanceMoneyTest** — VND 20000 → minor 20000; USD 4.50 → 450; USD 4.5 → 450; USD 4.123 → reject; JPY 100.5 → reject.

**FinanceMathTest** — §6 worked example; last day of month; empty month; USD example `pool=10000 cents`.

**FinanceDuePosterTest** — EUR monthly income posts `currency=EUR`; no duplicate; remaining 0 skip.

**FinanceNotifyTest** — VND over-slot then USD over-slot same day → **two** Telegram messages; second VND over-slot → none.

**FinanceControllerTest** — mismatch currency on saving contribute → 400; flag off 404; unauth 401.

**FE:** zod rejects USD amount with 3 decimals; hero formats `₫` vs `$`.

---

## 13. Acceptance checklist

- [ ] Login → Money in nav, Health still there
- [ ] Flag off → `/money` → `/`
- [ ] Home currency VND; log 45,000 VND coffee; hero drops
- [ ] Log $4.50 USD; VND hero **unchanged**; switch picker to USD → USD hero moves
- [ ] Monthly VND salary auto-posts VND; EUR rent auto-posts EUR
- [ ] Contribute USD to a VND saving goal → 400 `currency_mismatch`
- [ ] Over VND slot → one VND Telegram; second VND log same day → no second VND alert; over USD slot → separate USD alert
- [ ] 21:00 digest is **home currency only**
- [ ] History mixed list + currency filter
- [ ] Delete source → old history remains
- [ ] Portfolio `/` never shows money
- [ ] No FX, no email

---

## 14. Rollout order (after spec Approved)

1. V4 + catalog + math + dashboard/tx API + flag  
2. FE dashboard + currency picker + daily log + history  
3. Setup CRUD API + UI  
4. Due poster + catch-up  
5. Telegram digest + per-currency alerts  
6. Fly `FINANCE_ENABLED=true`

---

## 15. Review notes

1. **Saving is “money set aside”** in that currency’s pool, not a bank account. Correct?  
2. **Loan disbursement defaults off.** Correct?  
3. **Deleting an auto-post** can be rewritten the same month. Acceptable?  
4. **Digest 21:00, home currency only.** OK?  
5. **After login, land on Health.** OK?  
6. **No FX in v1** — USD and VND never share a total. OK?  
7. **Catalog of 19 codes** is enough for v1 (add more later in code). OK?

Reply **Approved** (and correct 1–7 if needed). Then comes the implementation plan and code.
