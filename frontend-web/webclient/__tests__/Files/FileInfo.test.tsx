import * as React from "react";
import * as Renderer from "react-test-renderer";
import files from "Files/Redux/FilesReducer";
import { configureStore } from "Utilities/ReduxUtilities";
import { initFiles } from "DefaultObjects";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router-dom";
import FileInfo from "Files/FileInfo";
import { updatePath, updateFiles } from "Files/Redux/FilesActions";
import { mockFiles_SensitivityConfidential } from "../mock/Files";

const emptyPageStore = configureStore({ files: initFiles("/home/user@test.abc/") }, { files });
const pageStore = configureStore({ files: initFiles("/home/user@user.telecity/") }, { files });
emptyPageStore.dispatch(updatePath("/Some/Folder"));
pageStore.dispatch(updatePath("/home/user@user.telecity/"));
pageStore.dispatch(updateFiles(mockFiles_SensitivityConfidential));

describe("FileInfo", () => {
    test.skip("No file", () => {
        expect(Renderer.create(
            <Provider store={emptyPageStore}>
                <MemoryRouter>
                    <FileInfo
                        match={{ params: ["/home/folder"] }}
                    />
                </MemoryRouter>
            </Provider>
        ).toJSON()).toMatchSnapshot();
    });

    // Can't be tested as long as 
    test.skip("Correct page for file to be shown", () => {
        expect(Renderer.create(
            <Provider store={pageStore}>
                <MemoryRouter>
                    <FileInfo
                        match={{ params: ["/home/user@user.telecity/Screenshot_2018-08-09 SDU-eScience SDUCloud-1.png"] }}
                    />
                </MemoryRouter>
            </Provider>
        ).toJSON()).toMatchSnapshot();
    });

    // Issue as long as fetch is done on incorrect page
    test.skip("Wrong page for file to be shown", () => {
        expect(Renderer.create(
            <Provider store={pageStore}>
                <MemoryRouter>
                    <FileInfo
                        match={{ params: ["/home/folder"] }}
                    />
                </MemoryRouter>
            </Provider>
        ).toJSON()).toMatchSnapshot();
    });
});