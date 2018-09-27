import { DashboardStateProps } from "Dashboard";
import { initDashboard } from "DefaultObjects";
import { DashboardActions } from "./DashboardActions";

export const SET_ALL_LOADING = "SET_ALL_LOADING";
export const RECEIVE_FAVORITES = "RECEIVE_FAVORITES";
export const RECEIVE_RECENT_ANALYSES = "RECEIVE_RECENT_ANALYSES";
export const RECEIVE_RECENT_FILES = "RECEIVE_RECENT_FILES";
export const DASHBOARD_FAVORITE_ERROR = "DASHBOARD_FAVORITE_ERROR";
export const DASHBOARD_RECENT_ANALYSES_ERROR = "DASHBOARD_RECENT_ANALYSES_ERROR";
export const DASHBOARD_RECENT_FILES_ERROR = "DASHBOARD_RECENT_FILES_ERROR";

const dashboard = (state: DashboardStateProps = initDashboard(), action: DashboardActions) => {
    switch (action.type) {
        case SET_ALL_LOADING: {
            const { loading } = action.payload;
            return { ...state, favoriteLoading: loading, recentLoading: loading, analysesLoading: loading, activityLoading: loading };
        }
        case RECEIVE_FAVORITES: {
            return { ...state, favoriteFiles: action.payload.content, favoriteLoading: false };
        }
        case RECEIVE_RECENT_ANALYSES: {
            return { ...state, recentAnalyses: action.payload.content, analysesLoading: false };
        }
        case RECEIVE_RECENT_FILES: {
            return { ...state, recentFiles: action.payload.content, recentLoading: false };
        }
        case DASHBOARD_FAVORITE_ERROR: {
            return { ...state, favoriteError: action.payload.error, favoriteLoading: false };
        }
        case DASHBOARD_RECENT_ANALYSES_ERROR: {
            return { ...state, recentAnalysesError: action.payload.error, analysesLoading: false };
        }
        case DASHBOARD_RECENT_FILES_ERROR: {
            return { ...state, recentFilesError: action.payload.error, recentLoading: false };
        }
        default: {
            return state;
        }
    }
};

export default dashboard;