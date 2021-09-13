import * as React from "react";

import Applications from "@/Applications/Browse";
import ApplicationsOverview from "@/Applications/Overview";
import ApplicationView from "@/Applications/View";
import AdminOverview from "@/Admin/Overview";
import App from "@/Applications/Studio/Applications";
import AvataaarModification from "@/UserSettings/Avataaar";
import Dashboard from "@/Dashboard/Dashboard";
import DetailedNews from "@/NewsPost/DetailedNews";
import FilesRouter from "@/Files/Files";
import FileCollectionsRouter from "@/Files/FileCollections";
import MetadataNamespacesRouter from "@/Files/Metadata/Templates/Namespaces";
import Shares from "@/Files/Shares";
import IngoingApplications from "@/Project/Grant/IngoingApplications";
import JobShell from "@/Applications/Jobs/Shell";
import JobWeb from "@/Applications/Jobs/Web";
import JobVnc from "@/Applications/Jobs/Vnc";
import LandingPage from "@/Project/Grant/LandingPage";
import LicenseServers from "@/Admin/LicenseServers";
import LoginPage from "@/Login/Login";
import LoginSelection from "@/Login/LoginSelection";
import NewsList from "@/NewsPost/NewsList";
import NewsManagement from "@/Admin/NewsManagement";
import OutgoingApplications from "@/Project/Grant/OutgoingApplications";
import Playground from "@/Playground/Playground";
import Products from "@/Products/Products";
import ProjectBrowser from "@/Project/Grant/ProjectBrowser";
import ProjectDashboard from "@/Project/ProjectDashboard";
import ProjectList from "@/Project/ProjectList";
import ProjectMembers from "@/Project/Members";
import ProjectSettings from "@/Project/ProjectSettings";
import ProjectResources from "@/Project/Resources";
import Search from "@/Search/Search";
import ServiceLicenseAgreement from "@/ServiceLicenseAgreement";
import Studio from "@/Applications/Studio/Page";
import Tool from "@/Applications/Studio/Tool";
import UserCreation from "@/Admin/UserCreation";
import UserSettings from "@/UserSettings/UserSettings";
import Wayf from "@/Login/Wayf";
import AppK8Admin from "@/Admin/AppK8Admin";
import AppAauAdmin from "@/Admin/AppAauAdmin";
import Demo from "@/Playground/Demo";
import LagTest from "@/Playground/LagTest";
import Providers from "@/Admin/Providers/Browse";
import CreateProvider from "@/Admin/Providers/Create";
import RegisterProvider from "@/Admin/Providers/Approve";
import ViewProvider from "@/Admin/Providers/View";
import ProviderConnection from "@/Providers/Connect";
import IngressRouter from "@/Applications/Ingresses/Router";
import LicenseRouter from "@/Applications/Licenses";
import NetworkIPsRouter from "@/Applications/NetworkIP/Router";
import {GrantApplicationEditor, RequestTarget} from "@/Project/Grant/GrantApplicationEditor";
import Sidebar from "@/ui-components/Sidebar";
import Uploader from "@/Files/Uploader";
import Snackbars from "@/Snackbar/Snackbars";
import Dialog from "@/Dialog/Dialog";
import {Route, RouteComponentProps, Switch} from "react-router-dom";
import {USER_LOGIN} from "@/Navigation/Redux/HeaderReducer";
import {inDevEnvironment} from "@/UtilityFunctions";
import {History} from "@/history";
import {ErrorBoundary} from "@/ErrorBoundary/ErrorBoundary";
import {MainContainer} from "@/MainContainer/MainContainer";
import {Client} from "@/Authentication/HttpClientInstance";
import CONF from "../site.config.json";
import JobRouter from "@/Applications/Jobs/NewApi";
import {Debugger} from "@/Debug/Debugger";
import Header from "@/Navigation/Header";
import {CONTEXT_SWITCH, USER_LOGOUT} from "@/Navigation/Redux/HeaderReducer";
import {Provider} from "react-redux";
import {BrowserRouter} from "react-router-dom";
import {createGlobalStyle, ThemeProvider} from "styled-components";
import {theme, UIGlobalStyle} from "@/ui-components";
import {invertedColors} from "@/ui-components/theme";
import {findAvatar} from "@/UserSettings/Redux/AvataaarActions";
import {store} from "@/Utilities/ReduxUtilities";
import {isLightThemeStored, removeExpiredFileUploads, setSiteTheme, toggleCssColors} from "@/UtilityFunctions";
import {injectFonts} from "@/ui-components/GlobalStyle";

