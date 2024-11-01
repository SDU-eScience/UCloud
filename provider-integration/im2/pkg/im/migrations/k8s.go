package migrations

import db "ucloud.dk/pkg/database"

func k8sV1() migrationScript {
	return migrationScript{
		Id: "k8sV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`create schema k8s`,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					create table k8s.tasks(
						id 						bigserial primary key,
						
						ucloud_username text not null,
						
						-- Can currently be null, mostly for background tasks which should not be visible
						-- to the end-user.
						ucloud_task_id 			int8,
						
						task_type 				text not null,
						
						ucloud_source 			text,
						ucloud_destination 		text,
						conflict_policy 		text,
						
						-- A foreign key into some other datastore (could be a table or something else) which contains
						-- more information about the task. How this should be interpreted depends on the task type.
						more_info 				text default null,
						
						created_at 				timestamptz not null default now(),
						paused 					bool not null default false
					)
				`,
				db.Params{},
			)
		},
	}
}
