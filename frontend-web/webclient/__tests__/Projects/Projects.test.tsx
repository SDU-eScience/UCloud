import * as React from "react";
import Projects from "Projects/Projects";
import { create } from "react-test-renderer";
import { configureStore } from "Utilities/ReduxUtilities";
import { initNotifications } from "DefaultObjects";
import notifications from "Notifications/Redux/NotificationsReducer";
import { Provider } from "react-redux";

describe("Projects", () => {
    test("Mount projects", () => {
        const store = configureStore({ notifications: initNotifications() }, { notifications });
        expect(create(<Provider store={store}><Projects /></Provider>)).toMatchSnapshot();
    });
});