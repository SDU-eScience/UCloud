import { DetailedResultReduxObject, initDetailedResult } from "DefaultObjects";
import { DetailedResultActions } from "./DetailedResultActions";

export const SET_DETAILED_RESULT_LOADING = "SET_DETAILED_RESULT_LOADING";
export const SET_DETAILED_RESULT_ERROR = "SET_DETAILED_RESULT_ERROR";
export const SET_DETAILED_RESULT_FILES_PAGE = "SET_DETAILED_RESULT_FILES_PAGE";

const detailedResult = (state: DetailedResultReduxObject = initDetailedResult(), action: DetailedResultActions): DetailedResultReduxObject => {
    switch (action.type) {
        case SET_DETAILED_RESULT_ERROR:
        case SET_DETAILED_RESULT_LOADING:
        case SET_DETAILED_RESULT_FILES_PAGE:
            return { ...state, ...action.payload }
        default:
            return state;
    }
} 

export default detailedResult;