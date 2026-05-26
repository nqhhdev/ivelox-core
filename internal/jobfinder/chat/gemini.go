package chat

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/google/generative-ai-go/genai"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/scorer"
	"google.golang.org/api/option"
)

const chatCandidateProfile = `
Name: Nguyen Quang Huy
Role: Mobile Software Engineer / Flutter Developer
Experience: 6+ years Flutter, Swift (6 months), Dart
Architecture: Clean Architecture, MVVM, MVC, MVP
Frameworks: Bloc, Riverpod, GetIt, Hive, Dio, GoRouter, Firebase, Background tasks, Isolates
iOS native: CoreData, MapKit, SwiftUI, APN Notifications, NSE
CI/CD: GitLab CI, Fastlane, GitHub Actions
Agile: Scrum Master, PO experience
Release manager: iOS, Android, Huawei AppGallery
Web3: MetaMask, WalletConnect, SubWallet
Preferred: Remote / Hybrid / Part-time (job2)
`

// Handler answers questions about a specific job in a multi-turn conversation.
type Handler struct {
	client *genai.Client
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

// Reply generates an AI response for the given question in the context of a job session.
func (h *Handler) Reply(ctx context.Context, sess *Session, question string) (string, error) {
	prompt := buildChatPrompt(sess.Job, sess.History, question)

	model := h.client.GenerativeModel("gemini-2.0-flash")
	model.SetTemperature(0.7)

	ctx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()

	resp, err := model.GenerateContent(ctx, genai.Text(prompt))
	if err != nil {
		return "", fmt.Errorf("gemini chat: %w", err)
	}

	if len(resp.Candidates) == 0 || len(resp.Candidates[0].Content.Parts) == 0 {
		return "", fmt.Errorf("gemini empty response")
	}

	return fmt.Sprintf("%v", resp.Candidates[0].Content.Parts[0]), nil
}

func buildChatPrompt(job scorer.ScoredJob, history []Message, question string) string {
	var sb strings.Builder

	sb.WriteString("You are a career advisor helping a mobile software engineer evaluate a job opportunity.\n\n")
	sb.WriteString("CANDIDATE PROFILE:\n")
	sb.WriteString(chatCandidateProfile)
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
		sb.WriteString("\nCONVERSATION HISTORY:\n")
		for _, m := range history {
			sb.WriteString(fmt.Sprintf("%s: %s\n", strings.ToUpper(m.Role), m.Content))
		}
	}

	sb.WriteString(fmt.Sprintf("\nUSER QUESTION:\n%s\n\n", question))
	sb.WriteString("Answer in Vietnamese or English (match the user's language). Be direct and practical. Max 300 words.")

	return sb.String()
}
