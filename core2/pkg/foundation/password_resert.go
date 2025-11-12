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
		return NewPassword(request.Token, request.NewPassword)
	})

	fndapi.PasswordReset.Handler(func(info rpc.RequestInfo, request fndapi.PasswordResetRequest) (util.Empty, *util.HttpError) {
		return CreateResetRequest(request.Email)
	})
}

type ResetRequest struct {
	Token     string    `json:"token"`
	UserId    string    `json:"userId"`
	ExpiresAt time.Time `json:"expiresAt"`
}

func NewPassword(token string, newPassword string) (util.Empty, *util.HttpError) {
	return db.NewTx2(func(tx *db.Transaction) (util.Empty, *util.HttpError) {
		resetRequest, ok := getResetRequest(tx, token)
		if !ok {
			return util.Empty{}, util.HttpErr(http.StatusNotFound, "Unable to reset password")
		}

		if resetRequest.ExpiresAt.Before(time.Now()) {
			return util.Empty{}, util.HttpErr(http.StatusForbidden, "Unable to reset password (token expired)")
		}

		err := UpdatePassword(tx, resetRequest.UserId, newPassword, false, "")
		if err != nil {
			return util.Empty{}, err
		}
		return util.Empty{}, nil
	})
}

func CreateResetRequest(email string) (util.Empty, *util.HttpError) {
	return db.NewTx2(func(tx *db.Transaction) (util.Empty, *util.HttpError) {
		users, ok := LookupUsernamesByEmail(tx, email)
		if !ok {
			return util.Empty{}, util.HttpErr(http.StatusNotFound, "Cannot create reset request")
		}

		token := util.SecureToken()
		var bulkRequest fndapi.BulkRequest[fndapi.MailSendToUserRequest]
		for _, userId := range users {
			insertTokenToDB(tx, token, userId)

			rawMail := map[string]any{
				"type":    fndapi.MailTypeResetPassword,
				"message": mailTemplates[fndapi.MailTypeResetPassword],
				"subject": "[UCloud] Reset of Password",
				"token":   token,
			}
			mailData, _ := json.Marshal(rawMail)
			bulkRequest.Items = append(
				bulkRequest.Items,
				fndapi.MailSendToUserRequest{
					Receiver:       userId,
					Mail:           fndapi.Mail(mailData),
					Mandatory:      util.OptValue(true),
					ReceivingEmail: util.OptValue(email),
				},
			)
		}

		_, err := fndapi.MailSendToUser.Invoke(bulkRequest)
		if err != nil {
			return util.Empty{}, err
		}
		return util.Empty{}, nil
	})

}

func insertTokenToDB(tx *db.Transaction, token string, userId string) {
	timeSource := time.Now()
	expireTime := timeSource.Add(time.Minute * 30)

	db.Exec(
		tx,
		`
			INSERT INTO password_reset.password_reset_requests (token, user_id, expires_at) 
			VALUES (:token, :userId, :expiresAt)
		`,
		db.Params{
			"token":     token,
			"userId":    userId,
			"expiresAt": expireTime,
		},
	)
}

func getResetRequest(tx *db.Transaction, token string) (ResetRequest, bool) {
	row, ok := db.Get[ResetRequest](
		tx,
		`
			SELECT token, user_id, expires_at
            FROM  password_reset.password_reset_requests
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
}
