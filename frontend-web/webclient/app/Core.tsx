import * as React from "react";

const App = React.lazy(() => import("@/Applications/Studio/Applications"));
const Applications = React.lazy(() => import("@/Applications/Browse"));
const ApplicationsOverview = React.lazy(() => import("./Applications/Overview"));
const AvataaarModification = React.lazy(() => import("@/UserSettings/Avataaar"));
const Dashboard = React.lazy(() => import("@/Dashboard/Dashboard"));
const DetailedNews = React.lazy(() => import("@/NewsPost/DetailedNews"));
const ProviderRouter = React.lazy(() => import("@/Admin/Providers/Router"));
const MetadataNamespacesRouter = React.lazy(() => import("@/Files/Metadata/Templates/Namespaces"));
const SharesAcceptLink = React.lazy(() => import("@/Files/SharesAcceptLink"));
const ShareRouter = React.lazy(() => import("@/Files/Shares"));
const JobShell = React.lazy(() => import("@/Applications/Jobs/Shell"));
const JobWeb = React.lazy(() => import("@/Applications/Jobs/Web"));
const JobVnc = React.lazy(() => import("@/Applications/Jobs/Vnc"));
const LoginPage = React.lazy(() => import("@/Login/Login"));
const Registration = React.lazy(() => import("@/Login/Registration"));
const VerifyEmail = React.lazy(() => import("@/Login/VerifyEmail"));
const VerifyResult = React.lazy(() => import("@/Login/VerifyResult"));
const NewsList = React.lazy(() => import("@/NewsPost/NewsList"));
const NewsManagement = React.lazy(() => import("@/Admin/NewsManagement"));
const Playground = React.lazy(() => import("@/Playground/Playground"));
const Products = React.lazy(() => import("@/Products/Products"));
const ProjectSettings = React.lazy(() => import("@/Project/ProjectSettings"));
const ProjectResources = React.lazy(() => import("@/Project/Resources"));
const ProjectAllocations = React.lazy(() => import("@/Project/Allocations"));
const ProjectMembers = React.lazy(() => import("@/Project/Members2"));
const ProjectAcceptInviteLink = React.lazy(() => import("@/Project/AcceptInviteLink"));
const Search = React.lazy(() => import("@/Search/Search"));
const ServiceLicenseAgreement = React.lazy(() => import("@/ServiceLicenseAgreement"));
const Studio = React.lazy(() => import("@/Applications/Studio/Page"));
const StudioGroup = React.lazy(() => import("@/Applications/Studio/Group"));
const StudioGroups = React.lazy(() => import("@/Applications/Studio/Groups"));
const Tool = React.lazy(() => import("@/Applications/Studio/Tool"));
const Scripts = React.lazy(() => import("@/Admin/Scripts"));
const UserCreation = React.lazy(() => import("@/Admin/UserCreation"));
const DevTestData = React.lazy(() => import("@/Admin/DevTestData"));
const UserSettings = React.lazy(() => import("@/UserSettings/UserSettings"));
const Wayf = React.lazy(() => import("@/Login/Wayf"));
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
const SshKeyCreate = React.lazy(() => import("@/Applications/SshKeys/Create"));
const ExperimentalFileBrowse = React.lazy(() => import("@/Files/ExperimentalBrowse"));
const ExperimentalDriveBrowse = React.lazy(() => import("@/Files/ExperimentalDriveBrowse"));

import {GrantApplicationEditor, RequestTarget} from "@/Project/Grant/GrantApplicationEditor";
import {Sidebar} from "@/ui-components/Sidebar";
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
import {CONTEXT_SWITCH, USER_LOGOUT} from "@/Navigation/Redux/HeaderReducer";
import {Provider} from "react-redux";
import {BrowserRouter} from "react-router-dom";
import {Flex, UIGlobalStyle} from "@/ui-components";
import {findAvatar} from "@/UserSettings/Redux/AvataaarActions";
import {store} from "@/Utilities/ReduxUtilities";
import {isLightThemeStored, removeExpiredFileUploads, setSiteTheme, toggleCssColors} from "@/UtilityFunctions";
import {injectFonts} from "@/ui-components/GlobalStyle";
import {SharesOutgoing} from "@/Files/SharesOutgoing";
import {TerminalContainer} from "@/Terminal/Container";
import {LOGIN_REDIRECT_KEY} from "@/Login/Login";
import AppRoutes from "./Routes";
import {RightPopIn} from "./ui-components/PopIn";
import {injectStyle, injectStyleSimple} from "./Unstyled";
import FilesApi from "@/UCloud/FilesApi";
import FileCollectionsApi from "@/UCloud/FileCollectionsApi";

import ExperimentalJobs from "./Applications/Jobs/ExperimentalJobs";
import {ExperimentalNetworkIP} from "./Applications/NetworkIP/ExperimentalBrowse";
import {ExperimentalSSHKey} from "./Applications/SshKeys/ExperimentalBrowse";
import {ExperimentalLicenses} from "./Applications/ExperimentalLicenses";
import {ExperimentalPublicLinks} from "./Applications/Ingresses/ExperimentalBrowse";
import {ExperimentalGrantApplications} from "./Project/Grant/ExperimentalGrantApplications";

