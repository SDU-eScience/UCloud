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

func k8sV4() db.MigrationScript {
	return db.MigrationScript{
		Id: "k8sV4",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table k8s.init_script_image_cache(
						workspace_type text not null,
						workspace_id text not null,
						cache_key text not null,
						repository_name text not null,
						tag text not null,
						image_digest text,
						exact_bytes bigint not null default 0,
						state text not null,
						builder_job_id text,
						created_at timestamptz not null default now(),
						last_used_at timestamptz not null default now(),
						primary key(workspace_type, workspace_id, cache_key),
						unique(repository_name, tag)
					);

					create table k8s.init_script_image_cache_jobs(
						job_id text primary key,
						workspace_type text not null,
						workspace_id text not null,
						cache_key text not null,
						state text not null,
						created_at timestamptz not null default now()
					);

					create index init_script_image_cache_last_used_idx
						on k8s.init_script_image_cache(state, last_used_at);

					create index init_script_image_cache_jobs_key_idx
						on k8s.init_script_image_cache_jobs(workspace_type, workspace_id, cache_key)
				`,
				db.Params{},
			)
		},
	}
}
