import * as React from "react";
import List from "../../app/Shares/List";
import {create} from "react-test-renderer";
import {MemoryRouter} from "react-router";
import {configure} from "enzyme"
import * as Adapter from "enzyme-adapter-react-16";
import theme from "../../app/ui-components/theme";
import {ThemeProvider} from "styled-components";
import "jest-styled-components";
import {store} from "../../app/Utilities/ReduxUtilities";
import {Provider} from "react-redux";

configure({adapter: new Adapter()});

describe("Shares List", () => {
    test("Shares component", () => {
        expect(create(
            <Provider store={store}>
                <ThemeProvider theme={theme}>
                    <MemoryRouter>
                        <List />
                    </MemoryRouter>
                </ThemeProvider>
            </Provider>
        )).toMatchSnapshot();
    });
});
