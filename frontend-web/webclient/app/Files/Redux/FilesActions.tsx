import { Cloud } from "Authentication/SDUCloudObject";
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
    RESET_FOLDER_EDITING,
    FILES_ERROR
} from "./FilesReducer";
import { failureNotification, getFilenameFromPath, replaceHomeFolder } from "UtilityFunctions";
import { Page, ReceivePage, SetLoadingAction, Action, Error } from "Types";
import { SortOrder, SortBy, File } from "..";

/**
* Creates a promise to fetch files. Sorts the files based on sorting function passed,
* and implicitly sets {filesLoading} to false in the reducer when the files are fetched.
* @param {string} path is the path of the folder being queried
* @param {number} itemsPerPage number of items to be fetched
* @param {Page<File>} page number of the page to be fetched
*/
export const fetchFiles = (path: string, itemsPerPage: number, page: number, order: SortOrder, sortBy: SortBy): Promise<ReceivePage<File> | Error> =>
    Cloud.get(`files?path=${path}&itemsPerPage=${itemsPerPage}&page=${page}&order=${order}&sortBy=${sortBy}`).then(({ response }) =>
        receiveFiles(response, path, order, sortBy)
    ).catch(() =>
        setErrorMessage(`An error occurred fetching files for ${getFilenameFromPath(replaceHomeFolder(path, Cloud.homeFolder))}`)
    );


export const setErrorMessage = (error?: string): Error => ({
    type: FILES_ERROR,
    error
})

/**
* Updates the files stored. 
* Intended for use when sorting the files, checking or favoriting, for instance.
* @param {Page<File>} page contains the currently held page with modifications made to the files client-side
*/
export const updateFiles = (page: Page<File>): ReceivePage<File> => ({
    type: UPDATE_FILES,
    page
});

/**
 * Sets whether or not the component is loading
 * @param {boolean} loading - whether or not it is loading
 */
export const setLoading = (loading: boolean): SetLoadingAction => ({
    type: SET_FILES_LOADING,
    loading
});

interface UpdatePathAction extends Action { path: string }
/**
 * Updates the path currently held intended for the files/fileinfo components.
 * @param {string} path - The current path for the component
 */
export const updatePath = (path: string): UpdatePathAction => ({
    type: UPDATE_PATH,
    path
});

interface ReceiveFiles extends ReceivePage<File> { path: string, sortOrder: SortOrder, sortBy: SortBy }
/**
 * The function used for the actual receiving the files, rather than the promise
 * @param {Page<File>} page - Contains the page
 * @param {string} path - The path the files were retrieved from
 * @param {SortOrder} sortOrder - The order in which the files were sorted
 * @param {SortBy} sortBy - the value the sorting was based on
 */
const receiveFiles = (page: Page<File>, path: string, sortOrder: SortOrder, sortBy: SortBy): ReceiveFiles => {
    page.items.forEach((f) => f.isChecked = false);
    return {
        type: RECEIVE_FILES,
        page,
        path,
        sortOrder,
        sortBy
    }
};

type SortingColumn = 0 | 1;
/**
 * Sets the selected 
 * @param index - the index of the sorting colum (0 or 1)
 * @param {SortOrder} asc - the order of the sorting. ASCENDING or DESCENDING
 * @param {SortBy} sortBy - what field the row should show
 */
export const setSortingColumn = (index: SortingColumn, asc: SortOrder, sortBy: SortBy) => ({
    type: SET_FILES_SORTING_COLUMN,
    index,
    asc,
    sortBy
});

interface FileSelectorShownAction extends Action { state: boolean }
export const fileSelectorShown = (state: boolean): FileSelectorShownAction => ({
    type: FILE_SELECTOR_SHOWN,
    state
});

interface ReceiveFileSelectorFilesAction extends ReceivePage<File> { path: string }
export const receiveFileSelectorFiles = (page: Page<File>, path: string): ReceiveFileSelectorFilesAction => ({
    type: RECEIVE_FILE_SELECTOR_FILES,
    page,
    path
});

export const fetchPageFromPath = (path: string, itemsPerPage: number, order: SortOrder, sortBy: SortBy): Promise<ReceivePage<File> | Action> =>
    Cloud.get(`files/lookup?path=${path}&itemsPerPage=${itemsPerPage}&order=${order}&sortBy=${sortBy}`)
        .then(({ response }) => receiveFiles(response, path, order, sortBy)).catch(() => {
            failureNotification(`An error occured fetching the page for file ${path}`);
            return { type: "ERROR" };
        });


export const fetchFileselectorFiles = (path: string, page: number, itemsPerPage: number) =>
    Cloud.get(`files?path=${path}&page=${page}&itemsPerPage=${itemsPerPage}`).then(({ response }) => {
        response.items.forEach(file => file.isChecked = false);
        return receiveFileSelectorFiles(response, path);
    }).catch(() => {
        failureNotification("An error occurred when fetching files for fileselection");
        return { type: "ERROR" }; // FIXME Will end up in default. Should have case for this
    });

/**
 * Sets the fileselector as loading. Intended for when retrieving files.
 */
export const setFileSelectorLoading = (): Action => ({
    type: SET_FILE_SELECTOR_LOADING
});


interface SetDisallowedPathsAction extends Action { paths: string[] }
/**
 * Sets paths for the file selector to omit.
 * @param {string[]} paths - the list of paths which shouldn't be displayed on
 * the fileselector modal.
 */
export const setDisallowedPaths = (paths: string[]): SetDisallowedPathsAction => ({
    type: SET_DISALLOWED_PATHS,
    paths
});

interface SetFileSelectorCallbackAction extends Action { callback: Function }
/**
 * Callback to be executed on fileselection in FileSelector
 * @param callback - callback to be being executed
 */
export const setFileSelectorCallback = (callback: Function): SetFileSelectorCallbackAction => ({
    type: SET_FILE_SELECTOR_CALLBACK,
    callback
});

interface SetEditingFileAction extends Action { editFileIndex: number }
/**
 * Sets the index of the file being edited.
 * @param editFileIndex - the index of the file in the current page being edited
 */
export const setEditingFile = (editFileIndex: number) => ({
    type: SET_EDITING_FILE,
    editFileIndex
});

interface SetCreatingFolder extends Action { creatingFolder: boolean }
/**
 * Sets the value of whether or not the user is creating a folder
 * @param {boolean} creatingFolder - whether or not the user is creating a folder
 */
export const setCreatingFolder = (creatingFolder: boolean): SetCreatingFolder => ({
    type: SET_CREATING_FOLDER,
    creatingFolder
});

/**
 * Sets the editing folder index to -1 (Meaning not currently editing a file), 
 * and sets creating folder to false.
 */
export const resetFolderEditing = (): Action => ({
    type: RESET_FOLDER_EDITING
})