package postgres

import (
	"context"
	"errors"
	"fmt"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/nqhhdev/ivelox-core/internal/domain"
)

type UserRepository struct {
	db *pgxpool.Pool
}

func NewUserRepository(db *pgxpool.Pool) *UserRepository {
	return &UserRepository{db: db}
}

func (r *UserRepository) GetByID(id uuid.UUID) (*domain.User, error) {
	var u domain.User
	err := r.db.QueryRow(context.Background(),
		`select id, coalesce(email,''), coalesce(display_name,''), coalesce(avatar_url,''), coalesce(provider,'email'), role::text, created_at, updated_at
		 from public.profiles where id = $1`, id,
	).Scan(&u.ID, &u.Email, &u.DisplayName, &u.AvatarURL, &u.Provider, &u.Role, &u.CreatedAt, &u.UpdatedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, fmt.Errorf("user not found: %w", err)
	}
	if err != nil {
		return nil, fmt.Errorf("query user by id: %w", err)
	}
	return &u, nil
}

func (r *UserRepository) Upsert(u *domain.User) error {
	_, err := r.db.Exec(context.Background(),
		`insert into public.profiles (id, email, display_name, avatar_url, provider, updated_at)
		 values ($1, $2, $3, $4, $5, now())
		 on conflict (id) do update set
		   email        = excluded.email,
		   display_name = excluded.display_name,
		   avatar_url   = excluded.avatar_url,
		   provider     = excluded.provider,
		   updated_at   = now()`,
		u.ID, u.Email, u.DisplayName, u.AvatarURL, u.Provider,
	)
	if err != nil {
		return fmt.Errorf("upsert user: %w", err)
	}
	return nil
}
