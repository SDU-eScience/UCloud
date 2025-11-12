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
	fndapi.PasswordClaimResetToken.Handler(func(info rpc.RequestInfo, request fndapi.PasswordClaimResetTokenRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, PasswordClaimResetLink(request.Token, request.NewPassword)
	})

	fndapi.PasswordReset.Handler(func(info rpc.RequestInfo, request fndapi.PasswordResetRequest) (util.Empty, *util.HttpError) {
		return util.Empty{}, PasswordHandleResetRequest(request.Email)
	})
}

func PasswordClaimResetLink(token string, newPassword string) *util.HttpError {
	return db.NewTx(func(tx *db.Transaction) *util.HttpError {
		resetRequest, ok := db.Get[struct {
			Token     string    `json:"token"`
			UserId    string    `json:"userId"`
			ExpiresAt time.Time `json:"expiresAt"`
		}](
			tx,
			`
			select token, user_id, expires_at
            from  password_reset.password_reset_requests
            where token = :token
		`,
			db.Params{
				"token": token,
			},
		)

		if !ok {
			return util.HttpErr(http.StatusNotFound, "Unable to reset password")
		}

		if resetRequest.ExpiresAt.Before(time.Now()) {
			return util.HttpErr(http.StatusForbidden, "Unable to reset password (token expired)")
		}

		err := PasswordUpdate(tx, resetRequest.UserId, newPassword, false, "")
		if err != nil {
			return err
		}
		return nil
	})
}

func PasswordHandleResetRequest(email string) *util.HttpError {
	return db.NewTx(func(tx *db.Transaction) *util.HttpError {
		users := PrincipalsLookupsByEmail(tx, email)
		if len(users) == 0 {
			return util.HttpErr(http.StatusNotFound, "Cannot create reset request")
		}

		token := util.SecureToken()
		var bulkRequest fndapi.BulkRequest[fndapi.MailSendToUserRequest]
		for _, userId := range users {
			timeSource := time.Now()
			expireTime := timeSource.Add(time.Minute * 30)

			db.Exec(
				tx,
				`
					insert into password_reset.password_reset_requests (token, user_id, expires_at) 
					values (:token, :userId, :expiresAt)
				`,
				db.Params{
					"token":     token,
					"userId":    userId,
					"expiresAt": expireTime,
				},
			)

			rawMail := map[string]any{
				"type":  fndapi.MailTypeResetPassword,
				"token": token,
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
			return err
		}
		return nil
	})
}
