package foundation

import (
	"encoding/json"
	"net/http"
	"time"

	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func initPasswordReset() {
	fndapi.NewPassword.Handler(func(info rpc.RequestInfo, request fndapi.NewPasswordRequest) (util.Empty, *util.HttpError) {
		return NewPassword()
	})

	fndapi.PasswordReset.Handler(func(info rpc.RequestInfo, request fndapi.PasswordResetRequest) (util.Empty, *util.HttpError) {
		return ResetPassword()
	})
}

type ResetRequest struct {
	token     string
	userId    string
	expiresAt time.Time
}

func NewPassword(token string, newPassword string) (util.Empty, *util.HttpError) {
	resetRequest, ok := getResetRequest(token)
	if !ok {
		return util.Empty{}, util.HttpErr(http.StatusNotFound, "Unable to reset password")
	}

	if resetRequest.expiresAt.Before(time.Now()) {
		return util.Empty{}, util.HttpErr(http.StatusForbidden, "Unable to reset password (token expired)")
	}

	UpdatePassword(resetRequest.userId, newPassword, false, "")
	return util.Empty{}, nil
}

func createResetRequest() {

}

func getResetRequest(token string) (ResetRequest, bool) {
	return db.NewTx2(func(tx *db.Transaction) (ResetRequest, bool) {
		row, ok := db.Get[ResetRequest](
			tx,
			`
			SELECT token, user_id, expires_at
            FROM password_reset_requests
            WHERE token = :token
		`,
			db.Params{
				"token": token,
			},
		)
		if !ok {
			return ResetRequest{}, false
		} else {
			return row, true
		}
	})

}

func ResetPassword() (util.Empty, *util.HttpError) {

}
