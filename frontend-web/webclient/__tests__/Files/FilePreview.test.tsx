import * as React from "react";
import FilePreview from "Files/FilePreview";
import { create } from "react-test-renderer";
import { MemoryRouter } from "react-router";
import { Provider } from "react-redux";
import { configureStore } from "Utilities/ReduxUtilities";
import { initFiles } from "DefaultObjects";
import files from "Files/Redux/FilesReducer";

describe("File Preview", () => {
    test("Mount preview", () => {
        const store = configureStore({ files: initFiles("/does/not/matter/") }, { files });
        expect(create(
            <Provider store={store}>
                <MemoryRouter>
                    <FilePreview match={{ params: { 0: "" }, isExact: true, path: "", url: "" }} />
                </MemoryRouter>
            </Provider>
        ));
    });
});