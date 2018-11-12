import { Cloud } from "Authentication/SDUCloudObject";
import { failureNotification, inSuccessRange } from "UtilityFunctions";
import { STATUS_CODES } from "http";
import { Sensitivity } from "DefaultObjects";

export const multipartUpload = async (location: string, file: File, sensitivity?: Sensitivity, onProgress?: (e: ProgressEvent) => void, onError?: (error: string) => void): Promise<XMLHttpRequest> => {
    const newFile = new File([file], "ignored");
    const token = await Cloud.receiveAccessTokenOrRefreshIt();
    let formData = new FormData();
    formData.append("location", location);
    if (sensitivity) formData.append("sensitivity", sensitivity);
    formData.append("upload", newFile);
    let request = new XMLHttpRequest();
    request.open("POST", "/api/files/upload");
    request.onreadystatechange = () => {
        if (!inSuccessRange(request.status))
            !!onError ? onError(`Upload failed: ${statusToError(request.status)}`) :
                failureNotification(statusToError(request.status))
    }
    request.setRequestHeader("Authorization", `Bearer ${token}`);
    request.upload.onprogress = (e) => {
        if (!!onProgress)
            onProgress(e);
    };
    request.responseType = "text";
    request.send(formData);
    return request;

    /* return fetch("/api/upload/bulk", {
        headers: { "Authorization", `Bearer ${token}`},
        method: "POST",
        credentials: "same-origin",
        body: formData
    }); */
}

export const bulkUpload = async (location: string, file: File, policy: BulkUploadPolicy, onProgress?: (e: ProgressEvent) => void, onError?: (error: string) => void): Promise<XMLHttpRequest> => {
    const newFile = new File([file], "ignored");
    const token = await Cloud.receiveAccessTokenOrRefreshIt();
    const format = "tgz";
    let formData = new FormData();
    formData.append("location", location);
    formData.append("format", format);
    formData.append("policy", policy);
    /* formData.append("sensitivity", "sensitive"); */
    formData.append("upload", newFile);
    let request = new XMLHttpRequest();



    request.open("POST", "/api/files/upload/bulk");
    request.onreadystatechange = () => {
        if (!inSuccessRange(request.status))
            !!onError ? onError(`Upload failed: ${statusToError(request.status)}`) :
                failureNotification(statusToError(request.status))
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