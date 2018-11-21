import { SidebarOption, Page } from "Types";
import { Status } from "Navigation";
import { Analysis, Application, DetailedApplicationSearchReduxState } from "Applications";
import { File, DetailedFileSearchReduxState } from "Files";
import { SortOrder, SortBy } from "Files";
import { DashboardStateProps } from "Dashboard";
import { Publication } from "Zenodo";
import { Notification } from "Notifications";
import { Upload } from "Uploader";
import { Activity } from "Activity";
import { Reducer } from "redux";
import { SimpleSearchStateProps } from "Search";

export const DefaultStatus: Status = {
    title: "No Issues",
    level: "NO ISSUES",
    body: "The system is running as intended."
};

export enum KeyCode {
    ENTER = 13,
    ESC = 27,
    UP = 38,
    DOWN = 40,
    LEFT = 37,
    RIGHT = 39,
    A = 65,
    B = 66
};

export const KCValues = [38, 76, 116, 156, 193, 232, 269, 308, 374, 439];

export const emptyPage: Page<any> = { items: [], itemsPerPage: 25, itemsInTotal: 0, pageNumber: 0, pagesInTotal: 0 };

export enum AnalysesStatusMap {
    "PENDING",
    "IN PROGRESS",
    "COMPLETED"
};

export enum RightsNameMap {
    "NONE" = "None",
    "READ" = "Read",
    "READ_WRITE" = "Read/Write",
    "EXECUTE" = "Execute"
};

export enum SensitivityLevel {
    "OPEN_ACCESS" = "Open Access",
    "PRIVATE" = "Private",
    "CONFIDENTIAL" = "Confidential",
    "SENSITIVE" = "Sensitive"
};

export type Sensitivity = keyof typeof SensitivityLevel;

export enum SensitivityLevelMap {
    "OPEN_ACCESS",
    "PRIVATE",
    "CONFIDENTIAL",
    "SENSITIVE"
};

const getFilesSortingColumnOrDefault = (columnIndex: number): SortBy => {
    const sortingColumn = window.localStorage.getItem(`filesSorting${columnIndex}`) as SortBy;
    if (!sortingColumn || !(sortingColumn in SortBy)) {
        if (columnIndex === 0) {
            window.localStorage.setItem("filesSorting0", SortBy.MODIFIED_AT);
            return SortBy.MODIFIED_AT;
        } else if (columnIndex === 1) {
            window.localStorage.setItem("filesSorting1", SortBy.ACL);
            return SortBy.ACL;
        }
    }
    return sortingColumn;
};

export interface ComponentWithPage<T> {
    page: Page<T>
    loading: boolean
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
    disallowedPaths: string[]
    invalidPath: boolean
}

export type AnalysisReduxObject = ComponentWithPage<Analysis>;

export interface NotificationsReduxObject extends ComponentWithPage<Notification> {
    redirectTo: string
}

export interface ZenodoReduxObject extends ComponentWithPage<Publication> {
    connected: boolean
}

export interface StatusReduxObject {
    status: Status
    title: string
}

export interface SidebarReduxObject {
    loading: boolean
    open: boolean
    pp: boolean
    options: SidebarOption[]
    kcCount: number
}

export interface HeaderSearchReduxObject {
    prioritizedSearch: HeaderSearchType
}

export interface ApplicationReduxObject extends ComponentWithPage<Application> {
    favorites: Page<Application>
    favoritesLoading: boolean
};

export type ActivityReduxObject = ComponentWithPage<Activity>

export type HeaderSearchType = "files" | "applications" | "projects";

export interface UploaderReduxObject {
    uploads: Upload[]
    visible: boolean
    path: string
    allowMultiple: boolean
    onFilesUploaded: (p: string) => void
    error?: string
}

export interface Reducers {
    dashboard?: Reducer<DashboardStateProps>
    files?: Reducer<FilesReduxObject>
    uploader?: Reducer<UploaderReduxObject>
    status?: Reducer<StatusReduxObject>
    applications?: Reducer<ApplicationReduxObject>
    notifications?: Reducer<NotificationsReduxObject>
    analyses?: Reducer<AnalysisReduxObject>
    zenodo?: Reducer<ZenodoReduxObject>
    header?: Reducer<HeaderSearchReduxObject>
    sidebar?: Reducer<SidebarReduxObject>
    activity?: Reducer<ActivityReduxObject>
    detailedResult?: Reducer<DetailedResultReduxObject>
}

