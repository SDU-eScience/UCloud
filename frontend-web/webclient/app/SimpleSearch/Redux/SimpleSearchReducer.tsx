export const SET_SIMPLE_FILES_LOADING = "SET_SIMPLE_FILES_LOADING";
export const SET_SIMPLE_APPLICATIONS_LOADING = "SET_SIMPLE_APPLICATIONS_LOADING";
export const SET_SIMPLE_PROJECTS_LOADING = "SET_SIMPLE_PROJECTS_LOADING";
export const SET_SIMPLE_SEARCH_ERROR = "SET_SIMPLE_SEARCH_ERROR";
export const RECEIVE_SIMPLE_FILES_PAGE = "RECEIVE_SIMPLE_FILES_PAGE";
export const RECEIVE_SIMPLE_APPLICATIONS_PAGE = "RECEIVE_SIMPLE_FILES_PAGE";
export const RECEIVE_SIMPLE_PROJECTS_PAGE = "RECEIVE_SIMPLE_PROJECTS_PAGE";
export const SET_SIMPLE_SEARCH_SEARCH = "SET_SIMPLE_SEARCH_SEARCH";

import { SimpleSearchActions } from "./SimpleSearchActions";
import { initSimpleSearch } from "DefaultObjects";
import { SimpleSearchStateProps } from "SimpleSearch";

const simpleSearch = (state: SimpleSearchStateProps = initSimpleSearch(), action: SimpleSearchActions): SimpleSearchStateProps => {
    switch (action.type) {
        case SET_SIMPLE_APPLICATIONS_LOADING:
        case SET_SIMPLE_FILES_LOADING:
        case SET_SIMPLE_PROJECTS_LOADING:
        case SET_SIMPLE_FILES_LOADING:
        case SET_SIMPLE_APPLICATIONS_LOADING:
        case SET_SIMPLE_PROJECTS_LOADING:
        case RECEIVE_SIMPLE_FILES_PAGE:
        case RECEIVE_SIMPLE_APPLICATIONS_PAGE:
        case RECEIVE_SIMPLE_PROJECTS_PAGE:
        case SET_SIMPLE_SEARCH_SEARCH:
            return { ...state, ...action.payload };
        case SET_SIMPLE_SEARCH_ERROR:
            if (!!action.payload.error) {
                return { ...state, errors: state.errors.concat([action.payload.error]) }
            }
            return { ...state, errors: [] }
        default:
            return state
    }
}

export default simpleSearch;