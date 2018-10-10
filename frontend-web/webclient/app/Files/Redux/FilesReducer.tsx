import { FileActions } from "./FilesActions";
import { FilesReduxObject, initFiles } from "DefaultObjects";

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
export const FILES_ERROR = "FILES_ERROR";
export const SET_FILE_SELECTOR_ERROR = "SET_FILE_SELECTOR_ERROR";

const files = (state: FilesReduxObject = initFiles({ homeFolder: "" }), action: FileActions): FilesReduxObject => {
    switch (action.type) {
        case RECEIVE_FILES: {
            return {
                ...state,
                page: action.payload.page,
                loading: false,
                fileSelectorPath: action.payload.path,
                fileSelectorPage: action.payload.page,
                sortOrder: action.payload.sortOrder,
                sortBy: action.payload.sortBy,
                error: undefined,
                fileSelectorError: undefined,
            };
        }
        case SET_FILES_LOADING:
        case UPDATE_FILES: {
            return { ...state, ...action.payload };
        }
        case UPDATE_PATH: {
            return { ...state, path: action.path, fileSelectorPath: action.path };
        }
        case FILE_SELECTOR_SHOWN: {
            return { ...state, fileSelectorShown: action.payload.state };
        }
        case RECEIVE_FILE_SELECTOR_FILES: {
            return { ...state, fileSelectorPage: action.payload.page, fileSelectorPath: action.payload.path, fileSelectorLoading: false };
        }
        case SET_FILE_SELECTOR_LOADING: {
            return { ...state, fileSelectorLoading: true };
        }
        case SET_FILE_SELECTOR_CALLBACK: {
            return { ...state, fileSelectorCallback: action.callback };
        }
        case SET_FILE_SELECTOR_ERROR: {
            return { ...state, fileSelectorError: action.payload.error }
        }
        case SET_DISALLOWED_PATHS: {
            return { ...state, disallowedPaths: action.payload.paths }
        }
        case FILES_ERROR: {
            return { ...state, error: action.payload.error, loading: false };
        }
        case SET_FILES_SORTING_COLUMN: {
            const { sortingColumns } = state;
            sortingColumns[action.index] = action.sortingColumn;
            window.localStorage.setItem(`filesSorting${action.index}`, action.sortingColumn);
            return { ...state, sortingColumns };
        }
        default: {
            return state;
        }
    }
}

export default files;