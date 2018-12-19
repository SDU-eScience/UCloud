import * as React from "react";
import Dashboard from "Dashboard/Dashboard";
import { create } from "react-test-renderer";
import * as DashboardActions from "Dashboard/Redux/DashboardActions";
import dashboard from "Dashboard/Redux/DashboardReducer";
import notifications from "Notifications/Redux/NotificationsReducer";
import status from "Navigation/Redux/StatusReducer";
import { Provider } from "react-redux";
import { configureStore } from "Utilities/ReduxUtilities";
import { initNotifications, initDashboard, initStatus } from "DefaultObjects";
import { mockFiles_SensitivityConfidential } from "../mock/Files";
import { MemoryRouter } from "react-router";
import { analyses } from "../mock/Analyses";
import { createMemoryHistory } from "history";

describe("Dashboard Component", () => {

    test.skip("Mount with favorites", () => {
        const store = configureStore({ notifications: initNotifications(), dashboard: initDashboard(), status: initStatus() }, { dashboard, notifications, status });
        store.dispatch(DashboardActions.receiveFavorites(mockFiles_SensitivityConfidential.items));
        expect(create(
            <Provider store={store}>
                <MemoryRouter>
                    <Dashboard history={createMemoryHistory()} />
                </MemoryRouter>
            </Provider >
        ).toJSON()).toMatchSnapshot();
    });

    // FIXME Requires backend support
    test.skip("Mount with favorites, de-favorite single file", () => {
        const store = configureStore({ notifications: initNotifications(), dashboard: initDashboard(), status: initStatus() }, { dashboard, notifications, status });
        store.dispatch(DashboardActions.receiveFavorites(mockFiles_SensitivityConfidential.items));
        expect(create(
            <Provider store={store}>
                <MemoryRouter>
                    <Dashboard history={createMemoryHistory()} />
                </MemoryRouter>
            </Provider >
        ).toJSON()).toMatchSnapshot();
    });

    test.skip("Mount with recent files", () => {
        const store = configureStore({ notifications: initNotifications(), dashboard: initDashboard(), status: initStatus() }, { dashboard, notifications, status });
        store.dispatch(DashboardActions.receiveRecentFiles(mockFiles_SensitivityConfidential.items));
        const files = store.getState().dashboard.recentFiles;
        files.forEach(it => { it.modifiedAt = 0 });
        store.dispatch(DashboardActions.receiveRecentFiles(files));
        expect(create(
            <Provider store={store}>
                <MemoryRouter>
                    <Dashboard history={createMemoryHistory()} />
                </MemoryRouter>
            </Provider >
        ).toJSON()).toMatchSnapshot();
    });

    test.skip("Mount with recent files", () => {
        const store = configureStore({ notifications: initNotifications(), dashboard: initDashboard(), status: initStatus() }, { dashboard, notifications, status });
        store.dispatch(DashboardActions.receiveRecentAnalyses(analyses.items));
        expect(create(
            <Provider store={store}>
                <MemoryRouter>
                    <Dashboard history={createMemoryHistory()} />
                </MemoryRouter>
            </Provider >
        ).toJSON()).toMatchSnapshot();
    });
});