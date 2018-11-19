import { Upload } from "Uploader";
import { SET_UPLOADER_CALLBACK, SET_UPLOADER_UPLOADS, SET_UPLOADER_VISIBLE, SET_UPLOADER_ERROR } from "./UploaderReducer";
import { PayloadAction, Error } from "Types";

interface SetUploaderVisibleProps extends PayloadAction<typeof SET_UPLOADER_VISIBLE, { visible: boolean }> { }
interface SetUploadsProps extends PayloadAction<typeof SET_UPLOADER_UPLOADS, { uploads: Upload[] }> { }
interface SetUploaderCallbackProps extends PayloadAction<typeof SET_UPLOADER_CALLBACK, { onFilesUploaded: (str :string) => void }> { }

export type UploaderActions = SetUploaderCallbackProps | SetUploadsProps | SetUploaderVisibleProps | Error<typeof SET_UPLOADER_ERROR>

export const setUploaderVisible = (visible: boolean): SetUploaderVisibleProps => ({
    type: SET_UPLOADER_VISIBLE,
    payload: { visible }
});

export const setUploads = (uploads: Upload[]): SetUploadsProps => ({
    type: SET_UPLOADER_UPLOADS,
    payload: { uploads }
});

export const setUploaderCallback = (onFilesUploaded: (s: string) => void): SetUploaderCallbackProps => ({
    type: SET_UPLOADER_CALLBACK,
    payload: { onFilesUploaded }
});

export const setUploaderError = (error?: string): Error<typeof SET_UPLOADER_ERROR> => ({
    type: SET_UPLOADER_ERROR,
    payload: { error }
}) 