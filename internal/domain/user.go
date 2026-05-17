package domain

import "github.com/google/uuid"

type User struct {
	ID          uuid.UUID
	Email       string
	DisplayName string
	Role        string
}

type UserRepository interface {
	GetByID(id uuid.UUID) (*User, error)
}
