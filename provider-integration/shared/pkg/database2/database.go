package database

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
	"reflect"
	"regexp"
	"strings"
	"time"
	"ucloud.dk/pgxscan"
	"unicode"

	"github.com/jackc/pgx/v5"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

// How to insert data
// =====================================================================================================================
// This utility library offers three separate ways of inserting data:
//
// 1. Normal insertions via db.Exec
// 2. Batch insertions via db.NewBatch and db.BatchExec
// 3. Data import via copy (db.ImportViaCopy)
//
// These all do, roughly, the same operation. The general rule-of-thumb is to pick based on the following criteria:
//
// - Less than   5 rows inserted: Normal insertion (db.Exec)
// - Less than 100 rows inserted: Batch insertion (db.NewBatch and db.BatchExec)
// - More than 100 rows inserted: Import via copy (db.ImportViaCopy)
//
// In addition, if the query needs to insert data to multiple tables or if data needs to be combined in a complex way,
// then consider using db.ImportViaCopy regardless of how many rows are being inserted since the overhead is likely
// small enough that the ease of writing the code is largely a bigger win. Especially if combined with batching of
// the data.
//
// NOTE(Dan): From my rough timing, ImportViaCopy adds about 3ms of overhead. On our network the latency to the database
// server is usually around 0.1ms, meaning that batch insertions are usually quite a bit better if we are only inserting
// a small amount of data. But keep in mind that if the writes are done in a reasonable way, then this overhead is very
// negligible.

// Data insertion examples
// =====================================================================================================================
/*

db.NewTx0(func(tx *db.Transaction) {
	b := db.BatchNew(tx)

	db.BatchExec(
		b,
		`delete from foobar where true`,
		db.Params{},
	)

	for i := 0; i < rowCount; i++ {
		db.BatchExec(
			b,
			`insert into foobar(hello) values (:hello)`,
			db.Params{"hello": i},
		)
	}

	rowPromise := db.BatchSelect[struct{ Hello int }](
		b,
		`select hello from foobar`,
		db.Params{},
	)

	db.BatchSend(b)

	rows := *rowPromise

	sum := 0
	for _, row := range rows {
		sum += row.Hello
	}
})

db.NewTx0(func(tx *db.Transaction) {
	db.Exec(
		tx,
		`delete from foobar where true`,
		db.Params{},
	)

	for i := 0; i < rowCount; i++ {
		db.Exec(
			tx,
			`insert into foobar(hello) values (:hello)`,
			db.Params{"hello": i},
		)
	}

	rows := db.Select[struct{ Hello int }](
		tx,
		`select hello from foobar`,
		db.Params{},
	)

	sum := 0
	for _, row := range rows {
		sum += row.Hello
	}
})

db.NewTx0(func(tx *db.Transaction) {
	var arr []struct{ Hello int }
	for i := 0; i < rowCount; i++ {
		arr = append(arr, struct{ Hello int }{Hello: i})
	}

	db.ImportViaCopy(tx, "data_import", arr)

	rows := db.Select[struct{ Hello int }](
		tx,
		`select hello from data_import`,
		db.Params{},
	)

	sum := 0
	for _, row := range rows {
		sum += row.Hello
	}
})

*/

var (
	metricDatabaseTransactionsInFlight = promauto.NewGaugeVec(prometheus.GaugeOpts{
		Namespace: "ucloud",
		Subsystem: "database",
		Name:      "transactions_in_flight",
		Help:      "Number of database transactions in flight",
	}, []string{"deployment"})

	metricDatabaseTransactionsDuration = promauto.NewSummaryVec(prometheus.SummaryOpts{
		Namespace: "ucloud",
		Subsystem: "database",
		Name:      "transactions_duration_seconds",
		Help:      "Summary of the duration (in seconds) it takes to make database transactions",
		Objectives: map[float64]float64{
			0.5:  0.01,
			0.75: 0.01,
			0.95: 0.01,
			0.99: 0.01,
		},
	}, []string{"deployment"})

	metricDatabaseQueryDuration = promauto.NewSummaryVec(prometheus.SummaryOpts{
		Namespace: "ucloud",
		Subsystem: "database",
		Name:      "query_duration",
		Help:      "Summary of a single query by its source-code origin",
		Objectives: map[float64]float64{
			0.5:  0.01,
			0.75: 0.01,
			0.95: 0.01,
			0.99: 0.01,
		},
	}, []string{"deployment", "file", "line"})
)

