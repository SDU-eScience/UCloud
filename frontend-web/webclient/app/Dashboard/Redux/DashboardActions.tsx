import { Cloud } from "Authentication/SDUCloudObject";
import {
    SET_ALL_LOADING,
    RECEIVE_FAVORITES,
    RECEIVE_RECENT_ANALYSES,
    RECEIVE_RECENT_FILES,
    DASHBOARD_FAVORITE_ERROR,
    DASHBOARD_RECENT_ANALYSES_ERROR,
    DASHBOARD_RECENT_FILES_ERROR
} from "./DashboardReducer";
import { SetLoadingAction, Error } from "Types";
import { Analysis } from "Applications";
import { File } from "Files";
import { hpcJobsQuery } from "Utilities/ApplicationUtilities";
import { PayloadAction } from "Types";
import { recentFilesQuery } from "Utilities/FileUtilities";


export type DashboardActions = Error<DashboardError> | ReceiveFavoritesProps | ReceiveRecentFilesProps |
    SetLoadingAction<typeof SET_ALL_LOADING> | ReceiveRecentAnalyses;



type DashboardError = typeof DASHBOARD_FAVORITE_ERROR | typeof DASHBOARD_RECENT_ANALYSES_ERROR | typeof DASHBOARD_RECENT_FILES_ERROR;

/**
 * Sets all dashboard lists as either loading or not loading
 * @param {loading} loading whether or not everything is loading or not
 */
export const setAllLoading = (loading: boolean): SetLoadingAction<typeof SET_ALL_LOADING> => ({
    type: SET_ALL_LOADING,
    payload: { loading }
});



export const setErrorMessage = (type: DashboardError, error?: string): Error<DashboardError> => ({
    type,
    payload: { error }
});

/**
 * Fetches the contents of the favorites folder and provides the initial 10 items
 */
export const fetchFavorites = (): Promise<ReceiveFavoritesProps | Error<DashboardError>> =>
    Cloud.get(`/files?path=${encodeURIComponent(`${Cloud.homeFolder}Favorites`)}`).then(({ response }) =>
        receiveFavorites(response.items.slice(0, 10))
    ).catch(() => setErrorMessage(DASHBOARD_FAVORITE_ERROR, "Failed to fetch favorites. Please try again later."));

type ReceiveFavoritesProps = PayloadAction<typeof RECEIVE_FAVORITES, { content: File[] }>
/**
 * Returns an action containing favorites
 * @param {File[]} content The list of favorites retrieved
 */
export const receiveFavorites = (content: File[]): ReceiveFavoritesProps => ({
    type: RECEIVE_FAVORITES,
    payload: { content }
});


type ReceiveRecentFilesProps = PayloadAction<typeof RECEIVE_RECENT_FILES, { content: File[] }>
/**
 * Fetches the contents of the users homefolder and returns 10 of them.
 */
export const fetchRecentFiles = (): Promise<ReceiveRecentFilesProps | Error<DashboardError>> =>
    Cloud.get(recentFilesQuery).then(({ response }) =>
        receiveRecentFiles(response.recentFiles)
    ).catch(() => setErrorMessage(DASHBOARD_RECENT_FILES_ERROR, "Failed to fetch recent files. Please try again later."));

/**
* Returns an action containing recently used files
* @param {File[]} content The list of recently used files retrieved
*/
export const receiveRecentFiles = (content: File[]): ReceiveRecentFilesProps => ({
    type: RECEIVE_RECENT_FILES,
    payload: { content }
});

/**
 * Fetches the 10 latest updated analyses
 */
export const fetchRecentAnalyses = (): Promise<ReceiveRecentAnalyses | Error<DashboardError>> =>
    Cloud.get(hpcJobsQuery(10, 0)).then(({ response }) =>
        receiveRecentAnalyses(response.items)
    ).catch(_ => setErrorMessage(DASHBOARD_RECENT_ANALYSES_ERROR, "Failed to fetch recent jobs. Please try again later."));

type ReceiveRecentAnalyses = PayloadAction<typeof RECEIVE_RECENT_ANALYSES, { content: Analysis[] }>
/**
* Returns an action containing most recently updated analyses
* @param {Analyses[]} content The list of recently updated analyses
*/
export const receiveRecentAnalyses = (content: Analysis[]): ReceiveRecentAnalyses => ({
    type: RECEIVE_RECENT_ANALYSES,
    payload: { content }
});