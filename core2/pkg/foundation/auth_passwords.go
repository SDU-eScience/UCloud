package foundation

import (
	"crypto/rand"
	"crypto/sha512"
	"crypto/subtle"
	"golang.org/x/crypto/pbkdf2"
	"math"
	"net/http"
	"time"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/util"
)

const (
	saltLength = 16
	iterations = 10000
	keyLength  = 256 // bits
)

type HashedPasswordAndSalt struct {
	HashedPassword []byte
	Salt           []byte
}

func genSalt() []byte {
	salt := make([]byte, saltLength)
	_, err := rand.Read(salt)
	if err != nil {
		panic(err)
	}
	return salt
}

// hashPassword hashes a password with the given salt, or generates a new salt if not provided
func hashPassword(password string, salt []byte) HashedPasswordAndSalt {
	if salt == nil {
		salt = genSalt()
	}

	passwordBytes := []byte(password)
	hashed := pbkdf2.Key(passwordBytes, salt, iterations, keyLength/8, sha512.New)

	return HashedPasswordAndSalt{
		HashedPassword: hashed,
		Salt:           salt,
	}
}

// checkPassword compares the stored correctPassword with the hash of the plainPassword
func checkPassword(correctPassword, salt []byte, plainPassword string) bool {
	incoming := hashPassword(plainPassword, salt)
	// Use constant time comparison
	if subtle.ConstantTimeCompare(incoming.HashedPassword, correctPassword) == 1 {
		return true
	}
	return false
}

var dummyPasswordForTiming = hashPassword("forcomparison1234!", nil)

func PasswordLogin(request *http.Request, username string, password string) (fndapi.AuthenticationTokens, *util.HttpError) {
	return db.NewTx2(func(tx *db.Transaction) (fndapi.AuthenticationTokens, *util.HttpError) {
		if !loginAllowed(tx, request, username) {
			return fndapi.AuthenticationTokens{}, util.HttpErr(http.StatusTooManyRequests, "Please wait before trying again.")
		}

		principal, ok := LookupPrincipal(tx, username)
		if !ok || !principal.HashedPassword.Present || !principal.Salt.Present {
			checkPassword(dummyPasswordForTiming.HashedPassword, dummyPasswordForTiming.Salt, password)
			logPasswordAttempt(tx, request, username)
			return fndapi.AuthenticationTokens{}, util.HttpErr(http.StatusUnauthorized, "Incorrect username or password.")
		}

		if !checkPassword(principal.HashedPassword.Value, principal.Salt.Value, password) {
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

		severity = max(severity+1, loginAttemptMaxSeverity)

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
