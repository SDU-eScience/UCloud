import {Cloud} from "Authentication/SDUCloudObject";
import {statFileQuery} from "Utilities/FileUtilities";
import {FILE_PREVIEW_RECEIVE_FILE, FILE_PREVIEW_SET_ERROR} from "./FilePreviewReducer";

export const fetchPreviewFile = (path: string) =>
    Cloud.get<File>(statFileQuery(path))
        .then(({response}) => receiveFile(response))
        .catch(({request}: {request: XMLHttpRequest}) => setFilePreviewError(request.statusText));

export const setFilePreviewError = (error?: string) => ({
    type: FILE_PREVIEW_SET_ERROR,
    payload: {error}
});

const receiveFile = (file: File) => ({
    type: FILE_PREVIEW_RECEIVE_FILE,
    payload: {file}
});
