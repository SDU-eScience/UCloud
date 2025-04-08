package rpc

import (
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

var DefaultClient *Client
var DefaultServer Server
var ServerAuthenticator func(r *http.Request) (Actor, *util.HttpError)

type Actor struct {
	Username  string
	Role      Role
	Project   util.Option[string]
	TokenInfo util.Option[TokenInfo]
}

type TokenInfo struct {
	IssuedAt               time.Time
	ExpiresAt              time.Time
	PublicSessionReference string
}

type RequestInfo struct {
	HttpWriter  http.ResponseWriter
	HttpRequest *http.Request
	Actor       Actor
}

type ServerHandler[Req any, Resp any] func(info RequestInfo, request Req) (Resp, *util.HttpError)

type serverHandlerData struct {
	byMethod map[string]func(w http.ResponseWriter, r *http.Request)
}

type Server struct {
	Mux      *http.ServeMux
	handlers map[string]*serverHandlerData
}

type Client struct {
	RefreshToken string
	AccessToken  string
	BasePath     string
	client       *http.Client

	refreshMutex sync.Mutex
}

type Call[Req any, Resp any] struct {
	Convention Convention

	CustomMethod        string
	CustomPath          string
	CustomServerParser  func(w http.ResponseWriter, r *http.Request) (Req, *util.HttpError)
	CustomClientHandler func(self *Call[Req, Resp], client *Client, request Req) (Resp, *util.HttpError)

	BaseContext string
	Operation   string

	Roles Role // Bit-set. See roles below.
	Audit AuditRules
}

func (c *Call[Req, Resp]) Invoke(request Req) (Resp, *util.HttpError) {
	return c.InvokeEx(DefaultClient, request)
}

func (c *Call[Req, Resp]) InvokeEx(client *Client, request Req) (Resp, *util.HttpError) {
	var result Resp
	var err *util.HttpError

	if client == nil {
		return result, util.HttpErr(http.StatusBadGateway, "client (DefaultClient?) has not been initialized")
	}

	switch c.Convention {
	case ConventionCustom:
		handler := c.CustomClientHandler
		if handler == nil {
			return result, util.HttpErr(http.StatusBadGateway, "CustomClientHandler is not set")
		}

		result, err = handler(c, client, request)

	case ConventionRetrieve:
		resp := CallViaQuery(client, fmt.Sprintf("/api/%s/retrieve%s", c.BaseContext, capitalized(c.Operation)),
			StructToParameters(request))
		result, err = ParseResponse[Resp](resp)

	case ConventionBrowse:
		resp := CallViaQuery(client, fmt.Sprintf("/api/%s/browse%s", c.BaseContext, capitalized(c.Operation)),
			StructToParameters(request))
		result, err = ParseResponse[Resp](resp)

	case ConventionUpdate:
		resp := CallViaJsonBody(client, "POST", fmt.Sprintf("/api/%s/%s", c.BaseContext, c.Operation), request)
		result, err = ParseResponse[Resp](resp)

	case ConventionDelete:
		resp := CallViaJsonBody(client, "DELETE", fmt.Sprintf("/api/%s/%s", c.BaseContext, c.Operation), request)
		result, err = ParseResponse[Resp](resp)

	case ConventionCreate:
		resp := CallViaJsonBody(client, "POST", fmt.Sprintf("/api/%s/%s", c.BaseContext, c.Operation), request)
		result, err = ParseResponse[Resp](resp)

	case ConventionSearch:
		resp := CallViaJsonBody(client, "POST", fmt.Sprintf("/api/%s/search%s", c.BaseContext,
			capitalized(c.Operation)), request)
		result, err = ParseResponse[Resp](resp)
	}

	return result, err
}

func (c *Call[Req, Resp]) Handler(handler ServerHandler[Req, Resp]) {
	c.HandlerEx(&DefaultServer, handler)
}

func (c *Call[Req, Resp]) HandlerEx(server *Server, handler ServerHandler[Req, Resp]) {
	if server == nil {
		log.Warn("server (DefaultServer?) has not been initialized before Handler was invoked!")
		os.Exit(1)
		return
	}

	if c.Convention == ConventionCustom && c.CustomServerParser == nil {
		log.Warn("CustomServerParser was requested but not set!")
		os.Exit(1)
		return
	}

	if server.handlers == nil {
		server.handlers = make(map[string]*serverHandlerData)
	}

	path := ""
	method := ""
	var parser func(w http.ResponseWriter, r *http.Request) (Req, *util.HttpError)

	switch c.Convention {
	case ConventionCustom:
		path = c.CustomPath
		method = c.CustomMethod
		parser = c.CustomServerParser

	case ConventionUpdate:
		path = fmt.Sprintf("/api/%s/%s", c.BaseContext, c.Operation)
		method = http.MethodPost
		parser = ParseRequestFromBody

	case ConventionRetrieve:
		path = fmt.Sprintf("/api/%s/retrieve%s", c.BaseContext, capitalized(c.Operation))
		method = http.MethodGet
		parser = ParseRequestFromQuery

	case ConventionDelete:
		path = fmt.Sprintf("/api/%s/%s", c.BaseContext, c.Operation)
		method = http.MethodDelete
		parser = ParseRequestFromBody

	case ConventionCreate:
		path = fmt.Sprintf("/api/%s/%s", c.BaseContext, c.Operation)
		method = http.MethodPost
		parser = ParseRequestFromBody

	case ConventionBrowse:
		path = fmt.Sprintf("/api/%s/retrieve%s", c.BaseContext, capitalized(c.Operation))
		method = http.MethodGet
		parser = ParseRequestFromQuery

	case ConventionSearch:
		path = fmt.Sprintf("/api/%s/search%s", c.BaseContext, capitalized(c.Operation))
		method = http.MethodPost
		parser = ParseRequestFromBody
	}

	path, _ = strings.CutSuffix(path, "/")

	handlerGroup, hasExisting := server.handlers[path]
	if !hasExisting {
		handlerGroup = &serverHandlerData{byMethod: make(map[string]func(w http.ResponseWriter, r *http.Request))}
	}

	handlerGroup.byMethod[method] = func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		var request Req
		var response Resp

		actor, err := ServerAuthenticator(r)

		if err == nil {
			if c.Roles&actor.Role == 0 {
				err = util.HttpErr(http.StatusForbidden, "Forbidden")
			} else {
				request, err = parser(w, r)
				if err == nil {
					info := RequestInfo{
						HttpWriter:  w,
						HttpRequest: r,
						Actor:       actor,
					}

					response, err = handler(info, request)
				}
			}
		}

		end := time.Now()

		if err != nil {
			data, _ := json.Marshal(err)

			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(err.StatusCode)
			_, _ = w.Write(data)
		} else {
			data, _ := json.Marshal(response)
			w.Header().Set("Content-Type", "application/json; charset=utf-8")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write(data)
		}

		if AuditConsumer != nil {
			ev := HttpCallLogEntry{
				JobId: util.OptStringIfNotEmpty(r.Header.Get("Job-Id")).GetOrDefault(util.RandomTokenNoTs(8)),
				HandledBy: ServiceInstance{
					Definition: ServiceDefinition{
						Name:    "ucloud",
						Version: "ucloud",
					},
					Hostname: "hostname",
					Port:     8080,
				},
				CausedBy:     util.OptNone[string](),
				RequestName:  fmt.Sprintf("%s.%s", c.BaseContext, c.Operation),
				UserAgent:    util.OptStringIfNotEmpty(r.Header.Get("User-Agent")),
				RemoteOrigin: r.RemoteAddr,
				Token:        util.Option[SecurityPrincipalToken]{},
				ResponseTime: uint64(end.Sub(start).Milliseconds()),
				Project:      actor.Project,
			}

			token := SecurityPrincipalToken{
				Principal: SecurityPrincipal{
					Username: actor.Username,
					Role:     actor.Role.String(),
				},
				Scopes:                 []string{"all:write"},
				IssuedAt:               0,
				ExpiresAt:              0,
				PublicSessionReference: util.Option[string]{},
				ExtendedBy:             util.Option[string]{},
				ExtendedByChain:        nil,
			}

			if actor.TokenInfo.Present {
				tokInfo := actor.TokenInfo.Value
				token.ExpiresAt = uint64(tokInfo.ExpiresAt.UnixMilli())
				token.IssuedAt = uint64(tokInfo.IssuedAt.UnixMilli())
				token.PublicSessionReference.Set(tokInfo.PublicSessionReference)
			}

			ev.Token.Set(token)

			expiry := end.Add(time.Duration(c.Audit.RetentionDays.GetOrDefault(180)) * 24 * time.Hour)
			ev.Expiry = uint64(expiry.UnixMilli())

			var requestData json.RawMessage
			if c.Audit.Transformer != nil {
				requestData = c.Audit.Transformer(request)
			} else {
				requestData, _ = json.Marshal(request)
			}

			ev.RequestJson.Set(requestData)

			contentLength, e := strconv.ParseInt(r.Header.Get("Content-Length"), 10, 64)
			if e == nil {
				ev.RequestSize = uint64(contentLength)
			}

			if err != nil {
				ev.ResponseCode = err.StatusCode
			} else {
				ev.ResponseCode = http.StatusOK
			}

			AuditConsumer(ev)
		}
	}

	if !hasExisting {
		server.handlers[path] = handlerGroup

		server.Mux.HandleFunc(path, func(w http.ResponseWriter, r *http.Request) {
			handlerByMethod, ok := handlerGroup.byMethod[r.Method]
			if ok {
				handlerByMethod(w, r)
			} else {
				http.NotFound(w, r)
			}
		})
	}
}

