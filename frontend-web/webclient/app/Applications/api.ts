import {JobState, RunsSortBy} from "Applications/index";
import {APICallParameters} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {SortOrder} from "Files";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {buildQueryString} from "Utilities/URIUtilities";
import {b64EncodeUnicode} from "Utilities/XHRUtils";
import {inSuccessRange} from "UtilityFunctions";

export interface ListByNameProps {
    itemsPerPage: number;
    page: number;
    name: string;
}

export function listByName({name, itemsPerPage, page}: ListByNameProps): APICallParameters<ListByNameProps> {
    return {
        reloadId: Math.random(),
        method: "GET",
        path: buildQueryString(`/hpc/apps/${name}`, {itemsPerPage, page}),
        parameters: {name, itemsPerPage, page}
    };
}

export interface ListApplicationsProps {
    page: number;
    itemsPerPage: number;
}

export function listApplications(props: ListApplicationsProps): APICallParameters<ListApplicationsProps> {
    return {
        reloadId: Math.random(),
        method: "GET",
        path: buildQueryString("/hpc/apps", props),
        parameters: props
    };
}

export type ListToolsProps = ListApplicationsProps;

export function listTools(props: ListToolsProps): APICallParameters<ListToolsProps> {
    return {
        reloadId: Math.random(),
        method: "GET",
        path: buildQueryString("/hpc/tools", props),
        parameters: props
    };
}

export function listToolsByName(props: ListByNameProps): APICallParameters<ListByNameProps> {
    return {
        reloadId: Math.random(),
        method: "GET",
        path: buildQueryString(`/hpc/tools/${props.name}`, {itemsPerPage: props.itemsPerPage, page: props.page}),
        parameters: props
    };
}

export interface ListByTool {
    page: number;
    itemsPerPage: number;
    tool: string;
}

export function listApplicationsByTool(props: ListByTool): APICallParameters<ListByTool> {
    return {
        reloadId: Math.random(),
        method: "GET",
        path: buildQueryString(`/hpc/apps/byTool/${props.tool}`, {itemsPerPage: props.itemsPerPage, page: props.page}),
        parameters: props
    };
}

export interface CreateApplicationTagProps {
    applicationName: string;
    tags: string[];
}

export function createApplicationTag(props: CreateApplicationTagProps): APICallParameters<CreateApplicationTagProps> {
    return {
        reloadId: Math.random(),
        method: "POST",
        path: "/hpc/apps/createTag",
        payload: props,
        parameters: props
    };
}

export enum ApplicationAccessRight {
    LAUNCH = "LAUNCH"
}

export enum LicenseServerAccessRight {
    READ = "READ",
    READ_WRITE = "READ_WRITE"
}

export enum UserEntityType {
    USER = "USER",
    PROJECT_GROUP = "PROJECT_GROUP"
}

export interface AccessEntity {
    user: string | null;
    project: string | null;
    group: string | null;
}

export interface UserEntity {
    id: string;
    type: UserEntityType;
}

export interface ApplicationPermissionEntry {
    entity: UserEntity;
    permission: ApplicationAccessRight;
}

export interface UpdateApplicationPermissionEntry {
    entity: UserEntity;
    rights: ApplicationAccessRight;
    revoke: boolean;
}

export interface UpdateApplicationPermissionProps {
    applicationName: string;
    changes: UpdateApplicationPermissionEntry[];
}

export interface UpdateLicenseServerPermissionEntry {
    entity: AccessEntity;
    rights: LicenseServerAccessRight;
    revoke: boolean;
}

export interface UpdateLicenseServerPermissionProps {
    serverId: string;
    changes: UpdateLicenseServerPermissionEntry[];
}

export interface DeleteLicenseServerProps {
    id: string;
}

export interface LicenseServerTagProps {
    serverId: string;
    tag: string;
}

export function addLicenseServerTag(props: LicenseServerTagProps): APICallParameters<LicenseServerTagProps> {
    return {
        reloadId: Math.random(),
        method: "POST",
        path: "/app/license/tag/add",
        payload: props,
        parameters: props
    };
}

export function deleteLicenseServerTag(props: LicenseServerTagProps): APICallParameters<LicenseServerTagProps> {
    return {
        reloadId: Math.random(),
        method: "POST",
        path: "/app/license/tag/delete",
        payload: props,
        parameters: props
    };
}

export function updateLicenseServerPermission(props: UpdateLicenseServerPermissionProps): APICallParameters<UpdateLicenseServerPermissionProps> {
    return {
        reloadId: Math.random(),
        method: "POST",
        path: "/app/license/updateAcl",
        payload: props,
        parameters: props
    };
}

