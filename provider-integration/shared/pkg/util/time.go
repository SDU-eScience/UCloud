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
	return ExponentialBackoffEx(attempt, 50*time.Millisecond, 1.6, 20*time.Second)
}

// ExponentialBackoffEx returns a delay for a given retry attempt.
// attempt is zero-based: 0, 1, 2, ...
func ExponentialBackoffEx(
	attempt int,
	base time.Duration,
	mult float64,
	max time.Duration,
) time.Duration {
	if mult > 2 {
		panic("mult must be <= 2")
	}
	if mult < 1 {
		panic("mult must be >= 1")
	}

	if attempt < 0 {
		attempt = 0
	}

	if attempt > 30 {
		attempt = 30 // Should be enough to ensure that we do not overflow.
	}

	delay := time.Duration(float64(base) * math.Pow(mult, float64(attempt)))
	if delay > max {
		delay = max
	}
	if delay <= 0 {
		return 0
	}

	halfDuration := int64(delay / 2)
	return time.Duration(halfDuration + rand.Int63n(halfDuration+1))
}
