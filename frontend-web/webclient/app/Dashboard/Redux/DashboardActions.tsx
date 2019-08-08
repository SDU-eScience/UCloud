import {Cloud} from "Authentication/SDUCloudObject";
import {
    SET_ALL_LOADING,
    RECEIVE_DASHBOARD_FAVORITES,
    RECEIVE_RECENT_JOBS,
    RECEIVE_RECENT_FILES,
    DASHBOARD_FAVORITE_ERROR,
    DASHBOARD_RECENT_JOBS_ERROR,
    DASHBOARD_RECENT_FILES_ERROR
} from "./DashboardReducer";
import {SetLoadingAction} from "Types";
import {Analysis} from "Applications";
import {File} from "Files";
import {hpcJobsQuery} from "Utilities/ApplicationUtilities";
import {PayloadAction} from "Types";
import {recentFilesQuery, favoritesQuery} from "Utilities/FileUtilities";
import {Action} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {SnackType} from "Snackbar/Snackbars";


export type DashboardActions = Action<DashboardError> | ReceiveFavoritesProps | ReceiveRecentFilesProps |
    SetLoadingAction<typeof SET_ALL_LOADING> | ReceiveRecentAnalyses;

type DashboardError = 
    typeof DASHBOARD_FAVORITE_ERROR |
    typeof DASHBOARD_RECENT_JOBS_ERROR |
    typeof DASHBOARD_RECENT_FILES_ERROR;

/**
 * Sets all dashboard lists as either loading or not loading
 * @param {loading} loading whether or not everything is loading or not
 */
export const setAllLoading = (loading: boolean): SetLoadingAction<typeof SET_ALL_LOADING> => ({
    type: SET_ALL_LOADING,
    payload: {loading}
});

export const setErrorMessage = (type: DashboardError): Action<typeof type> => ({
    type
});

/**
 * Fetches the contents of the favorites folder and provides the initial 10 items
 */
export const fetchFavorites = async (): Promise<ReceiveFavoritesProps | Action<DashboardError>> => {
    try {
        const {response} = await Cloud.get(favoritesQuery());
        return receiveFavorites(response.items.slice(0, 10))
    } catch {
        snackbarStore.addSnack({
            message: "Failed to fetch favorites. Please try again later.",
            type: SnackType.Failure
        });
        return setErrorMessage(DASHBOARD_FAVORITE_ERROR);
    }
};

type ReceiveFavoritesProps = PayloadAction<typeof RECEIVE_DASHBOARD_FAVORITES, {content: File[]}>
/**
 * Returns an action containing favorites
 * @param {File[]} content The list of favorites retrieved
 */
export const receiveFavorites = (content: File[]): ReceiveFavoritesProps => ({
    type: RECEIVE_DASHBOARD_FAVORITES,
    payload: {content}
});


type ReceiveRecentFilesProps = PayloadAction<typeof RECEIVE_RECENT_FILES, {content: File[]}>
/**
 * Fetches the contents of the users homefolder and returns 10 of them.
 */
export const fetchRecentFiles = async (): Promise<ReceiveRecentFilesProps | Action<DashboardError>> => {
    try {
        const {response} = await Cloud.get(recentFilesQuery);
        return receiveRecentFiles(response.recentFiles);
    } catch {
        snackbarStore.addSnack({
            message: "Failed to fetch recent files. Please try again later.",
            type: SnackType.Failure
        });
        return setErrorMessage(DASHBOARD_RECENT_FILES_ERROR);
    }
};

/**
* Returns an action containing recently used files
* @param {File[]} content The list of recently used files retrieved
*/
export const receiveRecentFiles = (content: File[]): ReceiveRecentFilesProps => ({
    type: RECEIVE_RECENT_FILES,
    payload: {content}
});

/**
 * Fetches the 10 latest updated analyses
 */
export const fetchRecentAnalyses = async (): Promise<ReceiveRecentAnalyses | Action<DashboardError>> => {
    try {
        const {response} = await Cloud.get(hpcJobsQuery(10, 0));
        return receiveRecentAnalyses(response.items)
    } catch {
        snackbarStore.addSnack({
            message: "Could not retrieve recent jobs.",
            type: SnackType.Failure
        });
        return setErrorMessage(DASHBOARD_RECENT_JOBS_ERROR);
    }
};
type ReceiveRecentAnalyses = PayloadAction<typeof RECEIVE_RECENT_JOBS, {content: Analysis[]}>
/**
* Returns an action containing most recently updated analyses
* @param {Analyses[]} content The list of recently updated analyses
*/
export const receiveRecentAnalyses = (content: Analysis[]): ReceiveRecentAnalyses => ({
    type: RECEIVE_RECENT_JOBS,
    payload: {content}
});