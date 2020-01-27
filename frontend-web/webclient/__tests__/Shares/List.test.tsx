import {configure} from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import * as React from "react";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import List from "../../app/Shares/List";
import theme from "../../app/ui-components/theme";
import {store} from "../../app/Utilities/ReduxUtilities";

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
