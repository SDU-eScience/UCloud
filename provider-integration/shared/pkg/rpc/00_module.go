package rpc

import (
	"encoding/json"
	"fmt"
	"github.com/golang-jwt/jwt/v5"
	ws "github.com/gorilla/websocket"
	"math"
	"net/http"
	"os"
	"reflect"
	"strconv"
	"strings"
	"sync"
	"time"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/util"
)

var DefaultClient *Client
var DefaultServer Server
var BearerAuthenticator func(bearer string, project string) (Actor, *util.HttpError)
var ServerAuthenticator func(r *http.Request) (Actor, *util.HttpError)

var LookupActor func(username string) (Actor, bool) = func(username string) (Actor, bool) {
	panic("LookupActor has not been defined in this context. You must set " +
		"`rpc.LookupActor = func() { ... }` before using this function.")
}

type Actor struct {
	Username         string
	Role             Role
	Project          util.Option[ProjectId]
	TokenInfo        util.Option[TokenInfo]
	Membership       ProjectMembership // TODO implement this
	Groups           GroupMembership   // TODO implement this
	ProviderProjects ProviderProjects  // TODO implement this (should only show up if also in membership)
	Domain           string            // email domain, TODO implement this
	OrgId            string            // TODO implement this
}

type ProjectId string
type GroupId string
type ProjectRole string
type ProviderId string

const (
	ProjectRolePI    ProjectRole = "PI"
	ProjectRoleAdmin ProjectRole = "ADMIN"
	ProjectRoleUser  ProjectRole = "USER"
)

var ProjectRoleOptions = []ProjectRole{ProjectRolePI, ProjectRoleAdmin, ProjectRoleUser}

func (p ProjectRole) Power() int {
	switch p {
	case ProjectRolePI:
		return 3
	case ProjectRoleAdmin:
		return 2
	case ProjectRoleUser:
		return 1
	default:
		return 0
	}
}

func (p ProjectRole) Normalize() ProjectRole {
	return util.EnumOrDefault(p, ProjectRoleOptions, ProjectRoleUser)
}

func (p ProjectRole) Satisfies(requirement ProjectRole) bool {
	if p == requirement {
		return true
	}

	power := p.Power()
	requiredPower := requirement.Power()
	if power > 0 && requiredPower > 0 {
		return power >= requiredPower
	} else {
		return false
	}
}

type GroupMembership map[GroupId]ProjectId
type ProjectMembership map[ProjectId]ProjectRole
type ProviderProjects map[ProviderId]ProjectId

var ActorSystem = Actor{
	Username:         "_ucloud",
	Role:             RoleService,
	Membership:       make(ProjectMembership),
	Groups:           make(GroupMembership),
	ProviderProjects: make(ProviderProjects),
}

type TokenInfo struct {
	IssuedAt               time.Time
	ExpiresAt              time.Time
	PublicSessionReference string
}

type RequestInfo struct {
	HttpWriter  http.ResponseWriter
	HttpRequest *http.Request
	WebSocket   *ws.Conn
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
	RefreshToken    string
	AccessToken     string
	BasePath        string
	Client          *http.Client
	CoreForProvider util.Option[string]

	refreshMutex sync.Mutex
}

type Call[Req any, Resp any] struct {
	Convention Convention

	CustomMethod         string
	CustomPath           string
	CustomServerParser   func(w http.ResponseWriter, r *http.Request) (Req, *util.HttpError)
	CustomServerProducer func(response Resp, err *util.HttpError, w http.ResponseWriter, r *http.Request)
	CustomClientHandler  func(self *Call[Req, Resp], client *Client, request Req) (Resp, *util.HttpError)

	BaseContext string
	Operation   string

	Roles Role // Bit-set. See roles below.
	Audit AuditRules
}

func rpcBaseContext(context string) string {
	if !strings.HasPrefix(context, "auth") && !strings.HasPrefix(context, "ucloud/") {
		return fmt.Sprintf("api/%s", context)
	} else {
		return context
	}
}

func (c *Call[Req, Resp]) FullName() string {
	result := strings.Builder{}
	result.WriteString(strings.ReplaceAll(c.BaseContext, "/", "."))
	result.WriteString(".")

	hasConventionName := true
	switch c.Convention {
	case ConventionRetrieve:
		result.WriteString("retrieve")
	case ConventionDelete:
		result.WriteString("delete")
	case ConventionCreate:
		result.WriteString("create")
	case ConventionBrowse:
		result.WriteString("browse")
	case ConventionSearch:
		result.WriteString("search")
	default:
		hasConventionName = false
	}

	if hasConventionName {
		name := []rune(c.Operation)
		if len(name) > 0 {
			result.WriteRune(name[0])
			result.WriteString(string(name[1:]))
		}
	} else {
		result.WriteString(c.Operation)
	}

	return result.String()
}

