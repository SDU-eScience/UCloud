import { Cloud } from "Authentication/SDUCloudObject";
import { DETAILED_APPS_SET_NAME, DETAILED_APPS_SET_VERSION, DETAILED_APPLICATIONS_RECEIVE_PAGE } from "./DetailedApplicationSearchReducer";
import { hpcApplicationsSearchQuery, hpcApplicationsTagSearchQuery } from "Utilities/ApplicationUtilities";
import { setErrorMessage } from "SimpleSearch/Redux/SimpleSearchActions";
import { PayloadAction } from "Types";

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


export const fetchApplicationPageFromName = (query: string, itemsPerPage: number, page: number) =>
    Cloud.get(hpcApplicationsSearchQuery(query, page, itemsPerPage))
        .then(({ response }) => receiveApplicationPage(response))
        .catch(_ => setErrorMessage("An error occurred searching for applications\n", { applicationsLoading: false }));

export const fetchApplicationPageFromTag = (query: string, itemsPerPage: number, page: number) =>
    Cloud.get(hpcApplicationsTagSearchQuery(query, page, itemsPerPage))
        .then(({ response }) => receiveApplicationPage(response))   
        .catch(_ => setErrorMessage("An error occurred searching for applications\n", { applicationsLoading: false }));

export const receiveApplicationPage = (page) => ({
    type: DETAILED_APPLICATIONS_RECEIVE_PAGE,
    payload: { page }
});