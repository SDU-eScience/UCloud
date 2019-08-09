import * as React from "react";
import {create} from "react-test-renderer";
import {Uploader} from "../../app/Uploader";
import {configureStore} from "../../app/Utilities/ReduxUtilities";
import {initUploads, SensitivityLevelMap} from "../../app/DefaultObjects";
import uploader from "../../app/Uploader/Redux/UploaderReducer";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import * as UploaderActions from "../../app/Uploader/Redux/UploaderActions";
import "jest-styled-components";
import {UploadPolicy} from "../../app/Uploader/api";
import {AnyAction} from "redux";


// configure({ adapter: new Adapter() });

describe("Uploader", () => {
    test("Closed Uploader component", () => {
        const store = configureStore({
            uploader: initUploads()
        }, {uploader});
        expect(create(
            <Provider store={store}>
                <MemoryRouter>
                    <Uploader />
                </MemoryRouter>
            </Provider>
        ).toJSON()).toMatchSnapshot();
    });

    // FIXME Tests modal, which requires accessing the portal it is being rendered in?
    test.skip("Open Uploader component", () => {
        const store = configureStore({
            uploader: initUploads()
        }, {uploader});
        store.dispatch(UploaderActions.setUploaderVisible(true, "") as AnyAction);
        expect(create(
            <Provider store={store}>
                <MemoryRouter>
                    <Uploader />
                </MemoryRouter>
            </Provider>
        ).toJSON()).toMatchSnapshot();
    });

    test.skip("Render Uploader component with files", () => {
        const store = configureStore({
            uploader: initUploads()
        }, {uploader});
        store.dispatch(UploaderActions.setUploaderVisible(false, "") as AnyAction);
        store.dispatch(UploaderActions.setUploads([{
            file: new File([], "file"),
            isUploading: false,
            sensitivity: SensitivityLevelMap.PRIVATE,
            resolution: UploadPolicy.REJECT,
            uploadEvents: [],
            progressPercentage: 0,
            extractArchive: false,
            uploadXHR: undefined,
            isPending: false,
            parentPath: ""
        }]) as AnyAction);
        expect(create(
            <Provider store={store}>
                <MemoryRouter>
                    <Uploader />
                </MemoryRouter>
            </Provider>
        ).toJSON()).toMatchSnapshot();
    });
});