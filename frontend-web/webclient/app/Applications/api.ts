import {Client} from "Authentication/HttpClientInstance";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {b64EncodeUnicode} from "Utilities/XHRUtils";
import {inSuccessRange} from "UtilityFunctions";

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

                    snackbarStore.addFailure(message, false);
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
