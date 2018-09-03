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
}

export const emptyPage: Page<any> = { items: [], itemsPerPage: 25, itemsInTotal: 0, pageNumber: 0, pagesInTotal: 0 };

export enum AnalysesStatusMap {
    "PENDING",
    "IN PROGRESS",
    "COMPLETED"
};

export const RightsNameMap: { [s: string]: string } = {
    "NONE": "None",
    "READ": "Read",
    "READ_WRITE": "Read/Write",
    "EXECUTE": "Execute"
};

export enum SensitivityLevel {
    "OPEN_ACCESS" = "Open Access",
    "CONFIDENTIAL" = "Confidential",
    "SENSITIVE" = "Sensitive"
};

export const SensitivityLevelMap: { [s: string]: number } = {
    "OPEN_ACCESS": 0,
    "CONFIDENTIAL": 1,
    "SENSITIVE": 2
};

interface UppyRestriction {
    maxFileSize?: false | number
    maxNumberOfFiles?: false | number
    minNumberOfFiles?: false | number
    allowedFileTypes: false | number
}

const initializeUppy = (restrictions: UppyRestriction, cloud: SDUCloud) =>
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
    fileSelectorShown: false
    fileSelectorPage: Page<File>
    fileSelectorPath: string
    fileSelectorCallback: Function
    disallowedPaths: string[]
}

interface NotificationsReduxObject extends ComponentWithPage<Notification> {
    redirectTo: string
}

interface ZenodoReduxObject extends ComponentWithPage<Publication> {
    connected: boolean
}

interface StatusReduxObject {
    status: Status
    title: string
}

interface SidebarReduxObject {
    loading: boolean
    open: boolean
    options: SidebarOption[]
}

interface HeaderSearch {
    prioritizedSearch: HeaderSearchType
}

export type HeaderSearchType = "files" | "applications" | "projects";

interface UploaderReduxObject {
    uploads: Upload[]
    visible: boolean
    path: string
    allowMultiple: boolean
    onFilesUploaded: () => void
}

export interface ReduxObject {
    dashboard: DashboardStateProps
    files: FilesReduxObject,
    uploader: UploaderReduxObject
    uppy: any
    status: StatusReduxObject,
    applications: ComponentWithPage<Application>
    notifications: NotificationsReduxObject
    analyses: ComponentWithPage<Analysis>
    zenodo: ZenodoReduxObject
    header: HeaderSearch
    sidebar: SidebarReduxObject
}

export const initObject = (cloud: SDUCloud): ReduxObject => ({
    dashboard: {
        favoriteFiles: [] as File[],
        recentFiles: [] as File[],
        recentAnalyses: [] as Analysis[],
        notifications: [] as Notification[],
        favoriteLoading: false,
        recentLoading: false,
        analysesLoading: false,
        favoriteError: undefined,
        recentFilesError: undefined,
        recentAnalysesError: undefined
    },
    files: {
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
        fileSelectorPath: cloud.homeFolder,
        fileSelectorCallback: () => null,
        fileSelectorError: undefined,
        disallowedPaths: []
    },
    uppy: {
        uppyFiles: initializeUppy({ maxNumberOfFiles: false } as UppyRestriction, cloud),
        uppyFilesOpen: false,
        uppyRunApp: initializeUppy({ maxNumberOfFiles: 1 } as UppyRestriction, cloud),
        uppyRunAppOpen: false
    },
    status: {
        status: DefaultStatus,
        title: ""
    },
    applications: {
        page: emptyPage,
        loading: false,
        error: undefined
    },
    header: {
        prioritizedSearch: "files"
    },
    notifications: {
        page: emptyPage,
        loading: false,
        redirectTo: "",
        error: undefined
    },
    analyses: {
        page: emptyPage,
        loading: false,
        error: undefined
    },
    zenodo: {
        connected: false,
        loading: false,
        page: emptyPage,
        error: undefined
    },
    sidebar: {
        open: false,
        loading: false,
        options: [] as SidebarOption[]
    },
    uploader: {
        path: "",
        uploads: [],
        visible: false,
        allowMultiple: false,
        onFilesUploaded: () => null
    }
});

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