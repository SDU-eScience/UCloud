package migrations

import db "ucloud.dk/shared/pkg/database"

func inferenceV25() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV25",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`alter table inference_model add column default_for jsonb not null default '[]'::jsonb`,
				db.Params{},
			)
			db.Exec(
				tx,
				`create unique index inference_default_image_generation on inference_model ((default_for ? 'TextToImage')) where default_for ? 'TextToImage'`,
				db.Params{},
			)
			db.Exec(
				tx,
				`create unique index inference_default_image_edit on inference_model ((default_for ? 'ImageToImage')) where default_for ? 'ImageToImage'`,
				db.Params{},
			)
		},
	}
}
