import * as SSActionTypes from "./SearchReducer";

export type SimpleSearchActions = SetApplicationsLoading | SetProjectsLoading |
    SetErrorMessage | SetSearchType;

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

type SetSearchType = PayloadAction<typeof SSActionTypes.SET_SIMPLE_SEARCH_SEARCH, {search: string}>;
export const setSearch = (search: string): SetSearchType => ({
    type: SSActionTypes.SET_SIMPLE_SEARCH_SEARCH,
    payload: {search}
});

export type SetErrorMessage = PayloadAction<typeof SSActionTypes.SET_SIMPLE_SEARCH_ERROR, {error?: string} & LoadingPanes>;
export const setErrorMessage = (loading?: LoadingPanes): SetErrorMessage => ({
    type: SSActionTypes.SET_SIMPLE_SEARCH_ERROR,
    payload: {...loading}
});

interface LoadingPanes {
    filesLoading?: boolean;
    applicationsLoading?: boolean;
    projectsLoading?: boolean;
}
