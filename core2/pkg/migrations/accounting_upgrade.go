package migrations

import (
	"ucloud.dk/core/pkg/accounting"
	db "ucloud.dk/shared/pkg/database"
)

func accountingV6() db.MigrationScript {
	return db.MigrationScript{
		Id:      "accountingV6",
		Execute: accounting.AccountingUpgrade,
	}
}
