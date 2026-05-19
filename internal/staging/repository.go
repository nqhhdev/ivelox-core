package staging

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/nqhhdev/ivelox-core/internal/domain"
)

type Repository struct {
	db *pgxpool.Pool
}

func NewRepository(db *pgxpool.Pool) *Repository {
	return &Repository{db: db}
}

func (r *Repository) Save(pe *domain.PendingExam) error {
	rawJSON, err := json.Marshal(pe.RawData)
	if err != nil {
		return fmt.Errorf("marshal raw_data: %w", err)
	}
	aiJSON, err := json.Marshal(pe.AINormalized)
	if err != nil {
		return fmt.Errorf("marshal ai_normalized: %w", err)
	}

	_, err = r.db.Exec(context.Background(), `
		insert into public.pending_exams
		  (id, source_url, source_name, series, test_number, year,
		   raw_data, ai_normalized, quality_score,
		   has_reading, has_listening, has_writing, has_speaking,
		   duplicate_of, status, scraped_at)
		values ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16)`,
		pe.ID, pe.SourceURL, pe.SourceName, pe.Series, pe.TestNumber, pe.Year,
		rawJSON, aiJSON, pe.QualityScore,
		pe.HasReading, pe.HasListening, pe.HasWriting, pe.HasSpeaking,
		pe.DuplicateOf, pe.Status, pe.ScrapedAt,
	)
	if err != nil {
		return fmt.Errorf("insert pending_exam: %w", err)
	}
	return nil
}

func (r *Repository) UpdateStatus(id uuid.UUID, status string, telegramMsgID int64, reviewedAt *time.Time) error {
	_, err := r.db.Exec(context.Background(), `
		update public.pending_exams
		set status = $2, telegram_msg_id = $3, reviewed_at = $4
		where id = $1`,
		id, status, telegramMsgID, reviewedAt,
	)
	if err != nil {
		return fmt.Errorf("update pending_exam status: %w", err)
	}
	return nil
}

func (r *Repository) ListPending() ([]*domain.PendingExam, error) {
	rows, err := r.db.Query(context.Background(), `
		select id, source_url, source_name, series, test_number, year,
		       raw_data, ai_normalized, quality_score,
		       has_reading, has_listening, has_writing, has_speaking,
		       status, scraped_at
		from public.pending_exams
		where status = 'pending'
		order by scraped_at desc`)
	if err != nil {
		return nil, fmt.Errorf("query pending_exams: %w", err)
	}
	defer rows.Close()

	var result []*domain.PendingExam
	for rows.Next() {
		var pe domain.PendingExam
		var rawJSON, aiJSON []byte
		if err := rows.Scan(
			&pe.ID, &pe.SourceURL, &pe.SourceName, &pe.Series, &pe.TestNumber, &pe.Year,
			&rawJSON, &aiJSON, &pe.QualityScore,
			&pe.HasReading, &pe.HasListening, &pe.HasWriting, &pe.HasSpeaking,
			&pe.Status, &pe.ScrapedAt,
		); err != nil {
			return nil, fmt.Errorf("scan pending_exam: %w", err)
		}
		json.Unmarshal(rawJSON, &pe.RawData)
		json.Unmarshal(aiJSON, &pe.AINormalized)
		result = append(result, &pe)
	}
	return result, nil
}

func (r *Repository) GetByID(id uuid.UUID) (*domain.PendingExam, error) {
	var pe domain.PendingExam
	var rawJSON, aiJSON []byte
	err := r.db.QueryRow(context.Background(), `
		select id, source_url, source_name, series, test_number, year,
		       raw_data, ai_normalized, quality_score,
		       has_reading, has_listening, has_writing, has_speaking,
		       duplicate_of, status, telegram_msg_id, scraped_at, reviewed_at
		from public.pending_exams where id = $1`, id,
	).Scan(
		&pe.ID, &pe.SourceURL, &pe.SourceName, &pe.Series, &pe.TestNumber, &pe.Year,
		&rawJSON, &aiJSON, &pe.QualityScore,
		&pe.HasReading, &pe.HasListening, &pe.HasWriting, &pe.HasSpeaking,
		&pe.DuplicateOf, &pe.Status, &pe.TelegramMsgID, &pe.ScrapedAt, &pe.ReviewedAt,
	)
	if err != nil {
		return nil, fmt.Errorf("get pending_exam by id: %w", err)
	}
	json.Unmarshal(rawJSON, &pe.RawData)
	json.Unmarshal(aiJSON, &pe.AINormalized)
	return &pe, nil
}
