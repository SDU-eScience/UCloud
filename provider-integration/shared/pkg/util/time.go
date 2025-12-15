package util

import (
	"math"
	"math/rand"
	"time"
)

// StartOfDayUTC returns midnight UTC for the UTC date containing t.
func StartOfDayUTC(t time.Time) time.Time {
	y, m, d := t.UTC().Date()
	return time.Date(y, m, d, 0, 0, 0, 0, time.UTC)
}

func ExponentialBackoffForNetwork(attempt int) time.Duration {
	return ExponentialBackoffEx(attempt, 50*time.Millisecond, 1.6, 10*time.Second)
}

// ExponentialBackoffEx returns a delay for a given retry attempt.
// attempt is zero-based: 0, 1, 2, ...
func ExponentialBackoffEx(
	attempt int,
	base time.Duration,
	mult float64,
	max time.Duration,
) time.Duration {
	if attempt < 0 {
		attempt = 0
	}

	delay := time.Duration(float64(base) * math.Pow(mult, float64(attempt)))
	if delay > max {
		delay = max
	}
	if delay <= 0 {
		return 0
	}

	return time.Duration(rand.Int63n(int64(delay) + 1))
}
