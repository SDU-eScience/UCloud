package migrations

import db "ucloud.dk/shared/pkg/database"

func customApplicationsV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "customApplicationsV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table app_store.custom_application_groups(
						id bigint generated always as identity primary key,
						created_by text not null references auth.principals(id),
						project_id text references project.projects(id),
						created_at timestamptz not null default now(),
						is_custom boolean not null,
						backed_by_group_id bigint references app_store.application_groups(id) on delete set null,
						snapshot_title text not null,
						snapshot_description text not null
					)
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create unique index custom_workspace_backed_groups
					on app_store.custom_application_groups(
						coalesce(project_id, created_by),
						backed_by_group_id
					)
					where backed_by_group_id is not null
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create unique index custom_workspace_group_titles
					on app_store.custom_application_groups(
						coalesce(project_id, created_by),
						lower(snapshot_title)
					)
					where is_custom
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create table app_store.custom_application_categories(
						id bigint generated always as identity primary key,
						created_by text not null references auth.principals(id),
						project_id text references project.projects(id),
						created_at timestamptz not null default now(),
						is_custom boolean not null,
						backed_by_category_id bigint references app_store.categories(id) on delete set null,
						snapshot_title text not null,
						snapshot_description text not null
					)
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create unique index custom_workspace_backed_categories
					on app_store.custom_application_categories(
						coalesce(project_id, created_by),
						backed_by_category_id
					)
					where backed_by_category_id is not null
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create unique index custom_workspace_category_titles
					on app_store.custom_application_categories(
						coalesce(project_id, created_by),
						lower(snapshot_title)
					)
					where is_custom
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create table app_store.custom_application_category_acl(
						category_id bigint not null references app_store.custom_application_categories(id) on delete cascade,
						project_group_id text not null references project.groups(id) on delete cascade,
						permission text not null check (permission in ('READ', 'EDIT')),
						primary key (category_id, project_group_id, permission)
					)
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create table app_store.custom_applications(
						id bigint generated always as identity primary key,
						created_by text not null references auth.principals(id),
						project_id text references project.projects(id),
						created_at timestamptz not null default now(),
						name text not null,
						version text not null,
						service_provider text not null references provider.providers(unique_name),
						published_to_project boolean not null default false,
						flavor_name text not null,
						custom_group_id bigint not null references app_store.custom_application_groups(id),
						custom_category_id bigint not null references app_store.custom_application_categories(id),
						source_application text not null
					)
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create unique index custom_workspace_apps
					on app_store.custom_applications(
						coalesce(project_id, created_by),
						name,
						version,
						service_provider
					)
				`,
				db.Params{},
			)
			db.Exec(
				tx,
				`
					create or replace function project.notify_project_group_change()
					returns trigger as $$
					begin
						if (tg_op = 'DELETE') then
							perform pg_notify('project_updates', old.project::text);
							perform pg_notify('project_group_updates', old.id::text);
							return old;
						else
							perform pg_notify('project_updates', new.project::text);
							return new;
						end if;
					end;
					$$ language plpgsql;
				`,
				db.Params{},
			)
		},
	}
}
