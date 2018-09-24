import * as React from "react";
import SimpleSearch from "SimpleSearch/SimpleSearch";
import { create } from "react-test-renderer";
import { configureStore } from "Utilities/ReduxUtilities";
import { Provider } from "react-redux"
import { initNotifications } from "DefaultObjects";
import notifications from "Notifications/Redux/NotificationsReducer";
import { mount, configure, shallow } from "enzyme";
import Adapter from "enzyme-adapter-react-16";

configure({ adapter: new Adapter() });


describe("Simple Search", () => {
    test("Mount simplesearch", () => {
        const store = configureStore({ notifications: initNotifications() }, { notifications });
        expect(create(
            <Provider store={store}>
                <SimpleSearch match={{ params: { 0: "", priority: "projects" } }} />
            </Provider>
        )).toMatchSnapshot();
    });
});