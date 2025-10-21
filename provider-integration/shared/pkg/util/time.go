package util

import "time"

// StartOfDayUTC returns midnight UTC for the UTC date containing t.
func StartOfDayUTC(t time.Time) time.Time {
	y, m, d := t.UTC().Date()
	return time.Date(y, m, d, 0, 0, 0, 0, time.UTC)
}
