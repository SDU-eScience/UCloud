import { Cloud } from "../../authentication/SDUCloudObject";
import {
    RECEIVE_FILES,
    UPDATE_FILES_PER_PAGE,
    UPDATE_FILES,
    SET_FILES_LOADING,
    UPDATE_PATH,
    TO_FILES_PAGE,
    UPDATE_FILES_INFO_PATH,
    SET_FILES_SORTING_COLUMN,
    FILE_SELECTOR_SHOWN,
    SET_FILE_SELECTOR_LOADING,
    RECEIVE_FILE_SELECTOR_FILES,
    SET_FILE_SELECTOR_CALLBACK,
    SET_DISALLOWED_PATHS
} from "../Reducers/Files";
import { getParentPath, sortFilesByTypeAndName } from "../UtilityFunctions";

/**
** Creates a promise to fetch files. Sorts the files based on sorting function passed,
** and implicitly sets @filesLoading to false in the reducer when the files are fetched.
** Additionally sets files for the file selector as well as 
**/
export const fetchFiles = (path, sorting, sortAscending) =>
    Cloud.get(`files?path=${path}`).then(({ response }) => {
        response.forEach(file => file.isChecked = false);
        if (sorting) {
            response = sorting(response, sortAscending);
        }
        return receiveFiles(response, path);
    });

export const toPage = (pageNumber) => ({
    type: TO_FILES_PAGE,
    pageNumber: pageNumber
});

/*
* Updates the files stored. 
* Intended for use when sorting the files, checking or favoriting, for instance.
*/
export const updateFiles = (files) => ({
    type: UPDATE_FILES,
    files
});

export const setLoading = (loading) => ({
    type: SET_FILES_LOADING,
    loading
});

export const updatePath = (newPath) => ({
    type: UPDATE_PATH,
    path: newPath
});

export const updateFilesPerPage = (filesPerPage, files) => {
    files.forEach(file => file.isChecked = false);
    return {
        type: UPDATE_FILES_PER_PAGE,
        filesPerPage,
        files
    }
};

const receiveFiles = (files, path) => ({
    type: RECEIVE_FILES,
    files,
    path
});

export const setSortingColumn = (index, name) => ({
    type: SET_FILES_SORTING_COLUMN,
    index,
    name
});

export const fileSelectorShown = (state) => ({
    type: FILE_SELECTOR_SHOWN,
    state
});

export const receiveFileSelectorFiles = (files, path) => ({
    type: RECEIVE_FILE_SELECTOR_FILES,
    files,
    path
})

export const fetchFileselectorFiles = (path) =>
    Cloud.get(`files?path=${path}`).then(({ response }) => {
        response.forEach(file => file.isChecked = false);
        response = sortFilesByTypeAndName(response, true);
        return receiveFileSelectorFiles(response, path);
    });

export const setFileSelectorLoading = () => ({
    type: SET_FILE_SELECTOR_LOADING
})

export const setDisallowedPaths = (paths) => ({
    type: SET_DISALLOWED_PATHS,
    paths
});

export const setFileSelectorCallback = (callback) => ({
    type: SET_FILE_SELECTOR_CALLBACK,
    callback
});