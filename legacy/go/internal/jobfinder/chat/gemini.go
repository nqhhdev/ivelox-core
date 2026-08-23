package chat

import (
	"context"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/google/generative-ai-go/genai"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/scorer"
	"google.golang.org/api/option"
)

// Handler answers questions about a specific job in a multi-turn conversation.
type Handler struct {
	client  *genai.Client
	mu      sync.RWMutex
	profile string // candidate profile text; updated via SetProfile
}

func NewHandler(ctx context.Context, apiKey string) (*Handler, error) {
	client, err := genai.NewClient(ctx, option.WithAPIKey(apiKey))
	if err != nil {
		return nil, fmt.Errorf("gemini chat client: %w", err)
	}
	return &Handler{client: client}, nil
}

func (h *Handler) Close() {
	h.client.Close()
}

// SetProfile updates the candidate profile text used for chat context.
func (h *Handler) SetProfile(profileText string) {
	h.mu.Lock()
	h.profile = profileText
	h.mu.Unlock()
}

// Reply generates an AI response for the given question in the context of a job session.
func (h *Handler) Reply(ctx context.Context, sess *Session, question string) (string, error) {
	h.mu.RLock()
	profile := h.profile
	h.mu.RUnlock()
	prompt := buildChatPrompt(profile, sess.Job, sess.History, question)

	model := h.client.GenerativeModel("gemini-2.5-flash-lite")
	model.SetTemperature(0.7)

	ctx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()

	resp, err := model.GenerateContent(ctx, genai.Text(prompt))
	if err != nil {
		return "", fmt.Errorf("gemini chat: %w", err)
	}

	if len(resp.Candidates) == 0 || resp.Candidates[0].Content == nil || len(resp.Candidates[0].Content.Parts) == 0 {
		return "", fmt.Errorf("gemini empty response")
	}

	return fmt.Sprintf("%v", resp.Candidates[0].Content.Parts[0]), nil
}

func buildChatPrompt(profile string, job scorer.ScoredJob, history []Message, question string) string {
	var sb strings.Builder

	sb.WriteString("You are a career advisor helping a mobile software engineer evaluate a job opportunity.\n\n")
	sb.WriteString("CANDIDATE PROFILE:\n")
	sb.WriteString(profile)
	sb.WriteString("\nJOB CONTEXT:\n")
	sb.WriteString(fmt.Sprintf("Title: %s\n", job.Title))
	sb.WriteString(fmt.Sprintf("Company: %s\n", job.Company))
	sb.WriteString(fmt.Sprintf("Location: %s\n", job.Location))
	sb.WriteString(fmt.Sprintf("Salary: %s\n", job.Salary))
	sb.WriteString(fmt.Sprintf("Match score: %d/100\n", job.Score))

	if len(job.MatchReasons) > 0 {
		sb.WriteString(fmt.Sprintf("Match reasons: %s\n", strings.Join(job.MatchReasons, ", ")))
	}
	if len(job.GapSkills) > 0 {
		sb.WriteString(fmt.Sprintf("Skill gaps: %s\n", strings.Join(job.GapSkills, ", ")))
	}

	desc := job.Description
	if len(desc) > 1500 {
		desc = desc[:1500]
	}
	sb.WriteString(fmt.Sprintf("Description: %s\n", desc))

	if len(history) > 0 {
		// Cap to last 10 turns to keep prompt size bounded.
		const maxHistory = 10
		start := 0
		if len(history) > maxHistory {
			start = len(history) - maxHistory
		}
		sb.WriteString("\nCONVERSATION HISTORY:\n")
		for _, m := range history[start:] {
			sb.WriteString(fmt.Sprintf("%s: %s\n", strings.ToUpper(m.Role), m.Content))
		}
	}

	sb.WriteString(fmt.Sprintf("\nUSER QUESTION:\n%s\n\n", question))
	sb.WriteString("Answer in Vietnamese or English (match the user's language). Be direct and practical. Max 300 words.")

	return sb.String()
}
