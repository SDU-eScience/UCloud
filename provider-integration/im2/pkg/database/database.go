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

func Exec(ctx *Transaction, query string, args any) {
	_, err := ctx.Transaction.NamedExec(query, args)
	if err != nil {
		ctx.Ok = false
		ctx.Error = DBError{fmt.Sprintf("Database exec failed: %v\nquery: %v\n", err.Error(), query)}
	}
}

func Get[T any](ctx *Transaction, query string, args any) T {
	items := Select[T](ctx, query, args)
	if len(items) != 1 {
		var dummy T
		ctx.Ok = false
		ctx.Error = DBError{fmt.Sprintf("Database returned the wrong amount of results: %v\nquery:%v\n", len(items), query)}
		return dummy
	}
	return items[0]
}

func Select[T any](ctx *Transaction, query string, args any) []T {
	var result []T
	res, err := ctx.Transaction.NamedQuery(query, args)
	if err != nil {
		ctx.Ok = false
		ctx.Error = DBError{fmt.Sprintf("Database select failed: %v\nquery: %v\n", err.Error(), query)}
		return nil
	}

	for res.Next() {
		var item T
		err = res.StructScan(&item)

		result = append(result, item)

		if err != nil {
			ctx.Ok = false
			ctx.Error = DBError{"Database select failed: " + err.Error() + "\nquery: " + query}
			return nil
		}
	}

	return result
}
