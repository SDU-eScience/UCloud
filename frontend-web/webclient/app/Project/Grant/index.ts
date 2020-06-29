import {APICallParameters} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";

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

export interface ResourceRequest{
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

export interface GrantApplication {
    status: GrantApplicationStatus;
    resourcesOwnedBy: string;
    requestedBy: string;
    grantRecipient: GrantRecipient;
    document: string;
    requestedResources: ResourceRequest[]
    id?: number;
}

export type SubmitGrantApplicationRequest = GrantApplication;

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
