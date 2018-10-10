import { Cloud } from "Authentication/SDUCloudObject";
import {
    RECEIVE_PUBLICATIONS,
    RECEIVE_ZENODO_LOGIN_STATUS,
    SET_ZENODO_LOADING,
    SET_ZENODO_ERROR
} from "./ZenodoReducer";
import { SetLoadingAction, ReceivePage, Page, Error } from "Types";
import { Publication } from "..";
import { PayloadAction } from "Types";

/**
 * Fetches publications by the user
 * @param {number} page the pagenumber to be fetched
 * @param {number} itemsPerPage the number of items to be fetched
 * @returns {Promise<ReceivePublications>} a promise containing receive publications action
 */

export type ZenodoActions = LoginStatusProps | SetLoadingAction<typeof SET_ZENODO_LOADING> | ReceivePublicationsAction | Error<typeof SET_ZENODO_ERROR>;

export const fetchPublications = (page: number, itemsPerPage: number): Promise<ReceivePublicationsAction | Error<typeof SET_ZENODO_ERROR>> =>
    Cloud.get(`/zenodo/publications/?itemsPerPage=${itemsPerPage}&page=${page}`).then(({ response }) =>
        receivePublications(response)
    ).catch(_ => setErrorMessage(SET_ZENODO_ERROR, "An error occurred fetching zenodo publications"));

export const setErrorMessage = (type: typeof SET_ZENODO_ERROR, error?: string): Error<typeof SET_ZENODO_ERROR> => ({
    type,
    payload: { error }
});

export const fetchLoginStatus = () =>
    Cloud.get("/zenodo/status")
        .then(({ response }) => receiveLoginStatus(response.connected))
        .catch(_ => setErrorMessage(SET_ZENODO_ERROR, "An error occurred fetching Zenodo log-in status"));

interface LoginStatusProps extends PayloadAction<typeof RECEIVE_ZENODO_LOGIN_STATUS, { connected: boolean }> { }
export const receiveLoginStatus = (connected: boolean): LoginStatusProps => ({
    type: RECEIVE_ZENODO_LOGIN_STATUS,
    payload: { connected }
});


type ReceivePublicationsAction = ReceivePage<typeof RECEIVE_PUBLICATIONS, Publication>
/**
 * The action for receiving a page of Publications
 * @param {Page<Publication>} page The page of publications by the user
 * @param {boolean} connected Whether or not the user is connected to Zenodo
 */
const receivePublications = (page: Page<Publication>): ReceivePublicationsAction => ({
    type: RECEIVE_PUBLICATIONS,
    payload: { page }
});

/**
 * Sets whether or not the Zenodo component is loading
 * @param {boolean} loading sets the loading state for Zenodo
 */
export const setZenodoLoading = (loading: boolean): SetLoadingAction<typeof SET_ZENODO_LOADING> => ({
    type: SET_ZENODO_LOADING,
    payload: { loading }
});