package foundation

import (
	"database/sql"
	"encoding/json"
	"net/http"

	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func UserOptInfoRetrieve(actor rpc.Actor) fndapi.OptionalUserInfo {
	result := db.NewTx(func(tx *db.Transaction) fndapi.OptionalUserInfo {
		row, _ := db.Get[struct {
			OrganizationFullName sql.Null[string]
			Department           sql.Null[string]
			ResearchField        sql.Null[string]
			Position             sql.Null[string]
			Gender               sql.Null[string]
		}](
			tx,
			`
			select
				info.organization_full_name,
				info.department,
				info.research_field,
				info.position,
				info.gender
			from
				auth.principals p join
				auth.additional_user_info info on p.uid = info.associated_user
			where
				p.id = :username and
				p.dtype = 'PERSON'
			`,
			db.Params{"username": actor.Username},
		)
		return fndapi.OptionalUserInfo{
			OrganizationFullName: util.SqlNullToOpt(row.OrganizationFullName),
			Department:           util.SqlNullToOpt(row.Department),
			ResearchField:        util.SqlNullToOpt(row.ResearchField),
			Position:             util.SqlNullToOpt(row.Position),
			Gender:               util.SqlNullToOpt(row.Gender),
		}
	})
	return result
}

func UsersOptInfoUpdate(actor rpc.Actor, info fndapi.OptionalUserInfo) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
			insert into auth.additional_user_info (associated_user, organization_full_name, department,
                research_field, position, gender) 
            select p.uid, :organization_full_name, :department, :research_field, :position, :gender
            from auth.principals p
            where p.id = :username
            on conflict (associated_user) do update set
                organization_full_name = excluded.organization_full_name,
                department = excluded.department,
                research_field = excluded.research_field,
                position = excluded.position,
				gender = excluded.gender
			`,
			db.Params{
				"username":               actor.Username,
				"organization_full_name": info.OrganizationFullName.Sql(),
				"department":             info.Department.Sql(),
				"research_field":         info.ResearchField.Sql(),
				"position":               info.Position.Sql(),
				"gender":                 info.Gender.Sql(),
			})
	})
}

func UsersInfoVerify(token string) bool {
	result := db.NewTx(func(tx *db.Transaction) bool {
		_, ok := db.Get[struct{ Dummy int }](
			tx,
			`
				with update_info as (
					update auth.user_info_update_request
					set
						confirmed = true,
						modified_at = now()
					where
						verification_token = :token
						and not confirmed
						and now() - created_at < '24 hours'::interval
					returning 
						uid,
						first_names,
						last_name,
						email
				)
				update auth.principals p
				set
					first_names = coalesce(i.first_names, p.first_names),
					last_name = coalesce(i.last_name, p.last_name),
					email = coalesce(i.email, p.email)
				from
					update_info i
				where
					i.uid = p.uid
				returning 1 as dummy
			`,
			db.Params{"token": token},
		)

		return ok
	})

	return result
}

func UsersInfoRetrieve(actor rpc.Actor) fndapi.UsersRetrieveInfoResponse {
	result, ok := db.NewTx2(func(tx *db.Transaction) (Principal, bool) {
		principal, ok := PrincipalRetrieve(tx, actor.Username)
		return principal, ok
	})
	if ok {
		return fndapi.UsersRetrieveInfoResponse{
			Email:        result.Email,
			FirstNames:   result.FirstNames,
			LastName:     result.LastName,
			Organization: result.OrgId,
		}
	} else {
		return fndapi.UsersRetrieveInfoResponse{}
	}
}

func UsersInfoUpdate(actor rpc.Actor, request fndapi.UsersUpdateInfoRequest, remoteHost string) *util.HttpError {
	token := util.SecureToken()
	recipientEmail, ok := db.NewTx2(func(tx *db.Transaction) (string, bool) {
		db.Exec(
			tx,
			`
				delete from auth.verification_email_log where now() - created_at > '24 hours'::interval 
			`,
			db.Params{},
		)

		_, ok := db.Get[struct{ IpAddress string }](tx,
			`
				with count as (
					select count(*) total
					from auth.verification_email_log
					where ip_address = :remote_host
				)
				insert into auth.verification_email_log(ip_address) 
				select :remote_host
				from count
				where count.total < 10
				returning ip_address
			`,
			db.Params{"remote_host": remoteHost},
		)

		if !ok {
			return "", false
		}

		recipientEmail, ok := db.Get[struct{ Email string }](
			tx,
			`
				with
                    user_info as (
                        select uid, email
                        from auth.principals
                        where id = :username
                    ),
                    insertion as (
                        insert into auth.user_info_update_request (uid, first_names, last_name, email, verification_token)
                        select uid, :first_names::text, :last_name::text, :email::text, :token::text
                        from user_info i
                    )
                select coalesce(:email::text, i.email) as email
                from user_info i;
			`,
			db.Params{
				"username":    actor.Username,
				"first_names": request.FirstNames.Sql(),
				"last_name":   request.LastName.Sql(),
				"email":       request.Email.Sql(),
				"token":       token,
			},
		)

		return recipientEmail.Email, ok
	})

	if !ok {
		return util.HttpErr(http.StatusBadRequest, "Could not update user information. Try again later")
	}

	mailVariables := map[string]any{
		"token":      token,
		"verifyType": "info-update",
		"type":       fndapi.MailTypeVerifyEmailAddress,
	}

	mailBytes, _ := json.Marshal(mailVariables)
	mail := fndapi.Mail(mailBytes)

	_, err := fndapi.MailSendDirect.Invoke(fndapi.BulkRequestOf(fndapi.MailSendDirectMandatoryRequest{
		RecipientEmail: recipientEmail,
		Mail:           mail,
	}))

	return err
}
