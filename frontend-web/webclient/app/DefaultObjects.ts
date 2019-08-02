import {SidebarOption, Page} from "Types";
import {Status} from "Navigation";
import {Analysis, DetailedApplicationSearchReduxState, RunsSortBy} from "Applications";
import {File, DetailedFileSearchReduxState} from "Files";
import {SortOrder, SortBy} from "Files";
import {DashboardStateProps} from "Dashboard";
import {Publication} from "Zenodo";
import {Notification} from "Notifications";
import {Upload} from "Uploader";
import {Activity, ActivityGroup, ActivityFilter} from "Activity";
import {Reducer} from "redux";
import {SimpleSearchStateProps} from "Search";
import * as ApplicationRedux from "Applications/Redux";
import * as AccountingRedux from "Accounting/Redux";
import * as SnackbarRedux from "Snackbar/Redux";
import * as FavoritesRedux from "Favorites/Redux";
import {defaultAvatar} from "UserSettings/Avataaar";
import {SidebarPages} from "ui-components/Sidebar";
import {ScrollResult} from "Scroll/Types";
import * as ProjectRedux from "Project/Redux";

export const DefaultStatus: Status = {
    title: "No Issues",
    level: "NO ISSUES",
    body: "The system is running as intended."
};

export enum KeyCode {
    ENTER = 13,
    ESC = 27
}

export const emptyPage: Page<any> = {items: [], itemsPerPage: 25, itemsInTotal: 0, pageNumber: 0, pagesInTotal: 0};

export enum AnalysesStatusMap {
    "PENDING",
    "IN PROGRESS",
    "COMPLETED"
}

export enum RightsNameMap {
    "NONE" = "None",
    "READ" = "Read",
    "READ_WRITE" = "Read/Write"
}

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

function getFilesSortingColumnOrDefault(columnIndex: 0 | 1): SortBy {
    const sortingColumn = window.localStorage.getItem(`filesSorting${columnIndex}`);
    if (sortingColumn && Object.values(SortBy).includes(sortingColumn)) return sortingColumn as SortBy;
    switch (columnIndex) {
        case 0:
            window.localStorage.setItem("filesSorting0", SortBy.MODIFIED_AT);
            return SortBy.MODIFIED_AT;
        case 1:
            window.localStorage.setItem("filesSorting1", SortBy.SIZE);
            return SortBy.SIZE;
    }
}

function getItemOrDefault<T, T2>(itemName: string, defaultValue: T, en: T2): T {
    const item = window.localStorage.getItem(itemName);
    if (item && Object.values(en).includes(item)) {
        return item as unknown as T;
    }
    return defaultValue;
}

export interface ComponentWithLoadingState {
    loading: boolean
    error?: string
}

export interface ComponentWithPage<T> extends ComponentWithLoadingState {
    page: Page<T>
}

export interface ComponentWithScroll<Item, OffsetType> extends ComponentWithLoadingState {
    scroll?: ScrollResult<Item, OffsetType>
}

export interface ResponsiveReduxObject {
    mediaType: string
    orientation: string
    lessThan: Record<string, boolean>
    greaterThan: Record<string, boolean>
    is: Record<string, boolean>
}

export const initResponsive = (): ResponsiveReduxObject => ({
    mediaType: "",
    orientation: "",
    lessThan: {},
    greaterThan: {},
    is: {},
});

export interface FilePreviewReduxState {
    file?: File
    error?: string
}

export interface FilesReduxObject extends ComponentWithPage<File> {
    sortOrder: SortOrder
    sortBy: SortBy
    path: string
    filesInfoPath: string
    fileSelectorError?: string
    sortingColumns: [SortBy, SortBy]
    fileSelectorLoading: boolean
    fileSelectorShown: boolean
    fileSelectorPage: Page<File>
    fileSelectorPath: string
    fileSelectorCallback: Function
    fileSelectorIsFavorites: boolean
    disallowedPaths: string[]
    invalidPath: boolean
}

export interface FileInfoReduxObject {
    file?: File
    error?: string
    activity: Page<Activity>
    loading: boolean
}

export interface AnalysisReduxObject extends ComponentWithPage<Analysis> {
    sortBy: RunsSortBy
    sortOrder: SortOrder
}

export interface NotificationsReduxObject {
    redirectTo: string
    items: Notification[]
    loading: boolean
    error?: string
}

export interface ZenodoReduxObject extends ComponentWithPage<Publication> {
    connected: boolean
}

export interface StatusReduxObject {
    status: Status
    title: string
    page: SidebarPages
    loading: boolean
}

export interface SidebarReduxObject {
    pp: boolean
    options: SidebarOption[]
    kcCount: number
}

export interface HeaderSearchReduxObject {
    prioritizedSearch: HeaderSearchType
    refresh?: () => void
}

export interface RunApplicationReduxObject {

}

export type ActivityReduxObject = ComponentWithScroll<ActivityGroup, number> & ActivityFilter;

export type HeaderSearchType = "files" | "applications" | "projects";

export interface UploaderReduxObject {
    uploads: Upload[]
    visible: boolean
    path: string
    allowMultiple: boolean
    onFilesUploaded: (p: string) => void
    error?: string
    loading: boolean
}

