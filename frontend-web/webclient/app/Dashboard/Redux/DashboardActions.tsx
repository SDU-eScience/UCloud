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
import { Action } from "redux";
import { Analysis } from "Applications";
import { File } from "Files";
import { hpcJobsQuery } from "Utilities/ApplicationUtilities";

interface Fetch<T1, T2> extends Action<T1> { content: T2[] }
/**
 * Sets all dashboard lists as either loading or not loading
 * @param {loading} loading whether or not everything is loading or not
 */
export const setAllLoading = (loading: boolean): SetLoadingAction<typeof SET_ALL_LOADING> => ({
    type: SET_ALL_LOADING,
    loading
});

export const setErrorMessage = (type: string, error?: string): Error<any> => ({
    type,
    error
});

/**
 * Fetches the contents of the favorites folder and provides the initial 10 items
 */
export const fetchFavorites = (): Promise<Fetch<typeof RECEIVE_FAVORITES, File> | Error<typeof DASHBOARD_FAVORITE_ERROR>> =>
    Cloud.get(`/files?path=${Cloud.homeFolder}Favorites`).then(({ response }) =>
        receiveFavorites(response.items.slice(0, 10))
    ).catch(() => setErrorMessage(DASHBOARD_FAVORITE_ERROR, "Failed to fetch favorites. Please try again later."));

/**
 * Returns an action containing favorites
 * @param {File[]} content The list of favorites retrieved
 */
export const receiveFavorites = (content: File[]): Fetch<typeof RECEIVE_FAVORITES, File> => ({
    type: RECEIVE_FAVORITES,
    content
});

/**
 * Fetches the contents of the users homefolder and returns 10 of them.
 */
// FIXME Should have specific endpoint so as to not use homefolder for this
export const fetchRecentFiles = (): Promise<Fetch<typeof RECEIVE_RECENT_FILES, File> | Error<typeof DASHBOARD_RECENT_FILES_ERROR>> =>
    Cloud.get(`files?path=${Cloud.homeFolder}&itemsPerPage=10&page=0&order=DESCENDING&sortBy=MODIFIED_AT`).then(({ response }) =>
        receiveRecentFiles(response.items)
    ).catch(() => setErrorMessage(DASHBOARD_RECENT_FILES_ERROR, "Failed to fetch recent files. Please try again later."));

/**
* Returns an action containing recently used files
* @param {File[]} content The list of recently used files retrieved
*/
export const receiveRecentFiles = (content: File[]): Fetch<typeof RECEIVE_RECENT_FILES, File> => ({
    type: RECEIVE_RECENT_FILES,
    content
});

/**
 * Fetches the 10 latest updated analyses
 */
export const fetchRecentAnalyses = (): Promise<Fetch<typeof RECEIVE_RECENT_ANALYSES, Analysis> | Error<typeof DASHBOARD_RECENT_ANALYSES_ERROR>> =>
    Cloud.get(hpcJobsQuery(10, 0)).then(({ response }) =>
        receiveRecentAnalyses(response.items)
    ).catch(_ => setErrorMessage(DASHBOARD_RECENT_ANALYSES_ERROR, "Failed to fetch recent analyses. Please try again later."));

/**
* Returns an action containing most recently updated analyses
* @param {Analyses[]} content The list of recently updated analyses
*/
export const receiveRecentAnalyses = (content: Analysis[]): Fetch<typeof RECEIVE_RECENT_ANALYSES, Analysis> => ({
    type: RECEIVE_RECENT_ANALYSES,
    content
});