type Params map[string]any

// Ctx is either a Pool ready to open a transaction or an already open Transaction. Use this type to indicate that you
// do not have strong requirements on the transaction. Use Pool if you must control the transaction. Use Transaction
// if it must already be open (and have the transaction controlled elsewhere).
type Ctx interface {
	open() *Transaction
}

type Pool struct {
	Connection *pgxpool.Pool
}

var Database *Pool = nil
var DiscardingTest = false

func (ctx *Pool) open() *Transaction {
	for i := 0; i < 10; i++ {
		tx, err := ctx.Connection.Begin(context.Background())
		if err != nil {
			log.Warn("Failed to open transaction: %v", err)
			time.Sleep(2 * time.Second)
			continue
		}

		return &Transaction{
			tx:    tx,
			depth: 1,
			Ok:    true,
		}
	}

	panic("Failed to open transaction after 10 retries. Fatal error!")
}

func NewTx0(fn func(tx *Transaction)) {
	NewTx(func(tx *Transaction) util.Empty {
		fn(tx)
		return util.Empty{}
	})
}

func NewTx2[A, B any](fn func(tx *Transaction) (A, B)) (A, B) {
	t := NewTx(func(tx *Transaction) util.Tuple2[A, B] {
		a, b := fn(tx)
		return util.Tuple2[A, B]{a, b}
	})
	return t.First, t.Second
}

func NewTx3[A, B, C any](fn func(tx *Transaction) (A, B, C)) (A, B, C) {
	t := NewTx(func(tx *Transaction) util.Tuple3[A, B, C] {
		a, b, c := fn(tx)
		return util.Tuple3[A, B, C]{a, b, c}
	})
	return t.First, t.Second, t.Third
}

func NewTx[T any](fn func(tx *Transaction) T) T {
	inFlight := metricDatabaseTransactionsInFlight.WithLabelValues(util.DeploymentName)
	inFlight.Inc()
	start := time.Now()
	result := continueTx(Database, fn)
	inFlight.Dec()
	metricDatabaseTransactionsDuration.WithLabelValues(util.DeploymentName).Observe(float64(time.Now().Sub(start).Seconds()))
	return result
}

func continueTx[T any](ctx Ctx, fn func(tx *Transaction) T) T {
	if ctx == nil {
		if !DiscardingTest {
			panic("no database")
		} else {
			var t T
			return t
		}
	}

	var errorLog []string
	devMode := util.DevelopmentModeEnabled()
	for i := 0; i < 10; i++ {
		tx := ctx.open()
		result := fn(tx)

		devModeRetry := devMode && i == 0 && tx.Ok
		if devModeRetry {
			tx.Ok = false
			tx.didConsumeError = false
			tx.error = fmt.Errorf("first run is always retried to ensure idempotency in dev mode")
		}

		if !tx.Ok {
			_ = tx.tx.Rollback(context.Background())
			if tx.didConsumeError {
				return result
			} else {
				errorLog = append(errorLog, tx.error.Error())
			}
		} else {
			err := tx.tx.Commit(context.Background())
			if err == nil {
				return result
			} else {
				tx.error = err
			}
		}

		if !devModeRetry {
			log.Warn("Database transaction has failed! Automatically retrying in two seconds... %v", tx.error.Error())
			time.Sleep(2 * time.Second)
		}
	}

	panic(
		fmt.Sprintf(
			"Unable to complete database transaction after 10 retries. Fatal failure!\n%v",
			strings.Join(errorLog, "\n"),
		),
	)
}

type Transaction struct {
	tx              pgx.Tx
	depth           int
	error           error
	didConsumeError bool

	Ok bool
}

func (ctx *Transaction) PeekError() error {
	if ctx.Ok {
		return nil
	}
	return ctx.error
}

