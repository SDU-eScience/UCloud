package migrations

import (
	_ "embed"
	"strings"

	db "ucloud.dk/shared/pkg/database2"
)

//go:embed corev1.sql
var coreV1Sql []byte

func coreV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "coreV1",
		Execute: func(tx *db.Transaction) {
			row, ok := db.Get[struct{ TableExists bool }](
				tx,
				`select to_regclass('task.tasks') is not null as table_exists;`,
				db.Params{},
			)

			isMigratingFromCore1 := ok && row.TableExists

			if isMigratingFromCore1 {
				return
			}

			script := string(coreV1Sql)
			for len(script) > 0 {
				// NOTE(Dan): I have manually added "-- SNIP" around blocks which should not use semicolon as a
				// delimiter between queries. This was much easier than doing correct SQL parsing on the file.

				nextSnip := strings.Index(script, "-- SNIP")
				nextSemicolon := strings.Index(script, ";")

				toExecute := ""

				if nextSnip == -1 && nextSemicolon == -1 {
					toExecute = script
					script = ""
				} else if nextSnip != -1 && nextSemicolon != -1 && nextSnip < nextSemicolon {
					script = script[nextSnip:]
					script = script[strings.Index(script, "\n"):]

					idx := strings.Index(script, "-- SNIP")
					toExecute = script[:idx]

					script = script[idx:]
					script = script[strings.Index(script, "\n"):]
				} else {
					toExecute = script[:nextSemicolon]
					script = script[nextSemicolon+1:]
				}

				db.Exec(tx, toExecute, db.Params{})
			}
		},
	}
}
