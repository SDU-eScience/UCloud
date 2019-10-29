import {createMemoryHistory} from "history";
import "jest-styled-components";
import * as React from "react";
import {Provider} from "react-redux";
import {MemoryRouter} from "react-router";
import {create} from "react-test-renderer";
import {Store} from "redux";
import {ThemeProvider} from "styled-components";
import * as AccountingRedux from "../../app/Accounting/Redux";
import Dashboard from "../../app/Dashboard/Dashboard";
import dashboard from "../../app/Dashboard/Redux/DashboardReducer";
import {initDashboard, initNotifications, initStatus, ReduxObject} from "../../app/DefaultObjects";
import status from "../../app/Navigation/Redux/StatusReducer";
import notifications from "../../app/Notifications/Redux/NotificationsReducer";
import theme, {responsiveBP} from "../../app/ui-components/theme";
import {configureStore} from "../../app/Utilities/ReduxUtilities";
import {createResponsiveStateReducer, responsiveStoreEnhancer} from "redux-responsive";

const createStore = () => {
    return configureStore({
        notifications: initNotifications(),
        dashboard: initDashboard(),
        status: initStatus(),
        ...AccountingRedux.init()
    }, {
        dashboard,
        notifications,
        status,
        ...AccountingRedux.reducers,
        responsive: createResponsiveStateReducer(
            responsiveBP,
            {infinity: "xxl"}),
    }, responsiveStoreEnhancer);
};

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

test("Dashboard mount", () => {
    expect(create(<WrappedDashboard store={createStore()} />)).toMatchSnapshot();
});