func (ctx *Transaction) ConsumeError() error {
	if ctx.Ok {
		return nil
	}

	ctx.didConsumeError = true
	return ctx.error
}

func (ctx *Transaction) open() *Transaction {
	ctx.depth++
	ctx.Ok = true
	return ctx
}

func Connect(username, password, host string, port int, database string, ssl bool) *Pool {
	sslMode := "enable"
	if !ssl {
		sslMode = "disable"
	}

	pool, err := pgxpool.New(
		context.Background(),
		fmt.Sprintf(
			"postgres://%v:%v@%v:%v/%v?sslmode=%v&pool_max_conns=64&pool_max_conn_lifetime=1000000h",
			username,
			password,
			host,
			port,
			database,
			sslMode,
		),
	)

	if err != nil {
		panic(fmt.Sprintf("Could not open database connection: %s", err))
	}

	pgxscan.SetColumnRenameFunction(util.ToSnakeCase)

	return &Pool{
		Connection: pool,
	}
}

func RequestRollback(ctx *Transaction) {
	if ctx.Ok {
		ctx.Ok = false
		ctx.error = nil
		ctx.didConsumeError = true
	}
}

func Exec(ctx *Transaction, query string, args Params) {
	caller := util.GetCaller()
	start := time.Now()

	statement, parameters, err := transformQuery(query, args)
	if err != nil {
		ctx.Ok = false
		ctx.error = err
		return
	}
	_, err = ctx.tx.Exec(context.Background(), statement, parameters...)
	if err != nil && ctx.Ok {
		ctx.Ok = false
		ctx.error = fmt.Errorf("Database exec failed: %v\nquery: %v\nreal query: %v\nArgs: %#v\nTransformed: %#v\n", err.Error(), query, statement, args, parameters)
	}
	end := time.Now()
	metricDatabaseQueryDuration.WithLabelValues(util.DeploymentName, caller.File, fmt.Sprint(caller.Line)).Observe(end.Sub(start).Seconds())
}

func Get[T any](ctx *Transaction, query string, args Params) (T, bool) {
	items := SelectEx[T](util.GetCaller(), ctx, query, args)
	if len(items) != 1 {
		var dummy T
		return dummy, false
	}
	return items[0], true
}

func Select[T any](ctx *Transaction, query string, args Params) []T {
	return SelectEx[T](util.GetCaller(), ctx, query, args)
}

func SelectEx[T any](caller util.FileAndLine, ctx *Transaction, query string, args Params) []T {
	start := time.Now()
	var result []T
	statement, parameters, err := transformQuery(query, args)
	if err != nil {
		ctx.Ok = false
		ctx.error = err
		return nil
	}
	res, err := ctx.tx.Query(context.Background(), statement, parameters...)
	if err != nil {
		if ctx.Ok {
			ctx.Ok = false
			ctx.error = fmt.Errorf("Database select failed: %v\nquery: %v\n", err.Error(), query)
		}
		return nil
	}

	scanner := pgxscan.NewScanner(res, pgxscan.ErrNoRowsQuery(false))
	if err := scanner.Scan(&result); err != nil && ctx.Ok {
		ctx.Ok = false
		ctx.error = fmt.Errorf("Database select failed: %v. Query: %v", err, query)
		return nil
	}

	end := time.Now()
	metricDatabaseQueryDuration.WithLabelValues(util.DeploymentName, caller.File, fmt.Sprint(caller.Line)).Observe(end.Sub(start).Seconds())
	return result
}

var (
	statementInputRegex = regexp.MustCompile("(^|[^:])[?:]([a-zA-Z0-9_]+)")
)

