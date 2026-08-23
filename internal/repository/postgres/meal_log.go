package postgres

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/nqhhdev/ivelox-core/internal/domain"
)

type MealLogRepository struct {
	db *pgxpool.Pool
}

func NewMealLogRepository(db *pgxpool.Pool) *MealLogRepository {
	return &MealLogRepository{db: db}
}

var _ domain.MealLogRepository = (*MealLogRepository)(nil)

const mealLogColumns = `id, user_id, food_cache_id, raw_input, image_url, quantity, unit,
	kcal, protein_g, carb_g, fat_g, meal_type, logged_at`

func utcDayBounds(day time.Time) (start, end time.Time) {
	y, m, d := day.UTC().Date()
	start = time.Date(y, m, d, 0, 0, 0, 0, time.UTC)
	end = start.AddDate(0, 0, 1)
	return start, end
}

func (r *MealLogRepository) Create(ctx context.Context, m *domain.MealLog) error {
	loggedAt := m.LoggedAt
	if loggedAt.IsZero() {
		loggedAt = time.Now().UTC()
	}
	unit := string(m.Unit)
	err := r.db.QueryRow(ctx,
		`insert into public.meal_logs (
			user_id, food_cache_id, raw_input, image_url, quantity, unit,
			kcal, protein_g, carb_g, fat_g, meal_type, logged_at
		) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
		returning id, logged_at`,
		m.UserID, m.FoodCacheID, m.RawInput, m.ImageURL, m.Quantity, unit,
		m.Kcal, m.ProteinG, m.CarbG, m.FatG, m.MealType, loggedAt,
	).Scan(&m.ID, &m.LoggedAt)
	if err != nil {
		return fmt.Errorf("insert meal log: %w", err)
	}
	return nil
}

func (r *MealLogRepository) ListByUserDate(ctx context.Context, userID uuid.UUID, day time.Time) ([]domain.MealLog, error) {
	start, end := utcDayBounds(day)
	rows, err := r.db.Query(ctx,
		`select `+mealLogColumns+`
		 from public.meal_logs
		 where user_id = $1 and logged_at >= $2 and logged_at < $3
		 order by logged_at desc`,
		userID, start, end,
	)
	if err != nil {
		return nil, fmt.Errorf("list meal logs by user date: %w", err)
	}
	defer rows.Close()

	var out []domain.MealLog
	for rows.Next() {
		var m domain.MealLog
		var unit string
		if err := rows.Scan(
			&m.ID, &m.UserID, &m.FoodCacheID, &m.RawInput, &m.ImageURL, &m.Quantity, &unit,
			&m.Kcal, &m.ProteinG, &m.CarbG, &m.FatG, &m.MealType, &m.LoggedAt,
		); err != nil {
			return nil, fmt.Errorf("scan meal log: %w", err)
		}
		m.Unit = domain.FoodUnit(unit)
		out = append(out, m)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate meal logs: %w", err)
	}
	if out == nil {
		out = []domain.MealLog{}
	}
	return out, nil
}

func (r *MealLogRepository) Delete(ctx context.Context, userID, id uuid.UUID) error {
	tag, err := r.db.Exec(ctx,
		`delete from public.meal_logs where id = $1 and user_id = $2`,
		id, userID,
	)
	if err != nil {
		return fmt.Errorf("delete meal log: %w", err)
	}
	if tag.RowsAffected() == 0 {
		return fmt.Errorf("meal log not found")
	}
	return nil
}

func (r *MealLogRepository) SummarizeDay(ctx context.Context, userID uuid.UUID, day time.Time) (*domain.DayMealSummary, error) {
	start, end := utcDayBounds(day)
	var s domain.DayMealSummary
	err := r.db.QueryRow(ctx,
		`select coalesce(sum(kcal),0), coalesce(sum(protein_g),0), coalesce(sum(carb_g),0),
		        coalesce(sum(fat_g),0), count(*)::int
		 from public.meal_logs
		 where user_id = $1 and logged_at >= $2 and logged_at < $3`,
		userID, start, end,
	).Scan(&s.EatenKcal, &s.ProteinG, &s.CarbG, &s.FatG, &s.MealCount)
	if err != nil {
		return nil, fmt.Errorf("summarize meal day: %w", err)
	}
	return &s, nil
}
