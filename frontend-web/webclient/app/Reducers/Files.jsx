export const RECEIVE_FILES = "RECEIVE_FILES";
export const SET_FAVORITE = "SET_FAVORITE";
export const UPDATE_FILES_PER_PAGE = "UPDATE_FILES_PER_PAGE";
export const UPDATE_FILES = "UPDATE_FILES";
export const SET_FILES_LOADING = "SET_FILES_LOADING";
export const UPDATE_PATH = "UPDATE_PATH";
export const TO_FILES_PAGE = "TO_FILES_PAGE";
export const UPDATE_FILES_INFO_PATH = "UPDATE_FILES_INFO_PATH";
export const SET_FILES_SORTING_COLUMN = "SET_FILES_SORTING_COLUMN";
export const FILE_SELECTOR_SHOWN = "FILE_SELECTOR_SHOWN";
export const RECEIVE_FILE_SELECTOR_FILES = "RECEIVE_FILE_SELECTOR_FILES";
export const SET_FILE_SELECTOR_LOADING = "SET_FILE_SELECTOR_LOADING";
export const SET_FILE_SELECTOR_CALLBACK = "SET_FILE_SELECTOR_CALLBACK";

const files = (state = [], action) => {
    switch (action.type) {
        case RECEIVE_FILES: {
            return { ...state, files: action.files, loading: false, currentFilesPage: 0, fileSelectorFiles: action.files, fileSelectorPath: action.path };
        }
        case UPDATE_FILES_PER_PAGE: {
            return { ...state, files: action.files, filesPerPage: action.filesPerPage, currentFilesPage: 0 };
        }
        case UPDATE_FILES: {
            return { ...state, files: action.files };
        }
        case SET_FILES_LOADING: {
            return { ...state, loading: action.loading };
        }
        case UPDATE_PATH: {
            return { ...state, path: action.path };
        }
        case TO_FILES_PAGE: {
            return { ...state, currentFilesPage: action.pageNumber };
        }
        case SET_FILES_SORTING_COLUMN: {
            return { 
                ...state,
                sortingColumns: state.files.sortingColumns.map((sc) =>
                    sc.index === action.index ? { ...sc, name: action.name } : sc
                )
            };
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
        default: {
            return state;
        }
    }
}

export default files;