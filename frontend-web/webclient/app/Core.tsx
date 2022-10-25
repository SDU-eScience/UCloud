import * as React from "react";

const Applications = React.lazy(() => import("@/Applications/Browse"));
const ApplicationsOverview = React.lazy(() => import("@/Applications/Overview"));
const AdminOverview = React.lazy(() => import("@/Admin/Overview"));
const App = React.lazy(() => import("@/Applications/Studio/Applications"));
const AvataaarModification = React.lazy(() => import("@/UserSettings/Avataaar"));
const Dashboard = React.lazy(() => import("@/Dashboard/Dashboard"));
const DetailedNews = React.lazy(() => import("@/NewsPost/DetailedNews"));
const FilesRouter = React.lazy(() => import("@/Files/Files"));
const ProviderRouter = React.lazy(() => import("@/Admin/Providers/Router"));
const FileCollectionsRouter = React.lazy(() => import("@/Files/FileCollections"));
const MetadataNamespacesRouter = React.lazy(() => import("@/Files/Metadata/Templates/Namespaces"));
const ShareRouter = React.lazy(() => import("@/Files/Shares"));
const IngoingApplications = React.lazy(() => import("@/Project/Grant/IngoingApplications"));
const OutgoingApplications = React.lazy(() => import("@/Project/Grant/OutgoingApplications"));
const JobShell = React.lazy(() => import("@/Applications/Jobs/Shell"));
const JobWeb = React.lazy(() => import("@/Applications/Jobs/Web"));
const JobVnc = React.lazy(() => import("@/Applications/Jobs/Vnc"));
const LicenseServers = React.lazy(() => import("@/Admin/LicenseServers"));
const LoginPage = React.lazy(() => import("@/Login/Login"));
const NewsList = React.lazy(() => import("@/NewsPost/NewsList"));
const NewsManagement = React.lazy(() => import("@/Admin/NewsManagement"));
const Playground = React.lazy(() => import("@/Playground/Playground"));
const Products = React.lazy(() => import("@/Products/Products"));
const ProjectDashboard = React.lazy(() => import("@/Project/ProjectDashboard"));
const ProjectList = React.lazy(() => import("@/Project/ProjectList"));
const ProjectMembers = React.lazy(() => import("@/Project/Members"));
const ProjectSettings = React.lazy(() => import("@/Project/ProjectSettings"));
const ProjectResources = React.lazy(() => import("@/Project/Resources"));
const ProjectAllocations = React.lazy(() => import("@/Project/Allocations"));
const ProjectList2 = React.lazy(() => import("@/Project/ProjectList2"));
const ProjectDashboard2 = React.lazy(() => import("@/Project/Dashboard2"));
const ProjectMembers2 = React.lazy(() => import("@/Project/Members2"));
const Search = React.lazy(() => import("@/Search/Search"));
const ServiceLicenseAgreement = React.lazy(() => import("@/ServiceLicenseAgreement"));
const Studio = React.lazy(() => import("@/Applications/Studio/Page"));
const Tool = React.lazy(() => import("@/Applications/Studio/Tool"));
const Scripts = React.lazy(() => import("@/Admin/Scripts"));
const UserCreation = React.lazy(() => import("@/Admin/UserCreation"));
const UserSettings = React.lazy(() => import("@/UserSettings/UserSettings"));
const Wayf = React.lazy(() => import("@/Login/Wayf"));
const AppK8Admin = React.lazy(() => import("@/Admin/AppK8Admin"));
const Demo = React.lazy(() => import("@/Playground/Demo"));
const LagTest = React.lazy(() => import("@/Playground/LagTest"));
const Providers = React.lazy(() => import("@/Admin/Providers/Browse"));
const CreateProvider = React.lazy(() => import("@/Admin/Providers/Save"));
const EditProvider = React.lazy(() => import("@/Admin/Providers/Save"));
const RegisterProvider = React.lazy(() => import("@/Admin/Providers/Approve"));
const ProviderConnection = React.lazy(() => import("@/Providers/Connect"));
const ProviderOverview = React.lazy(() => import("@/Providers/Overview"));
const ProviderDetailed = React.lazy(() => import("@/Providers/Detailed"));
const IngressRouter = React.lazy(() => import("@/Applications/Ingresses/Router"));
const LicenseRouter = React.lazy(() => import("@/Applications/Licenses"));
const NetworkIPsRouter = React.lazy(() => import("@/Applications/NetworkIP/Router"));
const SubprojectList = React.lazy(() => import("@/Project/SubprojectList"));
const ManualTestingOverview = React.lazy(() => import("@/Playground/ManualTesting"));
const SyncthingOverview = React.lazy(() => import("@/Syncthing/Overview"));
const SshKeyBrowse = React.lazy(() => import("@/Applications/SshKeys/Browse"));
const SshKeyCreate = React.lazy(() => import("@/Applications/SshKeys/Create"));

