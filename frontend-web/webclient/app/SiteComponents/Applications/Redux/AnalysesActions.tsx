import { Cloud } from "../../../../authentication/SDUCloudObject";
import { failureNotification } from "../../../UtilityFunctions";
import { SET_ANALYSES_LOADING, RECEIVE_ANALYSES } from "./AnalysesReducer";
import { Page, ReceivePage, SetLoadingAction } from "../../../Types";
import { Analysis } from "../";

/**
 * Fetches a page of analyses based on the itemsPerPage and page provided
 * @param {number} itemsPerPage number of items the retrieved page should contain
 * @param {number} page the page number to be retrieved
 */
export const fetchAnalyses = (itemsPerPage: number, page: number): Promise<ReceivePage<Analysis> | SetLoadingAction> =>
    Cloud.get(`/hpc/jobs/?itemsPerPage=${itemsPerPage}&page=${page}`)
        .then(({ response }) => receiveAnalyses(response)).catch(() => {
            failureNotification("Retrieval of analyses failed, please try again later.");
            return setLoading(false);
        });

/**
 * Returns an action containing the page retrieved
 * @param {Page<Analysis>} page contains the analyses, pageNumber and items per page
 */
const receiveAnalyses = (page: Page<Analysis>): ReceivePage<Analysis> => ({
    type: RECEIVE_ANALYSES,
    page
});

/**
 * Sets whether or not the component is loading
 * @param {boolean} loading - whether or not it is loading
 */
export const setLoading = (loading: boolean): SetLoadingAction => ({
    type: SET_ANALYSES_LOADING,
    loading
});