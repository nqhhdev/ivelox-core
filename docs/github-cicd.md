# GitHub CI/CD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Set up three GitHub Actions workflows: testing, deployment to Fly.io, and code review (syntax + vet + staticcheck).

**Architecture:** Three separate workflow files — each triggered independently. `ci.yml` runs on every PR and push. `deploy.yml` runs on push to `main` after tests pass. `review.yml` runs on PR open/update for static analysis.

**Tech Stack:** GitHub Actions, Go 1.23, flyctl, staticcheck

---

## File Structure

| File | Purpose |
|---|---|
| `.github/workflows/ci.yml` | Run `go build` + `go test` on every push and PR |
| `.github/workflows/deploy.yml` | Deploy to Fly.io on push to `main` (after CI passes) |
| `.github/workflows/review.yml` | `go vet` + `staticcheck` + `gofmt` check on PRs |

---

## Task 1: CI — Build & Test

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Create the workflow file**

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: ["**"]
  pull_request:
    branches: ["**"]

jobs:
  test:
    name: Build & Test
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up Go
        uses: actions/setup-go@v5
        with:
          go-version: "1.23"
          cache: true

      - name: Build
        run: go build ./...

      - name: Test
        run: go test ./... -v -race -count=1
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add build and test workflow"
```

- [ ] **Step 3: Push and verify**

Push to any branch and check GitHub → Actions tab. Expected: green `CI` workflow run.

---

## Task 2: Deploy — Fly.io on push to main

**Files:**
- Create: `.github/workflows/deploy.yml`

**Prerequisite:** `FLY_API_TOKEN` must be set in GitHub repo secrets.

Setup:
1. Go to GitHub repo → Settings → Secrets and variables → Actions
2. Add secret: `FLY_API_TOKEN` = output of `flyctl tokens create deploy`

- [ ] **Step 1: Create the workflow file**

```yaml
# .github/workflows/deploy.yml
name: Deploy

on:
  push:
    branches: ["main"]

jobs:
  deploy:
    name: Deploy to Fly.io
    runs-on: ubuntu-latest
    needs: []

    steps:
      - uses: actions/checkout@v4

      - name: Set up Go
        uses: actions/setup-go@v5
        with:
          go-version: "1.23"
          cache: true

      - name: Build check
        run: go build ./...

      - name: Test
        run: go test ./... -count=1

      - name: Deploy to Fly.io
        uses: superfly/flyctl-actions/setup-flyctl@master

      - run: flyctl deploy --remote-only
        env:
          FLY_API_TOKEN: ${{ secrets.FLY_API_TOKEN }}
```

- [ ] **Step 2: Add FLY_API_TOKEN to GitHub secrets**

```bash
flyctl tokens create deploy
```

Copy the token. Go to:
`github.com/nqhhdev/ivelox-core` → Settings → Secrets and variables → Actions → New repository secret

Name: `FLY_API_TOKEN`
Value: (paste token)

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/deploy.yml
git commit -m "ci: add Fly.io deploy workflow on push to main"
```

- [ ] **Step 4: Push to main and verify**

```bash
git push origin main
```

Check GitHub → Actions → `Deploy` workflow. Expected: build → test → deploy all green.
Check: `curl https://api.i-velox.app/api/v1/health` returns `{"status":"ok"}`.

---

## Task 3: Code Review — Syntax, Vet, Format

**Files:**
- Create: `.github/workflows/review.yml`

- [ ] **Step 1: Create the workflow file**

```yaml
# .github/workflows/review.yml
name: Code Review

on:
  pull_request:
    branches: ["**"]

jobs:
  lint:
    name: Lint & Vet
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up Go
        uses: actions/setup-go@v5
        with:
          go-version: "1.23"
          cache: true

      - name: Check formatting (gofmt)
        run: |
          unformatted=$(gofmt -l ./...)
          if [ -n "$unformatted" ]; then
            echo "The following files are not formatted:"
            echo "$unformatted"
            exit 1
          fi

      - name: go vet
        run: go vet ./...

      - name: Install staticcheck
        run: go install honnef.co/go/tools/cmd/staticcheck@latest

      - name: staticcheck
        run: staticcheck ./...
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/review.yml
git commit -m "ci: add code review workflow (gofmt, vet, staticcheck)"
```

- [ ] **Step 3: Verify on a PR**

Create a test branch, open a PR, check GitHub → Actions → `Code Review` workflow runs and passes.

---

## Task 4: Update plans/0009-deploy.md

**Files:**
- Modify: `plans/0009-deploy.md`

- [ ] **Step 1: Mark CI/CD tasks as done**

In `plans/0009-deploy.md`, update the task list:

```markdown
- [x] Write `.github/workflows/ci.yml`
- [x] Write `.github/workflows/deploy.yml`
- [x] Write `.github/workflows/review.yml`
- [x] Add `FLY_API_TOKEN` to GitHub Actions secrets
- [x] Verify auto-deploy on push to main
```

- [ ] **Step 2: Commit**

```bash
git add plans/0009-deploy.md
git commit -m "docs: mark CI/CD tasks complete in deploy plan"
```

- [ ] **Step 3: Push**

```bash
git push origin main
```

---

## Summary

| Workflow | Trigger | What it does |
|---|---|---|
| `ci.yml` | Every push & PR | `go build` + `go test -race` |
| `deploy.yml` | Push to `main` | build → test → `flyctl deploy` |
| `review.yml` | PR opened/updated | `gofmt` + `go vet` + `staticcheck` |

**Required secret:** `FLY_API_TOKEN` in GitHub repo secrets (Task 2, Step 2).
