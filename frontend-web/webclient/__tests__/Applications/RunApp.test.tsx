import * as React from "react";
import { create } from "react-test-renderer";
import applications from "../../app/Applications/Redux/BrowseReducer";
import RunApp from "../../app/Applications/Run";
import { configureStore, responsive } from "../../app/Utilities/ReduxUtilities";
import { Provider } from "react-redux";
import { MemoryRouter } from "react-router";
import "jest-styled-components";
import { createMemoryHistory } from "history";
import { ThemeProvider } from "styled-components";
import theme from "../../app/ui-components/theme";

describe("RunApp component", () => {
    test("Mount", () => {
        const store = configureStore({}, { applications, responsive })
        expect(create(
            <Provider store={store}>
                <ThemeProvider theme={theme}>
                    <MemoryRouter>
                        <RunApp match={{
                            params: { appName: "appName", appVersion: "appVersion" },
                            isExact: true,
                            path: "",
                            url: "",
                        }}
                            history={createMemoryHistory()} />
                    </MemoryRouter>
                </ThemeProvider>
            </Provider>).toJSON()
        ).toMatchSnapshot();
    });

});
