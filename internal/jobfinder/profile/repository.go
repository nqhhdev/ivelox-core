package profile

import (
	"context"
	"fmt"

	"github.com/jackc/pgx/v5/pgxpool"
)

// Profile holds the candidate's job search requirements, stored in job_finder.profile.
type Profile struct {
	Name      string
	Role      string
	Skills    string
	Location  string
	SalaryMin int
	Languages string
	Extra     string
}

// Repository reads and writes the single profile row.
type Repository struct {
	db *pgxpool.Pool
}

func NewRepository(db *pgxpool.Pool) *Repository {
	return &Repository{db: db}
}

// Get returns the current profile. Returns a zero-value Profile if no row exists yet.
func (r *Repository) Get(ctx context.Context) (Profile, error) {
	var p Profile
	err := r.db.QueryRow(ctx, `
		SELECT name, role, skills, location, salary_min, languages, extra
		FROM job_finder.profile WHERE id = 1
	`).Scan(&p.Name, &p.Role, &p.Skills, &p.Location, &p.SalaryMin, &p.Languages, &p.Extra)
	if err != nil {
		return p, fmt.Errorf("profile get: %w", err)
	}
	return p, nil
}

// Update saves changes to one or more fields using COALESCE so nil = keep existing.
func (r *Repository) Update(ctx context.Context, field, value string) error {
	var query string
	switch field {
	case "role":
		query = `UPDATE job_finder.profile SET role = $1, updated_at = now() WHERE id = 1`
	case "skills":
		query = `UPDATE job_finder.profile SET skills = $1, updated_at = now() WHERE id = 1`
	case "location":
		query = `UPDATE job_finder.profile SET location = $1, updated_at = now() WHERE id = 1`
	case "salary_min":
		query = `UPDATE job_finder.profile SET salary_min = $1::int, updated_at = now() WHERE id = 1`
	case "languages":
		query = `UPDATE job_finder.profile SET languages = $1, updated_at = now() WHERE id = 1`
	case "extra":
		query = `UPDATE job_finder.profile SET extra = $1, updated_at = now() WHERE id = 1`
	default:
		return fmt.Errorf("unknown profile field: %s", field)
	}
	_, err := r.db.Exec(ctx, query, value)
	if err != nil {
		return fmt.Errorf("profile update %s: %w", field, err)
	}
	return nil
}

// FormatText returns the profile as a human-readable string for Telegram display.
func (p Profile) FormatText() string {
	return fmt.Sprintf(
		"👤 *Name:* %s\n"+
			"💼 *Role:* %s\n"+
			"🛠 *Skills:* %s\n"+
			"🌏 *Location:* %s\n"+
			"💰 *Min Salary:* $%d/mo\n"+
			"🗣 *Languages:* %s\n"+
			"📝 *Extra:* %s",
		p.Name, p.Role, p.Skills, p.Location, p.SalaryMin, p.Languages, p.Extra,
	)
}

// ToPromptText returns the profile formatted for Gemini prompts.
func (p Profile) ToPromptText() string {
	return fmt.Sprintf(
		"Name: %s\nRole: %s\nSkills: %s\nPreferred location: %s\nMin salary: $%d/mo\nLanguages: %s\nExtra: %s",
		p.Name, p.Role, p.Skills, p.Location, p.SalaryMin, p.Languages, p.Extra,
	)
}
