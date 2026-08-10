package accounting

import (
	"context"
	"net"
	"sync"

	"ucloud.dk/core/pkg/coreutil"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// policyCache is a mapping of projectId -> map[schemaName] -> PolicySpecification
var policyCache struct {
	Mu                sync.RWMutex
	PoliciesByProject map[string]map[fndapi.PolicyName]fndapi.Specification
}

func initPolicySubscriptions() {

	policyCache.Mu.Lock()
	policyCache.PoliciesByProject = make(map[string]map[fndapi.PolicyName]fndapi.Specification)
	policyCache.Mu.Unlock()

	go func() {
		policyUpdates := db.Listen(context.Background(), "policy_updates")
		policyDeletes := db.Listen(context.Background(), "policy_deleted")

		var projectId string
		var policySpecifications map[fndapi.PolicyName]fndapi.Specification
		var policiesOk bool

		for {
			select {
			case projectId = <-policyUpdates:
				db.NewTx0(func(tx *db.Transaction) {
					policySpecifications, policiesOk = coreutil.PolicySpecificationsRetrieveFromDatabase(tx, projectId)
				})
			case projectId = <-policyDeletes:
				db.NewTx0(func(tx *db.Transaction) {

					policySpecifications, policiesOk = coreutil.PolicySpecificationsRetrieveFromDatabase(tx, projectId)
				})
			}

			if policiesOk {
				updatePolicyCacheForProject(projectId, policySpecifications)
			}
		}

	}()
}

// policiesByProject returns mapping of [schema Name] => PolicySpecification. If no policy is cached for the project it
// will attempt to retrieve it from DB. This is also how it is populated.
func policiesByProject(projectId string) map[fndapi.PolicyName]fndapi.Specification {
	policyCache.Mu.Lock()
	projectPolicies, ok := policyCache.PoliciesByProject[projectId]
	if !ok {
		db.NewTx0(func(tx *db.Transaction) {
			policySpecifications, policiesOk := coreutil.PolicySpecificationsRetrieveFromDatabase(tx, projectId)
			if policiesOk {
				policyCache.PoliciesByProject[projectId] = policySpecifications
			}
			projectPolicies = policySpecifications
		})
	}
	policyCache.Mu.Unlock()

	return projectPolicies
}

func updatePolicyCacheForProject(projectId string, policySpecifications map[fndapi.PolicyName]fndapi.Specification) {
	policyCache.Mu.Lock()
	policyCache.PoliciesByProject[projectId] = policySpecifications
	policyCache.Mu.Unlock()
}

func sourceIPisRestricted(info rpc.RequestInfo) bool {
	if !info.Actor.Project.Present {
		return false
	}

	policies := policiesByProject(info.Actor.Project.String())

	specification, ok := policies[fndapi.RestrictSourceIPRange]
	if !ok {
		return false
	}

	sourceIPSpecification, ok := specification.(*fndapi.RestrictSourceIPRangeSpecification)
	if !ok {
		return false
	}

	if !sourceIPSpecification.IsEnabled() {
		return false
	}

	allowedSubnets := sourceIPSpecification.Values.AllowedSubnets
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
