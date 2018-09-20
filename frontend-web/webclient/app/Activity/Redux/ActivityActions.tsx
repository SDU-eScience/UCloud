import { Cloud } from "Authentication/SDUCloudObject";
import { Activity } from "Activity";
import { Page } from "Types";
import { RECEIVE_ACTIVITY, SET_ACTIVITY_ERROR_MESSAGE, SET_ACTIVITY_LOADING } from "./ActivityReducer";

export const fetchActivity = (pageNumber: number, pageSize: number) => Cloud.get(
    `/activity/stream?page=${pageNumber}&itemsPerPage=${pageSize}`
).then(({ response }) => receiveActivity(response)).catch(() => setErrorMessage("Could not fetch activity from server"));

export const setErrorMessage = (error?: string) => ({
    type: SET_ACTIVITY_ERROR_MESSAGE,
    error
});

export const setLoading = (loading: boolean) => ({
    type: SET_ACTIVITY_LOADING,
    loading
});

const receiveActivity = (activity: Page<Activity>) => ({
    type: RECEIVE_ACTIVITY,
    activity
});

