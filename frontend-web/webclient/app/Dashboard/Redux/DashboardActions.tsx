import {Analysis} from "Applications";
import {Client} from "Authentication/HttpClientInstance";
import {File} from "Files";
import {Action} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Page, PayloadAction, SetLoadingAction} from "Types";
import {hpcJobsQuery} from "Utilities/ApplicationUtilities";
import {favoritesQuery, recentFilesQuery} from "Utilities/FileUtilities";
import {errorMessageOrDefault} from "UtilityFunctions";
import {
    DASHBOARD_FAVORITE_ERROR,
    DASHBOARD_RECENT_FILES_ERROR,
    DASHBOARD_RECENT_JOBS_ERROR,
    RECEIVE_DASHBOARD_FAVORITES,
    RECEIVE_RECENT_FILES,
    RECEIVE_RECENT_JOBS,
    SET_ALL_LOADING,
} from "./DashboardReducer";

export type DashboardActions = DashboardErrorAction | ReceiveFavoritesProps | ReceiveRecentFilesProps |
    SetLoadingAction<typeof SET_ALL_LOADING> | ReceiveRecentAnalyses;

type DashboardError =
    typeof DASHBOARD_FAVORITE_ERROR |
    typeof DASHBOARD_RECENT_JOBS_ERROR |
    typeof DASHBOARD_RECENT_FILES_ERROR;

type DashboardErrorAction = PayloadAction<DashboardError, {error?: string}>;

/**
 * Sets all dashboard lists as either loading or not loading
 * @param {loading} loading whether or not everything is loading or not
 */
export const setAllLoading = (loading: boolean): SetLoadingAction<typeof SET_ALL_LOADING> => ({
    type: SET_ALL_LOADING,
    payload: {loading}
});

export const setErrorMessage = (type: DashboardError, error?: string): DashboardErrorAction => ({
    type,
    payload: {
        error
    }
});

/**
 * Fetches the contents of the favorites folder and provides the initial 10 items
 */
export const fetchFavorites = async (): Promise<ReceiveFavoritesProps | Action<DashboardError>> => {
    try {
        const {response} = await Client.get<Page<File>>(favoritesQuery(0, 10));
        return receiveFavorites(response.items);
    } catch (err) {
        snackbarStore.addFailure("Failed to fetch favorites. Please try again later.");
        return setErrorMessage(DASHBOARD_FAVORITE_ERROR, errorMessageOrDefault(err, "An error occurred fetching favorites"));
    }
};

type ReceiveFavoritesProps = PayloadAction<typeof RECEIVE_DASHBOARD_FAVORITES, {content: File[]}>;
/**
 * Returns an action containing favorites
 * @param {File[]} content The list of favorites retrieved
 */
export const receiveFavorites = (content: File[]): ReceiveFavoritesProps => ({
    type: RECEIVE_DASHBOARD_FAVORITES,
    payload: {content}
});


type ReceiveRecentFilesProps = PayloadAction<typeof RECEIVE_RECENT_FILES, {content: File[]}>;
/**
 * Fetches the contents of the users homefolder and returns 10 of them.
 */
export const fetchRecentFiles = async (): Promise<ReceiveRecentFilesProps | Action<DashboardError>> => {
    try {
        const {response} = await Client.get(recentFilesQuery);
        return receiveRecentFiles(response.recentFiles);
    } catch (err) {
        snackbarStore.addFailure("Failed to fetch recent files. Please try again later.");
        return setErrorMessage(DASHBOARD_RECENT_FILES_ERROR, errorMessageOrDefault(err, "An error ocurred fetching recent files."));
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
        const {response} = await Client.get(hpcJobsQuery(10, 0));
        return receiveRecentAnalyses(response.items);
    } catch (err) {
        snackbarStore.addFailure("Could not retrieve recent jobs.");
        return setErrorMessage(DASHBOARD_RECENT_JOBS_ERROR, errorMessageOrDefault(err, "An error occurred fetching recent analyses."));
    }
};
type ReceiveRecentAnalyses = PayloadAction<typeof RECEIVE_RECENT_JOBS, {content: Analysis[]}>;
/**
 * Returns an action containing most recently updated analyses
 * @param {Analyses[]} content The list of recently updated analyses
 */
export const receiveRecentAnalyses = (content: Analysis[]): ReceiveRecentAnalyses => ({
    type: RECEIVE_RECENT_JOBS,
    payload: {content}
});
