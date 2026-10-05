package orchestrators

import (
	apm "ucloud.dk/shared/pkg/accounting"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/rpc"
	"ucloud.dk/shared/pkg/util"
)

type Service struct {
	Resource
	Specification ServiceSpecification `json:"specification"`
	Status        ServiceStatus        `json:"status"`
	Updates       []ServiceUpdate      `json:"updates"`
}

type ServiceSupport struct {
	Product apm.ProductReference `json:"product"`
}

type ServiceSpecification struct {
	Name             string                                   `json:"name"`
	Ports            []ServicePort                            `json:"ports"`
	InternalEndpoint util.Option[ServiceInternalEndpointSpec] `json:"internalEndpoint"`
	ResourceSpecification
}

type ServicePort struct {
	Name                string                          `json:"name"`
	Port                int                             `json:"port"`
	Protocol            ServicePortProtocol             `json:"protocol"`
	ApplicationProtocol util.Option[string]             `json:"applicationProtocol"`
	BackendTls          util.Option[ServiceBackendTls]  `json:"backendTls"`
	HealthCheck         util.Option[ServiceHealthCheck] `json:"healthCheck"`
	DrainTimeoutSeconds util.Option[int]                `json:"drainTimeoutSeconds"`
}

type ServicePortProtocol string

const (
	ServicePortProtocolTcp ServicePortProtocol = "TCP"
	ServicePortProtocolUdp ServicePortProtocol = "UDP"
)

type ServiceBackendTls struct {
	ServerName         string              `json:"serverName"`
	TrustBundlePem     util.Option[string] `json:"trustBundlePem"`
	InsecureSkipVerify bool                `json:"insecureSkipVerify"`
}

type ServiceHealthCheck struct {
	Type               ServiceHealthCheckType `json:"type"`
	Path               string                 `json:"path"`
	Port               util.Option[int]       `json:"port"`
	IntervalSeconds    int                    `json:"intervalSeconds"`
	TimeoutSeconds     int                    `json:"timeoutSeconds"`
	HealthyThreshold   int                    `json:"healthyThreshold"`
	UnhealthyThreshold int                    `json:"unhealthyThreshold"`
}

type ServiceHealthCheckType string

const (
	ServiceHealthCheckTypeTcp   ServiceHealthCheckType = "TCP"
	ServiceHealthCheckTypeHttp  ServiceHealthCheckType = "HTTP"
	ServiceHealthCheckTypeHttps ServiceHealthCheckType = "HTTPS"
)

type ServiceInternalEndpointSpec struct {
	PrivateNetworkId string `json:"privateNetworkId"`
}

type ServiceInternalEndpoint struct {
	PrivateNetworkId string `json:"privateNetworkId"`
	DnsName          string `json:"dnsName"`
}

type ServiceUpdate struct {
	ProvisioningState util.Option[string]                 `json:"provisioningState"`
	Message           util.Option[string]                 `json:"message"`
	InternalDnsName   util.Option[string]                 `json:"internalDnsName"`
	Backends          util.Option[[]ServiceBackendStatus] `json:"backends"`
	Timestamp         fnd.Timestamp                       `json:"timestamp"`
}

type ServiceBackendStatus struct {
	JobId string                     `json:"jobId"`
	Rank  int                        `json:"rank"`
	Ports []ServiceBackendPortStatus `json:"ports"`
}

type ServiceBackendPortStatus struct {
	Name    string              `json:"name"`
	State   string              `json:"state"`
	Message util.Option[string] `json:"message"`
}

const (
	ServiceBackendPortStateHealthy   = "HEALTHY"
	ServiceBackendPortStateUnhealthy = "UNHEALTHY"
	ServiceBackendPortStateDraining  = "DRAINING"
)

type ServiceState string

const (
	ServiceStatePreparing   ServiceState = "PREPARING"
	ServiceStateReady       ServiceState = "READY"
	ServiceStateUnavailable ServiceState = "UNAVAILABLE"
)

