import * as React from "react";
import {Navigate, Route, Routes} from "react-router-dom";
import {Dispatch} from "redux";
import {Provider, useDispatch} from "react-redux";
import {BrowserRouter} from "react-router-dom";

import App from "@/Applications/Studio/Applications";
import ApplicationsOverview from "./Applications/Category";
import ApplicationsLanding from "./Applications/Landing";
import ApplicationsGroup from "@/Applications/Group";
import ApplicationSearch from "@/Applications/Search";
import AvataaarModification from "@/UserSettings/Avataaar";
import Dashboard from "@/Dashboard/Dashboard";
import DetailedNews from "@/NewsPost/DetailedNews";
import ProviderRouter from "@/Admin/Providers/Router";
import SharesAcceptLink from "@/Files/SharesAcceptLink";
import JobShell from "@/Applications/Jobs/Shell";
import JobVnc from "@/Applications/Jobs/Vnc";
import LoginPage from "@/Login/Login";
import Registration from "@/Login/Registration";
import VerifyEmail from "@/Login/VerifyEmail";
import VerifyResult from "@/Login/VerifyResult";
import NewsList from "@/NewsPost/NewsList";
import NewsManagement from "@/Admin/NewsManagement";
import Playground from "@/Playground/Playground";
import Products from "@/Products/Products";
import ProjectSettings from "@/Project/ProjectSettings";
import ProjectMembers from "@/Project/Members";
import ProjectAcceptInviteLink from "@/Project/AcceptInviteLink";
import ServiceLicenseAgreement from "@/ServiceLicenseAgreement";
import StudioGroup from "@/Applications/Studio/Group";
import StudioGroups from "@/Applications/Studio/Groups";
import StudioCategories from "@/Applications/Studio/Categories";
import StudioSpotlightsEditor from "@/Applications/Studio/SpotlightsEditor";
import StudioSpotlights from "@/Applications/Studio/Spotlights";
import StudioHero from "@/Applications/Studio/HeroEditor";
import StudioTopPicks from "@/Applications/Studio/TopPicksEditor";
import UserCreation from "@/Admin/UserCreation";
import UserSettings from "@/UserSettings/UserSettings";
import Wayf from "@/Login/Wayf";
import Demo from "@/Playground/Demo";
import LagTest from "@/Playground/LagTest";
import Providers from "@/Admin/Providers/Browse";
import CreateProvider from "@/Admin/Providers/Save";
import EditProvider from "@/Admin/Providers/Save";
import ProviderConnection from "@/Providers/Connect";
import ProviderOverview from "@/Providers/Overview";
import ProviderDetailed from "@/Providers/Detailed";
import NetworkIPsRouter from "@/Applications/NetworkIP/Router";
import SyncthingOverview from "@/Syncthing/Overview";
import SshKeyCreate from "@/Applications/SshKeys/Add";
import ApiTokenCreate from "@/Applications/ApiTokens/Add";
import GrantEditor from "@/Grants/Editor";
import ResourceUsage from "@/Accounting/UsageCore2";
import ResourceAllocations from "@/Accounting/Allocations";
import Connection from "@/Providers/Connection";

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
import {USER_LOGIN, UserActionType, store} from "@/Utilities/ReduxUtilities";
import {removeExpiredFileUploads} from "@/UtilityFunctions";
import {injectFonts} from "@/ui-components/GlobalStyle";
import {OutgoingSharesBrowse} from "@/Files/SharesOutgoing";
import {TerminalContainer} from "@/Terminal/Container";
import {LOGIN_REDIRECT_KEY} from "@/Login/Login";
import AppRoutes from "./Routes";
import {RightPopIn} from "./ui-components/PopIn";
import {injectStyleSimple} from "./Unstyled";
import {SSHKeyBrowse} from "./Applications/SshKeys/SSHKeyBrowse";
import {ApiTokenBrowse} from "./Applications/ApiTokens/ApiTokensBrowse";
import {GrantApplicationBrowse} from "./Grants/GrantApplicationBrowse";
import {IngoingSharesBrowse} from "@/Files/Shares";
import {JobsRouter} from "@/Applications/Jobs/Router";
import {DrivesRouter, FilesRouter} from "@/Files/Router";
import LicenseRouter from "./Applications/Licenses";
import PublicLinksRouter from "@/Applications/PublicLinks/Router";
import SharesApi from "@/UCloud/SharesApi";
import {findCustomThemeColorOnLaunch} from "./UserSettings/CustomTheme";
import SupportPage, {
    AllocationSupportContent,
    JobSupportContent,
    ProjectSupportContent,
    UserSupportContent
} from "./Admin/SupportPage";
import {useEffect} from "react";
import {deinitNotifications, initTaskAndNotificationStream} from "@/Services/TaskAndNotificationStream";

const NotFound = (): React.ReactNode => (<MainContainer main={<div><h1>Not found.</h1></div>} />);

