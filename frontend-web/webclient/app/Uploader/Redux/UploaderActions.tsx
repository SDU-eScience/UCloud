import {Error, PayloadAction} from "Types";
import {Upload} from "Uploader";
import {
    SET_UPLOADER_CALLBACK,
    SET_UPLOADER_ERROR,
    SET_UPLOADER_LOADING,
    SET_UPLOADER_UPLOADS,
    SET_UPLOADER_VISIBLE
} from "./UploaderReducer";

type SetUploaderVisibleProps = PayloadAction<typeof SET_UPLOADER_VISIBLE, {visible: boolean, path: string}>;
type SetUploadsProps = PayloadAction<typeof SET_UPLOADER_UPLOADS, {uploads: Upload[]}>;
type SetUploaderCallbackProps = PayloadAction<typeof SET_UPLOADER_CALLBACK, {onFilesUploaded?: () => void}>;

export type UploaderActions = SetUploaderCallbackProps | SetUploadsProps | SetUploaderVisibleProps | Error<typeof SET_UPLOADER_ERROR> | SetLoading

export const setUploaderVisible = (visible: boolean, path: string): SetUploaderVisibleProps => ({
    type: SET_UPLOADER_VISIBLE,
    payload: {visible, path}
});

export const setUploads = (uploads: Upload[]): SetUploadsProps => ({
    type: SET_UPLOADER_UPLOADS,
    payload: {uploads}
});

export const setUploaderCallback = (onFilesUploaded?: () => void): SetUploaderCallbackProps => ({
    type: SET_UPLOADER_CALLBACK,
    payload: {onFilesUploaded}
});

export const setUploaderError = (error?: string): Error<typeof SET_UPLOADER_ERROR> => ({
    type: SET_UPLOADER_ERROR,
    payload: {error}
});

type SetLoading = PayloadAction<typeof SET_UPLOADER_LOADING, {loading: boolean}>
export const setLoading = (loading: boolean): SetLoading => ({
    type: SET_UPLOADER_LOADING,
    payload: {loading}
});