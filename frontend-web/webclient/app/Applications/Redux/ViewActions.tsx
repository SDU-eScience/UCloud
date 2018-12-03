import { Cloud } from "Authentication/SDUCloudObject";
import { PayloadAction, Page } from "Types";
import { Application } from "Applications";
import { isError, ErrorMessage, unwrap } from "Utilities/XHRUtils";

export enum Tag {
    SET_ERROR = "VIEW_APP_SET_ERROR",
    SET_LOADING = "VIEW_APP_SET_APP_LOADING",
    SET_PREV_LOADING = "VIEW_APP_SET_FAV_LOADING",
    RECEIVE_APP = "VIEW_APP_RECEIVE_APP",
    SET_PREV_ERROR = "VIEW_APP_SET_PREVIOUS_ERROR",
    RECEIVE_PREVIOUS_VERSIONS = "VIEW_APP_RECEIVE_PREVIOUS",
}

export type Type = 
    SetError | 
    SetLoading | 
    ReceiveApplication | 
    SetPreviousLoading | 
    ReceivePreviousVersions | 
    SetPreviousError;

type SetError = PayloadAction<typeof Tag.SET_ERROR, ErrorMessage>
type SetLoading = PayloadAction<typeof Tag.SET_LOADING, { loading: boolean }>
type SetPreviousLoading = PayloadAction<typeof Tag.SET_PREV_LOADING, { previousLoading: boolean }>
type ReceiveApplication = PayloadAction<typeof Tag.RECEIVE_APP, { application: Application }>
type ReceivePreviousVersions = PayloadAction<typeof Tag.RECEIVE_PREVIOUS_VERSIONS, { previous: Page<Application> }>
type SetPreviousError = PayloadAction<typeof Tag.SET_PREV_ERROR, { previousError: ErrorMessage }>

export async function fetchApplication(name: string, version: string): Promise<ReceiveApplication | SetError> {
    const result = await unwrap(Cloud.get<Application>(`/hpc/apps/${encodeURIComponent(name)}/${encodeURIComponent(version)}`));
    if (isError(result)) return { type: Tag.SET_ERROR, payload: result as ErrorMessage };
    else return { type: Tag.RECEIVE_APP, payload: { application: result as Application } };
}

export async function fetchPreviousVersions(name: string): Promise<ReceivePreviousVersions | SetError> {
    const result = await unwrap(Cloud.get<Page<Application>>(`/hpc/apps/${encodeURIComponent(name)}`));
    if (isError(result)) return { type: Tag.SET_ERROR, payload: result as ErrorMessage };
    else return { type: Tag.RECEIVE_PREVIOUS_VERSIONS, payload: { previous: result as Page<Application> } };
}