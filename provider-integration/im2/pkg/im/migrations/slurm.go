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

func slurmV2() migrationScript {
	return migrationScript{
		Id: "slurmV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table slurm.tasks(
						id 						bigserial primary key,
						
						-- Can currently be null, mostly for background tasks which should not be visible
						-- to the end-user.
						ucloud_task_id 			int8,
						
						owner_uid 				int not null,
						task_type 				text not null,
						
						ucloud_source 			text,
						ucloud_destination 		text,
						conflict_policy 		text,
						
						-- A foreign key into some other datastore (could be a table or something else) which contains
						-- more information about the task. How this should be interpreted depends on the task type.
						more_info 				text default null,
						
						created_at 				timestamptz not null default now()
					)
				`,
				db.Params{},
			)
		},
	}
}
