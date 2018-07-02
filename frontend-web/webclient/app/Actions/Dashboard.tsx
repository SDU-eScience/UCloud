import { Cloud } from "../../authentication/SDUCloudObject";
import { SET_ALL_LOADING, RECEIVE_FAVORITES, RECEIVE_RECENT_ANALYSES, RECEIVE_RECENT_FILES } from "../Reducers/Dashboard";
import { failureNotification } from "../UtilityFunctions";

export const setAllLoading = (loading) => ({
    type: SET_ALL_LOADING,
    loading
});

export const fetchFavorites = () =>
    Cloud.get(`/files?path=${Cloud.homeFolder}/Favorites`).then(({ response }) =>
        receiveFavorites(response.slice(0, 10))
    ).catch(() => {
        failureNotification("Failed to fetch favorites. Please try again later.")
        return receiveFavorites([])
    });

export const receiveFavorites = (favorites) => ({
    type: RECEIVE_FAVORITES,
    favorites
});

export const fetchRecentFiles = () =>
    Cloud.get(`/files?path=${Cloud.homeFolder}`).then(({ response }) =>
        receiveRecentFiles(response.slice(0, 10))
    ).catch(() => {
        failureNotification("Failed to fetch recent files. Please try again later.");
        return receiveRecentAnalyses([]);
    });

const receiveRecentFiles = (recentFiles) => ({
    type: RECEIVE_RECENT_FILES,
    recentFiles
});

export const fetchRecentAnalyses = () =>
    Cloud.get(`/hpc/jobs/?itemsPerPage=${10}&page=${0}`).then(({ response }) =>
        receiveRecentAnalyses(response.items)
    ).catch(() => {
        failureNotification("Failed to fetch recent analyses. Please try again later.")
        return receiveRecentAnalyses([]);
    });

const receiveRecentAnalyses = (recentAnalyses) => ({
    type: RECEIVE_RECENT_ANALYSES,
    recentAnalyses
});