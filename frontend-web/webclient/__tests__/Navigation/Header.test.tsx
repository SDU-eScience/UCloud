import * as React from "react";
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
        return false;
    });
});