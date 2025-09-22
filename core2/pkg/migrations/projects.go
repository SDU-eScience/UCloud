package migrations

import db "ucloud.dk/shared/pkg/database2"

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
