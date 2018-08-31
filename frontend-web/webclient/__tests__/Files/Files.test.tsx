import * as React from "react";
import * as Renderer from "react-test-renderer";
import { emptyPage } from "DefaultObjects";
import Files, { FilesTable, FileOperations } from "Files/Files";
import { File, SortOrder, SortBy } from "Files";
import { mockFiles_SensitivityConfidential } from "../mock/Files"
import { MemoryRouter } from "react-router-dom";
import { createMemoryHistory } from "history";
import { Provider } from "react-redux";
import { createStore, combineReducers } from "redux";
import files from "Files/Redux/FilesReducer";
import { Button, Dropdown } from "semantic-ui-react";
import { AllFileOperations } from "Utilities/FileUtilities";

test("Render empty files table", () => {
    expect(Renderer.create(
        <FilesTable
            files={emptyPage.items as File[]}
            fileOperations={[]}
            sortOrder={SortOrder.ASCENDING}
            sortingColumns={[SortBy.PATH, SortBy.MODIFIED_AT]}
            sortFiles={() => null}
            onCheckFile={() => null}
            refetchFiles={() => null}
            sortBy={SortBy.PATH}
            onFavoriteFile={() => null}
        />
    ).toJSON()).toMatchSnapshot();
});

test("Files in filestable", () => {
    expect(Renderer.create(
        <MemoryRouter>
            <FilesTable
                files={mockFiles_SensitivityConfidential.items}
                fileOperations={[]}
                sortOrder={SortOrder.ASCENDING}
                sortingColumns={[SortBy.PATH, SortBy.MODIFIED_AT]}
                sortFiles={() => null}
                onCheckFile={() => null}
                refetchFiles={() => null}
                sortBy={SortBy.PATH}
                onFavoriteFile={() => null}
            />
        </MemoryRouter>
    ).toJSON()).toMatchSnapshot()
});



// Files Component, connected

// Middleware allowing for dispatching promises.
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

const mockHistory = createMemoryHistory();

test("Full Files component, no files", () => {
    expect(Renderer.create(
        <Provider store={emptyPageStore}>
            <MemoryRouter>
                <Files
                    history={mockHistory}
                    match={{ params: [] as string[], isExact: false, path: "", url: "home" }}
                />
            </MemoryRouter>
        </Provider>)
    ).toMatchSnapshot();
});


const fullPageStore = {
    ...emptyPageStore
};

fullPageStore.getState().files.page = mockFiles_SensitivityConfidential;

test("Full Files component, full page of files", () => {
    expect(Renderer.create(
        <Provider store={fullPageStore}>
            <MemoryRouter>
                <Files
                    history={mockHistory}
                    match={{ params: [] as string[], isExact: false, path: "", url: "home" }}
                />
            </MemoryRouter>
        </Provider>)
    ).toMatchSnapshot();
});

test("Empty files list, button, empty FilesOperations", () => {
    expect(Renderer.create(
        <FileOperations
            fileOperations={[]}
            files={emptyPageStore.getState().files.page.items}
            As={Button}
            fluid
            basic
        />
    ).toJSON()).toMatchSnapshot()
});

test("Empty files list, dropdown.item, empty FilesOperations", () =>
    expect(Renderer.create(
        <FileOperations
            fileOperations={[]}
            files={emptyPageStore.getState().files.page.items}
            As={Dropdown.Item}
        />
    ).toJSON()).toMatchSnapshot()
);

test("Files list with items, button, emtpy FilesOperations", () =>
    expect(Renderer.create(
        <FileOperations
            fileOperations={[]}
            files={fullPageStore.getState().files.page.items}
            As={Button}
            fluid
            basic
        />
    ).toJSON()).toMatchSnapshot()
);

test("Files list with items, dropdown.item, empty FilesOperations", () =>
    expect(Renderer.create(
        <FileOperations
            fileOperations={[]}
            files={fullPageStore.getState().files.page.items}
            As={Button}
            fluid
            basic
        />
    ).toJSON()).toMatchSnapshot()
);

const fileOperations = AllFileOperations(true, false, () => null, mockHistory);

console.log(fullPageStore.getState().files);


test("Empty files list, button, some fileoperations", () =>
    expect(Renderer.create(
        <FileOperations
            fileOperations={fileOperations}
            files={emptyPageStore.getState().files.page.items}
            As={Button}
            fluid
            basic
        />
    )).toMatchSnapshot()
);

test("Empty files list, dropdown.item, some fileoperations", () =>
    expect(Renderer.create(
        <FileOperations
            fileOperations={fileOperations}
            files={emptyPageStore.getState().files.page.items}
            As={Dropdown.Item}
            fluid
            basic
        />
    )).toMatchSnapshot()
);

test("Files list with items, button, some fileoperations", () =>
    expect(Renderer.create(
        <FileOperations
            fileOperations={fileOperations}
            files={fullPageStore.getState().files.page.items}
            As={Button}
            fluid
            basic
        />
    )).toMatchSnapshot()
);

test("Files list with items, dropdown.item, some fileoperations", () =>
    expect(Renderer.create(
        <FileOperations
            fileOperations={fileOperations}
            files={fullPageStore.getState().files.page.items}
            As={Dropdown.Item}
        />
    )).toMatchSnapshot()
);