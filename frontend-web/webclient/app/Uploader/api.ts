import {Client} from "Authentication/HttpClientInstance";
import {Sensitivity} from "DefaultObjects";
import {STATUS_CODES} from "http";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {b64EncodeUnicode} from "Utilities/XHRUtils";
import {inSuccessRange} from "UtilityFunctions";

const timeBetweenUpdates = 150;

interface UploadArgs {
    location: string;
    file: File;
    sensitivity: Sensitivity;
    policy: UploadPolicy;
    onProgress?: (e: ProgressEvent) => void;
    onError?: (error: string) => void;
}

export const multipartUpload = async ({
    location,
    file,
    sensitivity,
    policy,
    onProgress,
    onError
}: UploadArgs): Promise<XMLHttpRequest> => {
    const token = await Client.receiveAccessTokenOrRefreshIt();

    const request = new XMLHttpRequest();
    request.open("POST", Client.computeURL("/api", "/files/upload/file"));
    request.onreadystatechange = () => {
        if (!inSuccessRange(request.status) && request.status !== 0) {
            let errorMessage = statusToError(request.status);
            try {
                const why = JSON.parse(request.responseText).why
                if (why && typeof why === "string") errorMessage = why;
            } catch (e) {
                // Ignored
            }

            !!onError ? onError(`Upload failed: ${errorMessage}`) :
                snackbarStore.addFailure(errorMessage, true);
        }
    };
    request.setRequestHeader("Authorization", `Bearer ${token}`);
    let nextProgressUpdate = new Date().getTime();
    request.upload.onprogress = (e: ProgressEvent) => {
        if (!!onProgress) {
            const now = new Date().getTime();
            if (nextProgressUpdate < now || e.loaded / e.total === 1) {
                nextProgressUpdate = now + timeBetweenUpdates;
                onProgress(e);
            }
        }
    };
    request.responseType = "text";
    request.setRequestHeader("Upload-Location", b64EncodeUnicode(location));
    if (sensitivity !== "INHERIT") request.setRequestHeader("Upload-Sensitivity", b64EncodeUnicode(sensitivity));
    request.setRequestHeader("Upload-Policy", b64EncodeUnicode(policy));
    request.setRequestHeader("content-type", "application/octet-stream");
    request.send(file);
    return request;
};

export const bulkUpload = async ({
    location,
    file,
    sensitivity,
    policy,
    onProgress,
    onError
}: UploadArgs): Promise<XMLHttpRequest> => {
    const token = await Client.receiveAccessTokenOrRefreshIt();
    const format = formatFromFileName(file.name);

    const request = new XMLHttpRequest();
    request.open("POST", Client.computeURL("/api", "/files/upload/archive"));
    request.onreadystatechange = () => {
        if (!inSuccessRange(request.status))
            !!onError ? onError(`Upload failed: ${statusToError(request.status)}`) :
                snackbarStore.addFailure(statusToError(request.status), true);
    };
    request.setRequestHeader("Authorization", `Bearer ${token}`);
    let nextProgressUpdate = new Date().getTime();
    request.upload.onprogress = (e: ProgressEvent) => {
        if (!!onProgress) {
            const now = new Date().getTime();
            if (nextProgressUpdate < now || e.loaded / e.total === 1) {
                nextProgressUpdate = now + timeBetweenUpdates;
                onProgress(e);
            }
        }
    };
    request.responseType = "text";
    if (sensitivity !== "INHERIT") request.setRequestHeader("Upload-Sensitivity", b64EncodeUnicode(sensitivity));
    request.setRequestHeader("Upload-Policy", b64EncodeUnicode(policy));
    request.setRequestHeader("Upload-Location", b64EncodeUnicode(location));
    request.setRequestHeader("Upload-Format", b64EncodeUnicode(format));
    request.setRequestHeader("Upload-Name", b64EncodeUnicode(file.name));
    request.send(file);
    return request;
};

function statusToError(status: number): string {
    switch (STATUS_CODES[status]) {
        case "Expectation Failed": {
            return "Expectation Failed";
        }
        case "Bad Request": {
            return "Bad Request";
        }
        case "Not Found": {
            return "Folder uploaded to does not exist";
        }
        case "Conflict": {
            return "Conflict";
        }
        case "Forbidden": {
            return "Forbidden";
        }
        case "Internal Server Error": {
            return "Internal Server Error Occurred";
        }
        default:
            return "An error occurred.";
    }
}

function formatFromFileName(type: string): string {
    if (type.endsWith(".zip"))
        return "zip";
    else if (type.endsWith(".tar.gz"))
        return "tgz";
    return "";
}

export enum UploadPolicy {
    OVERWRITE = "OVERWRITE",
    MERGE = "MERGE",
    RENAME = "RENAME",
    REJECT = "REJECT"
}
