import {createBrowserHistory} from "history";
import * as React from "react";
import {Provider} from "react-redux";
import {Router} from "react-router";
import {create} from "react-test-renderer";
import {ThemeProvider} from "styled-components";
import Files from "../../app/Files/Files";
import theme from "../../app/ui-components/theme";
import {store} from "../../app/Utilities/ReduxUtilities";

test("Mount Files component", () => {
    const history = createBrowserHistory();
    history.push("app/files?path=%2Fhome%2Fjonas%40hinchely.dk");
    expect(create(
        <Provider store={store}>
            <ThemeProvider theme={theme}>
                <Router history={history}>
                    <Files />
                </Router>
            </ThemeProvider>
        </Provider>
    )).toMatchSnapshot();
});
