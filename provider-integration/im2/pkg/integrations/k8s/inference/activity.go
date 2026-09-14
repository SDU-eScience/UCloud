package inference

// Inference user activity tracking
// =====================================================================================================================
// This file tracks when each user first and last used the inference API. A use is a successful chat, responses or
// playground call. Playground calls reach the audit path without HTTP middleware, so the shared audit emit point is the
// one hook that observes every qualifying call.
//
// Updates go to an in-memory map first. A background loop flushes the map to inference_user_activity with an upsert
// that keeps the earliest first_used_at and the latest last_used_at. A restart loses only the debounced window.
//
// The user counters read from the database on each Prometheus scrape. The total counts all rows. The daily counter
// counts rows used since the start of the local calendar day of the provider process.

import (
	"sync"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	db "ucloud.dk/shared/pkg/database"
)

const inferenceActivityFlushInterval = 10 * time.Second

var metricInferenceUsersTotal = promauto.NewCounterFunc(prometheus.CounterOpts{
	Namespace: "ucloud_im",
	Subsystem: "inference",
	Name:      "users_total",
	Help:      "Total number of users that have ever used the inference API successfully.",
}, func() float64 {
	return float64(inferenceActivityCountTotal())
})

var metricInferenceUsersToday = promauto.NewCounterFunc(prometheus.CounterOpts{
	Namespace: "ucloud_im",
	Subsystem: "inference",
	Name:      "users_today_total",
	Help:      "Number of users that have used the inference API successfully today (local calendar day).",
}, func() float64 {
	return float64(inferenceActivityCountToday())
})

var inferenceActivity struct {
	sync.Mutex
	pending map[string]time.Time
}

type inferenceActivityRow struct {
	Username    string
	FirstUsedAt time.Time
	LastUsedAt  time.Time
}

func inferenceActivityRecord(chainKey string, usedAt time.Time) {
	if chainKey == "" {
		return
	}

	inferenceActivity.Lock()
	defer inferenceActivity.Unlock()

	if inferenceActivity.pending == nil {
		inferenceActivity.pending = make(map[string]time.Time)
	}
	inferenceActivity.pending[chainKey] = usedAt
}

func inferenceActivityFlushLoop() {
	ticker := time.NewTicker(inferenceActivityFlushInterval)
	defer ticker.Stop()
	for range ticker.C {
		inferenceActivityFlush()
	}
}

func inferenceActivityFlush() {
	inferenceActivity.Lock()
	pending := inferenceActivity.pending
	inferenceActivity.pending = nil
	inferenceActivity.Unlock()

	if len(pending) == 0 {
		return
	}

	db.NewTx0(func(tx *db.Transaction) {
		for username, usedAt := range pending {
			db.Exec(
				tx,
				`
					insert into inference_user_activity(username, first_used_at, last_used_at)
					values (:username, :used_at, :used_at)
					on conflict (username) do update set
						first_used_at = least(inference_user_activity.first_used_at, excluded.first_used_at),
						last_used_at = greatest(inference_user_activity.last_used_at, excluded.last_used_at)
				`,
				db.Params{
					"username": username,
					"used_at":  usedAt,
				},
			)
		}
	})
}

func inferenceActivityCountTotal() int64 {
	row, _ := db.NewTx2(func(tx *db.Transaction) (struct{ Count int64 }, bool) {
		return db.Get[struct{ Count int64 }](
			tx,
			`
				select count(*) as count from inference_user_activity
			`,
			db.Params{},
		)
	})
	return row.Count
}

func inferenceActivityCountToday() int64 {
	row, _ := db.NewTx2(func(tx *db.Transaction) (struct{ Count int64 }, bool) {
		return db.Get[struct{ Count int64 }](
			tx,
			`
				select count(*) as count from inference_user_activity
				where last_used_at >= date_trunc('day', now())
			`,
			db.Params{},
		)
	})
	return row.Count
}
