import * as React from "react";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import {create} from "react-test-renderer";
import Notifications from "../../app/Notifications/index";
import {store} from "../../app/Utilities/ReduxUtilities";

describe.skip("Notifications", () => {
    test("Mount notifications", () => {
        expect(create(
            <Provider store={store}>
                <MemoryRouter>
                    <Notifications />
                </MemoryRouter>
            </Provider>
        )).toMatchSnapshot();
    });
});
