import {ActivityFilter, ActivityForFrontend} from "Activity";
import {TaskReduxState} from "BackgroundTasks/redux";
import {DashboardStateProps} from "Dashboard";
import {DetailedFileSearchReduxState} from "Files";
import {Notification} from "Notifications";
import * as ProjectRedux from "Project/Redux";
import {Reducer} from "redux";
import {ScrollResult} from "Scroll/Types";
import {SimpleSearchStateProps} from "Search";
import {SidebarOption} from "Types";
import {SidebarPages} from "ui-components/Sidebar";
import {Upload} from "Uploader";
import {defaultAvatar} from "UserSettings/Avataaar";
import {ProjectCache} from "Project/cache";
import {APICallStateWithParams} from "Authentication/DataHook";
import {
    ListGroupMembersRequestProps,
    ListOutgoingInvitesRequest,
    OutgoingInvite,
    ProjectMember,
    UserInProject
} from "Project";
import {GroupWithSummary} from "Project/GroupList";
import {Product} from "Accounting";
import * as UCloud from "UCloud";
import {BulkRequest} from "UCloud";

export enum KeyCode {
    ENTER = 13,
    ESC = 27
}

export function bulkRequestOf<T>(...items: T[]): BulkRequest<T> {
    return {"type": "bulk", items};
}

export const emptyPage: Readonly<Page<any>> =
    {items: [], itemsInTotal: 0, itemsPerPage: 25, pageNumber: 0};

export const emptyPageV2: Readonly<UCloud.PageV2<any>> =
    {items: [], itemsPerPage: 25};

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
    header?: Reducer<HeaderSearchReduxObject>;
    sidebar?: Reducer<SidebarReduxObject>;
    activity?: Reducer<ActivityReduxObject>;
}

/**
 * Global state created via useGlobal() similar to ReduxObject
 */
export interface HookStore {
    fileFavoriteCache?: Record<string, boolean>;
    projectCache?: ProjectCache;
    projectManagementDetails?: APICallStateWithParams<UserInProject>;
    projectManagement?: APICallStateWithParams<Page<ProjectMember>>;
    projectManagementGroupMembers?: APICallStateWithParams<Page<string>, ListGroupMembersRequestProps>;
    projectManagementGroupSummary?: APICallStateWithParams<Page<GroupWithSummary>, PaginationRequest>;
    projectManagementQuery?: string;
    projectManagementOutgoingInvites?: APICallStateWithParams<Page<OutgoingInvite>, ListOutgoingInvitesRequest>;
    computeProducts?: APICallStateWithParams<Page<Product>>;
    storageProducts?: APICallStateWithParams<Page<Product>>;
    frameHidden?: boolean;
    cloudApiCache?: Record<string, { expiresAt: number, cached: any }>;
}

interface LegacyReduxObject {
    hookStore: HookStore;
    dashboard: DashboardStateProps;
    uploader: UploaderReduxObject;
    status: StatusReduxObject;
    notifications: NotificationsReduxObject;
    header: HeaderSearchReduxObject;
    sidebar: SidebarReduxObject;
    activity: ActivityReduxObject;
    simpleSearch: SimpleSearchStateProps;
    detailedFileSearch: DetailedFileSearchReduxState;
    fileInfo: FileInfoReduxObject;
    avatar: AvatarReduxObject;
    responsive?: ResponsiveReduxObject;
    project: ProjectRedux.State;
    loading?: boolean;
}

declare global {
    export type ReduxObject =
        LegacyReduxObject &
        TaskReduxState;
}

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
    notifications: [],
});

export function initObject(): ReduxObject {
    return {
        hookStore: {},
        dashboard: initDashboard(),
        status: initStatus(),
        header: initHeader(),
        notifications: initNotifications(),
        sidebar: initSidebar(),
        uploader: initUploads(),
        activity: initActivity(),
        simpleSearch: initSimpleSearch(),
        detailedFileSearch: initFilesDetailedSearch(),
        fileInfo: initFileInfo(),
        avatar: initAvatar(),
        project: ProjectRedux.initialState,
        responsive: undefined,
    };
}

export type AvatarReduxObject = typeof defaultAvatar & { error?: string };
export const initAvatar = (): AvatarReduxObject => ({...defaultAvatar, error: undefined});

export const initSimpleSearch = (): SimpleSearchStateProps => ({
    files: emptyPage,
    filesLoading: false,
    errors: [],
    search: "",
    fileSearch: initFilesDetailedSearch()
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

