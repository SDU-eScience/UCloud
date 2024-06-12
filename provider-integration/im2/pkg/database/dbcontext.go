package database

import (
	"database/sql"
	"fmt"

	"github.com/jmoiron/sqlx"
	_ "github.com/lib/pq"
	"ucloud.dk/pkg/log"
)

type DBSession interface {
	Open() *Transaction
	Close()
	Exec(query string, args ...any)
	Query(query string, args ...any) *sql.Rows
	Get(dest interface{}, query string, args ...interface{})
	Select(dest interface{}, query string, args ...interface{})
}

type DBContext interface {
	Open() *Transaction
}

type ReadyToOpenTransaction struct {
	Connection *sqlx.DB
}

func (ctx *ReadyToOpenTransaction) Open() *Transaction {
	tx, _ := ctx.Connection.Beginx()
	return &Transaction{
		Transaction: tx,
		Ok:          true,
	}
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

func (ctx *Transaction) Open() *Transaction {
	ctx.Depth++
	ctx.Ok = true
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
	db, err := sqlx.Open(
		"postgres",
		"user=postgres dbname=postgres password=postgres host=localhost port=5432 sslmode=disable",
	)
	if err != nil {
		log.Fatal("Could not open database %v", err)
	}

	return ReadyToOpenTransaction{
		Connection: db,
	}
}

func (ctx *Transaction) Exec(query string, args ...any) {
	res, err := ctx.Transaction.Exec(query, args...)

	affected, _ := res.RowsAffected()
	fmt.Printf("Exec result: %d\n", affected)

	if err != nil {
		ctx.Ok = false
		ctx.Error = DBError{"Database exec failed: " + err.Error() + "\nquery: " + query}
	}
}

func (ctx *Transaction) NamedExec(query string, arg interface{}) {
	_, err := ctx.Transaction.NamedExec(query, arg)

	if err != nil {
		ctx.Ok = false
		ctx.Error = DBError{"Database exec failed: " + err.Error() + "\nquery: " + query}
	}
}

func (ctx *Transaction) Query(query string, arg interface{}) *sqlx.Rows {
	res, err := ctx.Transaction.Queryx(query, arg)

	if err != nil {
		log.Debug("Something went horribly wrong")
		ctx.Ok = false
		ctx.Error = DBError{"Database query failed: " + err.Error() + "\nquery: " + query}
	}

	return res
}

func (ctx *Transaction) NamedQuery(query string, arg interface{}) *sqlx.Rows {
	res, err := ctx.Transaction.NamedQuery(query, arg)

	if err != nil {
		log.Debug("Something went horribly wrong")
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
