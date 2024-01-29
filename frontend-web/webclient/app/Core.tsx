import * as React from "react";
import {Navigate, Route, Routes} from "react-router-dom";
import {Dispatch} from "redux";
import {Provider, useDispatch} from "react-redux";
import {BrowserRouter} from "react-router-dom";

const App = React.lazy(() => import("@/Applications/Studio/Applications"));
const ApplicationsOverview = React.lazy(() => import("./Applications/Overview"));
const ApplicationsLanding = React.lazy(() => import("./Applications/Landing2"));
const ApplicationsGroup = React.lazy(() => import("@/Applications/Group"));
const ApplicationSearch = React.lazy(() => import("@/Applications/Search"));
const AvataaarModification = React.lazy(() => import("@/UserSettings/Avataaar"));
const Dashboard = React.lazy(() => import("@/Dashboard/Dashboard"));
const DetailedNews = React.lazy(() => import("@/NewsPost/DetailedNews"));
const ProviderRouter = React.lazy(() => import("@/Admin/Providers/Router"));
const SharesAcceptLink = React.lazy(() => import("@/Files/SharesAcceptLink"));
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
const ProjectMembers = React.lazy(() => import("@/Project/Members"));
const ProjectAcceptInviteLink = React.lazy(() => import("@/Project/AcceptInviteLink"));
const ServiceLicenseAgreement = React.lazy(() => import("@/ServiceLicenseAgreement"));
const StudioGroup = React.lazy(() => import("@/Applications/Studio/Group"));
const StudioGroups = React.lazy(() => import("@/Applications/Studio/Groups"));
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
const NetworkIPsRouter = React.lazy(() => import("@/Applications/NetworkIP/Router"));
const SyncthingOverview = React.lazy(() => import("@/Syncthing/Overview"));
const SshKeyCreate = React.lazy(() => import("@/Applications/SshKeys/Create"));
const GrantEditor = React.lazy(() => import("@/Grants/Editor"));
const ResourceUsage = React.lazy(() => import("@/Accounting/Usage"));
const ResourceAllocations = React.lazy(() => import("@/Accounting/Allocations"));

import {Sidebar} from "@/ui-components/Sidebar";
import Uploader from "@/Files/Uploader";
import Snackbars from "@/Snackbar";
import {Dialog} from "@/Dialog/Dialog";
import {inDevEnvironment} from "@/UtilityFunctions";
import {ErrorBoundary} from "@/ErrorBoundary/ErrorBoundary";
import {MainContainer} from "@/ui-components/MainContainer";
import {Client} from "@/Authentication/HttpClientInstance";
import {Flex, UIGlobalStyle} from "@/ui-components";
import {findAvatar} from "@/UserSettings/Redux";
import {CONTEXT_SWITCH, USER_LOGIN, USER_LOGOUT, store} from "@/Utilities/ReduxUtilities";
import {removeExpiredFileUploads} from "@/UtilityFunctions";
import {injectFonts} from "@/ui-components/GlobalStyle";
import {OutgoingSharesBrowse} from "@/Files/SharesOutgoing";
import {TerminalContainer} from "@/Terminal/Container";
import {LOGIN_REDIRECT_KEY} from "@/Login/Login";
import AppRoutes from "./Routes";
import {RightPopIn} from "./ui-components/PopIn";
import {injectStyle, injectStyleSimple} from "./Unstyled";
import {SSHKeyBrowse} from "./Applications/SshKeys/SSHKeyBrowse";
import {GrantApplicationBrowse} from "./Grants/GrantApplicationBrowse";
import {IngoingSharesBrowse} from "@/Files/Shares";
import {JobsRouter} from "./Applications/Jobs/Router";
import {DrivesRouter, FilesRouter} from "./Files/Router";
import LicenseRouter from "./Applications/Licenses";
import PublicLinksRouter from "./Applications/PublicLinks/Router";
import SharesApi from "./UCloud/SharesApi";
import {findCustomThemeColorOnLaunch} from "./UserSettings/CustomTheme";

const NotFound = (): React.JSX.Element => (<MainContainer main={<div><h1>Not found.</h1></div>} />);

