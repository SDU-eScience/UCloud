package foundation

import (
	"encoding/json"
	"net/http"
	"sync"

	"golang.org/x/exp/maps"
	"gopkg.in/yaml.v3"
	"ucloud.dk/shared/pkg/cfgutil"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

var projectPolicies struct {
	Mu                sync.RWMutex
	PoliciesByProject map[string]*AssociatedPolicies
}

type AssociatedPolicies struct {
	EnabledPolices map[string]fndapi.PolicySpecification
}

var policySchemas map[string]fndapi.PolicySchema

func initPolicies() {
	policyPopulateSchemaCache()
	loadProjectPoliciesFromDB()

	fndapi.PoliciesRetrieve.Handler(func(info rpc.RequestInfo, request util.Empty) (map[string]fndapi.Policy, *util.HttpError) {
		return policiesRetrieve(info.Actor)
	})

	fndapi.PoliciesUpdate.Handler(func(info rpc.RequestInfo, request fndapi.PoliciesUpdateRequest) (util.Empty, *util.HttpError) {
		return policiesUpdate(info.Actor, request)
	})
}

func policyPopulateSchemaCache() {
	policies := pullProjectPolicies()
	policySchemas = make(map[string]fndapi.PolicySchema, len(policies))
	for _, policy := range policies {
		var document yaml.Node
		success := true

		err := yaml.Unmarshal(policy.Bytes, &document)
		if err != nil {
			log.Fatal("Error loading policy document ", policy.PolicyName, ": ", err)
		}

		var policySchema fndapi.PolicySchema

		cfgutil.Decode("", &document, &policySchema, &success)

		if !success {
			log.Fatal("Error decoding policy document ", policy.PolicyName, ": ", err)
		}
		policySchemas[policySchema.Name] = policySchema
	}
}

func loadProjectPoliciesFromDB() {
	projectPolicies.Mu.Lock()
	projectPolicies.PoliciesByProject = make(map[string]*AssociatedPolicies)
	projectPolicies.Mu.Unlock()

	db.NewTx0(func(tx *db.Transaction) {
		rows := db.Select[struct {
			ProjectId        string
			PolicyName       string
			PolicyProperties string
		}](
			tx,
			`
				select project_id, policy_name, policy_properties
				from project.policies
				order by project_id
		    `,
			db.Params{},
		)

		projectPolicies.Mu.Lock()

		for _, row := range rows {
			projectId := row.ProjectId
			policies, ok := projectPolicies.PoliciesByProject[projectId]
			if !ok {
				policies = &AssociatedPolicies{EnabledPolices: map[string]fndapi.PolicySpecification{}}
			}
			properties := []fndapi.PolicyPropertyValue{}
			err := json.Unmarshal([]byte(row.PolicyProperties), &properties)
			if err != nil {
				log.Fatal("Error loading policy document %v : %v", row.PolicyProperties, err)
			}
			specification := fndapi.PolicySpecification{
				Schema:     row.PolicyName,
				Project:    rpc.ProjectId(projectId),
				Properties: properties,
			}

			log.Debug("Loading policy %v \n", specification)

			policies.EnabledPolices[row.PolicyName] = specification
			projectPolicies.PoliciesByProject[projectId] = policies
		}

		projectPolicies.Mu.Unlock()
	})
}

func policiesRetrieve(actor rpc.Actor) (map[string]fndapi.Policy, *util.HttpError) {
	if !actor.Project.Present {
		return nil, util.HttpErr(http.StatusBadRequest, "Polices only applicable to projects")
	}
	if !actor.Membership[actor.Project.Value].Satisfies(rpc.ProjectRoleAdmin) {
		return nil, util.HttpErr(http.StatusForbidden, "Only PIs and Admins may list the policies")
	}

	result := make(map[string]fndapi.Policy, len(policySchemas))

	projectPolicies.Mu.RLock()
	policies := maps.Clone(projectPolicies.PoliciesByProject[string(actor.Project.Value)].EnabledPolices)
	projectPolicies.Mu.RUnlock()
	for name, schema := range policySchemas {

		specification, ok := policies[name]
		if !ok {
			specification = fndapi.PolicySpecification{}
		}
		result[name] = fndapi.Policy{
			Schema:        schema,
			Specification: specification,
		}
	}
	return result, nil
}

type SimplePolicyProperty struct {
	PropertyType  fndapi.PolicyPropertyType `yaml:"property_type"`
	PropertyValue any                       `yaml:"property_value"`
}

func policiesUpdate(actor rpc.Actor, request fndapi.PoliciesUpdateRequest) (util.Empty, *util.HttpError) {
	if !actor.Project.Present {
		return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Polices only applicable to projects")
	}
	if !actor.Membership[actor.Project.Value].Satisfies(rpc.ProjectRolePI) {
		return util.Empty{}, util.HttpErr(http.StatusForbidden, "Only PIs may update the policies")
	}

	filteredUpdates := map[string]map[string]fndapi.PolicySpecification{}
	for _, specification := range request.UpdatedPolicies {
		projectId := string(specification.Project)
		filteredUpdates[projectId][specification.Schema] = specification
	}

	db.NewTx0(func(tx *db.Transaction) {
		b := db.BatchNew(tx)
		for projectId, updates := range filteredUpdates {
			for _, specification := range updates {
				_, ok := policySchemas[specification.Schema]
				//When trying to update a schema that does not exist, we just skip it
				if !ok {
					log.Debug("Unknown Schema ", specification.Schema)
					continue
				}

				isEnabled := false
				for _, property := range specification.Properties {
					if property.Name == "enabled" {
						isEnabled = property.Bool
					}
				}

				//If the policy is not enabled we need to delete it form the DB.
				//Else we need to insert or update already existing project policy
				if !isEnabled {
					db.BatchExec(
						b,
						`
						delete from project.policies 
					    where project_id = :project_id and policy_name = :policy_name
				    `,
						db.Params{
							"project_id":  projectId,
							"policy_name": specification.Schema,
						},
					)
				} else {
					properties, err := json.Marshal(specification.Properties)
					if err != nil {
						log.Debug("Error marshalling policy document ", specification.Schema, " ", specification.Properties)
						continue
					}
					db.BatchExec(
						b,
						`
					insert into project.policies (policy_name, policy_property, project_id)
					values (:policy_name, :policy_properties, :project_id)
					on conflict (policy_name, project_id) do 
						update set policy_property = excluded.policy_property,
			    `,
						db.Params{
							"policy_name":       specification.Schema,
							"policy_properties": properties,
							"project_id":        projectId,
						},
					)
				}
			}
		}
		db.BatchSend(b)

		//Updating cache
		projectPolicies.Mu.Lock()
		for _, updates := range filteredUpdates {
			for _, specification := range updates {
				isEnabled := false
				for _, property := range specification.Properties {
					if property.Name == "enabled" {
						isEnabled = property.Bool
					}
				}
				projectId := string(specification.Project)
				if !isEnabled {
					policies, ok := projectPolicies.PoliciesByProject[projectId]
					//If no policies are enabled for the project then just skip the deletion
					if !ok {
						continue
					}
					policies.EnabledPolices[specification.Schema] = fndapi.PolicySpecification{}
				} else {
					policies, ok := projectPolicies.PoliciesByProject[projectId]
					if !ok {
						policies = &AssociatedPolicies{EnabledPolices: map[string]fndapi.PolicySpecification{}}
						projectPolicies.PoliciesByProject[projectId] = policies
					}
					policies.EnabledPolices[specification.Schema] = specification
				}
			}
		}
		projectPolicies.Mu.Unlock()
	})

	return util.Empty{}, nil
}
