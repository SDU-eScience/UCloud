import {createMemoryHistory} from "history";
import * as React from "react";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import {create, act} from "react-test-renderer";
import {Store} from "redux";
import {ThemeProvider} from "styled-components";
import Dashboard from "../../app/Dashboard/Dashboard";
import {emptyPage} from "../../app/DefaultObjects";
import theme from "../../app/ui-components/theme";
import {store} from "../../app/Utilities/ReduxUtilities";

jest.mock("Project", () => ({
    getProjectNames: () => [],
    userProjectStatus: () => {}
}));

jest.mock("Authentication/HttpClientInstance", () => ({
    Client: {
        get: (path: string) => {
            switch (path) {
                case "/accounting/storage/bytesUsed/usage":
                    return Promise.resolve({request: {status: 200} as XMLHttpRequest, response: {usage: 14690218167, quota: null, dataType: "bytes", title: "Storage Used"}});
                case "/accounting/compute/timeUsed/usage":
                    return Promise.resolve({request: {status: 200} as XMLHttpRequest, response: {usage: 36945000, quota: null, dataType: "duration", title: "Compute Time Used"}});
            }
            return Promise.resolve({request: {status: 200} as XMLHttpRequest, response: emptyPage});
        },
        call: call => {
            switch (call.path) {
                case "/accounting/wallets/balance?includeChildren=false":
                    return Promise.resolve({request: {status: 200} as XMLHttpRequest, response: {wallets: []}});
            }
            return Promise.resolve({request: {status: 200} as XMLHttpRequest, response: emptyPage});
        },
        homeFolder: "/home/test@test/"
    }
}));

const WrappedDashboard: React.FunctionComponent<{store: Store<ReduxObject>}> = props => (
    <Provider store={props.store}>
        <ThemeProvider theme={theme}>
            <MemoryRouter>
                <Dashboard history={createMemoryHistory()} />
            </MemoryRouter>
        </ThemeProvider>
    </Provider>
);

describe("Dashboard", () => {
    test("Dashboard mount", async () => {
        await act(async () => {
            const dash = await create(<WrappedDashboard store={store} />);
            expect(dash.toJSON()).toMatchSnapshot();
        });
    });
});
