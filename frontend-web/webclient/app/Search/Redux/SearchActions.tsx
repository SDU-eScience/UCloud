import {AdvancedSearchRequest as AppSearchRequest, FullAppInfo} from "Applications";
import {Client} from "Authentication/HttpClientInstance";
import {AdvancedSearchRequest, File} from "Files";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Page, PayloadAction} from "Types";
import {advancedSearchQuery} from "Utilities/ApplicationUtilities";
import {advancedFileSearch} from "Utilities/FileUtilities";
import {errorMessageOrDefault} from "UtilityFunctions";
import * as SSActionTypes from "./SearchReducer";

export type SimpleSearchActions = SetFilesLoading | SetApplicationsLoading | SetProjectsLoading | ReceiveFiles |
    ReceiveApplications | SetErrorMessage | SetSearchType;

type SetFilesLoading = PayloadAction<typeof SSActionTypes.SET_SIMPLE_FILES_LOADING, {filesLoading: boolean}>;
export const setFilesLoading = (loading: boolean): SetFilesLoading => ({
    type: SSActionTypes.SET_SIMPLE_FILES_LOADING,
    payload: {filesLoading: loading}
});

type SetApplicationsLoading =
    PayloadAction<typeof SSActionTypes.SET_SIMPLE_APPLICATIONS_LOADING, {applicationsLoading: boolean}>;
export const setApplicationsLoading = (loading: boolean): SetApplicationsLoading => ({
    type: SSActionTypes.SET_SIMPLE_APPLICATIONS_LOADING,
    payload: {applicationsLoading: loading}
});

type SetProjectsLoading = PayloadAction<typeof SSActionTypes.SET_SIMPLE_PROJECTS_LOADING, {projectsLoading: boolean}>;
export const setProjectsLoading = (loading: boolean): SetProjectsLoading => ({
    type: SSActionTypes.SET_SIMPLE_PROJECTS_LOADING,
    payload: {projectsLoading: loading}
});


export async function searchFiles(request: AdvancedSearchRequest): Promise<any> {
    try {
        const {response} = await Client.post(advancedFileSearch, request);
        return receiveFiles(response);
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred searching for files\n"), false);
        return setErrorMessage({filesLoading: false});
    }
}

export async function searchApplications(body: AppSearchRequest): Promise<any> {
    try {
        const {response} = await Client.post(advancedSearchQuery, body);
        return receiveApplications(response);
    } catch (e) {
        snackbarStore.addFailure("An error occurred searching for applications", false);
        return setErrorMessage({applicationsLoading: false});
    }
}

type ReceiveFiles =
    PayloadAction<typeof SSActionTypes.RECEIVE_SIMPLE_FILES_PAGE, {files: Page<File>, filesLoading: false}>;
export const receiveFiles = (files: Page<File>): ReceiveFiles => ({
    type: SSActionTypes.RECEIVE_SIMPLE_FILES_PAGE,
    payload: {files, filesLoading: false}
});

export type ReceiveApplications = PayloadAction<
    typeof SSActionTypes.RECEIVE_SIMPLE_APPLICATIONS_PAGE, {applications: Page<FullAppInfo>, applicationsLoading: false}
>;
export const receiveApplications = (applications: Page<FullAppInfo>): ReceiveApplications => ({
    type: SSActionTypes.RECEIVE_SIMPLE_APPLICATIONS_PAGE,
    payload: {applications, applicationsLoading: false}
});

type SetSearchType = PayloadAction<typeof SSActionTypes.SET_SIMPLE_SEARCH_SEARCH, {search: string}>;
export const setSearch = (search: string): SetSearchType => ({
    type: SSActionTypes.SET_SIMPLE_SEARCH_SEARCH,
    payload: {search}
});

type SetErrorMessage = PayloadAction<typeof SSActionTypes.SET_SIMPLE_SEARCH_ERROR, {error?: string} & LoadingPanes>;
export const setErrorMessage = (loading?: LoadingPanes): SetErrorMessage => ({
    type: SSActionTypes.SET_SIMPLE_SEARCH_ERROR,
    payload: {...loading}
});

interface LoadingPanes {
    filesLoading?: boolean;
    applicationsLoading?: boolean;
    projectsLoading?: boolean;
}
