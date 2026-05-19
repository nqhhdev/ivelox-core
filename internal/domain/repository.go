package domain

import (
	"time"

	"github.com/google/uuid"
)

type ExamRepository interface {
	List(skill string) ([]*Exam, error)
	GetByID(id uuid.UUID) (*Exam, error)
}

type SectionRepository interface {
	ListByExam(examID uuid.UUID) ([]*Section, error)
	GetByID(id uuid.UUID) (*Section, error)
}

type QuestionRepository interface {
	ListBySection(sectionID uuid.UUID) ([]*Question, error)
	GetByID(id uuid.UUID) (*Question, error)
}

type TranslationRepository interface {
	Get(sectionID uuid.UUID, lang string) (*Translation, error)
	Upsert(t *Translation) error
}

type PracticeSessionRepository interface {
	Create(s *PracticeSession) error
	GetByID(id uuid.UUID) (*PracticeSession, error)
	ListByUser(userID uuid.UUID) ([]*PracticeSession, error)
	Update(s *PracticeSession) error
	Delete(id uuid.UUID) error
}

type AnswerRepository interface {
	Create(a *Answer) error
	ListBySession(sessionID uuid.UUID) ([]*Answer, error)
	Update(a *Answer) error
	Delete(id uuid.UUID) error
}

type ProgressSnapshotRepository interface {
	Create(p *ProgressSnapshot) error
	ListByUser(userID uuid.UUID, skill string) ([]*ProgressSnapshot, error)
}

type TipRepository interface {
	List(skill string) ([]*Tip, error)
	GetByID(id uuid.UUID) (*Tip, error)
}

type ExamSetRepository interface {
	Create(es *ExamSet) error
	GetByID(id uuid.UUID) (*ExamSet, error)
	FindDuplicate(series string, testNumber int) (*ExamSet, error)
}

type SectionContentRepository interface {
	Upsert(sc *SectionContent) error
	GetBySectionID(sectionID uuid.UUID) (*SectionContent, error)
}

type PendingExamRepository interface {
	Save(pe *PendingExam) error
	UpdateStatus(id uuid.UUID, status string, telegramMsgID int64, reviewedAt *time.Time) error
	ListPending() ([]*PendingExam, error)
	GetByID(id uuid.UUID) (*PendingExam, error)
}
