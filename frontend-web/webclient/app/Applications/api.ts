import {Client} from "@/Authentication/HttpClientInstance";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {b64EncodeUnicode} from "@/Utilities/XHRUtils";
import {inSuccessRange} from "@/UtilityFunctions";
import {LogoType, appLogoCache, toolLogoCache} from "@/Applications/AppToolLogo";
import {compute} from "@/UCloud";
import ApplicationSummaryWithFavorite = compute.ApplicationSummaryWithFavorite;
import {buildQueryString} from "@/Utilities/URIUtilities";

export interface ApplicationGroup {
    id: number,
    title: string,
    description?: string,
    defaultApplication?: string,
    tags: string[]
}

export interface CreateGroupRequest {
    title: string;
}

export interface CreateGroupResponse {}

export interface RetrieveGroupRequest {
    id?: string;
    name?: string;
}

export interface RetrieveGroupResponse {
    group: ApplicationGroup;
    applications: ApplicationSummaryWithFavorite[];
}


export interface SetGroupRequest {
    groupId?: number,
    applicationName: string
}

export interface SetGroupResponse{}

export interface UpdateFlavorRequest {
    applicationName: string,
    flavorName: string
}

export interface UpdateFlavorResponse{}

export interface ListGroupsRequest {}

export interface UpdateGroupRequest {
    id: number,
    title: string,
    description?: string,
    logo?: Blob,
    defaultApplication?: string,
    tags: string[]
}

export interface UpdateGroupResponse {}

export interface DeleteGroupRequest {
    id: number,
}

export interface DeleteGroupResponse {}

export function retrieveGroup(
    request: RetrieveGroupRequest 
): APICallParameters<RetrieveGroupRequest, RetrieveGroupResponse> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/hpc/apps" + "/group", {id: request.id, name: request.name}),
        parameters: request,
        reloadId: Math.random(),
    }
}

export function createGroup(
    request: CreateGroupRequest 
): APICallParameters<CreateGroupRequest, CreateGroupResponse> {
    return {
        context: "",
        method: "POST",
        path: buildQueryString("/api/hpc/apps" + "/group", {}),
        parameters: request,
        reloadId: Math.random(),
        payload: request
    }
}

export function updateFlavor(
    request: UpdateFlavorRequest 
): APICallParameters<UpdateFlavorRequest, UpdateFlavorResponse> {
    return {
        context: "",
        method: "POST",
        path: buildQueryString("/api/hpc/apps" + "/updateFlavor", {}),
        parameters: request,
        reloadId: Math.random(),
        payload: request
    }
}



export function setGroup(
    request: SetGroupRequest 
): APICallParameters<SetGroupRequest, SetGroupResponse> {
    return {
        context: "",
        method: "POST",
        path: buildQueryString("/api/hpc/apps" + "/group/set", {}),
        parameters: request,
        reloadId: Math.random(),
        payload: request
    }
}

export function listGroups(
    request: ListGroupsRequest
): APICallParameters<ListGroupsRequest, ApplicationGroup[]> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/hpc/apps" + "/groups", {}),
        parameters: request,
        reloadId: Math.random(),
    }
}

export function updateGroup(
    request: UpdateGroupRequest
): APICallParameters<UpdateGroupRequest, UpdateGroupResponse> {
    return {
        context: "",
        method: "POST",
        path: buildQueryString("/api/hpc/apps" + "/group/update", {}),
        parameters: request,
        reloadId: Math.random(),
        payload: request
    }
}

export function deleteGroup(
    request: DeleteGroupRequest
): APICallParameters<DeleteGroupRequest, DeleteGroupResponse> {
    return {
        context: "",
        method: "DELETE",
        path: buildQueryString("/api/hpc/apps" + "/group", {}),
        parameters: request,
        reloadId: Math.random(),
        payload: request
    }
}

export interface UploadLogoProps {
    type: LogoType;
    file: File;
    name: string;
}

function getContext(type: LogoType): string {
    switch(type) {
        case "APPLICATION": {
            return "apps";
        }
        case "TOOL": {
            return "tools";
        }
        case "GROUP": {
            return "apps/group";
        }
    }
}


