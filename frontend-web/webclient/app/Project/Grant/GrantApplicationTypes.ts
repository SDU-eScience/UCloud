import {buildQueryString} from "@/Utilities/URIUtilities";

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
    form: String,

    /*
        A reference used for out-of-band book - keeping

        Updateable by: Grant givers
        Immutable after creation: No
        */
    referenceId: string | null,

    /*
        A comment describing why this change was made

        Update by: Original creator and grant givers
        Immutable after creation: No.First revision must always be null.
    */
    revisionComment: string | null,
}

export type Recipient =
    {type: "existing_project", id: string} |
    {type: "new_project", title: string} |
    {type: "personal_workspace", username: string};

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
}

export interface GrantGiverApprovalState {
    id: String;
    title: String;
    state: State;
}

export enum State {
    PENDING,
    APPROVED,
    REJECTED
}

export interface DeleteGrantApplicationCommentRequest {
    commentId: string;
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

export interface GrantsRetrieveAffiliationsRequest {
    grantId: string;
    itemsPerPage: number;
    page: number;
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

export interface CommentOnGrantApplicationRequest {
    requestId: string;
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

export type SubmitApplicationRequest = Document;