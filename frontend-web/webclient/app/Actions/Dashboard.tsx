import { Cloud } from "../../authentication/SDUCloudObject";
import { SET_ALL_LOADING, RECEIVE_FAVORITES, RECEIVE_RECENT_ANALYSES, RECEIVE_RECENT_FILES } from "../Reducers/Dashboard";
import { failureNotification } from "../UtilityFunctions";
import { Analysis, File } from "../types/types";

type Action = { type: string }

interface SetLoading extends Action { loading: boolean }
export const setAllLoading = (loading: boolean): SetLoading => ({
    type: SET_ALL_LOADING,
    loading
});

export const fetchFavorites = () =>
    Cloud.get(`/files?path=${Cloud.homeFolder}/Favorites`).then(({ response }) =>
        receiveFavorites(response.items.slice(0, 10))
    ).catch(() => {
        failureNotification("Failed to fetch favorites. Please try again later.")
        return receiveFavorites([])
    });

export const receiveFavorites = (favorites: File[]) => ({
    type: RECEIVE_FAVORITES,
    favorites
});

interface ReceiveRecentFilesAction extends Action { recentFiles: File[] }
export const fetchRecentFiles = (): Promise<ReceiveRecentFilesAction> =>
    Cloud.get(`/files?path=${Cloud.homeFolder}`).then(({ response }) =>
        receiveRecentFiles(response.items.slice(0, 10))
    ).catch(() => {
        failureNotification("Failed to fetch recent files. Please try again later.");
        return receiveRecentFiles([]);
    });

const receiveRecentFiles = (recentFiles): ReceiveRecentFilesAction => ({
    type: RECEIVE_RECENT_FILES,
    recentFiles
});

interface ReceiveRecentAnaylses extends Action { recentAnalyses: Analysis[] }
export const fetchRecentAnalyses = (): Promise<ReceiveRecentAnaylses> =>
    Cloud.get(`/hpc/jobs/?itemsPerPage=${10}&page=${0}`).then(({ response }) =>
        receiveRecentAnalyses(response.items)
    ).catch(() => {
        failureNotification("Failed to fetch recent analyses. Please try again later.")
        return receiveRecentAnalyses([]);
    });

const receiveRecentAnalyses = (recentAnalyses: Analysis[]): ReceiveRecentAnaylses => ({
    type: RECEIVE_RECENT_ANALYSES,
    recentAnalyses
});