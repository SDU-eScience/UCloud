import * as React from "react";
import { create } from "react-test-renderer";
import { configureStore } from "Utilities/ReduxUtilities";
import { initApplications } from "DefaultObjects";
import applications from "Applications/Redux/ApplicationsReducer";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router";

describe("RunApp component", () => {

    // FIXME Requires match props, but for some reason isn't allowed
    test.skip("Mount", () => {
        const store = configureStore({ applications: initApplications() }, { applications })
        expect(create(
            <Provider store={store}>
                <MemoryRouter>
                    {/* <RunApp match={{ params: { appName: "appName", appVersion: "appVersion" } }} /> */}
                </MemoryRouter>
            </Provider>).toJSON()
        ).toMatchSnapshot();
    });

});
