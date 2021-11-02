import {buildQueryString} from "@/Utilities/URIUtilities";
import {PageV2, PaginationRequestV2} from "@/UCloud";

export interface ReadTemplatesRequest {
    projectId: string;
}

export interface ReadTemplatesResponse {
    personalProject: string;
    newProject: string;
    existingProject: string;
}

export function readTemplates(request: ReadTemplatesRequest): APICallParameters<ReadTemplatesRequest> {
    return {
        method: "GET",
        path: buildQueryString("/grant/read-templates", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export type GrantRecipient = GrantRecipientPersonal | GrantRecipientExisting | GrantRecipientNew;

export interface GrantRecipientPersonal {
    username: string;
    type: "personal"
}

export interface GrantRecipientExisting {
    projectId: string;
    type: "existing_project";
}

export interface GrantRecipientNew {
    projectTitle: string;
    type: "new_project";
}

export interface ResourceRequest {
    productCategory: string;
    productProvider: string;
    balanceRequested?: number;
}

export enum GrantApplicationStatus {
    APPROVED = "APPROVED",
    REJECTED = "REJECTED",
    IN_PROGRESS = "IN_PROGRESS",
    CLOSED = "CLOSED"
}

export function isGrantFinalized(status?: GrantApplicationStatus): boolean {
    if (status === undefined) return false;
    return [
        GrantApplicationStatus.APPROVED,
        GrantApplicationStatus.REJECTED,
        GrantApplicationStatus.CLOSED
    ].includes(status);
}

export interface CreateGrantApplication {
    resourcesOwnedBy: string;
    grantRecipient: GrantRecipient;
    document: string;
    requestedResources: ResourceRequest[]
}

export interface GrantApplication {
    resourcesOwnedBy: string;
    grantRecipient: GrantRecipient;
    document: string;
    requestedResources: ResourceRequest[]
    status: GrantApplicationStatus;
    requestedBy: string;
    resourcesOwnedByTitle: string;
    grantRecipientPi: string;
    grantRecipientTitle: string;
    id: number;
    updatedAt: number;
    createdAt: number;
    statusChangedBy?: string;
    referenceId?: string;
}

export type SubmitGrantApplicationRequest = CreateGrantApplication;

export type SubmitGrantApplicationResponse = {};

export function submitGrantApplication(
    request: SubmitGrantApplicationRequest
): APICallParameters<SubmitGrantApplicationRequest> {
    return {
        method: "POST",
        path: "/grant/submit-application",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export interface Comment {
    id: number;
    postedBy: string;
    postedAt: number;
    comment: string;
}

export interface ViewGrantApplicationRequest {
    id: number;
}

export type ViewGrantApplicationResponse = {application: GrantApplication, comments: Comment[], approver: boolean};

export function viewGrantApplication(
    request: ViewGrantApplicationRequest
): APICallParameters<ViewGrantApplicationRequest> {
    return {
        method: "GET",
        path: buildQueryString("/grant", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export interface CommentOnGrantApplicationRequest {
    requestId: number;
    comment: string;
}

export type CommentOnGrantApplicationResponse = {};

export function commentOnGrantApplication(
    request: CommentOnGrantApplicationRequest
): APICallParameters<CommentOnGrantApplicationRequest> {
    return {
        method: "POST",
        path: "/grant/comment",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export interface DeleteGrantApplicationCommentRequest {
    commentId: number;
}

export function deleteGrantApplicationComment(
    request: DeleteGrantApplicationCommentRequest
): APICallParameters<DeleteGrantApplicationCommentRequest> {
    return {
        method: "DELETE",
        path: "/grant/comment",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export interface EditGrantApplicationRequest {
    id: number;
    newDocument: string;
    newResources: ResourceRequest[];
}

export function editGrantApplication(
    request: EditGrantApplicationRequest
): APICallParameters<EditGrantApplicationRequest> {
    return {
        method: "POST",
        path: "/grant/edit",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export interface EditReferenceIDRequest {
    id: number;
    newReferenceId?: string
}

export function editReferenceId(
    request:EditReferenceIDRequest
): APICallParameters<EditReferenceIDRequest> {
    return {
        method: "POST",
        path: "/grant/editReference",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    }
}

export interface ApproveGrantApplicationRequest {
    requestId: number;
}

export function approveGrantApplication(
    request: ApproveGrantApplicationRequest
): APICallParameters<ApproveGrantApplicationRequest> {
    return {
        method: "POST",
        path: "/grant/approve",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export interface RejectGrantApplicationRequest {
    requestId: number;
    notify?: boolean;
}

export function rejectGrantApplication(
    request: RejectGrantApplicationRequest
): APICallParameters<RejectGrantApplicationRequest> {
    return {
        method: "POST",
        path: "/grant/reject",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export interface CloseGrantApplicationRequest {
    requestId: number;
    notify?: boolean;
}

export function closeGrantApplication(
    request: CloseGrantApplicationRequest
): APICallParameters<CloseGrantApplicationRequest> {
    return {
        method: "POST",
        path: "/grant/close",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export interface ExternalApplicationsEnabledRequest {
    projectId: string;
}

export interface ExternalApplicationsEnabledResponse {
    enabled: boolean;
}

export interface AllowSubProjectsRenamingRequest {
    projectId: string;
}

export interface AllowSubProjectsRenamingResponse {
    allowed: boolean;
}

export interface ToggleSubProjectsRenamingRequest {
    projectId: string;
}

export function externalApplicationsEnabled(
    request: ExternalApplicationsEnabledRequest
): APICallParameters<ExternalApplicationsEnabledRequest> {
    return {
        method: "GET",
        path: buildQueryString("/grant/is-enabled", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export enum GrantApplicationFilter {
    SHOW_ALL = "SHOW_ALL",
    INACTIVE = "INACTIVE",
    ACTIVE = "ACTIVE"
}

export function grantApplicationFilterPrettify(filter: GrantApplicationFilter): string {
    switch (filter) {
        case GrantApplicationFilter.ACTIVE:
            return "Active";
        case GrantApplicationFilter.SHOW_ALL:
            return "Show all";
        case GrantApplicationFilter.INACTIVE:
            return "Inactive";
    }
}

export interface IngoingGrantApplicationsRequest extends PaginationRequestV2 {
    filter?: GrantApplicationFilter;
}
export type IngoingGrantApplicationsResponse = PageV2<GrantApplication>;

export function ingoingGrantApplications(
    request: IngoingGrantApplicationsRequest
): APICallParameters<IngoingGrantApplicationsRequest> {
    return {
        method: "GET",
        path: buildQueryString("/grant/ingoing", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export interface ReadGrantRequestSettingsRequest {
    projectId: string;
}

export type ReadGrantRequestSettingsResponse = ProjectGrantSettings;

export type UserCriteria = UserCriteriaAnyone | UserCriteriaEmail | UserCriteriaWayf;

export interface UserCriteriaAnyone {
    type: "anyone"
}

export interface UserCriteriaEmail {
    type: "email";
    domain: string;
}

export interface UserCriteriaWayf {
    type: "wayf";
    org: string;
}

export interface AutomaticApprovalSettings {
    from: UserCriteria[];
    maxResources: ResourceRequest[];
}

export interface ProjectGrantSettings {
    automaticApproval: AutomaticApprovalSettings;
    allowRequestsFrom: UserCriteria[];
    excludeRequestsFrom: UserCriteria[];
}

export function readGrantRequestSettings(
    request: ReadGrantRequestSettingsRequest
): APICallParameters<ReadGrantRequestSettingsRequest> {
    return {
        method: "GET",
        path: buildQueryString("/grant/request-settings", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export type UploadGrantRequestSettingsRequest = ProjectGrantSettings;

export function uploadGrantRequestSettings(
    request: UploadGrantRequestSettingsRequest
): APICallParameters<UploadGrantRequestSettingsRequest> {
    return {
        method: "POST",
        path: "/grant/request-settings",
        payload: request,
        parameters: request,
        reloadId: Math.random()
    };
}

export type UploadTemplatesRequest = ReadTemplatesResponse;

export function uploadTemplates(request: UploadTemplatesRequest): APICallParameters<UploadTemplatesRequest> {
    return {
        method: "POST",
        path: "/grant/upload-templates",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export interface RetrieveDescriptionRequest {
    projectId?: string;
}

export interface RetrieveDescriptionResponse {
    description: string;
}

export function retrieveDescription(
    request: RetrieveDescriptionRequest
): APICallParameters<RetrieveDescriptionRequest> {
    return {
        method: "GET",
        path: buildQueryString("/grant/description/", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export interface UploadDescriptionRequest {
    projectId: string,
    description: string
}

export function uploadDescription(request: UploadDescriptionRequest): APICallParameters<UploadDescriptionRequest> {
    return {
        method: "POST",
        path: "/grant/uploadDescription",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export type BrowseProjectsRequest = PaginationRequest;
export type BrowseProjectsResponse = Page<{projectId: string, title: string}>;

export function browseProjects(request: BrowseProjectsRequest): APICallParameters<BrowseProjectsRequest> {
    return {
        method: "GET",
        path: buildQueryString("/grant/browse-projects", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export interface GrantsRetrieveAffiliationsRequest {
    grantId: number,
    itemsPerPage: number,
    page: number
}
export type GrantsRetrieveAffiliationsResponse = Page<{projectId: string, title: string}>;

export function findAffiliations(request: GrantsRetrieveAffiliationsRequest): APICallParameters<GrantsRetrieveAffiliationsRequest> {
    return {
        method: "GET",
        path: buildQueryString("/grant/retrieveAffiliations", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export interface TransferApplicationRequest {
    applicationId: number;
    transferToProjectId: string
}

export function transferApplication(request: TransferApplicationRequest): APICallParameters<TransferApplicationRequest> {
    return {
        method: "POST",
        path:"/grant/transfer",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export interface ListOutgoingApplications extends PaginationRequestV2 {
    filter?: GrantApplicationFilter;
}

export function listOutgoingApplications(
    request: ListOutgoingApplications
): APICallParameters<ListOutgoingApplications> {
    return {
        path: buildQueryString("/grant/outgoing", request),
        method: "GET",
        parameters: request
    };
}
