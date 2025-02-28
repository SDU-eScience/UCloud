package database

import (
	"fmt"
	"reflect"
	"strings"
	"time"

	"github.com/jmoiron/sqlx"
	_ "github.com/lib/pq"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

type Params map[string]any

// Ctx is either a Pool ready to open a transaction or an already open Transaction. Use this type to indicate that you
// do not have strong requirements on the transaction. Use Pool if you must control the transaction. Use Transaction
// if it must already be open (and have the transaction controlled elsewhere).
type Ctx interface {
	open() *Transaction
}

type Pool struct {
	Connection *sqlx.DB
}

var Database *Pool = nil

func (ctx *Pool) open() *Transaction {
	for i := 0; i < 10; i++ {
		tx, err := ctx.Connection.Beginx()
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
	return ContinueTx(Database, fn)
}

func ContinueTx[T any](ctx Ctx, fn func(tx *Transaction) T) T {
	// TODO(Dan): Not sure this works if ctx is not Database

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
			_ = tx.tx.Rollback()
			if tx.didConsumeError {
				return result
			} else {
				errorLog = append(errorLog, tx.error.Error())
			}
		} else {
			err := tx.tx.Commit()
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

func ContinueTx0(ctx Ctx, fn func(tx *Transaction)) {
	ContinueTx(ctx, func(tx *Transaction) util.Empty {
		fn(tx)
		return util.Empty{}
	})
}

func ContinueTx2[A, B any](ctx Ctx, fn func(tx *Transaction) (A, B)) (A, B) {
	t := ContinueTx(ctx, func(tx *Transaction) util.Tuple2[A, B] {
		a, b := fn(tx)
		return util.Tuple2[A, B]{a, b}
	})
	return t.First, t.Second
}

func ContinueTx3[A, B, C any](ctx Ctx, fn func(tx *Transaction) (A, B, C)) (A, B, C) {
	t := ContinueTx(ctx, func(tx *Transaction) util.Tuple3[A, B, C] {
		a, b, c := fn(tx)
		return util.Tuple3[A, B, C]{a, b, c}
	})
	return t.First, t.Second, t.Third
}

type Transaction struct {
	tx              *sqlx.Tx
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

	db := sqlx.MustOpen(
		"postgres",
		fmt.Sprintf(
			"postgres://%v:%v@%v:%v/%v?sslmode=%v",
			username,
			password,
			host,
			port,
			database,
			sslMode,
		),
	)

	return &Pool{
		Connection: db,
	}
}

func Exec(ctx *Transaction, query string, args Params) {
	_, err := ctx.tx.NamedExec(query, transformParameters(args))
	if err != nil && ctx.Ok {
		ctx.Ok = false
		ctx.error = fmt.Errorf("Database exec failed: %v\nquery: %v\n", err.Error(), query)
	}
}

func Get[T any](ctx *Transaction, query string, args Params) (T, bool) {
	items := Select[T](ctx, query, args)
	if len(items) != 1 {
		var dummy T
		return dummy, false
	}
	return items[0], true
}

func Select[T any](ctx *Transaction, query string, args Params) []T {
	var result []T
	res, err := ctx.tx.NamedQuery(query, transformParameters(args))
	if err != nil && ctx.Ok {
		ctx.Ok = false
		ctx.error = fmt.Errorf("Database select failed: %v\nquery: %v\n", err.Error(), query)
		return nil
	}

	for res.Next() {
		var item T
		err = res.StructScan(&item)

		result = append(result, item)

		if err != nil && ctx.Ok {
			ctx.Ok = false
			ctx.error = fmt.Errorf("Database select failed: %v. Query: %v", err, query)
			return nil
		}
	}

	return result
}

func transformParameters(args Params) Params {
	result := Params{}
	for k, v := range args {
		result[k] = transformParameter(v)
	}
	return result
}

func transformParameter(param any) any {
	// NOTE(Dan): Transform slices into Postgres' array literal syntax. We could try and use the sqlx.In function, but
	// it seems like that would require us to use a different binding syntax which I would like to avoid. Either way,
	// this is the type of transformation that needs to take place at some point. We might as well do it here, since
	// it is not exactly a complicated transform. There should be no risk involved in doing this since we are still
	// binding everything into a prepared query.
	if reflect.TypeOf(param).Kind() == reflect.Slice || reflect.TypeOf(param).Kind() == reflect.Array {
		builder := strings.Builder{}
		builder.WriteString("{")

		v := reflect.ValueOf(param)
		l := v.Len()
		for i := 0; i < l; i++ {
			elem := v.Index(i)
			if i > 0 {
				builder.WriteString(",")
			}

			builder.WriteString(fmt.Sprint(elem))
		}
		builder.WriteString("}")
		return builder.String()
	}
	return param
}
