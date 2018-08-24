import { Cloud } from "Authentication/SDUCloudObject";
import {
    RECEIVE_PUBLICATIONS,
    RECEIVE_ZENODO_LOGIN_STATUS,
    SET_ZENODO_LOADING,
    SET_ZENODO_ERROR
} from "./ZenodoReducer";
import { SetLoadingAction, ReceivePage, Page, Error } from "Types";
import { Publication } from "..";

/**
 * Fetches publications by the user
 * @param {number} page the pagenumber to be fetched
 * @param {number} itemsPerPage the number of items to be fetched
 * @returns {Promise<ReceivePublications>} a promise containing receive publications action
 */

export const fetchPublications = (page: number, itemsPerPage: number): Promise<ReceivePage<Publication> | Error> =>
    Cloud.get(`/zenodo/publications/?itemsPerPage=${itemsPerPage}&page=${page}`).then(({ response }) =>
        receivePublications(response)
    ).catch(_ => setErrorMessage(SET_ZENODO_ERROR, "An error occurred fetching zenodo publications"));

export const setErrorMessage = (type: string, error?: string): Error => ({
    type,
    error
});

export const fetchLoginStatus = () =>
    Cloud.get("/zenodo/status").then(({ response }) => receiveLoginStatus(response.connected)); // FIXME Error handling

export const receiveLoginStatus = (connected: boolean) => ({
    type: RECEIVE_ZENODO_LOGIN_STATUS,
    connected
})

/**
 * The action for receiving a page of Publications
 * @param {Page<Publication>} page The page of publications by the user
 * @param {boolean} connected Whether or not the user is connected to Zenodo
 */
const receivePublications = (page: Page<Publication>): ReceivePage<Publication> => ({
    type: RECEIVE_PUBLICATIONS,
    page
});

/**
 * Sets whether or not the Zenodo component is loading
 * @param {boolean} loading sets the loading state for Zenodo
 */
export const setZenodoLoading = (loading: boolean): SetLoadingAction => ({
    type: SET_ZENODO_LOADING,
    loading
});