package launcher

import (
	"fmt"
	"github.com/golang-jwt/jwt/v5"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
	acc "ucloud.dk/core/pkg/account
	cfg "ucloud.dk/core/pkg/config"
	fnd "ucloud.dk/core/pkg/foundation"
	gonjautil "ucloud.dk/gonja/v2/utils"
	db "ucloud.dk/shared/pkg/database"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func readFromMap[T any](input map[string]any, key string) (T, bool) {
	var dummy T
	value, ok := input[key]
	if !ok {
		return dummy, false
	}

	result, ok := value.(T)
	if !ok {
		return dummy, false
	}

	return result, true
}

func Launch() {
	if os.Getenv("UCLOUD_EARLY_DEBUG") != "" {
		fmt.Printf("Ready for debugger\n")
		keepWaiting := true

		//goland:noinspection GoBoolExpressions
		for keepWaiting {
			// Break this loop via the debugger (the debugger can change the value of keepWaiting).
			time.Sleep(10 * time.Millisecond)
		}
	}

	ok := cfg.Parse("/etc/ucloud")
	if !ok {
		return
	}

	dbConfig := cfg.Configuration.Database
	db.Database = db.Connect(
		dbConfig.Username,
		dbConfig.Password,
		dbConfig.Host.Address,
		dbConfig.Host.Port,
		dbConfig.Database,
		dbConfig.Ssl,
	)
	db.Database.Connection.MapperFunc(util.ToSnakeCase)

	var jwtKeyFunc jwt.Keyfunc
	var jwtMethods []string

	if cfg.Configuration.TokenValidation.SharedSecret != "" {
		jwtMethods = []string{jwt.SigningMethodHS512.Name}
		jwtKeyFunc = func(token *jwt.Token) (interface{}, error) {
			return []byte(cfg.Configuration.TokenValidation.SharedSecret), nil
		}
	} else if cfg.Configuration.TokenValidation.PublicCertificate != "" {
		log.Info("Not yet implemented")
		os.Exit(1)
	} else {
		log.Info("Bad token validation supplied")
		os.Exit(1)
	}

	jwtParser := jwt.NewParser(
		jwt.WithIssuer("cloud.sdu.dk"),
		jwt.WithValidMethods(jwtMethods),
		jwt.WithIssuedAt(),
		jwt.WithExpirationRequired(),
	)

	rpc.DefaultServer.Mux = http.NewServeMux()
	rpc.ServerAuthenticator = func(r *http.Request) (rpc.Actor, *util.HttpError) {
		authHeader := r.Header.Get("Authorization")
		jwtToken, ok := strings.CutPrefix(authHeader, "Bearer ")
		if !ok {
			return rpc.Actor{Role: rpc.RoleGuest}, nil
		}

		tok, err := jwtParser.Parse(jwtToken, jwtKeyFunc)
		if err != nil {
			return rpc.Actor{Role: rpc.RoleGuest}, nil
		}

		subject, err := tok.Claims.GetSubject()
		if err != nil {
			return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "Bad token supplied")
		}

		claims, ok := tok.Claims.(jwt.MapClaims)
		if !ok {
			return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "Bad token supplied")
		}

		roleStr, ok := readFromMap[string](claims, "role")
		if !ok {
			return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "Bad token supplied")
		}

		sessionReference, ok := readFromMap[string](claims, "publicSessionReference")
		if !ok {
			return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "Bad token supplied")
		}

		issuedAtRaw, ok := readFromMap[float64](claims, "iat")
		if !ok {
			return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "Bad token supplied")
		}

		issuedAt := time.UnixMilli(int64(issuedAtRaw) * 1000)

		expiresAtRaw, ok := readFromMap[float64](claims, "exp")
		if !ok {
			return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "Bad token supplied")
		}

		expiresAt := time.UnixMilli(int64(expiresAtRaw) * 1000)

		// This is not needed, but let's be paranoid and check it anyway.
		if time.Now().After(expiresAt) {
			return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "Token has expired")
		}

		var role rpc.Role

		switch roleStr {
		case "USER":
			role = rpc.RoleUser

		case "ADMIN":
			role = rpc.RoleAdmin

		case "PROVIDER":
			role = rpc.RoleProvider

		case "SERVICE":
			role = rpc.RoleService

		default:
			return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "Bad token supplied")
		}

		project := util.OptStringIfNotEmpty(r.Header.Get("Project"))

		return rpc.Actor{
			Username: subject,
			Role:     role,
			Project:  project,
			TokenInfo: util.OptValue(rpc.TokenInfo{
				IssuedAt:               issuedAt,
				ExpiresAt:              expiresAt,
				PublicSessionReference: sessionReference,
			}),
		}, nil
	}

	rpc.AuditConsumer = func(event rpc.HttpCallLogEntry) {
		log.Info("%v/%v %v ms", event.RequestName, event.ResponseCode, event.ResponseTime)
		/*
			data, _ := json.MarshalIndent(event, "", "    ")
			log.Info("Audit: %s", string(data))
		*/
	}

	logCfg := cfg.Configuration.Logs
	log.SetLogConsole(false)
	err := log.SetLogFile(filepath.Join(logCfg.Directory, "server.log"))
	if err != nil {
		panic("Unable to open log file: " + err.Error())
	}

	if logCfg.Rotation.Enabled {
		log.SetRotation(log.RotateDaily, logCfg.Rotation.RetentionPeriodInDays, true)
	}

	// Jinja
	// -----------------------------------------------------------------------------------------------------------------
	// NOTE(Dan): Annoyingly, this currently needs to be set for the entire executable, meaning that we can only choose
	// one mode of escape for Jinja. This is currently set to HTML and is used by the mail related code of the
	// foundation layer.
	gonjautil.EscapeMode = gonjautil.EscapeModeHtml

	// Services
	// -----------------------------------------------------------------------------------------------------------------

	fnd.Init()
	acc.Init()

	launchMetricsServer()

	log.Info("UCloud is ready!")

	err = http.ListenAndServe("0.0.0.0:8080", http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		handler, _ := rpc.DefaultServer.Mux.Handler(request)
		handler.ServeHTTP(writer, request)
	}))

	log.Warn("Failed to start listener: %s", err)
	os.Exit(1)
}

var metricsServerHandler func(writer http.ResponseWriter, request *http.Request) = nil

func launchMetricsServer() {
	go func() {
		http.HandleFunc("/metrics", func(writer http.ResponseWriter, request *http.Request) {
			if metricsServerHandler != nil {
				metricsServerHandler(writer, request)
			} else {
				writer.WriteHeader(http.StatusNotFound)
			}
		})
		err := http.ListenAndServe(":7867", nil)
		if err != nil {
			log.Warn("Prometheus metrics server has failed unexpectedly! %v", err)
		}
	}()
}
