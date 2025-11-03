package foundation

import (
	"net/http"

	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initSupportAssistsFoundation() {
	fndapi.SupportAssistResetMFA.Handler(func(info rpc.RequestInfo, request fndapi.ResetMFARequest) (util.Empty, *util.HttpError) {
		return resetMFA(request.Username)
	})
}

func resetMFA(username string) (util.Empty, *util.HttpError) {
	_, found := rpc.LookupActor(username)
	if !found {
		return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Username wrong")
	}
	db.NewTx0(func(tx *db.Transaction) {
		batch := db.BatchNew(tx)
		db.BatchExec(
			batch,
			`
			WITH credentials AS (
				SELECT id
				FROM auth.two_factor_credentials
				WHERE principal_id  = :username
			)
			DELETE from auth.two_factor_challenges
			where credentials_id = (select id from credentials);
			`,
			db.Params{
				"username": username,
			})
		db.BatchExec(
			batch,
			`
				DELETE FROM auth.two_factor_credentials
				WHERE principal_id = :username;
			`,
			db.Params{
				"username": username,
			})
		db.BatchSend(batch)

	})

	return util.Empty{}, nil

}
