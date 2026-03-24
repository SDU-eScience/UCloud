package ucxsvc

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

// Private networks
// ---------------------------------------------------------------------------------------------------------------------

var PrivateNetworksCreate = ucx.Rpc[fndapi.BulkRequest[orcapi.PrivateNetworkSpecification], fndapi.BulkResponse[orcapi.PrivateNetwork]]{CallName: "privateNetworksCreate"}
var PrivateNetworksDelete = ucx.Rpc[fndapi.BulkRequest[fndapi.FindByStringId], util.Empty]{CallName: "privateNetworksDelete"}
var PrivateNetworksBrowse = ucx.Rpc[orcapi.PrivateNetworksBrowseRequest, fndapi.PageV2[orcapi.PrivateNetwork]]{CallName: "privateNetworksBrowse"}
var PrivateNetworksRetrieve = ucx.Rpc[orcapi.PrivateNetworksRetrieveRequest, orcapi.PrivateNetwork]{CallName: "privateNetworksRetrieve"}
var PrivateNetworksUpdateLabels = ucx.Rpc[fndapi.BulkRequest[orcapi.PrivateNetworksUpdateLabelsRequest], util.Empty]{CallName: "privateNetworksUpdateLabels"}
var PrivateNetworksRetrieveProducts = ucx.Rpc[util.Empty, []orcapi.ResolvedSupport[orcapi.PrivateNetworkSupport]]{CallName: "privateNetworksRetrieveProducts"}

// Public IPs
// ---------------------------------------------------------------------------------------------------------------------

var PublicIpsCreate = ucx.Rpc[fndapi.BulkRequest[orcapi.PublicIPSpecification], fndapi.BulkResponse[fndapi.FindByStringId]]{CallName: "publicIpsCreate"}
var PublicIpsDelete = ucx.Rpc[fndapi.BulkRequest[fndapi.FindByStringId], fndapi.BulkResponse[util.Empty]]{CallName: "publicIpsDelete"}
var PublicIpsBrowse = ucx.Rpc[orcapi.PublicIpsBrowseRequest, fndapi.PageV2[orcapi.PublicIp]]{CallName: "publicIpsBrowse"}
var PublicIpsRetrieve = ucx.Rpc[orcapi.PublicIpsRetrieveRequest, orcapi.PublicIp]{CallName: "publicIpsRetrieve"}
var PublicIpsUpdateLabels = ucx.Rpc[fndapi.BulkRequest[orcapi.PublicIpsUpdateLabelsRequest], util.Empty]{CallName: "publicIpsUpdateLabels"}
var PublicIpsUpdateFirewall = ucx.Rpc[fndapi.BulkRequest[orcapi.PublicIpUpdateFirewallRequest], util.Empty]{CallName: "publicIpsUpdateFirewall"}
var PublicIpsRetrieveProducts = ucx.Rpc[util.Empty, []orcapi.ResolvedSupport[orcapi.PublicIpSupport]]{CallName: "publicIpsRetrieveProducts"}

// PublicLinks (public links)
// ---------------------------------------------------------------------------------------------------------------------

var PublicLinksCreate = ucx.Rpc[fndapi.BulkRequest[orcapi.IngressSpecification], fndapi.BulkResponse[fndapi.FindByStringId]]{CallName: "publicLinksCreate"}
var PublicLinksDelete = ucx.Rpc[fndapi.BulkRequest[fndapi.FindByStringId], fndapi.BulkResponse[util.Empty]]{CallName: "publicLinksDelete"}
var PublicLinksBrowse = ucx.Rpc[orcapi.IngressesBrowseRequest, fndapi.PageV2[orcapi.Ingress]]{CallName: "publicLinksBrowse"}
var PublicLinksRetrieve = ucx.Rpc[orcapi.IngressesRetrieveRequest, orcapi.Ingress]{CallName: "publicLinksRetrieve"}
var PublicLinksUpdateLabels = ucx.Rpc[fndapi.BulkRequest[orcapi.IngressesUpdateLabelsRequest], util.Empty]{CallName: "publicLinksUpdateLabels"}
var PublicLinksRetrieveProducts = ucx.Rpc[util.Empty, []orcapi.ResolvedSupport[orcapi.IngressSupport]]{CallName: "publicLinksRetrieveProducts"}