type AuditRules struct {
	Transformer   func(request any) json.RawMessage
	RetentionDays util.Option[int]
	Flags         AuditFlag
}

type AuditFlag int

const (
	AuditFlagLongRunning = 1 << iota
)

type Convention int

// NOTE(Dan): Please do not re-order these
const (
	ConventionUpdate Convention = iota
	ConventionCustom
	ConventionRetrieve
	ConventionDelete
	ConventionCreate
	ConventionBrowse
	ConventionSearch
)

type Role uint

// NOTE(Dan): Please do not re-order these
const (
	RoleUnknown Role = 1 << iota
	RoleGuest
	RoleUser
	RoleAdmin
	RoleService
	RoleProvider
)

const (
	RolesPublic        Role = math.MaxUint // all bits set
	RolesAuthenticated      = RoleUser | RoleAdmin | RoleService | RoleProvider
	RolesEndUser            = RoleUser | RoleAdmin
	RolesPrivileged         = RoleAdmin | RoleService
	RolesService            = RoleService
	RolesAdmin              = RoleAdmin
	RolesProvider           = RoleProvider | RoleService
)

func (r Role) String() string {
	switch r {
	case RoleUnknown:
		return "UNKNOWN"
	case RoleGuest:
		return "GUEST"
	case RoleUser:
		return "USER"
	case RoleAdmin:
		return "ADMIN"
	case RoleService:
		return "SERVICE"
	case RoleProvider:
		return "PROVIDER"
	default:
		return "UNKNOWN"
	}
}
