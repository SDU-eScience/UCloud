import {SetLoadingAction} from "@/Types";
import {DashboardStateProps} from "@/Dashboard";
import {initDashboard} from "@/DefaultObjects";

export type Index = DashboardErrorAction |
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

export const SET_ALL_LOADING = "SET_ALL_LOADING";
export const DASHBOARD_RECENT_JOBS_ERROR = "DASHBOARD_RECENT_JOBS_ERROR";

export function dashboardReducer(state: DashboardStateProps = initDashboard(), action: Index): DashboardStateProps {
    switch (action.type) {
        case SET_ALL_LOADING: {
            const {loading} = action.payload;
            return {...state, loading};
        }
        default: {
            return state;
        }
    }
}