import {GrantApplicationEditor, RequestTarget} from "@/Project/Grant/GrantApplicationEditor";
import Sidebar from "@/ui-components/Sidebar";
import Uploader from "@/Files/Uploader";
import Snackbars from "@/Snackbar/Snackbars";
import {Dialog} from "@/Dialog/Dialog";
import {Route, RouteComponentProps, Switch} from "react-router-dom";
import {USER_LOGIN} from "@/Navigation/Redux/HeaderReducer";
import {inDevEnvironment} from "@/UtilityFunctions";
import {History} from "history";
import {ErrorBoundary} from "@/ErrorBoundary/ErrorBoundary";
import {MainContainer} from "@/MainContainer/MainContainer";
import {Client} from "@/Authentication/HttpClientInstance";
import JobRouter from "@/Applications/Jobs/Browse";
import {Debugger} from "@/Debug/Debugger";
import Header from "@/Navigation/Header";
import {CONTEXT_SWITCH, USER_LOGOUT} from "@/Navigation/Redux/HeaderReducer";
import {Provider} from "react-redux";
import {BrowserRouter} from "react-router-dom";
import {createGlobalStyle, ThemeProvider} from "styled-components";
import {Box, theme, UIGlobalStyle} from "@/ui-components";
import {invertedColors} from "@/ui-components/theme";
import {findAvatar} from "@/UserSettings/Redux/AvataaarActions";
import {store} from "@/Utilities/ReduxUtilities";
import {isLightThemeStored, removeExpiredFileUploads, setSiteTheme, toggleCssColors} from "@/UtilityFunctions";
import {injectFonts} from "@/ui-components/GlobalStyle";
import {SharesOutgoing} from "@/Files/SharesOutgoing";
import {TerminalContainer} from "@/Terminal/Container";

const NotFound = (): JSX.Element => (<MainContainer main={<div><h1>Not found.</h1></div>}/>);

