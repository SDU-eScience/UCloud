import { Cloud } from "../../authentication/SDUCloudObject";
import { failureNotification } from "../UtilityFunctions";
import { SET_ANALYSES_LOADING, RECEIVE_ANALYSES, SET_ANALYSES_PAGE_SIZE } from "../Reducers/Analyses";
import { Page, Analysis, ReceivePage, SetLoadingAction, SetItemsPerPage } from "../types/types";

export const fetchAnalyses = (analysesPerPage: number, currentPage: number):Promise<ReceivePage<Analysis> | SetLoadingAction> =>
    Cloud.get(`/hpc/jobs/?itemsPerPage=${analysesPerPage}&page=${currentPage}`)
        .then(({ response }) => receiveAnalyses(response)).catch(() => {
            failureNotification("Retrieval of analyses failed, please try again later.");
            return setLoading(false);
        });

const receiveAnalyses = (page: Page<Analysis>):ReceivePage<Analysis> => ({
    type: RECEIVE_ANALYSES,
    page
});

export const setPageSize = (itemsPerPage: number):SetItemsPerPage => ({
    type: SET_ANALYSES_PAGE_SIZE,
    itemsPerPage
});

export const setLoading = (loading: boolean):SetLoadingAction => ({
    type: SET_ANALYSES_LOADING,
    loading
});