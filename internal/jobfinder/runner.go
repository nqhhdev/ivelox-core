package jobfinder

import (
	"context"
	"fmt"
	"log"
	"sync"

	"github.com/nqhhdev/ivelox-core/internal/jobfinder/dedup"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/fetcher"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/notifier"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/scorer"
)

const scoreThreshold = 60
const maxScorerConcurrency = 5

// Runner orchestrates one full fetch→dedup→score→notify cycle.
type Runner struct {
	fetchers []fetcher.Fetcher
	dedup    *dedup.Repository
	scorer   *scorer.Scorer
	notifier *notifier.Notifier
	onNotify func([]scorer.ScoredJob) // called after notification
}

func NewRunner(
	fetchers []fetcher.Fetcher,
	dedup *dedup.Repository,
	scorer *scorer.Scorer,
	notifier *notifier.Notifier,
	onNotify func([]scorer.ScoredJob),
) *Runner {
	return &Runner{
		fetchers: fetchers,
		dedup:    dedup,
		scorer:   scorer,
		notifier: notifier,
		onNotify: onNotify,
	}
}

// Run executes one full cycle. Safe to call on a ticker.
func (r *Runner) Run(ctx context.Context) {
	log.Println("[jobfinder] run started")

	// 1. Fetch from all sources in parallel
	raw := r.fetchAll(ctx)
	log.Printf("[jobfinder] fetched %d total jobs", len(raw))

	if len(raw) == 0 {
		return
	}

	// 2. Deduplicate against seen_jobs
	urls := make([]string, len(raw))
	byURL := make(map[string]fetcher.RawJob, len(raw))
	for i, j := range raw {
		urls[i] = j.ApplyURL
		byURL[j.ApplyURL] = j
	}

	newURLs, err := r.dedup.FilterNew(ctx, urls)
	if err != nil {
		log.Printf("[jobfinder] dedup error: %v", err)
		return
	}
	log.Printf("[jobfinder] %d new jobs after dedup", len(newURLs))

	if len(newURLs) == 0 {
		return
	}

	newJobs := make([]fetcher.RawJob, 0, len(newURLs))
	for _, u := range newURLs {
		newJobs = append(newJobs, byURL[u])
	}

	// 3. Score with Gemini (bounded concurrency)
	scored := r.scoreAll(ctx, newJobs)
	log.Printf("[jobfinder] %d jobs scored >= %d", len(scored), scoreThreshold)

	if len(scored) == 0 {
		return
	}

	// 4. Notify via Telegram
	r.notifier.Notify(scored)

	// 5. Call onNotify hook if set (registers jobs in bot for chat sessions)
	if r.onNotify != nil {
		r.onNotify(scored)
	}

	// 6. Mark as seen
	entries := make([]dedup.SeenEntry, len(scored))
	for i, sj := range scored {
		entries[i] = dedup.SeenEntry{
			URL:     sj.ApplyURL,
			Title:   sj.Title,
			Company: sj.Company,
			Source:  sj.Source,
			Score:   sj.Score,
		}
	}
	if err := r.dedup.MarkSeen(ctx, entries); err != nil {
		log.Printf("[jobfinder] mark seen error: %v", err)
	}

	// 7. Cleanup old entries (opportunistic — errors are non-fatal)
	_ = r.dedup.Cleanup(ctx)

	log.Printf("[jobfinder] run complete — notified %d jobs", len(scored))
}

// fetchAll runs all fetchers in parallel and merges results.
func (r *Runner) fetchAll(_ context.Context) []fetcher.RawJob {
	var mu sync.Mutex
	var all []fetcher.RawJob
	var wg sync.WaitGroup

	for _, f := range r.fetchers {
		wg.Add(1)
		go func(f fetcher.Fetcher) {
			defer wg.Done()
			jobs, err := f.Fetch()
			if err != nil {
				log.Printf("[jobfinder] fetcher %s error: %v", f.Name(), err)
				return
			}
			mu.Lock()
			all = append(all, jobs...)
			mu.Unlock()
		}(f)
	}
	wg.Wait()
	return all
}

// scoreAll scores jobs with bounded concurrency, returns only those >= threshold.
func (r *Runner) scoreAll(ctx context.Context, jobs []fetcher.RawJob) []scorer.ScoredJob {
	sem := make(chan struct{}, maxScorerConcurrency)
	var mu sync.Mutex
	var scored []scorer.ScoredJob
	var wg sync.WaitGroup

	for _, job := range jobs {
		wg.Add(1)
		go func(j fetcher.RawJob) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()

			sj, err := r.scorer.Score(ctx, j, scoreThreshold)
			if err != nil {
				log.Printf("[jobfinder] score error for %q: %v", j.Title, err)
				return
			}
			if sj == nil {
				return // below threshold
			}
			mu.Lock()
			scored = append(scored, *sj)
			mu.Unlock()
		}(job)
	}
	wg.Wait()
	return scored
}

// RunWithErrorNotify wraps Run and sends a Telegram message if a panic occurs.
func (r *Runner) RunWithErrorNotify(ctx context.Context, sendErr func(string)) {
	defer func() {
		if rec := recover(); rec != nil {
			msg := fmt.Sprintf("❌ Job finder panic: %v", rec)
			log.Println(msg)
			sendErr(msg)
		}
	}()
	r.Run(ctx)
}
