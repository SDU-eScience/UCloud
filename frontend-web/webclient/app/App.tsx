import * as React from "react";
import * as ReactDOM from "react-dom";
import { Provider } from "react-redux";
import { BrowserRouter } from "react-router-dom";
import fileInfo from "Files/Redux/FileInfoReducer";
import { theme, UIGlobalStyle } from "ui-components";
import { createGlobalStyle, ThemeProvider } from "styled-components";
import { Cloud } from "Authentication/SDUCloudObject";
import { initObject } from "DefaultObjects";
import Core from "Core";
import header from "Navigation/Redux/HeaderReducer";
import files from "Files/Redux/FilesReducer";
import status from "Navigation/Redux/StatusReducer";
import applications from "Applications/Redux/BrowseReducer";
import dashboard from "Dashboard/Redux/DashboardReducer";
import zenodo from "Zenodo/Redux/ZenodoReducer";
import sidebar from "Navigation/Redux/SidebarReducer";
import analyses from "Applications/Redux/AnalysesReducer";
import notifications from "Notifications/Redux/NotificationsReducer";
import uploader from "Uploader/Redux/UploaderReducer";
import activity from "Activity/Redux/ActivityReducer";
import detailedResult from "Applications/Redux/DetailedResultReducer";
import simpleSearch from "Search/Redux/SearchReducer";
import detailedFileSearch from "Files/Redux/DetailedFileSearchReducer";
import detailedApplicationSearch from "Applications/Redux/DetailedApplicationSearchReducer";
import * as AppRedux from "Applications/Redux";
import { configureStore } from "Utilities/ReduxUtilities";

window.onload = () => Cloud.receiveAccessTokenOrRefreshIt();

const store = configureStore(initObject(Cloud.homeFolder), {
    activity,
    files,
    dashboard,
    analyses,
    applications,
    header,
    status,
    zenodo,
    sidebar,
    uploader,
    notifications,
    detailedResult,
    simpleSearch,
    detailedFileSearch,
    detailedApplicationSearch,
    fileInfo,
    ...AppRedux.reducers
});


const GlobalStyle = createGlobalStyle`
  ${props => UIGlobalStyle}
`;

ReactDOM.render(
    <Provider store={store}>
        <ThemeProvider theme={theme}>
        <>
            <GlobalStyle />
            <BrowserRouter basename="app">
                <Core />
            </BrowserRouter>
        </>
        </ThemeProvider>
    </Provider>,
    document.getElementById("app")
);