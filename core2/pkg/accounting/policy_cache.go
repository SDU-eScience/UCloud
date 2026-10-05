package accounting

import (
	"context"
	"sync"

	"ucloud.dk/core/pkg/coreutil"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
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

		var projectId string
		var policySpecifications map[fndapi.PolicyName]fndapi.Specification
		var policiesOk bool

		for {
			select {
			case projectId = <-policyUpdates:
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
	policyCache.Mu.RLock()
	projectPolicies, ok := policyCache.PoliciesByProject[projectId]
	policyCache.Mu.RUnlock()
	if ok {
		return projectPolicies
	}
	var fetched map[fndapi.PolicyName]fndapi.Specification
	var fetchedOk bool
	db.NewTx0(func(tx *db.Transaction) {
		fetched, fetchedOk = coreutil.PolicySpecificationsRetrieveFromDatabase(tx, projectId)
	})

	if !fetchedOk {
		return nil
	}

	policyCache.Mu.Lock()
	defer policyCache.Mu.Unlock()

	if existing, exists := policyCache.PoliciesByProject[projectId]; exists {
		return existing
	}

	policyCache.PoliciesByProject[projectId] = fetched
	return fetched
}

func updatePolicyCacheForProject(projectId string, policySpecifications map[fndapi.PolicyName]fndapi.Specification) {
	policyCache.Mu.Lock()
	policyCache.PoliciesByProject[projectId] = policySpecifications
	policyCache.Mu.Unlock()
}
