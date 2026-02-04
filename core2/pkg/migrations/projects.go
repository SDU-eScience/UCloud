package migrations

import db "ucloud.dk/shared/pkg/database"

func projectsV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "projectsV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table project.projects add column backend_id text unique default null
			    `,
				db.Params{},
			)
		},
	}
}

func projectsV2() db.MigrationScript {
	return db.MigrationScript{
		Id: "projectsV2",
		Execute: func(tx *db.Transaction) {
			statements := []string{
				`
					create or replace function project.notify_project_change()
					returns trigger as $$
					begin
						perform pg_notify('project_updates', new.id::text);
						return new;
					end;
					$$ language plpgsql;
				`,
				`
					create trigger project_update_trigger
					after insert or update
					on project.projects
					for each row
					execute function project.notify_project_change();
				`,

				`
					create or replace function project.notify_project_group_change()
					returns trigger as $$
					begin
						if (tg_op = 'DELETE') then
							perform pg_notify('project_updates', old.project::text);
							return old;
						else
							perform pg_notify('project_updates', new.project::text);
							return new;
						end if;
					end;
					$$ language plpgsql;
				`,
				`
					create trigger project_group_update_trigger
					after insert or update or delete
					on project.groups
					for each row
					execute function project.notify_project_group_change();
				`,

				`
					create or replace function project.notify_project_group_member_change()
					returns trigger as $$
					begin
						if (tg_op = 'DELETE') then
							perform pg_notify('project_group_updates', old.group_id::text);
							return old;
						else
							perform pg_notify('project_group_updates', new.group_id::text);
							return new;
						end if;
					end;
					$$ language plpgsql;
				`,
				`
					create trigger project_group_member_update_trigger
					after insert or update or delete
					on project.group_members
					for each row
					execute function project.notify_project_group_member_change();
				`,

				`
					create or replace function project.notify_project_member_change()
					returns trigger as $$
					begin
						if (tg_op = 'DELETE') then
							perform pg_notify('project_updates', old.project_id::text);
							return old;
						else
							perform pg_notify('project_updates', new.project_id::text);
							return new;
						end if;
					end;
					$$ language plpgsql;
				`,
				`
					create trigger project_member_update_trigger
					after insert or update or delete
					on project.project_members
					for each row
					execute function project.notify_project_member_change();
				`,
			}

			for _, statement := range statements {
				db.Exec(tx, statement, db.Params{})
			}
		},
	}
}

func projectsV3() db.MigrationScript {
	return db.MigrationScript{
		Id: "projectsV3",
		Execute: func(tx *db.Transaction) {
			statements := []string{
				`
					create table project.project_user_preferences
					(
						project_id text not null
							references project.projects
								on delete cascade,
						username   text not null
							references auth.principals,
						favorite   boolean not null default false,
						hidden     boolean not null default false,
						primary key (project_id, username),
						constraint project_user_preferences_project_members_id_fkey
							foreign key (project_id, username) references project.project_members (project_id, username)
								on delete cascade
					);
				`,
				`
					insert into project.project_user_preferences (project_id, username, favorite, hidden)
					select project_id, username, true, false
					from project.project_favorite
					on conflict (project_id, username) do nothing;
				`,
				`
					drop table project.project_favorite;
				`,
			}

			for _, statement := range statements {
				db.Exec(tx, statement, db.Params{})
			}
		},
	}
}

func projectsV4() db.MigrationScript {
	return db.MigrationScript{
		Id: "projectsV4",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					alter table project.invite_link_group_assignments
					drop constraint invite_link_group_assignments_link_token_fkey;
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table project.invite_link_group_assignments
					alter column link_token type text
					using link_token::text;
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table project.invite_links
					alter column token type text
					using token::text;
			    `,
				db.Params{},
			)

			db.Exec(
				tx,
				`
					alter table project.invite_link_group_assignments 
					add constraint invite_link_group_assignments_link_token_fkey 
						foreign key (link_token) references project.invite_links (token)
			    `,
				db.Params{},
			)
		},
	}
}
