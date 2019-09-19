import {AdvancedSearchRequest} from "Applications";
import {Cloud} from "Authentication/SDUCloudObject";
import {Action} from "redux";
import {receiveApplications, setErrorMessage} from "Search/Redux/SearchActions";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {PayloadAction} from "Types";
import {advancedSearchQuery} from "Utilities/ApplicationUtilities";
import {errorMessageOrDefault} from "UtilityFunctions";
import {
    DETAILED_APPLICATION_SET_ERROR,
    DETAILED_APPS_ADD_TAG,
    DETAILED_APPS_CLEAR_TAGS,
    DETAILED_APPS_REMOVE_TAG,
    DETAILED_APPS_SET_NAME,
    DETAILED_APPS_SET_VERSION
} from "./DetailedApplicationSearchReducer";

export type DetailedAppActions = SetAppVersionAction | SetAppNameAction | AddTag | RemoveTag | ClearTags |
    Action<typeof DETAILED_APPLICATION_SET_ERROR>;

type SetAppVersionAction = PayloadAction<typeof DETAILED_APPS_SET_VERSION, {appVersion: string}>;
export const setVersion = (appVersion: string): SetAppVersionAction => ({
    type: DETAILED_APPS_SET_VERSION,
    payload: {appVersion}
});

type SetAppNameAction = PayloadAction<typeof DETAILED_APPS_SET_NAME, {appName: string}>;
export const setAppName = (appName: string): SetAppNameAction => ({
    type: DETAILED_APPS_SET_NAME,
    payload: {appName}
});

type AddTag = PayloadAction<typeof DETAILED_APPS_ADD_TAG, {tag: string}>;
type RemoveTag = PayloadAction<typeof DETAILED_APPS_REMOVE_TAG, {tag: string}>;
export const tagAction = (
    action: typeof DETAILED_APPS_ADD_TAG | typeof DETAILED_APPS_REMOVE_TAG,
    tag: string
): AddTag | RemoveTag => ({
    type: action,
    payload: {tag}
});

type ClearTags = Action<typeof DETAILED_APPS_CLEAR_TAGS>;
export const clearTags = (): ClearTags => ({
    type: DETAILED_APPS_CLEAR_TAGS
});

export async function fetchApplications(body: AdvancedSearchRequest) {
    try {
        const {response} = await Cloud.post(advancedSearchQuery(), body);
        return receiveApplications(response);
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred searching for applications"));
        return setErrorMessage({applicationsLoading: false});
    }
}
