package coreutil

import (
	"net"
	"net/http"
	"time"

	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var projectPolicyCache = util.NewCache[string, map[fndapi.PolicyName]fndapi.Specification](time.Minute)

// ProjectPoliciesRetrieve returns the configured policies of a project. The policies are read directly from the
// database through a short-lived cache, such that the result is correct in every deployment of the Core.
func ProjectPoliciesRetrieve(projectId string) map[fndapi.PolicyName]fndapi.Specification {
	policies, _ := projectPolicyCache.Get(
		projectId,
		func() (map[fndapi.PolicyName]fndapi.Specification, error) {
			result, ok := db.NewTx2(func(tx *db.Transaction) (map[fndapi.PolicyName]fndapi.Specification, bool) {
				return PolicySpecificationsRetrieveFromDatabase(tx, projectId)
			})

			if !ok {
				result = make(map[fndapi.PolicyName]fndapi.Specification)
			}

			return result, nil
		},
	)

	return policies
}

func ProjectPoliciesInvalidateCache() {
	projectPolicyCache.InvalidateAll()
}

// SourceIpPolicy enforces the "RestrictSourceIPRange" project policy for a single RPC call. It is installed as
// the request policy of the RPC server and is consulted before every incoming request.
//
// Only the endpoints marked with restrictSourceIp = true are subject to the check. For those endpoints, the
// call is rejected if the client's IP address is not permitted by the policy of the actor's active project.
// Calls which are not subject to the policy, and calls which the policy allows, return a nil error.
func SourceIpPolicy(info rpc.RequestInfo, restrictSourceIP bool) *util.HttpError {
	if !restrictSourceIP {
		return nil
	}

	if SourceIpIsRestricted(info) {
		return util.HttpErr(
			http.StatusForbidden,
			"Client IP is not allowed by project",
		)
	}

	return nil
}

// SourceIpIsRestricted reports whether the "RestrictSourceIPRange" policy of the actor's active project rejects
// the client IP of the request.
func SourceIpIsRestricted(info rpc.RequestInfo) bool {
	if !info.Actor.Project.Present {
		return false
	}

	policies := ProjectPoliciesRetrieve(string(info.Actor.Project.Value))
	specification, ok := policies[fndapi.RestrictSourceIPRange]
	if !ok {
		return false
	}

	sourceIpSpecification, ok := specification.(*fndapi.RestrictSourceIPRangeSpecification)
	if !ok {
		return false
	}

	if !sourceIpSpecification.IsEnabled() {
		return false
	}

	allowedSubnets := sourceIpSpecification.Values.AllowedSubnets
	if allowedSubnets == "" {
		return true
	}

	ip := net.ParseIP(util.ClientIP(info.HttpRequest).String())
	if ip == nil {
		return true
	}

	_, subnet, err := net.ParseCIDR(allowedSubnets)
	if err != nil {
		return true
	}

	return !subnet.Contains(ip)
}

// ApiTokensIsRestricted reports whether the project has enabled the "RestrictApiTokens" policy. When true,
// authentication via API tokens must be rejected for the project.
func ApiTokensIsRestricted(projectId string) bool {
	specification, ok := ProjectPoliciesRetrieve(projectId)[fndapi.RestrictApiTokens]
	if !ok {
		return false
	}

	return specification.IsEnabled()
}
