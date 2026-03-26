package job_introspection

import (
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/rpc"
)

const baseContext = "internal/job-introspection"

type IntrospectAuthRequest struct {
	Token string `json:"token"`
}

type IntrospectJobResponse struct {
	Job       orcapi.Job `json:"job"`
	ServiceIp string     `json:"serviceIp"`
}

var IntrospectJob = rpc.Call[IntrospectAuthRequest, IntrospectJobResponse]{
	BaseContext: baseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "job",
}

type IntrospectNetworksResponse struct {
	Networks []IntrospectedNetwork `json:"networks"`
}

type IntrospectedNetwork struct {
	Id        string                      `json:"id"`
	Name      string                      `json:"name"`
	Subdomain string                      `json:"subdomain"`
	Members   []IntrospectedNetworkMember `json:"members"`
}

type IntrospectedNetworkMember struct {
	Id     string            `json:"id"`
	Name   string            `json:"name"`
	Fqdn   string            `json:"fqdn"`
	Labels map[string]string `json:"labels"`
}

var IntrospectNetworks = rpc.Call[IntrospectAuthRequest, IntrospectNetworksResponse]{
	BaseContext: baseContext,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPublic,
	Operation:   "networks",
}