func (c *Call[Req, Resp]) Invoke(request Req) (Resp, *util.HttpError) {
	return c.InvokeEx(DefaultClient, request, InvokeOpts{})
}

type InvokeOpts struct {
	Headers http.Header
}

func (c *Call[Req, Resp]) InvokeEx(client *Client, request Req, opts InvokeOpts) (Resp, *util.HttpError) {
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
		resp := CallViaQueryEx(client, fmt.Sprintf("/%s/retrieve%s", rpcBaseContext(c.BaseContext), capitalized(c.Operation)),
			StructToParameters(request), opts)
		result, err = ParseResponse[Resp](resp)

	case ConventionQueryParameters:
		resp := CallViaQueryEx(client, fmt.Sprintf("/%s/%s", rpcBaseContext(c.BaseContext), c.Operation),
			StructToParameters(request), opts)
		result, err = ParseResponse[Resp](resp)

	case ConventionBrowse:
		resp := CallViaQueryEx(client, fmt.Sprintf("/%s/browse%s", rpcBaseContext(c.BaseContext), capitalized(c.Operation)),
			StructToParameters(request), opts)
		result, err = ParseResponse[Resp](resp)

	case ConventionUpdate:
		resp := CallViaJsonBodyEx(client, "POST", fmt.Sprintf("/%s/%s", rpcBaseContext(c.BaseContext), c.Operation), request, opts)
		result, err = ParseResponse[Resp](resp)

	case ConventionDelete:
		resp := CallViaJsonBodyEx(client, "DELETE", fmt.Sprintf("/%s/%s", rpcBaseContext(c.BaseContext), c.Operation), request, opts)
		result, err = ParseResponse[Resp](resp)

	case ConventionCreate:
		resp := CallViaJsonBodyEx(client, "POST", fmt.Sprintf("/%s/%s", rpcBaseContext(c.BaseContext), c.Operation), request, opts)
		result, err = ParseResponse[Resp](resp)

	case ConventionSearch:
		resp := CallViaJsonBodyEx(client, "POST", fmt.Sprintf("/%s/search%s", rpcBaseContext(c.BaseContext),
			capitalized(c.Operation)), request, opts)
		result, err = ParseResponse[Resp](resp)

	case ConventionWebSocket:
		log.Fatal("Client is not supported for this endpoint")
	}

	return result, err
}

func (c *Call[Req, Resp]) Handler(handler ServerHandler[Req, Resp]) {
	c.HandlerEx(&DefaultServer, handler)
}

var wsUpgrader = ws.Upgrader{
	ReadBufferSize:  1024 * 4,
	WriteBufferSize: 1024 * 4,
	Subprotocols:    []string{"binary"},
}

