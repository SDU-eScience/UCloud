import { Cloud } from "Authentication/SDUCloudObject";
import { ActivityGroup } from "Activity";
import { PayloadAction, SetLoadingAction, Error, ScrollResult } from "Types";
import { activityQuery } from "Utilities/ActivityUtilities";
import { RECEIVE_ACTIVITY, SET_ACTIVITY_ERROR_MESSAGE, SET_ACTIVITY_LOADING } from "./ActivityReducer";
import { errorMessageOrDefault } from "UtilityFunctions";

export type ActivityActions = ActivityError | SetActivityLoading | ReceiveActivityAction;

export const fetchActivity = (offset: number | null, pageSize: number) =>
    Cloud.get(activityQuery(offset, pageSize))
        .then(({ response }) => receiveActivity(response))
        .catch(e => setErrorMessage(errorMessageOrDefault(e, "Could not fetch activity from server")));

type ActivityError = Error<typeof SET_ACTIVITY_ERROR_MESSAGE>
export const setErrorMessage = (error?: string): ActivityError => ({
    type: SET_ACTIVITY_ERROR_MESSAGE,
    payload: { error }
});

type SetActivityLoading = SetLoadingAction<typeof SET_ACTIVITY_LOADING>
export const setLoading = (loading: boolean): SetActivityLoading => ({
    type: SET_ACTIVITY_LOADING,
    payload: { loading }
});

type ReceiveActivityAction = PayloadAction<typeof RECEIVE_ACTIVITY, { page: ScrollResult<ActivityGroup, number> }>
const receiveActivity = (page: ScrollResult<ActivityGroup, number>): ReceiveActivityAction => ({
    type: RECEIVE_ACTIVITY,
    payload: { page }
});

