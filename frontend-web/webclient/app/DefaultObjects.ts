import * as AccountingRedux from "Accounting/Redux";
import {ActivityFilter, ActivityForFrontend} from "Activity";
import {Analysis, DetailedApplicationSearchReduxState, RunsSortBy} from "Applications";
import * as ApplicationRedux from "Applications/Redux";
import {TaskReduxState} from "BackgroundTasks/redux";
import {DashboardStateProps} from "Dashboard";
import {DetailedFileSearchReduxState, SortOrder} from "Files";
import {Notification} from "Notifications";
import * as ProjectRedux from "Project/Redux";
import {Reducer} from "redux";
import {ScrollResult} from "Scroll/Types";
import {SimpleSearchStateProps} from "Search";
import {Page, SidebarOption} from "Types";
import {SidebarPages} from "ui-components/Sidebar";
import {Upload} from "Uploader";
import {defaultAvatar} from "UserSettings/Avataaar";

export enum KeyCode {
    ENTER = 13,
    ESC = 27
}

export const emptyPage: Readonly<Page<any>> =
    {items: [], itemsPerPage: 25, itemsInTotal: 0, pageNumber: 0, pagesInTotal: 0};

export enum SensitivityLevel {
    "INHERIT" = "Inherit",
    "PRIVATE" = "Private",
    "CONFIDENTIAL" = "Confidential",
    "SENSITIVE" = "Sensitive"
}

export type Sensitivity = keyof typeof SensitivityLevel;

export enum SensitivityLevelMap {
    INHERIT = "INHERIT",
    PRIVATE = "PRIVATE",
    CONFIDENTIAL = "CONFIDENTIAL",
    SENSITIVE = "SENSITIVE"
}

export interface ComponentWithLoadingState {
    loading: boolean;
    error?: string;
}

export interface ComponentWithPage<T> extends ComponentWithLoadingState {
    page: Page<T>;
}

export interface ComponentWithScroll<Item, OffsetType> extends ComponentWithLoadingState {
    scroll?: ScrollResult<Item, OffsetType>;
}

export interface ResponsiveReduxObject {
    mediaType: string;
    orientation: string;
    lessThan: Record<string, boolean>;
    greaterThan: Record<string, boolean>;
    is: Record<string, boolean>;
}

export interface FileInfoReduxObject {
    error?: string;
    activity: Page<ActivityForFrontend>;
    loading: boolean;
}

export interface AnalysisReduxObject extends ComponentWithPage<Analysis> {
    sortBy: RunsSortBy;
    sortOrder: SortOrder;
}

export interface NotificationsReduxObject {
    redirectTo: string;
    items: Notification[];
    loading: boolean;
    error?: string;
}

export interface StatusReduxObject {
    title: string;
    page: SidebarPages;
    loading: boolean;
}

export interface SidebarReduxObject {
    pp: boolean;
    options: SidebarOption[];
    kcCount: number;
}

export interface HeaderSearchReduxObject {
    prioritizedSearch: HeaderSearchType;
    refresh?: () => void;
}

export type ActivityReduxObject = ComponentWithScroll<ActivityForFrontend, number> & ActivityFilter;

export type HeaderSearchType = "files" | "applications" | "projects";

export interface UploaderReduxObject {
    uploads: Upload[];
    visible: boolean;
    path: string;
    allowMultiple: boolean;
    onFilesUploaded: () => void;
    error?: string;
    loading: boolean;
}

interface LegacyReducers {
    dashboard?: Reducer<DashboardStateProps>;
    uploader?: Reducer<UploaderReduxObject>;
    status?: Reducer<StatusReduxObject>;
    notifications?: Reducer<NotificationsReduxObject>;
    analyses?: Reducer<AnalysisReduxObject>;
    header?: Reducer<HeaderSearchReduxObject>;
    sidebar?: Reducer<SidebarReduxObject>;
    activity?: Reducer<ActivityReduxObject>;
}

