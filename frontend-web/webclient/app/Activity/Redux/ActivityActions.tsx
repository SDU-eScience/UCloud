import {Cloud} from "Authentication/SDUCloudObject";
import {ActivityGroup, ActivityFilter} from "Activity";
import {PayloadAction, SetLoadingAction, Error} from "Types";
import {activityQuery} from "Utilities/ActivityUtilities";
import {errorMessageOrDefault} from "UtilityFunctions";
import {ScrollResult, ScrollRequest} from "Scroll/Types";
import {Action} from "redux";
import {async} from "q";
import {snackbarStore} from "Snackbar/SnackbarStore";

// Request builders
export const fetchActivity = async (scroll: ScrollRequest<number>, filter?: ActivityFilter) => {
    try {
        const {response} = await Cloud.get(activityQuery(scroll, filter));
        return receiveActivity(response)
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, "Could not fetch activity from server"))
        return setErrorMessage();
    }
}

// Action builders
export type ActivityActions =
    ActivityError |
    SetActivityLoading |
    ReceiveActivityAction |
    ResetActivityAction |
    UpdateActivityFilterAction;

export const SET_ACTIVITY_ERROR_MESSAGE = "SET_ACTIVITY_ERROR_MESSAGE";
type ActivityError = Action<typeof SET_ACTIVITY_ERROR_MESSAGE>
export const setErrorMessage = (): ActivityError => ({
    type: SET_ACTIVITY_ERROR_MESSAGE
});

export const SET_ACTIVITY_LOADING = "SET_ACTIVITY_LOADING";
type SetActivityLoading = SetLoadingAction<typeof SET_ACTIVITY_LOADING>
export const setLoading = (loading: boolean): SetActivityLoading => ({
    type: SET_ACTIVITY_LOADING,
    payload: {loading}
});

export const RECEIVE_ACTIVITY = "RECEIVE_ACTIVITY";
type ReceiveActivityAction = PayloadAction<typeof RECEIVE_ACTIVITY, {page: ScrollResult<ActivityGroup, number>}>
const receiveActivity = (page: ScrollResult<ActivityGroup, number>): ReceiveActivityAction => ({
    type: RECEIVE_ACTIVITY,
    payload: {page}
});

export const RESET_ACTIVITY = "RESET_ACTIVITY";
type ResetActivityAction = Action<typeof RESET_ACTIVITY>;
export const resetActivity = (): ResetActivityAction => ({type: RESET_ACTIVITY});

export const UPDATE_ACTIVITY_FILTER = "UPDATE_ACTIVITY_FILTER";
type UpdateActivityFilterAction = PayloadAction<typeof UPDATE_ACTIVITY_FILTER, {filter: Partial<ActivityFilter>}>
export const updateActivityFilter = (filter: Partial<ActivityFilter>): UpdateActivityFilterAction => (
    {type: UPDATE_ACTIVITY_FILTER, payload: {filter}}
);