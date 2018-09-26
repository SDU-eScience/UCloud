import StatusPage from "Navigation/StatusPage";
import { create } from "react-test-renderer";
import * as React from "react";
import { Provider } from "react-redux";
import { configureStore } from "Utilities/ReduxUtilities";
import { initStatus } from "DefaultObjects";
import status from "Navigation/Redux/StatusReducer";

describe("Status Page", () => {
    test("Mount component with NO ISSUES", () => {
        expect(create(<Provider store={configureStore({ status: initStatus() }, { status })}><StatusPage /></Provider>)).toMatchSnapshot()
    });
});