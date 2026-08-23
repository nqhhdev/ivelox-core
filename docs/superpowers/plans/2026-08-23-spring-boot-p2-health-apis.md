# Spring Boot P2 — Health APIs + Flyway + Gemini

**Goal:** Re-implement Health food resolve / meals / today summary on Spring Boot with Flyway schema and Gemini nutrition.

**Status:** In progress

## Checklist

- [x] JDBC + Flyway + Postgres/H2 deps; `V1__food_cache_meal_logs.sql` (owner-only `user_id text`)
- [x] Datasource defaults to local H2; prod via `SPRING_DATASOURCE_*`
- [x] Food normalize + ICT civil day helpers
- [x] Food cache + meal log JDBC repositories
- [x] FoodResolveService (cache ≥0.6, 90d TTL; image skips cache)
- [x] MealService (create/list/delete/today)
- [x] GeminiNutritionClient (HTTP generateContent + JSON repair)
- [x] HealthController under `/api/v1/health/**` (JWT); feature flag gate
- [x] Unit + Spring tests; `./mvnw test`


## API shapes (unchanged from Go P1)

| Method | Path |
|--------|------|
| POST | `/api/v1/health/foods/resolve` |
| POST | `/api/v1/health/meals` |
| GET | `/api/v1/health/meals?date=` |
| DELETE | `/api/v1/health/meals/:id` |
| GET | `/api/v1/health/check/today?date=` |

Civil day: `Asia/Ho_Chi_Minh`. Image ≤ 3MB; MIME jpeg/png/webp/gif.
Owner `user_id` = JWT subject `"owner"`.
