// TODO: Split in to more specific files
import { tusConfig } from "Configurations";
import * as Uppy from "uppy";
import { SidebarOption, Page } from "Types";
import { Status } from "Navigation";
import { Analysis, Application } from "Applications";
import { File } from "Files";
import SDUCloud from "Authentication/lib";
import { SortOrder, SortBy } from "Files";
import { DashboardStateProps } from "Dashboard";
import { Publication } from "Zenodo";
import { Notification } from "Notifications";
import { Upload } from "Uploader";
import { Activity } from "Activity";
import { Reducer } from "redux";

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
    "CONFIDENTIAL" = "Confidential",
    "SENSITIVE" = "Sensitive"
};

export enum SensitivityLevelMap {
    "OPEN_ACCESS",
    "CONFIDENTIAL",
    "SENSITIVE"
};

export interface UppyRestriction {
    maxFileSize?: false | number
    maxNumberOfFiles?: false | number
    minNumberOfFiles?: false | number
    allowedFileTypes: false | number
}

export const initializeUppy = (restrictions: UppyRestriction, cloud: SDUCloud): Uppy =>
    Uppy.Core({
        autoProceed: false,
        debug: false,
        restrictions: restrictions,
        meta: {
            sensitive: false,
        },
        onBeforeUpload: () => {
            return cloud.receiveAccessTokenOrRefreshIt().then((data: string) => {
                tusConfig.headers["Authorization"] = `Bearer ${data}`;
            });
        }
    }).use(Uppy.Tus, tusConfig);

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

interface ComponentWithPage<T> {
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
}

export interface HeaderSearchReduxObject {
    prioritizedSearch: HeaderSearchType
}

export type ApplicationReduxObject = ComponentWithPage<Application>;
export type ActivityReduxObject = ComponentWithPage<Activity>

export type HeaderSearchType = "files" | "applications" | "projects";

export interface UploaderReduxObject {
    uploads: Upload[]
    visible: boolean
    path: string
    allowMultiple: boolean
    onFilesUploaded: (p: string) => void
}

export interface Reducers {
    dashboard?: Reducer<DashboardStateProps>
    files?: Reducer<FilesReduxObject>
    uploader?: Reducer<UploaderReduxObject>
    uppy?: Reducer<any>
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
    uppy: { uppy: any, uppyOpen: boolean }
    status: StatusReduxObject,
    applications: ApplicationReduxObject
    notifications: NotificationsReduxObject
    analyses: AnalysisReduxObject
    zenodo: ZenodoReduxObject
    header: HeaderSearchReduxObject
    sidebar: SidebarReduxObject
    activity: ActivityReduxObject
    detailedResult: DetailedResultReduxObject
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

export const initApplications = () => ({
    page: emptyPage,
    loading: false,
    error: undefined
});

export const initStatus = () => ({
    status: DefaultStatus,
    title: ""
});

export const initDashboard = () => ({
    favoriteFiles: [],
    recentFiles: [],
    recentAnalyses: [],
    notifications: [],
    favoriteLoading: false,
    recentLoading: false,
    analysesLoading: false,
    favoriteError: undefined,
    recentFilesError: undefined,
    recentAnalysesError: undefined
});

export const initObject = (cloud: SDUCloud): ReduxObject => ({
    dashboard: initDashboard(),
    files: initFiles(cloud),
    uppy: initUppy(cloud),
    status: initStatus(),
    applications: initApplications(),
    header: initHeader(),
    notifications: initNotifications(),
    analyses: initAnalyses(),
    zenodo: initZenodo(),
    sidebar: initSidebar(),
    uploader: initUploads(),
    activity: initActivity(),
    detailedResult: initDetailedResult()
});

export const initAnalyses = (): ComponentWithPage<Analysis> => ({
    page: emptyPage,
    loading: false,
    error: undefined
});


export const initZenodo = () => ({
    connected: false,
    loading: false,
    page: emptyPage,
    error: undefined
})

export const initSidebar = () => ({
    open: false,
    loading: false,
    pp: false,
    options: []
});

export const initUploads = () => ({
    path: "",
    uploads: [],
    visible: false,
    allowMultiple: false,
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

export const initFiles = ({ homeFolder }: { homeFolder: string }): FilesReduxObject => ({
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
    fileSelectorCallback: () => null,
    fileSelectorError: undefined,
    disallowedPaths: []
});

export const initUppy = (cloud: SDUCloud) => ({
    uppy: initializeUppy({ maxNumberOfFiles: 1 } as UppyRestriction, cloud),
    uppyOpen: false
});
