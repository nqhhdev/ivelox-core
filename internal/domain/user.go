package domain

import (
	"time"

	"github.com/google/uuid"
)

type User struct {
	ID          uuid.UUID
	Email       string
	DisplayName string
	AvatarURL   string
	Provider    string // 'email' | 'google'
	Role        string // 'user' | 'admin'
	CreatedAt   time.Time
	UpdatedAt   time.Time
}

type UserGoal struct {
	ID         uuid.UUID
	UserID     uuid.UUID
	Skill      string // 'reading' | 'writing' | 'listening' | 'speaking'
	TargetBand float64
	TargetDate *time.Time
	CreatedAt  time.Time
	UpdatedAt  time.Time
}

// UserLevel holds the current computed band score per skill.
// Seeded from onboarding quick test, updated after each practice session.
type UserLevel struct {
	ID        uuid.UUID
	UserID    uuid.UUID
	Skill     string
	BandScore float64
	Source    string // 'onboarding' | 'session'
	UpdatedAt time.Time
}

// UserScore is an append-only record per completed session per skill.
// Used for progress charts and trend analysis.
type UserScore struct {
	ID         uuid.UUID
	UserID     uuid.UUID
	SessionID  *uuid.UUID
	Skill      string
	BandScore  float64
	Accuracy   *float64 // 0.0–1.0, applicable for reading/listening
	RecordedAt time.Time
}

// UserStreak tracks daily study for reminder notifications.
type UserStreak struct {
	UserID          uuid.UUID
	CurrentStreak   int
	LongestStreak   int
	LastStudyDate   *time.Time
	LastRemindedAt  *time.Time
	UpdatedAt       time.Time
}

type UserRepository interface {
	GetByID(id uuid.UUID) (*User, error)
	Upsert(u *User) error
}

type UserGoalRepository interface {
	List(userID uuid.UUID) ([]*UserGoal, error)
	Upsert(g *UserGoal) error
	Delete(userID uuid.UUID, skill string) error
}

type UserLevelRepository interface {
	List(userID uuid.UUID) ([]*UserLevel, error)
	Upsert(l *UserLevel) error
}

type UserScoreRepository interface {
	ListBySkill(userID uuid.UUID, skill string) ([]*UserScore, error)
	Create(s *UserScore) error
}

type UserStreakRepository interface {
	Get(userID uuid.UUID) (*UserStreak, error)
	Upsert(s *UserStreak) error
}
