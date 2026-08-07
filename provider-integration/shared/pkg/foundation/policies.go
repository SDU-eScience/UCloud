package foundation

import (
	"encoding/json"

	"gopkg.in/yaml.v3"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

// Model
// =====================================================================================================================

// Policy "Name"s. Remember to edit here when adding or editing policies
type PolicyName string

const (
	RestrictApplications           PolicyName = "RestrictApplications"
	RestrictCutAndPaste            PolicyName = "RestrictCutAndPaste"
	RestrictDownloads              PolicyName = "RestrictDownloads"
	RestrictIntegratedApplications PolicyName = "RestrictIntegratedApplications"
	RestrictInternetAccess         PolicyName = "RestrictInternetAccess"
	RestrictOrganizationMembers    PolicyName = "RestrictOrganizationMembers"
	RestrictProviderFileTransfers  PolicyName = "RestrictProviderFileTransfers"
	RestrictPublicIPs              PolicyName = "RestrictPublicIPs"
	RestrictPublicLinks            PolicyName = "RestrictPublicLinks"
	RestrictSourceIPRange          PolicyName = "RestrictSourceIPRange"
)

func (t PolicyName) String() string {
	return string(t)
}

type PoliciesForProject struct {
	ProjectId      string
	PoliciesByName map[PolicyName]Specification
}

type Policy struct {
	Schema        Schema        `yaml:"schema" json:"schema"`
	Specification Specification `yaml:"specification,omitempty" json:"specification,omitempty"`
}

type Specification interface {
	GetSpecificationName() PolicyName
	GetProject() rpc.ProjectId
	IsEnabled() bool
	GetValues() any
}

type PolicySpecification[T any] struct {
	Schema  PolicyName    `yaml:"schema" json:"schema"`
	Project rpc.ProjectId `yaml:"project" json:"project"`
	Values  T             `yaml:"values" json:"values"`
}

func (s *PolicySpecification[T]) GetSpecificationName() PolicyName {
	return s.Schema
}

func (s *PolicySpecification[T]) GetProject() rpc.ProjectId {
	return s.Project
}

func (s *PolicySpecification[T]) GetValues() any {
	return s.Values
}

type Schema interface {
	GetSchemaName() PolicyName
	GetSchemaTitle() string
	GetSchemaDescription() string
}
type PolicySchema[T any] struct {
	Name          PolicyName `yaml:"name" json:"name"`
	Title         string     `yaml:"title" json:"title"`
	Description   string     `yaml:"description" json:"description"`
	Configuration T          `yaml:"configuration" json:"configuration"`
}

func (s *PolicySchema[T]) GetSchemaName() PolicyName {
	return s.Name
}
func (s *PolicySchema[T]) GetSchemaTitle() string {
	return s.Title
}
func (s *PolicySchema[T]) GetSchemaDescription() string {
	return s.Description
}

type Property struct {
	Title       string   `yaml:"title" json:"title"`
	Description string   `yaml:"description" json:"description"`
	Options     []string `yaml:"options,omitempty" json:"options,omitempty"`
}

// Restrict Applications
type RestrictApplicationsConfig struct {
	Enabled      Property `yaml:"enabled" json:"enabled"`
	Applications Property `yaml:"applications" json:"applications"`
}
type RestrictApplicationsSchema = PolicySchema[RestrictApplicationsConfig]

type RestrictApplicationsValues struct {
	Enabled      bool     `yaml:"enabled" json:"enabled"`
	Applications []string `yaml:"applications" json:"applications"`
}
type RestrictApplicationsSpecification struct {
	PolicySpecification[RestrictApplicationsValues]
}

func (r *RestrictApplicationsSpecification) IsEnabled() bool {
	return r.Values.Enabled
}

// Restrict Cut and Paste
type RestrictCutAndPasteConfig struct {
	Enabled Property `yaml:"enabled" json:"enabled"`
}
type RestrictCutAndPasteSchema = PolicySchema[RestrictCutAndPasteConfig]

type RestrictCutAndPasteValues struct {
	Enabled bool `yaml:"enabled" json:"enabled"`
}
type RestrictCutAndPasteSpecification struct {
	PolicySpecification[RestrictCutAndPasteValues]
}

func (r *RestrictCutAndPasteSpecification) IsEnabled() bool {
	return r.Values.Enabled
}

// Restrict Download
type RestrictDownloadsConfig struct {
	Enabled Property `yaml:"enabled" json:"enabled"`
}
type RestrictDownloadsSchema = PolicySchema[RestrictDownloadsConfig]

type RestrictDownloadsValues struct {
	Enabled bool `yaml:"enabled" json:"enabled"`
}
type RestrictDownloadsSpecification struct {
	PolicySpecification[RestrictDownloadsValues]
}

func (r *RestrictDownloadsSpecification) IsEnabled() bool {
	return r.Values.Enabled
}

// Restrict Integrated Applications
type RestrictIntegratedApplicationsConfig struct {
	Enabled   Property `yaml:"enabled" json:"enabled"`
	AllowList Property `yaml:"allowList" json:"allowList"`
}
type RestrictIntegratedApplicationsSchema = PolicySchema[RestrictIntegratedApplicationsConfig]

type RestrictIntegratedApplicationsValues struct {
	Enabled   bool     `yaml:"enabled" json:"enabled"`
	AllowList []string `yaml:"allowList" json:"allowList"`
}
type RestrictIntegratedApplicationsSpecification struct {
	PolicySpecification[RestrictIntegratedApplicationsValues]
}

func (r *RestrictIntegratedApplicationsSpecification) IsEnabled() bool {
	return r.Values.Enabled
}

// Restrict Internet Access
type RestrictInternetAccessConfig struct {
	Enabled        Property `yaml:"enabled" json:"enabled"`
	AllowedSubnets Property `yaml:"allowedSubnets" json:"allowedSubnets"`
}
type RestrictInternetAccessSchema = PolicySchema[RestrictInternetAccessConfig]

type RestrictInternetAccessValues struct {
	Enabled        bool   `yaml:"enabled" json:"enabled"`
	AllowedSubnets string `yaml:"allowedSubnets" json:"allowedSubnets"`
}
type RestrictInternetAccessSpecification struct {
	PolicySpecification[RestrictInternetAccessValues]
}

func (r *RestrictInternetAccessSpecification) IsEnabled() bool {
	return r.Values.Enabled
}

// Restrict Organization Members
type RestrictOrganizationMembersConfig struct {
	Enabled       Property `yaml:"enabled" json:"enabled"`
	Organizations Property `yaml:"organizations" json:"organizations"`
}
type RestrictOrganizationMembersSchema = PolicySchema[RestrictOrganizationMembersConfig]

type RestrictOrganizationMembersValues struct {
	Enabled       bool     `yaml:"enabled" json:"enabled"`
	Organizations []string `yaml:"organizations" json:"organizations"`
}
type RestrictOrganizationMembersSpecification struct {
	PolicySpecification[RestrictOrganizationMembersValues]
}

func (r *RestrictOrganizationMembersSpecification) IsEnabled() bool {
	return r.Values.Enabled
}

// Restrict Provider File Transfers
type RestrictProviderFileTransfersConfig struct {
	Enabled          Property `yaml:"enabled" json:"enabled"`
	AllowedProviders Property `yaml:"allowedProviders" json:"allowedProviders"`
}
type RestrictProviderFileTransfersSchema = PolicySchema[RestrictProviderFileTransfersConfig]

type RestrictProviderFileTransfersValues struct {
	Enabled          bool     `yaml:"enabled" json:"enabled"`
	AllowedProviders []string `yaml:"allowedProviders" json:"allowedProviders"`
}
type RestrictProviderFileTransfersSpecification struct {
	PolicySpecification[RestrictProviderFileTransfersValues]
}

func (r *RestrictProviderFileTransfersSpecification) IsEnabled() bool {
	return r.Values.Enabled
}

// Restrict Public IPs
type RestrictPublicIPsConfig struct {
	Enabled Property `yaml:"enabled" json:"enabled"`
}
type RestrictPublicIPsSchema = PolicySchema[RestrictPublicIPsConfig]

type RestrictPublicIPsValues struct {
	Enabled bool `yaml:"enabled" json:"enabled"`
}
type RestrictPublicIPsSpecification struct {
	PolicySpecification[RestrictPublicIPsValues]
}

func (r *RestrictPublicIPsSpecification) IsEnabled() bool {
	return r.Values.Enabled
}

// Restrict Public Links
type RestrictPublicLinksConfig struct {
	Enabled Property `yaml:"enabled" json:"enabled"`
}
type RestrictPublicLinksSchema = PolicySchema[RestrictPublicLinksConfig]

type RestrictPublicLinksValues struct {
	Enabled bool `yaml:"enabled" json:"enabled"`
}
type RestrictPublicLinksSpecification struct {
	PolicySpecification[RestrictPublicLinksValues]
}

func (r *RestrictPublicLinksSpecification) IsEnabled() bool {
	return r.Values.Enabled
}

// Restrict Source IPs
type RestrictSourceIpRangeConfig struct {
	Enabled        Property `yaml:"enabled" json:"enabled"`
	AllowedSubnets Property `yaml:"allowedSubnets" json:"allowedSubnets"`
}
type RestrictSourceIpRangeSchema = PolicySchema[RestrictSourceIpRangeConfig]

type RestrictSourceIPRangeValues struct {
	Enabled        bool   `yaml:"enabled" json:"enabled"`
	AllowedSubnets string `yaml:"allowedSubnets" json:"allowedSubnets"`
}
type RestrictSourceIPRangeSpecification struct {
	PolicySpecification[RestrictSourceIPRangeValues]
}

func (r *RestrictSourceIPRangeSpecification) IsEnabled() bool {
	return r.Values.Enabled
}

// Decoders

func decodePolicySchema[T any](data []byte) (Schema, error) {
	var schema PolicySchema[T]

	if err := yaml.Unmarshal(data, &schema); err != nil {
		return nil, err
	}

	return &schema, nil
}

func schemaDecoder[T any](data []byte) (Schema, error) {
	return decodePolicySchema[T](data)
}

var SchemaDecoders = map[PolicyName]func([]byte) (Schema, error){
	RestrictApplications:           schemaDecoder[RestrictApplicationsConfig],
	RestrictCutAndPaste:            schemaDecoder[RestrictCutAndPasteConfig],
	RestrictDownloads:              schemaDecoder[RestrictDownloadsConfig],
	RestrictIntegratedApplications: schemaDecoder[RestrictIntegratedApplicationsConfig],
	RestrictInternetAccess:         schemaDecoder[RestrictInternetAccessConfig],
	RestrictOrganizationMembers:    schemaDecoder[RestrictOrganizationMembersConfig],
	RestrictProviderFileTransfers:  schemaDecoder[RestrictProviderFileTransfersConfig],
	RestrictPublicIPs:              schemaDecoder[RestrictPublicIPsConfig],
	RestrictPublicLinks:            schemaDecoder[RestrictPublicLinksConfig],
	RestrictSourceIPRange:          schemaDecoder[RestrictSourceIpRangeConfig],
}

func decodeRestrictApplications(data []byte, project rpc.ProjectId) (Specification, error) {
	var values RestrictApplicationsValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &RestrictApplicationsSpecification{
		PolicySpecification: PolicySpecification[RestrictApplicationsValues]{
			Schema:  RestrictApplications,
			Project: project,
			Values:  values,
		},
	}, nil
}

func decodeRestrictCutAndPaste(data []byte, project rpc.ProjectId) (Specification, error) {
	var values RestrictCutAndPasteValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &RestrictCutAndPasteSpecification{
		PolicySpecification: PolicySpecification[RestrictCutAndPasteValues]{
			Schema:  RestrictCutAndPaste,
			Project: project,
			Values:  values,
		},
	}, nil
}

func decodeRestrictDownloads(data []byte, project rpc.ProjectId) (Specification, error) {
	var values RestrictDownloadsValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &RestrictDownloadsSpecification{
		PolicySpecification: PolicySpecification[RestrictDownloadsValues]{
			Schema:  RestrictDownloads,
			Project: project,
			Values:  values,
		},
	}, nil
}

func decodeRestrictIntegratedApplications(data []byte, project rpc.ProjectId) (Specification, error) {
	var values RestrictIntegratedApplicationsValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &RestrictIntegratedApplicationsSpecification{
		PolicySpecification: PolicySpecification[RestrictIntegratedApplicationsValues]{
			Schema:  RestrictIntegratedApplications,
			Project: project,
			Values:  values,
		},
	}, nil
}

func decodeRestrictInternetAccess(data []byte, project rpc.ProjectId) (Specification, error) {
	var values RestrictInternetAccessValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &RestrictInternetAccessSpecification{
		PolicySpecification: PolicySpecification[RestrictInternetAccessValues]{
			Schema:  RestrictInternetAccess,
			Project: project,
			Values:  values,
		},
	}, nil
}

func decodeRestrictOrganizationMembers(data []byte, project rpc.ProjectId) (Specification, error) {
	var values RestrictOrganizationMembersValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &RestrictOrganizationMembersSpecification{
		PolicySpecification: PolicySpecification[RestrictOrganizationMembersValues]{
			Schema:  RestrictOrganizationMembers,
			Project: project,
			Values:  values,
		},
	}, nil
}

func decodeRestrictProviderTransfers(data []byte, project rpc.ProjectId) (Specification, error) {
	var values RestrictProviderFileTransfersValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &RestrictProviderFileTransfersSpecification{
		PolicySpecification: PolicySpecification[RestrictProviderFileTransfersValues]{
			Schema:  RestrictProviderFileTransfers,
			Project: project,
			Values:  values,
		},
	}, nil
}

func decodeRestrictPublicIPs(data []byte, project rpc.ProjectId) (Specification, error) {
	var values RestrictPublicIPsValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &RestrictPublicIPsSpecification{
		PolicySpecification: PolicySpecification[RestrictPublicIPsValues]{
			Schema:  RestrictPublicIPs,
			Project: project,
			Values:  values,
		},
	}, nil
}

func decodeRestrictPublicLinks(data []byte, project rpc.ProjectId) (Specification, error) {
	var values RestrictPublicLinksValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &RestrictPublicLinksSpecification{
		PolicySpecification: PolicySpecification[RestrictPublicLinksValues]{
			Schema:  RestrictPublicLinks,
			Project: project,
			Values:  values,
		},
	}, nil
}

func decodeRestrictSourceIPRange(data []byte, project rpc.ProjectId) (Specification, error) {
	var values RestrictSourceIPRangeValues

	if err := json.Unmarshal(data, &values); err != nil {
		return nil, err
	}

	return &RestrictSourceIPRangeSpecification{
		PolicySpecification: PolicySpecification[RestrictSourceIPRangeValues]{
			Schema:  RestrictSourceIPRange,
			Project: project,
			Values:  values,
		},
	}, nil
}

var SpecificationDecoders = map[PolicyName]func([]byte, rpc.ProjectId) (Specification, error){
	RestrictApplications:           decodeRestrictApplications,
	RestrictCutAndPaste:            decodeRestrictCutAndPaste,
	RestrictDownloads:              decodeRestrictDownloads,
	RestrictIntegratedApplications: decodeRestrictIntegratedApplications,
	RestrictInternetAccess:         decodeRestrictInternetAccess,
	RestrictOrganizationMembers:    decodeRestrictOrganizationMembers,
	RestrictProviderFileTransfers:  decodeRestrictProviderTransfers,
	RestrictPublicIPs:              decodeRestrictPublicIPs,
	RestrictPublicLinks:            decodeRestrictPublicLinks,
	RestrictSourceIPRange:          decodeRestrictSourceIPRange,
}

// API
// =====================================================================================================================

const policiesBaseContext = "projects/v2/policies"

type RetrievePoliciesRequest struct {
	ProjectId string `yaml:"projectId" json:"projectId"`
}

var PoliciesRetrieve = rpc.Call[RetrievePoliciesRequest, map[PolicyName]Policy]{
	BaseContext: policiesBaseContext,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesAuthenticated,
}

type PoliciesUpdateRequest struct {
	UpdatedPolicies map[PolicyName]Specification `yaml:"updatedPolicies" json:"updatedPolicies"`
}

var PoliciesUpdate = rpc.Call[PoliciesUpdateRequest, util.Empty]{
	BaseContext: policiesBaseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
}
