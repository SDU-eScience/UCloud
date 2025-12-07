package foundation

import (
	"math"
	"net/http"
	"time"

	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/util"
)

func PasswordUpdate(tx *db.Transaction, username string, newPassword string, conditionalChange bool, currentPasswordForVerification string) *util.HttpError {
	currentPasswordAndSalt, ok := db.Get[struct {
		HashedPassword []byte `json:"hashedPassword"`
		Salt           []byte `json:"salt"`
	}](
		tx,
		`
				select p.hashed_password, p.salt
				from auth.principals p
				where p.id = :username
			`,
		db.Params{
			"username": username,
		},
	)
	if !ok {
		return util.HttpErr(http.StatusBadRequest, "Cannot change password for this user")
	}

	currentPassword := currentPasswordAndSalt.HashedPassword
	currentSalt := currentPasswordAndSalt.Salt

	if conditionalChange {
		isValidPassword := util.CheckPassword(currentPassword, currentSalt, currentPasswordForVerification)
		if !isValidPassword {
			return util.HttpErr(http.StatusBadRequest, "Invalid username or password")
		}
	}

	generatedSalt := util.GenSalt()
	newPasswordAndSalt := util.HashPassword(newPassword, generatedSalt)
	db.Exec(
		tx,
		`
			update auth.principals
				set
					hashed_password = :hashed,
					salt = :salt,
					modified_at = now()
				where
					id = :id
		`,
		db.Params{
			"id":     username,
			"salt":   newPasswordAndSalt.Salt,
			"hashed": newPasswordAndSalt.HashedPassword,
		},
	)
	return nil
}

var dummyPasswordForTiming = util.HashPassword("forcomparison1234!", nil)

func PasswordLogin(request *http.Request, username string, password string) (fndapi.AuthenticationTokens, *util.HttpError) {
	return db.NewTx2(func(tx *db.Transaction) (fndapi.AuthenticationTokens, *util.HttpError) {
		if !loginAllowed(tx, request, username) {
			return fndapi.AuthenticationTokens{}, util.HttpErr(http.StatusTooManyRequests, "Please wait before trying again.")
		}

		principal, ok := PrincipalRetrieve(tx, username)
		if !ok || !principal.HashedPassword.Present || !principal.Salt.Present {
			util.CheckPassword(dummyPasswordForTiming.HashedPassword, dummyPasswordForTiming.Salt, password)
			logPasswordAttempt(tx, request, username)
			return fndapi.AuthenticationTokens{}, util.HttpErr(http.StatusUnauthorized, "Incorrect username or password.")
		}

		if !util.CheckPassword(principal.HashedPassword.Value, principal.Salt.Value, password) {
			logPasswordAttempt(tx, request, username)
			return fndapi.AuthenticationTokens{}, util.HttpErr(http.StatusUnauthorized, "Incorrect username or password.")
		}

		session := SessionCreate(request, tx, principal)
		return session, nil
	})
}

func logPasswordAttempt(tx *db.Transaction, request *http.Request, username string) {
	db.Exec(
		tx,
		`
			insert into auth.login_attempts(id, created_at, username) 
			values (nextval('auth.hibernate_sequence'), now(), :username)
	    `,
		db.Params{
			"username": username,
		},
	)
	loginAllowed(tx, request, username)
}

func loginAllowed(tx *db.Transaction, request *http.Request, username string) bool {
	cooldown, hasRecentCooldown := findLoginAttemptCooldown(tx, username)
	if hasRecentCooldown && cooldown.AllowLoginsAfter.After(time.Now()) {
		return false
	}

	tsForRelevantAttempts := time.Now().Add(time.Duration(-1) * loginAttemptObservationWindow)
	if cooldown.AllowLoginsAfter.After(tsForRelevantAttempts) {
		tsForRelevantAttempts = cooldown.AllowLoginsAfter
	}

	attemptsRow, ok := db.Get[struct{ RecentAttempts int64 }](
		tx,
		`
			select count(*) as recent_attempts
			from auth.login_attempts
			where
				username = :username
				and created_at >= to_timestamp(:time / 1000.0)
	    `,
		db.Params{
			"time":     tsForRelevantAttempts.UnixMilli(),
			"username": username,
		},
	)

	attempts := int64(0)
	if ok {
		attempts = attemptsRow.RecentAttempts
	}

	if attempts >= loginAttemptLockoutThreshold {
		severity := 0
		if hasRecentCooldown {
			severity = cooldown.Severity
		}

		severity = min(severity+1, loginAttemptMaxSeverity)

		lockoutDuration := time.Duration(math.Pow(loginAttemptLockoutDurationBase.Seconds(), float64(severity))) * time.Second
		allowLoginsAfter := time.Now().Add(lockoutDuration)
		expiresAt := time.Now().Add(loginAttemptCooldown)

		db.Exec(
			tx,
			`
				insert into auth.login_cooldown(id, allow_logins_after, expires_at, severity, username) 
				values (
					nextval('auth.hibernate_sequence'), 
					to_timestamp(:allow_logins_after / 1000.0), 
					to_timestamp(:expires_at / 1000.0),
					:severity,
					:username
				)
		    `,
			db.Params{
				"allow_logins_after": allowLoginsAfter.UnixMilli(),
				"expires_at":         expiresAt.UnixMilli(),
				"severity":           severity,
				"username":           username,
			},
		)

		return false
	}

	return true
}

type loginCooldown struct {
	Username         string
	ExpiresAt        time.Time
	AllowLoginsAfter time.Time
	Severity         int
	Id               int8
}

func findLoginAttemptCooldown(tx *db.Transaction, username string) (loginCooldown, bool) {
	return db.Get[loginCooldown](
		tx,
		`
			select *
			from auth.login_cooldown
			where
				username = :username
				and expires_at > now()
	    `,
		db.Params{
			"username": username,
		},
	)
}

const (
	loginAttemptLockoutThreshold    = 5
	loginAttemptLockoutDurationBase = 5 * time.Second
	loginAttemptObservationWindow   = 5 * time.Minute
	loginAttemptCooldown            = 1 * time.Hour
	loginAttemptMaxSeverity         = 5 // leads to a lockout period of roughly one hour
)
