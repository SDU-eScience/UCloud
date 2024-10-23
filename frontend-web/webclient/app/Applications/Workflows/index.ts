import {ApplicationParameter} from "@/Applications/AppStoreApi";
import {apiBrowse, apiCreate, apiDelete, apiRetrieve, apiUpdate} from "@/Authentication/DataHook";
import {BulkRequest, BulkResponse, FindByStringId, PaginationRequestV2} from "@/UCloud";

export interface Workflow {
    id: string;
    createdAt: number;
    owner: WorkflowOwner;
    specification: WorkflowSpecification;
    status: WorkflowStatus;
    permissions: WorkflowPermissions;
}

export interface WorkflowOwner {
    createdBy: string;
    project?: string | null;
}

export interface WorkflowSpecification {
    applicationName: string
    language: WorkflowLanguage;
    init?: string | null;
    job?: string | null;
    readme?: string | null;
    inputs: ApplicationParameter[];
}

export interface WorkflowStatus {
    path: string;
}

export interface WorkflowPermissions {
    openToWorkspace: boolean;
    myself: WorkflowPermission[];
    others: WorkflowAclEntry[];
}

export interface WorkflowAclEntry {
    group: string;
    permission: WorkflowPermission;
}

export type WorkflowPermission =
    | "READ"
    | "WRITE"
    | "ADMIN"
    ;

export type WorkflowLanguage =
    | "JINJA2"
    ;

const baseContext = "/api/hpc/workflows";

export function browse(
    request: PaginationRequestV2 & {
        filterApplicationName: string;
    }
): APICallParameters<unknown, PageV2<Workflow>> {
    return apiBrowse(request, baseContext);
}

export function create(
    request: BulkRequest<{
        path: string;
        allowOverwrite: boolean;
        specification: WorkflowSpecification;
    }>
): APICallParameters<unknown, BulkResponse<FindByStringId>> {
    return apiCreate(request, baseContext);
}

export function rename(
    request: BulkRequest<{
        id: string;
        newPath: string;
        allowOverwrite: boolean;
    }>
): APICallParameters {
   return apiUpdate(request, baseContext, "rename");
}

export function remove(
    request: BulkRequest<FindByStringId>
): APICallParameters {
    return apiDelete(request, baseContext);
}

export function updateAcl(
    request: BulkRequest<{
        id: string;
        isOpenForWorkspace: boolean;
        entries: WorkflowAclEntry[];
    }>
): APICallParameters {
    return apiUpdate(request, baseContext, "updateAcl");
}

export function retrieve(
    request: FindByStringId
): APICallParameters<unknown, Workflow> {
    return apiRetrieve(request, baseContext);
}
