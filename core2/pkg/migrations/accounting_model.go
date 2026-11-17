package migrations

import db "ucloud.dk/shared/pkg/database"

func accountingModelV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "accountingModelV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create type accounting.accounting_model as enum ('CAPACITY', 'CONSUMPTION');

					alter table accounting.product_categories
					add column accounting_model accounting.accounting_model;

					update accounting.product_categories
					set accounting_model = case
						when product_type = 'INFERENCE' then 'CONSUMPTION'::accounting.accounting_model
						when accounting_frequency = 'ONCE' then 'CAPACITY'::accounting.accounting_model
						else 'CONSUMPTION'::accounting.accounting_model
					end;

					alter table accounting.product_categories
					alter column accounting_model set not null;

					update accounting.product_categories
					set charge_type = case
						when accounting_model = 'CAPACITY' then 'DIFFERENTIAL_QUOTA'::accounting.charge_type
						else 'ABSOLUTE'::accounting.charge_type
					end;
				`,
				db.Params{},
			)
		},
	}
}