const Core = (): React.JSX.Element => (
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
                        <Route path={"/drives/*"} element={React.createElement(requireAuth(DrivesRouter))} />
                        <Route path="/files/*" element={React.createElement(requireAuth(FilesRouter))} />

                        <Route path={AppRoutes.users.registration()} element={<Registration />} />
                        <Route path={AppRoutes.users.verifyEmail()} element={<VerifyEmail />} />
                        <Route path={AppRoutes.users.verifyResult()} element={<VerifyResult />} />
                        <Route path="/shares/" element={React.createElement(requireAuth(IngoingSharesBrowse))} />
                        <Route path={"/shares/properties/:id/"} element={<SharesApi.Properties api={SharesApi} />} />
                        <Route path="/shares/outgoing" element={React.createElement(requireAuth(OutgoingSharesBrowse))} />
                        <Route path={"/shares/invite/:id"}
                            element={React.createElement(requireAuth(SharesAcceptLink))} />
                        <Route path={AppRoutes.syncthing.syncthing()}
                            element={React.createElement(requireAuth(SyncthingOverview))} />

                        <Route path={AppRoutes.apps.landing()}
                            element={React.createElement(requireAuth(ApplicationsLanding))} />
                        <Route path={AppRoutes.apps.group(":id")}
                            element={React.createElement(requireAuth(ApplicationsGroup))} />
                        <Route path={AppRoutes.apps.category()}
                               element={React.createElement(requireAuth(ApplicationsOverview))} />
                        <Route path={AppRoutes.apps.search()} element={React.createElement(requireAuth(ApplicationSearch))} />

                        <Route path="/jobs/*" element={React.createElement(requireAuth(JobsRouter))} />


                        <Route path={AppRoutes.apps.shell(":jobId", ":rank")}
                            element={React.createElement(requireAuth(JobShell))} />
                        <Route path={AppRoutes.apps.web(":jobId", ":rank")}
                            element={React.createElement(requireAuth(JobWeb))} />
                        <Route path={AppRoutes.apps.vnc(":jobId", ":rank")}
                            element={React.createElement(requireAuth(JobVnc))} />

                        <Route path={"/public-links/*"} element={React.createElement(requireAuth(PublicLinksRouter))} />
                        <Route path="/licenses/*" element={React.createElement(requireAuth(LicenseRouter))} />
                        <Route path="/public-ips/*" element={React.createElement(requireAuth(NetworkIPsRouter))} />

                        <Route path={"/ssh-keys"} element={React.createElement(requireAuth(SSHKeyBrowse))} />
                        <Route path={"/ssh-keys/create"} element={React.createElement(requireAuth(SshKeyCreate))} />

                        <Route path={AppRoutes.apps.studioGroups()} element={React.createElement(requireAuth(StudioGroups))} />
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

                        {/* Nullable paths args aren't supported (yet?) so we duplicate. */}
                        <Route path={AppRoutes.project.settings("")}
                            element={React.createElement(requireAuth(ProjectSettings))} />
                        <Route path={AppRoutes.project.settings(":page")}
                            element={React.createElement(requireAuth(ProjectSettings))} />

                        <Route path={AppRoutes.grants.editor()} element={React.createElement(requireAuth(GrantEditor))} />
                        <Route path={AppRoutes.grants.ingoing()}
                            element={React.createElement(requireAuth(GrantApplicationBrowse))} />
                        <Route path={AppRoutes.grants.outgoing()}
                            element={React.createElement(requireAuth(GrantApplicationBrowse))} />

                        <Route path={AppRoutes.accounting.usage()}
                            element={React.createElement(requireAuth(ResourceUsage))} />
                        <Route path={AppRoutes.accounting.allocations()}
                            element={React.createElement(requireAuth(ResourceAllocations))} />

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

function LoginSuccess(): React.JSX.Element {
    const dispatch = useDispatch();
    React.useEffect(() => {
        dispatchUserAction(dispatch, USER_LOGIN);
        onLogin(dispatch);
    }, []);

    const path = sessionStorage.getItem(LOGIN_REDIRECT_KEY) ?? "/";
    return <Navigate to={path} />;
}

function dispatchUserAction(dispatch: Dispatch, type: typeof USER_LOGIN | typeof USER_LOGOUT | typeof CONTEXT_SWITCH): void {
    dispatch({type});
}

async function onLogin(dispatch: Dispatch): Promise<void> {
    const action = await findAvatar();
    if (action !== null) dispatch(action);
}

injectStyle("ignored", () => `
    ${UIGlobalStyle}
`);

Client.initializeStore(store);
removeExpiredFileUploads();
findCustomThemeColorOnLaunch();

function MainApp({children}: {children?: React.ReactNode}): React.JSX.Element {
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

export default function UCloudApp(): React.ReactNode {
    if (window.location.pathname === "/" && inDevEnvironment()) window.location.href = "/app";
    return (
        <Provider store={store}>
            <MainApp>
                <Core />
            </MainApp>
        </Provider>
    );
}
