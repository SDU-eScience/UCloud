package migrations

import db "ucloud.dk/shared/pkg/database2"

func apiTokensV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "apiTokensV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table provider.api_tokens(
						resource bigint not null primary key references provider.resource,
						title text not null,
						description text not null,
						provider text,
						permissions jsonb not null,
						token_hash bytea, -- null if provider is set 
						token_salt bytea, -- null if provider is set 
						expires_at timestamptz not null
					);
			    `,
				db.Params{},
			)
		},
	}
}