func transformQuery(query string, args Params) (string, []any, error) {
	matches := statementInputRegex.FindAllStringSubmatchIndex(query, -1)

	var prepared []byte
	lastIdx := 0

	nameToPos := make(map[string]int)
	var out []any

	for _, loc := range matches {
		// loc = [mStart, mEnd, g1Start, g1End, g2Start, g2End, ...]
		if len(loc) < 6 || loc[2] < 0 || loc[3] < 0 || loc[4] < 0 || loc[5] < 0 {
			continue
		}

		prepared = append(prepared, query[lastIdx:loc[2]]...)
		prepared = append(prepared, query[loc[2]:loc[3]]...)

		name := query[loc[4]:loc[5]]

		pos, ok := nameToPos[name]
		if !ok {
			pos = len(out) + 1
			nameToPos[name] = pos
			if v, ok := args[name]; ok {
				out = append(out, v)
			} else {
				out = append(out, nil)
			}
		}

		prepared = append(prepared, fmt.Sprintf("$%d", pos)...)

		lastIdx = loc[1]
	}

	var missing []string
	for queryParam, _ := range nameToPos {
		_, ok := args[queryParam]
		if !ok {
			missing = append(missing, queryParam)
		}
	}

	if len(missing) > 0 {
		return "", nil, fmt.Errorf("missing query parameters: %v", missing)
	}

	// Trailing text
	prepared = append(prepared, query[lastIdx:]...)

	return string(prepared), out, nil
}

type Batch struct {
	Tx       *Transaction
	Ok       bool
	Error    error
	delegate *pgx.Batch
}

func BatchNew(ctx *Transaction) *Batch {
	return &Batch{
		Tx:       ctx,
		delegate: &pgx.Batch{},
		Ok:       true,
	}
}

func BatchNewDeferred() *Batch {
	return &Batch{
		delegate: &pgx.Batch{},
		Ok:       true,
	}
}

func BatchExec(batch *Batch, query string, args Params) {
	statement, params, err := transformQuery(query, args)
	if err != nil {
		batch.Ok = false
		batch.Error = err
		return
	}
	promise := batch.delegate.Queue(statement, params...)
	promise.Exec(func(ct pgconn.CommandTag) error {
		// Anything we need to do at all?
		return nil
	})
}

func BatchGet[T any](batch *Batch, query string, args Params) *util.Option[T] {
	var result util.Option[T]
	statement, params, err := transformQuery(query, args)
	if err != nil {
		batch.Ok = false
		batch.Error = err
		return &util.Option[T]{}
	}
	promise := batch.delegate.Queue(statement, params...)
	promise.Query(func(rows pgx.Rows) error {
		var scanned []T
		scanner := pgxscan.NewScanner(rows, pgxscan.ErrNoRowsQuery(false))

		if err := scanner.Scan(&scanned); err != nil && batch.Tx.Ok {
			batch.Tx.Ok = false
			batch.Tx.error = fmt.Errorf("Database select failed: %v. Query: %v", err, query)
			return nil
		}

		if len(scanned) == 1 {
			result.Set(scanned[0])
		}

		return nil
	})
	return &result
}

func BatchSelect[T any](batch *Batch, query string, args Params) *[]T {
	var result []T
	statement, params, err := transformQuery(query, args)
	if err != nil {
		batch.Ok = false
		batch.Error = err
		return &result
	}
	promise := batch.delegate.Queue(statement, params...)
	promise.Query(func(rows pgx.Rows) error {
		scanner := pgxscan.NewScanner(rows, pgxscan.ErrNoRowsQuery(false))

		if err := scanner.Scan(&result); err != nil && batch.Tx.Ok {
			batch.Tx.Ok = false
			batch.Tx.error = fmt.Errorf("Database select failed: %v. Query: %v", err, query)
			return nil
		}

		return nil
	})
	return &result
}

func BatchSendDeferred(tx *Transaction, batch *Batch) {
	batch.Tx = tx
	BatchSend(batch)
}

func BatchSend(batch *Batch) {
	if !batch.Ok {
		batch.Tx.Ok = batch.Ok
		batch.Tx.error = batch.Error
	}

	if batch.Tx == nil {
		log.Fatal("When using BatchNewDeferred you must also use BatchSendDeferred")
	}

	if !batch.Tx.Ok {
		return
	}

	caller := util.GetCaller()
	start := time.Now()

	results := batch.Tx.tx.SendBatch(context.Background(), batch.delegate)
	if err := results.Close(); err != nil && batch.Tx.Ok {
		batch.Tx.Ok = false
		batch.Tx.error = err
	}

	end := time.Now()
	metricDatabaseQueryDuration.WithLabelValues(util.DeploymentName, caller.File, fmt.Sprint(caller.Line)).Observe(end.Sub(start).Seconds())
}

