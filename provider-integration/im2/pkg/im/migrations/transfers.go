package migrations

import (
	db "ucloud.dk/pkg/database"
)

func fileTransfersV1() migrationScript {
	return migrationScript{
		Id: "fileTransferV1",
		Execute: func(tx *db.Transaction) {
			db.Exec(
				tx,
				`
					create table file_transfers(
						id 						text primary key,
						owner_uid 				int not null,   
						ucloud_source 			text not null,
						destination_provider 	text not null,
						
						selected_protocol 		text,
						protocol_parameters 	jsonb,

						created_at 				timestamptz not null default now()
					);
			    `,
				db.Params{},
			)
		},
	}
}
