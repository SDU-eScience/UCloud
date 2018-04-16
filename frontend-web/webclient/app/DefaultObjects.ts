import { tusConfig } from "./Configurations";
import * as Uppy from "uppy";
import { File, Analysis, Application, Status, Publication, SidebarOption, } from "./types/types"
import SDUCloud from "../authentication/lib";

export const DefaultStatus: Status = {
    title: "No Issues",
    level: "NO ISSUES",
    body: "The system is running as intended."
};

export enum KeyCode {
    ENTER = 13,
    ESC = 27
}

export const RightsMap: { [s: string]: number } = {
    "NONE": 0,
    "READ": 1,
    "READ_WRITE": 2,
    "EXECUTE": 3
};

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
    maxFileSize?: boolean | number
    maxNumberOfFiles?: boolean | number
    minNumberOfFiles?: boolean | number
    allowedFileTypes: boolean | number
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


const getFilesSortingColumnOrDefault = (index: number): string => {
    const sortingColumn = window.localStorage.getItem(`filesSorting${index}`);
    if (!sortingColumn) {
        if (index === 0) {
            window.localStorage.setItem("filesSorting0", "lastModified");
            return "lastModified";
        } else if (index === 1) {
            window.localStorage.setItem("filesSorting1", "acl");
            return "acl";
        }
    }
    return sortingColumn;
};

export const initObject = (cloud: SDUCloud) => ({
    dashboard: {
        favoriteFiles: [] as File[],
        recentFiles: [] as File[],
        recentAnalyses: [] as Analysis[],
        activity: [] as any[],
        favoriteLoading: false,
        recentLoading: false,
        analysesLoading: false,
        activityLoading: false,
    },
    files: {
        files: [] as File[],
        filesPerPage: 10,
        currentFilesPage: 0,
        loading: false,
        path: "",
        filesInfoPath: "",
        projects: [] as any[],
        sortingColumns: [ getFilesSortingColumnOrDefault(0), getFilesSortingColumnOrDefault(1) ]
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
        applications: [] as Application[],
        loading: false,
        applicationsPerPage: 10,
        currentApplicationsPage: 0
    },
    analyses: {
        loading: false,
        analyses: [] as Analysis[],
        analysesPerPage: 10,
        pageNumber: 0,
        totalPages: 0
    },
    zenodo: {
        loading: false,
        connected: false,
        publications: [] as Publication[]
    },
    sidebar: {
        loading: false,
        options: [] as SidebarOption[]
    }
});