interface LegacyReducers {
    dashboard?: Reducer<DashboardStateProps>
    files?: Reducer<FilesReduxObject>
    uploader?: Reducer<UploaderReduxObject>
    status?: Reducer<StatusReduxObject>
    notifications?: Reducer<NotificationsReduxObject>
    analyses?: Reducer<AnalysisReduxObject>
    zenodo?: Reducer<ZenodoReduxObject>
    header?: Reducer<HeaderSearchReduxObject>
    sidebar?: Reducer<SidebarReduxObject>
    activity?: Reducer<ActivityReduxObject>
    detailedResult?: Reducer<DetailedResultReduxObject>
}

export type Reducers = LegacyReducers & ApplicationRedux.Reducers & AccountingRedux.Reducers;

export type DetailedResultReduxObject = ComponentWithPage<File>

export const initDetailedResult = (): DetailedResultReduxObject => ({
    page: emptyPage,
    loading: false,
    error: undefined
});

/* FIXME */
interface LegacyReduxObject {
    dashboard: DashboardStateProps
    files: FilesReduxObject,
    uploader: UploaderReduxObject
    status: StatusReduxObject,
    notifications: NotificationsReduxObject
    analyses: AnalysisReduxObject
    zenodo: ZenodoReduxObject
    header: HeaderSearchReduxObject
    sidebar: SidebarReduxObject
    activity: ActivityReduxObject
    detailedResult: DetailedResultReduxObject
    simpleSearch: SimpleSearchStateProps
    detailedFileSearch: DetailedFileSearchReduxState
    detailedApplicationSearch: DetailedApplicationSearchReduxState
    fileInfo: FileInfoReduxObject
    avatar: AvatarReduxObject
    filePreview: FilePreviewReduxState
    responsive?: ResponsiveReduxObject
    project: ProjectRedux.State
    loading?: boolean
}

export type ReduxObject =
    LegacyReduxObject &
    ApplicationRedux.Objects &
    AccountingRedux.Objects &
    FavoritesRedux.Objects &
    SnackbarRedux.Wrapper;

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
    status: DefaultStatus,
    title: "",
    page: SidebarPages.None,
    loading: false
});

export const initDashboard = (): DashboardStateProps => ({
    favoriteFiles: [],
    recentFiles: [],
    recentAnalyses: [],
    notifications: [],
    favoriteLoading: false,
    recentLoading: false,
    analysesLoading: false
});

export function initObject(homeFolder: string): ReduxObject {
    return {
        dashboard: initDashboard(),
        files: initFiles(homeFolder),
        status: initStatus(),
        header: initHeader(),
        notifications: initNotifications(),
        analyses: initAnalyses(),
        zenodo: initZenodo(),
        sidebar: initSidebar(),
        uploader: initUploads(),
        activity: initActivity(),
        detailedResult: initDetailedResult(),
        simpleSearch: initSimpleSearch(),
        detailedApplicationSearch: initApplicationsAdvancedSearch(),
        detailedFileSearch: initFilesDetailedSearch(),
        fileInfo: initFileInfo(),
        avatar: initAvatar(),
        filePreview: initFilePreview(),
        project: ProjectRedux.initialState,
        ...ApplicationRedux.init(),
        ...AccountingRedux.init(),
        ...FavoritesRedux.init(),
        ...SnackbarRedux.init(),
        responsive: undefined,
    }
}


export const initFilePreview = () => ({
    file: undefined,
    error: undefined
});

export type AvatarReduxObject = typeof defaultAvatar & { error?: string };
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
    sortOrder: SortOrder.ASCENDING
});

export const initZenodo = (): ZenodoReduxObject => ({
    connected: false,
    loading: false,
    page: emptyPage
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

export const initFiles = (homeFolder: string): FilesReduxObject => ({
    page: emptyPage,
    sortOrder: getItemOrDefault("sortOrder", SortOrder.ASCENDING, SortOrder),
    sortBy: getItemOrDefault("sortBy", SortBy.PATH, SortBy),
    loading: false,
    error: undefined,
    path: "",
    filesInfoPath: "",
    sortingColumns: [getFilesSortingColumnOrDefault(0), getFilesSortingColumnOrDefault(1)],
    fileSelectorLoading: false,
    fileSelectorShown: false,
    fileSelectorPage: emptyPage,
    fileSelectorPath: homeFolder,
    fileSelectorIsFavorites: false,
    fileSelectorCallback: () => undefined,
    fileSelectorError: undefined,
    disallowedPaths: [],
    invalidPath: false
});

export const initFilesDetailedSearch = (): DetailedFileSearchReduxState => ({
    hidden: true,
    allowFolders: true,
    allowFiles: true,
    fileName: "",
    extensions: new Set(),
    tags: new Set(),
    sensitivities: new Set(),
    createdBefore: undefined,
    createdAfter: undefined,
    modifiedBefore: undefined,
    modifiedAfter: undefined,
    error: undefined,
    loading: false
});

export const initApplicationsAdvancedSearch = (): DetailedApplicationSearchReduxState => ({
    error: undefined,
    loading: false,
    hidden: true,
    appName: "",
    appVersion: "",
    tags: ""
});
