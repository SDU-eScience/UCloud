package ucxapi

import (
	fndapi "ucloud.dk/shared/pkg/foundation"
	orcapi "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/ucx"
	"ucloud.dk/shared/pkg/util"
)

type Message struct {
	Message string
}

var Frontend = ucx.Rpc[Message, Message]{CallName: "frontend"}
var Core = ucx.Rpc[Message, Message]{CallName: "core"}
var IM = ucx.Rpc[Message, Message]{CallName: "im"}

// Core
// =====================================================================================================================

// Stacks
// ---------------------------------------------------------------------------------------------------------------------

var StackAvailable = ucx.Rpc[fndapi.FindByStringId, bool]{CallName: "stackAvailable"}

// Private networks
// ---------------------------------------------------------------------------------------------------------------------

var PrivateNetworksCreate = ucx.Rpc[[]orcapi.PrivateNetworkSpecification, []orcapi.PrivateNetwork]{CallName: "privateNetworksCreate"}
var PrivateNetworksDelete = ucx.Rpc[[]string, util.Empty]{CallName: "privateNetworksDelete"}
var PrivateNetworksBrowse = ucx.Rpc[orcapi.PrivateNetworksBrowseRequest, fndapi.PageV2[orcapi.PrivateNetwork]]{CallName: "privateNetworksBrowse"}
var PrivateNetworksRetrieve = ucx.Rpc[orcapi.PrivateNetworksRetrieveRequest, orcapi.PrivateNetwork]{CallName: "privateNetworksRetrieve"}
var PrivateNetworksUpdateLabels = ucx.Rpc[fndapi.BulkRequest[orcapi.PrivateNetworksUpdateLabelsRequest], util.Empty]{CallName: "privateNetworksUpdateLabels"}
var PrivateNetworksRetrieveProducts = ucx.Rpc[util.Empty, []orcapi.ResolvedSupport[orcapi.PrivateNetworkSupport]]{CallName: "privateNetworksRetrieveProducts"}

var PrivateNetworkIpsCreate = ucx.Rpc[[]orcapi.PrivateNetworkIpSpecification, []orcapi.PrivateNetworkIp]{CallName: "privateNetworkIpsCreate"}
var PrivateNetworkIpsDelete = ucx.Rpc[[]string, util.Empty]{CallName: "privateNetworkIpsDelete"}
var PrivateNetworkIpsBrowse = ucx.Rpc[orcapi.PrivateNetworkIpsBrowseRequest, fndapi.PageV2[orcapi.PrivateNetworkIp]]{CallName: "privateNetworkIpsBrowse"}
var PrivateNetworkIpsRetrieve = ucx.Rpc[orcapi.PrivateNetworkIpsRetrieveRequest, orcapi.PrivateNetworkIp]{CallName: "privateNetworkIpsRetrieve"}
var PrivateNetworkIpsUpdateLabels = ucx.Rpc[fndapi.BulkRequest[orcapi.PrivateNetworkIpsUpdateLabelsRequest], util.Empty]{CallName: "privateNetworkIpsUpdateLabels"}
var PrivateNetworkIpsRetrieveProducts = ucx.Rpc[util.Empty, []orcapi.ResolvedSupport[orcapi.PrivateNetworkIpSupport]]{CallName: "privateNetworkIpsRetrieveProducts"}

// Public IPs
// ---------------------------------------------------------------------------------------------------------------------

