import {Product, ProductMetadata, ProductType, Wallet} from "@/Accounting";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {PageV2, PaginationRequestV2} from "@/UCloud";
import {buildQueryString} from "@/Utilities/URIUtilities";
import * as UCloud from "@/UCloud";
import ProjectWithTitle = UCloud.grant.ProjectWithTitle;
import {apiBrowse, apiDelete, apiUpdate} from "@/Authentication/DataHook";

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
    form: string,

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
    id: string;
    title: string;
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
    return apiDelete(request, "/grant/comment")
}

export interface GrantsRetrieveAffiliationsRequest {
    grantId: string;
    itemsPerPage: number;
    page: number;
}

export type GrantsRetrieveAffiliationsResponse = Page<{projectId: string, title: string}>;

export function browseAffiliations(request: GrantsRetrieveAffiliationsRequest): APICallParameters<GrantsRetrieveAffiliationsRequest> {
    return apiBrowse(request, "grant", "affiliations")
}

export interface CommentOnGrantApplicationRequest {
    requestId: string;
    comment: string;
}

export type CommentOnGrantApplicationResponse = Record<string, never>;

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
    }
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
        path: "/grant/reject",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export type SubmitApplicationRequest = Document;

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
    applicationId: string;
    transferToProjectId: string
}

export function transferApplication(request: TransferApplicationRequest): APICallParameters<TransferApplicationRequest> {
    return {
        method: "POST",
        path: "/grant/transfer",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}


// ========================= TEMPORARY ===================================
export function fetchGrantApplicationFake(request: FetchGrantApplicationRequest): Promise<GrantApplication> {
    const now = new Date().getTime();

    const grantApplication: GrantApplication = {
        id: `${(Math.random() * 1000) | 0}`,
        createdAt: now - 100000,
        updatedAt: now - 11231,
        createdBy: FlynnTaggart,
        currentRevision: {
            createdAt: now - 10,
            updatedBy: FlynnTaggart,
            revisionNumber: 0,
            document: {
                recipient: {
                    type: "personal_workspace",
                    username: FlynnTaggart
                },
                allocationRequests: [{
                    balanceRequested: 500,
                    category: "SSG",
                    provider: "UAC",
                    sourceAllocation: null,
                    grantGiver: "UAC",
                    period: {
                        start: now - 12343456,
                        end: undefined
                    }
                }],
                form: "I believe that providing me with cannon resources (technically compute), I can achieve new academic heights.",
                referenceId: null,
                revisionComment: null,
                parentProjectId: null
            }
        },
        status: {
            overallState: State.PENDING,
            stateBreakdown: [{
                id: "just-some-id-we-cant-consider-valid",
                title: "UAC",
                state: State.APPROVED,
            }, {
                id: "just-some-other-id-we-cant-consider-valid",
                title: "HELL",
                state: State.REJECTED,
            }, {
                id: "the-final-one",
                title: "Cultist Base",
                state: State.PENDING
            }],
            comments: [{
                id: "0",
                username: FlynnTaggart,
                createdAt: now - 12334,
                comment: "It's imperative that I recieve the funding to get to Mars. I need to find the lost city of Hebeth."
            }, {
                id: "1",
                username: DrSamHayden,
                createdAt: now - 12300,
                comment: "Using what?"
            }, {
                id: "2",
                username: DrSamHayden,
                createdAt: now - 12200,
                comment: "The cannon?"
            }, {
                id: "3",
                username: DrSamHayden,
                createdAt: now - 12100,
                comment: "That is a weapon, not a teleporter."
            }],
            revisions: []
        }
    };

    return new Promise((resolve) => {
        window.setTimeout(() => resolve(grantApplication), 200);
    });
}

const FlynnTaggart = "FlynnTaggart#1777";
const DrSamHayden = "SamuelHayden#1666";

function browseProjects(request: PaginationRequestV2): APICallParameters {
    return {
        method: "GET",
        context: "",
        path: buildQueryString("/api/grant/browse-projects", request),
        parameters: request
    };
}

export interface GrantProductCategory {
    metadata: ProductMetadata;
    currentBalance?: number;
    requestedBalance?: number;
}

interface FakeClient {
    activeUsername: string;
    username: string;
    userInfo?: {
        firstNames: string;
        lastName: string;
    }
}

export const Client: FakeClient = {
    activeUsername: FlynnTaggart,
    username: FlynnTaggart,
    userInfo: {
        firstNames: "Flynn",
        lastName: "Taggart"
    }
}

export interface FetchGrantApplicationRequest {
    id: string;
}

export type FetchGrantApplicationResponse = GrantApplication;

export function fetchGrantGiversFake(): PageV2<ProjectWithTitle> {
    const items: ProjectWithTitle[] = [{
        projectId: "just-some-id-we-cant-consider-valid",
        title: "UAC",
    }, {
        projectId: "just-some-other-id-we-cant-consider-valid",
        title: "HELL",
    }, {
        projectId: "the-final-one",
        title: "Cultist Base",
    }];

    return {
        items,
        itemsPerPage: 250,
        next: undefined
    };
}

export interface EditReferenceIDRequest {
    id: string;
    newReferenceId?: string
}

export function editReferenceId(
    request: EditReferenceIDRequest
): APICallParameters<EditReferenceIDRequest> {
    return apiUpdate(request, "grant", "editReference");
}

export async function fetchProducts(
    request: APICallParameters<UCloud.grant.GrantsRetrieveProductsRequest, UCloud.grant.GrantsRetrieveProductsResponse>
): Promise<{availableProducts: Product[]}> {
    switch (request.parameters?.projectId) {
        // UAC
        case "just-some-id-we-cant-consider-valid": {
            return new Promise(resolve => resolve({
                availableProducts: [{
                    balance: 123123,
                    category: {
                        name: "SSG",
                        provider: "UAC",
                    },
                    chargeType: "DIFFERENTIAL_QUOTA",
                    freeToUse: false,
                    hiddenInGrantApplications: false,
                    name: "name",
                    pricePerUnit: 1203,
                    unitOfPrice: "PER_UNIT",
                    type: "compute",
                    productType: "COMPUTE",
                    description: "Didn't appear in the first iteration. Introduced for the second one. Beats all",
                    priority: 0
                }, {
                    balance: 123123,
                    category: {
                        name: "Ballista",
                        provider: "UAC",
                    },
                    chargeType: "DIFFERENTIAL_QUOTA",
                    freeToUse: false,
                    hiddenInGrantApplications: false,
                    name: "name",
                    pricePerUnit: 1203,
                    unitOfPrice: "PER_UNIT",
                    type: "storage",
                    productType: "STORAGE",
                    description: "Efficient and noisy",
                    priority: 0
                }]
            }));
        }
        // HELL
        case "just-some-other-id-we-cant-consider-valid": {
            return new Promise(resolve => resolve({
                availableProducts: [{
                    balance: 123123,
                    category: {
                        name: "SSG",
                        provider: "HELL",
                    },
                    chargeType: "DIFFERENTIAL_QUOTA",
                    freeToUse: false,
                    hiddenInGrantApplications: false,
                    name: "name",
                    pricePerUnit: 1203,
                    unitOfPrice: "PER_UNIT",
                    type: "compute",
                    productType: "COMPUTE",
                    description: "Didn't appear in the first iteration. Introduced for the second one. Beats all",
                    priority: 0
                }, {
                    balance: 123123,
                    category: {
                        name: "PlasmaR",
                        provider: "HELL",
                    },
                    chargeType: "DIFFERENTIAL_QUOTA",
                    freeToUse: false,
                    hiddenInGrantApplications: false,
                    name: "name",
                    pricePerUnit: 1203,
                    unitOfPrice: "PER_UNIT",
                    type: "storage",
                    productType: "STORAGE",
                    description: "Efficient and noisy",
                    priority: 0
                }]
            }));
        }
        // Cultist Base
        case "the-final-one": {
            return new Promise(resolve => resolve({
                availableProducts: [{
                    balance: 123123,
                    category: {
                        name: "SSG",
                        provider: "Cultist Base",
                    },
                    chargeType: "DIFFERENTIAL_QUOTA",
                    freeToUse: false,
                    hiddenInGrantApplications: false,
                    name: "name",
                    pricePerUnit: 1203,
                    unitOfPrice: "PER_UNIT",
                    type: "network_ip",
                    productType: "NETWORK_IP",
                    description: "Didn't appear in the first iteration. Introduced for the second one. Beats all",
                    priority: 0
                }, {
                    balance: 123123,
                    category: {
                        name: "CyberD",
                        provider: "Cultist Base",
                    },
                    chargeType: "DIFFERENTIAL_QUOTA",
                    freeToUse: false,
                    hiddenInGrantApplications: false,
                    name: "name",
                    pricePerUnit: 1203,
                    unitOfPrice: "PER_UNIT",
                    type: "storage",
                    productType: "STORAGE",
                    description: "Efficient and noisy",
                    priority: 0
                }]
            }));
        }
    }
    return new Promise(resolve => resolve({
        availableProducts: []
    }));
}

export function debugWallet(type: ProductType, projectId: string, name: string, provider: string): Wallet {
    return {
        "owner": {
            "type": "project",
            "projectId": projectId,
        },
        "paysFor": {
            "name": name,
            "provider": provider
        },
        "allocations": [
            {
                "id": `${(Math.random() * 12390) | 0}`,
                "allocationPath":
                    "120"
                ,
                "balance": 9999999888,
                "initialBalance": 10000000000,
                "localBalance": 10000000000,
                "startDate": 1652854470991,
                "endDate": null,
            }
        ],
        "chargePolicy": "EXPIRE_FIRST",
        "productType": type,
        "chargeType": "ABSOLUTE",
        "unit": "UNITS_PER_MINUTE"
    };
}

export function listOfDebugWallets(type: ProductType, projectId: string, name: string, provider: string, count: number): Wallet[] {
    const result: Wallet[] = [];
    for (let i = 0; i < count; i++) {
        result.push(debugWallet(type, projectId, name, provider));
    }
    return result;
}


const DEBUGGING_WALLETS: Wallet[] = [
    {
        "owner": {
            "type": "project",
            "projectId": "just-some-id-we-cant-consider-valid"
        },
        "paysFor": {
            "name": "Ballista",
            "provider": "UAC"
        },
        "allocations": [
            {
                "id": "120",
                "allocationPath":
                    "120"
                ,
                "balance": 9999999888,
                "initialBalance": 10000000000,
                "localBalance": 10000000000,
                "startDate": 1652854470991,
                "endDate": null,
            }
        ],
        "chargePolicy": "EXPIRE_FIRST",
        "productType": "COMPUTE",
        "chargeType": "ABSOLUTE",
        "unit": "UNITS_PER_MINUTE"
    },
    {
        "owner": {
            "type": "project",
            "projectId": "just-some-other-id-we-cant-consider-valid"
        },
        "paysFor": {
            "name": "home",
            "provider": "hippo"
        },
        "allocations": [
            {
                "id": "78",
                "allocationPath":
                    "78"
                ,
                "balance": 9999999998,
                "initialBalance": 10000000000,
                "localBalance": 10000000000,
                "startDate": 1652257657990,
                "endDate": null,
            }
        ],
        "chargePolicy": "EXPIRE_FIRST",
        "productType": "STORAGE",
        "chargeType": "DIFFERENTIAL_QUOTA",
        "unit": "PER_UNIT"
    },
    {
        "owner": {
            "type": "project",
            "projectId": "the-final-one"
        },
        "paysFor": {
            "name": "PlasmaR",
            "provider": "HELL"
        },
        "allocations": [
            {
                "id": "79",
                "allocationPath":
                    "79"
                ,
                "balance": 9999999998,
                "initialBalance": 10000000000,
                "localBalance": 10000000000,
                "startDate": 1652257659253,
                "endDate": null,
            }
        ],
        "chargePolicy": "EXPIRE_FIRST",
        "productType": "STORAGE",
        "chargeType": "DIFFERENTIAL_QUOTA",
        "unit": "PER_UNIT"
    },
    {
        "owner": {
            "type": "project",
            "projectId": "the-final-one"
        },
        "paysFor": {
            "name": "tek-comsol",
            "provider": "ucloud"
        },
        "allocations": [
            {
                "id": "29",
                "allocationPath":
                    "29"
                ,
                "balance": 9999999989,
                "initialBalance": 10000000000,
                "localBalance": 10000000000,
                "startDate": 1644496228171,
                "endDate": null,
            }
        ],
        "chargePolicy": "EXPIRE_FIRST",
        "productType": "LICENSE",
        "chargeType": "ABSOLUTE",
        "unit": "PER_UNIT"
    },
    {
        "owner": {
            "type": "project",
            "projectId": "just-some-id-we-cant-consider-valid"
        },
        "paysFor": {
            "name": "HELL",
            "provider": "PlasmaR"
        },
        "allocations": [
            {
                "id": "1",
                "allocationPath":
                    "1"
                ,
                "balance": 999999999999982,
                "initialBalance": 1000000000000000,
                "localBalance": 1000000000000000,
                "startDate": 1642670509286,
                "endDate": null,
            }
        ],
        "chargePolicy": "EXPIRE_FIRST",
        "productType": "STORAGE",
        "chargeType": "DIFFERENTIAL_QUOTA",
        "unit": "PER_UNIT"
    },
    {
        "owner": {
            "type": "project",
            "projectId": "just-some-id-we-cant-consider-valid"
        },
        "paysFor": {
            "name": "foo",
            "provider": "HELL"
        },
        "allocations": [
            {
                "id": "2",
                allocationPath:
                    "2"
                ,
                "balance": 999999999999986,
                "initialBalance": 1000000000000000,
                "localBalance": 999999999999986,
                "startDate": 1642670511339,
                "endDate": null,
            }
        ],
        "chargePolicy": "EXPIRE_FIRST",
        "productType": "INGRESS",
        "chargeType": "ABSOLUTE",
        "unit": "PER_UNIT"
    },
    {
        "owner": {
            "type": "project",
            "projectId": "the-final-one"
        },
        "paysFor": {
            "name": "SSG",
            "provider": "HELL"
        },
        "allocations": [
            {
                "id": "3",
                "allocationPath":
                    "3"
                ,
                "balance": 999999892945986,
                "initialBalance": 1000000000000000,
                "localBalance": 999999896725000,
                "startDate": 1642670512116,
                "endDate": null,
            }
        ],
        "chargePolicy": "EXPIRE_FIRST",
        "productType": "COMPUTE",
        "chargeType": "ABSOLUTE",
        "unit": "CREDITS_PER_MINUTE"
    }
];