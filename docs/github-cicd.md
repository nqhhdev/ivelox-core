# GitHub CI/CD

## Workflows

| File | Trigger | Jobs |
|---|---|---|
| `.github/workflows/ci.yml` | Every push & PR | `go build` + `go test -race` |
| `.github/workflows/deploy.yml` | Push to `main` | build → test → `flyctl deploy` |
| `.github/workflows/review.yml` | PRs only | `gofmt` + `go vet` + `staticcheck` |
| `.github/workflows/integration.yml` | Push to `main` & PRs | Supabase branch DB → integration tests |

## Required GitHub Secrets

Add at: `github.com/nqhhdev/ivelox-core` → Settings → Secrets and variables → Actions

| Secret | How to get |
|---|---|
| `FLY_API_TOKEN` | `flyctl tokens create deploy` |
| `SUPABASE_ACCESS_TOKEN` | supabase.com → Account → Access Tokens |
| `SUPABASE_JWT_SECRET` | Supabase dashboard → Project Settings → JWT |

## Running Tests Locally

```bash
# Unit tests
export PATH="/opt/homebrew/bin:$PATH" && go test ./... -v -race -count=1

# Integration tests (real DB)
export PATH="/opt/homebrew/bin:$PATH" && export $(cat .env | grep -v '^#' | xargs) && go test -tags integration ./tests/... -v -count=1

# All with coverage
export PATH="/opt/homebrew/bin:$PATH" && go test ./... -cover -count=1
```

## Test Coverage

| Package | Coverage |
|---|---|
| `internal/delivery/http` | 100% |
| `internal/middleware` | 100% |
| `internal/usecase` | 100% |
| `internal/infrastructure/supabase` | 91.7% |
| `config` | 90% |
| `tests/integration` | real DB |

## Integration Test Flow (CI)

1. GitHub Actions creates a Supabase branch DB (`ci-<run-id>`)
2. Runs `go test -tags integration ./tests/...` against branch DB
3. Deletes branch DB after test (always, even on failure)
