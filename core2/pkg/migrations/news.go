package migrations

import db "ucloud.dk/shared/pkg/database"

func newsV1() db.MigrationScript {
	return db.MigrationScript{
		Id: "newsV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					update news.news
					set category = upper(category)
					where true;
			    `,
				db.Params{},
			)
		},
	}
}
