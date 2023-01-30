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
const ProjectSettings = React.lazy(() => import("@/Project/ProjectSettings"));
const ProjectResources = React.lazy(() => import("@/Project/Resources"));
const ProjectAllocations = React.lazy(() => import("@/Project/Allocations"));
const ProjectList = React.lazy(() => import("@/Project/ProjectList2"));
const ProjectDashboard = React.lazy(() => import("@/Project/Dashboard2"));
const ProjectMembers = React.lazy(() => import("@/Project/Members2"));
const ProjectAcceptInviteLink = React.lazy(() => import("@/Project/AcceptInviteLink"));
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
import {Navigate, Route, Routes} from "react-router-dom";
import {USER_LOGIN} from "@/Navigation/Redux/HeaderReducer";
import {inDevEnvironment} from "@/UtilityFunctions";
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
import {theme, UIGlobalStyle} from "@/ui-components";
import {invertedColors} from "@/ui-components/theme";
import {findAvatar} from "@/UserSettings/Redux/AvataaarActions";
import {store} from "@/Utilities/ReduxUtilities";
import {isLightThemeStored, removeExpiredFileUploads, setSiteTheme, toggleCssColors} from "@/UtilityFunctions";
import {injectFonts} from "@/ui-components/GlobalStyle";
import {SharesOutgoing} from "@/Files/SharesOutgoing";
import {ApplicationsOverview2} from "./Applications/Overview2";
import {TerminalContainer} from "@/Terminal/Container";
import {LOGIN_REDIRECT_KEY} from "@/Login/Login";
import AppRoutes from "./Routes";

const NotFound = (): JSX.Element => (<MainContainer main={<div><h1>Not found.</h1></div>} />);

