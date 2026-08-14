package orchestrator

import (
	"context"
	"fmt"
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
	fmt.Printf("polices was cached ok: %v \n ", ok)
	if !ok {
		fmt.Printf("Getting policies by project %v\n", projectId)
		db.NewTx0(func(tx *db.Transaction) {
			policySpecifications, policiesOk := coreutil.PolicySpecificationsRetrieveFromDatabase(tx, projectId)
			if policiesOk {
				fmt.Printf("Setting cache for policy %v\n", projectId)
				for name, specification := range policySpecifications {
					fmt.Printf("Setting %v with specification %v\n", name, specification.GetValues())
				}
				policyCache.PoliciesByProject[projectId] = policySpecifications
				projectPolicies = policySpecifications
			}
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
