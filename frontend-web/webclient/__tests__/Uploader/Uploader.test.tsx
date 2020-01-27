import * as React from "react";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import {create} from "react-test-renderer";
import {AnyAction} from "redux";
import {SensitivityLevelMap} from "../../app/DefaultObjects";
import {Uploader} from "../../app/Uploader";
import {UploadPolicy} from "../../app/Uploader/api";
import * as UploaderActions from "../../app/Uploader/Redux/UploaderActions";
import {store} from "../../app/Utilities/ReduxUtilities";


// configure({ adapter: new Adapter() });

describe("Uploader", () => {
    test("Closed Uploader component", () => {
        const storeCopy = {...store};
        expect(create(
            <Provider store={storeCopy}>
                <MemoryRouter>
                    <Uploader />
                </MemoryRouter>
            </Provider>
        ).toJSON()).toMatchSnapshot();
    });

    // FIXME Tests modal, which requires accessing the portal it is being rendered in?
    test.skip("Open Uploader component", () => {
        const storeCopy = {...store};
        storeCopy.dispatch(UploaderActions.setUploaderVisible(true, "") as AnyAction);
        expect(create(
            <Provider store={storeCopy}>
                <MemoryRouter>
                    <Uploader />
                </MemoryRouter>
            </Provider>
        ).toJSON()).toMatchSnapshot();
    });

    test.skip("Render Uploader component with files", () => {
        const storeCopy = {...store};
        storeCopy.dispatch(UploaderActions.setUploaderVisible(false, "") as AnyAction);
        storeCopy.dispatch(UploaderActions.setUploads([{
            file: new File([], "file"),
            isUploading: false,
            sensitivity: SensitivityLevelMap.PRIVATE,
            resolution: UploadPolicy.REJECT,
            uploadEvents: [],
            progressPercentage: 0,
            extractArchive: false,
            uploadXHR: undefined,
            isPending: false,
            path: "",
            uploadSize: 1
        }]) as AnyAction);
        expect(create(
            <Provider store={storeCopy}>
                <MemoryRouter>
                    <Uploader />
                </MemoryRouter>
            </Provider>
        ).toJSON()).toMatchSnapshot();
    });
});
