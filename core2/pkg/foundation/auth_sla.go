package foundation

import (
	cfg "ucloud.dk/core/pkg/config"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
)

func SlaRetrieveText() fndapi.ServiceAgreementText {
	slaConfig := cfg.Configuration.ServiceLicenseAgreement

	return fndapi.ServiceAgreementText{
		Version: slaConfig.Version,
		Text:    slaConfig.Text,
	}
}

func SlaAccept(actor rpc.Actor, version int) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				update auth.principals
				set service_license_agreement = :version
				where id = :username
		    `,
			db.Params{
				"version":  version,
				"username": actor.Username,
			},
		)
	})
}
