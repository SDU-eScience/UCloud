import * as React from "react";
import Dashboard from "Dashboard/Dashboard";
import { create } from "react-test-renderer";
import * as DashboardActions from "Dashboard/Redux/DashboardActions";
import dashboard from "Dashboard/Redux/DashboardReducer";
import notifications from "Notifications/Redux/NotificationsReducer";
import status from "Navigation/Redux/StatusReducer";
import { Provider } from "react-redux";
import { configureStore } from "Utilities/ReduxUtilities";
import { initNotifications, initDashboard, initStatus, ReduxObject } from "DefaultObjects";
import { mockFiles_SensitivityConfidential } from "../mock/Files";
import { MemoryRouter } from "react-router";
import { analyses } from "../mock/Analyses";
import * as AccountingRedux from "Accounting/Redux";
import { ThemeProvider } from "styled-components";
import { theme } from "ui-components";
import { Store } from "redux";

const createStore = () => {
    return configureStore(
        {
            notifications: initNotifications(),
            dashboard: initDashboard(),
            status: initStatus(),
            ...AccountingRedux.init()
        },
        {
            dashboard,
            notifications,
            status,
            ...AccountingRedux.reducers
        }
    );
};

const WrappedDashboard: React.FunctionComponent<{ store: Store<ReduxObject> }> = props => {
    return <Provider store={props.store}>
        <ThemeProvider theme={theme}>
            <MemoryRouter>
                <Dashboard />
            </MemoryRouter>
        </ThemeProvider>
    </Provider >
};

describe("Dashboard Component", () => {
    test("Mount with favorites", () => {
        const store = createStore();
        store
        store.dispatch(DashboardActions.receiveFavorites(mockFiles_SensitivityConfidential.items));
        expect(create(<WrappedDashboard store={store} />).toJSON()).toMatchSnapshot();
    });

    // FIXME Requires backend support
    test.skip("Mount with favorites, de-favorite single file", () => {
        const store = createStore();
        store.dispatch(DashboardActions.receiveFavorites(mockFiles_SensitivityConfidential.items));
        expect(create(<WrappedDashboard store={store} />).toJSON()).toMatchSnapshot();
    });

    test("Mount with recent files", () => {
        const store = createStore();
        store.dispatch(DashboardActions.receiveRecentFiles(mockFiles_SensitivityConfidential.items));
        const files = store.getState().dashboard.recentFiles;
        files.forEach(it => { it.modifiedAt = 0 });
        store.dispatch(DashboardActions.receiveRecentFiles(files));
        expect(create(<WrappedDashboard store={store} />).toJSON()).toMatchSnapshot();
    });

    test("Mount with recent files", () => {
        const store = createStore();
        store.dispatch(DashboardActions.receiveRecentAnalyses(analyses.items));
        expect(create(<WrappedDashboard store={store} />).toJSON()).toMatchSnapshot();
    });
});