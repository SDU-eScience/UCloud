import { DetailedProjectSearch } from "Files";
import { initProjectsAdvancedSearch } from "DefaultObjects";

export const DETAILED_PROJECT_SEARCH_SET_ERROR = "DETAILED_PROJECT_SEARCH_SET_ERROR";
export const DETAILED_PROJECT_SET_NAME = "DETAILED_PROJECT_SET_NAME";

const detailedProjectSearch = (state: DetailedProjectSearch = initProjectsAdvancedSearch(), action) => {
    switch (action.type) {
        default:
            return state;
    }
}

export default detailedProjectSearch;