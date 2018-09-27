import { Upload } from "Uploader";
import { Action } from "redux"
import { SET_UPLOADER_CALLBACK, SET_UPLOADER_UPLOADS, SET_UPLOADER_VISIBLE } from "./UploaderReducer";

interface SetUploaderVisibleProps extends Action<typeof SET_UPLOADER_VISIBLE> { visible: boolean }
interface SetUploadsProps extends Action<typeof SET_UPLOADER_UPLOADS> { uploads: Upload[] }
interface SetUploaderCallbackProps extends Action<typeof SET_UPLOADER_CALLBACK> { callback: (string) => void }

export type UploaderActions = SetUploaderCallbackProps | SetUploadsProps | SetUploaderVisibleProps

export const setUploaderVisible = (visible: boolean): SetUploaderVisibleProps => ({
    type: SET_UPLOADER_VISIBLE,
    visible
})

export const setUploads = (uploads: Upload[]): SetUploadsProps => ({
    type: SET_UPLOADER_UPLOADS,
    uploads
});

export const setUploaderCallback = (callback: (string) => void): SetUploaderCallbackProps => ({
    type: SET_UPLOADER_CALLBACK,
    callback
});