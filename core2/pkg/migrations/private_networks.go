package migrations

import db "ucloud.dk/shared/pkg/database"

func privateNetworksV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "privateNetworksV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table app_orchestrator.private_networks(
						name text not null,
						subdomain text not null,
						resource bigint primary key references provider.resource(id) 
					)
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table accounting.product_categories 
					alter column product_type type text 
					using product_type::text
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					drop type accounting.product_type
			    `,
				db.Params{},
			)
		},
	}
}