const Core = (): JSX.Element => (
    <>
        <Dialog />
        <Snackbars />
        <Uploader />
        <Sidebar />
        <div data-component="router-wrapper" style={{height: "calc(100vh - var(--termsize, 0px))", overflowX: "auto", overflowY: "auto"}}>
            <ErrorBoundary>
                <React.Suspense fallback={<MainContainer main={<div>Loading...</div>} />}>
                    <Routes>
                        <Route path="/login" element={<LoginPage />} />
                        <Route path="/loginSuccess" element={<LoginSuccess />} />
                        <Route path="/login/wayf" element={<Wayf />} />
                        <Route path="/" element={React.createElement(requireAuth(Dashboard))} />
                        <Route path="/dashboard" element={React.createElement(requireAuth(Dashboard))} />
                        <Route path={"/debugger"} element={<Debugger />} />
                        <Route path={"/drives/*"} element={React.createElement(requireAuth(FileCollectionsRouter))} />
                        <Route path={"/files/*"} element={React.createElement(requireAuth(FilesRouter))} />
                        <Route path={"/metadata/*"} element={React.createElement(requireAuth(MetadataNamespacesRouter))} />
                        <Route path={"/shares/outgoing"} element={React.createElement(requireAuth(SharesOutgoing))} />
                        <Route path={"/shares/*"} element={React.createElement(requireAuth(ShareRouter))} />

                        <Route path={"/syncthing"} element={React.createElement(requireAuth(SyncthingOverview))} />

                        <Route path="/applications" element={React.createElement(requireAuth(Applications))} />
                        <Route path="/applications/overview_old" element={React.createElement(requireAuth(ApplicationsOverview))} />
                        <Route path="/applications/overview" element={React.createElement(requireAuth(ApplicationsOverview2))} />
                        <Route path="/applications/search" element={React.createElement(requireAuth(Search))} />

                        {!inDevEnvironment() ? null :
                            <Route path="/MANUAL-TESTING-OVERVIEW" element={<ManualTestingOverview />} />
                        }

                        <Route path="/applications/shell/:jobId/:rank" element={React.createElement(requireAuth(JobShell))} />
                        <Route path="/applications/web/:jobId/:rank" element={React.createElement(requireAuth(JobWeb))} />
                        <Route path="/applications/vnc/:jobId/:rank" element={React.createElement(requireAuth(JobVnc))} />
                        <Route path="/public-links/*" element={React.createElement(requireAuth(IngressRouter))} />
                        <Route path="/jobs/*" element={React.createElement(requireAuth(JobRouter))} />
                        <Route path="/licenses/*" element={React.createElement(requireAuth(LicenseRouter))} />
                        <Route path="/public-ips/*" element={React.createElement(requireAuth(NetworkIPsRouter))} />
                        <Route path={"/ssh-keys"} element={React.createElement(requireAuth(SshKeyBrowse))} />
                        <Route path={"/ssh-keys/create"} element={React.createElement(requireAuth(SshKeyCreate))} />

                        <Route path="/applications/studio" element={React.createElement(requireAuth(Studio))} />
                        <Route path="/applications/studio/t/:name" element={React.createElement(requireAuth(Tool))} />
                        <Route path="/applications/studio/a/:name" element={React.createElement(requireAuth(App))} />

                        {!inDevEnvironment() ? null : <Route path={"/playground"} element={<Playground />} />}
                        {!inDevEnvironment() ? null : <Route path={"/playground/demo"} element={<Demo />} />}
                        {!inDevEnvironment() ? null : <Route path={"/playground/lag"} element={<LagTest />} />}

                        <Route path="/admin" element={React.createElement(requireAuth(AdminOverview))} />
                        <Route path="/admin/userCreation" element={React.createElement(requireAuth(UserCreation))} />
                        <Route path="/admin/licenseServers" element={React.createElement(requireAuth(LicenseServers))} />
                        <Route path="/admin/news" element={React.createElement(requireAuth(NewsManagement))} />
                        <Route path="/admin/appk8" element={React.createElement(requireAuth(AppK8Admin))} />
                        <Route path="/admin/scripts" element={React.createElement(requireAuth(Scripts))} />

                        <Route path="/admin/providers" element={React.createElement(requireAuth(Providers))} />
                        <Route path="/admin/providers/create" element={React.createElement(requireAuth(CreateProvider))} />
                        <Route path="/admin/providers/edit/:id" element={React.createElement(requireAuth(EditProvider))} />
                        <Route path="/admin/providers/register" element={React.createElement(requireAuth(RegisterProvider))} />

                        <Route path="/providers/connect" element={React.createElement(requireAuth(ProviderConnection))} />
                        <Route path="/providers/create" element={React.createElement(requireAuth(CreateProvider))} />
                        <Route path="/providers/edit/:id" element={React.createElement(requireAuth(EditProvider))} />
                        <Route path="/providers/register" element={React.createElement(requireAuth(RegisterProvider))} />
                        <Route path="/providers/overview" element={<ProviderOverview />} />
                        <Route path="/providers/detailed/:id" element={<ProviderDetailed />} />
                        <Route path="/providers/*" element={React.createElement(requireAuth(ProviderRouter))} />

                        <Route path={AppRoutes.news.detailed(":id")} element={<DetailedNews />} />

                        {/* Nullable paths args aren't supported (yet?) so we duplicate. */}
                        <Route path={AppRoutes.news.list("")} element={<NewsList />} />
                        <Route path={AppRoutes.news.list(":filter")} element={<NewsList />} />

                        <Route
                            path={AppRoutes.users.settings()}
                            element={React.createElement(requireAuth(UserSettings, {requireTwoFactor: false}))}
                        />
                        <Route path="/users/avatar" element={React.createElement(requireAuth(AvataaarModification))} />

                        <Route path="/skus" element={<Products />} />

                        <Route path="/projects/" element={React.createElement(requireAuth(ProjectList))} />
                        <Route path="/projects/:project" element={React.createElement(requireAuth(ProjectDashboard))} />
                        <Route path={AppRoutes.project.members(":project")} element={React.createElement(requireAuth(ProjectMembers))} />
                        <Route path={"/projects/invite/:id"} element={React.createElement(requireAuth(ProjectAcceptInviteLink))} />

                        <Route path="/subprojects/:project" element={React.createElement(requireAuth(SubprojectList))} />

                        {/* Nullable paths args aren't supported (yet?) so we duplicate. */}
                        <Route path="/project/settings/:project/" element={React.createElement(requireAuth(ProjectSettings))} />
                        <Route path="/project/settings/:project/:page" element={React.createElement(requireAuth(ProjectSettings))} />
                        <Route path="/project/resources/:project" element={React.createElement(requireAuth(ProjectResources))} />
                        <Route path="/project/allocations/:project" element={React.createElement(requireAuth(ProjectAllocations))} />
                        <Route
                            path="/project/grants/existing"
                            element={React.createElement(requireAuth(GrantApplicationEditor), {key: RequestTarget.EXISTING_PROJECT, target: RequestTarget.EXISTING_PROJECT})}
                        />
                        <Route
                            path="/project/grants/personal"
                            element={React.createElement(requireAuth(GrantApplicationEditor), {key: RequestTarget.PERSONAL_PROJECT, target: RequestTarget.PERSONAL_PROJECT})}
                        />
                        <Route
                            path="/project/grants/new"
                            element={React.createElement(requireAuth(GrantApplicationEditor), {key: RequestTarget.NEW_PROJECT, target: RequestTarget.NEW_PROJECT})}
                        />
                        <Route
                            path="/project/grants/view/:appId"
                            element={React.createElement(requireAuth(GrantApplicationEditor), {key: RequestTarget.VIEW_APPLICATION, target: RequestTarget.VIEW_APPLICATION})}
                        />
                        <Route path="/project/grants/ingoing/:project" element={React.createElement(requireAuth(IngoingApplications))} />
                        <Route path="/project/grants/outgoing/:project" element={React.createElement(requireAuth(OutgoingApplications))} />
                        <Route
                            path="/sla"
                            element={React.createElement(requireAuth(ServiceLicenseAgreement, {
                                requireTwoFactor: false,
                                requireSla: false
                            }))}
                        />
                        <Route path="*" element={<NotFound />} />
                    </Routes>
                </React.Suspense>
            </ErrorBoundary>
        </div>

        <TerminalContainer />
    </>
);

