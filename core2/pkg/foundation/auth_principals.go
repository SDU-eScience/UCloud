package foundation

import (
	"database/sql"
	"time"

	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type Principal struct {
	Type                    string
	Id                      string
	CreatedAt               fndapi.Timestamp
	ModifiedAt              fndapi.Timestamp
	Role                    fndapi.PrincipalRole
	FirstNames              util.Option[string]
	LastName                util.Option[string]
	HashedPassword          util.Option[[]byte]
	Salt                    util.Option[[]byte]
	OrgId                   util.Option[string]
	Email                   util.Option[string]
	ServiceLicenseAgreement bool
	MfaEnabled              bool
	Uid                     int
	Membership              rpc.ProjectMembership
	Groups                  rpc.GroupMembership
	ProviderProjects        rpc.ProviderProjects
}

func PrincipalLookupByEmail(tx *db.Transaction, email string) []string {
	ok := db.Select[struct {
		Id string
	}](
		tx,
		`
			select id
			from auth.principals
			where email = :email
		`,
		db.Params{
			"email": email,
		},
	)
	var results []string
	for _, elm := range ok {
		results = append(results, elm.Id)
	}
	return results

}

func LookupPrincipal(tx *db.Transaction, username string) (Principal, bool) {
	row, ok := db.Get[struct {
		Dtype                   string
		Id                      string
		CreatedAt               time.Time
		ModifiedAt              time.Time
		Role                    string
		FirstNames              sql.NullString
		LastName                sql.NullString
		HashedPassword          sql.Null[[]byte]
		Salt                    sql.Null[[]byte]
		OrgId                   sql.NullString
		Email                   sql.NullString
		ServiceLicenseAgreement int
		Uid                     int
		MfaEnabled              bool
	}](
		tx,
		`
			select p.*, cred.id is not null as mfa_enabled
			from
				auth.principals p
				left join auth.two_factor_credentials cred on p.id = cred.principal_id and cred.enforced = true
			where p.id = :username
	    `,
		db.Params{
			"username": username,
		},
	)

	if !ok {
		return Principal{}, false
	} else {
		principal := Principal{
			Type:                    row.Dtype,
			Id:                      row.Id,
			CreatedAt:               fndapi.Timestamp(row.CreatedAt),
			ModifiedAt:              fndapi.Timestamp(row.ModifiedAt),
			Role:                    fndapi.PrincipalRole(row.Role),
			FirstNames:              util.OptStringIfNotEmpty(row.FirstNames.String),
			LastName:                util.OptStringIfNotEmpty(row.LastName.String),
			OrgId:                   util.OptStringIfNotEmpty(row.OrgId.String),
			Email:                   util.OptStringIfNotEmpty(row.Email.String),
			ServiceLicenseAgreement: row.ServiceLicenseAgreement == SlaRetrieveText().Version,
			Uid:                     row.Uid,
			MfaEnabled:              row.MfaEnabled,
		}

		projectInfo := ProjectRetrieveClaimsInfo(row.Id)
		principal.Membership = projectInfo.Membership
		principal.ProviderProjects = projectInfo.ProviderProjects
		principal.Groups = projectInfo.Groups

		if row.HashedPassword.Valid {
			principal.HashedPassword = util.OptValue(row.HashedPassword.V)
		}

		if row.Salt.Valid {
			principal.Salt = util.OptValue(row.Salt.V)
		}

		return principal, true
	}
}
