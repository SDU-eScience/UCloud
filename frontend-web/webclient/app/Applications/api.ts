import {JobState, RunsSortBy} from "Applications/index";
import {APICallParameters} from "Authentication/DataHook";
import {Cloud} from "Authentication/SDUCloudObject";
import {SortOrder} from "Files";
import {SnackType} from "Snackbar/Snackbars";
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

export type AppOrTool = "APPLICATION" | "TOOL";

export interface UploadLogoProps {
    type: AppOrTool;
    file: File;
    name: string;
}

export async function uploadLogo(props: UploadLogoProps): Promise<boolean> {
    const token = await Cloud.receiveAccessTokenOrRefreshIt();

    return new Promise((resolve, reject) => {
        const request = new XMLHttpRequest();
        const context = props.type === "APPLICATION" ? "apps" : "tools";
        request.open("POST", Cloud.computeURL("/api", `/hpc/${context}/uploadLogo`));
        request.setRequestHeader("Authorization", `Bearer ${token}`);
        request.responseType = "text";
        request.setRequestHeader("Upload-Name", b64EncodeUnicode(props.name));
        request.onreadystatechange = () => {
            if (request.status !== 0) {
                if (!inSuccessRange(request.status)) {
                    snackbarStore.addSnack({message: "Logo upload failed", type: SnackType.Failure});
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
