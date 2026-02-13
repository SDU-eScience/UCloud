package migrations

import db "ucloud.dk/shared/pkg/database"

func genericLicensesV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "genericLicensesV1",
		Execute: func(ctx *db.Transaction) {
			db.Exec(ctx, `
				create table generic_license_servers(
					name text not null,
					category text not null,
					address text,
					port int,
					license text,
					primary key (name, category)
				);
			`, map[string]any{})

			db.Exec(ctx, `
					create table generic_license_instances(
					id text not null primary key,
					category text not null,
					owner text not null
				);
			`, map[string]any{})
		},
	}
}
