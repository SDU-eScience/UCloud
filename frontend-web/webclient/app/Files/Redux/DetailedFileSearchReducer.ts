import {DetailedFileSearchReduxState} from "Files";
import {DetailedFileSearchActions} from "./DetailedFileSearchActions";
import {initFilesDetailedSearch} from "DefaultObjects";
export const DETAILED_FILES_TOGGLE_HIDDEN = "DETAILED_FILES_TOGGLE_HIDDEN";
export const DETAILED_FILES_TOGGLE_FOLDERS = "DETAILED_FILES_TOGGLE_FOLDERS";
export const DETAILED_FILES_TOGGLE_FILES = "DETAILED_FILES_TOGGLE_FILES";
export const DETAILED_FILES_TOGGLE_INCLUDE_SHARES = "DETAILED_FILES_TOGGLE_INCLUDE_SHARES";
export const DETAILED_FILES_SET_FILENAME = "DETAILED_FILES_SET_FILENAME";
export const DETAILED_FILES_SET_LOADING = "DETAILED_FILES_SET_LOADING";
export const DETAILED_FILES_SET_TIME = "DETAILED_FILES_SET_TIME";
export const DETAILED_FILES_SET_ERROR = "DETAILED_FILES_SET_ERROR";
export const DETAILED_FILES_RECEIVE_PAGE = "DETAILED_FILES_SET_ERROR";
export const DETAILED_FILES_REMOVE_TAGS = "DETAILED_FILES_REMOVE_TAGS";
export const DETAILED_FILES_ADD_TAGS = "DETAILED_FILES_ADD_TAGS";
export const DETAILED_FILES_REMOVE_EXTENSIONS = "DETAILED_FILES_REMOVE_EXTENSIONS";
export const DETAILED_FILES_ADD_EXTENSIONS = "DETAILED_FILES_ADD_EXTENSIONS";
export const DETAILED_FILES_REMOVE_SENSITIVITIES = "DETAILED_FILES_REMOVE_SENSITIVITIES";
export const DETAILED_FILES_ADD_SENSITIVITIES = "DETAILED_FILES_ADD_SENSITIVITIES";

const detailedFileSearch = (state: DetailedFileSearchReduxState = initFilesDetailedSearch(), action: DetailedFileSearchActions): DetailedFileSearchReduxState => {
    switch (action.type) {
        case DETAILED_FILES_TOGGLE_HIDDEN: {
            return {...state, hidden: !state.hidden};
        }
        case DETAILED_FILES_TOGGLE_FOLDERS: {
            return {...state, allowFolders: !state.allowFolders};
        }
        case DETAILED_FILES_TOGGLE_FILES: {
            return {...state, allowFiles: !state.allowFiles};
        }
        case DETAILED_FILES_TOGGLE_INCLUDE_SHARES: {
            return {...state, includeShares: !state.includeShares};
        }
        // FIXME Not DRY compliant
        case DETAILED_FILES_REMOVE_TAGS: {
            const {tags} = state;
            action.payload.tags.forEach(it => tags.delete(it));
            return {...state, tags};
        }
        case DETAILED_FILES_ADD_TAGS: {
            const {tags} = state;
            action.payload.tags.forEach(it => tags.add(it));
            return {...state, tags};
        }
        case DETAILED_FILES_REMOVE_EXTENSIONS: {
            const {extensions} = state;
            action.payload.extensions.forEach(it => extensions.delete(it));
            return {...state, extensions};
        }
        case DETAILED_FILES_ADD_EXTENSIONS: {
            const {extensions} = state;
            action.payload.extensions.forEach(it => extensions.add(it));
            return {...state, extensions};
        }
        case DETAILED_FILES_REMOVE_SENSITIVITIES: {
            const {sensitivities} = state;
            action.payload.sensitivities.forEach(it => sensitivities.delete(it));
            return {...state, sensitivities};
        }
        case DETAILED_FILES_ADD_SENSITIVITIES: {
            const {sensitivities} = state;
            action.payload.sensitivities.forEach(it => sensitivities.add(it));
            return {...state, sensitivities};
        }
        // FIXME END Not DRY compliant

        case DETAILED_FILES_SET_FILENAME:
        case DETAILED_FILES_SET_LOADING:
        case DETAILED_FILES_RECEIVE_PAGE:
        case DETAILED_FILES_SET_TIME:
            return {...state, ...action.payload};
        case DETAILED_FILES_SET_ERROR:
        default:
            return state;
    }
};

export default detailedFileSearch;