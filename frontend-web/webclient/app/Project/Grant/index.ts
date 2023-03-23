import { BulkRequest, PageV2 } from "@/UCloud";
import { GrantApplication } from "@/Project/Grant/GrantApplicationTypes";
import * as UCloud from "@/UCloud";
import { apiRetrieve, apiUpdate } from "@/Authentication/DataHook";
import { WalletAllocation } from "@/Accounting";
import { timestampUnixMs } from "@/UtilityFunctions";

export interface ReadTemplatesRequest {
    projectId: string;
}

export interface ReadTemplatesResponse {
    type: "plain_text";
    personalProject: string;
    newProject: string;
    existingProject: string;
}

export function readTemplates(request: ReadTemplatesRequest): APICallParameters<ReadTemplatesRequest> {
    return apiRetrieve(request, "/api/grant/templates");
}

export interface ResourceRequest {
    productCategory: string;
    productProvider: string;
    balanceRequested?: number;
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
    return apiRetrieve(request, "/api/grant/enabled");
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

export type IngoingGrantApplicationsResponse = PageV2<GrantApplication>;

export interface ReadGrantRequestSettingsRequest {
    projectId: string;
}

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

export interface ProjectGrantSettings {
    projectId: string;
    allowRequestsFrom: UserCriteria[];
    excludeRequestsFrom: UserCriteria[];
}

export function readGrantRequestSettings(
    request: ReadGrantRequestSettingsRequest
): APICallParameters<ReadGrantRequestSettingsRequest> {
    return apiRetrieve(request, "/api/grant/settings");
}

export type UploadGrantRequestSettingsRequestItem = ProjectGrantSettings;

export function uploadGrantRequestSettings(
    request: BulkRequest<UploadGrantRequestSettingsRequestItem>
): APICallParameters {
    return apiUpdate(request, "/api/grant/settings", "upload");
}

export type UploadTemplatesRequest = ReadTemplatesResponse;

export function uploadTemplates(request: UploadTemplatesRequest): APICallParameters<UploadTemplatesRequest> {
    return apiUpdate(request, "/api/grant/templates", "uploadTemplates");
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
    return apiRetrieve(request, "/api/grant/description");
}

export interface UploadDescriptionRequest {
    projectId: string,
    description: string
}

export function uploadDescription(request: BulkRequest<UploadDescriptionRequest>): APICallParameters {
    return apiUpdate(request, "/api/grant/description", "upload");
}

export type GrantsRetrieveAffiliationsResponse = PageV2<UCloud.grant.ProjectWithTitle>;

export function isAllocationSuitableForSubAllocation(alloc: WalletAllocation): boolean {
    const now = timestampUnixMs();
    return (alloc.endDate == null || now < alloc.endDate) &&
        alloc.localBalance > 0 &&
        alloc.canAllocate;
}
