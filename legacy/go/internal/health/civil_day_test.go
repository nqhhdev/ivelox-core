package health_test

import (
	"testing"
	"time"

	"github.com/nqhhdev/ivelox-core/internal/health"
)

func TestCivilDayBounds_ICTMorningMapsToCivilDate(t *testing.T) {
	day, err := health.ParseCivilDate("2026-08-23")
	if err != nil {
		t.Fatalf("ParseCivilDate: %v", err)
	}

	start, end := health.CivilDayBounds(day)
	wantStart := time.Date(2026, 8, 22, 17, 0, 0, 0, time.UTC)
	wantEnd := time.Date(2026, 8, 23, 17, 0, 0, 0, time.UTC)
	if !start.Equal(wantStart) || !end.Equal(wantEnd) {
		t.Fatalf("CivilDayBounds UTC = [%v, %v), want [%v, %v)", start, end, wantStart, wantEnd)
	}

	loc := health.Location()
	morning := time.Date(2026, 8, 23, 1, 0, 0, 0, loc) // 18:00 UTC on 2026-08-22
	if morning.UTC().Before(start) || !morning.UTC().Before(end) {
		t.Fatalf("ICT 01:00 (UTC %v) should be inside civil 2026-08-23 [%v, %v)", morning.UTC(), start, end)
	}

	prevEvening := time.Date(2026, 8, 22, 23, 0, 0, 0, loc)
	if !prevEvening.UTC().Before(start) {
		t.Fatalf("ICT 23:00 previous day (UTC %v) should be before start %v", prevEvening.UTC(), start)
	}

	nextMorning := time.Date(2026, 8, 24, 0, 30, 0, 0, loc)
	if nextMorning.UTC().Before(end) {
		t.Fatalf("ICT 00:30 next day (UTC %v) should be >= end %v", nextMorning.UTC(), end)
	}
}

func TestParseCivilDate_RejectsInvalid(t *testing.T) {
	if _, err := health.ParseCivilDate("23-08-2026"); err == nil {
		t.Fatal("expected parse error")
	}
}
