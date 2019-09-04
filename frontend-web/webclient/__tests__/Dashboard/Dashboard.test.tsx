import * as React from "react";
import dashboard from "../../app/Dashboard/Redux/DashboardReducer";
import notifications from "../../app/Notifications/Redux/NotificationsReducer";
import status from "../../app/Navigation/Redux/StatusReducer";
import { configureStore } from "../../app/Utilities/ReduxUtilities";
import { initNotifications, initDashboard, initStatus, ReduxObject } from "../../app/DefaultObjects";
import * as AccountingRedux from "../../app/Accounting/Redux";
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
