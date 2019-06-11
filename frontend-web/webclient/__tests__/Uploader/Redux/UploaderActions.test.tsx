import * as Adapter from "enzyme-adapter-react-16";
import { configureStore } from "../../../app/Utilities/ReduxUtilities";
import { initUploads, initFiles, SensitivityLevelMap } from "../../../app/DefaultObjects";
import uploader from "../../../app/Uploader/Redux/UploaderReducer";
import files from "../../../app/Files/Redux/FilesReducer";
import * as UploaderActions from "../../../app/Uploader/Redux/UploaderActions";
import { configure } from "enzyme";
import { UploadPolicy } from "../../../app/Uploader/api";

configure({ adapter: new Adapter() });

describe("Uploader actions", () => {
    test("Show Uploader component", () => {
        const shown = true;
        const store = configureStore({
            files: initFiles("/home/user@test.dk/"),
            uploader: initUploads()
        }, { files, uploader });
        store.dispatch<any>(UploaderActions.setUploaderVisible(shown))
        expect(store.getState().uploader.visible).toBe(shown);
    });

    test("Hide Uploader component", () => {
        const shown = false;
        const store = configureStore({
            files: initFiles("/home/user@test.dk/"),
            uploader: initUploads()
        }, { files, uploader });
        store.dispatch<any>(UploaderActions.setUploaderVisible(shown))
        expect(store.getState().uploader.visible).toBe(shown);
    });

    test("Clear Uploader component uploads", () => {
        const store = configureStore({
            files: initFiles("/home/user@test.dk/"),
            uploader: initUploads()
        }, { files, uploader });
        store.dispatch<any>(UploaderActions.setUploads([]));
        expect(store.getState().uploader.uploads.length).toBe(0);
    });

    test.skip("Set Uploader component uploads to more than 0", () => {
        const store = configureStore({
            files: initFiles("/home/user@test.dk/"),
            uploader: initUploads()
        }, { files, uploader });
        store.dispatch<any>(UploaderActions.setUploads([{
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
            parentPath: ""
        }]));
        expect(store.getState().uploader.uploads.length).toBe(1);
    });
});