const listenMaxRecentFailures = 10

func Listen(ctx context.Context, channelName string) <-chan string {
	listenQuery, err := safeListenSql(channelName)
	if err != nil {
		panic(err)
	}

	out := make(chan string, 32)

	go func() {
		defer close(out)

		var (
			conn           *pgxpool.Conn
			recentFailures int
			backoffBase    = 100 * time.Millisecond
			maxBackoff     = 5 * time.Second
		)

		connect := func(isReconnect bool) bool {
			for {
				if isReconnect {
					log.Info("db.Listen(%s): reconnecting attempt %v of %v",
						channelName, recentFailures+1, listenMaxRecentFailures)
				}

				c, err := Database.Connection.Acquire(ctx)
				if err == nil {
					if _, err = c.Exec(ctx, listenQuery); err == nil {
						conn = c
						recentFailures = 0
						if isReconnect {
							log.Info("db.Listen(%s): connection re-established!", channelName)
						}
						return true
					}
					c.Release()
				}

				recentFailures++
				if recentFailures >= listenMaxRecentFailures {
					panic(fmt.Sprintf("db.Listen(%s): failed to (re)connect after %d recent attempts",
						channelName, listenMaxRecentFailures))
				}

				backoff := time.Duration(recentFailures*recentFailures) * backoffBase
				if backoff > maxBackoff {
					backoff = maxBackoff
				}

				select {
				case <-time.After(backoff):
				case <-ctx.Done():
					return false
				}
			}
		}

		if !connect(false) {
			return
		}

		defer func() {
			if conn != nil {
				conn.Release()
			}
		}()

		for {
			ntf, err := conn.Conn().WaitForNotification(ctx)
			if err != nil {
				if ctx.Err() != nil {
					return
				}

				conn.Release()
				conn = nil

				if !connect(true) {
					return
				}
				continue
			}

			select {
			case out <- ntf.Payload:
			case <-ctx.Done():
				return
			}
		}
	}()

	return out
}

func safeListenSql(channel string) (string, error) {
	if channel == "" {
		return "", errors.New("channel name is empty")
	}
	if len(channel) > 64 {
		return "", fmt.Errorf("channel name too long (max %d)", 64)
	}

	for i, r := range channel {
		switch {
		case i == 0:
			if !(unicode.IsLetter(r) || r == '_') {
				return "", errors.New("channel must start with a letter or underscore")
			}
		default:
			if !(unicode.IsLetter(r) || unicode.IsDigit(r) || r == '_') {
				return "", errors.New("channel may contain only letters, digits, or underscore")
			}
		}
	}

	return "listen " + channel, nil
}

// Import via copy functionality
// =====================================================================================================================

func ImportViaCopy[T any](ctx *Transaction, tableName string, data []T) int64 {
	if !ctx.Ok {
		return 0
	}

	if tableName == "" {
		panic("tableName is required")
	}

	cols, rowBuilder, err := introspectType[T]()
	if err != nil {
		ctx.Ok = false
		ctx.error = err
		return 0
	}
	if len(cols) == 0 {
		ctx.Ok = false
		ctx.error = fmt.Errorf("type %T has no exported fields", *new(T))
		return 0
	}

	ddl := buildTempTableDdl(tableName, cols)

	Exec(ctx, ddl, Params{})

	if !ctx.Ok {
		return 0
	}

	src := pgx.CopyFromSlice(len(data), func(i int) ([]any, error) {
		return rowBuilder(reflect.ValueOf(data[i]))
	})

	columnNames := make([]string, len(cols))
	for i, c := range cols {
		columnNames[i] = c.Name
	}

	n, err := ctx.tx.CopyFrom(context.Background(), pgx.Identifier{tableName}, columnNames, src)
	if err != nil {
		ctx.Ok = false
		ctx.error = fmt.Errorf("copy from: %w", err)
		return 0
	}

	return n
}

