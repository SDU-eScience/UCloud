import {Product, ProductMetadata, ProductType, Wallet} from "@/Accounting";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {PageV2, PaginationRequestV2} from "@/UCloud";
import * as UCloud from "@/UCloud";
import ProjectWithTitle = UCloud.grant.ProjectWithTitle;
import {apiBrowse, apiCreate, apiDelete, apiRetrieve, apiSearch, apiUpdate, callAPI} from "@/Authentication/DataHook";
import {bulkRequestOf} from "@/DefaultObjects";
import {GrantApplicationFilter} from ".";

const grantBaseContext = "/api/grant/";

export interface GrantApplication {

    // A unique identifier representing a GrantApplication
    //
    // The ID is used for all requests which manipulate the application. The ID is issued by UCloud/Core when the
    // initial revision is submitted. The ID is never re-used by the system, even if a previous version has been
    // closed.
    id: string;

    // Username of the user who originially submitted the application
    createdBy: string;
    // Timestamp representing when the application was originially submitted
    createdAt: number;
    // Timestamp representing when the application was last updated
    updatedAt: number;

    // Information about the current revision
    currentRevision: Revision,

    // Status information about the application in its entirety
    status: Status;
}

export interface Document {
    /*
        Describes the recipient of resources, should the application be accepted

        Updateable by: Original creator(createdBy of application)
        Immutable after creation: Yes
    */
    recipient: Recipient;

    /*
        Describes the allocations for resources which are requested by this application

        Updateable by: Original creator and grant givers
        Immutable after creation: No
    */
    allocationRequests: AllocationRequest[];

    /*
        A form describing why these resources are being requested

        Updateable by: Original creator
        Immutable after creation: No
    */
    form: Form;

    /*
        A reference used for out-of-band book-keeping

        Updateable by: Grant givers
        Immutable after creation: No
        */
    referenceId: string | null,

    /*
        A comment describing why this change was made

        Update by: Original creator and grant givers
        Immutable after creation: No. First revision must always be null.
    */
    revisionComment: string | null,

    /* 
        A field to reference the intended parent project of the application.

        Not null if and only if the recipient is a new project.
    */
    parentProjectId: string | null;
}

type Form = PersonalWorkspaceForm | NewProjectForm | ExistingProjectForm;

interface PersonalWorkspaceForm {
    type: "plain_text";
    text: string;
}

interface NewProjectForm {
    type: "plain_text";
    text: string;
}

interface ExistingProjectForm {
    type: "plain_text";
    text: string;
}

export type Recipient =
    {type: "existingProject", id: string;} |
    {type: "newProject", title: string;} |
    {type: "personalWorkspace", username: string;};

export interface AllocationRequest {
    category: string;
    provider: string;
    grantGiver: string;
    balanceRequested: number;
    sourceAllocation: string | null;
    period: Period,
}

export interface Period {
    start?: number;
    end?: number;
}

/* 
    Contains information about a specific revision of the application.

    The primary contents of the revision is stored in the document. The document describes the contents of the
    application, including which resource allocations are requested and by whom. Every time a change is made to
    the application, a new revision is created. Each revision contains the full document. Changes between versions
    can be computed by comparing with the previous revision.
 */
export interface Revision {
    // Timestamp indicating when this revision was made
    createdAt: number;

    // Username of the user who created this revision
    updatedBy: string;

    /*
        A number indicating which revision this is

        Revision numbers are guaranteed to be unique and always increasing. The first revision number must be 0.
        The backend does not guarantee that revision numbers are issued without gaps. Thus it is allowed for the
        first revision to be 0 and the second revision to be 10.
    */
    revisionNumber: number;

    // Contains the application form from the end-user
    document: Document;
}

export interface Comment {
    id: string;
    username: string;
    createdAt: number;
    comment: string;
}

export interface Status {
    overallState: State;
    stateBreakdown: GrantGiverApprovalState[];
    comments: Comment[];
    revisions: Revision[];
    projectTitle?: string;
    projectPI: string;
}

export interface GrantGiverApprovalState {
    projectId: string;
    projectTitle: string;
    state: State;
}

export enum State {
    APPROVED = "APPROVED",
    REJECTED = "REJECTED",
    CLOSED = "CLOSED",
    IN_PROGRESS = "IN_PROGRESS",
}

export interface DeleteGrantApplicationCommentRequest {
    grantId: string;
    commentId: string;
}

export function deleteGrantApplicationComment(
    request: DeleteGrantApplicationCommentRequest
): APICallParameters<UCloud.BulkRequest<DeleteGrantApplicationCommentRequest>> {
    return apiDelete(bulkRequestOf(request), "api/grant/comment");
}

export interface GrantsRetrieveAffiliationsRequest extends PaginationRequestV2 {
    recipientId?: string;
    recipientType?: string;
}

export type GrantsRetrieveAffiliationsResponse = PageV2<ProjectWithTitle>;

export function browseAffiliations(request: GrantsRetrieveAffiliationsRequest): APICallParameters<GrantsRetrieveAffiliationsRequest> {
    return apiBrowse(request, grantBaseContext, "affiliations");
}

export interface CommentOnGrantApplicationRequest {
    grantId: string;
    comment: string;
}

