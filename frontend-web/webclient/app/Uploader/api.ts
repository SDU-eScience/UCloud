import { Cloud } from "Authentication/SDUCloudObject";

export const multipartUpload = (location: string, file: File, onProgress?: (e: ProgressEvent) => void): Promise<XMLHttpRequest> => {
    return Cloud.receiveAccessTokenOrRefreshIt().then(token => {
        let formData = new FormData();
        formData.append("location", location);
        formData.append("upload", file);

        let request = new XMLHttpRequest();
        request.open("POST", "/api/upload");
        request.setRequestHeader("Authorization", `Bearer ${token}`);
        request.upload.onprogress = (e) => {
            if (!!onProgress) onProgress(e);
        };
        request.responseType = "text";
        request.send(formData);
        return request;
    });
}

export const bulkUpload = (location: string, file: File, policy: BulkUploadPolicy, onProgress?: (e: ProgressEvent) => void): Promise<XMLHttpRequest> => {
    return Cloud.receiveAccessTokenOrRefreshIt().then(token => {
        const format = "tgz";

        let formData = new FormData();
        formData.append("path", location);
        formData.append("format", format);
        formData.append("policy", policy);
        formData.append("upload", file);

        let request = new XMLHttpRequest();
        request.open("POST", "/api/upload/bulk");
        request.setRequestHeader("Authorization", `Bearer ${token}`);
        request.upload.onprogress = (e) => {
            if (!!onProgress) onProgress(e);
        };
        request.responseType = "text";
        request.send(formData);
        return request;
    });
}

export enum BulkUploadPolicy {
    OVERWRITE = "OVERWRITE",
    RENAME = "RENAME",
    REJECT = "REJECT"
}