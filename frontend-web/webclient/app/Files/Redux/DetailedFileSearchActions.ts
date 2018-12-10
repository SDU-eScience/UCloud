import * as DFSReducer from "./DetailedFileSearchReducer";
import { Action } from "redux";
import { PayloadAction, Error, SetLoadingAction, Page } from "Types";
import { File, AdvancedSearchRequest, SensitivityLevel } from "Files";
import { Cloud } from "Authentication/SDUCloudObject";
import { advancedFileSearch } from "Utilities/FileUtilities";

export type DetailedFileSearchActions = ToggleFilesSearchHiddenAction | ToggleFoldersAllowedAction | SetTime |
    ToggleFilesAllowedAction | SetFilename | TagAction | SensitivityAction | SetError | SetFilesSearchLoading |
    ReceiveFilesSearchFiles | ExtensionAction

type ToggleFilesSearchHiddenAction = Action<typeof DFSReducer.DETAILED_FILES_TOGGLE_HIDDEN>;
export const toggleFilesSearchHidden = (): ToggleFilesSearchHiddenAction => ({
    type: DFSReducer.DETAILED_FILES_TOGGLE_HIDDEN
});

type ToggleFoldersAllowedAction = Action<typeof DFSReducer.DETAILED_FILES_TOGGLE_FOLDERS>;
export const toggleFoldersAllowed = (): ToggleFoldersAllowedAction => ({
    type: DFSReducer.DETAILED_FILES_TOGGLE_FOLDERS
});

type ToggleFilesAllowedAction = Action<typeof DFSReducer.DETAILED_FILES_TOGGLE_FILES>;
export const toggleFilesAllowed = (): ToggleFilesAllowedAction => ({
    type: DFSReducer.DETAILED_FILES_TOGGLE_FILES
});

type SetFilename = PayloadAction<typeof DFSReducer.DETAILED_FILES_SET_FILENAME, { fileName: string }>;
export const setFilename = (fileName: string): SetFilename => ({
    type: DFSReducer.DETAILED_FILES_SET_FILENAME,
    payload: { fileName }
});

type ExtensionTypes = typeof DFSReducer.DETAILED_FILES_ADD_EXTENSIONS | typeof DFSReducer.DETAILED_FILES_REMOVE_EXTENSIONS
type ExtensionAction = PayloadAction<ExtensionTypes, { extensions: string[] }>
export const extensionAction = (type: ExtensionTypes, extensions: string[]): ExtensionAction => ({
    type,
    payload: { extensions }
});

type TagTypes = typeof DFSReducer.DETAILED_FILES_ADD_TAGS | typeof DFSReducer.DETAILED_FILES_REMOVE_TAGS;
type TagAction = PayloadAction<TagTypes, { tags: string[] }>
export const tagAction = (type: TagTypes, tags: string[]): TagAction => ({
    type,
    payload: { tags }
});

type SensitivityTypes = typeof DFSReducer.DETAILED_FILES_ADD_SENSITIVITIES | typeof DFSReducer.DETAILED_FILES_REMOVE_SENSITIVITIES;
type SensitivityAction = PayloadAction<SensitivityTypes, { sensitivities }>
export const sensitivityAction = (type: SensitivityTypes, sensitivities: SensitivityLevel[]): SensitivityAction => ({
    type,
    payload: { sensitivities }
});

type SetError = Error<typeof DFSReducer.DETAILED_FILES_SET_ERROR>
export const setErrorMessage = (error?: string): SetError => ({
    type: DFSReducer.DETAILED_FILES_SET_ERROR,
    payload: { error }
});

type SetFilesSearchLoading = SetLoadingAction<typeof DFSReducer.DETAILED_FILES_SET_LOADING>
export const setFilesSearchLoading = (loading: boolean): SetFilesSearchLoading => ({
    type: DFSReducer.DETAILED_FILES_SET_LOADING,
    payload: { loading }
});

export const fetchFiles = (request: AdvancedSearchRequest): Promise<ReceiveFilesSearchFiles | SetError> =>
    Cloud.post<Page<File>>(advancedFileSearch, request)
        .then(it => receivePage(it.response))
        .catch(err => setErrorMessage(`An error occurred during the search: ${err}`));

type ReceiveFilesSearchFiles = PayloadAction<typeof DFSReducer.DETAILED_FILES_RECEIVE_PAGE, { page: Page<File> }>
export const receivePage = (page: Page<File>): ReceiveFilesSearchFiles => ({
    type: DFSReducer.DETAILED_FILES_RECEIVE_PAGE,
    payload: { page }
});

type SetTime = PayloadAction<typeof DFSReducer.DETAILED_FILES_SET_TIME, Times>
export const setTime = (times: Times): SetTime => ({
    type: DFSReducer.DETAILED_FILES_SET_TIME,
    payload: { ...times }
});

export interface Times {
    createdAfter?: Date
    modifiedAfter?: Date
    createdBefore?: Date
    modifiedBefore?: Date
}