func (c *Call[Req, Resp]) HandlerEx(server *Server, handler ServerHandler[Req, Resp]) {
	const websocketMethod = "\n\nwebsocket\n\n" // fake method used for multiplexing to websockets

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
		path = fmt.Sprintf("/%s/%s", rpcBaseContext(c.BaseContext), c.Operation)
		method = http.MethodPost
		parser = ParseRequestFromBody

	case ConventionRetrieve:
		path = fmt.Sprintf("/%s/retrieve%s", rpcBaseContext(c.BaseContext), capitalized(c.Operation))
		method = http.MethodGet
		parser = ParseRequestFromQuery

	case ConventionQueryParameters:
		path = fmt.Sprintf("/%s/%s", rpcBaseContext(c.BaseContext), c.Operation)
		method = http.MethodGet
		parser = ParseRequestFromQuery

	case ConventionDelete:
		path = fmt.Sprintf("/%s/%s", rpcBaseContext(c.BaseContext), c.Operation)
		method = http.MethodDelete
		parser = ParseRequestFromBody

	case ConventionCreate:
		path = fmt.Sprintf("/%s/%s", rpcBaseContext(c.BaseContext), c.Operation)
		method = http.MethodPost
		parser = ParseRequestFromBody

	case ConventionBrowse:
		path = fmt.Sprintf("/%s/browse%s", rpcBaseContext(c.BaseContext), capitalized(c.Operation))
		method = http.MethodGet
		parser = ParseRequestFromQuery

	case ConventionSearch:
		path = fmt.Sprintf("/%s/search%s", rpcBaseContext(c.BaseContext), capitalized(c.Operation))
		method = http.MethodPost
		parser = ParseRequestFromBody

	case ConventionWebSocket:
		path = fmt.Sprintf("/%s/%s", rpcBaseContext(c.BaseContext), c.Operation)
		method = websocketMethod
		parser = func(w http.ResponseWriter, r *http.Request) (Req, *util.HttpError) {
			var req Req
			return req, nil
		}

		if c.Roles != RolesPublic && c.Roles != 0 {
			panic("ConventionWebSocket does not support direct authentication, please do it within the connection.")
		}

		var req Req
		var resp Resp
		if !reflect.TypeOf(req).AssignableTo(reflect.TypeOf(util.Empty{})) {
			panic("ConventionWebSocket does not support direct request parsing, do it in the connection.")
		}
		if !reflect.TypeOf(resp).AssignableTo(reflect.TypeOf(util.Empty{})) {
			panic("ConventionWebSocket does not support direct response production, do it in the connection.")
		}
	}

	path, _ = strings.CutSuffix(path, "/")

	handlerGroup, hasExisting := server.handlers[path]
	if !hasExisting {
		handlerGroup = &serverHandlerData{byMethod: make(map[string]func(w http.ResponseWriter, r *http.Request))}
	}

	if _, existingGetHandler := handlerGroup.byMethod[http.MethodGet]; !existingGetHandler && method == websocketMethod {
		handlerGroup.byMethod[http.MethodGet] = func(w http.ResponseWriter, r *http.Request) {
			if strings.Contains(r.Header.Get("Upgrade"), "websocket") {
				wsHandler, ok := handlerGroup.byMethod[websocketMethod]
				if ok {
					wsHandler(w, r)
					return
				}
			}

			w.WriteHeader(http.StatusNotFound)
			_, _ = w.Write(nil)
		}
	}

	handlerGroup.byMethod[method] = func(w http.ResponseWriter, r *http.Request) {
		if method == websocketMethod {
			conn, err := wsUpgrader.Upgrade(w, r, nil)

			if err != nil {
				w.WriteHeader(http.StatusBadRequest)
				_, _ = w.Write(nil)
			} else {
				var req Req

				_, _ = handler(RequestInfo{
					HttpWriter:  w,
					HttpRequest: r,
					WebSocket:   conn,
					Actor:       Actor{Role: RoleGuest},
				}, req)
			}

			return
		}

		// NOTE(Dan): This if-statement only needs to catch that we should upgrade, it doesn't need to validate
		// anything.
		if strings.Contains(r.Header.Get("Upgrade"), "websocket") {
			wsHandler, ok := handlerGroup.byMethod[websocketMethod]
			if ok {
				wsHandler(w, r)
				return
			}
		}

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

		producer := c.CustomServerProducer
		if producer != nil {
			producer(response, err, w, r)
		} else {
			SendResponseOrError(w, response, err)
		}

		if AuditConsumer != nil {
			stringProject := util.OptNone[string]()
			if actor.Project.Present {
				stringProject.Set(string(actor.Project.Value))
			}

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
				CausedBy:          util.OptNone[string](),
				RequestName:       c.FullName(),
				UserAgent:         util.OptStringIfNotEmpty(r.Header.Get("User-Agent")),
				RemoteOrigin:      r.RemoteAddr,
				Token:             util.Option[SecurityPrincipalToken]{},
				ResponseTime:      uint64(end.Sub(start).Milliseconds()),
				ResponseTimeNanos: uint64(end.Sub(start).Nanoseconds()),
				Project:           stringProject,
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

func SendResponseOrError(w http.ResponseWriter, response any, err *util.HttpError) {
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
	ConventionQueryParameters
	ConventionWebSocket
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

type CorePrincipalBaseClaims struct {
	Role                    string              `json:"role"`
	Uid                     int                 `json:"uid"`
	FirstNames              util.Option[string] `json:"firstNames"`
	LastName                util.Option[string] `json:"lastName"`
	Email                   util.Option[string] `json:"email"`
	OrgId                   util.Option[string] `json:"orgId"`
	TwoFactorAuthentication bool                `json:"twoFactorAuthentication"`
	ServiceLicenseAgreement bool                `json:"serviceLicenseAgreement"`
	PrincipalType           string              `json:"principalType"`
	SessionReference        util.Option[string] `json:"publicSessionReference"`
	ExtendedByChain         []string            `json:"extendedByChain"`
	Membership              ProjectMembership   `json:"membership"`
	Groups                  GroupMembership     `json:"groups"`
	ProviderProjects        ProviderProjects    `json:"providerProjects"`
	Domain                  string              `json:"domain"`
}

type CorePrincipalClaims struct {
	CorePrincipalBaseClaims
	jwt.RegisteredClaims
}
