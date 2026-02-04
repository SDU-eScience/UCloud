package foundation

import (
	"bytes"
	"encoding/base64"
	"image/png"
	"math/rand"
	"net/http"
	"strings"

	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"

	"github.com/pquerna/otp"
	"github.com/pquerna/otp/totp"
	"ucloud.dk/shared/pkg/util"
)

func MfaCreateCredentials(actor rpc.Actor) (fndapi.MfaCredentials, *util.HttpError) {
	key, err := totp.Generate(totp.GenerateOpts{
		Issuer:      "UCloud",
		AccountName: actor.Username,
		Period:      30,
		SecretSize:  20,
		Algorithm:   otp.AlgorithmSHA1,
		Digits:      otp.DigitsSix,
	})

	if err != nil {
		return fndapi.MfaCredentials{}, util.HttpErr(http.StatusInternalServerError, "Internal error")
	}

	challengeId, httpErr := db.NewTx2(func(tx *db.Transaction) (string, *util.HttpError) {
		row, ok := db.Get[struct {
			Username       string
			HasCredentials bool
		}](
			tx,
			`
				select p.id as username, mfa.id is not null as has_credentials
				from
					auth.principals p
					left join auth.two_factor_credentials mfa on 
						p.id = mfa.principal_id
						and mfa.enforced = true
				where
					p.id = :username
					and (
						p.role = 'USER'
						or p.role = 'ADMIN'
					)
		    `,
			db.Params{
				"username": actor.Username,
			},
		)

		if !ok {
			return "", util.HttpErr(http.StatusForbidden, "Forbidden")
		} else if row.HasCredentials {
			return "", util.HttpErr(http.StatusForbidden, "2FA is already activated on this account")
		} else {
			db.Exec(
				tx,
				`
					delete from auth.two_factor_challenges chal
					using auth.two_factor_credentials cred
					where
						chal.credentials_id = cred.id
						and cred.principal_id = :username
						and not cred.enforced
			    `,
				db.Params{
					"username": actor.Username,
				},
			)

			db.Exec(
				tx,
				`
					delete from auth.two_factor_credentials cred
					where
						cred.principal_id = :username
						and not cred.enforced
			    `,
				db.Params{
					"username": actor.Username,
				},
			)

			row, _ := db.Get[struct{ Id int }](
				tx,
				`
					insert into auth.two_factor_credentials(id, enforced, shared_secret, principal_id)
					values (nextval('auth.hibernate_sequence'), false, :secret, :username)
					returning id
			    `,
				db.Params{
					"secret":   key.Secret(),
					"username": actor.Username,
				},
			)

			result, ok := mfaCreateInternalChallenge(tx, actor.Username, util.OptValue(row.Id))
			if !ok || !tx.Ok {
				_ = tx.ConsumeError()
				db.RequestRollback(tx)
				return "", util.HttpErr(http.StatusInternalServerError, "Internal error")
			} else {
				return result, nil
			}
		}
	})

	if httpErr != nil {
		return fndapi.MfaCredentials{}, httpErr
	}

	var qrBuf bytes.Buffer
	qrCode, err := key.Image(200, 200)
	qrUrl := strings.Builder{}
	if err != nil {
		return fndapi.MfaCredentials{}, util.HttpErr(http.StatusInternalServerError, "Internal error")
	} else {
		err = png.Encode(&qrBuf, qrCode)
		if err != nil {
			return fndapi.MfaCredentials{}, util.HttpErr(http.StatusInternalServerError, "Internal error")
		}

		qrUrl.WriteString("data:image/png;base64,")
		qrUrl.WriteString(base64.StdEncoding.EncodeToString(qrBuf.Bytes()))
	}

	return fndapi.MfaCredentials{
		QrCodeB64Data: qrUrl.String(),
		ChallengeId:   challengeId,
	}, nil
}

func MfaCreateChallenge(username string) (string, bool) {
	return db.NewTx2(func(tx *db.Transaction) (string, bool) {
		return mfaCreateInternalChallenge(tx, username, util.OptNone[int]())
	})
}

func MfaIsConnected(actor rpc.Actor) bool {
	return MfaIsConnectedEx(actor.Username)
}

func MfaIsConnectedEx(username string) bool {
	return db.NewTx(func(tx *db.Transaction) bool {
		_, ok := db.Get[struct{ Number int }](
			tx,
			`
				select 1 as number
				from auth.two_factor_credentials
				where
					principal_id = :username
					and enforced = true
		    `,
			db.Params{
				"username": username,
			},
		)

		return ok
	})
}

