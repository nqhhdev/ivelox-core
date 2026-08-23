package health

import "time"

// DefaultLocation is the civil calendar used for meal-day queries (YYYY-MM-DD).
const DefaultLocation = "Asia/Ho_Chi_Minh"

// Location returns Asia/Ho_Chi_Minh, or a fixed UTC+7 zone if tzdata is missing.
func Location() *time.Location {
	loc, err := time.LoadLocation(DefaultLocation)
	if err != nil {
		return time.FixedZone("ICT", 7*60*60)
	}
	return loc
}

// ParseCivilDate parses YYYY-MM-DD as midnight in DefaultLocation.
func ParseCivilDate(raw string) (time.Time, error) {
	return time.ParseInLocation("2006-01-02", raw, Location())
}

// CivilDayBounds returns [start, end) UTC instants covering the civil day of `day`
// in DefaultLocation. `day` may be in any zone; its calendar date is taken in that location.
func CivilDayBounds(day time.Time) (start, end time.Time) {
	loc := Location()
	y, m, d := day.In(loc).Date()
	startLocal := time.Date(y, m, d, 0, 0, 0, 0, loc)
	return startLocal.UTC(), startLocal.AddDate(0, 0, 1).UTC()
}
