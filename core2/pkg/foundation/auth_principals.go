package foundation

import (
	"database/sql"
	"fmt"
	"math/rand"
	"net/http"
	"strings"
	"time"

	"golang.org/x/text/cases"
	"golang.org/x/text/language"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type Principal struct {
	PrincipalSpecification
	ServiceLicenseAgreement bool
	MfaEnabled              bool
	Uid                     int
	Membership              rpc.ProjectMembership
	Groups                  rpc.GroupMembership
	ProviderProjects        rpc.ProviderProjects
	CreatedAt               fndapi.Timestamp
	ModifiedAt              fndapi.Timestamp
}

type PrincipalSpecification struct {
	Type           string
	Id             string
	Role           fndapi.PrincipalRole
	FirstNames     util.Option[string]
	LastName       util.Option[string]
	HashedPassword util.Option[[]byte]
	Salt           util.Option[[]byte]
	OrgId          util.Option[string]
	Email          util.Option[string]
}

func PrincipalRetrieve(tx *db.Transaction, username string) (Principal, bool) {
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
			PrincipalSpecification: PrincipalSpecification{
				Type:       row.Dtype,
				Id:         row.Id,
				Role:       fndapi.PrincipalRole(row.Role),
				FirstNames: util.OptStringIfNotEmpty(row.FirstNames.String),
				LastName:   util.OptStringIfNotEmpty(row.LastName.String),
				OrgId:      util.OptStringIfNotEmpty(row.OrgId.String),
				Email:      util.OptStringIfNotEmpty(row.Email.String),
			},
			CreatedAt:               fndapi.Timestamp(row.CreatedAt),
			ModifiedAt:              fndapi.Timestamp(row.ModifiedAt),
			ServiceLicenseAgreement: row.ServiceLicenseAgreement == SlaRetrieveText().Version,
			Uid:                     row.Uid,
			MfaEnabled:              row.MfaEnabled,
		}

		if principal.Role != fndapi.PrincipalService {
			projectInfo := ProjectRetrieveClaimsInfo(row.Id)
			principal.Membership = projectInfo.Membership
			principal.ProviderProjects = projectInfo.ProviderProjects
			principal.Groups = projectInfo.Groups
		}

		if row.HashedPassword.Valid {
			principal.HashedPassword = util.OptValue(row.HashedPassword.V)
		}

		if row.Salt.Valid {
			principal.Salt = util.OptValue(row.Salt.V)
		}

		return principal, true
	}
}

func PrincipalCreate(spec PrincipalSpecification) (int, *util.HttpError) {
	return db.NewTx2(func(tx *db.Transaction) (int, *util.HttpError) {
		return PrincipalCreateOrUpdate(tx, &spec, false, false)
	})
}

func PrincipalUpdate(spec PrincipalSpecification) (int, *util.HttpError) {
	return db.NewTx2(func(tx *db.Transaction) (int, *util.HttpError) {
		return PrincipalCreateOrUpdate(tx, &spec, true, false)
	})
}

func PrincipalUpdatePassword(username string, newPassword string) bool {
	if len(newPassword) == 0 {
		return false
	}

	salt := genSalt()
	hash := hashPassword(newPassword, salt)

	return db.NewTx(func(tx *db.Transaction) bool {
		principal, ok := PrincipalRetrieve(tx, username)
		if ok {
			spec := principal.PrincipalSpecification
			spec.HashedPassword.Set(hash.HashedPassword)
			spec.Salt.Set(salt)
			_, err := PrincipalCreateOrUpdate(tx, &spec, true, false)
			return err == nil
		} else {
			return false
		}
	})
}

func PrincipalCreateOrUpdate(tx *db.Transaction, spec *PrincipalSpecification, isUpdate bool, clearInvalidPers bool) (int, *util.HttpError) {
	if spec.Type == "PERSON" {
		var err *util.HttpError
		util.ValidateEnum(&spec.Role, []fndapi.PrincipalRole{fndapi.PrincipalUser, fndapi.PrincipalAdmin}, "role", &err)
		util.ValidateStringIfPresentEx(&spec.FirstNames, "firstNames", 0, &err, clearInvalidPers)
		util.ValidateStringIfPresentEx(&spec.LastName, "lastName", 0, &err, clearInvalidPers)
		util.ValidateStringIfPresentEx(&spec.OrgId, "orgId", 0, &err, clearInvalidPers)
		util.ValidateStringIfPresentEx(&spec.Email, "email", util.StringValidationRequireEmail, &err, clearInvalidPers)

		if err != nil {
			return 0, err
		}
	} else if spec.Type == "SERVICE" {
		var err *util.HttpError
		if err == nil && spec.Role != fndapi.PrincipalService {
			err = util.HttpErr(http.StatusBadRequest, "role must be SERVICE")
		}
		if err == nil && spec.FirstNames.Present {
			err = util.HttpErr(http.StatusBadRequest, "firstNames must not be specified")
		}
		if err == nil && spec.LastName.Present {
			err = util.HttpErr(http.StatusBadRequest, "lastName must not be specified")
		}
		if err == nil && spec.OrgId.Present {
			err = util.HttpErr(http.StatusBadRequest, "orgId must not be specified")
		}
		if err == nil && spec.Email.Present {
			err = util.HttpErr(http.StatusBadRequest, "email must not be specified")
		}

		if err != nil {
			return 0, err
		}
	} else {
		return 0, util.HttpErr(http.StatusInternalServerError, "invalid principal specified (type)")
	}

	var uid int
	ok := false

	params := db.Params{
		"type":            spec.Type,
		"id":              spec.Id,
		"role":            string(spec.Role),
		"first_names":     spec.FirstNames.Sql(),
		"last_name":       spec.LastName.Sql(),
		"hashed_password": spec.HashedPassword.Sql(),
		"salt":            spec.Salt.Sql(),
		"org_id":          spec.OrgId.Sql(),
		"email":           spec.Email.Sql(),
	}

	if isUpdate {
		row, exists := db.Get[struct{ Uid int }](
			tx,
			`
				update auth.principals
				set
					dtype = :type,
					modified_at = now(),
					role = :role,
					first_names = coalesce(:first_names, first_names),
					last_name = coalesce(:last_name, last_name),
					hashed_password = coalesce(:password, hashed_password),
					salt = coalesce(:salt, salt),
					org_id = coalesce(:org_id, org_id),
					email = coalesce(:email, email)
				where
					id = :id
				returning uid
			`,
			params,
		)

		uid = row.Uid
		ok = exists
	} else {
		row, exists := db.Get[struct{ Uid int }](
			tx,
			`
				insert into auth.principals(dtype, id, created_at, modified_at, role, first_names, last_name, 
					hashed_password, salt, org_id, email) 
				values (:type, :id, now(), now(), :role, :first_names, :last_name, :password, :salt, :org_id, :email)
				on conflict (id) do nothing 
				returning uid
			`,
			params,
		)
		uid = row.Uid
		ok = exists
	}

	if !ok {
		return 0, util.HttpErr(http.StatusConflict, "a user with this username already exists")
	} else {
		return uid, nil
	}
}

