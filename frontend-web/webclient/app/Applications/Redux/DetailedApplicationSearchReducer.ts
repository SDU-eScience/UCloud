import { DetailedApplicationSearchReduxState } from "Applications";
import { initApplicationsAdvancedSearch } from "DefaultObjects";

export const DETAILED_APPS_SET_VERSION = "DETAILED_APPS_SET_VERSION";
export const DETAILED_APPS_SET_NAME = "DETAILED_APPS_SET_NAME";
export const DETAILED_APPLICATIONS_RECEIVE_PAGE = "DETAILED_APPLICATIONS_RECEIVE_PAGE";

const detailedApplicationSearch = (state: DetailedApplicationSearchReduxState = initApplicationsAdvancedSearch(), action) => {
    switch (action) {
        case DETAILED_APPS_SET_VERSION:
        case DETAILED_APPS_SET_NAME:
        case DETAILED_APPLICATIONS_RECEIVE_PAGE: {
            return { ...state, ...action.payload };
        }
        default: {
            return state;
        }
    }
}

export default detailedApplicationSearch;