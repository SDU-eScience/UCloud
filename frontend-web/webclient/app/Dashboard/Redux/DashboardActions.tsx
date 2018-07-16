import { Cloud } from "Authentication/SDUCloudObject";
import { SET_ALL_LOADING, RECEIVE_FAVORITES, RECEIVE_RECENT_ANALYSES, RECEIVE_RECENT_FILES } from "./DashboardReducer";
import { failureNotification } from "UtilityFunctions";
import { SetLoadingAction, Action } from "Types";
import { Analysis } from "Applications"; 
import { File } from "Files";

interface Fetch<T> extends Action { content: T[] }
/**
 * Sets all dashboard lists as either loading or not loading
 * @param {loading} loading whether or not everything is loading or not
 */
export const setAllLoading = (loading: boolean): SetLoadingAction => ({
    type: SET_ALL_LOADING,
    loading
});

/**
 * Fetches the contents of the favorites folder and provides the initial 10 items
 */
export const fetchFavorites = (): Promise<Fetch<File>> =>
    Cloud.get(`/files?path=${Cloud.homeFolder}Favorites`).then(({ response }) =>
        receiveFavorites(response.items.slice(0, 10))
    ).catch(() => {
        failureNotification("Failed to fetch favorites. Please try again later.")
        return receiveFavorites([])
    });

/**
 * Returns an action containing favorites
 * @param {File[]} content The list of favorites retrieved
 */
export const receiveFavorites = (content: File[]): Fetch<File> => ({
    type: RECEIVE_FAVORITES,
    content
});

/**
 * Fetches the contents of the users homefolder and returns 10 of them.
 */
// FIXME Should limit to ten items, should sort by modified_at, desc
export const fetchRecentFiles = (): Promise<Fetch<File>> =>
    Cloud.get(`/files?path=${Cloud.homeFolder}`).then(({ response }) =>
        receiveRecentFiles(response.items.slice(0, 10))
    ).catch(() => {
        failureNotification("Failed to fetch recent files. Please try again later.");
        return receiveRecentFiles([]);
    });

/**
* Returns an action containing recently used files
* @param {File[]} content The list of recently used files retrieved
*/
const receiveRecentFiles = (content): Fetch<File> => ({
    type: RECEIVE_RECENT_FILES,
    content
});

/**
 * Fetches the 10 latest updated analyses
 */
export const fetchRecentAnalyses = (): Promise<Fetch<Analysis>> =>
    Cloud.get(`/hpc/jobs/?itemsPerPage=${10}&page=${0}`).then(({ response }) =>
        receiveRecentAnalyses(response.items)
    ).catch(() => {
        failureNotification("Failed to fetch recent analyses. Please try again later.")
        return receiveRecentAnalyses([]);
    });

/**
* Returns an action containing most recently updated analyses
* @param {Analyses[]} content The list of recently updated analyses
*/
const receiveRecentAnalyses = (content: Analysis[]): Fetch<Analysis> => ({
    type: RECEIVE_RECENT_ANALYSES,
    content
});