# iVelox Core — Deployment & Swagger Guide

## 1. Prerequisites

| Tool | Version | Install |
|---|---|---|
| Go | 1.22+ | `brew install go` |
| Docker | latest | https://docs.docker.com/get-docker |
| Railway CLI (optional) | latest | `brew install railway` |

---

## 2. Run locally

```bash
# Clone repo
git clone https://github.com/nqhhdev/ivelox-core
cd ivelox-core

# Copy env
cp .env.example .env
# Fill in SUPABASE_JWT_SECRET and DATABASE_URL in .env

# Download dependencies
go mod tidy

# Run server
go run ./cmd/server/
```

Server starts at `http://localhost:8080`

Test health endpoint:
```bash
curl http://localhost:8080/api/v1/health
# {"status":"ok"}
```

---

## 3. Add Swagger (swaggo)

### 3.1 Install swag CLI

```bash
go install github.com/swaggo/swag/cmd/swag@latest
export PATH="$PATH:$(go env GOPATH)/bin"
```

### 3.2 Add swaggo dependencies

```bash
go get github.com/swaggo/gin-swagger
go get github.com/swaggo/files
go get github.com/swaggo/swag
```

### 3.3 Annotate main.go

Add these comments at the top of `cmd/server/main.go` (above `package main`):

```go
// @title           iVelox API
// @version         1.0
// @description     IELTS learning platform API
// @host            localhost:8080
// @BasePath        /api/v1
// @securityDefinitions.apikey BearerAuth
// @in header
// @name Authorization
// @description Enter: Bearer <your-jwt-token>
package main
```

### 3.4 Annotate handlers

Example for `internal/delivery/http/auth_handler.go`:

```go
// Verify godoc
// @Summary      Verify JWT and return user profile
// @Tags         auth
// @Security     BearerAuth
// @Produce      json
// @Success      200  {object}  map[string]interface{}
// @Failure      401  {object}  map[string]interface{}
// @Failure      404  {object}  map[string]interface{}
// @Router       /auth/verify [post]
func (h *AuthHandler) Verify(c *gin.Context) {
```

### 3.5 Generate swagger docs

```bash
swag init -g cmd/server/main.go -o docs/swagger
```

This creates `docs/swagger/docs.go`, `swagger.json`, `swagger.yaml`.

### 3.6 Register swagger route in router.go

```go
import (
    swaggerFiles "github.com/swaggo/files"
    ginSwagger "github.com/swaggo/gin-swagger"
    _ "github.com/nqhhdev/ivelox-core/docs/swagger" // generated docs
)

// Inside NewRouter, add:
r.GET("/swagger/*any", ginSwagger.WrapHandler(swaggerFiles.Handler))
```

### 3.7 Access Swagger UI

```
http://localhost:8080/swagger/index.html
```

---

## 4. Docker

### 4.1 Create `Dockerfile` at repo root

```dockerfile
FROM golang:1.22-alpine AS builder
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN go build -o server ./cmd/server/

FROM alpine:latest
WORKDIR /app
COPY --from=builder /app/server .
EXPOSE 8080
CMD ["./server"]
```

### 4.2 Create `.dockerignore`

```
.env
.git
*.md
docs/
```

### 4.3 Build and run Docker image

```bash
docker build -t ivelox-core .

docker run -p 8080:8080 \
  -e PORT=8080 \
  -e FRONTEND_URL=http://localhost:5173 \
  -e SUPABASE_URL=https://zvcpgyzwmwwmredwzgcy.supabase.co \
  -e SUPABASE_JWT_SECRET=your-jwt-secret \
  -e DATABASE_URL=your-database-url \
  ivelox-core
```

---

## 5. Deploy to Railway (Recommended)

Railway là platform deploy Go app nhanh nhất, free tier có $5 credit/month.

### 5.1 Setup

1. Vào https://railway.app → New Project → Deploy from GitHub repo
2. Chọn `nqhhdev/ivelox-core`
3. Railway tự detect Go và build

### 5.2 Set environment variables

Trong Railway dashboard → Variables, thêm:

```
PORT=8080
FRONTEND_URL=https://your-frontend-domain.vercel.app
SUPABASE_URL=https://zvcpgyzwmwwmredwzgcy.supabase.co
SUPABASE_JWT_SECRET=your-jwt-secret
DATABASE_URL=your-database-url
```

### 5.3 Custom domain (optional)

Railway dashboard → Settings → Domains → Generate Domain

URL sẽ có dạng: `https://ivelox-core-production.up.railway.app`

---

## 6. Deploy to Fly.io (Alternative)

Fly.io cho phép deploy Docker container, free tier 3 shared VMs.

### 6.1 Install flyctl

```bash
brew install flyctl
fly auth login
```

### 6.2 Init và deploy

```bash
cd ivelox-core
fly launch --name ivelox-core --region sin  # Singapore — gần VN nhất
fly secrets set SUPABASE_JWT_SECRET=your-secret DATABASE_URL=your-url
fly deploy
```

### 6.3 Check logs

```bash
fly logs
```

---

## 7. CI/CD với GitHub Actions

Tạo `.github/workflows/deploy.yml`:

```yaml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-go@v5
        with:
          go-version: '1.22'
      - run: go test ./...

  deploy:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: superfly/flyctl-actions/setup-flyctl@master
      - run: flyctl deploy --remote-only
        env:
          FLY_API_TOKEN: ${{ secrets.FLY_API_TOKEN }}
```

---

## 8. Recommended: Railway (Phase B) → Fly.io (Phase C)

| Phase | Platform | Cost | Why |
|---|---|---|---|
| B (friends) | Railway | Free/$5 | Zero config, auto-deploy from GitHub |
| C (public) | Fly.io | ~$5-10/month | More control, multi-region, Docker-based |
