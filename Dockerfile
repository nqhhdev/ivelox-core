# Build stage
FROM golang:1.25-alpine AS builder

WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY . .

RUN CGO_ENABLED=0 GOOS=linux go build -o server ./cmd/server
RUN CGO_ENABLED=0 GOOS=linux go build -o jobfinder ./cmd/jobfinder

# ─── API server ───────────────────────────────────────────────────────────────
FROM alpine:3.20 AS server

WORKDIR /app
RUN apk --no-cache add ca-certificates tzdata

COPY --from=builder /app/server .

EXPOSE 8080
CMD ["./server"]

# ─── Job finder worker ────────────────────────────────────────────────────────
FROM alpine:3.20 AS jobfinder

WORKDIR /app
RUN apk --no-cache add ca-certificates tzdata

COPY --from=builder /app/jobfinder .

CMD ["./jobfinder"]
