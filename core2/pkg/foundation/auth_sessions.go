package foundation

import (
	"database/sql"
	_ "embed"
	"encoding/json"
	"fmt"
	"math/rand"
	"net/http"
	"net/url"
	"strings"
	"time"

	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func SessionCreate(r *http.Request, tx *db.Transaction, principal Principal) fndapi.AuthenticationTokens {
	ip := r.RemoteAddr
	userAgent := r.UserAgent()
	refreshToken := util.RandomTokenNoTs(32)
	csrfToken := util.RandomTokenNoTs(32)
	sessionReference := util.RandomTokenNoTs(32)

	// TODO (just to find it later) This is down significantly from the old core
	expiresAfter := 14 * 24 * time.Hour

	db.Exec(
		tx,
		`
			insert into auth.refresh_tokens
				(token, associated_user_id, csrf, public_session_reference, extended_by, 
				scopes, expires_after, refresh_token_expiry, extended_by_chain, created_at, 
				ip, user_agent) 
			values
				(:token, :username, :csrf, :session_reference, null,
					:scope, :access_expires_after, :refresh_expires_after, '[]', now(), 
					:ip, :user_agent)
		`,
		db.Params{
			"token":                 refreshToken,
			"username":              principal.Id,
			"csrf":                  csrfToken,
			"session_reference":     sessionReference,
			"access_expires_after":  (10 * time.Minute).Milliseconds(),
			"refresh_expires_after": expiresAfter.Milliseconds(),
			"ip":                    ip,
			"user_agent":            userAgent,
			"scope":                 "[\"all:write\"]",
		},
	)

	accessTok := SignPrincipalToken(principal, util.OptValue(sessionReference))

	return fndapi.AuthenticationTokens{
		Username:     principal.Id,
		RefreshToken: refreshToken,
		AccessToken:  accessTok,
		CsrfToken:    util.OptValue(csrfToken),
		ExpiresAt:    fndapi.Timestamp(time.Now().Add(expiresAfter)),
	}
}

func SessionRefresh(request fndapi.AuthenticationTokens) (fndapi.AccessTokenAndCsrf, *util.HttpError) {
	return db.NewTx2(func(tx *db.Transaction) (fndapi.AccessTokenAndCsrf, *util.HttpError) {
		if rand.Intn(1000) == 1 {
			db.Exec(
				tx,
				`
					delete from auth.refresh_tokens
					where
					    refresh_token_expiry is not null
						and now() > (created_at + cast((refresh_token_expiry || 'ms') as interval))
			    `,
				db.Params{},
			)
		}

		row, ok := db.Get[struct {
			Username               string
			CsrfToken              string
			PublicSessionReference sql.NullString
		}](
			tx,
			`
				select associated_user_id as username, csrf as csrf_token, public_session_reference
				from auth.refresh_tokens
				where
					token = :token
					and (
						:csrf = ''
						or csrf = :csrf
					)
					and (
					    (created_at + cast((refresh_token_expiry || 'ms') as interval)) > now()
					    or refresh_token_expiry is null
					)
						
		    `,
			db.Params{
				"token": request.RefreshToken,
				"csrf":  request.CsrfToken.Value,
			},
		)

		if ok {
			principal, ok := PrincipalRetrieve(tx, row.Username)
			if ok {
				sessionReference := util.OptNone[string]()
				if row.PublicSessionReference.Valid {
					sessionReference.Set(row.PublicSessionReference.String)
				}
				return fndapi.AccessTokenAndCsrf{
					AccessToken: SignPrincipalToken(principal, sessionReference),
					CsrfToken:   row.CsrfToken,
				}, nil
			}
		}

		return fndapi.AccessTokenAndCsrf{}, util.HttpErr(http.StatusUnauthorized, "Unauthorized")
	})
}

func SessionLogout(request fndapi.AuthenticationTokens) *util.HttpError {
	return db.NewTx(func(tx *db.Transaction) *util.HttpError {
		db.Exec(
			tx,
			`
				delete from auth.refresh_tokens
				where
					token = :token
					and (
						:csrf = ''
						or csrf = :csrf
					)
		    `,
			db.Params{
				"token": request.RefreshToken,
				"csrf":  request.CsrfToken.GetOrDefault(""),
			},
		)

		return nil
	})
}

//go:embed auth_redirect.html
var authRedirectPage []byte

type SessionLoginFlag int

const (
	SessionLoginMfaComplete SessionLoginFlag = 1 << iota
)

func SessionLoginResponse(r *http.Request, w http.ResponseWriter, session fndapi.AuthenticationTokens, flags SessionLoginFlag) {
	respondWithJson := strings.Contains(r.Header.Get("Accept"), "application/json")
	scheme := getScheme(r)
	secureScheme := scheme == "https"

	respondViaCookieRedirect := func(response any) {
		responseJson, _ := json.Marshal(response)
		encoded := url.QueryEscape(string(responseJson))

		http.SetCookie(w, &http.Cookie{
			Name:     "authState",
			Value:    encoded,
			Secure:   secureScheme,
			HttpOnly: false,
			Expires:  time.Now().Add(5 * time.Minute),
			Path:     "/",
			SameSite: http.SameSiteStrictMode,
		})

		// NOTE(Dan): Using a 301 redirect causes Apple browsers (at least Safari likely more) to ignore the cookie.
		// Using a redirect via HTML works.
		// NOTE(Dan): The WAYF name is mostly for legacy reasons. It doesn't really mean anything in this context.
		endpoint := fmt.Sprintf("%s://%s/app/login/wayf", scheme, r.Host)
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(fmt.Sprintf(string(authRedirectPage), endpoint, endpoint)))
	}

	mfaChallenge := ""
	mfaRequired := false
	if flags&SessionLoginMfaComplete == 0 {
		mfaChallenge, mfaRequired = MfaCreateChallenge(session.Username)
	}

	if mfaRequired {
		response := map[string]string{"2fa": mfaChallenge}

		if respondWithJson {
			rpc.SendResponseOrError(r, w, response, nil)
		} else {
			respondViaCookieRedirect(response)
		}
	} else {
		http.SetCookie(w, &http.Cookie{
			Name:     "refreshToken",
			Value:    session.RefreshToken,
			Secure:   secureScheme,
			HttpOnly: true,
			Expires:  session.ExpiresAt.Time(),
			Path:     "/",
			SameSite: http.SameSiteStrictMode,
		})

		session.RefreshToken = ""

		if respondWithJson {
			w.Header().Set("Access-Control-Allow-Credentials", "true")
			rpc.SendResponseOrError(r, w, session, nil)
		} else {
			// NOTE(Dan): This will happen if we get a redirect from OIDC
			respondViaCookieRedirect(session)
		}
	}
}

