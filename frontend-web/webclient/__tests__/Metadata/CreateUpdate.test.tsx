import * as React from "react";
import { create } from "react-test-renderer";
import CreateUpdate from "../../app/Project/CreateUpdate";
import { createMemoryHistory } from "history";
import { Provider } from "react-redux";
import { configureStore, responsive } from "../../app/Utilities/ReduxUtilities";
import { initStatus } from "../../app/DefaultObjects";
import theme from "../../app/ui-components/theme";
import status from "../../app/Navigation/Redux/StatusReducer";
import "jest-styled-components";
import { ThemeProvider } from "styled-components";

describe("CreateUpdate Component", () => {
    test("Mount", () => {
        expect(create(
            <Provider store={configureStore({ status: initStatus() }, { status, responsive })}>
                <ThemeProvider theme={theme}>
                    <CreateUpdate
                        location={{ search: "?" }}
                        history={createMemoryHistory()}
                    />
                </ThemeProvider>
            </Provider>
        ).toJSON()).toMatchSnapshot()
    });
});