type ServiceFlags struct {
	ResourceFlags
}

type PublicLinkServiceTarget struct {
	ServiceId string `json:"serviceId"`
	Port      string `json:"port"`
}

type ServiceStatus struct {
	ProvisioningState string                               `json:"provisioningState"`
	Message           util.Option[string]                  `json:"message"`
	InternalEndpoint  util.Option[ServiceInternalEndpoint] `json:"internalEndpoint"`
	Members           []string                             `json:"members"`
	Backends          []ServiceBackendStatus               `json:"backends"`
	ResourceStatus[ServiceSupport]
}

// Service API
// =====================================================================================================================

const serviceNamespace = "services"

var ServicesCreate = rpc.Call[fnd.BulkRequest[ServiceSpecification], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: serviceNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesEndUser,
}

var ServicesDelete = rpc.Call[fnd.BulkRequest[fnd.FindByStringId], fnd.BulkResponse[util.Empty]]{
	BaseContext: serviceNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesEndUser,
}

var ServicesRetrieve = rpc.Call[ServicesRetrieveRequest, Service]{
	BaseContext: serviceNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
}

type ServicesRetrieveRequest struct {
	Id string `json:"id"`
	ServiceFlags
}

var ServicesBrowse = rpc.Call[ServicesBrowseRequest, fnd.PageV2[Service]]{
	BaseContext: serviceNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesEndUser,
}

type ServicesBrowseRequest struct {
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	ServiceFlags
}

type ServicesUpdateRequest struct {
	Name  util.Option[string]        `json:"name"`
	Ports util.Option[[]ServicePort] `json:"ports"`
}

var ServicesUpdate = rpc.Call[fnd.BulkRequest[ResourceUpdateAndId[ServicesUpdateRequest]], util.Empty]{
	BaseContext: serviceNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "update",
}

type ServicesMembersRequest struct {
	Id     string   `json:"id"`
	JobIds []string `json:"jobIds"`
}

var ServicesAddMembers = rpc.Call[fnd.BulkRequest[ServicesMembersRequest], util.Empty]{
	BaseContext: serviceNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "addMembers",
}

var ServicesRemoveMembers = rpc.Call[fnd.BulkRequest[ServicesMembersRequest], util.Empty]{
	BaseContext: serviceNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "removeMembers",
}

var ServicesUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAcl], fnd.BulkResponse[util.Empty]]{
	BaseContext: serviceNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateAcl",
}

type ServicesUpdateLabelsRequest struct {
	Id     string            `json:"id"`
	Labels map[string]string `json:"labels"`
}

var ServicesUpdateLabels = rpc.Call[fnd.BulkRequest[ServicesUpdateLabelsRequest], util.Empty]{
	BaseContext: serviceNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesEndUser,
	Operation:   "updateLabels",
}

var ServicesRetrieveProducts = rpc.Call[util.Empty, SupportByProvider[ServiceSupport]]{
	BaseContext: serviceNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesEndUser,
	Operation:   "products",
}

// Service Control API
// =====================================================================================================================

const serviceControlNamespace = "services/control"

type ServicesControlRetrieveRequest struct {
	JobId string `json:"jobId,omitempty"`
	Id    string `json:"id"`
	ServiceFlags
}

var ServicesControlRetrieve = rpc.Call[ServicesControlRetrieveRequest, Service]{
	BaseContext: serviceControlNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesProvider,
}

type ServicesControlBrowseRequest struct {
	JobId        string              `json:"jobId,omitempty"`
	ItemsPerPage int                 `json:"itemsPerPage"`
	Next         util.Option[string] `json:"next"`

	ServiceFlags
}

var ServicesControlBrowse = rpc.Call[ServicesControlBrowseRequest, fnd.PageV2[Service]]{
	BaseContext: serviceControlNamespace,
	Convention:  rpc.ConventionBrowse,
	Roles:       rpc.RolesProvider,
}