func getScheme(r *http.Request) string {
	if proto := r.Header.Get("X-Forwarded-Proto"); proto != "" {
		return proto
	}

	if r.TLS != nil {
		return "https"
	}

	return "http"
}

func SessionListInfo(actor rpc.Actor) fndapi.Page[fndapi.AuthSessionInfo] {
	info := db.NewTx(func(tx *db.Transaction) []fndapi.AuthSessionInfo {
		rows := db.Select[struct {
			Ip        string
			UserAgent string
			CreatedAt time.Time
		}](
			tx,
			`
				select ip, user_agent, created_at
				from auth.refresh_tokens
				where
					associated_user_id = :username
					and extended_by is null
		    `,
			db.Params{
				"username": actor.Username,
			},
		)

		var result []fndapi.AuthSessionInfo
		for _, row := range rows {
			result = append(result, fndapi.AuthSessionInfo{
				IpAddress: row.Ip,
				UserAgent: row.UserAgent,
				CreatedAt: fndapi.Timestamp(row.CreatedAt),
			})
		}

		return result
	})

	return fndapi.Page[fndapi.AuthSessionInfo]{
		ItemsInTotal: len(info),
		Items:        info,
		PageNumber:   0,
		ItemsPerPage: len(info),
	}
}

func SessionInvalidateAll(actor rpc.Actor) {
	db.NewTx0(func(tx *db.Transaction) {
		db.Exec(
			tx,
			`
				delete from auth.refresh_tokens
				where
					associated_user_id = :username
					and extended_by is null
		    `,
			db.Params{
				"username": actor.Username,
			},
		)
	})
}
