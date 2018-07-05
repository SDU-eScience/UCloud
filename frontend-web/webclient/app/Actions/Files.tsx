import { Cloud } from "../../authentication/SDUCloudObject";
import {
    RECEIVE_FILES,
    UPDATE_FILES,
    SET_FILES_LOADING,
    UPDATE_PATH,
    SET_FILES_SORTING_COLUMN,
    FILE_SELECTOR_SHOWN,
    SET_FILE_SELECTOR_LOADING,
    RECEIVE_FILE_SELECTOR_FILES,
    SET_FILE_SELECTOR_CALLBACK,
    SET_DISALLOWED_PATHS,
    SET_CREATING_FOLDER,
    SET_EDITING_FILE,
    RESET_FOLDER_EDITING
} from "../Reducers/Files";
import { sortFilesByTypeAndName, failureNotification } from "../UtilityFunctions";
import { Page, emptyPage, File } from "../types/types";
import { SortOrder, SortBy } from "../SiteComponents/Files/Files";

/**
* Creates a promise to fetch files. Sorts the files based on sorting function passed,
* and implicitly sets {filesLoading} to false in the reducer when the files are fetched.
* @param {string} path is the path of the folder being queried
* @param {number} itemsPerPage number of items to be fetched
* @param {Page<File>} page number of the page to be fetched
*/
export const fetchFiles = (path: string, itemsPerPage: number, page: number, order: SortOrder, sortBy: SortBy) =>
    Cloud.get(`files?path=${path}&itemsPerPage=${itemsPerPage}&page=${page}&order=${order}&sortBy=${sortBy}`).then(({ response }) =>
        receiveFiles(response, path, order, sortBy)
    ).catch(() => {
        failureNotification("An error occurred fetching files for this folder.");
        return receiveFiles(emptyPage, path, order, sortBy);
    });

/**
* Updates the files stored. 
* Intended for use when sorting the files, checking or favoriting, for instance.
* @param {Page<File>} page contains the currently held page with modifications made to the files client-side
*/
export const updateFiles = (page: Page<File>) => ({
    type: UPDATE_FILES,
    page
});

/**
 * Sets whether or not the component is loading
 * @param {boolean} loading - whether or not it is loading
 */
export const setLoading = (loading: boolean) => ({
    type: SET_FILES_LOADING,
    loading
});

/**
 * Updates the path currently held intended for the files/fileinfo components.
 * @param {string} path - The current path for the component
 */
export const updatePath = (path: string) => ({
    type: UPDATE_PATH,
    path
});

/**
 * The function used for the actual receiving the files, rather than the promise
 * @param {Page<File>} page - Contains the page
 * @param {string} path - The path the files were retrieved from
 * @param {SortOrder} sortOrder - The order in which the files were sorted
 * @param {SortBy} sortBy - the value the sorting was based on
 */
const receiveFiles = (page: Page<File>, path: string, sortOrder: SortOrder, sortBy: SortBy) => {
    page.items.forEach((f) => f.isChecked = false);
    return {
        type: RECEIVE_FILES,
        page,
        path,
        sortOrder,
        sortBy
    }
};



/**
 * Sets the selected 
 * @param index - the index of the sorting colum (0 or 1)
 * @param {SortBy} sortBy - what field the row should show
 */
type SortingColumn = 0 | 1;
export const setSortingColumn = (index: SortingColumn, asc: SortOrder, sortBy: SortBy) => ({
    type: SET_FILES_SORTING_COLUMN,
    index,
    asc,
    sortBy
});

export const fileSelectorShown = (state) => ({
    type: FILE_SELECTOR_SHOWN,
    state
});

export const receiveFileSelectorFiles = (files, path) => ({
    type: RECEIVE_FILE_SELECTOR_FILES,
    files,
    path
});

export const fetchPageFromPath = (path: string, itemsPerPage: number, order: SortOrder, sortBy: SortBy) =>
    Cloud.get(`files/lookup?path=${path}&itemsPerPage=${itemsPerPage}&order=${order}&sortBy=${sortBy}`)
        .then(({ response }) => receiveFiles(response, path, order, sortBy)).catch(() => {
            failureNotification(`An error occured fetching the page for file ${path}`);
            return { type: "ERROR" };
        }
    );

// FIXME add pagination to FileSelector and rewrite this:
export const fetchFileselectorFiles = (path: string, page: number, itemsPerPage: number) =>
    Cloud.get(`files?path=${path}&page=${page}&itemsPerPage=${itemsPerPage}`).then(({ response }) => {
        let files = response.items;
        files.forEach(file => file.isChecked = false);
        files = sortFilesByTypeAndName(files, true);
        return receiveFileSelectorFiles(files, path);
    }).catch(() => {
        failureNotification("An error occurred when fetching files for fileselection");
        return { type: "ERROR" }; // FIXME Will end up in default. Should have case for this
    });

/**
 * Sets the fileselector as loading. Intended for when retrieving files.
 */
export const setFileSelectorLoading = () => ({
    type: SET_FILE_SELECTOR_LOADING
});

/**
 * Sets paths for the file selector to omit.
 * @param {string[]} paths - the list of paths which shouldn't be displayed on
 * the fileselector modal.
 */
export const setDisallowedPaths = (paths) => ({
    type: SET_DISALLOWED_PATHS,
    paths
});

/**
 * Callback to be executed on fileselection in FileSelector
 * @param callback - callback to be being executed
 */
export const setFileSelectorCallback = (callback) => ({
    type: SET_FILE_SELECTOR_CALLBACK,
    callback
});

/**
 * Sets the index of the file being edited.
 * @param editFileIndex - the index of the file in the current page being edited
 */
export const setEditingFile = (editFileIndex: number) => ({
    type: SET_EDITING_FILE,
    editFileIndex
});

/**
 * Sets the value of whether or not the user is creating a folder
 * @param {boolean} creatingFolder - whether or not the user is creating a folder
 */
export const setCreatingFolder = (creatingFolder: boolean) => ({
    type: SET_CREATING_FOLDER,
    creatingFolder
});

/**
 * Sets the editing folder index to -1 (Meaning not currently editing a file), 
 * and sets creating folder to false.
 */
export const resetFolderEditing = () => ({
    type: RESET_FOLDER_EDITING
})