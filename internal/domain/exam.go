package domain

import (
	"time"

	"github.com/google/uuid"
)

type Exam struct {
	ID         uuid.UUID
	Title      string
	Year       int
	Source     string
	Skill      string
	Difficulty string
	CreatedAt  time.Time
}

type Section struct {
	ID        uuid.UUID
	ExamID    uuid.UUID
	Position  int
	Title     string
	Content   string
	AudioURL  string
	CreatedAt time.Time
}

type Question struct {
	ID          uuid.UUID
	SectionID   uuid.UUID
	Position    int
	Type        string
	Prompt      string
	Options     []string
	Correct     string
	Explanation string
	CreatedAt   time.Time
}

type Translation struct {
	ID        uuid.UUID
	SectionID uuid.UUID
	Lang      string
	Content   string
	UpdatedAt time.Time
}
