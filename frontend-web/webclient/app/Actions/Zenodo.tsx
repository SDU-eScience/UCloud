import { Cloud } from "../../authentication/SDUCloudObject";
import {
    RECEIVE_PUBLICATIONS,
    SET_ZENODO_LOADING
} from "../Reducers/Zenodo";
import { SetLoadingAction, ReceivePage, Publication, Page, emptyPage } from "../types/types";

/**
 * Fetches publications by the user
 * @param {number} page the pagenumber to be fetched
 * @param {number} itemsPerPage the number of items to be fetched
 * @returns {Promise<ReceivePublications>} a promise containing receive publications action
 */
export const fetchPublications = (page: number, itemsPerPage: number): Promise<ReceivePublications> =>
    Cloud.get(`/zenodo/publications/?itemsPerPage=${itemsPerPage}&page=${page}`).then(({ response }) => {
        return receivePublications(response.inProgress, response.connected);
    }).catch(failure => {
        return receivePublications(emptyPage, false);
    });

interface ReceivePublications extends ReceivePage<Publication> { connected: boolean }
/**
 * The action for receiving a page of Publications
 * @param {page<Publication>} page The page of publications by the user
 * @param {boolean} connected Whether or not the user is connected to Zenodo
 */
const receivePublications = (page: Page<Publication>, connected: boolean): ReceivePublications => ({
    type: RECEIVE_PUBLICATIONS,
    connected,
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