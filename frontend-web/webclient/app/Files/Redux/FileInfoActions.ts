import { Cloud } from "Authentication/SDUCloudObject";
import { statFileQuery } from "Utilities/FileUtilities";
import { PayloadAction, Error, SetLoadingAction, Page, ReceivePage } from "Types";
import { FILE_INFO_ERROR, RECEIVE_FILE_STAT, SET_FILE_INFO_LOADING, RECEIVE_FILE_ACTIVITY } from "./FileInfoReducer";
import { Activity } from "Activity";
import { File } from "Files";
import { activityStreamByPath } from "Utilities/ActivityUtilities";

export type FileInfoActions = ReceiveFileStat | FileInfoError | SetFileInfoLoading | ReceiveFileActivity

export const fetchFileActivity = (path: string): Promise<ReceiveFileActivity | FileInfoError> =>
    Cloud.get(activityStreamByPath(path))
        .then(({ response }) => receiveFileActivity(response))
        .catch(({ request }: { request: XMLHttpRequest }) => fileInfoError(request.statusText));

type ReceiveFileActivity = PayloadAction<typeof RECEIVE_FILE_ACTIVITY, { activity: Page<Activity>, loading: false }>
const receiveFileActivity = (activity: Page<Activity>): ReceiveFileActivity => ({
    type: RECEIVE_FILE_ACTIVITY,
    payload: { activity, loading: false }
});

export const fetchFileStat = (path: string): Promise<ReceiveFileStat | FileInfoError> =>
    Cloud.get<File>(statFileQuery(path))
        .then(({ response }) => receiveFileStat(response))
        .catch(({ request }: { request: XMLHttpRequest }) => fileInfoError(request.statusText));

type ReceiveFileStat = PayloadAction<typeof RECEIVE_FILE_STAT, { file: File, loading: false }>
export const receiveFileStat = (file: File): ReceiveFileStat => ({
    type: RECEIVE_FILE_STAT,
    payload: { file, loading: false }
});

type FileInfoError = Error<typeof FILE_INFO_ERROR>;
const fileInfoError = (error?: string): FileInfoError => ({
    type: FILE_INFO_ERROR,
    payload: { error }
});

type SetFileInfoLoading = SetLoadingAction<typeof SET_FILE_INFO_LOADING>
export const setLoading = (loading: boolean): SetFileInfoLoading => ({
    type: SET_FILE_INFO_LOADING,
    payload: { loading }
})