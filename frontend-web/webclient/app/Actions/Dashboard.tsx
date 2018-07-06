import { Cloud } from "../../authentication/SDUCloudObject";
import { SET_ALL_LOADING, RECEIVE_FAVORITES, RECEIVE_RECENT_ANALYSES, RECEIVE_RECENT_FILES } from "../Reducers/Dashboard";
import { failureNotification } from "../UtilityFunctions";
import { Analysis, File, SetLoadingAction, Action } from "../types/types";

interface Fetch<T> extends Action { content: T[] }

export const setAllLoading = (loading: boolean): SetLoadingAction => ({
    type: SET_ALL_LOADING,
    loading
});

export const fetchFavorites = ():Promise<Fetch<File>> =>
    Cloud.get(`/files?path=${Cloud.homeFolder}/Favorites`).then(({ response }) =>
        receiveFavorites(response.items.slice(0, 10))
    ).catch(() => {
        failureNotification("Failed to fetch favorites. Please try again later.")
        return receiveFavorites([])
    });

export const receiveFavorites = (content: File[]):Fetch<File> => ({
    type: RECEIVE_FAVORITES,
    content
});

export const fetchRecentFiles = ():Promise<Fetch<File>> =>
    Cloud.get(`/files?path=${Cloud.homeFolder}`).then(({ response }) =>
        receiveRecentFiles(response.items.slice(0, 10))
    ).catch(() => {
        failureNotification("Failed to fetch recent files. Please try again later.");
        return receiveRecentFiles([]);
    });

const receiveRecentFiles = (content):Fetch<File> => ({
    type: RECEIVE_RECENT_FILES,
    content
});

export const fetchRecentAnalyses = ():Promise<Fetch<Analysis>> =>
    Cloud.get(`/hpc/jobs/?itemsPerPage=${10}&page=${0}`).then(({ response }) =>
        receiveRecentAnalyses(response.items)
    ).catch(() => {
        failureNotification("Failed to fetch recent analyses. Please try again later.")
        return receiveRecentAnalyses([]);
    });

const receiveRecentAnalyses = (content: Analysis[]):Fetch<Analysis> => ({
    type: RECEIVE_RECENT_ANALYSES,
    content
});