type IdpResponse struct {
	Idp        int
	Identity   string
	FirstNames util.Option[string]
	LastName   util.Option[string]
	OrgId      util.Option[string]
	Email      util.Option[string]
}

func PrincipalRetrieveOrCreateFromIdpResponse(resp IdpResponse) (Principal, *util.HttpError) {
	if resp.Identity == "" {
		return Principal{}, util.HttpErr(http.StatusBadGateway, "malformed response from identity provider")
	}

	return db.NewTx2(func(tx *db.Transaction) (Principal, *util.HttpError) {
		userRow, ok := db.Get[struct{ AssociatedUser int }](
			tx,
			`
				select associated_user
				from auth.idp_auth_responses resp
				where
					resp.idp_identity = :identity
					and resp.idp = :idp
		    `,
			db.Params{
				"idp":      resp.Idp,
				"identity": resp.Identity,
			},
		)

		userId := userRow.AssociatedUser

		if !ok {
			caser := cases.Title(language.English)
			normalizeName := func(s string) string {
				s = strings.ReplaceAll(s, "-", " ")
				s = strings.ReplaceAll(s, "_", " ")
				s = strings.ReplaceAll(s, "\t", " ")
				s = strings.ReplaceAll(s, "\r", " ")
				s = strings.ReplaceAll(s, "\n", " ")
				return caser.String(s)
			}

			baseUsername := normalizeName(resp.FirstNames.GetOrDefault("")) + normalizeName(resp.LastName.GetOrDefault(""))
			if len(baseUsername) == 0 {
				baseUsername = "Unknown"
			}

			baseUsername = strings.ReplaceAll(baseUsername, " ", "")

			potentialConflictRows := db.Select[struct{ Id string }](
				tx,
				`select id from auth.principals where id ilike :base_name || '#%'`,
				db.Params{
					"base_name": baseUsername,
				},
			)

			potentialConflicts := map[string]util.Empty{}
			for _, row := range potentialConflictRows {
				potentialConflicts[strings.ToLower(row.Id)] = util.Empty{}
			}

			maxNumberGuess := 10_000
			if len(potentialConflicts) > 1000 {
				maxNumberGuess = 10_000_000
			}

			username := util.Option[string]{}
			for attempt := 0; attempt < 5000; attempt++ {
				numberString := fmt.Sprintf("%04d", rand.Intn(maxNumberGuess))
				guess := baseUsername + "#" + numberString
				_, exists := potentialConflicts[strings.ToLower(guess)]
				if !exists {
					username.Set(guess)
					break
				}
			}

			if !username.Present {
				return Principal{}, util.HttpErr(http.StatusInternalServerError, "could not create user")
			}

			spec := PrincipalSpecification{
				Type:       "PERSON",
				Id:         username.Value,
				Role:       fndapi.PrincipalUser,
				FirstNames: resp.FirstNames,
				LastName:   resp.LastName,
				OrgId:      resp.OrgId,
				Email:      resp.Email,
			}

			uid, err := PrincipalCreateOrUpdate(tx, &spec, false, true)
			if err != nil {
				return Principal{}, err
			}

			db.Exec(
				tx,
				`
					insert into auth.idp_auth_responses(associated_user, idp, idp_identity, first_names, last_name, organization_id, email) 
					values (:uid, :idp, :username, :first_names, :last_name, :org_id, :email)
			    `,
				db.Params{
					"uid":         uid,
					"idp":         resp.Idp,
					"username":    spec.Id,
					"first_names": spec.FirstNames.Sql(),
					"last_name":   spec.LastName.Sql(),
					"org_id":      spec.OrgId.Sql(),
					"email":       spec.Email.Sql(),
				},
			)

			principal, _ := PrincipalRetrieve(tx, spec.Id)
			return principal, nil
		} else {
			username, ok := db.Get[struct{ Id string }](
				tx,
				`select id from auth.principals where uid = :uid`,
				db.Params{"uid": userId},
			)

			if !ok {
				return Principal{}, util.HttpErr(http.StatusInternalServerError, "internal error")
			}

			principal, ok := PrincipalRetrieve(tx, username.Id)

			if !ok {
				return Principal{}, util.HttpErr(http.StatusInternalServerError, "internal error")
			}

			return principal, nil
		}
	})
}
