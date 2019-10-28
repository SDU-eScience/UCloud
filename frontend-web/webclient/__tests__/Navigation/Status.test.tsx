import {initStatus} from "DefaultObjects";
import "jest-styled-components";
import status from "Navigation/Redux/StatusReducer";
import Status, {statusToColor} from "Navigation/Status";
import * as React from "react";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import {configureStore} from "Utilities/ReduxUtilities";
import theme from "../../app/ui-components/theme";

describe("Status", () => {
    test("Mount component", () => {
        expect(create(
            <Provider store={configureStore({status: initStatus()}, {status})}>
                <ThemeProvider theme={theme}>
                    <MemoryRouter><Status /></MemoryRouter>
                </ThemeProvider>
            </Provider>
        ).toJSON()).toMatchSnapshot();
    });

    test("statusToButton", () => {
        expect(statusToColor("NO ISSUES")).toBe("green");
        expect(statusToColor("MAINTENANCE")).toBe("yellow");
        expect(statusToColor("UPCOMING MAINTENANCE")).toBe("yellow");
        expect(statusToColor("ERROR")).toBe("red");
    });
});