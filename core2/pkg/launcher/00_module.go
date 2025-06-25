package launcher

import (
	"fmt"
	"github.com/golang-jwt/jwt/v5"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
	acc "ucloud.dk/core/pkg/accounting"
	cfg "ucloud.dk/core/pkg/config"
	fnd "ucloud.dk/core/pkg/foundation"
	"ucloud.dk/core/pkg/migrations"
	orc "ucloud.dk/core/pkg/orchestrator"
	gonjautil "ucloud.dk/gonja/v2/utils"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
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
	migrations.Init()
	db.Migrate()

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

	claimsToActor := func(subject string, project util.Option[string], claims rpc.CorePrincipalBaseClaims) (rpc.Actor, bool) {
		sessionReference := claims.SessionReference

		var role rpc.Role

		switch claims.Role {
		case "USER":
			role = rpc.RoleUser

		case "ADMIN":
			role = rpc.RoleAdmin

		case "PROVIDER":
			role = rpc.RoleProvider

		case "SERVICE":
			role = rpc.RoleService

		default:
			return rpc.Actor{}, false
		}

		if _, ok := claims.Membership[rpc.ProjectId(project.Value)]; project.Present && !ok {
			project.Clear()
		}

		return rpc.Actor{
			Username:         subject,
			Role:             role,
			Project:          project,
			Membership:       claims.Membership,
			Groups:           claims.Groups,
			ProviderProjects: claims.ProviderProjects,
			Domain:           claims.Domain,
			OrgId:            claims.OrgId.Value,
			TokenInfo: util.OptValue(rpc.TokenInfo{
				PublicSessionReference: sessionReference.Value,
			}),
		}, true
	}

	rpc.ServerAuthenticator = func(r *http.Request) (rpc.Actor, *util.HttpError) {
		authHeader := r.Header.Get("Authorization")
		jwtToken, ok := strings.CutPrefix(authHeader, "Bearer ")
		if !ok {
			return rpc.Actor{Role: rpc.RoleGuest}, nil
		}

		claims := &rpc.CorePrincipalClaims{}

		tok, err := jwtParser.ParseWithClaims(jwtToken, claims, jwtKeyFunc)
		if err != nil {
			return rpc.Actor{Role: rpc.RoleGuest}, nil
		}

		subject, err := tok.Claims.GetSubject()
		if err != nil {
			return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "Bad token supplied")
		}

		issuedAt, err := claims.GetIssuedAt()
		if err != nil {
			return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "Bad token supplied")
		}

		expiresAt, err := claims.GetExpirationTime()
		if err != nil {
			return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "Bad token supplied")
		}

		// This is not needed, but let's be paranoid and check it anyway.
		if time.Now().After(expiresAt.Time) {
			return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "Token has expired")
		}

		project := util.OptStringIfNotEmpty(r.Header.Get("Project"))

		actor, ok := claimsToActor(subject, project, claims.CorePrincipalBaseClaims)
		if !ok {
			return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "Bad token supplied")
		} else {
			actor.TokenInfo.Value.ExpiresAt = expiresAt.Time
			actor.TokenInfo.Value.IssuedAt = issuedAt.Time
			return actor, nil
		}
	}

	rpc.AuditConsumer = func(event rpc.HttpCallLogEntry) {
		log.Info("%v/%v %v ms", event.RequestName, event.ResponseCode, event.ResponseTime)
		/*
			data, _ := json.MarshalIndent(event, "", "    ")
			log.Info("Audit: %s", string(data))
		*/
	}

	rpc.LookupActor = func(username string) (rpc.Actor, bool) {
		resp, err := fndapi.AuthLookupUser.Invoke(fndapi.FindByStringId{Id: username})
		if err != nil {
			return rpc.Actor{}, false
		} else {
			actor, ok := claimsToActor(username, util.OptNone[string](), resp)
			return actor, ok
		}
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

	rpc.DefaultClient = &rpc.Client{
		RefreshToken: cfg.Configuration.RefreshToken,
		BasePath:     cfg.Configuration.SelfAddress.ToURL(),
		Client:       &http.Client{},
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
	orc.Init()

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
