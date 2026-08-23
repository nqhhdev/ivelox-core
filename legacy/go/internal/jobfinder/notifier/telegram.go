package notifier

import (
	"crypto/md5"
	"fmt"
	"sort"
	"strings"
	"time"

	tgbotapi "github.com/go-telegram-bot-api/telegram-bot-api/v5"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/scorer"
)

// Notifier sends job notifications to Telegram.
type Notifier struct {
	api    *tgbotapi.BotAPI
	chatID int64
}

func NewNotifier(api *tgbotapi.BotAPI, chatID int64) *Notifier {
	return &Notifier{api: api, chatID: chatID}
}

// Notify sends all scored jobs sorted by score descending.
// Sends a summary header first if 3+ jobs.
func (n *Notifier) Notify(jobs []scorer.ScoredJob) {
	if len(jobs) == 0 {
		return
	}

	// Sort descending by score
	sort.Slice(jobs, func(i, j int) bool {
		return jobs[i].Score > jobs[j].Score
	})

	if len(jobs) >= 3 {
		header := fmt.Sprintf("🔍 Found *%d* new matches this run (%s)",
			len(jobs), time.Now().Format("15:04"))
		n.send(header, nil)
	}

	for _, job := range jobs {
		text := FormatJobMessage(job)
		keyboard := tgbotapi.NewInlineKeyboardMarkup(
			tgbotapi.NewInlineKeyboardRow(
				tgbotapi.NewInlineKeyboardButtonURL("👉 Apply Now", job.ApplyURL),
				tgbotapi.NewInlineKeyboardButtonData("💬 Chat with AI", "job_chat:"+urlHash(job.ApplyURL)),
			),
		)
		n.send(text, &keyboard)
		time.Sleep(time.Second) // Telegram rate limit: 1 msg/sec
	}
}

// FormatJobMessage formats a single job as a Telegram markdown message.
// Exported for tests.
func FormatJobMessage(job scorer.ScoredJob) string {
	badge := "🟡"
	if job.Score >= 80 {
		badge = "🟢"
	}

	workType := job.WorkType
	if workType == "unknown" || workType == "" {
		workType = "—"
	}

	var sb strings.Builder
	sb.WriteString(fmt.Sprintf("%s *Match: %d/100* · %s\n\n", badge, job.Score, workType))
	sb.WriteString(fmt.Sprintf("*%s*\n", escapeMarkdown(job.Title)))
	sb.WriteString(fmt.Sprintf("🏢 %s", escapeMarkdown(job.Company)))
	if job.Location != "" {
		sb.WriteString(fmt.Sprintf("  ·  🌏 %s", escapeMarkdown(job.Location)))
	}
	sb.WriteString("\n")
	if job.Salary != "" {
		sb.WriteString(fmt.Sprintf("💰 %s\n", escapeMarkdown(job.Salary)))
	}
	sb.WriteString(fmt.Sprintf("📌 Source: %s\n", job.Source))

	if len(job.MatchReasons) > 0 {
		sb.WriteString("\n✅ *Why you match:*\n")
		for _, r := range job.MatchReasons {
			sb.WriteString(fmt.Sprintf("• %s\n", r))
		}
	}

	if len(job.GapSkills) > 0 {
		sb.WriteString("\n⚠️ *Skill gaps:*\n")
		for _, g := range job.GapSkills {
			sb.WriteString(fmt.Sprintf("• %s\n", g))
		}
	}

	return sb.String()
}

func (n *Notifier) send(text string, keyboard *tgbotapi.InlineKeyboardMarkup) {
	msg := tgbotapi.NewMessage(n.chatID, text)
	msg.ParseMode = tgbotapi.ModeMarkdown
	if keyboard != nil {
		msg.ReplyMarkup = *keyboard
	}
	if _, err := n.api.Send(msg); err != nil {
		fmt.Printf("[notifier] send error: %v\n", err)
	}
}

// urlHash returns the first 16 hex chars of the MD5 of a URL — safe for callback_data.
func urlHash(url string) string {
	return fmt.Sprintf("%x", md5.Sum([]byte(url)))[:16]
}

// escapeMarkdown escapes special Telegram MarkdownV1 characters.
func escapeMarkdown(s string) string {
	replacer := strings.NewReplacer(
		"_", "\\_",
		"[", "\\[",
		"`", "\\`",
	)
	return replacer.Replace(s)
}
