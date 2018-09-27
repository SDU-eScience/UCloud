import { Upload } from "Uploader";
import { SET_UPLOADER_CALLBACK, SET_UPLOADER_UPLOADS, SET_UPLOADER_VISIBLE } from "./UploaderReducer";
import { PayloadAction } from "Types";

interface SetUploaderVisibleProps extends PayloadAction<typeof SET_UPLOADER_VISIBLE, { visible: boolean }> { }
interface SetUploadsProps extends PayloadAction<typeof SET_UPLOADER_UPLOADS, { uploads: Upload[] }> { }
interface SetUploaderCallbackProps extends PayloadAction<typeof SET_UPLOADER_CALLBACK, { onFilesUploaded: (string) => void }> { }

export type UploaderActions = SetUploaderCallbackProps | SetUploadsProps | SetUploaderVisibleProps

export const setUploaderVisible = (visible: boolean): SetUploaderVisibleProps => ({
    type: SET_UPLOADER_VISIBLE,
    payload: { visible }
});

export const setUploads = (uploads: Upload[]): SetUploadsProps => ({
    type: SET_UPLOADER_UPLOADS,
    payload: { uploads }
});

export const setUploaderCallback = (onFilesUploaded: (string) => void): SetUploaderCallbackProps => ({
    type: SET_UPLOADER_CALLBACK,
    payload: { onFilesUploaded }
});