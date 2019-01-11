import * as React from "react";
import { create } from "react-test-renderer";
import { configureStore } from "Utilities/ReduxUtilities";
import { init } from "Applications/Redux/BrowseObject";
import applications from "Applications/Redux/BrowseReducer";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router";
import "jest-styled-components";

describe("RunApp component", () => {

    // FIXME Requires match props, but for some reason isn't allowed
    test.skip("Mount", () => {
        const store = configureStore({ applications: init() }, { applications })
        expect(create(
            <Provider store={store}>
                <MemoryRouter>
                    {/* <RunApp match={{ params: { appName: "appName", appVersion: "appVersion" } }} /> */}
                </MemoryRouter>
            </Provider>).toJSON()
        ).toMatchSnapshot();
    });

});
