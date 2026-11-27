package migrations

import db "ucloud.dk/shared/pkg/database"

func terminalSessionsV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "terminalSessionsV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create schema if not exists terminal
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create table terminal.sessions(
						id text primary key,
						job_id bigint not null
							references provider.resource(id) on delete cascade,
						provider_id text not null,
						created_by text not null
							references auth.principals(id) on delete cascade,
						title text not null,
						active_tab_id text,
						revision bigint not null default 1,
						created_at timestamptz not null default now(),
						updated_at timestamptz not null default now(),
						last_accessed_at timestamptz not null default now()
					)
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create index idx_terminal_sessions_user_accessed
					on terminal.sessions(created_by, last_accessed_at desc)
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create index idx_terminal_sessions_job_id
					on terminal.sessions(job_id)
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create table terminal.tabs(
						id text primary key,
						session_id text not null
							references terminal.sessions(id) on delete cascade,
						title text not null,
						title_pinned boolean not null default false,
						position int not null,
						active_pane_id text,
						layout jsonb not null
					)
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create index idx_terminal_tabs_session_position
					on terminal.tabs(session_id, position)
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create table terminal.panes(
						id text primary key,
						session_id text not null
							references terminal.sessions(id) on delete cascade,
						tab_id text not null
							references terminal.tabs(id) on delete cascade,
						rank int not null default 0,
						title text not null,
						title_pinned boolean not null default false,
						backend_handle text not null,
						created_at timestamptz not null default now(),
						unique (session_id, backend_handle)
					)
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create index idx_terminal_panes_tab_id
					on terminal.panes(tab_id)
				`,
				db.Params{},
			)
		},
	}
}
