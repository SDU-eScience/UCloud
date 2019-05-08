import * as React from "react";
import { create } from "react-test-renderer";
import BackgroundTasks from "../../app/BackgroundTasks/BackgroundTask";
import { Provider } from "react-redux";
import { configureStore } from "../../app/Utilities/ReduxUtilities";
import uploader from "../../app/Uploader/Redux/UploaderReducer";
import { initUploads } from "../../app/DefaultObjects";

describe("BackgroundTasks", () => {
    test("Mount", () => {

        expect(create(
            <Provider store={configureStore({ uploader: initUploads() }, { uploader })}>
                <BackgroundTasks />
            </Provider>)).toMatchSnapshot();
    });
});
