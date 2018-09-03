import * as React from "react";
import * as ReactDOM from "react-dom";
import { Provider } from "react-redux";
import { BrowserRouter } from "react-router-dom";
import Core from "Core";
import { Cloud } from "Authentication/SDUCloudObject";
import { initObject } from "DefaultObjects";
import header from "Navigation/Redux/HeaderReducer";
import files from "Files/Redux/FilesReducer";
import uppyReducers from "Uppy/Redux/UppyReducers";
import status from "Navigation/Redux/StatusReducer";
import applications from "Applications/Redux/ApplicationsReducer";
import dashboard from "Dashboard/Redux/DashboardReducer";
import zenodo from "Zenodo/Redux/ZenodoReducer";
import sidebar from "Navigation/Redux/SidebarReducer";
import analyses from "Applications/Redux/AnalysesReducer";
import notifications from "Notifications/Redux/NotificationsReducer";
import uploader from "Uploader/Redux/UploaderReducer";
import { configureStore } from "Utilities/ReduxUtilities";

window.onload = () => Cloud.receiveAccessTokenOrRefreshIt();

const store = configureStore(initObject(Cloud), {
    files,
    dashboard,
    analyses,
    applications,
    uppy: uppyReducers,
    header,
    status,
    zenodo,
    sidebar,
    uploader,
    notifications
});

ReactDOM.render(
    <Provider store={store}>
        <BrowserRouter basename="app">
            <Core />
        </BrowserRouter>
    </Provider>,
    document.getElementById("app")
);
