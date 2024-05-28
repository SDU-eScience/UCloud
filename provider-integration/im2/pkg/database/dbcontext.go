package database

import (
	"database/sql"
	"fmt"
	"log"

	"github.com/jmoiron/sqlx"
	_ "github.com/lib/pq"
)

type DBError struct {
	Message string
}

type DBContext struct {
	Connection *sqlx.DB
	Session    *sqlx.Tx
	Ok         bool
	Error      DBError
}

func DBConnect() DBContext {
	db, err := sqlx.Open("postgres", "postgres://postgres:postgrespassword@localhost/postgres")
	if err != nil {
		log.Fatalf("Could not open database %v", err)
	}

	return DBContext{
		Connection: db,
		Ok:         true,
	}
}

func (db *DBContext) OpenSession() {
	session, err := db.Connection.Beginx()

	if err != nil {
		log.Fatalf("Could not open transaction %v", err)
		db.Ok = false
		db.Error = DBError{fmt.Sprintf("Could not open transaction %v", err)}
	}

	db.Session = session
}

func (db *DBContext) Close() error {
	var err error = nil
	if !db.Ok {
		err = db.Session.Rollback()
	} else {
		err = db.Session.Commit()
	}

	if err != nil {
		log.Fatalf("Could not commit %v", err)
	}
	return err
}

func (db *DBContext) Exec(query string, args ...any) {
	_, err := db.Session.Exec(query, args...)

	if err != nil {
		db.Ok = false
		db.Error = DBError{"Database exec failed: " + query}
	}
}

func (db *DBContext) Query(query string, args ...any) *sql.Rows {
	res, err := db.Session.Query(query, args...)

	if err != nil {
		db.Ok = false
		db.Error = DBError{"Database query failed: " + query}
	}

	return res
}

func (db *DBContext) Get(dest interface{}, query string, args ...interface{}) {
	err := db.Session.Get(dest, query, args...)

	if err != nil {
		db.Ok = false
		db.Error = DBError{"Database get failed: " + query}
	}
}

func (db *DBContext) Select(dest interface{}, query string, args ...interface{}) {
	err := db.Session.Select(dest, query, args...)

	if err != nil {
		db.Ok = false
		db.Error = DBError{"Database select failed: " + query}
	}
}
