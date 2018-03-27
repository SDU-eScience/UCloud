export const SET_ALL_LOADING = "SET_ALL_LOADING";
export const RECEIVE_FAVORITES = "RECEIVE_FAVORITES";
export const RECEIVE_RECENT_ANALYSES = "RECEIVE_RECENT_ANALYSES";
export const RECEIVE_RECENT_FILES = "RECEIVE_RECENT_FILES";

const dashboard = (state = [], action) => {
    switch (action.type) {
        case SET_ALL_LOADING: {
            const { loading } = action;
            return { ...state, favoriteLoading: loading, recentLoading: loading, analysesLoading: loading, activityLoading: loading };
        }
        case RECEIVE_FAVORITES: {
            return { ...state, favoriteFiles: action.favorites, favoriteLoading: false }; 
        }
        case RECEIVE_RECENT_ANALYSES: {
            return { ...state, recentAnalyses: action.recentAnalyses, analysesLoading: false };
        }
        case RECEIVE_RECENT_FILES: {
            return { ...state, recentFiles: action.recentFiles, recentLoading: false };
        }
        default: {
            return state;
        }
    }
};

export default dashboard;