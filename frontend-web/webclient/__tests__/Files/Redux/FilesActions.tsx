import * as FileActions from "Files/Redux/FilesActions";
import { mockFiles_SensitivityConfidential } from "../../mock/Files";
import { createStore, combineReducers } from "redux";
import { emptyPage } from "DefaultObjects";
import { SortBy, SortOrder } from "Files";
import files from "Files/Redux/FilesReducer";

const addPromiseSupportToDispatch = (store) => {
    const rawDispatch = store.dispatch;
    return (action) => {
        if (typeof action.then === "function") {
            return action.then(rawDispatch);
        }
        return rawDispatch(action);
    };
};

const rootReducer = combineReducers({
    files
});

const configureStore = (initialObject) => {
    const store = createStore(rootReducer, initialObject);
    store.dispatch = addPromiseSupportToDispatch(store);
    return store;
};

const emptyPageStore = configureStore({
    files: {
        page: emptyPage,
        sortOrder: SortOrder.ASCENDING,
        sortBy: SortBy.PATH,
        loading: false,
        error: undefined,
        path: "",
        filesInfoPath: "",
        sortingColumns: [SortBy.PATH, SortBy.MODIFIED_AT],
        fileSelectorLoading: false,
        fileSelectorShown: false,
        fileSelectorPage: emptyPage,
        fileSelectorPath: "/home/Home",
        fileSelectorCallback: () => null,
        fileSelectorError: undefined,
        disallowedPaths: []
    }
});

test("Check all files in empty page", () => {
    emptyPageStore.dispatch(FileActions.checkAllFiles(true, emptyPageStore.getState().files.page));
    expect(emptyPageStore.getState().files).toBe(emptyPageStore.getState().files);
});

const nonEmptyPageStore = { ...emptyPageStore };
nonEmptyPageStore.getState().files.page = mockFiles_SensitivityConfidential;

test("Check all files in non-empty page", () => {
    const checked = true;
    nonEmptyPageStore.dispatch(FileActions.checkAllFiles(checked, nonEmptyPageStore.getState().files.page));
    const page = { ...nonEmptyPageStore.getState().files.page }
    page.items.forEach(f => f.checked = checked);
    expect(nonEmptyPageStore.getState().files.page).toEqual(page);
});

test("Remove check from all files in non-empty page", () => {
    const checked = true;
    nonEmptyPageStore.dispatch(FileActions.checkAllFiles(checked, nonEmptyPageStore.getState().files.page));
    nonEmptyPageStore.dispatch(FileActions.checkAllFiles(!checked, nonEmptyPageStore.getState().files.page));
    const page = { ...nonEmptyPageStore.getState().files.page }
    page.items.forEach(f => f.checked = !checked);
    expect(nonEmptyPageStore.getState().files.page).toEqual(page);
});

test("Set Files as loading", () => {
    nonEmptyPageStore.dispatch(FileActions.setLoading(true));
    expect(nonEmptyPageStore.getState().files.loading).toBe(true);
});

test("Set Files as not loading", () => {
    nonEmptyPageStore.dispatch(FileActions.setLoading(false));
    expect(nonEmptyPageStore.getState().files.loading).toBe(false);
});

test("Set Files error", () => {
    const ErrorMessage = "Error_Message";
    nonEmptyPageStore.dispatch(FileActions.setErrorMessage(ErrorMessage));
    expect(nonEmptyPageStore.getState().files.error).toBe(ErrorMessage);
})

test("Clear Files error", () => {
    const ErrorMessage = undefined;
    nonEmptyPageStore.dispatch(FileActions.setErrorMessage(ErrorMessage));
    expect(nonEmptyPageStore.getState().files.error).toBe(ErrorMessage);
});

test("Show file selector", () => {
    nonEmptyPageStore.dispatch(FileActions.fileSelectorShown(true));
    expect(nonEmptyPageStore.getState().files.fileSelectorShown).toBe(true);
});

test("Hide file selector", () => {
    nonEmptyPageStore.dispatch(FileActions.fileSelectorShown(false));
    expect(nonEmptyPageStore.getState().files.fileSelectorShown).toBe(false);
});

// setDisallowedPaths

