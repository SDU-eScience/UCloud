import { Cloud } from "Authentication/SDUCloudObject";
import { failureNotification, inSuccessRange } from "UtilityFunctions";
import { STATUS_CODES } from "http";

export const multipartUpload = async (location: string, file: File, onProgress?: (e: ProgressEvent) => void, onError?: (error: string) => void): Promise<XMLHttpRequest> => {
    const token = await Cloud.receiveAccessTokenOrRefreshIt();
    let formData = new FormData();
    formData.append("location", location);
    /* formData.append("sensitivity", "sensitive"); */
    formData.append("upload", file);
    let request = new XMLHttpRequest();
    request.open("POST", "/api/upload");
    request.onreadystatechange = () => {
        if (!inSuccessRange(request.status))
            !!onError ? onError(`Upload failed: ${statusToError(request.status)}`) : failureNotification(statusToError(request.status))
    }
    request.setRequestHeader("Authorization", `Bearer ${token}`);
    request.upload.onprogress = (e) => {
        if (!!onProgress)
            onProgress(e);
    };
    request.responseType = "text";
    request.send(formData);
    return request;
}

export const bulkUpload = async (location: string, file: File, policy: BulkUploadPolicy, onProgress?: (e: ProgressEvent) => void, onError?: (error: string) => void): Promise<XMLHttpRequest> => {
    const token = await Cloud.receiveAccessTokenOrRefreshIt();
    const format = "tgz";
    let formData = new FormData();
    formData.append("path", location);
    formData.append("format", format);
    formData.append("policy", policy);
    /* formData.append("sensitivity", "sensitive"); */
    formData.append("upload", file);
    let request = new XMLHttpRequest();
    request.open("POST", "/api/upload/bulk");
    request.onreadystatechange = () => {
        if (!inSuccessRange(request.status))
            !!onError ? onError(`Upload failed: ${statusToError(request.status)}`) : failureNotification(statusToError(request.status))
    }
    request.setRequestHeader("Authorization", `Bearer ${token}`);
    request.upload.onprogress = (e) => {
        if (!!onProgress)
            onProgress(e);
    };

    request.responseType = "text";
    request.send(formData);
    return request;
}

function statusToError(status: number) {
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
            return "An error ocurred."
    }
}

export enum BulkUploadPolicy {
    OVERWRITE = "OVERWRITE",
    RENAME = "RENAME",
    REJECT = "REJECT"
}