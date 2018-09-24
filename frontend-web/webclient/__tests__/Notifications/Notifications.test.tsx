import Notifications from "Notifications/index";
import { create } from "react-test-renderer";
import * as React from "react";
import { Provider } from "react-redux";
import { configureStore } from "Utilities/ReduxUtilities";
import { initNotifications, initUploads } from "DefaultObjects";
import notifications from "Notifications/Redux/NotificationsReducer";
import uploader from "Uploader/Redux/UploaderReducer";
import { MemoryRouter } from "react-router";

describe.skip("Notifications", () => {
    // FIXME Will try to contact backend and get wrong result, overwriting the page
    test("Mount notifications", () => {
        expect(create(
            <Provider store={configureStore({ notifications: initNotifications(), uploader: initUploads() }, { notifications, uploader })}>
                <MemoryRouter>
                    <Notifications />
                </MemoryRouter>
            </Provider>
        )).toMatchSnapshot();
    })
}); 