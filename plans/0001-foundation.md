# Phase 0 — Foundation ✅

## Goal
Bare-metal Go + Gin server wired with Clean Architecture, Supabase auth, and PostgreSQL connection.

## Completed
- [x] Go + Gin HTTP server (`cmd/server/main.go`)
- [x] Clean Architecture layers: domain / usecase / repository / delivery / infrastructure
- [x] Config loading via godotenv (`config/config.go`)
- [x] Supabase JWT verification middleware (`internal/middleware/auth.go`)
- [x] CORS middleware (`internal/middleware/cors.go`)
- [x] `GET /api/v1/health` — unauthenticated health check
- [x] `POST /api/v1/auth/verify` — verify JWT, return user profile
- [x] pgx/v5 pool connected to Supabase PostgreSQL
- [x] `profiles` table in Supabase (UUID PK, display_name, target_band, role enum, created_at)
- [x] RLS policies on profiles (user can read/update own row)
- [x] Auto-create profile trigger on `auth.users` insert
- [x] Unit tests: JWT middleware (valid / missing / expired)
- [x] Unit tests: auth handler with fake repo
- [x] `CLAUDE.md` + `.claude/architecture.md`
- [x] `docs/deployment.md`

## Key Files
```
cmd/server/main.go
config/config.go
internal/domain/user.go
internal/usecase/auth.go
internal/repository/postgres/user.go
internal/delivery/http/router.go
internal/delivery/http/auth_handler.go
internal/infrastructure/supabase/jwt.go
internal/middleware/auth.go
internal/middleware/cors.go
```
