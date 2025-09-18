package pgxscan

import sqlmapper "ucloud.dk/pgxscan/internal/sqlmapper"

func SetColumnRenameFunction(newFunction func(string) string) {
	sqlmapper.SetColumnRenameFunction(newFunction)
}
