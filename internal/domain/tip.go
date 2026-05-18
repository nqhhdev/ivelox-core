package domain

import (
	"time"

	"github.com/google/uuid"
)

type Tip struct {
	ID        uuid.UUID
	Skill     string
	Title     string
	Content   string
	BandRange string
	CreatedAt time.Time
}
