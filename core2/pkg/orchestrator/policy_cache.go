package orchestrator

import (
	"context"
	"sync"

	"ucloud.dk/core/pkg/coreutil"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
)

// policyCache is a mapping of projectId -> map[schemaName] -> PolicySpecification
var policyCache struct {
	Mu                sync.RWMutex
	PoliciesByProject map[string]map[string]*fndapi.PolicySpecification
}

func initPolicySubscriptions() {
	go func() {
		policyUpdates := db.Listen(context.Background(), "policy_updates")

		var policySpecifications map[string]*fndapi.PolicySpecification
		var policiesOk bool

		for {
			projectId := <-policyUpdates

			db.NewTx0(func(tx *db.Transaction) {
				policySpecifications, policiesOk = coreutil.PolicySpecificationsRetrieveFromDatabase(tx, projectId)
			})

			if policiesOk {
				updatePolicyCacheForProject(projectId, policySpecifications)
			}
		}

	}()
}

// policiesByProject returns mapping of [schema Name] => PolicySpecification. If no policy is cached for the project it
// will attempt to retrieve it from DB. This is also how it is populated.
func policiesByProject(projectId string) map[string]*fndapi.PolicySpecification {
	projectPolicies := map[string]*fndapi.PolicySpecification{}
	policyCache.Mu.Lock()
	projectPolicies, ok := policyCache.PoliciesByProject[projectId]
	if !ok {
		log.Debug("No policies for project %v", projectId)
		db.NewTx0(func(tx *db.Transaction) {
			policySpecifications, policiesOk := coreutil.PolicySpecificationsRetrieveFromDatabase(tx, projectId)
			if policiesOk {
				policyCache.PoliciesByProject[projectId] = policySpecifications
			} else {
				log.Debug("No policies for project %v found in DB", projectId)
			}
		})
	}
	policyCache.Mu.Unlock()

	return projectPolicies
}

func updatePolicyCacheForProject(projectId string, policySpecifications map[string]*fndapi.PolicySpecification) {
	policyCache.Mu.Lock()
	policyCache.PoliciesByProject[projectId] = policySpecifications
	policyCache.Mu.Unlock()
}
