package dedup

import (
	"context"
	"crypto/md5"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

// Repository manages the seen_jobs deduplication table.
type Repository struct {
	db *pgxpool.Pool
}

func NewRepository(db *pgxpool.Pool) *Repository {
	return &Repository{db: db}
}

// Hash returns the md5 hex of a URL — used as the primary key.
func Hash(url string) string {
	return fmt.Sprintf("%x", md5.Sum([]byte(url)))
}

// FilterNew returns only the URLs (from input) not yet in seen_jobs.
func (r *Repository) FilterNew(ctx context.Context, urls []string) ([]string, error) {
	if len(urls) == 0 {
		return nil, nil
	}

	hashes := make([]string, len(urls))
	hashToURL := make(map[string]string, len(urls))
	for i, u := range urls {
		h := Hash(u)
		hashes[i] = h
		hashToURL[h] = u
	}

	rows, err := r.db.Query(ctx,
		`select url_hash from job_finder.seen_jobs where url_hash = any($1)`,
		hashes,
	)
	if err != nil {
		return nil, fmt.Errorf("dedup query: %w", err)
	}
	defer rows.Close()

	seen := make(map[string]bool)
	for rows.Next() {
		var h string
		if err := rows.Scan(&h); err != nil {
			return nil, err
		}
		seen[h] = true
	}

	var newURLs []string
	for _, u := range urls {
		if !seen[Hash(u)] {
			newURLs = append(newURLs, u)
		}
	}
	return newURLs, nil
}

// MarkSeen inserts a batch of seen jobs. Ignores conflicts (already seen).
func (r *Repository) MarkSeen(ctx context.Context, entries []SeenEntry) error {
	for _, e := range entries {
		_, err := r.db.Exec(ctx,
			`insert into job_finder.seen_jobs (url_hash, title, company, source, score, notified_at)
			 values ($1, $2, $3, $4, $5, $6)
			 on conflict (url_hash) do nothing`,
			Hash(e.URL), e.Title, e.Company, e.Source, e.Score, time.Now(),
		)
		if err != nil {
			return fmt.Errorf("mark seen %s: %w", e.URL, err)
		}
	}
	return nil
}

// SeenEntry is the data needed to record a notified job.
type SeenEntry struct {
	URL     string
	Title   string
	Company string
	Source  string
	Score   int
}

// Cleanup deletes entries older than 30 days.
func (r *Repository) Cleanup(ctx context.Context) error {
	_, err := r.db.Exec(ctx,
		`delete from job_finder.seen_jobs where notified_at < now() - interval '30 days'`,
	)
	return err
}
