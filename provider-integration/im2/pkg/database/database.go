package database

import (
	"fmt"
	"github.com/jmoiron/sqlx"
	_ "github.com/lib/pq"
	"os"
	"ucloud.dk/pkg/log"
)

type Params map[string]any

// Ctx is either a Pool ready to open a transaction or an already open Transaction. Use this type to indicate that you
// do not have strong requirements on the transaction. Use Pool if you must control the transaction. Use Transaction
// if it must already be open (and have the transaction controlled elsewhere).
type Ctx interface {
	Open() *Transaction
}

type Pool struct {
	Connection *sqlx.DB
}

var Database *Pool = nil

func (ctx *Pool) Open() *Transaction {
	tx, err := ctx.Connection.Beginx()
	if err != nil {
		log.Error("Failed to open transaction: %v", err)
		os.Exit(1) // TODO worried about this
	}

	return &Transaction{
		tx:    tx,
		depth: 1,
		Ok:    true,
	}
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

func (ctx *Transaction) Open() *Transaction {
	ctx.depth++
	ctx.Ok = true
	return ctx
}

func (ctx *Transaction) CloseAndReturnErr() error {
	ctx.close(false, false)
	return ctx.ConsumeError()
}

func (ctx *Transaction) CloseOrLog() bool {
	ctx.close(false, true)
	return ctx.Ok
}

func (ctx *Transaction) CloseOrPanic() {
	ctx.close(true, true)
}

func (ctx *Transaction) close(doPanic bool, doLog bool) {
	ctx.depth--

	if ctx.depth == 0 {
		var err error = nil
		if ctx.Ok {
			err = ctx.tx.Commit()
		} else {
			err = ctx.tx.Rollback()
			if !ctx.didConsumeError {
				if doLog {
					log.Warn("Error from database: %v", ctx.error.Error())
				}

				if doPanic {
					panic(fmt.Sprintf("Error from database was never consumed! %v", ctx.error.Error()))
				}
			}
		}

		if err != nil {
			log.Error("Failed to close transaction: %v", err)
		}
	}
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
	_, err := ctx.tx.NamedExec(query, args)
	if err != nil {
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
	res, err := ctx.tx.NamedQuery(query, args)
	if err != nil {
		ctx.Ok = false
		ctx.error = fmt.Errorf("Database select failed: %v\nquery: %v\n", err.Error(), query)
		return nil
	}

	for res.Next() {
		var item T
		err = res.StructScan(&item)

		result = append(result, item)

		if err != nil {
			ctx.Ok = false
			ctx.error = fmt.Errorf("Database select failed: %v. Query: %v", err, query)
			return nil
		}
	}

	return result
}
