package migrations

import db "ucloud.dk/shared/pkg/database"

func k8sV1() db.MigrationScript {
	return db.MigrationScript{
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

func k8sV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "k8sv2",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table k8s.tasks_v2(
						id text primary key,
						ucloud_task_id int8,
						api_token text not null unique,
						created_at timestamptz not null default now()
					)
			    `,
				db.Params{},
			)
		},
	}
}

func k8sV3() db.MigrationScript {
	return db.MigrationScript{
		Id: "k8sV3",
		Execute: func(tx *db.Transaction) {
			db.Exec(tx, `
				create table k8s.node_lifecycles(
					node_name text primary key,
					node_uid text not null,
					cordoned boolean not null,
					available boolean not null,
					cordon_changed_at timestamptz not null default now(),
					availability_changed_at timestamptz not null default now(),
					maintenance_generation bigint not null default 0
				);
				create table k8s.node_lifecycle_deliveries(
					node_name text not null,
					maintenance_generation bigint not null,
					event_kind text not null,
					job_id text not null,
					delivered_at timestamptz,
					primary key(node_name, maintenance_generation, event_kind, job_id)
				)
			`, db.Params{})
		},
	}
}
