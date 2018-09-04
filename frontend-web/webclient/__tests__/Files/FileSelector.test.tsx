import * as React from "react";
import * as Renderer from "react-test-renderer";
import FileSelector from "Files/FileSelector";
import files from "Files/Redux/FilesReducer";
import { initFiles } from "DefaultObjects";
import { configureStore } from "Utilities/ReduxUtilities";
import { Provider } from "react-redux";

const emptyPageStore = configureStore({ files: initFiles({ homeFolder: "/home/user@test.abc/" }) }, { files });

describe("File Selector", () => {
    test("Test muter", () =>
        expect(1).toBe(1)
    );

    // TODO Tests (Currently depends on the Cloud object)
});

/* :::Props:: */
// remove
// uppy

describe("File Selector Modal", () => {
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
});