func MfaAnswerChallenge(r *http.Request, w http.ResponseWriter, challengeId string, answer string) *util.HttpError {
	// TODO Might want another layer of rate limiting here just to be absolutely sure that this function is
	//   rate-limited.
	tokens, didUpgrade, err := db.NewTx3(func(tx *db.Transaction) (fndapi.AuthenticationTokens, bool, *util.HttpError) {
		tokens := fndapi.AuthenticationTokens{}

		if rand.Intn(100) == 1 {
			db.Exec(
				tx,
				`
					delete from auth.two_factor_challenges
					where now() > expires_at
			    `,
				db.Params{},
			)
		}

		row, ok := db.Get[struct {
			Id               int
			Enforced         bool
			SharedSecret     string
			PrincipalId      string
			HasEnforcedCreds bool
		}](
			tx,
			`
				select
					cred.id, cred.enforced, cred.shared_secret, cred.principal_id, 
					enforced_creds.id is not null has_enforced_creds
				from
					auth.two_factor_challenges challenge
					join auth.two_factor_credentials cred on challenge.credentials_id = cred.id
					left join auth.two_factor_credentials enforced_creds on 
						cred.principal_id = enforced_creds.principal_id 
						and enforced_creds.enforced = true
				where
					challenge.challenge_id = :challenge_id
					and now() < challenge.expires_at
		    `,
			db.Params{
				"challenge_id": challengeId,
			},
		)

		if !ok {
			return tokens, false, util.HttpErr(http.StatusNotFound, "Challenge expired. Try reloading the page.")
		}

		if !row.Enforced && row.HasEnforcedCreds {
			// TODO(Dan): I really think a DB constraint is required for this to work correctly.
			// The check still makes sense to do, but I don't think there are any guarantees that you cannot get two
			// enforced credentials on your account. I don't see any immediate dangers of this, but it is confusing to
			// end-users.
			return tokens, false, util.HttpErr(http.StatusNotFound, "You already have 2FA setup on this account.")
		}

		principal, ok := PrincipalRetrieve(tx, row.PrincipalId)
		if !ok {
			return tokens, false, util.HttpErr(http.StatusInternalServerError, "Internal error.")
		}

		ok = totp.Validate(answer, row.SharedSecret)
		if !ok {
			return tokens, false, util.HttpErr(http.StatusForbidden, "Invalid 2FA code. Try again.")
		}

		db.Exec(
			tx,
			`
				delete from auth.two_factor_challenges
				where challenge_id = :challenge_id
		    `,
			db.Params{
				"challenge_id": challengeId,
			},
		)

		if !row.Enforced {
			db.Exec(
				tx,
				`
					update auth.two_factor_credentials
					set enforced = true
					where id = :id
			    `,
				db.Params{
					"id": row.Id,
				},
			)
		} else {
			tokens = SessionCreate(r, tx, principal)
		}

		return tokens, !row.Enforced, nil
	})

	if err != nil {
		return err
	} else {
		if didUpgrade {
			w.WriteHeader(http.StatusNoContent)
		} else {
			SessionLoginResponse(r, w, tokens, SessionLoginMfaComplete)
		}
		return nil
	}
}

func mfaCreateInternalChallenge(tx *db.Transaction, username string, credentialsId util.Option[int]) (string, bool) {
	challengeId := util.RandomTokenNoTs(32)

	_, ok := db.Get[struct{ ChallengeId string }](
		tx,
		`
			with
			  requested_credentials as (
				  select cast(:credentials_id as bigint) as id
			  )
			insert into auth.two_factor_challenges(dtype, challenge_id, expires_at, credentials_id, service)
			select
			  'LOGIN',
			  :challenge_id,
			  now() + cast('10 minutes' as interval),
			  cred.id,
			  null
			from
			  requested_credentials req
			  join auth.two_factor_credentials cred on cred.principal_id = :username
			where
			  (req.id >= 0 and req.id = cred.id)
			  or (cred.enforced = true)
			returning challenge_id
	    `,
		db.Params{
			"credentials_id": credentialsId.GetOrDefault(-1),
			"username":       username,
			"challenge_id":   challengeId,
		},
	)

	return challengeId, ok
}
