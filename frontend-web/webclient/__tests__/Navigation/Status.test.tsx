import * as React from "react";
import Status, { statusToColor } from "Navigation/Status";
import { create } from "react-test-renderer";
import { Provider } from "react-redux";
import { configureStore } from "Utilities/ReduxUtilities";
import { initStatus } from "DefaultObjects";
import status from "Navigation/Redux/StatusReducer";
import { MemoryRouter } from "react-router";
import "jest-styled-components";

describe("Status", () => {
    test("Mount component", () => {
        expect(create(
            <Provider store={configureStore({ status: initStatus() }, { status })}>
                <MemoryRouter><Status /></MemoryRouter>
            </Provider>).toJSON()).toMatchSnapshot();
    });

    test("statusToButton", () => {
        expect(statusToColor("NO ISSUES")).toBe("green");
        expect(statusToColor("MAINTENANCE")).toBe("yellow");
        expect(statusToColor("UPCOMING MAINTENANCE")).toBe("yellow");
        expect(statusToColor("ERROR")).toBe("red");
    });
});