export type CommentOnGrantApplicationResponse = Record<string, never>;

const commentBaseContent = "/api/grant/comment"

export function commentOnGrantApplication(
    request: CommentOnGrantApplicationRequest
): APICallParameters<UCloud.BulkRequest<CommentOnGrantApplicationRequest>> {
    return apiCreate(bulkRequestOf(request), commentBaseContent)
}

interface RetrieveGrantApplicationRequest {
    id: string;
}

type RetrieveGrantApplicationResponse = GrantApplication;

export function retrieveGrantApplication(request: RetrieveGrantApplicationRequest): APICallParameters<RetrieveGrantApplicationRequest, RetrieveGrantApplicationResponse> {
    snackbarStore.addInformation("Can't fetch grant application yet", false);
    return {
        method: "GET",
        path: "",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
};

export interface RejectGrantApplicationRequest {
    requestId: string;
    notify?: boolean;
}

export function rejectGrantApplication(
    request: RejectGrantApplicationRequest
): APICallParameters<RejectGrantApplicationRequest> {
    return {
        method: "POST",
        path: grantBaseContext + "reject",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export type SubmitApplicationRequest = UCloud.BulkRequest<{document: Document}>;
export type SubmitApplicationResponse = UCloud.BulkResponse<UCloud.FindByLongId>;

export function submitApplicationRequest(payload: SubmitApplicationRequest) {
    return apiCreate(payload, "/api/grant", "submit-application")
}

export type EditApplicationRequest = UCloud.BulkRequest<{
    applicationId: string;
    document: Document;
}>;

export function editApplicationRequest(payload: EditApplicationRequest) {
    return apiUpdate(payload, "/api/grant", "edit");
}

export interface ApproveGrantApplicationRequest {
    requestId: string;
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


export interface TransferApplicationRequest {
    applicationId: number;
    transferToProjectId: string;
    revisionComment: string;
}

export function transferApplication(request: UCloud.BulkRequest<TransferApplicationRequest>): APICallParameters<UCloud.BulkRequest<TransferApplicationRequest>> {
    return {
        method: "POST",
        path: "/grant/transfer",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export interface GrantProductCategory {
    metadata: ProductMetadata;
    currentBalance?: number;
    requestedBalance?: number;
}

export interface FetchGrantApplicationRequest {
    id: string;
}

interface BrowseAffiliationsByResourceRequest extends UCloud.PaginationRequestV2 {
    applicationId: string
}

export function browseAffiliationsByResource(request: UCloud.PaginationRequestV2 & {
    applicationId: string
}): APICallParameters<BrowseAffiliationsByResourceRequest, UCloud.PageV2<ProjectWithTitle>> {
    return apiBrowse(request, grantBaseContext, "affiliationsByResource");
}

export type FetchGrantApplicationResponse = GrantApplication;

export interface EditReferenceIDRequest {
    id: string;
    newReferenceId?: string;
}

export function editReferenceId(
    request: EditReferenceIDRequest
): APICallParameters<EditReferenceIDRequest> {
    return apiUpdate(request, "grant", "editReference");
}

export async function fetchProducts(
    request: UCloud.grant.GrantsRetrieveProductsRequest
): Promise<{availableProducts: Product[];}> {
    return callAPI(apiBrowse(request, "/api/grant", "products"));
}

export function closeApplication(request: UCloud.BulkRequest<{applicationId: string}>): APICallParameters<UCloud.BulkRequest<{applicationId: string}>> {
    return apiUpdate(request, grantBaseContext, "close")
}

interface BrowseApplicationsRequest extends PaginationRequestV2 {
    filter: GrantApplicationFilter;
    includeIngoingApplications: boolean;
    includeOutgoingApplications: boolean;
}

export function browseGrantApplications(
    request: BrowseApplicationsRequest
): APICallParameters<BrowseApplicationsRequest> {
    return apiBrowse(request, grantBaseContext);
}

interface RetrieveTemplatesRequest {
    projectId: string;
}

export function retrieveTemplates(request: RetrieveTemplatesRequest): APICallParameters<RetrieveTemplatesRequest> {
    return apiRetrieve(request, "/api/grant/templates");
}

export interface Templates {
    type: "plain_text";
    personalProject: string;
    newProject: string;
    existingProject: string;
}

interface UpdateStateRequest {
    applicationId: string;
    newState: State;
    notify: boolean;
}

export function updateState(request: UCloud.BulkRequest<UpdateStateRequest>): APICallParameters<UCloud.BulkRequest<UpdateStateRequest>> {
    return apiUpdate(request, "/api/grant", "update-state");
}

export async function fetchGrantApplication(request: FetchGrantApplicationRequest): Promise<FetchGrantApplicationResponse> {
    return callAPI<FetchGrantApplicationResponse>(apiRetrieve(request, "api/grant"));
}

interface BrowseProductsRequest {
    projectId: string;
    recipientType: "existingProject" | "newProject" | "personalWorkspace";
    recipientId: string;
    showHidden: boolean;
}

export function browseProducts(request: BrowseProductsRequest): APICallParameters<BrowseProductsRequest> {
    return apiBrowse(request, "/api/grant", "products");
}