import {Cloud} from "Authentication/SDUCloudObject";
import {
    RECEIVE_PUBLICATIONS,
    RECEIVE_ZENODO_LOGIN_STATUS,
    SET_ZENODO_LOADING,
    SET_ZENODO_ERROR
} from "./ZenodoReducer";
import {SetLoadingAction, ReceivePage, Page, Error} from "Types";
import {Publication} from "..";
import {PayloadAction} from "Types";
import {Action} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "UtilityFunctions";
import {SnackType} from "Snackbar/Snackbars";

/**
 * Fetches publications by the user
 * @param {number} page the pagenumber to be fetched
 * @param {number} itemsPerPage the number of items to be fetched
 * @returns {Promise<ReceivePublications>} a promise containing receive publications action
 */

export type ZenodoActions = LoginStatusProps | SetLoadingAction<typeof SET_ZENODO_LOADING> | ReceivePublicationsAction | Error<typeof SET_ZENODO_ERROR>;

export const fetchPublications = async (page: number, itemsPerPage: number): Promise<ReceivePublicationsAction | Action<typeof SET_ZENODO_ERROR>> => {
    try {
        const {response} = await Cloud.get(`/zenodo/publications/?itemsPerPage=${itemsPerPage}&page=${page}`);
        return receivePublications(response);
    } catch (e) {
        snackbarStore.addSnack({
            message: errorMessageOrDefault(e, "An error occurred fetching zenodo publications"),
            type: SnackType.Failure
        });
        return setErrorMessage();
    }
}

export const setErrorMessage = (): Action<typeof SET_ZENODO_ERROR> => ({
    type: SET_ZENODO_ERROR
});

/**
 * Contacts a backend to see if the user is logged in to Zenodo.
 */
export async function fetchLoginStatus() {
    try {
        const {response} = await Cloud.get("/zenodo/status");
        return receiveLoginStatus(response.connected)
    } catch (e) {
        snackbarStore.addSnack({
            message: "An error occurred fetching Zenodo log-in status",
            type: SnackType.Failure
        })
        return setErrorMessage();
    }
}

interface LoginStatusProps extends PayloadAction<typeof RECEIVE_ZENODO_LOGIN_STATUS, {connected: boolean}> {}
export const receiveLoginStatus = (connected: boolean): LoginStatusProps => ({
    type: RECEIVE_ZENODO_LOGIN_STATUS,
    payload: {connected}
});


type ReceivePublicationsAction = ReceivePage<typeof RECEIVE_PUBLICATIONS, Publication>
/**
 * The action for receiving a page of Publications
 * @param {Page<Publication>} page The page of publications by the user
 * @param {boolean} connected Whether or not the user is connected to Zenodo
 */
const receivePublications = (page: Page<Publication>): ReceivePublicationsAction => ({
    type: RECEIVE_PUBLICATIONS,
    payload: {page}
});

/**
 * Sets whether or not the Zenodo component is loading
 * @param {boolean} loading sets the loading state for Zenodo
 */
export const setZenodoLoading = (loading: boolean): SetLoadingAction<typeof SET_ZENODO_LOADING> => ({
    type: SET_ZENODO_LOADING,
    payload: {loading}
});