import {AdvancedSearchRequest} from "Applications";
import {Client} from "Authentication/HttpClientInstance";
import {Action} from "redux";
import {receiveApplications, setErrorMessage, ReceiveApplications, SetErrorMessage} from "Search/Redux/SearchActions";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {advancedSearchQuery} from "Utilities/ApplicationUtilities";
import {errorMessageOrDefault} from "UtilityFunctions";
import {
    DETAILED_APPLICATION_SET_ERROR,
    DETAILED_APPS_ADD_TAG,
    DETAILED_APPS_CLEAR_TAGS,
    DETAILED_APPS_REMOVE_TAG,
    DETAILED_APPS_SET_NAME,
    DETAILED_APPS_SHOW_ALL_VERSIONS
} from "./DetailedApplicationSearchReducer";

export type DetailedAppActions = SetAppQuery | AddTag | RemoveTag | ClearTags | SetShowAllVersions |
    Action<typeof DETAILED_APPLICATION_SET_ERROR>;


type SetAppQuery = PayloadAction<typeof DETAILED_APPS_SET_NAME, {appQuery: string}>;
export const setAppQuery = (appQuery: string): SetAppQuery => ({
    type: DETAILED_APPS_SET_NAME,
    payload: {appQuery}
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

type SetShowAllVersions = Action<typeof DETAILED_APPS_SHOW_ALL_VERSIONS>;
export const setShowAllVersions = (): SetShowAllVersions => ({
    type: DETAILED_APPS_SHOW_ALL_VERSIONS,
});

export async function fetchApplications(body: AdvancedSearchRequest): Promise<ReceiveApplications | SetErrorMessage> {
    try {
        const {response} = await Client.post(advancedSearchQuery, body);
        return receiveApplications(response);
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred searching for applications"), false);
        return setErrorMessage({applicationsLoading: false});
    }
}
