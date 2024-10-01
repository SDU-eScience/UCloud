package migrations

import "ucloud.dk/pkg/database"

func genericLicensesV1() migrationScript {
	return migrationScript{
		Id: "genericLicensesV1",
		Execute: func(ctx *database.Transaction) {
			database.Exec(ctx, `
				create table generic_license_servers(
					name text not null,
					category text not null,
					address text,
					port int,
					license text,
					primary key (name, category)
				);
			`, map[string]any{})

			database.Exec(ctx, `
					create table generic_license_instances(
					id text not null primary key,
					category text not null,
					owner text not null
				);
			`, map[string]any{})
		},
	}
}
