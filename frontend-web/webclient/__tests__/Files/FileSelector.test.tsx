import * as React from "react";
import * as TestUtils from "react-dom/test-utils";
import * as ReactDom from "react-dom";
import * as Renderer from "react-test-renderer";
import FileSelector, { FileSelectorModal } from "Files/FileSelector";
import files from "Files/Redux/FilesReducer";
import { initFiles } from "DefaultObjects";
import { configureStore } from "Utilities/ReduxUtilities";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router";

const emptyPageStore = configureStore({ files: initFiles({ homeFolder: "/home/user@test.abc/" }) }, { files });

/* describe("File Selector", () => {
    test("Minimal FileSelector", () =>
        expect(Renderer.create(
            <Provider store={emptyPageStore}>
                <FileSelector
                    onFileSelect={(f) => f}
                    path="/home/Folder/Fawlder"
                />
            </Provider>
        ).toJSON()).toMatchSnapshot());

    test("Minimal FileSelector, allow upload", () =>
        expect(Renderer.create(
            <Provider store={emptyPageStore}>
                <FileSelector
                    allowUpload
                    onFileSelect={(f) => f}
                    path="/home/Folder/Fawlder"
                />
            </Provider>
        ).toJSON()).toMatchSnapshot());

    test("Minimal FileSelector, is required", () =>
        expect(Renderer.create(
            <Provider store={emptyPageStore}>
                <FileSelector
                    isRequired
                    onFileSelect={(f) => f}
                    path="/home/Folder/Fawlder"
                />
            </Provider>
        ).toJSON()).toMatchSnapshot());

    test("Minimal FileSelector, can select folders", () =>
        expect(Renderer.create(
            <Provider store={emptyPageStore}>
                <FileSelector
                    canSelectFolders
                    onFileSelect={(f) => f}
                    path="/home/Folder/Fawlder"
                />
            </Provider>
        ).toJSON()).toMatchSnapshot());

    test("Minimal FileSelector, only allow folders", () =>
        expect(Renderer.create(
            <Provider store={emptyPageStore}>
                <FileSelector
                    onlyAllowFolders
                    onFileSelect={(f) => f}
                    path="/home/Folder/Fawlder"
                />
            </Provider>
        ).toJSON()).toMatchSnapshot());
}); */

describe("File Selector Modal", () => {
    // BLOCKED by https://github.com/Semantic-Org/Semantic-UI-React/issues/3100#issuecomment-415000769
    /* test("Shown fileselector modal", () =>
        expect(Renderer.create(
            <MemoryRouter>
                <FileSelectorModal
                    show={true}
                    path="/home/Folder/Fawlder"
                    loading={false}
                    onHide={(e, d) => false}
                    page={emptyPageStore.getState().files.page}
                    setSelectedFile={(f) => f}
                    fetchFiles={(f) => f}
                    disallowedPaths={[]}
                    onlyAllowFolders={false}
                    canSelectFolders={false}
                    creatingFolder={false}
                    handleKeyDown={() => null}
                    createFolder={() => null}
                    errorMessage={""}
                    navigate={(a, b, c) => null}
                    onErrorDismiss={() => null}
                />
            </MemoryRouter>
        ).toJSON()).toMatchSnapshot()
    ); */

    test("Hidden fileselector modal", () =>
        expect(Renderer.create(
            <MemoryRouter>
                <FileSelectorModal
                    show={false}
                    path="/home/Folder/Fawlder"
                    loading={false}
                    onHide={() => false}
                    page={emptyPageStore.getState().files.page}
                    setSelectedFile={(f) => f}
                    fetchFiles={(f) => f}
                />
            </MemoryRouter>
        ).toJSON()).toMatchSnapshot()
    );
});