package migrations

import db "ucloud.dk/shared/pkg/database"

func inferenceV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`create table inference_api_keys(api_key text primary key, owner text not null)`,
				db.Params{},
			)
		},
	}
}

func inferenceV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV2",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					drop table if exists inference_api_keys
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
				create table inference_api_keys (
				    token_id text primary key,
				    owner text not null,
				    token_hash bytea not null,
				    token_salt bytea not null,
				    expires_at timestamptz not null,
				    last_used_at timestamptz not null default now()
				)
			    `,
				db.Params{},
			)
		},
	}
}

func inferenceV3() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV3",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table inference_model(
						name text primary key,
						title text not null,
						capabilities jsonb not null,
						price_cached_input int not null,
						price_input int not null,
						price_output int not null,
						inference_endpoint_path text not null,
						inference_endpoint_model text not null,
						public bool not null,
						available_to jsonb not null
					)
				`,
				db.Params{},
			)
		},
	}
}

func inferenceV4() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV4",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table inference_model add column context_window int
				`,
				db.Params{},
			)
		},
	}
}

func inferenceV5() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV5",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table inference_model add column temperature double precision not null default 0.8
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					alter table inference_model add column top_p double precision not null default 0.1
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					alter table inference_model add column max_completion_tokens int not null default 65536
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					alter table inference_model add column system_prompt text
				`,
				db.Params{},
			)
		},
	}
}

func inferenceV6() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV6",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table inference_playground_thread(
						id text primary key,
						owner_username text not null,
						title text not null,
						created_at timestamptz not null,
						updated_at timestamptz not null
					)
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create index inference_playground_thread_owner_idx on inference_playground_thread(owner_username, updated_at desc)
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create table inference_playground_message(
						thread_id text not null references inference_playground_thread(id) on delete cascade,
						message_index int not null,
						role text not null,
						content text not null,
						generated_at timestamptz not null,
						primary key(thread_id, message_index)
					)
				`,
				db.Params{},
			)
		},
	}
}

func inferenceV7() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV7",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table inference_model add column title_model_name text
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					update inference_model set title_model_name = name where title_model_name is null
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					alter table inference_model alter column title_model_name set not null
				`,
				db.Params{},
			)
		},
	}
}

func inferenceV8() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV8",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table inference_playground_message add column reasoning text not null default ''
				`,
				db.Params{},
			)
		},
	}
}

func inferenceV9() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV9",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table inference_playground_message add column reasoning_title text not null default ''
				`,
				db.Params{},
			)
		},
	}
}

func inferenceV10() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV10",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table inference_playground_message add column model_name text not null default ''
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					alter table inference_playground_message add column started_at timestamptz
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					alter table inference_playground_message add column first_token_at timestamptz
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					alter table inference_playground_message add column finished_at timestamptz
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					alter table inference_playground_message add column output_tokens bigint not null default 0
				`,
				db.Params{},
			)
		},
	}
}

func inferenceV11() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV11",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table inference_model add column page_metadata jsonb
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create table inference_benchmark(
						id text primary key,
						title text not null,
						description text not null default '',
						higher_is_better bool not null default true,
						model_names jsonb not null
					)
				`,
				db.Params{},
			)
		},
	}
}

func inferenceV12() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV12",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table k8s.inference_attachments(
						id text primary key,
						created_by text not null,
						project_id text null,
						created_at timestamptz not null default now()
					)
				`,
				db.Params{},
			)
		},
	}
}

func inferenceV13() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV13",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`alter table k8s.inference_attachments add column filename text not null default ''`,
				db.Params{},
			)
		},
	}
}

func inferenceV14() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV14",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`alter table k8s.inference_attachments add column markdown_attachment_id text null`,
				db.Params{},
			)
		},
	}
}
