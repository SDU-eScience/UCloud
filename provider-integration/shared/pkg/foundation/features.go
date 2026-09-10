package foundation

import (
	"slices"

	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

const FeaturesContext = "features"

const (
	FeatureContainerRepositories = "container-repositories"
)

var FeatureOptions = []string{
	FeatureContainerRepositories,
}

func FeatureIsValid(feature string) bool {
	return slices.Contains(FeatureOptions, feature)
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

var FeaturesRetrieveEnabled = rpc.Call[util.Empty, []string]{
	BaseContext: FeaturesContext,
	Convention:  rpc.ConventionRetrieve,
	Operation:   "enabled",
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
