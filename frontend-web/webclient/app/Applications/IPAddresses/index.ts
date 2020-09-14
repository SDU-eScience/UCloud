import {buildQueryString} from "Utilities/URIUtilities";

export interface ApplyForAddressRequest {
    application: string,
}
export interface ApplyForAddressResponse {
    id: number,
}
export function applyForAddress(
    request: ApplyForAddressRequest
): APICallParameters<ApplyForAddressRequest> {
    return {
        method: "POST",
        path: "/hpc/ip/apply",
        parameters: request,
        reloadId: Math.random(),
        payload: request
    };
}
export interface ApproveAddressRequest {
    id: number,
}
export function approveAddress(
    request: ApproveAddressRequest
): APICallParameters<ApproveAddressRequest> {
    return {
        method: "POST",
        path: "/hpc/ip/approve",
        parameters: request,
        reloadId: Math.random(),
        payload: request
    };
}
export interface RejectAddressRequest {
    id: number,
}
export function rejectAddress(
    request: RejectAddressRequest
): APICallParameters<RejectAddressRequest> {
    return {
        method: "POST",
        path: "/hpc/ip/reject",
        parameters: request,
        reloadId: Math.random(),
        payload: request
    };
}
export interface ReleaseAddressRequest {
    id: number,
}
export function releaseAddress(
    request: ReleaseAddressRequest
): APICallParameters<ReleaseAddressRequest> {
    return {
        method: "POST",
        path: "/hpc/ip/release",
        parameters: request,
        reloadId: Math.random(),
        payload: request
    };
}
export interface UpdatePortsRequest {
    id: number,
    newPortList: PortAndProtocol[],
}
export interface PortAndProtocol {
    port: number,
    protocol: InternetProtocol,
}
export enum InternetProtocol {
    TCP = "TCP",
    UDP = "UDP",
}
export function updatePorts(
    request: UpdatePortsRequest
): APICallParameters<UpdatePortsRequest> {
    return {
        method: "POST",
        path: "/hpc/ip/update-ports",
        parameters: request,
        reloadId: Math.random(),
        payload: request
    };
}
export interface AddToPoolRequest {
    addresses: string[],
    exceptions: string[],
}
export function addToPool(
    request: AddToPoolRequest
): APICallParameters<AddToPoolRequest> {
    return {
        method: "POST",
        path: "/hpc/ip/add-to-pool",
        parameters: request,
        reloadId: Math.random(),
        payload: request
    };
}
export interface RemoveFromPoolRequest {
    addresses: string[],
    exceptions: string[],
}
export function removeFromPool(
    request: RemoveFromPoolRequest
): APICallParameters<RemoveFromPoolRequest> {
    return {
        method: "POST",
        path: "/hpc/ip/remove-from-pool",
        parameters: request,
        reloadId: Math.random(),
        payload: request
    };
}
export interface ListAssignedAddressesRequest {
    itemsPerPage?: number,
    page?: number,
}
export interface PublicIP {
    entityType: WalletOwnerType,
    id: number,
    ipAddress: string,
    openPorts: PortAndProtocol[],
    ownerEntity: string,
    inUseBy?: string,
}
export enum WalletOwnerType {
    USER = "USER",
    PROJECT = "PROJECT",
}
export interface PortAndProtocol {
    port: number,
    protocol: InternetProtocol,
}
export function listAssignedAddresses(
    request: ListAssignedAddressesRequest
): APICallParameters<ListAssignedAddressesRequest> {
    return {
        method: "GET",
        path: buildQueryString("/hpc/ip/list-assigned", request),
        parameters: request,
        reloadId: Math.random(),
        payload: undefined
    };
}
export interface ListMyAddressesRequest {
    itemsPerPage?: number,
    page?: number,
}
export interface PublicIP {
    entityType: WalletOwnerType,
    id: number,
    ipAddress: string,
    openPorts: PortAndProtocol[],
    ownerEntity: string,
}
export function listMyAddresses(
    request: ListMyAddressesRequest
): APICallParameters<ListMyAddressesRequest> {
    return {
        method: "GET",
        path: buildQueryString("/hpc/ip/list", request),
        parameters: request,
        reloadId: Math.random(),
        payload: undefined
    };
}
export interface ListAddressApplicationsRequest {
    itemsPerPage?: number,
    page?: number,
}
export interface AddressApplication {
    application: string,
    id: number,
    createdAt: number,
}
export function listAddressApplications(
    request: ListAddressApplicationsRequest
): APICallParameters<ListAddressApplicationsRequest> {
    return {
        method: "GET",
        path: buildQueryString("/hpc/ip/list-applications", request),
        parameters: request,
        reloadId: Math.random(),
        payload: undefined
    };
}
export interface ListAddressApplicationsForApprovalRequest {
    itemsPerPage?: number,
    page?: number,
}
export function listAddressApplicationsForApproval(
    request: ListAddressApplicationsForApprovalRequest
): APICallParameters<ListAddressApplicationsForApprovalRequest> {
    return {
        method: "GET",
        path: buildQueryString("/hpc/ip/review-applications", request),
        parameters: request,
        reloadId: Math.random(),
        payload: undefined
    };
}
