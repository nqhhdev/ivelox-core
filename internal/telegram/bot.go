package telegram

import (
	"context"
	"crypto/md5"
	"fmt"
	"log"
	"strings"
	"sync"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/chat"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/profile"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/scorer"
)

const jobCacheTTL = 2 * time.Hour

type cachedJob struct {
	job       scorer.ScoredJob
	expiresAt time.Time
}

// Bot manages all Telegram interactions.
type Bot struct {
	api         *tgbotapi.BotAPI
	chatID      int64
	sessions    *chat.Store
	chatH       *chat.Handler
	profileRepo *profile.Repository
	scorer      *scorer.Scorer

	// jobsByHash allows looking up a ScoredJob by the MD5 hash of its ApplyURL.
	// Using the hash keeps callback_data under Telegram's 64-byte limit.
	// Protected by mu since RegisterJobs (ticker goroutine) and startJobChat
	// (polling goroutine) run concurrently.
	// Entries expire after jobCacheTTL to prevent unbounded memory growth.
	mu         sync.RWMutex
	jobsByHash map[string]cachedJob
}

func NewBot(token string, chatID int64, chatH *chat.Handler, profileRepo *profile.Repository, sc *scorer.Scorer) (*Bot, error) {
	api, err := tgbotapi.NewBotAPI(token)
	if err != nil {
		return nil, fmt.Errorf("telegram init: %w", err)
	}
	return &Bot{
		api:         api,
		chatID:      chatID,
		sessions:    chat.NewStore(),
		chatH:       chatH,
		profileRepo: profileRepo,
		scorer:      sc,
		jobsByHash:  make(map[string]cachedJob),
	}, nil
}

// RegisterJobs stores scored jobs so the bot can start chat sessions when
// the user taps [💬 Chat with AI]. Old entries are pruned on each call to
// prevent unbounded memory growth from long-lived workers.
func (b *Bot) RegisterJobs(jobs []scorer.ScoredJob) {
	b.mu.Lock()
	defer b.mu.Unlock()

	// Prune expired entries before adding new ones.
	now := time.Now()
	for k, v := range b.jobsByHash {
		if now.After(v.expiresAt) {
			delete(b.jobsByHash, k)
		}
	}

	expires := now.Add(jobCacheTTL)
	for _, j := range jobs {
		b.jobsByHash[urlHash(j.ApplyURL)] = cachedJob{job: j, expiresAt: expires}
	}
}

func urlHash(url string) string {
	return fmt.Sprintf("%x", md5.Sum([]byte(url)))[:16]
}

// StartPolling blocks until ctx is cancelled.
func (b *Bot) StartPolling(ctx context.Context) {
	u := tgbotapi.NewUpdate(0)
	u.Timeout = 60
	updates := b.api.GetUpdatesChan(u)

	log.Println("[telegram] polling started")
	for {
		select {
		case <-ctx.Done():
			b.api.StopReceivingUpdates()
			return
		case update := <-updates:
			if update.Message != nil {
				b.handleMessage(ctx, update.Message)
			} else if update.CallbackQuery != nil {
				b.handleCallback(ctx, update.CallbackQuery)
			}
		}
	}
}

// SendMessage sends a plain markdown message to the configured chat.
func (b *Bot) SendMessage(text string) error {
	msg := tgbotapi.NewMessage(b.chatID, text)
	msg.ParseMode = tgbotapi.ModeMarkdown
	_, err := b.api.Send(msg)
	return err
}

// SendMessageToChat sends a markdown message to a specific chat ID.
func (b *Bot) SendMessageToChat(chatID int64, text string) {
	msg := tgbotapi.NewMessage(chatID, text)
	msg.ParseMode = tgbotapi.ModeMarkdown
	if _, err := b.api.Send(msg); err != nil {
		log.Printf("[telegram] send error: %v", err)
	}
}

// API returns the underlying bot API (used by notifier to send job messages).
func (b *Bot) API() *tgbotapi.BotAPI { return b.api }

// ─── Message handlers ────────────────────────────────────────────────────────

func (b *Bot) handleMessage(ctx context.Context, msg *tgbotapi.Message) {
	if msg.Chat.ID != b.chatID {
		return
	}

	// If user has an active job chat session, route message to Gemini
	if sess := b.sessions.Get(msg.Chat.ID); sess != nil && msg.Command() == "" {
		b.handleChatMessage(ctx, msg.Chat.ID, sess, msg.Text)
		return
	}

	arg := strings.TrimSpace(msg.CommandArguments())

	switch msg.Command() {
	case "done":
		b.sessions.End(msg.Chat.ID)
		b.SendMessageToChat(msg.Chat.ID, "Chat session ended.")
	case "help", "start":
		b.sendHelp(msg.Chat.ID)
	case "profile":
		b.handleProfile(ctx, msg.Chat.ID)
	case "setrole":
		b.handleSetField(ctx, msg.Chat.ID, "role", arg)
	case "setskills":
		b.handleSetField(ctx, msg.Chat.ID, "skills", arg)
	case "setlocation":
		b.handleSetField(ctx, msg.Chat.ID, "location", arg)
	case "setsalary":
		b.handleSetField(ctx, msg.Chat.ID, "salary_min", arg)
	case "setlang":
		b.handleSetField(ctx, msg.Chat.ID, "languages", arg)
	case "setextra":
		b.handleSetField(ctx, msg.Chat.ID, "extra", arg)
	}
}

