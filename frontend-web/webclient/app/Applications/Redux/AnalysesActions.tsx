import {AppState, JobState, JobWithStatus} from "Applications";
import {Client} from "Authentication/HttpClientInstance";
import {SortOrder} from "Files";
import {Action} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Page, PayloadAction, SetLoadingAction} from "Types";
import {hpcJobsQuery} from "Utilities/ApplicationUtilities";
import {errorMessageOrDefault} from "UtilityFunctions";
import {RunsSortBy} from "..";
import {
    CHECK_ALL_ANALYSES,
    CHECK_ANALYSIS,
    RECEIVE_ANALYSES,
    SET_ANALYSES_ERROR,
    SET_ANALYSES_LOADING
} from "./AnalysesReducer";

export type AnalysesActions = ReceiveAnalysesProps | AnalysesError | AnalysesLoading | CheckAnalysis | CheckAllAnalyses;

/**
 * Fetches a page of analyses based on the itemsPerPage and page provided
 * @param {number} itemsPerPage number of items the retrieved page should contain
 * @param {number} page the page number to be retrieved
 * @param {SortOrder} sortOrder the order the page should be sorted by
 * @param {RunsSortBy} sortBy the field the analyses should be
 * @param {number} minTimestamp
 * @param {number} maxTimestamp
 * @param {JobState} filter
 */
export const fetchAnalyses = async (
    itemsPerPage: number,
    page: number,
    sortOrder: SortOrder,
    sortBy: RunsSortBy,
    minTimestamp?: number,
    maxTimestamp?: number,
    filter?: JobState
): Promise<ReceiveAnalysesProps | AnalysesError> => {
    try {
        const {response} = await Client.get(
            hpcJobsQuery(itemsPerPage, page, sortOrder, sortBy, minTimestamp, maxTimestamp, filter)
        );
        return receiveAnalyses(response, sortBy, sortOrder);
    } catch (e) {
        snackbarStore.addFailure(errorMessageOrDefault(e, "Retrieval of analyses failed, please try again later."));
        return setError();
    }
};

type ReceiveAnalysesProps = PayloadAction<
    typeof RECEIVE_ANALYSES, {page: Page<JobWithStatus>, sortBy: RunsSortBy, sortOrder: SortOrder}
>;
/**
 * Returns an action containing the page retrieved
 * @param page contains the analyses, pageNumber and items per page
 * @param sortBy is the field the analyses are sorted by
 * @param sortOrder is the order the analyses are sorted
 */
const receiveAnalyses = (page: Page<JobWithStatus>, sortBy: RunsSortBy, sortOrder: SortOrder): ReceiveAnalysesProps => ({
    type: RECEIVE_ANALYSES,
    payload: {page, sortBy, sortOrder}
});

type AnalysesError = Action<typeof SET_ANALYSES_ERROR>;
/**
 * Action used to represent an error has occurred.
 * @returns {AnalysesError}
 */
export const setError = (): AnalysesError => ({
    type: SET_ANALYSES_ERROR
});


type AnalysesLoading = SetLoadingAction<typeof SET_ANALYSES_LOADING>;
/**
 * Sets whether or not the component is loading
 * @param {boolean} loading - whether or not it is loading
 */
export const setLoading = (loading: boolean): AnalysesLoading => ({
    type: SET_ANALYSES_LOADING,
    payload: {loading}
});

type CheckAllAnalyses = PayloadAction<typeof CHECK_ALL_ANALYSES, {checked: boolean}>;
export const checkAllAnalyses = (checked: boolean) => ({
    type: CHECK_ALL_ANALYSES,
    payload: {
        checked
    }
});

type CheckAnalysis = PayloadAction<typeof CHECK_ANALYSIS, {jobId: string, checked: boolean}>;
export const checkAnalysis = (jobId: string, checked: boolean) => ({
    type: CHECK_ANALYSIS,
    payload: {
        jobId,
        checked
    }
});
