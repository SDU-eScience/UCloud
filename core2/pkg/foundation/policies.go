package foundation

import (
	"encoding/json"
	"net"
	"net/http"
	"slices"
	"sync"

	"golang.org/x/exp/maps"
	"gopkg.in/yaml.v3"
	db "ucloud.dk/shared/pkg/database"
	fndapi "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// projectPolicies should not be used directly to retrieve policies
// but use
var projectPolicies struct {
	Mu                sync.RWMutex
	PoliciesByProject map[string]*AssociatedPolicies
}

type AssociatedPolicies struct {
	ConfiguredPolicies map[fndapi.PolicyName]fndapi.Specification
}

var policySchemas map[fndapi.PolicyName]fndapi.Schema

var policyGlobals struct {
	TestingEnabled            bool
	TestDefaultPolicySettings map[string]map[fndapi.PolicyName]fndapi.Specification
}

func initPolicies() {
	policyPopulateSchemaCache()
	loadProjectPoliciesFromDB()

	fndapi.PoliciesRetrieve.Handler(func(info rpc.RequestInfo, request fndapi.RetrievePoliciesRequest) (map[fndapi.PolicyName]fndapi.Policy, *util.HttpError) {
		if request.DefaultPolicy {
			return policiesDefaultRetrieve(info.Actor, request)
		}

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
			log.Fatal("Error loading policy document %v : %v \n", policy.PolicyName, err)
		}

		decoder, ok := fndapi.SchemaDecoders[fndapi.PolicyName(header.Name)]
		if !ok {
			log.Fatal("No decoder registered for policy %v \n", header.Name)
		}

		schema, err := decoder(policy.Bytes)
		if err != nil {
			log.Fatal("Error loading policy document %v : %v \n", policy.PolicyName, err)
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
			ProjectId        string `json:"project"`
			PolicyName       string `json:"schema"`
			PolicyProperties string `json:"values"`
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

		type policySpecificationRaw struct {
			Schema  fndapi.PolicyName `json:"schema"`
			Project rpc.ProjectId     `json:"project"`
			Values  json.RawMessage   `json:"values"`
		}

		for _, row := range rows {
			policies, ok := projectPolicies.PoliciesByProject[row.ProjectId]
			if !ok {
				policies = &AssociatedPolicies{
					ConfiguredPolicies: make(map[fndapi.PolicyName]fndapi.Specification),
				}
				projectPolicies.PoliciesByProject[row.ProjectId] = policies
			}
			policyName := fndapi.PolicyName(row.PolicyName)
			decoder, ok := fndapi.SpecificationDecoders[policyName]
			if !ok {
				log.Fatal("Unknown policy %v", policyName)
			}

			specificationData := policySpecificationRaw{
				Schema:  policyName,
				Project: rpc.ProjectId(row.ProjectId),
				Values:  json.RawMessage(row.PolicyProperties),
			}
			data, err := json.Marshal(specificationData)
			if err != nil {
				log.Debug("Failed to marshal policy specification: %v", err)
			}
			specification, err := decoder(data)

			if err != nil {
				log.Fatal("Error loading policy %v : %v", policyName, err)
			}

			policies.ConfiguredPolicies[policyName] = specification
		}
	})
}

func policiesDefaultRetrieve(actor rpc.Actor, request fndapi.RetrievePoliciesRequest) (map[fndapi.PolicyName]fndapi.Policy, *util.HttpError) {
	projectId := request.ProjectId

	if actor.Role != rpc.RoleProvider && actor.Role != rpc.RoleService {
		if !actor.Project.Present {
			return nil, util.HttpErr(http.StatusBadRequest, "Polices only applicable to projects")
		}
		if !actor.Membership[actor.Project.Value].Equals(rpc.ProjectRoleDataManager) {
			return nil, util.HttpErr(http.StatusForbidden, "Only data managers may list the policies")
		}
		projectId = actor.Project.String()
	}

	result := make(map[fndapi.PolicyName]fndapi.Policy, len(policySchemas))

	configuredDefaults := defaultPoliciesReadFromDb(projectId)

	for name, schema := range policySchemas {
		specification, ok := configuredDefaults[name]
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

	// Validate Specification Values
	for _, specification := range request.UpdatedPolicies {
		switch specification.GetSpecificationName() {
		case fndapi.RestrictApiTokens:
			{
				_, ok := specification.GetValues().(fndapi.RestrictApiTokensValues)
				if !ok {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Malformed policy specification (API Tokens)")
				}
				break
			}
		case fndapi.RestrictApplications:
			{
				_, ok := specification.GetValues().(fndapi.RestrictApplicationsValues)
				if !ok {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Malformed policy specification (Applications)")
				}
				break
			}
		case fndapi.RestrictCutAndPaste:
			{
				_, ok := specification.GetValues().(fndapi.RestrictCutAndPasteValues)
				if !ok {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Malformed policy specification (Cut and Paste)")
				}
				break
			}
		case fndapi.RestrictDownloads:
			{
				_, ok := specification.GetValues().(fndapi.RestrictDownloadsValues)
				if !ok {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Malformed policy specification (Downloads)")
				}
				break
			}
		case fndapi.RestrictExternalProjectFolderMounting:
			{
				_, ok := specification.GetValues().(fndapi.RestrictExternalProjectFolderMountingValues)
				if !ok {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Malformed policy specification (External Project Folder Mounting)")
				}
				break
			}
		case fndapi.RestrictIntegratedApplications:
			{
				_, ok := specification.GetValues().(fndapi.RestrictIntegratedApplicationsValues)
				if !ok {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Malformed policy specification (Integrated Applications)")
				}
				break
			}
		case fndapi.RestrictInternetAccess:
			{
				values, ok := specification.GetValues().(fndapi.RestrictInternetAccessValues)
				if !ok {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Malformed policy specification (Internet Access)")
				}
				subnet := values.AllowedSubnets
				if len(subnet) == 0 {
					// Empty is allowed and should be handled by the code as no one is allowed
					break
				}
				_, _, err := net.ParseCIDR(subnet)
				if err != nil {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Malformed policy specification (Internet Access, CIDR)")
				}
				break
			}
		case fndapi.RestrictMoveAndCopy:
			{
				_, ok := specification.GetValues().(fndapi.RestrictMoveAndCopyValues)
				if !ok {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Malformed policy specification (MoveAndCopy)")
				}
				break
			}
		case fndapi.RestrictOrganizationMembers:
			{
				_, ok := specification.GetValues().(fndapi.RestrictOrganizationMembersValues)
				if !ok {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Malformed policy specification (Organization Members)")
				}
				break
			}
		case fndapi.RestrictProviderFileTransfers:
			{
				_, ok := specification.GetValues().(fndapi.RestrictProviderFileTransfersValues)
				if !ok {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Malformed policy specification (Provider Transfer)")
				}
				break
			}
		case fndapi.RestrictPublicIPs:
			{
				_, ok := specification.GetValues().(fndapi.RestrictPublicIPsValues)
				if !ok {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Malformed policy specification (Public IPs)")
				}
				break
			}
		case fndapi.RestrictPublicLinks:
			{
				_, ok := specification.GetValues().(fndapi.RestrictPublicLinksValues)
				if !ok {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Malformed policy specification (Public Links)")
				}
				break
			}
		case fndapi.RestrictSourceIPRange:
			{
				values, ok := specification.GetValues().(fndapi.RestrictSourceIPRangeValues)
				if !ok {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Malformed policy specification (Source IP Range)")
				}
				subnet := values.AllowedSubnets
				if len(subnet) == 0 {
					// Empty is allowed and should be handled by the code as no one is allowed
					break
				}
				_, _, err := net.ParseCIDR(subnet)
				if err != nil {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Malformed policy specification (Source IP Range, CIDR)")
				}
				break
			}
		case fndapi.RestrictSsh:
			{
				_, ok := specification.GetValues().(fndapi.RestrictSshValues)
				if !ok {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Malformed policy specification (SSH)")
				}
				break
			}
		case fndapi.RestrictUploads:
			{
				_, ok := specification.GetValues().(fndapi.RestrictUploadsValues)
				if !ok {
					return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Malformed policy specification (Uploads)")
				}
				break
			}
		default:
			{
				return util.Empty{}, util.HttpErr(http.StatusBadRequest, "Unknown policy")
			}
		}
	}
	if request.DefaultPolicy {
		if policyGlobals.TestingEnabled {
			// Tests have no database, keep the setting in memory instead
			for _, specification := range request.UpdatedPolicies {
				projectID := string(specification.GetProject())

				policies := policyGlobals.TestDefaultPolicySettings[projectID]
				if policies == nil {
					policies = make(map[fndapi.PolicyName]fndapi.Specification)
					policyGlobals.TestDefaultPolicySettings[projectID] = policies
				}

				policies[specification.GetSpecificationName()] = specification
			}

			return util.Empty{}, nil
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
					insert into project.default_policy_settings (
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

		return util.Empty{}, nil
	}
	if !policyGlobals.TestingEnabled {
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
	}

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

func defaultPoliciesReadFromDb(projectId string) map[fndapi.PolicyName]fndapi.Specification {
	if policyGlobals.TestingEnabled {
		return maps.Clone(policyGlobals.TestDefaultPolicySettings[projectId])
	}

	type policySpecificationRaw struct {
		Schema  fndapi.PolicyName `json:"schema"`
		Project rpc.ProjectId     `json:"project"`
		Values  json.RawMessage   `json:"values"`
	}

	return db.NewTx(func(tx *db.Transaction) map[fndapi.PolicyName]fndapi.Specification {
		rows := db.Select[struct {
			ProjectId        string `json:"project"`
			PolicyName       string `json:"schema"`
			PolicyProperties string `json:"values"`
		}](
			tx,
			`
			select project_id, policy_name, policy_properties
			from project.default_policy_settings
			where project_id = :project_id
			`,
			db.Params{
				"project_id": projectId,
			},
		)

		policies := make(map[fndapi.PolicyName]fndapi.Specification)

		for _, row := range rows {
			policyName := fndapi.PolicyName(row.PolicyName)
			decoder, ok := fndapi.SpecificationDecoders[policyName]
			if !ok {
				log.Warn("Unknown policy in the default policy setting: %v", policyName)
				continue
			}

			data, err := json.Marshal(policySpecificationRaw{
				Schema:  policyName,
				Project: rpc.ProjectId(row.ProjectId),
				Values:  json.RawMessage(row.PolicyProperties),
			})
			if err != nil {
				log.Warn("Failed to marshal policy specification: %v", err)
				continue
			}

			specification, err := decoder(data)
			if err != nil {
				log.Warn("Error loading policy %v : %v", policyName, err)
				continue
			}

			policies[policyName] = specification
		}
		return policies
	})
}

func applyDefaultPoliciesToNewSubproject(grantGiverProjectIds []string, projectId string) {
	configuredByGiver := make([]map[fndapi.PolicyName]fndapi.Specification, 0, len(grantGiverProjectIds))
	for _, giver := range grantGiverProjectIds {
		defaults := defaultPoliciesReadFromDb(giver)
		if len(defaults) > 0 {
			configuredByGiver = append(configuredByGiver, defaults)
		}
	}

	if len(configuredByGiver) == 0 {
		// No grant giver has a saved setting. The hardcoded default (all policies disabled) requires no work
		// since disabled policies are equivalent to unconfigured policies.
		return
	}

	// Collect every policy configured by at least one grant giver
	policyNames := map[fndapi.PolicyName]util.Empty{}
	for _, defaults := range configuredByGiver {
		for name := range defaults {
			policyNames[name] = util.Empty{}
		}
	}

	type policySpecificationRaw struct {
		Schema  fndapi.PolicyName `json:"schema"`
		Project rpc.ProjectId     `json:"project"`
		Values  json.RawMessage   `json:"values"`
	}

	var specifications []fndapi.Specification

	applyMergedPolicies := func(b *db.Batch) {
		for policyName := range policyNames {
			var values []any
			for _, defaults := range configuredByGiver {
				if specification, ok := defaults[policyName]; ok {
					values = append(values, specification.GetValues())
				}
			}

			mergedValues, enabled := mergeDefaultPolicyValues(policyName, values)
			if !enabled {
				continue
			}

			properties, err := json.Marshal(mergedValues)
			if err != nil {
				log.Warn("Failed to marshal the default policy %s: %v", policyName, err)
				continue
			}

			// Decode the specification such that the in-memory policy cache can be updated.
			decoder, hasDecoder := fndapi.SpecificationDecoders[policyName]
			if !hasDecoder {
				log.Warn("Unknown policy in the default policy setting: %v", policyName)
				continue
			}

			data, err := json.Marshal(policySpecificationRaw{
				Schema:  policyName,
				Project: rpc.ProjectId(projectId),
				Values:  properties,
			})
			if err != nil {
				continue
			}

			specification, err := decoder(data)
			if err != nil {
				log.Warn("Failed to decode the default policy %s: %v", policyName, err)
				continue
			}

			specifications = append(specifications, specification)
		}
	}

	if policyGlobals.TestingEnabled {
		applyMergedPolicies(nil)
	} else {
		db.NewTx0(func(tx *db.Transaction) {
			b := db.BatchNew(tx)
			applyMergedPolicies(b)
			db.BatchSend(b)
		})
	}

	// Update the in-memory policy cache of this service if any changes even apply.
	// The database trigger on project.policies notifies the remaining services about the change.
	if len(specifications) > 0 {
		projectPolicies.Mu.Lock()

		entry, ok := projectPolicies.PoliciesByProject[projectId]
		if !ok {
			entry = &AssociatedPolicies{
				ConfiguredPolicies: make(map[fndapi.PolicyName]fndapi.Specification),
			}
			projectPolicies.PoliciesByProject[projectId] = entry
		}

		for _, specification := range specifications {
			entry.ConfiguredPolicies[specification.GetSpecificationName()] = specification
		}
		projectPolicies.Mu.Unlock()
	}
}

func intersectAllowLists(lists [][]string) []string {
	if len(lists) == 0 {
		return nil
	}

	result := map[string]util.Empty{}
	for _, item := range lists[0] {
		result[item] = util.Empty{}
	}

	for _, list := range lists[1:] {
		next := map[string]util.Empty{}
		for _, item := range list {
			if _, ok := result[item]; ok {
				next[item] = util.Empty{}
			}
		}
		result = next
	}

	var resultSlice []string
	for item := range result {
		resultSlice = append(resultSlice, item)
	}
	slices.Sort(resultSlice)
	return resultSlice
}

func intersectSubnets(subnets []string) string {
	if len(subnets) == 0 {
		return ""
	}

	_, result, err := net.ParseCIDR(subnets[0])
	if err != nil {
		return ""
	}

	for _, subnet := range subnets[1:] {
		_, network, err := net.ParseCIDR(subnet)
		if err != nil {
			return ""
		}

		if result.Contains(network.IP) {
			// network is contained in result (or equal): the intersection is network
			result = network
		} else if network.Contains(result.IP) {
			// result is contained in network: the intersection is unchanged
		} else {
			// The blocks are disjoint: no address is allowed by all grant givers
			return ""
		}
	}

	return result.String()
}

// mergeDefaultPolicyValues merges policies from multiple grant givers always resulting in most restrictive result
//
//   - A policy is enabled in the result if it is enabled by at least one grant giver (union of restrictions).
//   - Allow-lists (applications, integrated applications, organizations, providers and subnets) are intersected
//     across all grant givers which have the policy enabled.
//
// Grant givers which have not configured the policy (or have it disabled) impose no restrictions and thus do not
// constrain the result. The boolean return value reports whether the merged policy is enabled.
func mergeDefaultPolicyValues(policyName fndapi.PolicyName, values []any) (any, bool) {
	switch policyName {
	case fndapi.RestrictApiTokens:
		merged := fndapi.RestrictApiTokensValues{}
		for _, v := range values {
			if val, ok := v.(fndapi.RestrictApiTokensValues); ok && val.Enabled {
				merged.Enabled = true
			}
		}
		return merged, merged.Enabled

	case fndapi.RestrictCutAndPaste:
		merged := fndapi.RestrictCutAndPasteValues{}
		for _, v := range values {
			if val, ok := v.(fndapi.RestrictCutAndPasteValues); ok && val.Enabled {
				merged.Enabled = true
			}
		}
		return merged, merged.Enabled

	case fndapi.RestrictDownloads:
		merged := fndapi.RestrictDownloadsValues{}
		for _, v := range values {
			if val, ok := v.(fndapi.RestrictDownloadsValues); ok && val.Enabled {
				merged.Enabled = true
			}
		}
		return merged, merged.Enabled

	case fndapi.RestrictExternalProjectFolderMounting:
		merged := fndapi.RestrictExternalProjectFolderMountingValues{}
		for _, v := range values {
			if val, ok := v.(fndapi.RestrictExternalProjectFolderMountingValues); ok && val.Enabled {
				merged.Enabled = true
			}
		}
		return merged, merged.Enabled

	case fndapi.RestrictMoveAndCopy:
		merged := fndapi.RestrictMoveAndCopyValues{}
		for _, v := range values {
			if val, ok := v.(fndapi.RestrictMoveAndCopyValues); ok && val.Enabled {
				merged.Enabled = true
			}
		}
		return merged, merged.Enabled

	case fndapi.RestrictPublicIPs:
		merged := fndapi.RestrictPublicIPsValues{}
		for _, v := range values {
			if val, ok := v.(fndapi.RestrictPublicIPsValues); ok && val.Enabled {
				merged.Enabled = true
			}
		}
		return merged, merged.Enabled

	case fndapi.RestrictPublicLinks:
		merged := fndapi.RestrictPublicLinksValues{}
		for _, v := range values {
			if val, ok := v.(fndapi.RestrictPublicLinksValues); ok && val.Enabled {
				merged.Enabled = true
			}
		}
		return merged, merged.Enabled

	case fndapi.RestrictSsh:
		merged := fndapi.RestrictSshValues{}
		for _, v := range values {
			if val, ok := v.(fndapi.RestrictSshValues); ok && val.Enabled {
				merged.Enabled = true
			}
		}
		return merged, merged.Enabled

	case fndapi.RestrictUploads:
		merged := fndapi.RestrictUploadsValues{}
		for _, v := range values {
			if val, ok := v.(fndapi.RestrictUploadsValues); ok && val.Enabled {
				merged.Enabled = true
			}
		}
		return merged, merged.Enabled

	case fndapi.RestrictApplications:
		merged := fndapi.RestrictApplicationsValues{}
		var lists [][]string
		for _, v := range values {
			if val, ok := v.(fndapi.RestrictApplicationsValues); ok && val.Enabled {
				merged.Enabled = true
				lists = append(lists, val.Applications)
			}
		}
		if merged.Enabled {
			merged.Applications = intersectAllowLists(lists)
		}
		return merged, merged.Enabled

	case fndapi.RestrictIntegratedApplications:
		merged := fndapi.RestrictIntegratedApplicationsValues{}
		var lists [][]string
		for _, v := range values {
			if val, ok := v.(fndapi.RestrictIntegratedApplicationsValues); ok && val.Enabled {
				merged.Enabled = true
				lists = append(lists, val.AllowList)
			}
		}
		if merged.Enabled {
			merged.AllowList = intersectAllowLists(lists)
		}
		return merged, merged.Enabled

	case fndapi.RestrictOrganizationMembers:
		merged := fndapi.RestrictOrganizationMembersValues{}
		var lists [][]string
		for _, v := range values {
			if val, ok := v.(fndapi.RestrictOrganizationMembersValues); ok && val.Enabled {
				merged.Enabled = true
				lists = append(lists, val.Organizations)
			}
		}
		if merged.Enabled {
			merged.Organizations = intersectAllowLists(lists)
		}
		return merged, merged.Enabled

	case fndapi.RestrictProviderFileTransfers:
		merged := fndapi.RestrictProviderFileTransfersValues{}
		var lists [][]string
		for _, v := range values {
			if val, ok := v.(fndapi.RestrictProviderFileTransfersValues); ok && val.Enabled {
				merged.Enabled = true
				lists = append(lists, val.AllowedProviders)
			}
		}
		if merged.Enabled {
			merged.AllowedProviders = intersectAllowLists(lists)
		}
		return merged, merged.Enabled

	case fndapi.RestrictInternetAccess:
		merged := fndapi.RestrictInternetAccessValues{}
		var subnets []string
		for _, v := range values {
			if val, ok := v.(fndapi.RestrictInternetAccessValues); ok && val.Enabled {
				merged.Enabled = true
				subnets = append(subnets, val.AllowedSubnets)
			}
		}
		if merged.Enabled {
			merged.AllowedSubnets = intersectSubnets(subnets)
		}
		return merged, merged.Enabled

	case fndapi.RestrictSourceIPRange:
		merged := fndapi.RestrictSourceIPRangeValues{}
		var subnets []string
		for _, v := range values {
			if val, ok := v.(fndapi.RestrictSourceIPRangeValues); ok && val.Enabled {
				merged.Enabled = true
				subnets = append(subnets, val.AllowedSubnets)
			}
		}
		if merged.Enabled {
			merged.AllowedSubnets = intersectSubnets(subnets)
		}
		return merged, merged.Enabled

	default:
		log.Warn("Cannot merge default policy values of unknown policy: %v", policyName)
		return nil, false
	}
}

// ApiTokensIsRestricted reports whether the project has enabled the "RestrictApiTokens" policy.
// When true, authentication via API tokens must be rejected for the project.
func ApiTokensIsRestricted(projectId string) bool {
	projectPolicies.Mu.RLock()
	configured, ok := projectPolicies.PoliciesByProject[projectId]
	projectPolicies.Mu.RUnlock()

	if !ok {
		return false
	}

	specification, ok := configured.ConfiguredPolicies[fndapi.RestrictApiTokens]
	if !ok {
		return false
	}

	return specification.IsEnabled()
}

// SourceIpPolicy enforces the "RestrictSourceIPRange" project policy for a single RPC call. It is
// installed as the request policy of the RPC server and is consulted before every incoming request.
//
// Only the endpoints marked with restrictSourceIp = true are subject to the check. For those
// endpoints, the call is rejected if the client's IP address is not permitted by the policy of the
// actor's active project. Calls which are not subject to the policy, and calls which the policy
// allows, return a nil error.
func SourceIpPolicy(callName string, info rpc.RequestInfo, restrictSourceIP bool) *util.HttpError {
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

func SourceIpIsRestricted(info rpc.RequestInfo) bool {
	if !info.Actor.Project.Present {
		return false
	}

	projectPolicies.Mu.RLock()
	_, ok := projectPolicies.PoliciesByProject[string(info.Actor.Project.Value)]
	if !ok {
		projectPolicies.PoliciesByProject[string(info.Actor.Project.Value)] = &AssociatedPolicies{ConfiguredPolicies: make(map[fndapi.PolicyName]fndapi.Specification)}
	}
	policies := maps.Clone(projectPolicies.PoliciesByProject[string(info.Actor.Project.Value)].ConfiguredPolicies)
	projectPolicies.Mu.RUnlock()

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
