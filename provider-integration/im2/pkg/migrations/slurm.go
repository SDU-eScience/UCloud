package migrations

import (
	db "ucloud.dk/pkg/database"
)

func slurmV1() migrationScript {
	return migrationScript{
		Id: "slurmV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`create schema slurm`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create table slurm.accounts(
					    owner_username text not null,
						owner_project text not null,
						category text not null,
						account_name text not null,
						
						primary key (owner_username, owner_project, category)
					)
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create index acc_name on slurm.accounts(account_name)
			    `,
				db.Params{},
			)
		},
	}
}
