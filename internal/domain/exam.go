package domain

import (
	"time"

	"github.com/google/uuid"
)

type ExamSet struct {
	ID          uuid.UUID
	Title       string
	Series      string  // "Cambridge 18", "IDP RAT Vol.6"
	TestNumber  int
	Year        int
	Source      string  // "cambridge" | "idp" | "british_council" | "mock"
	Difficulty  string
	IsPublished bool
	CreatedAt   time.Time
}

type Exam struct {
	ID         uuid.UUID
	ExamSetID  uuid.UUID
	Title      string
	Year       int
	Source     string
	Skill      string  // "reading" | "listening" | "writing" | "speaking"
	Difficulty string
	CreatedAt  time.Time
}

type Section struct {
	ID        uuid.UUID
	ExamID    uuid.UUID
	Position  int
	Title     string
	AudioURL  string   // kept for backwards compat
	AudioURLs []string // multiple parts (Listening 4 parts)
	ImageURLs []string // diagrams/maps/charts
	CreatedAt time.Time
}

type SectionContent struct {
	SectionID  uuid.UUID
	Content    string // passage text (Reading) or transcript (Listening)
	Transcript string // Listening full transcript
	UpdatedAt  time.Time
}

type Question struct {
	ID             uuid.UUID
	SectionID      uuid.UUID
	Position       int
	Type           string   // "mcq"|"fill_blank"|"true_false"|"matching"|"short_answer"
	Prompt         string
	Options        []string
	Correct        string
	Explanation    string
	ImageURL       string
	AudioTimestamp int // seconds in audio (Listening)
	WordLimit      int // Writing tasks
	CreatedAt      time.Time
}

type Translation struct {
	ID        uuid.UUID
	SectionID uuid.UUID
	Lang      string
	Content   string
	UpdatedAt time.Time
}

type PendingExam struct {
	ID            uuid.UUID
	SourceURL     string
	SourceName    string
	Series        string
	TestNumber    int
	Year          int
	RawData       map[string]any
	AINormalized  map[string]any
	QualityScore  float64
	HasReading    bool
	HasListening  bool
	HasWriting    bool
	HasSpeaking   bool
	DuplicateOf   *uuid.UUID
	RejectReason  string
	Status        string // "pending"|"approved"|"rejected"
	TelegramMsgID int64
	ScrapedAt     time.Time
	ReviewedAt    *time.Time
}
