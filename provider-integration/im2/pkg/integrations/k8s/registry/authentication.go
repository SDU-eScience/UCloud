package registry

import (
	"context"
	"net/http"
	"slices"

	ocidauth "github.com/distribution/distribution/v3/registry/auth"
)

const authenticationName = "ucloud-authentication"

type requestStateKey struct{}

type requestState struct {
	grant  *ocidauth.Grant
	access []ocidauth.Access
}

func requestStateFromContext(ctx context.Context) *requestState {
	state, _ := ctx.Value(requestStateKey{}).(*requestState)
	return state
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

	var grant *ocidauth.Grant
	var err error

	grant, err = &ocidauth.Grant{}, nil // TODO actually compute these

	state.grant = grant
	return grant, err
}
