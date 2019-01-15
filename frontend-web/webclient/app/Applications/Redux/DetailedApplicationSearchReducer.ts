import { DetailedApplicationSearchReduxState } from "Applications";
import { initApplicationsAdvancedSearch } from "DefaultObjects";
import { DetailedAppActions } from "./DetailedApplicationSearchActions";

export const DETAILED_APPS_SET_VERSION = "DETAILED_APPS_SET_VERSION";
export const DETAILED_APPS_SET_NAME = "DETAILED_APPS_SET_NAME";
export const DETAILED_APPLICATIONS_RECEIVE_PAGE = "DETAILED_APPLICATIONS_RECEIVE_PAGE";
export const DETAILED_APPLICATION_SET_ERROR = "DETAILED_APPLICATION_SET_ERROR";

const detailedApplicationSearch = (state: DetailedApplicationSearchReduxState = initApplicationsAdvancedSearch(), action: DetailedAppActions) => {
    switch (action.type) {
        case DETAILED_APPLICATION_SET_ERROR:
        case DETAILED_APPS_SET_VERSION:
        case DETAILED_APPS_SET_NAME: {
            return { ...state, ...action.payload };
        }
        default: {
            return state;
        }
    }
}

export default detailedApplicationSearch;