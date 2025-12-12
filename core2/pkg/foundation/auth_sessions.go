package foundation

import (
	"database/sql"
	_ "embed"
	"encoding/json"
	"fmt"
	"math/rand"
	"net"
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
	ip := ClientIP(r, ClientIPConfig{
		TrustedProxies: []string{
			"10.0.0.0/8",
			"172.16.0.0/12",
			"192.168.0.0/16",
			"127.0.0.1",
			"::1",
		},
		AllowPrivate: false,
	}).String()
	userAgent := r.UserAgent()
	refreshToken := util.RandomTokenNoTs(32)
	csrfToken := util.RandomTokenNoTs(32)
	sessionReference := util.RandomTokenNoTs(32)

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
		mfaRequired = mfaRequired && MfaIsConnectedEx(session.Username)
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

// ClientIPConfig controls when and how proxy headers are trusted.
type ClientIPConfig struct {
	// TrustedProxies are IPs or CIDRs (e.g. "10.0.0.0/8", "192.168.1.10", "fd00::/8")
	// that are allowed to supply Forwarded/X-Forwarded-For/X-Real-IP.
	TrustedProxies []string

	// If true, allows private/loopback IPs from headers; usually keep false for logging/rate-limit purposes.
	AllowPrivate bool
}

type cidrOrIP struct {
	ip   net.IP
	netw *net.IPNet
}

func parseTrusted(list []string) []cidrOrIP {
	out := make([]cidrOrIP, 0, len(list))
	for _, s := range list {
		s = strings.TrimSpace(s)
		if s == "" {
			continue
		}
		if strings.Contains(s, "/") {
			_, n, err := net.ParseCIDR(s)
			if err == nil {
				out = append(out, cidrOrIP{netw: n})
			}
			continue
		}
		if ip := net.ParseIP(s); ip != nil {
			out = append(out, cidrOrIP{ip: ip})
		}
	}
	return out
}

func isTrustedProxy(remoteIP net.IP, trusted []cidrOrIP) bool {
	if remoteIP == nil {
		return false
	}
	for _, t := range trusted {
		if t.ip != nil && t.ip.Equal(remoteIP) {
			return true
		}
		if t.netw != nil && t.netw.Contains(remoteIP) {
			return true
		}
	}
	return false
}

func parseRemoteAddrIP(remoteAddr string) net.IP {
	host, _, err := net.SplitHostPort(strings.TrimSpace(remoteAddr))
	if err != nil {
		// Might already be a bare IP without port.
		return net.ParseIP(strings.TrimSpace(remoteAddr))
	}
	return net.ParseIP(host)
}

func isPrivateOrLoopback(ip net.IP) bool {
	if ip == nil {
		return true
	}
	ip = ip.To16()
	if ip == nil {
		return true
	}
	// IPv4-mapped addresses should still work with IsPrivate/IsLoopback in modern Go,
	// but normalize for safety.
	if v4 := ip.To4(); v4 != nil {
		ip = v4
	}
	return ip.IsLoopback() || ip.IsPrivate() || ip.IsLinkLocalUnicast() || ip.IsLinkLocalMulticast()
}

func cleanIPToken(s string) net.IP {
	s = strings.TrimSpace(s)
	if s == "" {
		return nil
	}
	// Forwarded: for= may be quoted and may include port.
	s = strings.Trim(s, "\"")
	s = strings.TrimPrefix(s, "for=")

	// Remove IPv6 brackets [::1]:1234
	s = strings.TrimPrefix(s, "[")
	if idx := strings.IndexByte(s, ']'); idx >= 0 {
		s = s[:idx]
	}

	// Remove :port for IPv4/hostname-ish tokens (best-effort)
	if h, _, err := net.SplitHostPort(s); err == nil {
		s = h
	}

	// Remove possible obfuscated identifiers (Forwarded allows "for=_hidden")
	if strings.HasPrefix(s, "_") {
		return nil
	}

	return net.ParseIP(s)
}

// ClientIP returns the best-effort client IP.
// It only trusts proxy headers if the TCP peer (RemoteAddr) is a trusted proxy.
func ClientIP(r *http.Request, cfg ClientIPConfig) net.IP {
	remoteIP := parseRemoteAddrIP(r.RemoteAddr)
	trusted := parseTrusted(cfg.TrustedProxies)

	// If we can't trust the proxy, do not read forwarded headers.
	if !isTrustedProxy(remoteIP, trusted) {
		return remoteIP
	}

	// 1) RFC 7239 Forwarded header (may appear multiple times, comma-separated)
	// Example: Forwarded: for=203.0.113.60;proto=https;by=203.0.113.43
	if fwd := r.Header.Values("Forwarded"); len(fwd) > 0 {
		joined := strings.Join(fwd, ",")
		parts := strings.Split(joined, ",")
		for _, p := range parts {
			// Find "for=" parameter inside this element.
			semi := strings.Split(p, ";")
			for _, kv := range semi {
				kv = strings.TrimSpace(kv)
				if strings.HasPrefix(strings.ToLower(kv), "for=") {
					ip := cleanIPToken(kv)
					if ip == nil {
						continue
					}
					if !cfg.AllowPrivate && isPrivateOrLoopback(ip) {
						continue
					}
					return ip
				}
			}
		}
	}

	// 2) X-Forwarded-For: client, proxy1, proxy2
	// We typically want the left-most valid IP. If you have multiple proxy layers you control,
	// this still works as long as your edge proxy preserves the chain.
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		for _, token := range strings.Split(xff, ",") {
			ip := cleanIPToken(token)
			if ip == nil {
				continue
			}
			if !cfg.AllowPrivate && isPrivateOrLoopback(ip) {
				continue
			}
			return ip
		}
	}

	// 3) X-Real-IP (often set by nginx)
	if xrip := r.Header.Get("X-Real-IP"); xrip != "" {
		ip := cleanIPToken(xrip)
		if ip != nil && (cfg.AllowPrivate || !isPrivateOrLoopback(ip)) {
			return ip
		}
	}

	// Fallback: TCP peer.
	return remoteIP
}
