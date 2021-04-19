export const SET_SIMPLE_APPLICATIONS_LOADING = "SET_SIMPLE_APPLICATIONS_LOADING";
export const SET_SIMPLE_PROJECTS_LOADING = "SET_SIMPLE_PROJECTS_LOADING";
export const SET_SIMPLE_SEARCH_ERROR = "SET_SIMPLE_SEARCH_ERROR";
export const SET_SIMPLE_SEARCH_SEARCH = "SET_SIMPLE_SEARCH_SEARCH";

import {initSimpleSearch} from "DefaultObjects";
import {SimpleSearchStateProps} from "Search";
import {SimpleSearchActions} from "./SearchActions";

const simpleSearch = (
    state: SimpleSearchStateProps = initSimpleSearch(),
    action: SimpleSearchActions
): SimpleSearchStateProps => {
    switch (action.type) {
        case SET_SIMPLE_APPLICATIONS_LOADING:
        case SET_SIMPLE_PROJECTS_LOADING:
        case SET_SIMPLE_SEARCH_SEARCH:
            return {...state, ...action.payload};
        case SET_SIMPLE_SEARCH_ERROR:
            if (!!action.payload.error) {
                return {...state, ...action.payload, errors: state.errors.concat([action.payload.error])};
            }
            return {...state, ...action.payload, errors: []};
        default:
            return state;
    }
};

export default simpleSearch;
