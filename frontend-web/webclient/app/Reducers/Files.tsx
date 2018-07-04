export const RECEIVE_FILES = "RECEIVE_FILES";
export const UPDATE_FILES = "UPDATE_FILES";
export const SET_FILES_LOADING = "SET_FILES_LOADING";
export const UPDATE_PATH = "UPDATE_PATH";
export const UPDATE_FILES_INFO_PATH = "UPDATE_FILES_INFO_PATH";
export const SET_FILES_SORTING_COLUMN = "SET_FILES_SORTING_COLUMN";
export const FILE_SELECTOR_SHOWN = "FILE_SELECTOR_SHOWN";
export const RECEIVE_FILE_SELECTOR_FILES = "RECEIVE_FILE_SELECTOR_FILES";
export const SET_FILE_SELECTOR_LOADING = "SET_FILE_SELECTOR_LOADING";
export const SET_FILE_SELECTOR_CALLBACK = "SET_FILE_SELECTOR_CALLBACK";
export const SET_DISALLOWED_PATHS = "SET_DISALLOWED_PATHS";

const files = (state: any = {}, action) => {
    switch (action.type) {
        case RECEIVE_FILES: {
            return { ...state, page: action.page, loading: false, fileSelectorFiles: action.page.items, fileSelectorPath: action.path, sortOrder: action.sortOrder, sortBy: action.sortBy };
        }
        case UPDATE_FILES: {
            return { ...state, page: action.page };
        }
        case SET_FILES_LOADING: {
            return { ...state, loading: action.loading };
        }
        case UPDATE_PATH: {
            return { ...state, path: action.path };
        }
        case FILE_SELECTOR_SHOWN: {
            return { ...state, fileSelectorShown: action.state };
        }
        case RECEIVE_FILE_SELECTOR_FILES: {
            return { ...state, fileSelectorFiles: action.files, fileSelectorPath: action.path, fileSelectorLoading: false };
        }
        case SET_FILE_SELECTOR_LOADING: {
            return { ...state, fileSelectorLoading: true };
        }
        case SET_FILE_SELECTOR_CALLBACK: {
            return { ...state, fileSelectorCallback: action.callback };
        }
        case SET_DISALLOWED_PATHS: {
            return { ...state, disallowedPaths: action.paths }
        }
        default: {
            return state;
        }
    }
}

export default files;