/* FIXME */
interface LegacyReduxObject {
    dashboard: DashboardStateProps;
    uploader: UploaderReduxObject;
    status: StatusReduxObject;
    notifications: NotificationsReduxObject;
    analyses: AnalysisReduxObject;
    header: HeaderSearchReduxObject;
    sidebar: SidebarReduxObject;
    activity: ActivityReduxObject;
    simpleSearch: SimpleSearchStateProps;
    detailedFileSearch: DetailedFileSearchReduxState;
    detailedApplicationSearch: DetailedApplicationSearchReduxState;
    fileInfo: FileInfoReduxObject;
    avatar: AvatarReduxObject;
    responsive?: ResponsiveReduxObject;
    project: ProjectRedux.State;
    loading?: boolean;
}

export type ReduxObject =
    LegacyReduxObject &
    ApplicationRedux.Objects &
    AccountingRedux.Objects &
    TaskReduxState;

export const initActivity = (): ActivityReduxObject => ({
    loading: false
});

export const initNotifications = (): NotificationsReduxObject => ({
    items: [],
    loading: false,
    redirectTo: "",
    error: undefined
});

export const initHeader = (): HeaderSearchReduxObject => ({
    prioritizedSearch: "files"
});

export const initStatus = (): StatusReduxObject => ({
    title: "",
    page: SidebarPages.None,
    loading: false
});

export const initDashboard = (): DashboardStateProps => ({
    favoriteFiles: [],
    recentAnalyses: [],
    notifications: [],
    favoriteLoading: false,
    analysesLoading: false
});

export function initObject(): ReduxObject {
    return {
        dashboard: initDashboard(),
        status: initStatus(),
        header: initHeader(),
        notifications: initNotifications(),
        analyses: initAnalyses(),
        sidebar: initSidebar(),
        uploader: initUploads(),
        activity: initActivity(),
        simpleSearch: initSimpleSearch(),
        detailedApplicationSearch: initApplicationsAdvancedSearch(),
        detailedFileSearch: initFilesDetailedSearch(),
        fileInfo: initFileInfo(),
        avatar: initAvatar(),
        project: ProjectRedux.initialState,
        ...ApplicationRedux.init(),
        ...AccountingRedux.init(),
        responsive: undefined,
    };
}

export type AvatarReduxObject = typeof defaultAvatar & {error?: string};
export const initAvatar = (): AvatarReduxObject => ({...defaultAvatar, error: undefined});

export const initSimpleSearch = (): SimpleSearchStateProps => ({
    files: emptyPage,
    filesLoading: false,
    applications: emptyPage,
    applicationsLoading: false,
    errors: [],
    search: "",
    applicationSearch: initApplicationsAdvancedSearch(),
    fileSearch: initFilesDetailedSearch()
});

export const initAnalyses = (): AnalysisReduxObject => ({
    page: emptyPage,
    loading: false,
    error: undefined,
    sortBy: RunsSortBy.createdAt,
    sortOrder: SortOrder.DESCENDING
});

export const initSidebar = (): SidebarReduxObject => ({
    pp: false,
    kcCount: 0,
    options: []
});

export const initUploads = (): UploaderReduxObject => ({
    path: "",
    uploads: [],
    visible: false,
    allowMultiple: false,
    error: undefined,
    onFilesUploaded: () => null,
    loading: false
});

export const initFileInfo = (): FileInfoReduxObject => ({
    activity: emptyPage,
    loading: false
});

export const initFilesDetailedSearch = (): DetailedFileSearchReduxState => ({
    hidden: true,
    allowFolders: true,
    allowFiles: true,
    extensions: new Set(),
    tags: new Set(),
    sensitivities: new Set(),
    modifiedBefore: undefined,
    modifiedAfter: undefined,
    includeShares: false,
    error: undefined,
    loading: false
});

export const initApplicationsAdvancedSearch = (): DetailedApplicationSearchReduxState => ({
    error: undefined,
    loading: false,
    hidden: true,
    appQuery: "",
    tags: new Set(),
    showAllVersions: false
});
