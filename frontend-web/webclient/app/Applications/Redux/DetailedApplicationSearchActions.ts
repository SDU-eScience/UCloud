import {Cloud} from "Authentication/SDUCloudObject";
import {
    DETAILED_APPS_SET_NAME,
    DETAILED_APPS_SET_VERSION,
    DETAILED_APPLICATION_SET_ERROR
} from "./DetailedApplicationSearchReducer";
import {hpcApplicationsSearchQuery, hpcApplicationsTagSearchQuery} from "Utilities/ApplicationUtilities";
import {setErrorMessage, receiveApplications} from "Search/Redux/SearchActions";
import {PayloadAction} from "Types";
import {Action} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "UtilityFunctions";

export type DetailedAppActions = SetAppVersionAction | SetAppNameAction | Action<typeof DETAILED_APPLICATION_SET_ERROR>;

type SetAppVersionAction = PayloadAction<typeof DETAILED_APPS_SET_VERSION, {appVersion: string}>
export const setVersion = (appVersion: string): SetAppVersionAction => ({
    type: DETAILED_APPS_SET_VERSION,
    payload: {appVersion}
});

type SetAppNameAction = PayloadAction<typeof DETAILED_APPS_SET_NAME, {appName: string}>
export const setAppName = (appName: string): SetAppNameAction => ({
    type: DETAILED_APPS_SET_NAME,
    payload: {appName}
});

export async function fetchApplicationPageFromName(query: string, itemsPerPage: number, page: number) {
    try {
        const {response} = await Cloud.get(hpcApplicationsSearchQuery({query, page, itemsPerPage}));
        return receiveApplications(response);
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred searching for applications"));
        return setErrorMessage({applicationsLoading: false});
    }
}

export async function fetchApplicationPageFromTag(query: string, itemsPerPage: number, page: number) {
    try {
        const {response} = await Cloud.get(hpcApplicationsTagSearchQuery({query, page, itemsPerPage}));
        return receiveApplications(response)
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred searching for applications"));
        return setErrorMessage({applicationsLoading: false});
    }
}