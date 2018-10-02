import { DetailedResultReduxObject, initDetailedResultReduxObject } from "DefaultObjects";

export const SET_DETAILED_RESULT_LOADING = "SET_DETAILED_RESULT_LOADING";
export const SET_DETAILED_RESULT_ERROR = "SET_DETAILED_RESULT_ERROR";
export const SET_DETAILED_RESULT_FILES_PAGE = "SET_DETAILED_RESULT_FILES_PAGE";

export const detailedResult = (state: DetailedResultReduxObject = initDetailedResultReduxObject(), action: any): DetailedResultReduxObject => {
    switch (action.type) {
        case SET_DETAILED_RESULT_ERROR:
        case SET_DETAILED_RESULT_LOADING:
        case SET_DETAILED_RESULT_FILES_PAGE: {
            // So, if an error occurs, payload will contain the error as well, making the following code viable.
            // So payload should be loading, or page/error
            return { ...state, ...action.payload }
        }
        default:
            return state;
    }
} 