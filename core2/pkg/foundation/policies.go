package foundation

import (
	"encoding/json"
	"fmt"
	"net"
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
			log.Fatal(fmt.Sprintf("Error loading policy document %v : %v \n", policy.PolicyName, err))
		}

		decoder, ok := fndapi.SchemaDecoders[fndapi.PolicyName(header.Name)]
		if !ok {
			log.Fatal(fmt.Sprintf("No decoder registered for policy %v \n", header.Name))
		}

		schema, err := decoder(policy.Bytes)
		if err != nil {
			log.Fatal(fmt.Sprintf("Error loading policy document %v : %v \n", policy.PolicyName, err))
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
// Only the endpoints listed in sourceIpRestrictedEndpoints are subject to the check. For those
// endpoints, the call is rejected if the client's IP address is not permitted by the policy of the
// actor's active project. Calls which are not subject to the policy, and calls which the policy
// allows, return a nil error.
func SourceIpPolicy(callName string, info rpc.RequestInfo) *util.HttpError {
	if _, ok := sourceIpRestrictedEndpoints[callName]; !ok {
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

var sourceIpRestrictedEndpoints = map[string]struct{}{
	// accounting
	"accounting.v2.rootAllocate":     {},
	"accounting.v2.updateAllocation": {},
	"accounting.v2.wallets.browse":   {},

	// drive
	"files.collections.browse":    {},
	"files.collections.retrieve":  {},
	"files.collections":           {},
	"files.collections.rename":    {},
	"files.collections.search":    {},
	"files.collections.updateAcl": {},
	"files.collections.delete":    {},

	// files
	"files.browse":          {},
	"files.retrieve":        {},
	"files.move":            {},
	"files.copy":            {},
	"files.upload":          {},
	"files.download":        {},
	"files.folder":          {},
	"files.delete":          {},
	"files.trash":           {},
	"files.emptyTrash":      {},
	"files.transfer":        {},
	"files.streamingSearch": {},

	// grants
	"grant.v2.export":         {},
	"grant.v2.exportCsv":      {},
	"grant.v2.browse":         {},
	"grant.v2.retrieve":       {},
	"grant.v2.submitRevision": {},
	"grant.v2.postComment":    {},
	"grant.v2.deleteComment":  {},

	// ingresses
	"ingresses.browse":           {},
	"ingresses.control.browse":   {},
	"ingresses.control.retrieve": {},
	"ingresses":                  {},
	"ingresses.retrieve":         {},
	"ingresses.search":           {},
	"ingresses.delete":           {},
	"ingresses.updateAcl":        {},

	// jobs
	"jobs":                       {},
	"jobs.control.browseSshKeys": {},

	// licenses
	"licenses.browse":    {},
	"licenses.retrieve":  {},
	"licenses.delete":    {},
	"licenses.updateAcl": {},
	"licenses":           {},

	// public_ips
	"networkips.browse":           {},
	"networkips.delete":           {},
	"networkips":                  {},
	"networkips.retrieve":         {},
	"networkips.control.retrieve": {},
	"networkips.updateAcl":        {},

	// ssh
	"ssh":          {},
	"ssh.retrieve": {},
	"ssh.browse":   {},
	"ssh.delete":   {},

	// tokens
	"tokens":        {},
	"tokens.browse": {},
	"tokens.revoke": {},
}
