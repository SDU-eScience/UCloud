import { Cloud } from "Authentication/SDUCloudObject";
import { DETAILED_APPS_SET_NAME, DETAILED_APPS_SET_VERSION, DETAILED_APPLICATION_SET_ERROR } from "./DetailedApplicationSearchReducer";
import { hpcApplicationsSearchQuery, hpcApplicationsTagSearchQuery } from "Utilities/ApplicationUtilities";
import { setErrorMessage, receiveApplications } from "Search/Redux/SearchActions";
import { PayloadAction } from "Types";

export type DetailedAppActions = SetAppVersionAction | SetAppNameAction | ClearError;

type SetAppVersionAction = PayloadAction<typeof DETAILED_APPS_SET_VERSION, { appVersion: string }>
export const setVersion = (appVersion: string): SetAppVersionAction => ({
    type: DETAILED_APPS_SET_VERSION,
    payload: { appVersion }
});

type SetAppNameAction = PayloadAction<typeof DETAILED_APPS_SET_NAME, { appName: string }>
export const setAppName = (appName: string): SetAppNameAction => ({
    type: DETAILED_APPS_SET_NAME,
    payload: { appName }
});

type ClearError = PayloadAction<typeof DETAILED_APPLICATION_SET_ERROR, { error?: string }>
export const setError = (error?: string): ClearError => ({
    type: DETAILED_APPLICATION_SET_ERROR,
    payload: { error }
});

// FIXME Does it make sense to have them here when the actions called are located elsewhere?
export const fetchApplicationPageFromName = (query: string, itemsPerPage: number, page: number) =>
    Cloud.get(hpcApplicationsSearchQuery(query, page, itemsPerPage))
        .then(({ response }) => receiveApplications(response))
        .catch(_ => setErrorMessage("An error occurred searching for applications\n", { applicationsLoading: false }));

export const fetchApplicationPageFromTag = (query: string, itemsPerPage: number, page: number) =>
    Cloud.get(hpcApplicationsTagSearchQuery(query, page, itemsPerPage))
        .then(({ response }) => receiveApplications(response))
        .catch(_ => setErrorMessage("An error occurred searching for applications\n", { applicationsLoading: false }));

// FIXME END