import {APICallParameters} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";
import {Page, PaginationRequest} from "Types";

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
    creditsRequested?: number;
    quotaRequested?: number;
}

export enum GrantApplicationStatus {
    APPROVED = "APPROVED",
    REJECTED = "REJECTED",
    IN_PROGRESS = "IN_PROGRESS"
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
}

export type SubmitGrantApplicationRequest = CreateGrantApplication;

export type SubmitGrantApplicationResponse = {};

export function submitGrantApplication(request: SubmitGrantApplicationRequest): APICallParameters<SubmitGrantApplicationRequest> {
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

export type ViewGrantApplicationResponse = { application: GrantApplication, comments: Comment[], approver: boolean } ;

export function viewGrantApplication(
    request: ViewGrantApplicationRequest
): APICallParameters<ViewGrantApplicationRequest> {
    return {
        method: "GET",
        path: `/grant/${request.id}`,
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

export interface ExternalApplicationsEnabledRequest {
    projectId: string;
}

export interface ExternalApplicationsEnabledResponse {
    enabled: boolean;
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

export type IngoingGrantApplicationsRequest = PaginationRequest;
export type IngoingGrantApplicationsResponse = Page<GrantApplication>;

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
