import * as React from "react";
import {Provider} from "react-redux";
import {create} from "react-test-renderer";
import BackgroundTasks from "../../app/BackgroundTasks/BackgroundTask";
import {store} from "../../app/Utilities/ReduxUtilities";

describe("BackgroundTasks", () => {
    test("Mount", () => {

        expect(create(
            <Provider store={store}>
                <BackgroundTasks />
            </Provider>
        )).toMatchSnapshot();
    });
});
