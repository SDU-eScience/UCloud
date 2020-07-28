import {ActivityFilter, ActivityForFrontend} from "Activity";
import {Client} from "Authentication/HttpClientInstance";
import {Action} from "redux";
import {ScrollRequest, ScrollResult} from "Scroll/Types";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {SetLoadingAction} from "Types";
import {activityQuery} from "Utilities/ActivityUtilities";
import {errorMessageOrDefault} from "UtilityFunctions";

// Request builders
export const fetchActivity = async (
    scroll: ScrollRequest<number>,
    filter?: ActivityFilter
): Promise<ReceiveActivityAction | ActivityError> => {
    try {
        const {response} = await Client.get(activityQuery(scroll, filter));
        response.items.forEach(it => {
            if (it.activityEvent.username.startsWith("_")) {
                it.activityEvent.username = "UCloud";
            }
        });
        return receiveActivity(response);
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, "Could not fetch activity from server"), false);
        return setErrorMessage();
    }
};

export type ActivityActions =
    ActivityError |
    SetActivityLoading |
    ReceiveActivityAction |
    ResetActivityAction |
    UpdateActivityFilterAction;

export const SET_ACTIVITY_ERROR_MESSAGE = "SET_ACTIVITY_ERROR_MESSAGE";
type ActivityError = Action<typeof SET_ACTIVITY_ERROR_MESSAGE>;
export const setErrorMessage = (): ActivityError => ({
    type: SET_ACTIVITY_ERROR_MESSAGE
});

export const SET_ACTIVITY_LOADING = "SET_ACTIVITY_LOADING";
type SetActivityLoading = SetLoadingAction<typeof SET_ACTIVITY_LOADING>;
export const setLoading = (loading: boolean): SetActivityLoading => ({
    type: SET_ACTIVITY_LOADING,
    payload: {loading}
});

export const RECEIVE_ACTIVITY = "RECEIVE_ACTIVITY";
type ReceiveActivityAction = PayloadAction<typeof RECEIVE_ACTIVITY, {page: ScrollResult<ActivityForFrontend, number>}>;
const receiveActivity = (page: ScrollResult<ActivityForFrontend, number>): ReceiveActivityAction => ({
    type: RECEIVE_ACTIVITY,
    payload: {page}
});

export const RESET_ACTIVITY = "RESET_ACTIVITY";
type ResetActivityAction = Action<typeof RESET_ACTIVITY>;
export const resetActivity = (): ResetActivityAction => ({type: RESET_ACTIVITY});

export const UPDATE_ACTIVITY_FILTER = "UPDATE_ACTIVITY_FILTER";
type UpdateActivityFilterAction = PayloadAction<typeof UPDATE_ACTIVITY_FILTER, {filter: Partial<ActivityFilter>}>;
export const updateActivityFilter = (filter: Partial<ActivityFilter>): UpdateActivityFilterAction => (
    {type: UPDATE_ACTIVITY_FILTER, payload: {filter}}
);
