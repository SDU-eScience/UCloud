export const SET_ALL_LOADING = "SET_ALL_LOADING";
export const RECEIVE_FAVORITES = "RECEIVE_FAVORITES";
export const RECEIVE_RECENT_ANALYSES = "RECEIVE_RECENT_ANALYSES";
export const RECEIVE_RECENT_FILES = "RECEIVE_RECENT_FILES";
export const DASHBOARD_FAVORITE_ERROR = "DASHBOARD_FAVORITE_ERROR";
export const DASHBOARD_RECENT_ANALYSES_ERROR = "DASHBOARD_RECENT_ANALYSES_ERROR";
export const DASHBOARD_RECENT_FILES_ERROR = "DASHBOARD_RECENT_FILES_ERROR";

const dashboard = (state = [], action) => {
    switch (action.type) {
        case SET_ALL_LOADING: {
            const { loading } = action;
            return { ...state, favoriteLoading: loading, recentLoading: loading, analysesLoading: loading, activityLoading: loading };
        }
        case RECEIVE_FAVORITES: {
            return { ...state, favoriteFiles: action.content, favoriteLoading: false };
        }
        case RECEIVE_RECENT_ANALYSES: {
            return { ...state, recentAnalyses: action.content, analysesLoading: false };
        }
        case RECEIVE_RECENT_FILES: {
            return { ...state, recentFiles: action.content, recentLoading: false };
        }
        case DASHBOARD_FAVORITE_ERROR: {
            return { ...state, favoriteError: action.error, favoriteLoading: false };
        }
        case DASHBOARD_RECENT_ANALYSES_ERROR: {
            return { ...state, recentAnalysesError: action.error, analysesLoading: false };
        }
        case DASHBOARD_RECENT_FILES_ERROR: {
            return { ...state, recentFilesError: action.error, recentLoading: false };
        }
        default: {
            return state;
        }
    }
};

export default dashboard;