const NotFound = (): JSX.Element => (<MainContainer main={<div><h1>Not found.</h1></div>} />);

const Core = (): JSX.Element => (
    <>
        <Dialog />
        <Snackbars />
        <Uploader />
        <Sidebar />
        <ErrorBoundary>
            <React.Suspense fallback={<div>Loading</div>}>
                <Switch>
                    <Route exact path="/login" component={LoginPage} />
                    {inDevEnvironment() || window.location.host === CONF.DEV_SITE ?
                        <Route exact path="/login/selection" component={LoginSelection} /> :
                        <Route exact path="/login/selection" component={LoginPage} />
                    }
                    <Route exact path="/loginSuccess" component={LoginSuccess} />
                    <Route exact path="/login/wayf" component={Wayf} />
                    <Route exact path="/" component={requireAuth(Dashboard)} />
                    <Route exact path="/dashboard" component={requireAuth(Dashboard)} />
                    <Route exact path={"/debugger"} component={Debugger} />

                    <Route path={"/drives"}><FileCollectionsRouter /></Route>
                    <Route path={"/files"}><FilesRouter /></Route>
                    <Route path={"/metadata"}><MetadataNamespacesRouter /></Route>
                    <Route exact path="/shares" component={requireAuth(Shares)} />

                    <Route exact path="/applications" component={requireAuth(Applications)} />
                    <Route exact path="/applications/overview" component={requireAuth(ApplicationsOverview)} />
                    <Route
                        exact
                        path="/applications/details/:appName/:appVersion"
                        component={requireAuth(ApplicationView)}
                    />

                    <Route exact path="/applications/shell/:jobId/:rank" component={JobShell} />
                    <Route exact path="/applications/web/:jobId/:rank" component={JobWeb} />
                    <Route exact path="/applications/vnc/:jobId/:rank" component={JobVnc} />
                    <Route path="/public-links"><IngressRouter /></Route>
                    <Route path="/jobs"><JobRouter /></Route>
                    <Route path="/licenses"><LicenseRouter /></Route>
                    <Route path="/public-ips"><NetworkIPsRouter /></Route>

                    <Route exact path="/applications/studio" component={requireAuth(Studio)} />
                    <Route exact path="/applications/studio/t/:name" component={requireAuth(Tool)} />
                    <Route exact path="/applications/studio/a/:name" component={requireAuth(App)} />

                    {!inDevEnvironment() ? null : <Route exact path={"/playground"} component={Playground} />}
                    {!inDevEnvironment() ? null : <Route exact path={"/playground/demo"} component={Demo} />}
                    {!inDevEnvironment() ? null : <Route exact path={"/playground/lag"} component={LagTest} />}

                    <Route exact path="/admin" component={requireAuth(AdminOverview)} />
                    <Route exact path="/admin/userCreation" component={requireAuth(UserCreation)} />
                    <Route exact path="/admin/licenseServers" component={requireAuth(LicenseServers)} />
                    <Route exact path="/admin/news" component={requireAuth(NewsManagement)} />
                    <Route exact path="/admin/appk8" component={requireAuth(AppK8Admin)} />
                    <Route exact path="/admin/appaau" component={requireAuth(AppAauAdmin)} />
                    <Route exact path="/admin/providers" component={requireAuth(Providers)} />
                    <Route exact path="/admin/providers/create" component={requireAuth(CreateProvider)} />
                    <Route exact path="/admin/providers/register" component={requireAuth(RegisterProvider)} />
                    <Route exact path="/admin/providers/view/:id" component={requireAuth(ViewProvider)} />

                    <Route exact path="/news/detailed/:id" component={DetailedNews} />
                    <Route exact path="/news/list/:filter?" component={NewsList} />

                    <Route
                        exact
                        path="/users/settings"
                        component={requireAuth(UserSettings, {requireTwoFactor: false})}
                    />
                    <Route exact path="/users/avatar" component={requireAuth(AvataaarModification)} />

                    <Route exact path="/search/:priority" component={requireAuth(Search)} />

                    <Route exact path="/skus" component={Products} />

                    <Route exact path="/projects" component={requireAuth(ProjectList)} />
                    <Route exact path="/project/dashboard" component={requireAuth(ProjectDashboard)} />
                    <Route exact path="/project/settings/:page?" component={requireAuth(ProjectSettings)} />
                    <Route exact path="/project/resources" component={requireAuth(ProjectResources)} />
                    <Route
                        exact
                        path="/project/grants-landing"
                        component={requireAuth(LandingPage)}
                    />
                    <Route
                        exact
                        path="/project/grants/existing"
                        component={requireAuth(GrantApplicationEditor(RequestTarget.EXISTING_PROJECT))}
                    />
                    <Route
                        exact
                        path="/project/grants/personal/:projectId"
                        component={requireAuth(GrantApplicationEditor(RequestTarget.PERSONAL_PROJECT))}
                    />
                    <Route
                        exact
                        path="/project/grants/new/:projectId"
                        component={requireAuth(GrantApplicationEditor(RequestTarget.NEW_PROJECT))}
                    />
                    <Route
                        exact
                        path="/project/grants/view/:appId"
                        component={requireAuth(GrantApplicationEditor(RequestTarget.VIEW_APPLICATION))}
                    />
                    <Route
                        exact
                        path="/project/members/:group?/:member?"
                        component={requireAuth(ProjectMembers)}
                    />
                    <Route exact path="/project/grants/ingoing" component={requireAuth(IngoingApplications)} />
                    <Route exact path="/project/grants/outgoing" component={requireAuth(OutgoingApplications)} />
                    <Route exact path="/projects/browser/:action" component={requireAuth(ProjectBrowser)} />

                    <Route exact path={"/providers/connect"} component={requireAuth(ProviderConnection)} />

                    <Route
                        exact
                        path="/sla"
                        component={requireAuth(ServiceLicenseAgreement, {requireTwoFactor: false, requireSla: false})}
                    />
                    <Route component={NotFound} />
                </Switch>
            </React.Suspense>
        </ErrorBoundary>
    </>
);

