package migrations

import db "ucloud.dk/shared/pkg/database"

func inferenceV26() db.MigrationScript {
	return db.MigrationScript{
		Id: "inferenceV26",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`create unique index inference_default_transcription on inference_model ((default_for ? 'SpeechToText')) where default_for ? 'SpeechToText'`,
				db.Params{},
			)
		},
	}
}
