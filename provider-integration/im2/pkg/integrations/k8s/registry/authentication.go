package registry

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"slices"
	"strings"
	"time"

	ocidauth "github.com/distribution/distribution/v3/registry/auth"
	"github.com/golang-jwt/jwt/v5"
	"ucloud.dk/pkg/controller"
	"ucloud.dk/pkg/integrations/k8s/shared"
	apm "ucloud.dk/shared/pkg/accounting"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

const (
	authenticationName = "ucloud-authentication"
	registryJWTIssuer  = "ucloud-registry"
	registryJWTExpiry  = 15 * time.Minute
)

type requestStateKey struct{}

type requestState struct {
	grant      *ocidauth.Grant
	access     []ocidauth.Access
	owner      apm.WalletOwner
	apiTokenId string
}

func requestStateFromContext(ctx context.Context) *requestState {
	// NOTE(Dan): Guaranteed to not be nil
	state, _ := ctx.Value(requestStateKey{}).(*requestState)
	return state
}

type registryJWTAccess struct {
	Type    string   `json:"type"`
	Name    string   `json:"name"`
	Actions []string `json:"actions"`
}

type registryJWTClaims struct {
	jwt.RegisteredClaims
	Owner            apm.WalletOwner          `json:"owner"`
	Username         string                   `json:"username"`
	ApiTokenId       string                   `json:"apiTokenId"`
	TokenPermissions []orc.ApiTokenPermission `json:"tokenPermissions"`
	Access           []registryJWTAccess      `json:"access"`
}

type accessControllerFunc func(r *http.Request, access ...ocidauth.Access) (*ocidauth.Grant, error)

func (f accessControllerFunc) Authorized(r *http.Request, access ...ocidauth.Access) (*ocidauth.Grant, error) {
	return f(r, access...)
}

func registerAuthentication() error {
	return ocidauth.Register(authenticationName, func(options map[string]any) (ocidauth.AccessController, error) {
		return accessControllerFunc(authenticateAndAuthorize), nil
	})
}

func authenticateAndAuthorize(r *http.Request, access ...ocidauth.Access) (*ocidauth.Grant, error) {
	state := requestStateFromContext(r.Context())
	state.access = slices.Clone(access)

	prefix, rawToken, ok := strings.Cut(r.Header.Get("Authorization"), " ")
	if !ok || rawToken == "" || !strings.EqualFold(prefix, "bearer") {
		return nil, registryAuthChallenge{access: access, cause: errors.New("authorization token required")}
	}

	claims := registryJWTClaims{}
	token, err := jwt.ParseWithClaims(
		rawToken,
		&claims,
		func(token *jwt.Token) (any, error) {
			if token.Method != jwt.SigningMethodHS512 {
				return nil, errors.New("unexpected signing algorithm")
			}
			return []byte(shared.ServiceConfig.Registry.Secrets.AuthSharedSecret), nil
		},
		jwt.WithValidMethods([]string{jwt.SigningMethodHS512.Alg()}),
		jwt.WithIssuer(registryJWTIssuer),
		jwt.WithAudience(Service()),
	)
	if err != nil || !token.Valid {
		return nil, registryAuthChallenge{access: access, cause: errors.New("invalid authorization token")}
	}

	grant := &ocidauth.Grant{User: ocidauth.UserInfo{Name: claims.Username}}
	for _, requested := range access {
		if !jwtAllowsAccess(claims.Access, requested) {
			return nil, registryAuthChallenge{access: access, cause: errors.New("insufficient scope")}
		}
		grant.Resources = append(grant.Resources, requested.Resource)
	}

	state.grant = grant
	state.owner = claims.Owner
	state.apiTokenId = claims.ApiTokenId
	return grant, nil
}

func jwtAllowsAccess(granted []registryJWTAccess, requested ocidauth.Access) bool {
	for _, entry := range granted {
		if entry.Type == requested.Type && entry.Name == requested.Name && slices.Contains(entry.Actions, requested.Action) {
			return true
		}
	}
	return false
}

type registryAuthChallenge struct {
	access []ocidauth.Access
	cause  error
}

func (c registryAuthChallenge) Error() string { return c.cause.Error() }

func (c registryAuthChallenge) SetHeaders(r *http.Request, w http.ResponseWriter) {
	scheme := "http"
	if r.TLS != nil {
		scheme = "https"
	}
	if forwardedProto := r.Header.Get("X-Forwarded-Proto"); forwardedProto != "" {
		scheme = strings.TrimSpace(strings.Split(forwardedProto, ",")[0])
	}
	realm := fmt.Sprintf("%s://%s/auth/token", scheme, r.Host)
	value := fmt.Sprintf("Bearer realm=%q,service=%q", realm, Service())
	if scope := registryScope(c.access); scope != "" {
		value += fmt.Sprintf(",scope=%q", scope)
	}
	w.Header().Set("WWW-Authenticate", value)
}