const Core = (): JSX.Element => (
    <>
        <Dialog/>
        <Snackbars/>
        <Uploader/>
        <Sidebar/>
        <div style={{ height: "calc(100vh - var(--termsize, 0px))", overflowX: "auto", overflowY: "auto" }}>
            <ErrorBoundary>
                <React.Suspense fallback={<MainContainer main={<div>Loading...</div>}/>}>
                    <Switch>
                        <Route exact path="/login" component={LoginPage}/>
                        <Route exact path="/loginSuccess" component={LoginSuccess}/>
                        <Route exact path="/login/wayf" component={Wayf}/>
                        <Route exact path="/" component={requireAuth(Dashboard)}/>
                        <Route exact path="/dashboard" component={requireAuth(Dashboard)}/>
                        <Route exact path={"/debugger"} component={Debugger}/>

                        <Route path={"/drives"} component={requireAuth(FileCollectionsRouter)}/>
                        <Route path={"/files"} component={requireAuth(FilesRouter)}/>
                        <Route path={"/metadata"} component={requireAuth(MetadataNamespacesRouter)}/>
                        <Route exact path={"/shares/outgoing"} component={requireAuth(SharesOutgoing)}/>
                        <Route path={"/shares"} component={requireAuth(ShareRouter)}/>

                        <Route exact path={"/syncthing"} component={requireAuth(SyncthingOverview)}/>

                        <Route exact path="/applications" component={requireAuth(Applications)}/>
                        <Route exact path="/applications/overview" component={requireAuth(ApplicationsOverview)}/>
                        <Route exact path="/applications/search" component={requireAuth(Search)}/>

                        {!inDevEnvironment() ? null :
                            <Route exact path="/MANUAL-TESTING-OVERVIEW" component={ManualTestingOverview}/>
                        }

                        <Route exact path="/applications/shell/:jobId/:rank" component={requireAuth(JobShell)}/>
                        <Route exact path="/applications/web/:jobId/:rank" component={requireAuth(JobWeb)}/>
                        <Route exact path="/applications/vnc/:jobId/:rank" component={requireAuth(JobVnc)}/>
                        <Route path="/public-links" component={requireAuth(IngressRouter)}/>
                        <Route path="/jobs" component={requireAuth(JobRouter)}/>
                        <Route path="/licenses" component={requireAuth(LicenseRouter)}/>
                        <Route path="/public-ips" component={requireAuth(NetworkIPsRouter)}/>
                        <Route path={"/ssh-keys"} exact component={requireAuth(SshKeyBrowse)}/>
                        <Route path={"/ssh-keys/create"} exact component={requireAuth(SshKeyCreate)}/>

                        <Route exact path="/applications/studio" component={requireAuth(Studio)}/>
                        <Route exact path="/applications/studio/t/:name" component={requireAuth(Tool)}/>
                        <Route exact path="/applications/studio/a/:name" component={requireAuth(App)}/>

                        {!inDevEnvironment() ? null : <Route exact path={"/playground"} component={Playground}/>}
                        {!inDevEnvironment() ? null : <Route exact path={"/playground/demo"} component={Demo}/>}
                        {!inDevEnvironment() ? null : <Route exact path={"/playground/lag"} component={LagTest}/>}

                        <Route exact path="/admin" component={requireAuth(AdminOverview)}/>
                        <Route exact path="/admin/userCreation" component={requireAuth(UserCreation)}/>
                        <Route exact path="/admin/licenseServers" component={requireAuth(LicenseServers)}/>
                        <Route exact path="/admin/news" component={requireAuth(NewsManagement)}/>
                        <Route exact path="/admin/appk8" component={requireAuth(AppK8Admin)}/>
                        <Route exact path="/admin/scripts" component={requireAuth(Scripts)}/>

                        <Route exact path="/admin/providers" component={requireAuth(Providers)}/>
                        <Route exact path="/admin/providers/create" component={requireAuth(CreateProvider)}/>
                        <Route exact path="/admin/providers/edit/:id" component={requireAuth(EditProvider)}/>
                        <Route exact path="/admin/providers/register" component={requireAuth(RegisterProvider)}/>

                        <Route exact path={"/providers/connect"} component={requireAuth(ProviderConnection)} />
                        <Route exact path="/providers/create" component={requireAuth(CreateProvider)} />
                        <Route exact path="/providers/edit/:id" component={requireAuth(EditProvider)} />
                        <Route exact path="/providers/register" component={requireAuth(RegisterProvider)} />
                        <Route exact path="/providers/overview" component={ProviderOverview} />
                        <Route exact path="/providers/detailed/:id" component={ProviderDetailed} />
                        <Route path={"/providers"} component={requireAuth(ProviderRouter)} />

                        <Route exact path="/news/detailed/:id" component={DetailedNews}/>
                        <Route exact path="/news/list/:filter?" component={NewsList}/>

                        <Route
                            exact
                            path="/users/settings"
                            component={requireAuth(UserSettings, {requireTwoFactor: false})}
                        />
                        <Route exact path="/users/avatar" component={requireAuth(AvataaarModification)}/>

                        <Route exact path="/skus" component={Products}/>

                        <Route exact path="/projects2/" component={requireAuth(ProjectList2)}/>
                        <Route exact path="/projects2/:project" component={requireAuth(ProjectDashboard2)}/>
                        <Route exact path="/projects2/:project/members" component={requireAuth(ProjectMembers2)}/>

                        <Route exact path="/projects/" component={requireAuth(ProjectList)}/>
                        <Route exact path="/subprojects" component={requireAuth(SubprojectList)}/>
                        <Route exact path="/project/dashboard" component={requireAuth(ProjectDashboard)}/>
                        <Route exact path="/project/settings/:page?" component={requireAuth(ProjectSettings)}/>
                        <Route exact path="/project/resources" component={requireAuth(ProjectResources)}/>
                        <Route exact path="/project/allocations" component={requireAuth(ProjectAllocations)}/>
                        <Route
                            exact
                            path="/project/grants/existing"
                            component={requireAuth(GrantApplicationEditor(RequestTarget.EXISTING_PROJECT))}
                        />
                        <Route
                            exact
                            path="/project/grants/personal"
                            component={requireAuth(GrantApplicationEditor(RequestTarget.PERSONAL_PROJECT))}
                        />
                        <Route
                            exact
                            path="/project/grants/new"
                            component={requireAuth(GrantApplicationEditor(RequestTarget.NEW_PROJECT))}
                        />
                        <Route
                            exact
                            path="/project/grants/view/:appId"
                            component={requireAuth(GrantApplicationEditor(RequestTarget.VIEW_APPLICATION))}
                        />
                        <Route exact path="/project/members/:group?/:member?" component={requireAuth(ProjectMembers)}/>
                        <Route exact path="/project/grants/ingoing" component={requireAuth(IngoingApplications)}/>
                        <Route exact path="/project/grants/outgoing" component={requireAuth(OutgoingApplications)}/>

                        <Route
                            exact
                            path="/sla"
                            component={requireAuth(ServiceLicenseAgreement, {
                                requireTwoFactor: false,
                                requireSla: false
                            })}
                        />
                        <Route component={NotFound}/>
                    </Switch>
                </React.Suspense>
            </ErrorBoundary>
        </div>

        <TerminalContainer/>
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

const LoginSuccess = (props: { history: History }): null => {
    React.useEffect(() => {
        dispatchUserAction(USER_LOGIN);
        onLogin();
        props.history.push("/");
    }, []);
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

function MainApp({children}: { children?: React.ReactNode }): JSX.Element {
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
            <GlobalStyle/>
            <BrowserRouter basename="app">
                <Header toggleTheme={toggle}/>
                {children}
            </BrowserRouter>
        </ThemeProvider>
    );
}

injectFonts();

export default function UCloudApp(): JSX.Element {
    return (
        <Provider store={store}>
            <MainApp>
                <Core/>
            </MainApp>
        </Provider>
    );
}
