package foundation

import (
	"database/sql"

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
		}](
			tx,
			`
			select
				info.organization_full_name,
				info.department,
				info.research_field,
				info.position
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
                research_field, position) 
            select p.uid, :organization_full_name, :department, :research_field, :position
            from auth.principals p
            where p.id = :username
            on conflict (associated_user) do update set
                organization_full_name = excluded.organization_full_name,
                department = excluded.department,
                research_field = excluded.research_field,
                position = excluded.position
			`,
			db.Params{
				"username":               actor.Username,
				"organization_full_name": info.OrganizationFullName,
				"department":             info.Department,
				"research_field":         info.ResearchField,
				"position":               info.Position,
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

func UsersInfoUpdate(request fndapi.UsersUpdateInfoRequest, remoteHost string) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(tx,
			``,
			db.Params{})
	})
}
