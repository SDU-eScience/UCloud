import {JobWithStatus} from "Applications";
import {Client} from "Authentication/HttpClientInstance";
import {Action} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {SetLoadingAction} from "Types";
import {hpcJobsQuery} from "Utilities/ApplicationUtilities";
import {errorMessageOrDefault} from "UtilityFunctions";
import {
    DASHBOARD_RECENT_JOBS_ERROR,
    RECEIVE_RECENT_JOBS,
    SET_ALL_LOADING,
} from "./DashboardReducer";

export type DashboardActions = DashboardErrorAction |
    SetLoadingAction<typeof SET_ALL_LOADING> | ReceiveRecentAnalyses;

type DashboardError =
    typeof DASHBOARD_RECENT_JOBS_ERROR;

type DashboardErrorAction = PayloadAction<DashboardError, {error?: string}>;

/**
 * Sets all dashboard lists as either loading or not loading
 * @param {loading} loading whether or not everything is loading or not
 */
export const setAllLoading = (loading: boolean): SetLoadingAction<typeof SET_ALL_LOADING> => ({
    type: SET_ALL_LOADING,
    payload: {loading}
});

export const setErrorMessage = (type: DashboardError, error?: string): DashboardErrorAction => ({
    type,
    payload: {
        error
    }
});

/**
 * Fetches the 10 latest updated analyses
 */
export const fetchRecentAnalyses = async (): Promise<ReceiveRecentAnalyses | Action<DashboardError>> => {
    try {
        const {response} = await Client.get(hpcJobsQuery(10, 0));
        return receiveRecentAnalyses(response.items);
    } catch (err) {
        snackbarStore.addFailure("Could not retrieve recent jobs.", false);
        return setErrorMessage(DASHBOARD_RECENT_JOBS_ERROR, errorMessageOrDefault(err, "An error occurred fetching recent analyses."));
    }
};
type ReceiveRecentAnalyses = PayloadAction<typeof RECEIVE_RECENT_JOBS, {content: JobWithStatus[]}>;
/**
 * Returns an action containing most recently updated analyses
 * @param {JobState[]} content The list of recently updated analyses
 */
export const receiveRecentAnalyses = (content: JobWithStatus[]): ReceiveRecentAnalyses => ({
    type: RECEIVE_RECENT_JOBS,
    payload: {content}
});
