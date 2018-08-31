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

// setErrorMessage

test("Show file selector", () => {
    nonEmptyPageStore.dispatch(FileActions.fileSelectorShown(true));
    expect(nonEmptyPageStore.getState().files.fileSelectorShown).toBe(true);
});

test("Hide file selector", () => {
    nonEmptyPageStore.dispatch(FileActions.fileSelectorShown(false));
    expect(nonEmptyPageStore.getState().files.fileSelectorShown).toBe(false);
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


// fetchFiles (Can't do currently)
// fetchPageFromPath (Can't do currently)
// fetchFileselectorFiles (Can't do currently)

// updateFiles
// updatePath
// setSortingColumn
// receiveFileSelectorFiles
// setFileSelectorLoading
// setDisallowedPaths