type column struct {
	Name    string // snake_case
	SQLType string // e.g., BIGINT, TEXT, JSONB
	NotNull bool
	enc     func(fieldV reflect.Value) (any, error) // expects the FIELD value (not the parent struct)
}

func introspectType[T any]() ([]column, func(v reflect.Value) ([]any, error), error) {
	var zero T
	rt := reflect.TypeOf(zero)
	for rt.Kind() == reflect.Pointer {
		rt = rt.Elem()
	}
	if rt.Kind() != reflect.Struct {
		return nil, nil, fmt.Errorf("ImportViaCopy expects T to be a struct (or pointer to struct), got %s", rt.Kind())
	}

	var cols []column
	var fieldIndexes [][]int

	for i := 0; i < rt.NumField(); i++ {
		sf := rt.Field(i)
		// Skip unexported fields
		if sf.PkgPath != "" {
			continue
		}
		// Honor explicit JSON "-" (common pattern)
		if tag := sf.Tag.Get("json"); tag == "-" {
			continue
		}

		colName := util.ToSnakeCase(sf.Name)

		sqlType, notNull, enc := encoderForType(sf.Type)
		cols = append(cols, column{
			Name:    colName,
			SQLType: sqlType,
			NotNull: notNull,
			enc:     enc,
		})
		fieldIndexes = append(fieldIndexes, sf.Index)
	}

	if len(cols) == 0 {
		return nil, nil, fmt.Errorf("type %T has no exported fields", *new(T))
	}

	rowBuilder := func(v reflect.Value) ([]any, error) {
		for v.Kind() == reflect.Pointer {
			if v.IsNil() {
				v = reflect.New(v.Type().Elem()).Elem()
				break
			}
			v = v.Elem()
		}

		out := make([]any, len(cols))
		for i := range cols {
			fv := v.FieldByIndex(fieldIndexes[i])
			val, err := cols[i].enc(fv)
			if err != nil {
				return nil, err
			}
			out[i] = val
		}
		return out, nil
	}

	return cols, rowBuilder, nil
}

