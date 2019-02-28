import { Cloud } from "Authentication/SDUCloudObject";
import { failureNotification, inSuccessRange } from "UtilityFunctions";
import { STATUS_CODES } from "http";
import { Sensitivity } from "DefaultObjects";

const timeBetweenUpdates = 150;

export const multipartUpload = async (location: string, file: File, sensitivity: Sensitivity, policy: UploadPolicy, onProgress?: (e: ProgressEvent) => void, onError?: (error: string) => void): Promise<XMLHttpRequest> => {
    const newFile = new File([file], "ignored");
    const token = await Cloud.receiveAccessTokenOrRefreshIt();
    let formData = new FormData();
    formData.append("location", location);
    if (sensitivity !== "INHERIT") formData.append("sensitivity", sensitivity);
    formData.append("policy", policy);
    formData.append("upload", newFile);
    let request = new XMLHttpRequest();
    request.open("POST", "/api/files/upload");
    request.onreadystatechange = () => {
        if (!inSuccessRange(request.status) && request.status !== 0) {
            !!onError ? onError(`Upload failed: ${statusToError(request.status)}`) :
                failureNotification(statusToError(request.status))
        }
    }
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
    request.send(formData);
    return request;
}

export const bulkUpload = async (location: string, file: File, sensitivity: Sensitivity, policy: UploadPolicy, onProgress?: (e: ProgressEvent) => void, onError?: (error: string) => void): Promise<XMLHttpRequest> => {
    const newFile = new File([file], "ignored");
    const token = await Cloud.receiveAccessTokenOrRefreshIt();
    const format = formatFromType(file.type);
    let formData = new FormData();
    formData.append("location", location);
    formData.append("format", format);
    if (sensitivity !== "INHERIT") formData.append("sensitivity", sensitivity);
    formData.append("policy", policy);
    formData.append("upload", newFile);
    let request = new XMLHttpRequest();
    request.open("POST", "/api/files/upload/bulk");
    request.onreadystatechange = () => {
        if (!inSuccessRange(request.status))
            !!onError ? onError(`Upload failed: ${statusToError(request.status)}`) :
                failureNotification(statusToError(request.status))
    }
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

function formatFromType(type: string): string {
    switch (type) {
        case "application/zip":
            return "zip";
        case "application/x-gzip":
            return "tgz";
        default:
            return "";
    }
}

export enum UploadPolicy {
    OVERWRITE = "OVERWRITE",
    RENAME = "RENAME",
    REJECT = "REJECT"
}