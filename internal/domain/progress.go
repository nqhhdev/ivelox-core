package domain

import (
	"time"

	"github.com/google/uuid"
)

type ProgressSnapshot struct {
	ID         uuid.UUID
	UserID     uuid.UUID
	Skill      string
	BandScore  float64
	Accuracy   float64
	SnapshotAt time.Time
}
