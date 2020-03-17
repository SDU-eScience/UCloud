import {DashboardStateProps} from "Dashboard";
import {initDashboard} from "DefaultObjects";
import {DashboardActions} from "./DashboardActions";

export const SET_ALL_LOADING = "SET_ALL_LOADING";
export const RECEIVE_DASHBOARD_FAVORITES = "RECEIVE_DASHBOARD_FAVORITES";
export const RECEIVE_RECENT_JOBS = "RECEIVE_RECENT_JOBS";
export const DASHBOARD_FAVORITE_ERROR = "DASHBOARD_FAVORITE_ERROR";
export const DASHBOARD_RECENT_JOBS_ERROR = "DASHBOARD_RECENT_JOBS_ERROR";

const dashboard = (state: DashboardStateProps = initDashboard(), action: DashboardActions): DashboardStateProps => {
    switch (action.type) {
        case SET_ALL_LOADING: {
            const {loading} = action.payload;
            return {...state, favoriteLoading: loading, analysesLoading: loading};
        }
        case RECEIVE_DASHBOARD_FAVORITES: {
            return {...state, favoriteFiles: action.payload.content, favoriteLoading: false, favoritesError: undefined};
        }
        case RECEIVE_RECENT_JOBS: {
            return {...state, recentAnalyses: action.payload.content, analysesLoading: false, recentJobsError: undefined};
        }
        case DASHBOARD_FAVORITE_ERROR:
            return {
                ...state,
                favoriteLoading: false,
                favoritesError: action.payload.error
            };
        case DASHBOARD_RECENT_JOBS_ERROR:
            return {
                ...state,
                analysesLoading: false,
                recentJobsError: action.payload.error
            };
        default: {
            return state;
        }
    }
};

export default dashboard;
