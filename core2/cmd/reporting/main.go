package main

import (
	"flag"
	"fmt"
	"os"
	"strconv"
	"time"
	acc "ucloud.dk/core/pkg/accounting/reporting"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/util"
)

func main() {
	dumpFlag := flag.Bool("dump-only", false, "only produce a raw data dump, do not process")
	noDump := flag.Bool("no-dump", false, "skip dump and use already dumped data")
	start := flag.String("start", "", "start time")
	end := flag.String("end", "", "end time")
	provider := flag.String("provider", "", "provider")
	dataDir := flag.String("data-dir", ".", "directory used to store data")
	flag.Parse()

	if !flag.Parsed() {
		flag.PrintDefaults()
		return
	}

	startTime, err1 := time.Parse(time.DateOnly, *start)
	endTime, err2 := time.Parse(time.DateOnly, *end)

	if err1 != nil || err2 != nil {
		fmt.Printf("invalid dates: err1=%s err2=%s\n", err1, err2)
		return
	}

	if *provider == "" {
		fmt.Printf("no provider specified")
		return
	}

	dbHost := os.Getenv("DB_HOST")
	dbPort := os.Getenv("DB_PORT")
	dbUser := os.Getenv("DB_USER")
	dbPassword := os.Getenv("DB_PASSWORD")
	dbDatabase := os.Getenv("DB_DATABASE")
	dbSsl := os.Getenv("DB_SSL")

	if dbPort == "" {
		dbPort = "5432"
	}

	dbPortParsed, err := strconv.ParseInt(dbPort, 10, 64)
	if err != nil {
		fmt.Printf("missing db port")
		return
	}

	if dbHost == "" || dbUser == "" || dbPassword == "" || dbDatabase == "" {
		fmt.Printf("missing db")
		return
	}

	db.Database = db.Connect(
		dbUser,
		dbPassword,
		dbHost,
		int(dbPortParsed),
		dbDatabase,
		dbSsl == "true",
	)
	db.Database.Connection.MapperFunc(util.ToSnakeCase)

	acc.RunReporting(*provider, startTime, endTime, *dumpFlag, *noDump, *dataDir)
}
