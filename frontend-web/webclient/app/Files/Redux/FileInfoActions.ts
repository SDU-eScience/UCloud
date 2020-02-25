import {Activity} from "Activity";
import {Client} from "Authentication/HttpClientInstance";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Page, PayloadAction, SetLoadingAction} from "Types";
import {activityStreamByPath} from "Utilities/ActivityUtilities";
import {errorMessageOrDefault} from "UtilityFunctions";
import {FILE_INFO_ERROR, RECEIVE_FILE_ACTIVITY, SET_FILE_INFO_LOADING} from "./FileInfoReducer";

export type FileInfoActions = FileInfoError | SetFileInfoLoading | ReceiveFileActivity;

export async function fetchFileActivity(path: string): Promise<ReceiveFileActivity | FileInfoError> {
    try {
        const {response} = await Client.get(activityStreamByPath(path));
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
