import {createMemoryHistory} from "history";
import * as React from "react";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import {create} from "react-test-renderer";
import {Store} from "redux";
import {ThemeProvider} from "styled-components";
import Dashboard from "../../app/Dashboard/Dashboard";
import {ReduxObject} from "../../app/DefaultObjects";
import theme from "../../app/ui-components/theme";
import {store} from "../../app/Utilities/ReduxUtilities";

const WrappedDashboard: React.FunctionComponent<{store: Store<ReduxObject>}> = props => {
    return (
        <Provider store={props.store}>
            <ThemeProvider theme={theme}>
                <MemoryRouter>
                    <Dashboard history={createMemoryHistory()} />
                </MemoryRouter>
            </ThemeProvider>
        </Provider>
    );
};

describe("Dashboard", () => {
    test("Dashboard mount", () => {
        expect(create(<WrappedDashboard store={store} />)).toMatchSnapshot();
    });
});
