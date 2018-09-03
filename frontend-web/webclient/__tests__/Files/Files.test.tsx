import * as React from "react";
import * as Renderer from "react-test-renderer";
import { emptyPage } from "DefaultObjects";
import Files, { FilesTable, FileOperations } from "Files/Files";
import { SortOrder, SortBy } from "Files";
import { mockFiles_SensitivityConfidential } from "../mock/Files"
import { MemoryRouter } from "react-router-dom";
import { Provider } from "react-redux";
import { createMemoryHistory } from "history";
import files from "Files/Redux/FilesReducer";
import { Button, Dropdown } from "semantic-ui-react";
import { AllFileOperations } from "Utilities/FileUtilities";
import { configureStore } from "Utilities/ReduxUtilities";
import { initFiles } from "DefaultObjects";

const emptyPageStore = configureStore({ files: initFiles({ homeFolder: "/home/user@test.abc/" }) }, { files });

const mockHistory = createMemoryHistory();

const fullPageStore = {
    ...emptyPageStore
};

fullPageStore.getState().files.page = mockFiles_SensitivityConfidential;

const fileOperations = AllFileOperations(true, false, () => null, mockHistory);


describe("FilesTable", () => {
    test("Render empty", () => {
        expect(Renderer.create(
            <FilesTable
                files={emptyPage.items}
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

    test("Render non-empty", () => {
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
    })
});

describe("Files-component", () => {
    test("Full Files component, no files", () => {
        expect(Renderer.create(
            <Provider store={emptyPageStore}>
                <MemoryRouter>
                    <Files
                        history={mockHistory}
                        match={{ params: [], isExact: false, path: "", url: "home" }}
                    />
                </MemoryRouter>
            </Provider>).toJSON()
        ).toMatchSnapshot();
    });

    test("Full Files component, full page of files", () => {
        expect(Renderer.create(
            <Provider store={fullPageStore}>
                <MemoryRouter>
                    <Files
                        history={mockHistory}
                        match={{ params: [], isExact: false, path: "", url: "home" }}
                    />
                </MemoryRouter>
            </Provider>).toJSON()
        ).toMatchSnapshot();
    });
});


describe("File operations", () => {
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

    test("Files list with items, button, empty FilesOperations", () =>
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
});