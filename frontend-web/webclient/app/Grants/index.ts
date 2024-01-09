import {apiBrowse, apiRetrieve, apiUpdate} from "@/Authentication/DataHook";
import * as Accounting from "@/Accounting";
import {FindByStringId, PageV2, PaginationRequestV2} from "@/UCloud";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";

const baseContext = "/api/grants/v2";

// Core workflow and CRUD
// =====================================================================================================================
export function browse(
    request: PaginationRequestV2 & {
        filter?: ApplicationFilter,
        includeIngoingApplications?: boolean,
        includeOutgoingApplications?: boolean,
    }
): APICallParameters<unknown, PageV2<Application>> {
    return apiBrowse(request, baseContext);
}

export function retrieve(
    request: FindByStringId
): APICallParameters<unknown, Application> {
    return apiRetrieve(request, baseContext);
}

export function submitRevision(
    request: {
        revision: Doc,
        comment: string,
        applicationId?: string
    }
): APICallParameters<unknown, FindByStringId> {
    return apiUpdate(request, baseContext, "submitRevision");
}

export function updateState(
    request: {
        applicationId: string,
        newState: State,
    }
): APICallParameters<unknown, {}> {
    return apiUpdate(request, baseContext, "updateState");
}

export function transfer(
    request: {
        applicationId: string,
        target: string,
        comment: string,
    }
): APICallParameters<unknown, {}> {
    return apiUpdate(request, baseContext, "transfer");
}

export type RetrieveGrantGiversRequest =
    { type: "PersonalWorkspace" }
    | { type: "NewProject", title: string }
    | { type: "ExistingProject", id: string }
    | { type: "ExistingApplication", id: string }
    ;

export function retrieveGrantGivers(
    request: RetrieveGrantGiversRequest
): APICallParameters<unknown, { grantGivers: GrantGiver[] }> {
    return apiUpdate(request, baseContext, "retrieveGrantGivers");
}

// Comments
// ====================================================================================================================
export function postComment(
    request: {
        applicationId: string,
        comment: string
    }
): APICallParameters<unknown, FindByStringId> {
    return apiUpdate(request, baseContext, "postComment");
}

export function deleteComment(
    request: {
        applicationId: string,
        commentId: string
    }
): APICallParameters<unknown, {}> {
    return apiUpdate(request, baseContext, "deleteComment");
}

// Request settings
// ====================================================================================================================
export function updateRequestSettings(
    request: RequestSettings,
): APICallParameters<unknown, {}> {
    return apiUpdate(request, baseContext, "updateRequestSettings");
}

export function retrieveRequestSettings(): APICallParameters<unknown, RequestSettings> {
    return apiRetrieve({}, baseContext, "requestSettings");
}

// Types
// ====================================================================================================================
export interface Application {
    // A unique identifier representing a GrantApplication
    //
    // The ID is used for all requests which manipulate the application. The ID is issued by UCloud/Core when the
    // initial revision is submitted. The ID is never re-used by the system, even if a previous version has been
    // closed.
    id: string;

    // Username of the user who originally submitted the application
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

export interface Doc {
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
        A reference used for out-of-band bookkeeping

        Updateable by: Grant givers
        Immutable after creation: No
        */
    referenceIds?: string[] | null,

    /*
        A comment describing why this change was made

        Update by: Original creator and grant givers
        Immutable after creation: No. First revision must always be null.
    */
    revisionComment?: string | null,

    /*
        A field to reference the intended parent project of the application.

        Not null if and only if the recipient is a new project.
    */
    parentProjectId?: string | null;
}

type Form = PlainTextForm | GrantGiverInitiatedForm;

interface PlainTextForm {
    type: "plain_text";
    text: string;
}

interface GrantGiverInitiatedForm {
    type: "grant_giver_initiated";
    text: string;
    subAllocator: boolean;
}

export type Recipient =
    { type: "existingProject", id: string; } |
    { type: "newProject", title: string; } |
    { type: "personalWorkspace", username: string; };

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
    document: Doc;
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

export interface GrantGiver {
    id: string;
    title: string;
    description: string;
    templates: Templates;
    categories: Accounting.ProductCategoryV2[];
}

export interface Templates {
    type: "plain_text";
    personalProject: string;
    newProject: string;
    existingProject: string;
}

export interface RequestSettings {
    enabled: boolean;
    description: string;
    allowRequestsFrom: UserCriteria[];
    excludeRequestsFrom: UserCriteria[];
    templates: Templates;
}

export enum ApplicationFilter {
    SHOW_ALL = "SHOW_ALL",
    INACTIVE = "INACTIVE",
    ACTIVE = "ACTIVE"
}

export type UserCriteria =
    { type: "anyone" }
    | { type: "email", domain: string }
    | { type: "wayf", org: string }
    ;

export function stateToIconAndColor(state: State): { icon: IconName, color: ThemeColor } {
    switch (state) {
        case State.APPROVED:
            return { icon: "heroCheck", color: "green" };
        case State.REJECTED:
            return { icon: "heroXMark", color: "red" };
        case State.CLOSED:
            return { icon: "heroXMark", color: "red" };
        case State.IN_PROGRESS:
            return { icon: "heroMinus", color: "gray" };
    }
}