var ServicesControlAddUpdate = rpc.Call[fnd.BulkRequest[ResourceUpdateAndId[ServiceUpdate]], util.Empty]{
	BaseContext: serviceControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "update",
}

var ServicesControlUpdateLabels = rpc.Call[ControlMutateRequest[ServicesUpdateLabelsRequest], util.Empty]{
	BaseContext: serviceControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "updateLabels",
}

var ServicesControlCreate = ControlCreateCall[ServiceSpecification, fnd.BulkResponse[fnd.FindByStringId]](serviceControlNamespace)

type ServicesControlUpdateMembersRequest struct {
	JobId         string   `json:"jobId"`
	Id            string   `json:"id"`
	AddedJobIds   []string `json:"addedJobIds"`
	RemovedJobIds []string `json:"removedJobIds"`
}

var ServicesControlUpdateMembers = rpc.Call[ServicesControlUpdateMembersRequest, util.Empty]{
	BaseContext: serviceControlNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesProvider,
	Operation:   "updateMembers",
}

var ServicesControlDelete = ControlMutateCall[fnd.FindByStringId, fnd.BulkResponse[util.Empty]](serviceControlNamespace, "delete")
var ServicesControlUpdateSpec = ControlMutateCall[ResourceUpdateAndId[ServicesUpdateRequest], util.Empty](serviceControlNamespace, "updateSpec")

// Service Provider API
// =====================================================================================================================

const serviceProviderNamespace = "ucloud/" + rpc.ProviderPlaceholder + "/services"

var ServicesProviderCreate = rpc.Call[fnd.BulkRequest[Service], fnd.BulkResponse[fnd.FindByStringId]]{
	BaseContext: serviceProviderNamespace,
	Convention:  rpc.ConventionCreate,
	Roles:       rpc.RolesPrivileged,
}

var ServicesProviderDelete = rpc.Call[fnd.BulkRequest[Service], fnd.BulkResponse[util.Empty]]{
	BaseContext: serviceProviderNamespace,
	Convention:  rpc.ConventionDelete,
	Roles:       rpc.RolesPrivileged,
}

var ServicesProviderRetrieveProducts = rpc.Call[util.Empty, fnd.BulkResponse[ServiceSupport]]{
	BaseContext: serviceProviderNamespace,
	Convention:  rpc.ConventionRetrieve,
	Roles:       rpc.RolesPrivileged,
	Operation:   "products",
}

type ServicesProviderUpdateRequest struct {
	Service Service       `json:"service"`
	Name    string        `json:"name"`
	Ports   []ServicePort `json:"ports"`
}

var ServicesProviderUpdate = rpc.Call[fnd.BulkRequest[ServicesProviderUpdateRequest], fnd.BulkResponse[util.Empty]]{
	BaseContext: serviceProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "update",
}

type ServicesProviderUpdateMembersRequest struct {
	Service       Service  `json:"service"`
	AddedJobIds   []string `json:"addedJobIds"`
	RemovedJobIds []string `json:"removedJobIds"`
}

var ServicesProviderUpdateMembers = rpc.Call[fnd.BulkRequest[ServicesProviderUpdateMembersRequest], fnd.BulkResponse[util.Empty]]{
	BaseContext: serviceProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "updateMembers",
}

var ServicesProviderOnUpdatedLabels = rpc.Call[fnd.BulkRequest[Service], util.Empty]{
	BaseContext: serviceProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "onUpdatedLabels",
}

var ServicesProviderUpdateAcl = rpc.Call[fnd.BulkRequest[UpdatedAclWithResource[Service]], fnd.BulkResponse[util.Empty]]{
	BaseContext: serviceProviderNamespace,
	Convention:  rpc.ConventionUpdate,
	Roles:       rpc.RolesPrivileged,
	Operation:   "updateAcl",
}
