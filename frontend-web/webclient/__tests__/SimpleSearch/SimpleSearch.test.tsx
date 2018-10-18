import * as React from "react";
import SimpleSearch from "SimpleSearch/SimpleSearch";
import { create } from "react-test-renderer";
import { configureStore } from "Utilities/ReduxUtilities";
import { Provider } from "react-redux"
import { initNotifications, initSimpleSearch } from "DefaultObjects";
import notifications from "Notifications/Redux/NotificationsReducer";
import { configure } from "enzyme";
import simpleSearch from "SimpleSearch/Redux/SimpleSearchReducer";
import * as Adapter from "enzyme-adapter-react-16";

configure({ adapter: new Adapter() });


describe("Simple Search", () => {
    test("Mount simplesearch", () => {
        const store = configureStore({ notifications: initNotifications(), simpleSearch: initSimpleSearch() }, { notifications, simpleSearch });
        expect(create(
            <Provider store={store}>
                <SimpleSearch match={{ params: { 0: "", priority: "projects" } }} />
            </Provider>
        )).toMatchSnapshot();
    });
});