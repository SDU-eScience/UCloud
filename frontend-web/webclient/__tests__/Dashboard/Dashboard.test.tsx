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
import { createMemoryHistory } from "history";
import * as AccountingRedux from "Accounting/Redux";
import { ThemeProvider } from "styled-components";
import { theme } from "ui-components";
import { Store } from "redux";
import "jest-styled-components";

const createStore = () => {
    return configureStore(
        {
            notifications: initNotifications(),
            dashboard: initDashboard(),
            status: initStatus(),
            ...AccountingRedux.init()
        }, {
            dashboard,
            notifications,
            status,
            ...AccountingRedux.reducers
        }
    );
};

const WrappedDashboard: React.FunctionComponent<{ store: Store<ReduxObject> }> = props => {
    return null;
    /* return (<Provider store={props.store}>
        <ThemeProvider theme={theme}>
            <MemoryRouter>
                <Dashboard history={createMemoryHistory()} />
            </MemoryRouter>
        </ThemeProvider>
    </Provider>) */
};