const Core = (): React.ReactNode => (
    <>
        <Dialog />
        <Snackbars />
        <Uploader />
        <RightPopIn />
        <div data-component="router-wrapper" className={RouteWrapperClass}>
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
                    <Route path={AppRoutes.apps.vnc(":jobId", ":rank")}
                        element={React.createElement(requireAuth(JobVnc))} />

                    <Route path={"/public-links/*"} element={React.createElement(requireAuth(PublicLinksRouter))} />
                    <Route path="/licenses/*" element={React.createElement(requireAuth(LicenseRouter))} />
                    <Route path="/public-ips/*" element={React.createElement(requireAuth(NetworkIPsRouter))} />

                    <Route path={AppRoutes.resources.sshKeys()} element={React.createElement(requireAuth(SSHKeyBrowse))} />
                    <Route path={AppRoutes.resources.sshKeysCreate()} element={React.createElement(requireAuth(SshKeyCreate))} />

                    <Route path={AppRoutes.resources.apiTokens()} element={React.createElement(requireAuth(ApiTokenBrowse))} />
                    <Route path={AppRoutes.resources.apiTokensCreate()} element={React.createElement(requireAuth(ApiTokenCreate))} />

                    <Route path={AppRoutes.appStudio.topPicks()} element={React.createElement(requireAuth(StudioTopPicks))} />
                    <Route path={AppRoutes.appStudio.hero()} element={React.createElement(requireAuth(StudioHero))} />
                    <Route path={AppRoutes.appStudio.spotlights()} element={React.createElement(requireAuth(StudioSpotlights))} />
                    <Route path={AppRoutes.appStudio.spotlightsEditor()} element={React.createElement(requireAuth(StudioSpotlightsEditor))} />
                    <Route path={AppRoutes.appStudio.categories()} element={React.createElement(requireAuth(StudioCategories))} />
                    <Route path={AppRoutes.appStudio.groups()} element={React.createElement(requireAuth(StudioGroups))} />
                    <Route path={AppRoutes.appStudio.app(":name")}
                        element={React.createElement(requireAuth(App))} />
                    <Route path={AppRoutes.appStudio.group(":id")}
                        element={React.createElement(requireAuth(StudioGroup))} />

                    {!inDevEnvironment() ? null : <Route path={"/playground"} element={<Playground />} />}
                    {!inDevEnvironment() ? null : <Route path={"/playground/demo"} element={<Demo />} />}
                    {!inDevEnvironment() ? null : <Route path={"/playground/lag"} element={<LagTest />} />}

                    <Route path={AppRoutes.admin.userCreation()}
                        element={React.createElement(requireAuth(UserCreation))} />
                    <Route path={AppRoutes.admin.news()}
                        element={React.createElement(requireAuth(NewsManagement))} />

                    <Route path={AppRoutes.supportAssist.base()} element={React.createElement(requireAuth(SupportPage))} />
                    <Route path={AppRoutes.supportAssist.user()} element={React.createElement(requireAuth(UserSupportContent))} />
                    <Route path={AppRoutes.supportAssist.project()} element={React.createElement(requireAuth(ProjectSupportContent))} />
                    <Route path={AppRoutes.supportAssist.job()} element={React.createElement(requireAuth(JobSupportContent))} />
                    <Route path={AppRoutes.supportAssist.allocation()} element={React.createElement(requireAuth(AllocationSupportContent))} />


                    <Route path="/admin/providers" element={React.createElement(requireAuth(Providers))} />
                    <Route path="/admin/providers/create"
                        element={React.createElement(requireAuth(CreateProvider))} />
                    <Route path="/admin/providers/edit/:id"
                        element={React.createElement(requireAuth(EditProvider))} />

                    <Route path={"/connection"} element={React.createElement(requireAuth(Connection))} />
                    <Route path="/providers/connect"
                        element={React.createElement(requireAuth(ProviderConnection))} />
                    <Route path="/providers/create" element={React.createElement(requireAuth(CreateProvider))} />
                    <Route path="/providers/edit/:id" element={React.createElement(requireAuth(EditProvider))} />
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

function LoginSuccess(): React.ReactNode {
    const dispatch = useDispatch();
    React.useEffect(() => {
        dispatchUserAction(dispatch, USER_LOGIN);
        onLogin(dispatch);
    }, []);

    const path = sessionStorage.getItem(LOGIN_REDIRECT_KEY) ?? "/";
    sessionStorage.removeItem(LOGIN_REDIRECT_KEY);
    return <Navigate to={path} />;
}

function dispatchUserAction(dispatch: Dispatch, type: UserActionType): void {
    dispatch({type});
}

async function onLogin(dispatch: Dispatch): Promise<void> {
    const action = await findAvatar();
    if (action !== null) dispatch(action);
}

(() => {
    const globalStyle = document.createElement("style");
    globalStyle.innerHTML = UIGlobalStyle;
    document.head.append(globalStyle);
})();

Client.initializeStore(store);
removeExpiredFileUploads();
findCustomThemeColorOnLaunch();

function MainApp({children}: React.PropsWithChildren): React.ReactNode {
    useEffect(() => {
        initTaskAndNotificationStream();
        return () => {
            deinitNotifications();
        }
    }, []);

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
        <ErrorBoundary>
            <Provider store={store}>
                <MainApp>
                    <Core />
                </MainApp>
            </Provider>
        </ErrorBoundary>
    );
}
