import * as Adapter from "enzyme-adapter-react-16";
import { configureStore } from "Utilities/ReduxUtilities";
import { initUploads, initFiles, SensitivityLevelMap } from "DefaultObjects";
import uploader from "Uploader/Redux/UploaderReducer";
import files from "Files/Redux/FilesReducer";
import * as UploaderActions from "Uploader/Redux/UploaderActions";
import { configure } from "enzyme";
import { UploadPolicy } from "Uploader/api";

configure({ adapter: new Adapter() });

describe("Uploader actions", () => {
    test("Show Uploader component", () => {
        const shown = true;
        const store = configureStore({
            files: initFiles("/home/user@test.dk/"),
            uploader: initUploads()
        }, { files, uploader });
        store.dispatch(UploaderActions.setUploaderVisible(shown))
        expect(store.getState().uploader.visible).toBe(shown);
    });

    test("Hide Uploader component", () => {
        const shown = false;
        const store = configureStore({
            files: initFiles("/home/user@test.dk/"),
            uploader: initUploads()
        }, { files, uploader });
        store.dispatch(UploaderActions.setUploaderVisible(shown))
        expect(store.getState().uploader.visible).toBe(shown);
    });

    test("Clear Uploader component uploads", () => {
        const store = configureStore({
            files: initFiles("/home/user@test.dk/"),
            uploader: initUploads()
        }, { files, uploader });
        store.dispatch(UploaderActions.setUploads([]));
        expect(store.getState().uploader.uploads.length).toBe(0);
    });

    test.skip("Set Uploader component uploads to more than 0", () => {
        const store = configureStore({
            files: initFiles("/home/user@test.dk/"),
            uploader: initUploads()
        }, { files, uploader });
        store.dispatch(UploaderActions.setUploads([{
            file: new File(["1"], "name"),
            conflictFile: undefined,
            resolution: UploadPolicy.REJECT,
            sensitivity: SensitivityLevelMap.PRIVATE,
            uploadEvents: [],
            isUploading: false,
            progressPercentage: 0,
            extractArchive: false,
            uploadXHR: new XMLHttpRequest(),
            isPending: false,
            conflictError: false,
            parentPath: ""
        }]));
        expect(store.getState().uploader.uploads.length).toBe(1);
    });
});