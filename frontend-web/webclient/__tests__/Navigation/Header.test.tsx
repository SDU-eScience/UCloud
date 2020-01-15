import * as React from "react";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import Header from "../../app/Navigation/Header";
import theme from "../../app/ui-components/theme";
import {store} from "../../app/Utilities/ReduxUtilities";

describe("Header", () => {
    test("Mount header", () => {
        expect(create(
            <Provider store={store}>
                <ThemeProvider theme={theme}>
                    <MemoryRouter>
                        <Header toggleTheme={() => undefined} />
                    </MemoryRouter>
                </ThemeProvider>
            </Provider>
        )).toMatchSnapshot();
    });
});