func encoderForType(t reflect.Type) (string, bool, func(reflect.Value) (any, error)) {
	if t.Kind() == reflect.Pointer {
		elemSQL, _, elemEnc := encoderForType(t.Elem())
		return elemSQL, false, func(fv reflect.Value) (any, error) {
			if fv.IsNil() {
				return nil, nil
			}
			return elemEnc(fv.Elem())
		}
	}

	switch t {
	case reflect.TypeOf(sql.NullString{}):
		return "TEXT", false, func(fv reflect.Value) (any, error) {
			ns := fv.Interface().(sql.NullString)
			if !ns.Valid {
				return nil, nil
			}
			return ns.String, nil
		}
	case reflect.TypeOf(sql.NullInt64{}):
		return "BIGINT", false, func(fv reflect.Value) (any, error) {
			ni := fv.Interface().(sql.NullInt64)
			if !ni.Valid {
				return nil, nil
			}
			return ni.Int64, nil
		}
	case reflect.TypeOf(sql.NullInt32{}):
		return "INTEGER", false, func(fv reflect.Value) (any, error) {
			ni := fv.Interface().(sql.NullInt32)
			if !ni.Valid {
				return nil, nil
			}
			return ni.Int32, nil
		}
	case reflect.TypeOf(sql.NullBool{}):
		return "BOOLEAN", false, func(fv reflect.Value) (any, error) {
			nb := fv.Interface().(sql.NullBool)
			if !nb.Valid {
				return nil, nil
			}
			return nb.Bool, nil
		}
	case reflect.TypeOf(sql.NullFloat64{}):
		return "DOUBLE PRECISION", false, func(fv reflect.Value) (any, error) {
			nf := fv.Interface().(sql.NullFloat64)
			if !nf.Valid {
				return nil, nil
			}
			return nf.Float64, nil
		}
	case reflect.TypeOf(sql.NullTime{}):
		return "TIMESTAMPTZ", false, func(fv reflect.Value) (any, error) {
			nt := fv.Interface().(sql.NullTime)
			if !nt.Valid {
				return nil, nil
			}
			return nt.Time, nil
		}
	}

	switch t.Kind() {
	case reflect.String:
		return "TEXT", true, func(fv reflect.Value) (any, error) { return fv.String(), nil }
	case reflect.Bool:
		return "BOOLEAN", true, func(fv reflect.Value) (any, error) { return fv.Bool(), nil }
	case reflect.Int, reflect.Int32:
		return "INTEGER", true, func(fv reflect.Value) (any, error) { return int32(fv.Int()), nil }
	case reflect.Int16, reflect.Int8:
		return "SMALLINT", true, func(fv reflect.Value) (any, error) { return int16(fv.Int()), nil }
	case reflect.Int64:
		return "BIGINT", true, func(fv reflect.Value) (any, error) { return fv.Int(), nil }
	case reflect.Uint, reflect.Uint32:
		return "BIGINT", true, func(fv reflect.Value) (any, error) { return uint64(fv.Uint()), nil }
	case reflect.Uint16, reflect.Uint8:
		return "INTEGER", true, func(fv reflect.Value) (any, error) { return uint32(fv.Uint()), nil }
	case reflect.Uint64:
		return "NUMERIC(20,0)", true, func(fv reflect.Value) (any, error) { return fv.Uint(), nil }
	case reflect.Float32:
		return "REAL", true, func(fv reflect.Value) (any, error) { return float32(fv.Float()), nil }
	case reflect.Float64:
		return "DOUBLE PRECISION", true, func(fv reflect.Value) (any, error) { return fv.Float(), nil }
	case reflect.Struct:
		if t.AssignableTo(reflect.TypeOf(time.Time{})) {
			return "TIMESTAMPTZ", true, func(fv reflect.Value) (any, error) {
				return fv.Interface().(time.Time), nil
			}
		}
		return "JSONB", true, func(fv reflect.Value) (any, error) { return jsonMarshalOrNil(fv.Interface()) }
	case reflect.Slice:
		if t.Elem().Kind() == reflect.Uint8 {
			return "BYTEA", true, func(fv reflect.Value) (any, error) { return fv.Bytes(), nil }
		}
		return "JSONB", true, func(fv reflect.Value) (any, error) { return jsonMarshalOrNil(fv.Interface()) }
	case reflect.Map:
		return "JSONB", true, func(fv reflect.Value) (any, error) { return jsonMarshalOrNil(fv.Interface()) }
	default:
		return "JSONB", true, func(fv reflect.Value) (any, error) { return jsonMarshalOrNil(fv.Interface()) }
	}
}

func buildTempTableDdl(table string, cols []column) string {
	qTable := pgx.Identifier{table}.Sanitize()
	defs := make([]string, len(cols))
	for i, c := range cols {
		qCol := pgx.Identifier{c.Name}.Sanitize()
		nn := ""
		if c.NotNull {
			nn = " NOT NULL"
		}
		defs[i] = fmt.Sprintf("%s %s%s", qCol, c.SQLType, nn)
	}
	return fmt.Sprintf(`CREATE TEMP TABLE IF NOT EXISTS %s (%s) ON COMMIT DROP;`, qTable, joinWithComma(defs))
}

func jsonMarshalOrNil(v any) (any, error) {
	if isZero(v) {
		return nil, nil
	}
	b, err := json.Marshal(v)
	if err != nil {
		return nil, err
	}
	return b, nil
}

func isZero(v any) bool {
	rv := reflect.ValueOf(v)
	if !rv.IsValid() {
		return true
	}
	switch rv.Kind() {
	case reflect.Map, reflect.Slice, reflect.Array, reflect.String:
		return rv.Len() == 0
	default:
		z := reflect.Zero(rv.Type()).Interface()
		return reflect.DeepEqual(v, z)
	}
}

func joinWithComma(parts []string) string {
	switch len(parts) {
	case 0:
		return ""
	case 1:
		return parts[0]
	default:
		out := parts[0]
		for i := 1; i < len(parts); i++ {
			out += ", " + parts[i]
		}
		return out
	}
}
