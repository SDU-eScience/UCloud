import * as React from "react";
import Header from "Navigation/Header";
import { create } from "react-test-renderer";
import { Provider } from "react-redux";
import { configureStore } from "Utilities/ReduxUtilities";
import { initSidebar, initHeader, initUploads, initNotifications, initZenodo } from "DefaultObjects";
import header from "Navigation/Redux/HeaderReducer";
import sidebar from "Navigation/Redux/SidebarReducer";
import uploader from "Uploader/Redux/UploaderReducer";
import notifications from "Notifications/Redux/NotificationsReducer";
import zenodo from "Zenodo/Redux/ZenodoReducer";
import { MemoryRouter } from "react-router";

describe("Header", () => {
    // FIXME Will try to contact backend and get wrong result, overwriting the page
    test.skip("Mount header", () => {
        expect(create(
            <Provider store={configureStore({ header: initHeader(), sidebar: initSidebar(), uploader: initUploads(), notifications: initNotifications(), zenodo: initZenodo() }, { header, sidebar, uploader, notifications, zenodo })}>
                <MemoryRouter>
                    <Header />
                </MemoryRouter>
            </Provider >).toJSON()
        ).toMatchSnapshot();
    });
});