const NotFound = (): JSX.Element => (<MainContainer main={<div><h1>Not found.</h1></div>} />);

const Core = (): JSX.Element => (
    <>
        <Dialog />
        <Snackbars />
        <Uploader />
        <RightPopIn />
        <div data-component="router-wrapper" className={RouteWrapperClass}>
            <ErrorBoundary>
                <React.Suspense fallback={<MainContainer main={<div>Loading...</div>} />}>
                    <Routes>
                        <Route path={AppRoutes.login.login()} element={<LoginPage />} />
                        <Route path={AppRoutes.login.loginSuccess()} element={<LoginSuccess />} />
                        <Route path={AppRoutes.login.loginWayf()} element={<Wayf />} />
                        <Route path={AppRoutes.dashboard.dashboardA()}
                            element={React.createElement(requireAuth(Dashboard))} />
                        <Route path={AppRoutes.dashboard.dashboardB()}
                            element={React.createElement(requireAuth(Dashboard))} />
                        {/* Kind of a hardcoded solution, removing the generalised solution fro ResourceBrowse */}
                        <Route path={"/drives/properties/:id/"} element={React.createElement(requireAuth(FileCollectionsApi.Properties), {api: FileCollectionsApi})} />
                        <Route path={"/drives/"} element={React.createElement(requireAuth(ExperimentalDriveBrowse))} />
                        {/* Kind of a hardcoded solution, removing the generalised solution fro ResourceBrowse */}
                        <Route path={`/files/properties/:id/`} element={React.createElement(requireAuth(FilesApi.Properties), {api: FilesApi})} />
                        {/* Hardcoded solution end */}
                        <Route path={"/files/*"} element={React.createElement(requireAuth(ExperimentalFileBrowse))} />
                        <Route path="/registration" element={<Registration />}  />
                        <Route path="/verifyEmail" element={<VerifyEmail />}  />
                        <Route path="/verifyResult" element={<VerifyResult />}  />
                        <Route path={"/metadata/*"} element={React.createElement(requireAuth(MetadataNamespacesRouter))} />
                        <Route path={"/shares/outgoing"} element={React.createElement(requireAuth(SharesOutgoing))} />
                        <Route path={"/shares/invite/:id"}
                            element={React.createElement(requireAuth(SharesAcceptLink))} />
                        <Route path={"/shares/*"} element={React.createElement(requireAuth(ShareRouter))} />

                        <Route path={AppRoutes.syncthing.syncthing()}
                            element={React.createElement(requireAuth(SyncthingOverview))} />

                        <Route path={AppRoutes.apps.applications()}
                            element={React.createElement(requireAuth(Applications))} />
                        <Route path={AppRoutes.apps.overview()}
                            element={React.createElement(requireAuth(ApplicationsOverview))} />
                        <Route path={AppRoutes.apps.landing()}
                            element={React.createElement(requireAuth(ApplicationsOverview))} />
                        <Route path={AppRoutes.apps.search()} element={React.createElement(requireAuth(Search))} />

                        {!inDevEnvironment() ? null :
                            <Route path="/MANUAL-TESTING-OVERVIEW" element={<ManualTestingOverview />} />
                        }

                        <Route path={AppRoutes.apps.shell(":jobId", ":rank")}
                            element={React.createElement(requireAuth(JobShell))} />
                        <Route path={AppRoutes.apps.web(":jobId", ":rank")}
                            element={React.createElement(requireAuth(JobWeb))} />
                        <Route path={AppRoutes.apps.vnc(":jobId", ":rank")}
                            element={React.createElement(requireAuth(JobVnc))} />

                        <Route path={AppRoutes.resources.publicLinks()} element={React.createElement(requireAuth(ExperimentalPublicLinks))} />
                        <Route path="/public-links/*" element={React.createElement(requireAuth(IngressRouter))} />
                        <Route path={AppRoutes.jobs.list()} element={React.createElement(requireAuth(ExperimentalJobs))} />
                        <Route path="/jobs/*" element={React.createElement(requireAuth(JobRouter))} />

                        <Route path="/licenses/" element={React.createElement(requireAuth(ExperimentalLicenses))} />
                        <Route path="/licenses/*" element={React.createElement(requireAuth(LicenseRouter))} />

                        <Route path="/public-ips/" element={React.createElement(ExperimentalNetworkIP)} />
                        <Route path="/public-ips/*" element={React.createElement(requireAuth(NetworkIPsRouter))} />

                        <Route path={"/ssh-keys"} element={React.createElement(requireAuth(ExperimentalSSHKey))} />
                        <Route path={"/ssh-keys/create"} element={React.createElement(requireAuth(SshKeyCreate))} />

                        <Route path={AppRoutes.apps.studio()} element={React.createElement(requireAuth(Studio))} />
                        <Route path={AppRoutes.apps.studioGroups()} element={React.createElement(requireAuth(StudioGroups))} />
                        <Route path={AppRoutes.apps.studioTool(":name")}
                            element={React.createElement(requireAuth(Tool))} />
                        <Route path={AppRoutes.apps.studioApp(":name")}
                            element={React.createElement(requireAuth(App))} />
                        <Route path={AppRoutes.apps.studioGroup(":id")}
                            element={React.createElement(requireAuth(StudioGroup))} />

                        {!inDevEnvironment() ? null : <Route path={"/playground"} element={<Playground />} />}
                        {!inDevEnvironment() ? null : <Route path={"/playground/demo"} element={<Demo />} />}
                        {!inDevEnvironment() ? null : <Route path={"/playground/lag"} element={<LagTest />} />}

                        <Route path={AppRoutes.admin.userCreation()}
                            element={React.createElement(requireAuth(UserCreation))} />
                        <Route path={AppRoutes.admin.news()}
                            element={React.createElement(requireAuth(NewsManagement))} />
                        <Route path={AppRoutes.admin.scripts()} element={React.createElement(requireAuth(Scripts))} />

                        <Route path="/admin/providers" element={React.createElement(requireAuth(Providers))} />
                        <Route path="/admin/providers/create"
                            element={React.createElement(requireAuth(CreateProvider))} />
                        <Route path="/admin/providers/edit/:id"
                            element={React.createElement(requireAuth(EditProvider))} />
                        <Route path="/admin/providers/register"
                            element={React.createElement(requireAuth(RegisterProvider))} />

                        <Route path="/providers/connect"
                            element={React.createElement(requireAuth(ProviderConnection))} />
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

                        <Route path={AppRoutes.project.members()}
                            element={React.createElement(requireAuth(ProjectMembers))} />
                        <Route path={"/projects/invite/:id"}
                            element={React.createElement(requireAuth(ProjectAcceptInviteLink))} />

                        <Route path="/subprojects/" element={React.createElement(requireAuth(SubprojectList))} />

                        {/* Nullable paths args aren't supported (yet?) so we duplicate. */}
                        <Route path={AppRoutes.project.settings("")}
                            element={React.createElement(requireAuth(ProjectSettings))} />
                        <Route path={AppRoutes.project.settings(":page")}
                            element={React.createElement(requireAuth(ProjectSettings))} />
                        <Route path={AppRoutes.project.usage()}
                            element={React.createElement(requireAuth(ProjectResources))} />
                        <Route path={AppRoutes.project.allocations()}
                            element={React.createElement(requireAuth(ProjectAllocations))} />
                        <Route
                            path="/project/grants/existing"
                            element={React.createElement(requireAuth(GrantApplicationEditor), {
                                key: RequestTarget.EXISTING_PROJECT,
                                target: RequestTarget.EXISTING_PROJECT
                            })}
                        />
                        <Route
                            path="/project/grants/personal"
                            element={React.createElement(requireAuth(GrantApplicationEditor), {
                                key: RequestTarget.PERSONAL_PROJECT,
                                target: RequestTarget.PERSONAL_PROJECT
                            })}
                        />
                        <Route
                            path="/project/grants/new"
                            element={React.createElement(requireAuth(GrantApplicationEditor), {
                                key: RequestTarget.NEW_PROJECT,
                                target: RequestTarget.NEW_PROJECT
                            })}
                        />
                        <Route
                            path="/project/grants/view/:appId"
                            element={React.createElement(requireAuth(GrantApplicationEditor), {
                                key: RequestTarget.VIEW_APPLICATION,
                                target: RequestTarget.VIEW_APPLICATION
                            })}
                        />
                        <Route path={AppRoutes.project.grantsIngoing()}
                            element={React.createElement(requireAuth(ExperimentalGrantApplications))} />
                        <Route path={AppRoutes.project.grantsOutgoing()}
                            element={React.createElement(requireAuth(ExperimentalGrantApplications))} />
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

const RouteWrapperClass = injectStyleSimple("route-wrapper", `
    width: 100%;
    height: calc(100vh - var(--termsize, 0px));
    overflow-x: auto;
    overflow-y: auto;
`);

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

const _ignored = injectStyle("ignored", () => `
    ${UIGlobalStyle}
`);

Client.initializeStore(store);
removeExpiredFileUploads();

const isLight = isLightThemeStored();
toggleCssColors(isLight);

export function toggleTheme(): void {
    const isLight = isLightThemeStored();
    toggleCssColors(!isLight);
    setSiteTheme(!isLight);
    for (const listener of Object.values(themeListeners)) {
        listener();
    }
}

const themeListeners: Record<string, () => void> = {};
export function addThemeListener(key: string, op: () => void) {
    themeListeners[key] = op;
}

export function removeThemeListener(key: string) {
    delete themeListeners[key];
}

function MainApp({children}: {children?: React.ReactNode}): JSX.Element {
    return (
        <BrowserRouter basename="app">
            <Flex>
                <Sidebar />
                {children}
            </Flex>
        </BrowserRouter>
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
