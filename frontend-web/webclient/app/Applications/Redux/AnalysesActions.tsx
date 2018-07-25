import { Cloud } from "Authentication/SDUCloudObject";
import { SET_ANALYSES_LOADING, RECEIVE_ANALYSES, SET_ANALYSES_ERROR } from "./AnalysesReducer";
import { Page, ReceivePage, SetLoadingAction, Error } from "Types";
import { Analysis } from "..";

/**
 * Fetches a page of analyses based on the itemsPerPage and page provided
 * @param {number} itemsPerPage number of items the retrieved page should contain
 * @param {number} page the page number to be retrieved
 */
export const fetchAnalyses = (itemsPerPage: number, page: number): Promise<ReceivePage<Analysis> | Error> =>
    Cloud.get(`/hpc/jobs/?itemsPerPage=${itemsPerPage}&page=${page}`)
        .then(({ response }) => receiveAnalyses(response)).catch(() =>
            setErrorMessage("Retrieval of analyses failed, please try again later.")
        );

/**
 * Returns an action containing the page retrieved
 * @param {Page<Analysis>} page contains the analyses, pageNumber and items per page
 */
const receiveAnalyses = (page: Page<Analysis>): ReceivePage<Analysis> => ({
    type: RECEIVE_ANALYSES,
    page
});

/**
 * Used to set or remove the error for the component
 * @param {string?} error The error to be renered in the component. Nothing will be rendered if string is null
 * @returns {Error}
 */
export const setErrorMessage = (error?: string): Error => ({
    type: SET_ANALYSES_ERROR,
    error
});

/**
 * Sets whether or not the component is loading
 * @param {boolean} loading - whether or not it is loading
 */
export const setLoading = (loading: boolean): SetLoadingAction => ({
    type: SET_ANALYSES_LOADING,
    loading
});