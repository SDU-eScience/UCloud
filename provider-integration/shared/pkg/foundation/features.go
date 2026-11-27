package foundation

import (
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const FeaturesContext = "features"

const (
	FeatureContainerRepositories = "container-repositories"
	FeatureApplicationFlavors    = "application-flavors"
	FeatureCustomApplications    = "custom-applications"
	FeatureUsageV2               = "usage-v2"
	FeatureAllocationsV2         = "allocations-v2"
	FeatureTerminalV2            = "terminal-v2"
)

type FeatureDefinition struct {
	Feature     string `json:"feature"`
	Title       string `json:"title"`
	Description string `json:"description"`
	Toggleable  bool   `json:"toggleable"`
}

var FeatureDefinitions = []FeatureDefinition{
	{
		Feature:     FeatureContainerRepositories,
		Title:       "Container repositories",
		Description: "Use container repositories to store, share and pull container images.",
	},
	{
		Feature:     FeatureApplicationFlavors,
		Title:       "Application flavors",
		Description: "Use additional application flavors when launching applications.",
	},
	{
		Feature:     FeatureCustomApplications,
		Title:       "Custom applications",
		Description: "Create and share custom applications.",
	},
	{
		Feature:     FeatureUsageV2,
		Title:       "New usage history",
		Description: "Use the new usage history page with expanded usage breakdowns and charts.",
		Toggleable:  true,
	},
	{
		Feature:     FeatureAllocationsV2,
		Title:       "New allocation pages",
		Description: "Use the new allocation pages, including the updated received and sub-allocated allocation views.",
		Toggleable:  true,
	},
	{
		Feature:     FeatureTerminalV2,
		Title:       "New terminal experience",
		Description: "Use the redesigned multi-tab, split-pane terminal workspace with persistent sessions powered by libghostty.",
		Toggleable:  true,
	},
}

var featureDefinitionsById = func() map[string]FeatureDefinition {
	result := make(map[string]FeatureDefinition, len(FeatureDefinitions))
	for _, definition := range FeatureDefinitions {
		result[definition.Feature] = definition
	}
	return result
}()

var FeatureOptions = func() []string {
	result := make([]string, 0, len(FeatureDefinitions))
	for _, definition := range FeatureDefinitions {
		result = append(result, definition.Feature)
	}
	return result
}()

func FeatureDefinitionById(feature string) (FeatureDefinition, bool) {
	definition, ok := featureDefinitionsById[feature]
	return definition, ok
}

func FeatureIsValid(feature string) bool {
	_, ok := featureDefinitionsById[feature]
	return ok
}

type FeatureGrant struct {
	Feature      string    `json:"feature"`
	ProjectId    string    `json:"projectId"`
	ProjectTitle string    `json:"projectTitle"`
	GrantedAt    Timestamp `json:"grantedAt"`
	GrantedBy    string    `json:"grantedBy"`
}

type FeatureGrantRequest struct {
	Feature   string `json:"feature"`
	ProjectId string `json:"projectId"`
}

type FeatureToggle struct {
	Feature     string `json:"feature"`
	Title       string `json:"title"`
	Description string `json:"description"`
	Enabled     bool   `json:"enabled"`
}

type FeatureToggleUpdateRequest struct {
	Feature string `json:"feature"`
	Enabled bool   `json:"enabled"`
}

type FeatureAdminEntry struct {
	Feature         string `json:"feature"`
	Title           string `json:"title"`
	Description     string `json:"description"`
	Toggleable      bool   `json:"toggleable"`
	GloballyEnabled bool   `json:"globallyEnabled"`
	ConfigEnabled   bool   `json:"configEnabled"`
}

type FeatureGlobalUpdateRequest struct {
	Feature string `json:"feature"`
	Enabled bool   `json:"enabled"`
}

var FeaturesRetrieveEnabled = rpc.Call[util.Empty, []string]{
	BaseContext: FeaturesContext,
	Convention:  rpc.ConventionRetrieve,
	Operation:   "enabled",
	Roles:       rpc.RolesEndUser,
}

var FeaturesRetrieveToggles = rpc.Call[util.Empty, []FeatureToggle]{
	BaseContext: FeaturesContext,
	Convention:  rpc.ConventionRetrieve,
	Operation:   "toggles",
	Roles:       rpc.RolesEndUser,
}

var FeaturesUpdateToggle = rpc.Call[FeatureToggleUpdateRequest, util.Empty]{
	BaseContext: FeaturesContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "toggleUser",
	Roles:       rpc.RolesEndUser,
}

var FeaturesBrowseGrants = rpc.Call[util.Empty, []FeatureGrant]{
	BaseContext: FeaturesContext,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesAdmin,
}

var FeaturesGrantToProject = rpc.Call[FeatureGrantRequest, util.Empty]{
	BaseContext: FeaturesContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "grant",
	Roles:       rpc.RolesAdmin,
}

var FeaturesRevokeFromProject = rpc.Call[FeatureGrantRequest, util.Empty]{
	BaseContext: FeaturesContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "revoke",
	Roles:       rpc.RolesAdmin,
}

var FeaturesBrowseAdmin = rpc.Call[util.Empty, []FeatureAdminEntry]{
	BaseContext: FeaturesContext,
	Convention:  rpc.ConventionBrowse,
	Operation:   "admin",
	Roles:       rpc.RolesAdmin,
}

var FeaturesUpdateGlobal = rpc.Call[FeatureGlobalUpdateRequest, util.Empty]{
	BaseContext: FeaturesContext,
	Convention:  rpc.ConventionUpdate,
	Operation:   "global",
	Roles:       rpc.RolesAdmin,
}