var PublicIpsCreate = ucx.Rpc[[]orcapi.PublicIPSpecification, []orcapi.PublicIp]{CallName: "publicIpsCreate"}
var PublicIpsDelete = ucx.Rpc[[]string, util.Empty]{CallName: "publicIpsDelete"}
var PublicIpsBrowse = ucx.Rpc[orcapi.PublicIpsBrowseRequest, fndapi.PageV2[orcapi.PublicIp]]{CallName: "publicIpsBrowse"}
var PublicIpsRetrieve = ucx.Rpc[orcapi.PublicIpsRetrieveRequest, orcapi.PublicIp]{CallName: "publicIpsRetrieve"}
var PublicIpsUpdateLabels = ucx.Rpc[fndapi.BulkRequest[orcapi.PublicIpsUpdateLabelsRequest], util.Empty]{CallName: "publicIpsUpdateLabels"}
var PublicIpsUpdateFirewall = ucx.Rpc[fndapi.BulkRequest[orcapi.PublicIpUpdateFirewallRequest], util.Empty]{CallName: "publicIpsUpdateFirewall"}
var PublicIpsRetrieveProducts = ucx.Rpc[util.Empty, []orcapi.ResolvedSupport[orcapi.PublicIpSupport]]{CallName: "publicIpsRetrieveProducts"}

// Public links
// ---------------------------------------------------------------------------------------------------------------------

var PublicLinksCreate = ucx.Rpc[[]orcapi.IngressSpecification, []orcapi.Ingress]{CallName: "publicLinksCreate"}
var PublicLinksDelete = ucx.Rpc[[]string, util.Empty]{CallName: "publicLinksDelete"}
var PublicLinksBrowse = ucx.Rpc[orcapi.IngressesBrowseRequest, fndapi.PageV2[orcapi.Ingress]]{CallName: "publicLinksBrowse"}
var PublicLinksRetrieve = ucx.Rpc[orcapi.IngressesRetrieveRequest, orcapi.Ingress]{CallName: "publicLinksRetrieve"}
var PublicLinksUpdateLabels = ucx.Rpc[fndapi.BulkRequest[orcapi.IngressesUpdateLabelsRequest], util.Empty]{CallName: "publicLinksUpdateLabels"}
var PublicLinksRetrieveProducts = ucx.Rpc[util.Empty, []orcapi.ResolvedSupport[orcapi.IngressSupport]]{CallName: "publicLinksRetrieveProducts"}
var PublicLinksSetTarget = ucx.Rpc[fndapi.BulkRequest[orcapi.IngressesSetTargetRequest], util.Empty]{CallName: "publicLinksSetTarget"}

// Services
// ---------------------------------------------------------------------------------------------------------------------

var ServicesCreate = ucx.Rpc[[]orcapi.ServiceSpecification, []orcapi.Service]{CallName: "servicesCreate"}
var ServicesDelete = ucx.Rpc[[]string, util.Empty]{CallName: "servicesDelete"}
var ServicesBrowse = ucx.Rpc[orcapi.ServicesBrowseRequest, fndapi.PageV2[orcapi.Service]]{CallName: "servicesBrowse"}
var ServicesRetrieve = ucx.Rpc[orcapi.ServicesRetrieveRequest, orcapi.Service]{CallName: "servicesRetrieve"}
var ServicesUpdate = ucx.Rpc[fndapi.BulkRequest[orcapi.ResourceUpdateAndId[orcapi.ServicesUpdateRequest]], util.Empty]{CallName: "servicesUpdate"}
var ServicesAddMembers = ucx.Rpc[fndapi.BulkRequest[orcapi.ServicesMembersRequest], util.Empty]{CallName: "servicesAddMembers"}
var ServicesRemoveMembers = ucx.Rpc[fndapi.BulkRequest[orcapi.ServicesMembersRequest], util.Empty]{CallName: "servicesRemoveMembers"}
var ServicesRetrieveProducts = ucx.Rpc[util.Empty, []orcapi.ResolvedSupport[orcapi.ServiceSupport]]{CallName: "servicesRetrieveProducts"}

// Licenses
// ---------------------------------------------------------------------------------------------------------------------

