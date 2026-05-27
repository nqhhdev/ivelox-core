package chat_test

import (
	"testing"

	"github.com/nqhhdev/ivelox-core/internal/jobfinder/chat"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/fetcher"
	"github.com/nqhhdev/ivelox-core/internal/jobfinder/scorer"
)

func newTestJob() scorer.ScoredJob {
	return scorer.ScoredJob{
		RawJob: fetcher.RawJob{Title: "Flutter Dev", Company: "Grab"},
		Score:  85,
	}
}

func TestStore_StartAndGet(t *testing.T) {
	store := chat.NewStore()
	store.Start(123, newTestJob())

	sess := store.Get(123)
	if sess == nil {
		t.Fatal("expected session, got nil")
	}
	if sess.Job.Title != "Flutter Dev" {
		t.Fatalf("unexpected job title: %s", sess.Job.Title)
	}
}

func TestStore_End(t *testing.T) {
	store := chat.NewStore()
	store.Start(123, newTestJob())
	store.End(123)

	if store.Get(123) != nil {
		t.Fatal("expected nil after End")
	}
}

func TestStore_Append(t *testing.T) {
	store := chat.NewStore()
	store.Start(123, newTestJob())
	store.Append(123, "user", "Am I qualified?")
	store.Append(123, "model", "Yes, you match well.")

	sess := store.Get(123)
	if len(sess.History) != 2 {
		t.Fatalf("expected 2 messages, got %d", len(sess.History))
	}
	if sess.History[0].Role != "user" {
		t.Fatalf("expected user, got %s", sess.History[0].Role)
	}
}

func TestStore_GetNonExistent(t *testing.T) {
	store := chat.NewStore()
	if store.Get(999) != nil {
		t.Fatal("expected nil for unknown chatID")
	}
}

func TestStore_ReplaceSession(t *testing.T) {
	store := chat.NewStore()
	job1 := newTestJob()
	job2 := scorer.ScoredJob{RawJob: fetcher.RawJob{Title: "iOS Dev", Company: "Apple"}, Score: 90}

	store.Start(123, job1)
	store.Start(123, job2) // replace

	sess := store.Get(123)
	if sess.Job.Title != "iOS Dev" {
		t.Fatalf("expected replaced session, got %s", sess.Job.Title)
	}
	if len(sess.History) != 0 {
		t.Fatal("replaced session should have empty history")
	}
}
