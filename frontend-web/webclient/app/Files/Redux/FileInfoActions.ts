import {Activity} from "Activity";
import {Cloud} from "Authentication/SDUCloudObject";
import {File} from "Files";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Page, PayloadAction, SetLoadingAction} from "Types";
import {activityStreamByPath} from "Utilities/ActivityUtilities";
import {statFileQuery} from "Utilities/FileUtilities";
import {errorMessageOrDefault} from "UtilityFunctions";
import {FILE_INFO_ERROR, RECEIVE_FILE_ACTIVITY, RECEIVE_FILE_STAT, SET_FILE_INFO_LOADING, } from "./FileInfoReducer";

export type FileInfoActions = ReceiveFileStat | FileInfoError | SetFileInfoLoading | ReceiveFileActivity;

export async function fetchFileActivity(path: string): Promise<ReceiveFileActivity | FileInfoError> {
    try {
        const {response} = await Cloud.get(activityStreamByPath(path));
        return receiveFileActivity(response);
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred fetching activity."));
        return fileInfoError();
    }
}

type ReceiveFileActivity = PayloadAction<typeof RECEIVE_FILE_ACTIVITY, {activity: Page<Activity>, loading: false}>;
const receiveFileActivity = (activity: Page<Activity>): ReceiveFileActivity => ({
    type: RECEIVE_FILE_ACTIVITY,
    payload: {activity, loading: false}
});

export async function fetchFileStat(path: string): Promise<ReceiveFileStat | FileInfoError> {
    try {
        const {response} = await Cloud.get<File>(statFileQuery(path));
        return receiveFileStat(response);
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred fetching file"));
        return fileInfoError();
    }
}

type ReceiveFileStat = PayloadAction<typeof RECEIVE_FILE_STAT, {file: File, loading: false}>;
export const receiveFileStat = (file: File): ReceiveFileStat => ({
    type: RECEIVE_FILE_STAT,
    payload: {file, loading: false}
});

type FileInfoError = PayloadAction<typeof FILE_INFO_ERROR, {loading: false, error?: string, file: undefined}>;
export const fileInfoError = (): FileInfoError => ({
    type: FILE_INFO_ERROR,
    payload: {loading: false, file: undefined}
});

type SetFileInfoLoading = SetLoadingAction<typeof SET_FILE_INFO_LOADING>;
export const setLoading = (loading: boolean): SetFileInfoLoading => ({
    type: SET_FILE_INFO_LOADING,
    payload: {loading}
});