var LicensesCreate = ucx.Rpc[[]orcapi.LicenseSpecification, []orcapi.License]{CallName: "licensesCreate"}
var LicensesDelete = ucx.Rpc[[]string, util.Empty]{CallName: "licensesDelete"}
var LicensesBrowse = ucx.Rpc[orcapi.LicensesBrowseRequest, fndapi.PageV2[orcapi.License]]{CallName: "licensesBrowse"}
var LicensesRetrieve = ucx.Rpc[orcapi.LicensesRetrieveRequest, orcapi.License]{CallName: "licensesRetrieve"}
var LicensesUpdateLabels = ucx.Rpc[fndapi.BulkRequest[orcapi.LicensesUpdateLabelsRequest], util.Empty]{CallName: "licensesUpdateLabels"}
var LicensesRetrieveProducts = ucx.Rpc[util.Empty, []orcapi.ResolvedSupport[orcapi.LicenseSupport]]{CallName: "licensesRetrieveProducts"}

// Drives
// ---------------------------------------------------------------------------------------------------------------------

var DrivesCreate = ucx.Rpc[[]orcapi.DriveSpecification, []orcapi.Drive]{CallName: "drivesCreate"}
var DrivesDelete = ucx.Rpc[[]string, util.Empty]{CallName: "drivesDelete"}
var DrivesBrowse = ucx.Rpc[orcapi.DrivesBrowseRequest, fndapi.PageV2[orcapi.Drive]]{CallName: "drivesBrowse"}
var DrivesRetrieve = ucx.Rpc[orcapi.DrivesRetrieveRequest, orcapi.Drive]{CallName: "drivesRetrieve"}
var DrivesRename = ucx.Rpc[fndapi.BulkRequest[orcapi.DriveRenameRequest], util.Empty]{CallName: "drivesRename"}
var DrivesUpdateLabels = ucx.Rpc[fndapi.BulkRequest[orcapi.DrivesUpdateLabelsRequest], util.Empty]{CallName: "drivesUpdateLabels"}
var DrivesRetrieveProducts = ucx.Rpc[util.Empty, []orcapi.ResolvedSupport[orcapi.FSSupport]]{CallName: "drivesRetrieveProducts"}

// Container repositories
// ---------------------------------------------------------------------------------------------------------------------

var ContainerRepositoriesCreate = ucx.Rpc[[]orcapi.ContainerRepositorySpecification, []orcapi.ContainerRepository]{CallName: "containerRepositoriesCreate"}
var ContainerRepositoriesDelete = ucx.Rpc[[]string, util.Empty]{CallName: "containerRepositoriesDelete"}
var ContainerRepositoriesBrowse = ucx.Rpc[orcapi.ContainerRepositoriesBrowseRequest, fndapi.PageV2[orcapi.ContainerRepository]]{CallName: "containerRepositoriesBrowse"}
var ContainerRepositoriesRetrieve = ucx.Rpc[orcapi.ContainerRepositoriesRetrieveRequest, orcapi.ContainerRepository]{CallName: "containerRepositoriesRetrieve"}
var ContainerRepositoriesUpdateLabels = ucx.Rpc[fndapi.BulkRequest[orcapi.ContainerRepositoriesUpdateLabelsRequest], util.Empty]{CallName: "containerRepositoriesUpdateLabels"}
var ContainerRepositoriesRetrieveProducts = ucx.Rpc[util.Empty, []orcapi.ResolvedSupport[orcapi.FSSupport]]{CallName: "containerRepositoriesRetrieveProducts"}

// Jobs
// ---------------------------------------------------------------------------------------------------------------------

