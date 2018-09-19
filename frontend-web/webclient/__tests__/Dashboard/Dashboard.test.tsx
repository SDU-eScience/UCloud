import * as React from "react";
import Dashboard from "Dashboard/Dashboard";
import { create } from "react-test-renderer";
import * as DashboardActions from "Dashboard/Redux/DashboardActions";
import * as NotificationActions from "Notifications/Redux/NotificationsActions";
import dashboard from "Dashboard/Redux/DashboardReducer";
import notifications from "Notifications/Redux/NotificationsReducer";
import { Provider } from "react-redux";
import { configureStore } from "Utilities/ReduxUtilities";
import { initNotifications, initDashboard } from "DefaultObjects";
import { mockFiles_SensitivityConfidential } from "../mock/Files";
import { MemoryRouter } from "react-router";
import { analyses } from "../mock/Analyses";
import { notifications as MockNotifications } from "../mock/Notifications";

describe("Dashboard Component", () => {
    test("Mount with no data", () => {
        const store = configureStore({ notifications: initNotifications(), dashboard: initDashboard() }, { dashboard, notifications });
        expect(create(
            <Provider store={store}>
                <Dashboard />
            </Provider >
        ).toJSON()).toMatchSnapshot();
    });

    test("Mount with favorites", () => {
        const store = configureStore({ notifications: initNotifications(), dashboard: initDashboard() }, { dashboard, notifications });
        store.dispatch(DashboardActions.receiveFavorites(mockFiles_SensitivityConfidential.items));
        expect(create(
            <Provider store={store}>
                <MemoryRouter>
                    <Dashboard />
                </MemoryRouter>
            </Provider >
        ).toJSON()).toMatchSnapshot();
    });

    // FIXME Requires backend support
    test.skip("Mount with favorites, de-favorite single file", () => {
        const store = configureStore({ notifications: initNotifications(), dashboard: initDashboard() }, { dashboard, notifications });
        store.dispatch(DashboardActions.receiveFavorites(mockFiles_SensitivityConfidential.items));
        expect(create(
            <Provider store={store}>
                <MemoryRouter>
                    <Dashboard />
                </MemoryRouter>
            </Provider >
        ).toJSON()).toMatchSnapshot();
    });

    test("Mount with recent files", () => {
        const store = configureStore({ notifications: initNotifications(), dashboard: initDashboard() }, { dashboard, notifications });
        store.dispatch(DashboardActions.receiveRecentFiles(mockFiles_SensitivityConfidential.items));
        const files = store.getState().dashboard.recentFiles;
        files.forEach(it => { it.modifiedAt = 0 });
        store.dispatch(DashboardActions.receiveRecentFiles(files));
        expect(create(
            <Provider store={store}>
                <MemoryRouter>
                    <Dashboard />
                </MemoryRouter>
            </Provider >
        ).toJSON()).toMatchSnapshot();
    });

    test("Mount with recent files", () => {
        const store = configureStore({ notifications: initNotifications(), dashboard: initDashboard() }, { dashboard, notifications });
        store.dispatch(DashboardActions.receiveRecentAnalyses(analyses.items));
        expect(create(
            <Provider store={store}>
                <MemoryRouter>
                    <Dashboard />
                </MemoryRouter>
            </Provider >
        ).toJSON()).toMatchSnapshot();
    });

    test("Mount with notifications", () => {
        const store = configureStore({ notifications: initNotifications(), dashboard: initDashboard() }, { dashboard, notifications });
        store.dispatch(NotificationActions.receiveNotifications(MockNotifications));
        const notificationspage = store.getState().notifications.page;
        notificationspage.items.forEach(it => it.ts = 0);
        store.dispatch(NotificationActions.receiveNotifications(notificationspage));
        expect(create(
            <Provider store={store}>
                <MemoryRouter>
                    <Dashboard />
                </MemoryRouter>
            </Provider >
        ).toJSON()).toMatchSnapshot();
    });
});