interface RequireAuthOpts {
    requireTwoFactor?: boolean;
    requireSla?: boolean;
}

function requireAuth<T>(Delegate: React.FunctionComponent<T>, opts?: RequireAuthOpts): React.FunctionComponent<T> {
    return function Auth(props: T & RouteComponentProps) {
        const info = Client.userInfo;
        if (!Client.isLoggedIn || info === undefined) {
            props.history.push("/login");
            return null;
        }

        if (opts === undefined || opts.requireSla !== false) {
            if (info.serviceLicenseAgreement === false) {
                props.history.push("/sla");
                return null;
            }
        }

        if (opts === undefined || opts.requireTwoFactor) {
            if (info.principalType === "password" && Client.userRole === "USER" &&
                info.twoFactorAuthentication === false) {
                props.history.push("/users/settings");
                return null;
            }
        }

        return <Delegate {...props} />;
    };
}

const LoginSuccess = (props: {history: History}): null => {
    dispatchUserAction(USER_LOGIN);
    onLogin();
    props.history.push("/");
    return null;
};

export function dispatchUserAction(type: typeof USER_LOGIN | typeof USER_LOGOUT | typeof CONTEXT_SWITCH): void {
    store.dispatch({type});
}

export async function onLogin(): Promise<void> {
    const action = await findAvatar();
    if (action !== null) store.dispatch(action);
}

const GlobalStyle = createGlobalStyle`
  ${UIGlobalStyle}
`;

Client.initializeStore(store);
removeExpiredFileUploads();

function MainApp({children}: {children?: React.ReactNode}): JSX.Element {
    const [isLightTheme, setTheme] = React.useState(() => {
        const isLight = isLightThemeStored();
        toggleCssColors(isLight);
        return isLight;
    });
    const setAndStoreTheme = (isLight: boolean): void => (setSiteTheme(isLight), setTheme(isLight));

    function toggle(): void {
        toggleCssColors(isLightTheme);
        setAndStoreTheme(!isLightTheme);
    }

    return (
        <ThemeProvider theme={isLightTheme ? theme : {...theme, colors: invertedColors}}>
            <GlobalStyle />
            <BrowserRouter basename="app">
                <Header toggleTheme={toggle} />
                {children}
            </BrowserRouter>
        </ThemeProvider>
    );
}

injectFonts();

export default function UCloudApp(): JSX.Element {
    return (<Provider store={store}>
        <MainApp>
            <Core />
        </MainApp>
    </Provider>);
}
