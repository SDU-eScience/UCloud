package launcher

import (
	"crypto/rsa"
	"crypto/x509"
	"encoding/pem"
	"fmt"
	"github.com/golang-jwt/jwt/v5"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
	acc "ucloud.dk/core/pkg/accounting"
	cfg "ucloud.dk/core/pkg/config"
	fnd "ucloud.dk/core/pkg/foundation"
	"ucloud.dk/core/pkg/migrations"
	orc "ucloud.dk/core/pkg/orchestrator"
	gonjautil "ucloud.dk/gonja/v2/utils"
	db "ucloud.dk/shared/pkg/database2"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

func Launch() {
	if os.Getenv("UCLOUD_EARLY_DEBUG") != "" {
		fmt.Printf("Waiting for debugger - UCloud will not start without a debugger\n")
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
		log.Info("Not yet implemented") // TODO
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

	providerJwtParserCacheInit()

	claimsToActor := func(subject string, project util.Option[rpc.ProjectId], claims rpc.CorePrincipalBaseClaims) (rpc.Actor, bool) {
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

		if _, ok := claims.Membership[project.Value]; project.Present && !ok {
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

	rpc.BearerAuthenticator = func(bearer string, projectHeader string) (rpc.Actor, *util.HttpError) {
		if bearer == "" {
			return rpc.Actor{Role: rpc.RoleGuest}, nil
		}

		unverifiedSubject := ""
		{
			unverifiedTok, _, err := jwtParser.ParseUnverified(bearer, &jwt.MapClaims{})
			if err != nil {
				return rpc.Actor{Role: rpc.RoleGuest}, nil
			}

			unverifiedSubject, err = unverifiedTok.Claims.GetSubject()
			if err != nil {
				return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "Bad token supplied")
			}
		}

		if strings.HasPrefix(unverifiedSubject, fndapi.ProviderSubjectPrefix) {
			verifier, ok := providerJwtParserRetrieve(unverifiedSubject)
			if !ok {
				return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "Bad token supplied")
			}

			tok, err := verifier.Parser.ParseWithClaims(bearer, &jwt.MapClaims{}, verifier.KeyFunc)
			if err != nil {
				return rpc.Actor{Role: rpc.RoleGuest}, nil
			}

			claims := tok.Claims.(*jwt.MapClaims)

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

			actor := rpc.Actor{
				Username: subject,
				Role:     rpc.RoleProvider,
				TokenInfo: util.OptValue[rpc.TokenInfo](rpc.TokenInfo{
					IssuedAt:  issuedAt.Time,
					ExpiresAt: expiresAt.Time,
				}),
			}

			return actor, nil
		} else {
			claims := &rpc.CorePrincipalClaims{}

			tok, err := jwtParser.ParseWithClaims(bearer, claims, jwtKeyFunc)
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

			project := util.OptNone[rpc.ProjectId]()
			if projectHeader != "" {
				project.Set(rpc.ProjectId(projectHeader))
			}

			actor, ok := claimsToActor(subject, project, claims.CorePrincipalBaseClaims)
			if !ok {
				return rpc.Actor{}, util.HttpErr(http.StatusForbidden, "Bad token supplied")
			} else {
				actor.TokenInfo.Value.ExpiresAt = expiresAt.Time
				actor.TokenInfo.Value.IssuedAt = issuedAt.Time
				return actor, nil
			}
		}
	}

	rpc.ServerAuthenticator = func(r *http.Request) (rpc.Actor, *util.HttpError) {
		authHeader := r.Header.Get("Authorization")
		bearer, _ := strings.CutPrefix(authHeader, "Bearer ")
		projectHeader := r.Header.Get("Project")
		return rpc.BearerAuthenticator(bearer, projectHeader)
	}

	rpc.AuditConsumer = func(event rpc.HttpCallLogEntry) {
		log.Info("%v/%v %v", event.RequestName, event.ResponseCode, time.Duration(event.ResponseTimeNanos)*time.Nanosecond)
		/*
			data, _ := json.MarshalIndent(event, "", "    ")
			log.Info("Audit: %s", string(data))
		*/
	}

	if cfg.Configuration.Elk.Elasticsearch.Host.Address != "" {
		elasticConfig := cfg.Configuration

		rpc.AuditConsumer = fnd.InitAuditElasticSearch(*elasticConfig)
	}

	rpc.LookupActor = func(username string) (rpc.Actor, bool) {
		atuple := util.RetryOrPanic("rpc.LookupActor", func() (util.Tuple2[rpc.Actor, bool], error) {
			resp, err := fndapi.AuthLookupUser.Invoke(fndapi.FindByStringId{Id: username})
			if err != nil {
				if err.StatusCode == http.StatusNotFound {
					return util.Tuple2[rpc.Actor, bool]{rpc.Actor{}, false}, nil
				} else {
					return util.Tuple2[rpc.Actor, bool]{}, err
				}
			} else {
				actor, ok := claimsToActor(username, util.OptNone[rpc.ProjectId](), resp)
				return util.Tuple2[rpc.Actor, bool]{actor, ok}, nil
			}
		})

		return atuple.First, atuple.Second
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
		Client: &http.Client{
			Timeout: 10 * time.Second,
		},
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

	err = http.ListenAndServe("0.0.0.0:8080", collapseServerSlashes(
		http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
			handler, _ := rpc.DefaultServer.Mux.Handler(request)
			handler.ServeHTTP(writer, request)
		}),
	))

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

type providerJwtParser struct {
	Parser  *jwt.Parser
	KeyFunc jwt.Keyfunc
}

var providerJwtParserCache struct {
	Mu        sync.RWMutex
	Providers map[string]providerJwtParser
}

func providerJwtParserCacheInit() {
	providerJwtParserCache.Providers = map[string]providerJwtParser{}
}

func providerJwtParserRetrieve(actorName string) (providerJwtParser, bool) {
	providerId, _ := strings.CutPrefix(actorName, fndapi.ProviderSubjectPrefix)
	cache := &providerJwtParserCache

	cache.Mu.RLock()
	cached, ok := cache.Providers[providerId]
	cache.Mu.RUnlock()

	if ok {
		return cached, true
	} else {
		cache.Mu.Lock()
		cached, ok = cache.Providers[providerId]
		if !ok {
			var key string
			key, ok = db.NewTx2(func(tx *db.Transaction) (string, bool) {
				row, ok := db.Get[struct{ PubKey string }](
					tx,
					`
						select pub_key
						from auth.providers
						where id = :provider_id
				    `,
					db.Params{
						"provider_id": providerId,
					},
				)

				return row.PubKey, ok
			})

			if ok {
				pubKey, err := readPublicKey(key)
				if err == nil {
					entry := providerJwtParser{
						Parser: jwt.NewParser(
							jwt.WithIssuer("cloud.sdu.dk"),
							jwt.WithValidMethods([]string{jwt.SigningMethodRS256.Name}),
							jwt.WithIssuedAt(),
							jwt.WithExpirationRequired(),
						),
						KeyFunc: func(token *jwt.Token) (interface{}, error) {
							return pubKey, nil
						},
					}

					cache.Providers[providerId] = entry
					cached, ok = entry, true
				}
			}
		}
		cache.Mu.Unlock()
		return cached, ok
	}
}

func readPublicKey(content string) (*rsa.PublicKey, error) {
	var keyBuilder strings.Builder
	keyBuilder.WriteString("-----BEGIN PUBLIC KEY-----\n")
	keyBuilder.WriteString(util.ChunkString(content, 64))
	keyBuilder.WriteString("\n-----END PUBLIC KEY-----\n")

	key := keyBuilder.String()

	block, _ := pem.Decode([]byte(key))
	if block == nil {
		return nil, fmt.Errorf("invalid key")
	}

	result, err := x509.ParsePKIXPublicKey(block.Bytes)
	if err == nil {
		publicKey, ok := result.(*rsa.PublicKey)
		if ok {
			return publicKey, nil
		} else {
			return nil, fmt.Errorf("not an rsa key")
		}
	}
	return nil, err
}

func collapseServerSlashes(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		p := r.URL.Path
		if p == "" {
			next.ServeHTTP(w, r)
			return
		}

		// Preserve a trailing slash (except for root)
		trailing := strings.HasSuffix(p, "/") && p != "/"

		// Replace until stable
		clean := p
		for {
			newp := strings.ReplaceAll(clean, "//", "/")
			if newp == clean {
				break
			}
			clean = newp
		}
		if trailing && clean != "/" && !strings.HasSuffix(clean, "/") {
			clean += "/"
		}

		if clean == p {
			next.ServeHTTP(w, r)
			return
		}

		// Clone request and update path (leave query untouched)
		r2 := r.Clone(r.Context())
		u := *r.URL
		u.Path = clean
		r2.URL = &u
		next.ServeHTTP(w, r2)
	})
}
