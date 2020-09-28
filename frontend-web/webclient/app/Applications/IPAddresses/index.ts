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
export interface OpenPortsRequest {
    id: number,
    portList: PortAndProtocol[],
}
export interface ClosePortsRequest {
    id: number,
    portList: PortAndProtocol[],
}
export interface PortAndProtocol {
    port: number,
    protocol: InternetProtocol,
}
export enum InternetProtocol {
    TCP = "TCP",
    UDP = "UDP",
}
export enum ApplicationStatus {
    PENDING = "PENDING",
    APPROVED = "APPROVED",
    DECLINED = "DECLINED",
    RELEASED = "RELEASED"
}

export function readableApplicationStatus(status: ApplicationStatus): String {
    switch (status) {
        case ApplicationStatus.PENDING:
            return "Pending";
        case ApplicationStatus.APPROVED:
            return "Approved";
        case ApplicationStatus.DECLINED:
            return "Declined"
        case ApplicationStatus.RELEASED:
            return "Released"
    }
}

export function openPorts(
    request: OpenPortsRequest
): APICallParameters<OpenPortsRequest> {
    return {
        method: "POST",
        path: "/hpc/ip/open-ports",
        parameters: request,
        reloadId: Math.random(),
        payload: request
    };
}
export function closePorts(
    request: ClosePortsRequest
): APICallParameters<ClosePortsRequest> {
    return {
        method: "POST",
        path: "/hpc/ip/close-ports",
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
export interface ListAvailableAddressesRequest {
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
export function listAvailableAddresses(
    request: ListAvailableAddressesRequest
): APICallParameters<ListAvailableAddressesRequest> {
    return {
        method: "GET",
        path: buildQueryString("/hpc/ip/list-available", request),
        parameters: request,
        reloadId: Math.random(),
        payload: undefined
    };
}
export interface ListMyAddressesRequest {
    itemsPerPage?: number,
    page?: number,
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
    pending: boolean,
    itemsPerPage?: number,
    page?: number,
}
export interface AddressApplication {
    id: number;
    application: string;
    status: ApplicationStatus;
    createdAt: number;
    entityId: string;
    entityType: WalletOwnerType;
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