// ─── Profile commands ────────────────────────────────────────────────────────

func (b *Bot) handleProfile(ctx context.Context, chatID int64) {
	p, err := b.profileRepo.Get(ctx)
	if err != nil {
		log.Printf("[telegram] profile get: %v", err)
		b.SendMessageToChat(chatID, "❌ Failed to load profile.")
		return
	}
	b.SendMessageToChat(chatID, p.FormatText())
}

func (b *Bot) handleSetField(ctx context.Context, chatID int64, field, value string) {
	if value == "" {
		b.SendMessageToChat(chatID, fmt.Sprintf("Usage: /set%s <value>", field))
		return
	}

	// Read current value first so user can see what changed
	old, err := b.profileRepo.Get(ctx)
	if err != nil {
		log.Printf("[telegram] profile get before update: %v", err)
		b.SendMessageToChat(chatID, "❌ Failed to read current profile.")
		return
	}

	if err := b.profileRepo.Update(ctx, field, value); err != nil {
		log.Printf("[telegram] profile update %s: %v", field, err)
		b.SendMessageToChat(chatID, fmt.Sprintf("❌ Failed to update %s.", field))
		return
	}

	// Reload updated profile and push to scorer + chat handler
	updated, err := b.profileRepo.Get(ctx)
	if err == nil {
		profileText := updated.ToPromptText()
		if b.scorer != nil {
			b.scorer.SetProfile(profileText)
		}
		if b.chatH != nil {
			b.chatH.SetProfile(profileText)
		}
	}

	oldVal := fieldValue(old, field)
	b.SendMessageToChat(chatID, fmt.Sprintf(
		"✅ *%s* updated\n\n*Before:* %s\n*After:* %s",
		field, oldVal, value,
	))
}

// fieldValue returns the current string value of a profile field for display.
func fieldValue(p profile.Profile, field string) string {
	switch field {
	case "role":
		return p.Role
	case "skills":
		return p.Skills
	case "location":
		return p.Location
	case "salary_min":
		return fmt.Sprintf("$%d", p.SalaryMin)
	case "languages":
		return p.Languages
	case "extra":
		return p.Extra
	}
	return "—"
}

// ─── Callback handlers ───────────────────────────────────────────────────────

func (b *Bot) handleCallback(ctx context.Context, cb *tgbotapi.CallbackQuery) {
	if cb.Message == nil || cb.Message.Chat.ID != b.chatID {
		return
	}
	chatID := cb.Message.Chat.ID
	b.answerCallback(cb.ID, "")

	parts := strings.SplitN(cb.Data, ":", 2)
	if len(parts) != 2 {
		return
	}
	action, value := parts[0], parts[1]

	switch action {
	case "job_chat":
		b.startJobChat(ctx, chatID, value)
	}
}

// ─── Job chat ────────────────────────────────────────────────────────────────

func (b *Bot) startJobChat(_ context.Context, chatID int64, hash string) {
	b.mu.RLock()
	entry, ok := b.jobsByHash[hash]
	b.mu.RUnlock()
	if !ok || time.Now().After(entry.expiresAt) {
		b.SendMessageToChat(chatID, "❌ Job not found. It may have expired. Try the next run.")
		return
	}
	job := entry.job
	b.sessions.Start(chatID, job)
	b.SendMessageToChat(chatID, fmt.Sprintf(
		"💬 *Chatting about:* %s @ %s\n\nAsk me anything about this job 👇\n_(Send /done to end)_",
		job.Title, job.Company,
	))
}

func (b *Bot) handleChatMessage(ctx context.Context, chatID int64, sess *chat.Session, question string) {
	if strings.TrimSpace(question) == "" {
		return
	}
	if b.chatH == nil {
		b.SendMessageToChat(chatID, "Chat feature is not available.")
		return
	}

	b.sessions.Append(chatID, "user", question)

	reply, err := b.chatH.Reply(ctx, sess, question)
	if err != nil {
		log.Printf("[telegram] chat reply error: %v", err)
		b.SendMessageToChat(chatID, "❌ AI error. Try again.")
		return
	}

	b.sessions.Append(chatID, "model", reply)
	b.SendMessageToChat(chatID, reply)
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

func (b *Bot) sendHelp(chatID int64) {
	b.SendMessageToChat(chatID, ""+
		"*iVelox Job Finder*\n\n"+
		"*Profile commands:*\n"+
		"/profile — view current profile\n"+
		"/setrole <role> — e.g. Senior Flutter Developer\n"+
		"/setskills <skills> — e.g. Flutter, Kotlin, Swift\n"+
		"/setlocation <locations> — e.g. Remote, Vietnam\n"+
		"/setsalary <min_usd> — e.g. 2000\n"+
		"/setlang <languages> — e.g. Vietnamese, English\n"+
		"/setextra <notes> — any extra requirements\n\n"+
		"*Chat commands:*\n"+
		"/done — end current job chat session\n"+
		"/help — show this message\n\n"+
		"_Jobs are fetched every 15 minutes._",
	)
}

func (b *Bot) answerCallback(callbackID, text string) {
	answer := tgbotapi.NewCallback(callbackID, text)
	if _, err := b.api.Request(answer); err != nil {
		log.Printf("[telegram] answer callback error: %v", err)
	}
}