export async function uploadLogo(props: UploadLogoProps): Promise<boolean> {
    const token = await Client.receiveAccessTokenOrRefreshIt();

    return new Promise((resolve) => {
        const request = new XMLHttpRequest();
        const context = getContext(props.type);
        request.open("POST", Client.computeURL("/api", `/hpc/${context}/uploadLogo`));
        request.setRequestHeader("Authorization", `Bearer ${token}`);
        request.responseType = "text";
        request.setRequestHeader("Upload-Name", b64EncodeUnicode(props.name));
        request.onreadystatechange = () => {
            if (request.status !== 0) {
                if (!inSuccessRange(request.status)) {
                    let message = "Logo upload failed";
                    try {
                        message = JSON.parse(request.responseText).why;
                    } catch (e) {
                        // tslint:disable-next-line: no-console
                        console.log(e);
                        // Do nothing
                    }

                    snackbarStore.addFailure(message, false);
                    resolve(false);
                } else {
                    if (props.type === "APPLICATION") appLogoCache.forget(props.name);
                    else toolLogoCache.forget(props.name);
                    resolve(true);
                }
            }
        };

        request.send(props.file);
    });
}

export interface ClearLogoProps {
    type: LogoType;
    name: string;
}

export function clearLogo(props: ClearLogoProps): APICallParameters<ClearLogoProps> {
    const context = getContext(props.type);
    return {
        reloadId: Math.random(),
        method: "DELETE",
        path: `/hpc/${context}/clearLogo`,
        parameters: props,
        payload: props
    };
}



export interface UpdateLandingProps {
    document: File;
}

export async function updateLanding(props: UpdateLandingProps): Promise<boolean> {
    const token = await Client.receiveAccessTokenOrRefreshIt();

    return new Promise(resolve => {
        const request = new XMLHttpRequest();
        request.open("PUT", Client.computeURL("/api", `/hpc/apps/updateLanding`));
        request.setRequestHeader("Authorization", `Bearer ${token}`);
        request.responseType = "text";
        request.onreadystatechange = () => {
            if (request.status !== 0) {
                if (!inSuccessRange(request.status)) {
                    let message = "Upload failed";
                    try {
                        message = JSON.parse(request.responseText).why;
                    } catch (e) {
                        console.log(e);
                        console.log(request.responseText);
                        // Do nothing
                    }
                    snackbarStore.addFailure(message, false);
                    resolve(false);
                } else {
                    resolve(true);
                }
            }
        };

        request.send(props.document);
    });
}

export interface UpdateOverviewProps {
    document: File;
}

export async function updateOverview(props: UpdateOverviewProps): Promise<boolean> {
    const token = await Client.receiveAccessTokenOrRefreshIt();

    return new Promise(resolve => {
        const request = new XMLHttpRequest();
        request.open("PUT", Client.computeURL("/api", `/hpc/apps/updateOverview`));
        request.setRequestHeader("Authorization", `Bearer ${token}`);
        request.responseType = "text";
        request.onreadystatechange = () => {
            if (request.status !== 0) {
                if (!inSuccessRange(request.status)) {
                    let message = "Upload failed";
                    try {
                        message = JSON.parse(request.responseText).why;
                    } catch (e) {
                        console.log(e);
                        console.log(request.responseText);
                        // Do nothing
                    }
                    snackbarStore.addFailure(message, false);
                    resolve(false);
                } else {
                    resolve(true);
                }
            }
        };

        request.send(props.document);
    });
}

export interface UploadDocumentProps {
    type: LogoType;
    document: File;
}

export async function uploadDocument(props: UploadDocumentProps): Promise<boolean> {
    const token = await Client.receiveAccessTokenOrRefreshIt();

    return new Promise(resolve => {
        const request = new XMLHttpRequest();
        const context = props.type === "APPLICATION" ? "apps" : "tools";
        request.open("PUT", Client.computeURL("/api", `/hpc/${context}`));
        request.setRequestHeader("Authorization", `Bearer ${token}`);
        request.responseType = "text";
        request.onreadystatechange = () => {
            if (request.status !== 0) {
                if (!inSuccessRange(request.status)) {
                    let message = "Upload failed";
                    try {
                        message = JSON.parse(request.responseText).why;
                    } catch (e) {
                        console.log(e);
                        console.log(request.responseText);
                        // Do nothing
                    }
                    snackbarStore.addFailure(message, false);
                    resolve(false);
                } else {
                    resolve(true);
                }
            }
        };

        request.send(props.document);
    });
}
