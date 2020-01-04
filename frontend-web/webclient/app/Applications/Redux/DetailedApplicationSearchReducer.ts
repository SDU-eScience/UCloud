import {DetailedApplicationSearchReduxState} from "Applications";
import {initApplicationsAdvancedSearch} from "DefaultObjects";
import {DetailedAppActions} from "./DetailedApplicationSearchActions";

export const DETAILED_APPS_SET_NAME = "DETAILED_APPS_SET_NAME";
export const DETAILED_APPLICATION_SET_ERROR = "DETAILED_APPLICATION_SET_ERROR";
export const DETAILED_APPS_ADD_TAG = "DETAILED_APPS_ADD_TAG";
export const DETAILED_APPS_REMOVE_TAG = "DETAILED_APPS_REMOVE_TAG";
export const DETAILED_APPS_CLEAR_TAGS = "DETAILED_APPS_CLEAR_TAGS";
export const DETAILED_APPS_SHOW_ALL_VERSIONS = "DETAILED_APPS_SHOW_ALL_VERSIONS"

const detailedApplicationSearch = (
    state: DetailedApplicationSearchReduxState = initApplicationsAdvancedSearch(),
    action: DetailedAppActions
): DetailedApplicationSearchReduxState => {
    switch (action.type) {
        case DETAILED_APPS_SET_NAME: {
            return {...state, ...action.payload};
        }
        case DETAILED_APPS_ADD_TAG: {
            const {tags} = state;
            if (action.payload.tag) tags.add(action.payload.tag.trim());
            return {...state, tags};
        }
        case DETAILED_APPS_REMOVE_TAG: {
            const {tags} = state;
            tags.delete(action.payload.tag.trim());
            return {...state, tags};
        }
        case DETAILED_APPS_CLEAR_TAGS: {
            return {...state, tags: new Set()};
        }
        case DETAILED_APPS_SHOW_ALL_VERSIONS: {
            return {...state, showAllVersions: !state.showAllVersions };
        }
        case DETAILED_APPLICATION_SET_ERROR:
        default: {
            return state;
        }
    }
};

export default detailedApplicationSearch;
