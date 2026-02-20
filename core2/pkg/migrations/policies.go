package migrations

import db "ucloud.dk/shared/pkg/database"

func policiesV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "policiesV1",
		Execute: func(tx *db.Transaction) {
			statements := []string{
				`
					create table if not exists project.policies (
					    policy_name text not null,
					    policy_properties jsonb not null,
					    project_id text not null references project.projects(id) on delete cascade,
					    primary key (project_id, policy_name)
					)
			    `,
				`
					create or replace function project.notify_policy_change()
					returns trigger as $$
					begin
						perform pg_notify('policy_updates', new.project_id::text);
						return new;
					end;
					$$ language plpgsql;
				`,
				`
					create trigger policy_update_trigger
					after insert or update or delete
					on project.policies
					for each row
					execute function project.notify_policy_change();
				`,
			}

			for _, statement := range statements {
				db.Exec(tx, statement, db.Params{})
			}
		},
	}
}
