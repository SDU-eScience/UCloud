import { Cloud } from "Authentication/SDUCloudObject";
import { SET_ANALYSES_LOADING, RECEIVE_ANALYSES, SET_ANALYSES_ERROR } from "./AnalysesReducer";
import { Page, ReceivePage, SetLoadingAction, Error } from "Types";
import { Analysis } from "..";


export type AnalysesActions = ReceiveAnalysesProps | AnalysesError | AnalysesLoading;

/**
 * Fetches a page of analyses based on the itemsPerPage and page provided
 * @param {number} itemsPerPage number of items the retrieved page should contain
 * @param {number} page the page number to be retrieved
 */
export const fetchAnalyses = (itemsPerPage: number, page: number): Promise<ReceiveAnalysesProps | AnalysesError> =>
    Cloud.get(`/hpc/jobs/?itemsPerPage=${itemsPerPage}&page=${page}`)
        .then(({ response }) => receiveAnalyses(response)).catch(() =>
            setErrorMessage("Retrieval of analyses failed, please try again later.")
        );

type ReceiveAnalysesProps = ReceivePage<typeof RECEIVE_ANALYSES, Analysis>
/**
 * Returns an action containing the page retrieved
 * @param {Page<Analysis>} page contains the analyses, pageNumber and items per page
 */
const receiveAnalyses = (page: Page<Analysis>): ReceiveAnalysesProps => ({
    type: RECEIVE_ANALYSES,
    payload: { page }
});

type AnalysesError = Error<typeof SET_ANALYSES_ERROR>
/**
 * Used to set or remove the error for the component
 * @param {string?} error The error to be renered in the component. Nothing will be rendered if string is null
 * @returns {Error}
 */
export const setErrorMessage = (error?: string): AnalysesError => ({
    type: SET_ANALYSES_ERROR,
    payload: { error }
});


type AnalysesLoading = SetLoadingAction<typeof SET_ANALYSES_LOADING>
/**
 * Sets whether or not the component is loading
 * @param {boolean} loading - whether or not it is loading
 */
export const setLoading = (loading: boolean): AnalysesLoading => ({
    type: SET_ANALYSES_LOADING,
    payload: { loading }
});