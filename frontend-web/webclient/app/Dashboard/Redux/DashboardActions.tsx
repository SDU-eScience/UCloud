import {SetLoadingAction} from "@/Types";
import {
    DASHBOARD_RECENT_JOBS_ERROR,
    SET_ALL_LOADING,
} from "./DashboardReducer";

export type DashboardActions = DashboardErrorAction |
    SetLoadingAction<typeof SET_ALL_LOADING>;

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
