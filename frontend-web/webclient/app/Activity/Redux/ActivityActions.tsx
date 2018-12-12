import { Cloud } from "Authentication/SDUCloudObject";
import { Activity } from "Activity";
import { Page, PayloadAction, SetLoadingAction, Error } from "Types";
import { activityQuery } from "Utilities/ActivityUtilities";
import { RECEIVE_ACTIVITY, SET_ACTIVITY_ERROR_MESSAGE, SET_ACTIVITY_LOADING } from "./ActivityReducer";

export type ActivityActions = ActivityError | SetActivityLoading | ReceiveActivityAction;

export const fetchActivity = (pageNumber: number, pageSize: number) => 
    Cloud.get(activityQuery(pageNumber, pageSize))
        .then(({ response }) => receiveActivity(response))
        .catch(() => setErrorMessage("Could not fetch activity from server"));

type ActivityError = Error<typeof SET_ACTIVITY_ERROR_MESSAGE>
export const setErrorMessage = (error?: string): ActivityError => ({
    type: SET_ACTIVITY_ERROR_MESSAGE,
    payload: {error}
});

type SetActivityLoading = SetLoadingAction<typeof SET_ACTIVITY_LOADING>
export const setLoading = (loading: boolean): SetActivityLoading => ({
    type: SET_ACTIVITY_LOADING,
    payload: { loading }
});

type ReceiveActivityAction = PayloadAction<typeof RECEIVE_ACTIVITY, { page: Page<Activity> }>
const receiveActivity = (page: Page<Activity>): ReceiveActivityAction => ({
    type: RECEIVE_ACTIVITY,
    payload: { page }
});

