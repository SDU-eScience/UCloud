import * as FileActions from "Files/Redux/FilesActions";
import { mockFiles_SensitivityConfidential } from "../../mock/Files";
import { emptyPage } from "DefaultObjects";
import { SortBy } from "Files";
import { configureStore } from "Utilities/ReduxUtilities";
import { initFiles } from "DefaultObjects";
import files from "Files/Redux/FilesReducer";

const emptyPageStore = configureStore({ files: initFiles({ homeFolder: "/home/user@test.abc/" }) }, { files });

const nonEmptyPageStore = { ...emptyPageStore };
nonEmptyPageStore.getState().files.page = mockFiles_SensitivityConfidential;

describe("Check All Files", () => {
    test("Check all files in empty page", () => {
        emptyPageStore.dispatch(FileActions.checkAllFiles(true, emptyPageStore.getState().files.page));
        expect(emptyPageStore.getState().files).toBe(emptyPageStore.getState().files);
    });

    test("Check all files in non-empty page", () => {
        const checked = true;
        nonEmptyPageStore.dispatch(FileActions.checkAllFiles(checked, nonEmptyPageStore.getState().files.page));
        const page = { ...nonEmptyPageStore.getState().files.page }
        page.items.forEach(f => f.isChecked = checked);
        expect(nonEmptyPageStore.getState().files.page).toEqual(page);
    });

    test("Remove check from all files in non-empty page", () => {
        const checked = true;
        nonEmptyPageStore.dispatch(FileActions.checkAllFiles(checked, nonEmptyPageStore.getState().files.page));
        nonEmptyPageStore.dispatch(FileActions.checkAllFiles(!checked, nonEmptyPageStore.getState().files.page));
        const page = { ...nonEmptyPageStore.getState().files.page }
        page.items.forEach(f => f.isChecked = !checked);
        expect(nonEmptyPageStore.getState().files.page).toEqual(page);
    });
});

describe("Set Files as loading", () => {
    test("Loading", () => {
        nonEmptyPageStore.dispatch(FileActions.setLoading(true));
        expect(nonEmptyPageStore.getState().files.loading).toBe(true);
    });

    test("Not loading", () => {
        nonEmptyPageStore.dispatch(FileActions.setLoading(false));
        expect(nonEmptyPageStore.getState().files.loading).toBe(false);
    });
});

describe("Setting Error message", () => {
    test("Set Files error", () => {
        const ErrorMessage = "Error_Message";
        nonEmptyPageStore.dispatch(FileActions.setErrorMessage(ErrorMessage));
        expect(nonEmptyPageStore.getState().files.error).toBe(ErrorMessage);
    })

    test("Clear Files error", () => {
        const ErrorMessage = undefined;
        nonEmptyPageStore.dispatch(FileActions.setErrorMessage(ErrorMessage));
        expect(nonEmptyPageStore.getState().files.error).toBe(ErrorMessage);
    })
});


describe("Show FileSelector", () => {
    test("Show file selector", () => {
        nonEmptyPageStore.dispatch(FileActions.fileSelectorShown(true));
        expect(nonEmptyPageStore.getState().files.fileSelectorShown).toBe(true);
    });

    test("Hide file selector", () => {
        nonEmptyPageStore.dispatch(FileActions.fileSelectorShown(false));
        expect(nonEmptyPageStore.getState().files.fileSelectorShown).toBe(false);
    });
});


describe("Set disallowed paths for FileSelector", () => {
    test("Set disallowed paths", () => {
        const disallowedPaths = ["1", "2", "3"]
        nonEmptyPageStore.dispatch(FileActions.setDisallowedPaths(disallowedPaths));
        expect(nonEmptyPageStore.getState().files.disallowedPaths);
    });

    test("Clear disallowed paths", () => {
        const disallowedPaths = [];
        nonEmptyPageStore.dispatch(FileActions.setDisallowedPaths(disallowedPaths));
        expect(nonEmptyPageStore.getState().files.disallowedPaths);
    });
});

describe("File Selector loading", () => {
    test("Set FileSelector loading", () => {
        nonEmptyPageStore.dispatch(FileActions.setFileSelectorLoading());
        expect(nonEmptyPageStore.getState().files.fileSelectorLoading).toBe(true);
    });
});

describe("FileSelector callback", () => {
    test("Set fileselector callback", () => {
        const callback = () => 42;
        nonEmptyPageStore.dispatch(FileActions.setFileSelectorCallback(callback));
        expect(nonEmptyPageStore.getState().files.fileSelectorCallback()).toBe(callback());
    });

    test("Clear fileselector callback", () => {
        nonEmptyPageStore.dispatch(FileActions.setFileSelectorCallback(() => undefined));
        expect(nonEmptyPageStore.getState().files.fileSelectorCallback()).toBe(undefined);
    });
});

describe("File Selector Error message", () => {
    test("Set File Selector error", () => {
        const ErrorMessage = "Error_Message";
        nonEmptyPageStore.dispatch(FileActions.setFileSelectorError(ErrorMessage));
        expect(nonEmptyPageStore.getState().files.fileSelectorError).toBe(ErrorMessage);
    });

    test("Clear File Selector error", () => {
        const ErrorMessage = undefined;
        nonEmptyPageStore.dispatch(FileActions.setFileSelectorError(ErrorMessage));
        expect(nonEmptyPageStore.getState().files.fileSelectorError).toBe(ErrorMessage);
    });
});

describe("Update page in store", () => {
    test("Update currently held files to be empty", () => {
        nonEmptyPageStore.dispatch(FileActions.updateFiles(emptyPage));
        expect(nonEmptyPageStore.getState().files.page).toBe(emptyPage);
    });

    test("Update currently held files to contain items", () => {
        emptyPageStore.dispatch(FileActions.updateFiles(mockFiles_SensitivityConfidential))
        expect(emptyPageStore.getState().files.page).toBe(mockFiles_SensitivityConfidential)
    });
});

describe("Path update", () => {
    test("Update path", () => {
        const newPath = "/home/path/to/folder";
        emptyPageStore.dispatch(FileActions.updatePath(newPath));
        expect(emptyPageStore.getState().files.path).toBe(newPath);
    });
})

describe("Sorting columns", () => {
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
});

describe("File Selector Loading", () => {
    test("Set file selector loading", () => {
        emptyPageStore.dispatch(FileActions.setFileSelectorLoading());
        expect(emptyPageStore.getState().files.fileSelectorLoading).toBe(true);
    });
});

describe("Receive fileselector page", () => {
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
});

// Missing ability to contact backend
// fetchFiles (Can't do currently)
// fetchPageFromPath (Can't do currently)
// fetchFileselectorFiles (Can't do currently)