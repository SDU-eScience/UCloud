import { Cloud } from "../../authentication/SDUCloudObject";
import { SET_LOADING, RECEIVE_ANALYSES, SET_PAGE_SIZE } from "../Reducers/Analyses";

export const fetchAnalyses = (analysesPerPage, currentPage) =>
    Cloud.get(`/hpc/jobs/?itemsPerPage=${analysesPerPage}&page=${currentPage}`)
         .then(({response}) => receiveAnalyses(response))

const receiveAnalyses = ({ items, itemsPerPage, pageNumber, pagesInTotal }) => ({
    type: RECEIVE_ANALYSES,
    analyses: items,
    analysesPerPage: itemsPerPage,
    pageNumber: pageNumber,
    totalPages: pagesInTotal
})

export const setPageSize = (pageSize) => ({
    type: SET_PAGE_SIZE,
    pageSize
});

export const setLoading = (loading) => ({
    type: SET_LOADING,
    loading
});