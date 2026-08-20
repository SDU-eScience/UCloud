package migrations

import db "ucloud.dk/shared/pkg/database"

func applicationVariantsV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "applicationVariantsV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(tx, `
				create table app_store.application_variants(
					id bigserial primary key,
					base_name text not null,
					base_version text not null,
					base_group bigint not null,
					created_by text not null references auth.principals(id),
					project_id text,
					provider text not null references provider.providers(unique_name),
					title text not null,
					published_to_project boolean not null default false,
					state text not null,
					failure text,
					created_at timestamptz not null default now(),
					modified_at timestamptz not null default now(),
					foreign key(base_name, base_version) references app_store.applications(name, version)
				)
			`,
				db.Params{},
			)
			db.Exec(tx, `
				create unique index application_variants_unique_title
				on app_store.application_variants(coalesce(project_id, created_by), base_group, lower(title))
				where state <> 'DELETED'
			`,
				db.Params{},
			)
			db.Exec(tx, `
				create table app_store.application_variant_revisions(
					id bigserial primary key,
					variant_id bigint not null references app_store.application_variants(id),
					created_by text not null references auth.principals(id),
					image text not null,
					image_digest text not null,
					created_at timestamptz not null default now()
				)
			`,
				db.Params{},
			)
		},
	}
}
