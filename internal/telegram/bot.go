package telegram

import (
	"context"
	"fmt"
	"log"
	"strings"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"
	"github.com/google/uuid"
	"github.com/nqhhdev/ivelox-core/internal/domain"
	"github.com/nqhhdev/ivelox-core/internal/staging"
)

// Bot wraps the Telegram Bot API and provides methods for sending
// pending exam previews and handling approve/reject callbacks.
type Bot struct {
	api    *tgbotapi.BotAPI
	chatID int64
	repo   *staging.Repository
}

// NewBot creates a new Telegram bot with the given token, target chat ID,
// and staging repository for persisting review decisions.
func NewBot(token string, chatID int64, repo *staging.Repository) (*Bot, error) {
	api, err := tgbotapi.NewBotAPI(token)
	if err != nil {
		return nil, fmt.Errorf("telegram bot init: %w", err)
	}
	return &Bot{api: api, chatID: chatID, repo: repo}, nil
}

// SendPreview sends a pending exam preview to Telegram with Approve/Reject buttons.
// Returns the sent message ID (stored in pending_exams.telegram_msg_id).
func (b *Bot) SendPreview(pe *domain.PendingExam) (int, error) {
	text := FormatPreviewMessage(pe)

	keyboard := tgbotapi.NewInlineKeyboardMarkup(
		tgbotapi.NewInlineKeyboardRow(
			tgbotapi.NewInlineKeyboardButtonData("Approve", "approve:"+pe.ID.String()),
			tgbotapi.NewInlineKeyboardButtonData("Reject", "reject:"+pe.ID.String()),
		),
	)

	msg := tgbotapi.NewMessage(b.chatID, text)
	msg.ParseMode = tgbotapi.ModeMarkdown
	msg.ReplyMarkup = keyboard

	sent, err := b.api.Send(msg)
	if err != nil {
		return 0, fmt.Errorf("send telegram message: %w", err)
	}
	return sent.MessageID, nil
}

// FormatPreviewMessage formats a PendingExam into a human-readable
// Telegram message. Exported for testability.
func FormatPreviewMessage(pe *domain.PendingExam) string {
	var skills []string
	if pe.HasReading {
		skills = append(skills, "Reading")
	}
	if pe.HasListening {
		skills = append(skills, "Listening")
	}
	if pe.HasWriting {
		skills = append(skills, "Writing")
	}
	if pe.HasSpeaking {
		skills = append(skills, "Speaking")
	}

	series := pe.Series
	if series == "" {
		series = pe.SourceName
	}
	title := series
	if pe.TestNumber > 0 {
		title = fmt.Sprintf("%s Test %d", series, pe.TestNumber)
	}

	dupNote := ""
	if pe.DuplicateOf != nil {
		dupNote = "\n*Possible duplicate* of existing exam"
	}

	return fmt.Sprintf(
		"*New Exam Found*\n\n"+
			"*Title:* %s\n"+
			"*Year:* %d\n"+
			"*Source:* %s\n"+
			"*Skills:* %s\n"+
			"*Quality:* %.1f/10%s\n\n"+
			"*ID:* %s",
		title,
		pe.Year,
		pe.SourceName,
		strings.Join(skills, " | "),
		pe.QualityScore,
		dupNote,
		pe.ID.String(),
	)
}

// StartPolling listens for callback queries (Approve/Reject button taps).
// It blocks until the context is cancelled.
func (b *Bot) StartPolling(ctx context.Context) {
	u := tgbotapi.NewUpdate(0)
	u.Timeout = 60
	updates := b.api.GetUpdatesChan(u)

	log.Println("[telegram] bot polling started")
	for {
		select {
		case <-ctx.Done():
			b.api.StopReceivingUpdates()
			return
		case update := <-updates:
			if update.CallbackQuery == nil {
				continue
			}
			b.handleCallback(ctx, update.CallbackQuery)
		}
	}
}

func (b *Bot) handleCallback(_ context.Context, cb *tgbotapi.CallbackQuery) {
	parts := strings.SplitN(cb.Data, ":", 2)
	if len(parts) != 2 {
		return
	}
	action, idStr := parts[0], parts[1]

	id, err := uuid.Parse(idStr)
	if err != nil {
		log.Printf("[telegram] invalid id in callback: %s", idStr)
		return
	}

	now := time.Now()
	var status, label string
	switch action {
	case "approve":
		status = "approved"
		label = "Approved"
	case "reject":
		status = "rejected"
		label = "Rejected"
	default:
		return
	}

	if err := b.repo.UpdateStatus(id, status, int64(cb.Message.MessageID), &now); err != nil {
		log.Printf("[telegram] update status error: %v", err)
		return
	}

	// Edit message to show result
	edit := tgbotapi.NewEditMessageText(b.chatID, cb.Message.MessageID,
		cb.Message.Text+"\n\n*"+label+"*")
	edit.ParseMode = tgbotapi.ModeMarkdown
	if _, err := b.api.Send(edit); err != nil {
		log.Printf("[telegram] edit message error: %v", err)
	}

	// Answer callback to remove loading spinner
	answer := tgbotapi.NewCallback(cb.ID, label)
	if _, err := b.api.Request(answer); err != nil {
		log.Printf("[telegram] answer callback error: %v", err)
	}

	log.Printf("[telegram] exam %s -> %s", idStr, status)
}