func registryScope(access []ocidauth.Access) string {
	type resourceActions struct {
		resource ocidauth.Resource
		actions  []string
	}
	var resources []resourceActions
	for _, item := range access {
		found := false
		for i := range resources {
			if resources[i].resource == item.Resource {
				resources[i].actions = append(resources[i].actions, item.Action)
				found = true
				break
			}
		}
		if !found {
			resources = append(resources, resourceActions{resource: item.Resource, actions: []string{item.Action}})
		}
	}

	parts := make([]string, 0, len(resources))
	for _, item := range resources {
		parts = append(parts, fmt.Sprintf("%s:%s:%s", item.resource.Type, item.resource.Name, strings.Join(item.actions, ",")))
	}
	return strings.Join(parts, " ")
}

func handleAuthenticationToken(w http.ResponseWriter, r *http.Request) {
	_, apiToken, ok := r.BasicAuth()
	if !ok {
		w.Header().Set("WWW-Authenticate", `Basic realm="UCloud container registry"`)
		http.Error(w, "API token required", http.StatusUnauthorized)
		return
	}

	identity, authErr := controller.ApiTokenValidateWithIdentity(containerRepositoryApiTokenKind, apiToken)
	if authErr != nil {
		http.Error(w, "invalid API token", http.StatusUnauthorized)
		return
	}

	requestedService := r.URL.Query().Get("service")
	if requestedService != "" && requestedService != Service() {
		http.Error(w, "invalid registry service", http.StatusBadRequest)
		return
	}

	access := authorizedTokenAccess(identity, r.URL.Query()["scope"])
	state := requestStateFromContext(r.Context())
	state.grant = &ocidauth.Grant{User: ocidauth.UserInfo{Name: identity.Username}}
	state.owner = identity.Owner
	state.apiTokenId = identity.TokenId
	for _, entry := range access {
		for _, action := range entry.Actions {
			state.access = append(state.access, ocidauth.Access{
				Resource: ocidauth.Resource{Type: entry.Type, Name: entry.Name},
				Action:   action,
			})
		}
	}
	now := time.Now()
	ownerReference := identity.Owner.Reference()
	claims := registryJWTClaims{
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer:    registryJWTIssuer,
			Subject:   ownerReference,
			Audience:  jwt.ClaimStrings{Service()},
			ExpiresAt: jwt.NewNumericDate(now.Add(registryJWTExpiry)),
			IssuedAt:  jwt.NewNumericDate(now),
			NotBefore: jwt.NewNumericDate(now),
		},
		Owner:            identity.Owner,
		Username:         identity.Username,
		ApiTokenId:       identity.TokenId,
		TokenPermissions: identity.Permissions,
		Access:           access,
	}

	signed, err := jwt.NewWithClaims(jwt.SigningMethodHS512, claims).SignedString([]byte(shared.ServiceConfig.Registry.Secrets.AuthSharedSecret))
	if err != nil {
		http.Error(w, "unable to issue registry token", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")

	type tok struct {
		Token       string `json:"token"`
		AccessToken string `json:"access_token"`
		ExpiresIn   int64  `json:"expires_in"`
		IssuedAt    string `json:"issued_at"`
	}

	_ = json.NewEncoder(w).Encode(tok{
		Token:       signed,
		AccessToken: signed,
		ExpiresIn:   int64(registryJWTExpiry.Seconds()),
		IssuedAt:    now.UTC().Format(time.RFC3339),
	})
}

func authorizedTokenAccess(identity controller.ApiTokenIdentity, rawScopes []string) []registryJWTAccess {
	actor := orc.ResourceOwner{CreatedBy: identity.Username}
	if identity.Owner.ProjectId != "" {
		actor.Project = util.OptValue(identity.Owner.ProjectId)
	}

	canPull := apiTokenAllows(identity.Permissions, "pull")
	canPush := apiTokenAllows(identity.Permissions, "push")
	var result []registryJWTAccess
	for _, rawScope := range rawScopes {
		for scope := range strings.FieldsSeq(rawScope) {
			parts := strings.SplitN(scope, ":", 3)
			if len(parts) != 3 || parts[0] != "repository" {
				continue
			}

			repository, ok := controller.ContainerRepositoryRetrieveByRepository(parts[1])
			if !ok {
				continue
			}

			entry := registryJWTAccess{Type: parts[0], Name: parts[1]}
			for action := range strings.SplitSeq(parts[2], ",") {
				switch action {
				case "pull":
					if canPull && controller.ResourceCanUse(actor, repository.Owner, repository.Permissions, true) {
						entry.Actions = append(entry.Actions, action)
					}
				case "push":
					if canPush && controller.ResourceCanUse(actor, repository.Owner, repository.Permissions, false) {
						entry.Actions = append(entry.Actions, action)
					}
				}
			}
			if len(entry.Actions) > 0 {
				result = append(result, entry)
			}
		}
	}
	return result
}

func apiTokenAllows(permissions []orc.ApiTokenPermission, action string) bool {
	for _, permission := range permissions {
		if permission.Name == containerRepositoryApiTokenKind && permission.Action == action {
			return true
		}
	}
	return false
}
