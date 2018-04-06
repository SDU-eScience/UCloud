import {tusConfig} from "./Configurations";
import * as Uppy from "uppy/lib";
import { File, Analysis, Application, Status } from "./types/types"
import SDUCloud from "../authentication/lib";

export const DefaultStatus:Status = {
    title: "No Issues",
    level: "NO ISSUES",
    body: "The system is running as intended."
};

export const RightsMap:any = {
    NONE: 0,
    READ: 1,
    READ_WRITE: 2,
    OWN: 3
};

export enum AnalysesStatusMap {
    "PENDING",
    "IN PROGRESS",
    "COMPLETED"
};

export const RightsNameMap:any = {
    "NONE": "None",
    "READ": "Read",
    "READ_WRITE": "Read/Write",
    "OWN": "Own"
};

export enum SensitivityLevel {
    "OPEN_ACCESS" = "Open Access",
    "CONFIDENTIAL" = "Confidential",
    "SENSITIVE" = "Sensitive"
};

export const SensitivityLevelMap:any = {
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
        projects: [] as any[]
    },
    uppy: {
        uppyFiles: initializeUppy({maxNumberOfFiles: false} as UppyRestriction, cloud),
        uppyFilesOpen: false,
        uppyRunApp: initializeUppy({ maxNumberOfFiles: 1} as UppyRestriction, cloud),
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
        publications: [] as any[]
    }
});