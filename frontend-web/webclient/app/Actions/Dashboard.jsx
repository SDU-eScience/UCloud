import { Cloud } from "../../authentication/SDUCloudObject";
import { SET_ALL_LOADING, RECEIVE_FAVORITES, RECEIVE_RECENT_ANALYSES, RECEIVE_RECENT_FILES } from "../Reducers/Dashboard";

export const setAllLoading = (loading) => ({
    type: SET_ALL_LOADING,
    loading
});

export const fetchFavorites = () =>
    Cloud.get(`/files?path=${Cloud.homeFolder}`).then(({ response }) => {
        let actualFavorites = response.filter(file => file.favorited);
        const subsetFavorites = actualFavorites.slice(0, 10);
        return receiveFavorites(subsetFavorites);
    });

export const receiveFavorites = (favorites) => ({
    type: RECEIVE_FAVORITES,
    favorites
});

export const fetchRecentFiles = () =>
    Cloud.get(`/files?path=${Cloud.homeFolder}`).then(({ response }) => {
        const recentSubset = response.slice(0, 10);
        return receiveRecentFiles(recentSubset);
    });

const receiveRecentFiles = (recentFiles) => ({
    type: RECEIVE_RECENT_FILES,
    recentFiles
});

export const fetchRecentAnalyses = () =>
    Cloud.get("/hpc/jobs").then(({ response }) => {
        const recentAnalyses = response.items.slice(0, 10);
        return receiveRecentAnalyses(recentAnalyses);
    });

const receiveRecentAnalyses = (recentAnalyses) => ({
    type: RECEIVE_RECENT_ANALYSES,
    recentAnalyses
});