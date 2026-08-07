package foundation

import (
	"encoding/json"
	"net/http"
	"sync"

	"golang.org/x/exp/maps"
	"gopkg.in/yaml.v3"
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
	ConfiguredPolicies map[fndapi.PolicyName]fndapi.Specification
}

var policySchemas map[fndapi.PolicyName]fndapi.Schema

func initPolicies() {
	policyPopulateSchemaCache()
	loadProjectPoliciesFromDB()

	fndapi.PoliciesRetrieve.Handler(func(info rpc.RequestInfo, request fndapi.RetrievePoliciesRequest) (map[fndapi.PolicyName]fndapi.Policy, *util.HttpError) {
		return policiesRetrieve(info.Actor, request)
	})

	fndapi.PoliciesUpdate.Handler(func(info rpc.RequestInfo, request fndapi.PoliciesUpdateRequest) (util.Empty, *util.HttpError) {
		return policiesUpdate(info.Actor, request)
	})
}

func policyPopulateSchemaCache() {
	policies := pullProjectPolicies()
	policySchemas = make(map[fndapi.PolicyName]fndapi.Schema, len(policies))
	for _, policy := range policies {
		var header struct {
			Name string `yaml:"name"`
		}

		if err := yaml.Unmarshal(policy.Bytes, &header); err != nil {
			log.Fatal("Error loading policy document ", policy.PolicyName, ": ", err)
		}

		decoder, ok := fndapi.SchemaDecoders[fndapi.PolicyName(header.Name)]
		if !ok {
			log.Fatal("No decoder registered for policy ", header.Name)
		}

		schema, err := decoder(policy.Bytes)
		if err != nil {
			log.Fatal("Error loading policy document ", policy.PolicyName, ": ", err)
		}

		policySchemas[schema.GetSchemaName()] = schema
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
		defer projectPolicies.Mu.Unlock()

		for _, row := range rows {
			policies, ok := projectPolicies.PoliciesByProject[row.ProjectId]
			if !ok {
				policies = &AssociatedPolicies{
					ConfiguredPolicies: make(map[fndapi.PolicyName]fndapi.Specification),
				}
				projectPolicies.PoliciesByProject[row.ProjectId] = policies
			}
			pname := fndapi.PolicyName(row.PolicyName)
			decoder, ok := specificationDecoders[pname]
			if !ok {
				log.Fatal("Unknown policy %v", row.PolicyName)
			}

			specification, err := decoder(
				[]byte(row.PolicyProperties),
				rpc.ProjectId(row.ProjectId),
			)
			if err != nil {
				log.Fatal("Error loading policy %v : %v", row.PolicyName, err)
			}

			policies.ConfiguredPolicies[pname] = specification
		}
	})
}

func policiesRetrieve(actor rpc.Actor, request fndapi.RetrievePoliciesRequest) (map[fndapi.PolicyName]fndapi.Policy, *util.HttpError) {
	projectId := request.ProjectId
	if actor.Role != rpc.RoleProvider {
		if !actor.Project.Present {
			return nil, util.HttpErr(http.StatusBadRequest, "Polices only applicable to projects")
		}
		if !actor.Membership[actor.Project.Value].Equals(rpc.ProjectRoleDataManager) {
			return nil, util.HttpErr(http.StatusForbidden, "Only data managers may list the policies")
		}
		projectId = actor.Project.String()
	}

	result := make(map[fndapi.PolicyName]fndapi.Policy, len(policySchemas))

	projectPolicies.Mu.Lock()
	_, ok := projectPolicies.PoliciesByProject[projectId]
	if !ok {
		projectPolicies.PoliciesByProject[projectId] = &AssociatedPolicies{ConfiguredPolicies: make(map[fndapi.PolicyName]fndapi.Specification)}
	}
	policies := maps.Clone(projectPolicies.PoliciesByProject[projectId].ConfiguredPolicies)
	projectPolicies.Mu.Unlock()
	for name, schema := range policySchemas {

		specification, ok := policies[name]
		if !ok {
			specification = nil
		}
		result[name] = fndapi.Policy{
			Schema:        schema,
			Specification: specification,
		}
	}
	return result, nil
}

func policiesUpdate(actor rpc.Actor, request fndapi.PoliciesUpdateRequest) (util.Empty, *util.HttpError) {
	if !actor.Project.Present {
		return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Polices only applicable to projects")
	}

	if !actor.Membership[actor.Project.Value].Equals(rpc.ProjectRoleDataManager) {
		return util.Empty{}, util.HttpErr(http.StatusForbidden, "Only data managers may update the policies")
	}

	//Validate that all updates are for the active project
	for _, specification := range request.UpdatedPolicies {
		if specification.GetProject() != actor.Project.Value {
			return util.Empty{}, util.HttpErr(http.StatusBadRequest, "You can only update policies in the current project")
		}
	}

	db.NewTx0(func(tx *db.Transaction) {
		b := db.BatchNew(tx)
		for _, specification := range request.UpdatedPolicies {
			policyName := specification.GetSpecificationName()

			if _, ok := policySchemas[policyName]; !ok {
				log.Warn("Unknown Schema: %v ", policyName)
				continue
			}

			properties, err := json.Marshal(specification.GetValues())
			if err != nil {
				log.Warn("Failed to marshal policy %s: %v", policyName, err)
				continue
			}

			db.BatchExec(
				b,
				`
				insert into project.policies (
					project_id,
					policy_name,
					policy_properties,
					modified_at
				)
				values (
					:project_id,
					:policy_name,
					:policy_properties,
					now()
				)
				on conflict (project_id, policy_name)
				do update set
					policy_properties = excluded.policy_properties,
					modified_at = now()
				`,
				db.Params{
					"project_id":        specification.GetProject(),
					"policy_name":       policyName,
					"policy_properties": properties,
				},
			)
		}

		db.BatchSend(b)
	})

	//Updating cache
	projectPolicies.Mu.Lock()
	defer projectPolicies.Mu.Unlock()

	for _, specification := range request.UpdatedPolicies {
		projectID := string(specification.GetProject())

		policies := projectPolicies.PoliciesByProject[projectID]
		if policies == nil {
			policies = &AssociatedPolicies{
				ConfiguredPolicies: make(map[fndapi.PolicyName]fndapi.Specification),
			}
			projectPolicies.PoliciesByProject[projectID] = policies
		}

		policies.ConfiguredPolicies[specification.GetSpecificationName()] = specification
	}

	return util.Empty{}, nil
}