export type DetailedResultReduxObject = ComponentWithPage<File>

export const initDetailedResult = (): DetailedResultReduxObject => ({
    page: emptyPage,
    loading: false,
    error: undefined
});

export interface ReduxObject {
    dashboard: DashboardStateProps
    files: FilesReduxObject,
    uploader: UploaderReduxObject
    status: StatusReduxObject,
    applications: ApplicationReduxObject
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
}

export const initActivity = (): ActivityReduxObject => ({
    page: emptyPage,
    error: undefined,
    loading: false
});

export const initNotifications = (): NotificationsReduxObject => ({
    page: emptyPage,
    loading: false,
    redirectTo: "",
    error: undefined
});

export const initHeader = (): HeaderSearchReduxObject => ({
    prioritizedSearch: "files"
});

export const initApplications = (): ApplicationReduxObject => ({
    page: emptyPage,
    favorites: emptyPage,
    favoritesLoading: false,
    loading: false,
    error: undefined
});

export const initStatus = (): StatusReduxObject => ({
    status: DefaultStatus,
    title: ""
});

export const initDashboard = (): DashboardStateProps => ({
    favoriteFiles: [],
    recentFiles: [],
    recentAnalyses: [],
    notifications: [],
    favoriteLoading: false,
    recentLoading: false,
    analysesLoading: false,
    errors: []
});

export const initObject = (homeFolder: string): ReduxObject => ({
    dashboard: initDashboard(),
    files: initFiles(homeFolder),
    status: initStatus(),
    applications: initApplications(),
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
    detailedFileSearch: initFilesDetailedSearch()
});

export const initSimpleSearch = (): SimpleSearchStateProps => ({
    files: emptyPage,
    filesLoading: false,
    applications: emptyPage,
    applicationsLoading: false,
    projects: emptyPage,
    projectsLoading: false,
    errors: [],
    search: "",
    applicationSearch: initApplicationsAdvancedSearch(),
    fileSearch: initFilesDetailedSearch()
})

export const initAnalyses = (): ComponentWithPage<Analysis> => ({
    page: emptyPage,
    loading: false,
    error: undefined
});


export const initZenodo = (): ZenodoReduxObject => ({
    connected: false,
    loading: false,
    page: emptyPage,
    error: undefined
})

export const initSidebar = (): SidebarReduxObject => ({
    open: false,
    loading: false,
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
    onFilesUploaded: () => null
})

export const identifierTypes = [
    {
        text: "Cited by",
        value: "isCitedBy"
    },
    {
        text: "Cites",
        value: "cites"
    },
    {
        text: "Supplement to",
        value: "isSupplementTo"
    },
    {
        text: "Supplemented by",
        value: "“isSupplementedBy”"
    },
    {
        text: "New version of",
        value: "isNewVersionOf"
    },
    {
        text: "Previous version of",
        value: "isPreviousVersionOf"
    },
    {
        text: "Part of",
        value: "“isPartOf”"
    },
    {
        text: "Has part",
        value: "“hasPart”"
    },
    {
        text: "Compiles",
        value: "compiles"
    },
    {
        text: "Is compiled by",
        value: "isCompiledBy"
    },
    {
        text: "Identical to",
        value: "isIdenticalTo"
    },
    {
        text: "Alternative identifier",
        value: "IsAlternateIdentifier"
    }
];

export const initFiles = (homeFolder: string): FilesReduxObject => ({
    page: emptyPage,
    sortOrder: SortOrder.ASCENDING,
    sortBy: SortBy.PATH,
    loading: false,
    error: undefined,
    path: "",
    filesInfoPath: "",
    sortingColumns: [getFilesSortingColumnOrDefault(0), getFilesSortingColumnOrDefault(1)],
    fileSelectorLoading: false,
    fileSelectorShown: false,
    fileSelectorPage: emptyPage,
    fileSelectorPath: homeFolder,
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
    page: emptyPage,
    loading: false
});

export const initApplicationsAdvancedSearch = (): DetailedApplicationSearchReduxState => ({
    page: emptyPage,
    error: undefined,
    loading: false,
    hidden: true,
    appName: "",
    appVersion: "", // Makes sense as range instead
    tags: ""
});