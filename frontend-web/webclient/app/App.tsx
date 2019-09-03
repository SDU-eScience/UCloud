import * as AccountingRedux from "Accounting/Redux";
import activity from "Activity/Redux/ActivityReducer";
import * as AppRedux from "Applications/Redux";
import analyses from "Applications/Redux/AnalysesReducer";
import applications from "Applications/Redux/BrowseReducer";
import detailedApplicationSearch from "Applications/Redux/DetailedApplicationSearchReducer";
import {Cloud} from "Authentication/SDUCloudObject";
import Core from "Core";
import dashboard from "Dashboard/Redux/DashboardReducer";
import {initObject} from "DefaultObjects";
import detailedFileSearch from "Files/Redux/DetailedFileSearchReducer";
import fileInfo from "Files/Redux/FileInfoReducer";
import filePreview from "Files/Redux/FilePreviewReducer";
import Header from "Navigation/Header";
import header, {CONTEXT_SWITCH, USER_LOGIN, USER_LOGOUT} from "Navigation/Redux/HeaderReducer";
import sidebar from "Navigation/Redux/SidebarReducer";
import status from "Navigation/Redux/StatusReducer";
import notifications from "Notifications/Redux/NotificationsReducer";
import * as ProjectRedux from "Project/Redux";
import * as React from "react";
import * as ReactDOM from "react-dom";
import {Provider} from "react-redux";
import {BrowserRouter} from "react-router-dom";
import {createResponsiveStateReducer, responsiveStoreEnhancer} from "redux-responsive";
import simpleSearch from "Search/Redux/SearchReducer";
import {createGlobalStyle, ThemeProvider} from "styled-components";
import {theme, UIGlobalStyle} from "ui-components";
import {invertedColors, responsiveBP} from "ui-components/theme";
import uploader from "Uploader/Redux/UploaderReducer";
import {findAvatar} from "UserSettings/Redux/AvataaarActions";
import avatar from "UserSettings/Redux/AvataaarReducer";
import {configureStore} from "Utilities/ReduxUtilities";
import {isLightThemeStored, setSiteTheme} from "UtilityFunctions";

const store = configureStore(initObject(), {
    activity,
    dashboard,
    analyses,
    applications,
    header,
    status,
    sidebar,
    uploader,
    notifications,
    simpleSearch,
    detailedFileSearch,
    detailedApplicationSearch,
    fileInfo,
    filePreview,
    ...AppRedux.reducers,
    ...AccountingRedux.reducers,
    avatar,
    loading,
    project: ProjectRedux.reducer,
    responsive: createResponsiveStateReducer(
        responsiveBP,
        {infinity: "xxl"}),
}, responsiveStoreEnhancer);

function loading(state = false, action: {type: string}): boolean {
    switch (action.type) {
        case "LOADING_START":
            return true;
        case "LOADING_END":
            return false;
        default:
            return state;
    }
}

export function dispatchUserAction(type: typeof USER_LOGIN | typeof USER_LOGOUT | typeof CONTEXT_SWITCH) {
    store.dispatch({type});
}

export async function onLogin() {
    const action = await findAvatar();
    if (action !== null) store.dispatch(action);
}

const GlobalStyle = createGlobalStyle`
  ${() => UIGlobalStyle}
`;

Cloud.initializeStore(store);

function App({children}) {
    const [isLightTheme, setTheme] = React.useState(isLightThemeStored());
    const setAndStoreTheme = (isLight: boolean) => (setSiteTheme(isLight), setTheme(isLight));
    return (
        <ThemeProvider theme={isLightTheme ? theme : {...theme, colors: invertedColors}}>
            <>
                <GlobalStyle/>
                <BrowserRouter basename="app">
                    <Header toggleTheme={() => isLightTheme ? setAndStoreTheme(false) : setAndStoreTheme(true)}/>
                    {children}
                </BrowserRouter>
            </>
        </ThemeProvider>
    );
}

ReactDOM.render(
    <Provider store={store}>
        <App>
            <Core/>
        </App>
    </Provider>,
    document.getElementById("app")
);