test("Set disallowed paths", () => {
    const disallowedPaths = ["1", "2", "3"]
    nonEmptyPageStore.dispatch(FileActions.setDisallowedPaths(disallowedPaths));
    expect(nonEmptyPageStore.getState().files.disallowedPaths);
});

test("Clear disallowed paths", () => {
    const disallowedPaths = []
    nonEmptyPageStore.dispatch(FileActions.setDisallowedPaths(disallowedPaths));
    expect(nonEmptyPageStore.getState().files.disallowedPaths);
});


test("Set FileSelector loading", () => {
    nonEmptyPageStore.dispatch(FileActions.setFileSelectorLoading());
    expect(nonEmptyPageStore.getState().files.fileSelectorLoading).toBe(true);
});

test("Set fileselector callback", () => {
    const callback = () => 42;
    nonEmptyPageStore.dispatch(FileActions.setFileSelectorCallback(callback));
    expect(nonEmptyPageStore.getState().files.fileSelectorCallback()).toBe(callback());
});

test("Clear fileselector callback", () => {
    nonEmptyPageStore.dispatch(FileActions.setFileSelectorCallback(() => undefined));
    expect(nonEmptyPageStore.getState().files.fileSelectorCallback()).toBe(undefined);
});

test("Set File Selector error", () => {
    const ErrorMessage = "Error_Message";
    nonEmptyPageStore.dispatch(FileActions.setFileSelectorError(ErrorMessage));
    expect(nonEmptyPageStore.getState().files.fileSelectorError).toBe(ErrorMessage);
})

test("Clear File Selector error", () => {
    const ErrorMessage = undefined;
    nonEmptyPageStore.dispatch(FileActions.setFileSelectorError(ErrorMessage));
    expect(nonEmptyPageStore.getState().files.fileSelectorError).toBe(ErrorMessage);
});

test("Update currently held files to be empty", () => {
    nonEmptyPageStore.dispatch(FileActions.updateFiles(emptyPage));
    expect(nonEmptyPageStore.getState().files.page).toBe(emptyPage);
});

test("Update currently held files to contain items", () => {
    emptyPageStore.dispatch(FileActions.updateFiles(mockFiles_SensitivityConfidential))
    expect(emptyPageStore.getState().files.page).toBe(mockFiles_SensitivityConfidential)
});

test("Update path", () => {
    const newPath = "/home/path/to/folder";
    emptyPageStore.dispatch(FileActions.updatePath(newPath));
    expect(emptyPageStore.getState().files.path).toBe(newPath);
});

test("Set sorting column 0", () => {
    const index = 0;
    emptyPageStore.dispatch(FileActions.setSortingColumn(SortBy.CREATED_AT, index));
    expect(emptyPageStore.getState().files.sortingColumns[index]).toBe(SortBy.CREATED_AT);
});

test("Set sorting column 1", () => {
    const index = 1;
    emptyPageStore.dispatch(FileActions.setSortingColumn(SortBy.SENSITIVITY, index));
    expect(emptyPageStore.getState().files.sortingColumns[index]).toBe(SortBy.SENSITIVITY);
});

test("Set file selector loading", () => {
    emptyPageStore.dispatch(FileActions.setFileSelectorLoading());
    expect(emptyPageStore.getState().files.fileSelectorLoading).toBe(true);
});

test("Receive empty page for file selector", () => {
    const pathForEmptyPage = "/home/user@test.telecity/";
    nonEmptyPageStore.dispatch(FileActions.receiveFileSelectorFiles(emptyPage, pathForEmptyPage));
    expect(nonEmptyPageStore.getState().files.fileSelectorPage).toBe(emptyPage);
});

test("Receive non-empty page for file selector", () => {
    const pathForNonEmptyPage = "/home/user@test.telecity/";
    emptyPageStore.dispatch(FileActions.receiveFileSelectorFiles(mockFiles_SensitivityConfidential, pathForNonEmptyPage));
    expect(emptyPageStore.getState().files.fileSelectorPage).toBe(mockFiles_SensitivityConfidential);
});

// Missing ability to contact backend
// fetchFiles (Can't do currently)
// fetchPageFromPath (Can't do currently)
// fetchFileselectorFiles (Can't do currently)
