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

func decodePolicySchema[T any](data []byte) (fndapi.Schema, error) {
	var schema fndapi.PolicySchema[T]

	if err := yaml.Unmarshal(data, &schema); err != nil {
		return nil, err
	}

	return &schema, nil
}

func schemaDecoder[T any](data []byte) (fndapi.Schema, error) {
	return decodePolicySchema[T](data)
}

var schemaDecoders = map[fndapi.PolicyName]func([]byte) (fndapi.Schema, error){
	fndapi.RestrictApplications:           schemaDecoder[fndapi.RestrictApplicationsConfig],
	fndapi.RestrictCutAndPaste:            schemaDecoder[fndapi.RestrictCutAndPasteConfig],
	fndapi.RestrictDownloads:              schemaDecoder[fndapi.RestrictDownloadsConfig],
	fndapi.RestrictIntegratedApplications: schemaDecoder[fndapi.RestrictIntegratedApplicationsConfig],
	fndapi.RestrictInternetAccess:         schemaDecoder[fndapi.RestrictInternetAccessConfig],
	fndapi.RestrictOrganizationMembers:    schemaDecoder[fndapi.RestrictOrganizationMembersConfig],
	fndapi.RestrictProviderFileTransfers:  schemaDecoder[fndapi.RestrictProviderFileTransfersConfig],
	fndapi.RestrictPublicIPs:              schemaDecoder[fndapi.RestrictPublicIPsConfig],
	fndapi.RestrictPublicLinks:            schemaDecoder[fndapi.RestrictPublicLinksConfig],
	fndapi.RestrictSourceIPRange:          schemaDecoder[fndapi.RestrictSourceIpRangeConfig],
}

func decodeRestrictApplications(data []byte, project rpc.ProjectId) (fndapi.Specification, error) {
	var values fndapi.RestrictApplicationsValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &fndapi.RestrictApplicationsSpecification{
		PolicySpecification: fndapi.PolicySpecification[fndapi.RestrictApplicationsValues]{
			Schema:  fndapi.RestrictApplications,
			Project: project,
			Values:  values,
		},
	}, nil
}

func decodeRestrictCutAndPaste(data []byte, project rpc.ProjectId) (fndapi.Specification, error) {
	var values fndapi.RestrictCutAndPasteValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &fndapi.RestrictCutAndPasteSpecification{
		PolicySpecification: fndapi.PolicySpecification[fndapi.RestrictCutAndPasteValues]{
			Schema:  fndapi.RestrictCutAndPaste,
			Project: project,
			Values:  values,
		},
	}, nil
}

func decodeRestrictDownloads(data []byte, project rpc.ProjectId) (fndapi.Specification, error) {
	var values fndapi.RestrictDownloadsValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &fndapi.RestrictDownloadsSpecification{
		PolicySpecification: fndapi.PolicySpecification[fndapi.RestrictDownloadsValues]{
			Schema:  fndapi.RestrictDownloads,
			Project: project,
			Values:  values,
		},
	}, nil
}

func decodeRestrictIntegratedApplications(data []byte, project rpc.ProjectId) (fndapi.Specification, error) {
	var values fndapi.RestrictIntegratedApplicationsValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &fndapi.RestrictIntegratedApplicationsSpecification{
		PolicySpecification: fndapi.PolicySpecification[fndapi.RestrictIntegratedApplicationsValues]{
			Schema:  fndapi.RestrictIntegratedApplications,
			Project: project,
			Values:  values,
		},
	}, nil
}

func decodeRestrictInternetAccess(data []byte, project rpc.ProjectId) (fndapi.Specification, error) {
	var values fndapi.RestrictInternetAccessValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &fndapi.RestrictInternetAccessSpecification{
		PolicySpecification: fndapi.PolicySpecification[fndapi.RestrictInternetAccessValues]{
			Schema:  fndapi.RestrictInternetAccess,
			Project: project,
			Values:  values,
		},
	}, nil
}

func decodeRestrictOrganizationMembers(data []byte, project rpc.ProjectId) (fndapi.Specification, error) {
	var values fndapi.RestrictOrganizationMembersValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &fndapi.RestrictOrganizationMembersSpecification{
		PolicySpecification: fndapi.PolicySpecification[fndapi.RestrictOrganizationMembersValues]{
			Schema:  fndapi.RestrictOrganizationMembers,
			Project: project,
			Values:  values,
		},
	}, nil
}

func decodeRestrictProviderTransfers(data []byte, project rpc.ProjectId) (fndapi.Specification, error) {
	var values fndapi.RestrictProviderFileTransfersValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &fndapi.RestrictProviderFileTransfersSpecification{
		PolicySpecification: fndapi.PolicySpecification[fndapi.RestrictProviderFileTransfersValues]{
			Schema:  fndapi.RestrictProviderFileTransfers,
			Project: project,
			Values:  values,
		},
	}, nil
}

func decodeRestrictPublicIPs(data []byte, project rpc.ProjectId) (fndapi.Specification, error) {
	var values fndapi.RestrictPublicIPsValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &fndapi.RestrictPublicIPsSpecification{
		PolicySpecification: fndapi.PolicySpecification[fndapi.RestrictPublicIPsValues]{
			Schema:  fndapi.RestrictPublicIPs,
			Project: project,
			Values:  values,
		},
	}, nil
}

func decodeRestrictPublicLinks(data []byte, project rpc.ProjectId) (fndapi.Specification, error) {
	var values fndapi.RestrictPublicLinksValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &fndapi.RestrictPublicLinksSpecification{
		PolicySpecification: fndapi.PolicySpecification[fndapi.RestrictPublicLinksValues]{
			Schema:  fndapi.RestrictPublicLinks,
			Project: project,
			Values:  values,
		},
	}, nil
}

func decodeRestrictSourceIPRange(data []byte, project rpc.ProjectId) (fndapi.Specification, error) {
	var values fndapi.RestrictSourceIPRangeValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &fndapi.RestrictSourceIPRangeSpecification{
		PolicySpecification: fndapi.PolicySpecification[fndapi.RestrictSourceIPRangeValues]{
			Schema:  fndapi.RestrictSourceIPRange,
			Project: project,
			Values:  values,
		},
	}, nil
}

var specificationDecoders = map[fndapi.PolicyName]func([]byte, rpc.ProjectId) (fndapi.Specification, error){
	fndapi.RestrictApplications:           decodeRestrictApplications,
	fndapi.RestrictCutAndPaste:            decodeRestrictCutAndPaste,
	fndapi.RestrictDownloads:              decodeRestrictDownloads,
	fndapi.RestrictIntegratedApplications: decodeRestrictIntegratedApplications,
	fndapi.RestrictInternetAccess:         decodeRestrictInternetAccess,
	fndapi.RestrictOrganizationMembers:    decodeRestrictOrganizationMembers,
	fndapi.RestrictProviderFileTransfers:  decodeRestrictProviderTransfers,
	fndapi.RestrictPublicIPs:              decodeRestrictPublicIPs,
	fndapi.RestrictPublicLinks:            decodeRestrictPublicLinks,
	fndapi.RestrictSourceIPRange:          decodeRestrictSourceIPRange,
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

		decoder, ok := schemaDecoders[fndapi.PolicyName(header.Name)]
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
