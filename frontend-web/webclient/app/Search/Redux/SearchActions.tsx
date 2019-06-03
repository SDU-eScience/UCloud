import * as SSActionTypes from "./SearchReducer";
import {Cloud} from "Authentication/SDUCloudObject";
import {Page, PayloadAction} from "Types";
import {File, AdvancedSearchRequest} from "Files";
import {WithAppFavorite, WithAppMetadata} from "Applications";
import {hpcApplicationsSearchQuery} from "Utilities/ApplicationUtilities";
import {advancedFileSearch} from "Utilities/FileUtilities";

export type SimpleSearchActions = SetFilesLoading | SetApplicationsLoading | SetProjectsLoading | ReceiveFiles |
    ReceiveApplications | SetErrorMessage | SetSearchType

type SetFilesLoading = PayloadAction<typeof SSActionTypes.SET_SIMPLE_FILES_LOADING, { filesLoading: boolean }>
export const setFilesLoading = (loading: boolean): SetFilesLoading => ({
    type: SSActionTypes.SET_SIMPLE_FILES_LOADING,
    payload: {filesLoading: loading}
});

type SetApplicationsLoading = PayloadAction<typeof SSActionTypes.SET_SIMPLE_APPLICATIONS_LOADING, { applicationsLoading: boolean }>
export const setApplicationsLoading = (loading: boolean): SetApplicationsLoading => ({
    type: SSActionTypes.SET_SIMPLE_APPLICATIONS_LOADING,
    payload: {applicationsLoading: loading}
});

type SetProjectsLoading = PayloadAction<typeof SSActionTypes.SET_SIMPLE_PROJECTS_LOADING, { projectsLoading: boolean }>
export const setProjectsLoading = (loading: boolean): SetProjectsLoading => ({
    type: SSActionTypes.SET_SIMPLE_PROJECTS_LOADING,
    payload: {projectsLoading: loading}
});


export const searchFiles = (request: AdvancedSearchRequest): Promise<any> =>
    Cloud.post(advancedFileSearch, request)
        .then(({response}) => receiveFiles(response))
        .catch(_ => setErrorMessage("An error occurred searching for files\n", {filesLoading: false}));

export const searchApplications = (query: string, page: number, itemsPerPage: number): Promise<any> =>
    Cloud.get(hpcApplicationsSearchQuery({query, page, itemsPerPage}))
        .then(({response}) => receiveApplications(response))
        .catch(_ => setErrorMessage("An error occurred searching for applications\n", {applicationsLoading: false}));

type ReceiveFiles = PayloadAction<typeof SSActionTypes.RECEIVE_SIMPLE_FILES_PAGE, { files: Page<File>, filesLoading: false }>
export const receiveFiles = (files: Page<File>): ReceiveFiles => ({
    type: SSActionTypes.RECEIVE_SIMPLE_FILES_PAGE,
    payload: {files, filesLoading: false}
});

type ReceiveApplications = PayloadAction<typeof SSActionTypes.RECEIVE_SIMPLE_APPLICATIONS_PAGE, { applications: Page<WithAppMetadata & WithAppFavorite>, applicationsLoading: false }>
export const receiveApplications = (applications: Page<WithAppMetadata & WithAppFavorite>): ReceiveApplications => ({
    type: SSActionTypes.RECEIVE_SIMPLE_APPLICATIONS_PAGE,
    payload: {applications, applicationsLoading: false}
});

type SetSearchType = PayloadAction<typeof SSActionTypes.SET_SIMPLE_SEARCH_SEARCH, { search: string }>
export const setSearch = (search: string): SetSearchType => ({
    type: SSActionTypes.SET_SIMPLE_SEARCH_SEARCH,
    payload: {search}
});

type SetErrorMessage = PayloadAction<typeof SSActionTypes.SET_SIMPLE_SEARCH_ERROR, { error?: string } & LoadingPanes>
export const setErrorMessage = (error?: string, loading?: LoadingPanes): SetErrorMessage => ({
    type: SSActionTypes.SET_SIMPLE_SEARCH_ERROR,
    payload: {error, ...loading}
});

interface LoadingPanes {
    filesLoading?: boolean
    applicationsLoading?: boolean
    projectsLoading?: boolean
}