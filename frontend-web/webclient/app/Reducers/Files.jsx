export const RECEIVE_FILES = "RECEIVE_FILES";
export const SET_FAVORITE = "SET_FAVORITE";
export const UPDATE_FILES_PER_PAGE = "UPDATE_FILES_PER_PAGE";
export const UPDATE_FILES = "UPDATE_FILES";
export const SET_LOADING = "SET_LOADING";
export const UPDATE_PATH = "UPDATE_PATH";
export const TO_PAGE = "TO_PAGE";
export const UPDATE_FILES_INFO_PATH = "UPDATE_FILES_INFO_PATH";

const files = (state = [], action) => {
    switch (action.type) {
        case RECEIVE_FILES: {
            return { ...state, files: action.files, loading: false };
        }
        case UPDATE_FILES_PER_PAGE: {
            return { ...state, files: action.files, filesPerPage: action.filesPerPage, currentFilesPage: 0 };
        }
        case UPDATE_FILES: {
            return { ...state, files: action.files };
        }
        case SET_LOADING: {
            return { ...state, loading: action.loading };
        }
        case UPDATE_PATH: {
            return { ...state, path: action.path };
        }
        case TO_PAGE: {
            return { ...state, currentFilesPage: action.pageNumber };
        }
        default: {
            return state;
        }
    }
}

export default files;