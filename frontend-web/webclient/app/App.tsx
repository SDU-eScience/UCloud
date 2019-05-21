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
import avatar from "UserSettings/Redux/AvataaarReducer";
import header, { USER_LOGIN, USER_LOGOUT, CONTEXT_SWITCH } from "Navigation/Redux/HeaderReducer";
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
import detailedProjectSearch from "Project/Redux/ProjectSearchReducer";
import filePreview from "Files/Redux/FilePreviewReducer";
import shares from "Shares/Redux/SharesReducer";
import * as AppRedux from "Applications/Redux";
import * as AccountingRedux from "Accounting/Redux";
import snackbar from "Snackbar/Redux/SnackbarsReducer";
import * as FavoritesRedux from "Favorites/Redux";
import dialog from "Dialog/Redux/DialogReducer";
import { configureStore } from "Utilities/ReduxUtilities";
import { responsiveStoreEnhancer, createResponsiveStateReducer } from 'redux-responsive';
import { responsiveBP } from "ui-components/theme";
import { fetchLoginStatus } from "Zenodo/Redux/ZenodoActions";
import { findAvatar } from "UserSettings/Redux/AvataaarActions";
import { addSnack } from "Snackbar/Redux/SnackbarsActions";
import { Snack } from "Snackbar/Snackbars";

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
    detailedProjectSearch,
    fileInfo,
    filePreview,
    ...AppRedux.reducers,
    ...AccountingRedux.reducers,
    ...FavoritesRedux.reducers,
    snackbar,
    dialog,
    avatar,
    shares,
    responsive: createResponsiveStateReducer(
        responsiveBP,
        { infinity: "xxl" }),
}, responsiveStoreEnhancer);

export function dispatchUserAction(type: typeof USER_LOGIN | typeof USER_LOGOUT | typeof CONTEXT_SWITCH) {
    store.dispatch({ type })
}

export function GLOBAL_addSnack(snack: Snack) {
    store.dispatch(addSnack(snack))
}

export async function onLogin() {
    store.dispatch(await fetchLoginStatus());
    store.dispatch(await findAvatar());
}

const GlobalStyle = createGlobalStyle`
  ${() => UIGlobalStyle}
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