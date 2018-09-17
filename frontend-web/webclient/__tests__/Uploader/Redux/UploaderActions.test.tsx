import * as React from "react";
import * as Adapter from "enzyme-adapter-react-16";
import { configureStore } from "Utilities/ReduxUtilities";
import { initUploads, initFiles } from "DefaultObjects";
import uploader from "Uploader/Redux/UploaderReducer";
import files from "Files/Redux/FilesReducer";
import * as UploaderActions from "Uploader/Redux/UploaderActions";
import { configure } from "enzyme";


configure({ adapter: new Adapter() });

describe("Uploader actions", () => {
    test("Show Uploader component", () => {
        const shown = true;
        const store = configureStore({
            files: initFiles({ homeFolder: "/home/user@test.dk/" }),
            uploader: initUploads()
        }, { files, uploader });
        store.dispatch(UploaderActions.setUploaderVisible(shown))
        expect(store.getState().uploader.visible).toBe(shown);
    });

    test("Hide Uploader component", () => {
        const shown = false;
        const store = configureStore({
            files: initFiles({ homeFolder: "/home/user@test.dk/" }),
            uploader: initUploads()
        }, { files, uploader });
        store.dispatch(UploaderActions.setUploaderVisible(shown))
        expect(store.getState().uploader.visible).toBe(shown);
    });
});