interface RequireAuthOpts {
    requireTwoFactor?: boolean;
    requireSla?: boolean;
}

function requireAuth<T>(Delegate: React.FunctionComponent<T>, opts?: RequireAuthOpts): React.FunctionComponent<T> {
    return function Auth(props: React.PropsWithChildren<T>) {
        const info = Client.userInfo;

        if (!Client.isLoggedIn || info === undefined) {
            const loginPath = window.location.href.replace(`${window.location.origin}/app`, "");
            if (loginPath) {
                sessionStorage.setItem(LOGIN_REDIRECT_KEY, loginPath);
            }
            return <Navigate to="/login" />;
        }

        if (opts === undefined || opts.requireSla !== false) {
            if (info.serviceLicenseAgreement === false) {
                return <Navigate to="/sla" />;
            }
        }

        if (opts === undefined || opts.requireTwoFactor) {
            if (info.principalType === "password" && Client.userRole === "USER" &&
                info.twoFactorAuthentication === false) {
                return <Navigate to={AppRoutes.users.settings()} />;
            }
        }

        return <Delegate {...props} />;
    };
}

const LoginSuccess = (): JSX.Element => {
    React.useEffect(() => {
        dispatchUserAction(USER_LOGIN);
        onLogin();
    }, []);

    const path = sessionStorage.getItem(LOGIN_REDIRECT_KEY) ?? "/";
    return <Navigate to={path} />;
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
    if (window.location.pathname === "/" && inDevEnvironment()) window.location.href = "/app";
    return (
        <Provider store={store}>
            <MainApp>
                <Core />
            </MainApp>
        </Provider>
    );
}
