import {configure} from "enzyme";
import * as Adapter from "enzyme-adapter-react-16";
import * as React from "react";
import {Provider} from "react-redux";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import Search from "../../app/Search/Search";
import theme from "../../app/ui-components/theme";
import {store} from "../../app/Utilities/ReduxUtilities";
import {withRouter} from "react-router";

configure({adapter: new Adapter()});

import {createBrowserHistory} from "history";

jest.mock("react-router", () => ({
    useHistory: () => createBrowserHistory(),
    useLocation: () => ({search: ""}),
    useRouteMatch: () => ({
        isExact: false,
        params: {priority: "FILES"},
        url: "",
        path: ""
    }),
    withRouter: a => a
}))

test("Search mount", () => {
    expect(
        create(
            <Provider store={store}>
                <ThemeProvider theme={theme}>
                    <Search />
                </ThemeProvider>
            </Provider>
        )
    ).toMatchSnapshot();
});
