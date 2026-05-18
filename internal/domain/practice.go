package domain

import (
	"time"

	"github.com/google/uuid"
)

type PracticeSession struct {
	ID         uuid.UUID
	UserID     uuid.UUID
	ExamID     uuid.UUID
	Skill      string
	Status     string
	StartedAt  time.Time
	FinishedAt *time.Time
}

type Answer struct {
	ID          uuid.UUID
	SessionID   uuid.UUID
	QuestionID  *uuid.UUID
	UserAnswer  string
	IsCorrect   *bool
	AIScore     *float64
	AIFeedback  string
	Transcript  string
	AudioURL    string
	SubmittedAt time.Time
}
