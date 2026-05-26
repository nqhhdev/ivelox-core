package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/nqhhdev/ivelox-core/config"
	jobfinder "github.com/nqhhdev/ivelox-core/internal/jobfinder"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/chat"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/dedup"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/fetcher"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/notifier"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/scorer"
	"github.com/nqhhdev/ivelox-core/internal/telegram"
)

const runInterval = 15 * time.Minute

func main() {
	cfg := config.Load()
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	// Database
	db, err := pgxpool.New(ctx, cfg.DatabaseURL)
	if err != nil {
		log.Fatalf("db connect: %v", err)
	}
	defer db.Close()

	// Telegram bot API (shared with bot polling)
	tgAPI, err := tgbotapi.NewBotAPI(cfg.TelegramToken)
	if err != nil {
		log.Fatalf("telegram init: %v", err)
	}

	// Gemini chat handler
	chatHandler, err := chat.NewHandler(ctx, cfg.GeminiAPIKey)
	if err != nil {
		log.Fatalf("chat handler: %v", err)
	}
	defer chatHandler.Close()

	// Telegram bot (for job chat sessions)
	bot, err := telegram.NewBot(cfg.TelegramToken, cfg.TelegramChatID, chatHandler)
	if err != nil {
		log.Fatalf("bot init: %v", err)
	}

	// Gemini scorer
	sc, err := scorer.NewScorer(ctx, cfg.GeminiAPIKey)
	if err != nil {
		log.Fatalf("scorer init: %v", err)
	}
	defer sc.Close()

	// Fetchers
	fetchers := []fetcher.Fetcher{
		fetcher.NewRemotiveFetcher(),
		fetcher.NewArbeitnowFetcher(),
		fetcher.NewTheMuseFetcher(),
		fetcher.NewTopDevFetcher(),
		fetcher.NewITviecFetcher(),
	}

	// Notifier
	ntf := notifier.NewNotifier(tgAPI, cfg.TelegramChatID)

	// Dedup
	dedupRepo := dedup.NewRepository(db)

	// Runner with onNotify hook to register jobs in bot for chat sessions
	runner := jobfinder.NewRunner(fetchers, dedupRepo, sc, ntf, func(jobs []scorer.ScoredJob) {
		bot.RegisterJobs(jobs)
	})

	// Start bot polling (handles job chat callbacks)
	go bot.StartPolling(ctx)

	log.Printf("[jobfinder] starting — interval %s", runInterval)

	// Run immediately on start, then on ticker
	runner.RunWithErrorNotify(ctx, func(msg string) {
		_ = bot.SendMessage(msg)
	})

	ticker := time.NewTicker(runInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			log.Println("[jobfinder] shutting down")
			return
		case <-ticker.C:
			runner.RunWithErrorNotify(ctx, func(msg string) {
				_ = bot.SendMessage(msg)
			})
		}
	}
}
