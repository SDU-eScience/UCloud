import { Cloud } from "../../authentication/SDUCloudObject";
import { failureNotification } from "../UtilityFunctions";
import { SET_ANALYSES_LOADING, RECEIVE_ANALYSES, SET_ANALYSES_PAGE_SIZE } from "../Reducers/Analyses";
import { Page, Analysis } from "../types/types";

export const fetchAnalyses = (analysesPerPage: number, currentPage: number) =>
    Cloud.get(`/hpc/jobs/?itemsPerPage=${analysesPerPage}&page=${currentPage}`)
        .then(({ response }) => receiveAnalyses(response)).catch(() => {
            failureNotification("Retrieval of analyses failed, please try again later.");
            return setLoading(false);
        });

const receiveAnalyses = (page: Page<Analysis>) => ({
    type: RECEIVE_ANALYSES,
    page: page
});

export const setPageSize = (pageSize: number) => ({
    type: SET_ANALYSES_PAGE_SIZE,
    pageSize
});

export const setLoading = (loading: boolean) => ({
    type: SET_ANALYSES_LOADING,
    loading
});