// Licenses
// ---------------------------------------------------------------------------------------------------------------------

var LicensesCreate = ucx.Rpc[fndapi.BulkRequest[orcapi.LicenseSpecification], fndapi.BulkResponse[fndapi.FindByStringId]]{CallName: "licensesCreate"}
var LicensesDelete = ucx.Rpc[fndapi.BulkRequest[fndapi.FindByStringId], fndapi.BulkResponse[util.Empty]]{CallName: "licensesDelete"}
var LicensesBrowse = ucx.Rpc[orcapi.LicensesBrowseRequest, fndapi.PageV2[orcapi.License]]{CallName: "licensesBrowse"}
var LicensesRetrieve = ucx.Rpc[orcapi.LicensesRetrieveRequest, orcapi.License]{CallName: "licensesRetrieve"}
var LicensesUpdateLabels = ucx.Rpc[fndapi.BulkRequest[orcapi.LicensesUpdateLabelsRequest], util.Empty]{CallName: "licensesUpdateLabels"}
var LicensesRetrieveProducts = ucx.Rpc[util.Empty, []orcapi.ResolvedSupport[orcapi.LicenseSupport]]{CallName: "licensesRetrieveProducts"}

// Drives
// ---------------------------------------------------------------------------------------------------------------------

var DrivesCreate = ucx.Rpc[fndapi.BulkRequest[orcapi.DriveSpecification], fndapi.BulkResponse[fndapi.FindByStringId]]{CallName: "drivesCreate"}
var DrivesDelete = ucx.Rpc[fndapi.BulkRequest[fndapi.FindByStringId], fndapi.BulkResponse[util.Empty]]{CallName: "drivesDelete"}
var DrivesBrowse = ucx.Rpc[orcapi.DrivesBrowseRequest, fndapi.PageV2[orcapi.Drive]]{CallName: "drivesBrowse"}
var DrivesRetrieve = ucx.Rpc[orcapi.DrivesRetrieveRequest, orcapi.Drive]{CallName: "drivesRetrieve"}
var DrivesRename = ucx.Rpc[fndapi.BulkRequest[orcapi.DriveRenameRequest], util.Empty]{CallName: "drivesRename"}
var DrivesUpdateLabels = ucx.Rpc[fndapi.BulkRequest[orcapi.DrivesUpdateLabelsRequest], util.Empty]{CallName: "drivesUpdateLabels"}
var DrivesRetrieveProducts = ucx.Rpc[util.Empty, []orcapi.ResolvedSupport[orcapi.FSSupport]]{CallName: "drivesRetrieveProducts"}

// Jobs
// ---------------------------------------------------------------------------------------------------------------------

var JobsCreate = ucx.Rpc[fndapi.BulkRequest[orcapi.JobSpecification], fndapi.BulkResponse[fndapi.FindByStringId]]{CallName: "jobsCreate"}
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
	StackName string
}

type StackCreateResponse struct {
	Id     string
	Labels map[string]string
	Mounts []orcapi.AppParameterValue
}

var StackCreate = ucx.Rpc[StackCreateRequest, StackCreateResponse]{CallName: "stackCreate"}

type StackDataWriteRequest struct {
	Path string
	Data string
	Mode int
}

var StackDataWrite = ucx.Rpc[StackDataWriteRequest, util.Empty]{CallName: "stackDataWrite"}

// Frontend
// =====================================================================================================================

var StackOpen = ucx.Rpc[fndapi.FindByStringId, util.Empty]{CallName: "stackOpen"}
