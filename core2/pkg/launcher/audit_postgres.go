package launcher

import (
	"encoding/json"
	"fmt"
	"sync"
	"sync/atomic"
	"time"

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
		go auditPgLogPartitioner(&wg)
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

			bucket.Count++
		}
		bucket.Mu.Unlock()
	}
}

func auditPgSyncer(batches []*auditPgBucket) {
	for {
		var toSync []*db.Batch
		for _, batch := range batches {
			batch.Mu.Lock()
			if batch.Count > 0 {
				toSync = append(toSync, batch.Batch)

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

		time.Sleep(5 * time.Second)
	}
}

func auditPgLogPartitioner(readyWg *sync.WaitGroup) {
	prevMonth := time.Month(-1)
	prevYear := -1

	for {
		now := time.Now().UTC()
		month := now.Month()
		year := now.Year()

		if month != prevMonth || year != prevYear {
			db.NewTx0(func(tx *db.Transaction) {
				type tableToCreate struct {
					Name  string
					Start time.Time
					End   time.Time
				}

				var tablesToCreate []tableToCreate
				for i := 0; i <= 1; i++ {
					start := now.AddDate(0, i, 0)
					end := now.AddDate(0, i+1, 0)

					tablesToCreate = append(tablesToCreate, tableToCreate{
						Name:  fmt.Sprintf("logs_%d_%d", start.Year(), start.Month()),
						Start: start,
						End:   end,
					})
				}

				var tablesToDrop []string
				for i := 6; i <= 12; i++ {
					prevTime := now.AddDate(0, -i, 0)
					tablesToDrop = append(tablesToDrop, fmt.Sprintf("logs_%d_%d", prevTime.Year(), prevTime.Month()))
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
						db.BatchExec(
							b,
							fmt.Sprintf(
								"create table audit_logs.%s partition of audit_logs.logs for values from ('%s') to ('%s')",
								table.Name,
								fmt.Sprintf("%d-%d-01", table.Start.Year(), table.Start.Month()),
								fmt.Sprintf("%d-%d-01", table.End.Year(), table.End.Month()),
							),
							db.Params{},
						)

						db.BatchExec(
							b,
							fmt.Sprintf(`create index on audit_logs.%s (received_at)`, table.Name),
							db.Params{},
						)

						db.BatchExec(
							b,
							fmt.Sprintf(`create index on audit_logs.%s (request_name, received_at desc)`, table.Name),
							db.Params{},
						)

						db.BatchExec(
							b,
							fmt.Sprintf(`create index on audit_logs.%s (username, received_at desc)`, table.Name),
							db.Params{},
						)

						db.BatchExec(
							b,
							fmt.Sprintf(`create index on audit_logs.%s (response_code, received_at desc)`, table.Name),
							db.Params{},
						)

						db.BatchSend(b)
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
		}

		if prevYear == -1 {
			readyWg.Done()
		}

		prevMonth = month
		prevYear = year
		time.Sleep(60 * time.Minute)
	}
}
