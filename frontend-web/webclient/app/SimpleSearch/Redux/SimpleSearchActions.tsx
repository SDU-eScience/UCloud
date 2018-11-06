import * as SSActionTypes from "./SimpleSearchReducer";
import { Cloud } from "Authentication/SDUCloudObject";
import { fileSearchQuery } from "Utilities/FileUtilities";
import { Page, PayloadAction } from "Types";
import { File } from "Files";
import { Application } from "Applications";
import { ProjectMetadata, simpleSearch } from "Metadata/api";
import { hpcApplicationsQuery, hpcApplicationsSearchQuery } from "Utilities/ApplicationUtilities";

export type SimpleSearchActions = SetFilesLoading | SetApplicationsLoading | SetProjectsLoading | ReceiveFiles |
    ReceiveApplications | ReceiveProjects | SetErrorMessage | SetSearchType

type SetFilesLoading = PayloadAction<typeof SSActionTypes.SET_SIMPLE_FILES_LOADING, { filesLoading: boolean }>
export const setFilesLoading = (loading: boolean): SetFilesLoading => ({
    type: SSActionTypes.SET_SIMPLE_FILES_LOADING,
    payload: { filesLoading: loading }
});

type SetApplicationsLoading = PayloadAction<typeof SSActionTypes.SET_SIMPLE_APPLICATIONS_LOADING, { applicationsLoading: boolean }>
export const setApplicationsLoading = (loading: boolean): SetApplicationsLoading => ({
    type: SSActionTypes.SET_SIMPLE_APPLICATIONS_LOADING,
    payload: { applicationsLoading: loading }
});

type SetProjectsLoading = PayloadAction<typeof SSActionTypes.SET_SIMPLE_PROJECTS_LOADING, { projectsLoading: boolean }>
export const setProjectsLoading = (loading: boolean): SetProjectsLoading => ({
    type: SSActionTypes.SET_SIMPLE_PROJECTS_LOADING,
    payload: { projectsLoading: loading }
});


export const searchFiles = (search: string, pageNumber: number, itemsPerPage: number): Promise<any> =>
    Cloud.get(fileSearchQuery(search, pageNumber, itemsPerPage))
        .then(({ response }) => receiveFiles(response))
        .catch(_ => setErrorMessage("An error occurred searching for files\n"));

export const searchApplications = (query: string, page: number, itemsPerPage: number): Promise<any> =>
    Cloud.get(hpcApplicationsSearchQuery(query, page, itemsPerPage))
        .then(({ response }) => receiveApplications(response))
        .catch(_ => setErrorMessage("An error occurred searching for applications\n"));


export const searchProjects = (query: string, page: number, itemsPerPage: number): Promise<any> =>
    simpleSearch(query, page, itemsPerPage)
        .then(response => receiveProjects(response))
        .catch(_ => setErrorMessage("An error occurred searching for projects\n"));


type ReceiveFiles = PayloadAction<typeof SSActionTypes.RECEIVE_SIMPLE_FILES_PAGE, { files: Page<File>, filesLoading: false }>
export const receiveFiles = (files: Page<File>): ReceiveFiles => ({
    type: SSActionTypes.RECEIVE_SIMPLE_FILES_PAGE,
    payload: { files, filesLoading: false }
});

type ReceiveApplications = PayloadAction<typeof SSActionTypes.RECEIVE_SIMPLE_APPLICATIONS_PAGE, { applications: Page<Application>, applicationsLoading: false }>
export const receiveApplications = (applications: Page<Application>): ReceiveApplications => ({
    type: SSActionTypes.RECEIVE_SIMPLE_APPLICATIONS_PAGE,
    payload: { applications, applicationsLoading: false }
});

type ReceiveProjects = PayloadAction<typeof SSActionTypes.RECEIVE_SIMPLE_PROJECTS_PAGE, { projects: Page<ProjectMetadata>, projectsLoading: false }>
export const receiveProjects = (projects: Page<ProjectMetadata>): ReceiveProjects => ({
    type: SSActionTypes.RECEIVE_SIMPLE_PROJECTS_PAGE,
    payload: { projects, projectsLoading: false }
});

type SetSearchType = PayloadAction<typeof SSActionTypes.SET_SIMPLE_SEARCH_SEARCH, { search: string }>
export const setSearch = (search: string): SetSearchType => ({
    type: SSActionTypes.SET_SIMPLE_SEARCH_SEARCH,
    payload: { search }
})

type SetErrorMessage = PayloadAction<typeof SSActionTypes.SET_SIMPLE_SEARCH_ERROR, { error?: string }>
export const setErrorMessage = (error?: string): SetErrorMessage => ({
    type: SSActionTypes.SET_SIMPLE_SEARCH_ERROR,
    payload: { error }
});