export function deleteLicenseServer(props: DeleteLicenseServerProps): APICallParameters<DeleteLicenseServerProps> {
    return {
        reloadId: Math.random(),
        method: "DELETE",
        path: "/app/license",
        payload: props,
        parameters: props
    };
}

export function updateApplicationPermission(props: UpdateApplicationPermissionProps): APICallParameters<UpdateApplicationPermissionProps> {
    return {
        reloadId: Math.random(),
        method: "POST",
        path: "/hpc/apps/updateAcl",
        payload: props,
        parameters: props
    };
}

export type DeleteApplicationTagProps = CreateApplicationTagProps;

export function deleteApplicationTag(props: DeleteApplicationTagProps): APICallParameters<DeleteApplicationTagProps> {
    return {
        reloadId: Math.random(),
        method: "POST",
        path: "/hpc/apps/deleteTag",
        payload: props,
        parameters: props
    };
}

export function machineTypes(): APICallParameters {
    return {
        reloadId: Math.random(),
        method: "GET",
        path: "/hpc/jobs/machine-types"
    };
}

export interface ListLicenseServersProps {
    tags: string[];
}

export function licenseServers(props: ListLicenseServersProps): APICallParameters {
    return {
        reloadId: Math.random(),
        method: "GET",
        path: "/api/app/license/list",
        payload: props,
        parameters: props
    };
}

export interface MachineReservation {
    name: string;
    cpu: number | null;
    memoryInGigs: number | null;
    gpu: number | null;
}

export type AppOrTool = "APPLICATION" | "TOOL";

export interface UploadLogoProps {
    type: AppOrTool;
    file: File;
    name: string;
}

export async function uploadLogo(props: UploadLogoProps): Promise<boolean> {
    const token = await Client.receiveAccessTokenOrRefreshIt();

    return new Promise((resolve) => {
        const request = new XMLHttpRequest();
        const context = props.type === "APPLICATION" ? "apps" : "tools";
        request.open("POST", Client.computeURL("/api", `/hpc/${context}/uploadLogo`));
        request.setRequestHeader("Authorization", `Bearer ${token}`);
        request.responseType = "text";
        request.setRequestHeader("Upload-Name", b64EncodeUnicode(props.name));
        request.onreadystatechange = () => {
            if (request.status !== 0) {
                if (!inSuccessRange(request.status)) {
                    let message: string = "Logo upload failed";
                    try {
                        message = JSON.parse(request.responseText).why;
                    } catch (e) {
                        // tslint:disable-next-line: no-console
                        console.log(e);
                        // Do nothing
                    }

                    snackbarStore.addFailure(message);
                    resolve(false);
                } else {
                    resolve(true);
                }
            }
        };

        request.send(props.file);
    });
}

export interface ClearLogoProps {
    type: AppOrTool;
    name: string;
}

export function clearLogo(props: ClearLogoProps): APICallParameters<ClearLogoProps> {
    const context = props.type === "APPLICATION" ? "apps" : "tools";
    return {
        reloadId: Math.random(),
        method: "DELETE",
        path: `/hpc/${context}/clearLogo/${props.name}`,
        parameters: props
    };
}

export interface UploadDocumentProps {
    type: AppOrTool;
    document: File;
}

export async function uploadDocument(props: UploadDocumentProps): Promise<boolean> {
    const token = await Client.receiveAccessTokenOrRefreshIt();

    return new Promise((resolve, reject) => {
        const request = new XMLHttpRequest();
        const context = props.type === "APPLICATION" ? "apps" : "tools";
        request.open("PUT", Client.computeURL("/api", `/hpc/${context}`));
        request.setRequestHeader("Authorization", `Bearer ${token}`);
        request.responseType = "text";
        request.onreadystatechange = () => {
            if (request.status !== 0) {
                if (!inSuccessRange(request.status)) {
                    let message: string = "Upload failed";
                    try {
                        message = JSON.parse(request.responseText).why;
                    } catch (e) {
                        console.log(e);
                        console.log(request.responseText);
                        // Do nothing
                    }
                    snackbarStore.addFailure(message);
                    resolve(false);
                } else {
                    resolve(true);
                }
            }
        };

        request.send(props.document);
    });
}

export interface ListJobsProps {
    itemsPerPage: number;
    page: number;
    order?: SortOrder;
    sortBy?: RunsSortBy;
    minTimestamp?: number;
    maxTimestamp?: number;
    filter?: JobState;
    application?: string;
    version?: string;
}

export function listJobs(props: ListJobsProps): APICallParameters<ListJobsProps> {
    return {
        reloadId: Math.random(),
        method: "GET",
        parameters: props,
        path: buildQueryString(
            "/hpc/jobs",
            props
        )
    };
}
