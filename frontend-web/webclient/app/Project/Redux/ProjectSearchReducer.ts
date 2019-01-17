import { DetailedProjectSearch } from "Files";
import { initProjectsAdvancedSearch } from "DefaultObjects";
import { ProjectSearchActions } from "./ProjectSearchActions";

export const DETAILED_PROJECT_SEARCH_SET_ERROR = "DETAILED_PROJECT_SEARCH_SET_ERROR";
export const DETAILED_PROJECT_SET_NAME = "DETAILED_PROJECT_SET_NAME";

const detailedProjectSearch = (state: DetailedProjectSearch = initProjectsAdvancedSearch(), action: ProjectSearchActions) => {
    switch (action.type) {
        case DETAILED_PROJECT_SET_NAME:
        case DETAILED_PROJECT_SEARCH_SET_ERROR:
            return { ...state, ...action.payload };
        default:
            return state;
    }
}

export default detailedProjectSearch;