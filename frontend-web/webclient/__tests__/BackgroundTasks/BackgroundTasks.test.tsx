import * as React from "react";
import {Provider} from "react-redux";
import {create} from "react-test-renderer";
import BackgroundTasks from "../../app/BackgroundTasks/BackgroundTask";
import {initUploads} from "../../app/DefaultObjects";
import uploader from "../../app/Uploader/Redux/UploaderReducer";
import {configureStore} from "../../app/Utilities/ReduxUtilities";

describe("BackgroundTasks", () => {
    test("Mount", () => {

        expect(create(
            <Provider store={configureStore({uploader: initUploads()}, {uploader})}>
                <BackgroundTasks />
            </Provider>
        )).toMatchSnapshot();
    });
});
