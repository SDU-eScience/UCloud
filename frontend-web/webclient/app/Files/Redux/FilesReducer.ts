import { FileActions } from "./FilesActions";
import { FilesReduxObject, initFiles, emptyPage } from "DefaultObjects";
import { newMockFolder } from "Utilities/FileUtilities";

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
export const CHECK_ALL_FILES = "CHECK_ALL_FILES";
export const CHECK_FILE = "CHECK_FILE";
export const CREATE_FOLDER = "CREATE_FOLDER";
export const FILES_INVALID_PATH = "FILES_INVALID_PATH";

function files(state: FilesReduxObject = initFiles(""), action: FileActions): FilesReduxObject {
    switch (action.type) {
        case RECEIVE_FILES: {
            return {
                ...state,
                page: action.payload.page,
                loading: false,
                path: action.payload.path,
                fileSelectorPath: action.payload.path,
                fileSelectorPage: action.payload.page,
                sortOrder: action.payload.sortOrder,
                sortBy: action.payload.sortBy,
                error: undefined,
                fileSelectorError: undefined,
                invalidPath: false
            };
        };
        case SET_FILES_LOADING:
        case FILES_INVALID_PATH:
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
            return { ...state, fileSelectorCallback: action.payload.callback };
        }
        case SET_FILE_SELECTOR_ERROR: {
            return { ...state, fileSelectorError: action.payload.error, fileSelectorLoading: false }
        }
        case SET_DISALLOWED_PATHS: {
            return { ...state, disallowedPaths: action.payload.paths }
        }
        case FILES_ERROR: {
            return { ...state, error: action.payload.error, loading: false, page: emptyPage };
        }
        case CHECK_ALL_FILES: {
            return {
                ...state, page: {
                    ...state.page, items: state.page.items.map(f => {
                        f.isChecked = action.payload.checked;
                        return f;
                    })
                }
            }
        }
        case CHECK_FILE: {
            return {
                ...state, page: {
                    ...state.page, items: state.page.items.map((f) => {
                        if (action.payload.path === f.path) f.isChecked = action.payload.checked
                        return f;
                    })
                }
            }
        }
        case SET_FILES_SORTING_COLUMN: {
            const { sortingColumns } = state;
            sortingColumns[action.index] = action.sortingColumn;
            window.localStorage.setItem(`filesSorting${action.index}`, action.sortingColumn);
            return { ...state, sortingColumns };
        }
        case CREATE_FOLDER: {
            if (state.page.items.some(it => !!it.isMockFolder)) return state;
            return {
                ...state, page: {
                    ...state.page, items: [newMockFolder()].concat([...state.page.items])
                }
            };
        }
        default: {
            return state;
        }
    }
}

export default files;