import { Cloud } from "../../authentication/SDUCloudObject";
import {
    RECEIVE_FILES,
    UPDATE_FILES_PER_PAGE,
    UPDATE_FILES,
    SET_LOADING,
    UPDATE_PATH,
    TO_PAGE,
    UPDATE_FILES_INFO_PATH
} from "../Reducers/Files";
import { getParentPath } from "../UtilityFunctions";

/*
* Creates a promise to fetch files. Sorts the files based on sorting function passed,
* and implicitely sets @filesLoading to false in the reducer when the files are fetched.
*/
export const fetchFiles = (path, sorting, sortAscending) =>
    Cloud.get(`files?path=${path}`).then(({ response }) => {
        response.forEach(file => file.isChecked = false);
        if (sorting) {
            response = sorting(response, sortAscending);
        }
        return receiveFiles(response);
    });

export const toPage = (pageNumber) => ({
    type: TO_PAGE,
    pageNumber: pageNumber
});

/*
* Updates the files stored. 
* Intended for use when sorting the files, checking or favoriting, for instance.
*/
export const updateFiles = (files) => {
    return {
        type: UPDATE_FILES,
        files
    }
};

export const setLoading = (loading) => ({
    type: SET_LOADING,
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

const receiveFiles = (files) => ({
    type: RECEIVE_FILES,
    files,
});