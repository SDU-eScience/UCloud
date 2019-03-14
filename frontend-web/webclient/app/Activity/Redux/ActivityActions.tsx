import { Cloud } from "Authentication/SDUCloudObject";
import { ActivityGroup, ActivityFilter } from "Activity";
import { PayloadAction, SetLoadingAction, Error } from "Types";
import { activityQuery } from "Utilities/ActivityUtilities";
import { errorMessageOrDefault } from "UtilityFunctions";
import { ScrollResult } from "Scroll/Types";
import { Action } from "redux";

// Request builders
export const fetchActivity = (offset: number | null, pageSize: number) =>
    Cloud.get(activityQuery(offset, pageSize))
        .then(({ response }) => receiveActivity(response))
        .catch(e => setErrorMessage(errorMessageOrDefault(e, "Could not fetch activity from server")));

// Action builders
export type ActivityActions =
    ActivityError |
    SetActivityLoading |
    ReceiveActivityAction |
    ResetActivityAction |
    UpdateActivityFilterAction;

export const SET_ACTIVITY_ERROR_MESSAGE = "SET_ACTIVITY_ERROR_MESSAGE";
type ActivityError = Error<typeof SET_ACTIVITY_ERROR_MESSAGE>
export const setErrorMessage = (error?: string): ActivityError => ({
    type: SET_ACTIVITY_ERROR_MESSAGE,
    payload: { error }
});

export const SET_ACTIVITY_LOADING = "SET_ACTIVITY_LOADING";
type SetActivityLoading = SetLoadingAction<typeof SET_ACTIVITY_LOADING>
export const setLoading = (loading: boolean): SetActivityLoading => ({
    type: SET_ACTIVITY_LOADING,
    payload: { loading }
});

export const RECEIVE_ACTIVITY = "RECEIVE_ACTIVITY";
type ReceiveActivityAction = PayloadAction<typeof RECEIVE_ACTIVITY, { page: ScrollResult<ActivityGroup, number> }>
const receiveActivity = (page: ScrollResult<ActivityGroup, number>): ReceiveActivityAction => ({
    type: RECEIVE_ACTIVITY,
    payload: { page }
});

export const RESET_ACTIVITY = "RESET_ACTIVITY";
type ResetActivityAction = Action<typeof RESET_ACTIVITY>;
export const resetActivity = (): ResetActivityAction => ({ type: RESET_ACTIVITY });

export const UPDATE_ACTIVITY_FILTER = "UPDATE_ACTIVITY_FILTER";
type UpdateActivityFilterAction = PayloadAction<typeof UPDATE_ACTIVITY_FILTER, { filter: Partial<ActivityFilter> }>
export const updateActivityFilter = (filter: Partial<ActivityFilter>): UpdateActivityFilterAction => (
    { type: UPDATE_ACTIVITY_FILTER, payload: { filter } }
);