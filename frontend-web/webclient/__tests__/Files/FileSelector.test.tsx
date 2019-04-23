import * as React from "react";
import * as Renderer from "react-test-renderer";
import { FileSelectorModal } from "../../app/Files/FileSelector";
import files from "../../app/Files/Redux/FilesReducer";
import { initFiles } from "../../app/DefaultObjects";
import { configureStore } from "../../app/Utilities/ReduxUtilities";
import { MemoryRouter } from "react-router";
import "jest-styled-components";

const emptyPageStore = configureStore({ files: initFiles("/home/user@test.abc/") }, { files });

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

    test("Hidden fileselector modal", () =>
        expect(Renderer.create(
            <MemoryRouter>
                <FileSelectorModal
                    isFavorites={false}
                    fetchFavorites={() => undefined}
                    show={false}
                    path="/home/Folder/Fawlder"
                    loading={false}
                    onHide={() => false}
                    page={emptyPageStore.getState().files.page}
                    setSelectedFile={(f: File) => f}
                    fetchFiles={f => f}
                />
            </MemoryRouter>
        ).toJSON()).toMatchSnapshot()
    );
});