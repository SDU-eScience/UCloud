package database

import (
	"database/sql"

	"github.com/jmoiron/sqlx"
	_ "github.com/lib/pq"
	"ucloud.dk/pkg/log"
)

type DBSession interface {
	Open() DBSession
	Close()
	Exec(query string, args ...any)
	Query(query string, args ...any) *sql.Rows
	Get(dest interface{}, query string, args ...interface{})
	Select(dest interface{}, query string, args ...interface{})
}

type DBContext interface {
	Open() DBSession
}

type ReadyToOpenTransaction struct {
	Connection *sqlx.DB
}

func (ctx *ReadyToOpenTransaction) Open() DBSession {
	tx, _ := ctx.Connection.Beginx()
	return &Transaction{Transaction: tx}
}

type Transaction struct {
	Transaction *sqlx.Tx
	Ok          bool
	Depth       int
	Error       DBError
}

type DBError struct {
	Message string
}

func (ctx *Transaction) Open() DBSession {
	ctx.Depth++
	return ctx
}

func (ctx *Transaction) Close() {
	ctx.Depth--

	if ctx.Depth == 0 {
		var err error = nil
		if ctx.Ok {
			err = ctx.Transaction.Commit()
		} else {
			err = ctx.Transaction.Rollback()
		}

		if err != nil {
			log.Error("Failed to close transaction: %v", err)
		}
	}
}

func Connect() ReadyToOpenTransaction {
	db, err := sqlx.Open("postgres", "postgres://postgres:postgres@localhost/postgres")
	if err != nil {
		log.Error("Could not open database %v", err)
	}

	return ReadyToOpenTransaction{
		Connection: db,
	}
}

func (ctx *Transaction) Exec(query string, args ...any) {
	_, err := ctx.Transaction.Exec(query, args...)

	if err != nil {
		ctx.Ok = false
		ctx.Error = DBError{"Database exec failed: " + err.Error() + "\nquery: " + query}
	}
}

func (ctx *Transaction) Query(query string, args ...any) *sql.Rows {
	res, err := ctx.Transaction.Query(query, args...)

	if err != nil {
		ctx.Ok = false
		ctx.Error = DBError{"Database query failed: " + err.Error() + "\nquery: " + query}
	}

	return res
}

func (ctx *Transaction) Get(dest interface{}, query string, args ...interface{}) {
	err := ctx.Transaction.Get(dest, query, args...)

	if err != nil {
		ctx.Ok = false
		ctx.Error = DBError{"Database get failed: " + err.Error() + "\nquery: " + query}
	}
}

func (ctx *Transaction) Select(dest interface{}, query string, args ...interface{}) {
	err := ctx.Transaction.Select(dest, query, args...)

	if err != nil {
		ctx.Ok = false
		ctx.Error = DBError{"Database select failed: " + err.Error() + "\nquery: " + query}
	}
}
