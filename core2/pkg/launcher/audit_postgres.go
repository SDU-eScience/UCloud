package launcher

import (
	"encoding/json"
	"fmt"
	"sync"
	"sync/atomic"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	db "ucloud.dk/shared/pkg/database2"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type auditPgBucket struct {
	Mu    sync.Mutex
	Batch *db.Batch
	Count int
}

func initAuditPg() {
	{
		wg := sync.WaitGroup{}
		wg.Add(1)
		go auditPgLogPartitioner(&wg, 7)
		wg.Wait()
	}

	var buckets []*auditPgBucket
	for i := 0; i < 4; i++ {
		buckets = append(buckets, &auditPgBucket{
			Batch: db.BatchNewDeferred(),
		})
	}

	go auditPgSyncer(buckets)

	counter := atomic.Uint64{}

	rpc.AuditConsumer = func(event rpc.HttpCallLogEntry) {
		log.Info("%v/%v %v", event.RequestName, event.ResponseCode, time.Duration(event.ResponseTimeNanos)*time.Nanosecond)

		username := util.OptNone[string]()
		tokenRef := util.OptNone[string]()

		if event.Token.Present {
			username.Set(event.Token.Value.Principal.Username)
			tokenRef = event.Token.Value.PublicSessionReference
		}

		bucket := buckets[int(counter.Add(1))%len(buckets)]
		bucket.Mu.Lock()
		{
			b := bucket.Batch
			db.BatchExec(
				b,
				`
					insert into audit_logs.logs
						(request_name, received_at, request_size, response_code, response_time_nanos, request_body, 
						username, user_agent, token_reference, remote_origin)
					values 
						(:request_name, :received_at, :request_size, :response_code, :response_time_nanos, :request_body, 
						:username, :user_agent, :token_reference, :remote_origin)
			    `,
				db.Params{
					"request_name":        event.RequestName,
					"received_at":         event.ReceivedAt,
					"request_size":        event.RequestSize,
					"response_code":       event.ResponseCode,
					"response_time_nanos": event.ResponseTimeNanos,
					"request_body":        string(event.RequestJson.GetOrDefault(json.RawMessage("{}"))),
					"username":            username.Sql(),
					"user_agent":          event.UserAgent.Sql(),
					"token_reference":     tokenRef.Sql(),
					"remote_origin":       event.RemoteOrigin,
				},
			)

			auditPgEventsIngested.WithLabelValues(util.DeploymentName).Inc()
			auditPgEventsBuffered.WithLabelValues(util.DeploymentName).Inc()
			bucket.Count++
		}
		bucket.Mu.Unlock()
	}
}

func auditPgSyncer(batches []*auditPgBucket) {
	for {
		start := time.Now()

		totalCount := 0
		var toSync []*db.Batch
		for _, batch := range batches {
			batch.Mu.Lock()
			if batch.Count > 0 {
				toSync = append(toSync, batch.Batch)

				totalCount += batch.Count
				batch.Count = 0
				batch.Batch = db.BatchNewDeferred()
			}
			batch.Mu.Unlock()
		}

		if len(toSync) > 0 {
			db.NewTx0(func(tx *db.Transaction) {
				for _, batch := range toSync {
					db.BatchSendDeferred(tx, batch)
				}
			})
		}

		end := time.Now()
		auditPgFlushes.WithLabelValues(util.DeploymentName).Inc()
		auditPgFlushDuration.WithLabelValues(util.DeploymentName).Observe(end.Sub(start).Seconds())
		auditPgFlushedInTotal.WithLabelValues(util.DeploymentName).Add(float64(totalCount))
		auditPgEventsBuffered.WithLabelValues(util.DeploymentName).Sub(float64(totalCount))

		time.Sleep(5 * time.Second)
	}
}

func auditPgLogPartitioner(readyWg *sync.WaitGroup, partitionSizeDays int) {
	// Fixed epoch so partition boundaries are stable over time.
	epoch := time.Date(2020, 1, 1, 0, 0, 0, 0, time.UTC)
	prevPartitionIndex := int64(-1)

	for {
		now := time.Now().UTC()
		daysSinceEpoch := int64(now.Sub(epoch) / (24 * time.Hour))
		if daysSinceEpoch < 0 {
			daysSinceEpoch = 0
		}

		partitionIndex := daysSinceEpoch / int64(partitionSizeDays)

		if partitionIndex != prevPartitionIndex {
			db.NewTx0(func(tx *db.Transaction) {
				type tableToCreate struct {
					Name  string
					Start time.Time
					End   time.Time
				}

				var tablesToCreate []tableToCreate
				for i := int64(0); i <= 1; i++ {
					idx := partitionIndex + i
					start := epoch.Add(time.Duration(idx*int64(partitionSizeDays)) * 24 * time.Hour)
					end := start.Add(time.Duration(partitionSizeDays) * 24 * time.Hour)

					tablesToCreate = append(tablesToCreate, tableToCreate{
						Name:  fmt.Sprintf("logs_%d_%02d_%02d", start.Year(), start.Month(), start.Day()),
						Start: start,
						End:   end,
					})
				}

				var tablesToDrop []string
				for i := 180 / partitionSizeDays; i <= 365/partitionSizeDays; i++ {
					idx := partitionIndex - int64(i)
					if idx < 0 {
						continue
					}
					start := epoch.Add(time.Duration(idx*int64(partitionSizeDays)) * 24 * time.Hour)
					tablesToDrop = append(tablesToDrop, fmt.Sprintf("logs_%d_%02d_%02d", start.Year(), start.Month(), start.Day()))
				}

				for _, table := range tablesToCreate {
					_, exists := db.Get[struct{ Found int }](
						tx,
						`
							select 1 as found
							from
								pg_catalog.pg_class c
								join pg_catalog.pg_namespace n on n.oid = c.relnamespace
							where 
								n.nspname = 'audit_logs'
								and c.relkind = 'r'
								and c.relname = :table
						`,
						db.Params{
							"table": table.Name,
						},
					)

					if !exists {
						b := db.BatchNew(tx)

						startStr := table.Start.Format("2006-01-02")
						endStr := table.End.Format("2006-01-02")

						db.BatchExec(
							b,
							fmt.Sprintf(
								"create table if not exists audit_logs.%s partition of audit_logs.logs for values from ('%s') to ('%s')",
								table.Name,
								startStr,
								endStr,
							),
							db.Params{},
						)

						db.BatchExec(
							b,
							fmt.Sprintf(`create index if not exists on audit_logs.%s (received_at)`, table.Name),
							db.Params{},
						)

						db.BatchExec(
							b,
							fmt.Sprintf(`create index if not exists on audit_logs.%s (request_name, received_at desc)`, table.Name),
							db.Params{},
						)

						db.BatchExec(
							b,
							fmt.Sprintf(`create index if not exists on audit_logs.%s (username, received_at desc)`, table.Name),
							db.Params{},
						)

						db.BatchExec(
							b,
							fmt.Sprintf(`create index if not exists on audit_logs.%s (response_code, received_at desc)`, table.Name),
							db.Params{},
						)

						db.BatchSend(b)

						log.Info("auditPgLogPartitioner: created partition %s for [%s, %s)", table.Name, startStr, endStr)
					}
				}

				{
					b := db.BatchNew(tx)
					for _, table := range tablesToDrop {
						db.BatchExec(
							b,
							fmt.Sprintf(`drop table if exists audit_logs.%s`, table),
							db.Params{},
						)
					}
					db.BatchSend(b)
				}
			})

			if prevPartitionIndex == -1 {
				// NOTE(Dan): This is required to ensure that the audit system does not start consuming events prior to
				// the log parishioner having had a chance to initialize the current partition. Given that the
				// partitioner will always create one partition ahead, we only consider this a risk at startup.

				log.Info("auditPg is ready!")
				readyWg.Done()
			}
			prevPartitionIndex = partitionIndex
		}

		time.Sleep(60 * time.Minute)
	}
}

// Metrics
// =====================================================================================================================

var (
	auditPgEventsIngested = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud",
		Subsystem: "audit",
		Name:      "events_ingested_total",
		Help:      "Number of audit events accepted from the RPC layer",
	}, []string{"deployment"})

	auditPgEventsBuffered = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Namespace: "ucloud",
		Subsystem: "audit",
		Name:      "events_buffered",
		Help:      "Number of audit events currently in the buffer pending a DB flush",
	}, []string{"deployment"})

	auditPgFlushes = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud",
		Subsystem: "audit",
		Name:      "flushes_total",
		Help:      "Number of times an audit flush has occurred",
	}, []string{"deployment"})

	auditPgFlushedInTotal = promauto.NewCounterVec(prometheus.CounterOpts{
		Namespace: "ucloud",
		Subsystem: "audit",
		Name:      "flushed_events_total",
		Help:      "Number of events that have been flushed successfully",
	}, []string{"deployment"})

	auditPgFlushDuration = promauto.NewSummaryVec(prometheus.SummaryOpts{
		Namespace: "ucloud",
		Subsystem: "audit",
		Name:      "flush_duration_seconds",
		Help:      "Summary of the duration (in seconds) it takes to complete a flush",
		Objectives: map[float64]float64{
			0.5:  0.01,
			0.75: 0.01,
			0.95: 0.01,
			0.99: 0.01,
		},
	}, []string{"deployment"})
)