var JobsCreate = ucx.Rpc[[]orcapi.JobSpecification, []orcapi.Job]{CallName: "jobsCreate"}
var JobsBrowse = ucx.Rpc[orcapi.JobsBrowseRequest, fndapi.PageV2[orcapi.Job]]{CallName: "jobsBrowse"}
var JobsRetrieve = ucx.Rpc[orcapi.JobsRetrieveRequest, orcapi.Job]{CallName: "jobsRetrieve"}
var JobsRename = ucx.Rpc[fndapi.BulkRequest[orcapi.JobRenameRequest], util.Empty]{CallName: "jobsRename"}
var JobsTerminate = ucx.Rpc[fndapi.BulkRequest[fndapi.FindByStringId], fndapi.BulkResponse[util.Empty]]{CallName: "jobsTerminate"}
var JobsExtend = ucx.Rpc[fndapi.BulkRequest[orcapi.JobsExtendRequestItem], fndapi.BulkResponse[util.Empty]]{CallName: "jobsExtend"}
var JobsSuspend = ucx.Rpc[fndapi.BulkRequest[fndapi.FindByStringId], fndapi.BulkResponse[util.Empty]]{CallName: "jobsSuspend"}
var JobsUnsuspend = ucx.Rpc[fndapi.BulkRequest[fndapi.FindByStringId], fndapi.BulkResponse[util.Empty]]{CallName: "jobsUnsuspend"}
var JobsRetrieveProducts = ucx.Rpc[util.Empty, []orcapi.ResolvedSupport[orcapi.JobSupport]]{CallName: "jobsRetrieveProducts"}

// Provider
// =====================================================================================================================

type StackCreateRequest struct {
	StackType string
	StackId   string
}

type Stack struct {
	InstanceId string
	Labels     map[string]string
	Mount      orcapi.AppParameterValue
}

var StackCreate = ucx.Rpc[StackCreateRequest, Stack]{CallName: "stackCreate"}

type StackDataWriteRequest struct {
	InstanceId string `json:"instanceId"`
	Path       string `json:"path"`
	Data       string `json:"data"`
	Perm       uint32 `json:"perm"`
	Atomic     bool   `json:"atomic,omitempty"`
}

var StackDataWrite = ucx.Rpc[StackDataWriteRequest, util.Empty]{CallName: "stackDataWrite"}

type StackDataAppendRequest struct {
	InstanceId string
	Path       string
	Data       []byte
	Perm       uint32
}

var StackDataAppend = ucx.Rpc[StackDataAppendRequest, util.Empty]{CallName: "stackDataAppend"}

var StackConfirm = ucx.Rpc[fndapi.FindByStringId, util.Empty]{CallName: "stackConfirm"}

var StackHeartbeat = ucx.Rpc[fndapi.FindByStringId, util.Empty]{CallName: "stackHeartbeat"}

// Frontend
// =====================================================================================================================

var StackOpen = ucx.Rpc[fndapi.FindByStringId, util.Empty]{CallName: "stackOpen"}
var StackRefresh = ucx.Rpc[util.Empty, util.Empty]{CallName: "stackRefresh"}

type StackInfoResponse struct {
	Id            string
	Type          string
	Provider      string
	CreatedAt     int64
	ResourceCount int
}

var StackInfo = ucx.Rpc[util.Empty, StackInfoResponse]{CallName: "stackInfo"}
var StackDelete = ucx.Rpc[util.Empty, util.Empty]{CallName: "stackDelete"}
var StackShowResources = ucx.Rpc[util.Empty, util.Empty]{CallName: "stackShowResources"}

type UiSendMessageRequest struct {
	Message string
	Success bool
}

var UiSendMessage = ucx.Rpc[UiSendMessageRequest, util.Empty]{CallName: "uiSendMessage"}

type RouterPushPageRequest struct {
	Path string
}

var RouterPushPage = ucx.Rpc[RouterPushPageRequest, util.Empty]{CallName: "routerPushPage"}

type OpenUrlRequest struct {
	Path string
}

var OpenUrl = ucx.Rpc[OpenUrlRequest, util.Empty]{CallName: "openUrl"}

type TerminalOpenShellToJobRequest struct {
	JobId string
	Rank  int
}

var TerminalOpenShellToJob = ucx.Rpc[TerminalOpenShellToJobRequest, util.Empty]{CallName: "terminalOpenShellToJob"}

type StackDownloadFileRequest struct {
	FileName string
}

var StackCopyFile = ucx.Rpc[StackDownloadFileRequest, util.Empty]{CallName: "stackCopyFile"}
var StackDownloadFile = ucx.Rpc[StackDownloadFileRequest, util.Empty]{CallName: "stackDownloadFile"}
