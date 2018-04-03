import {tusConfig} from "./Configurations";
import Uppy from "uppy";

export const DefaultStatus = {
    title: "No Issues",
    level: "NO ISSUES",
    body: "The system is running as intended.",
};

export const RightsMap = {
    NONE: 0,
    READ: 1,
    READ_WRITE: 2,
    OWN: 3,
};

export const AnalysesStatusMap = {
    "PENDING": 0,
    "IN PROGRESS": 1,
    "COMPLETED": 2,
};

export const RightsNameMap = {
    NONE: "None",
    READ: "Read",
    READ_WRITE: "Read/Write",
    OWN: "Own",
};

export const SensitivityLevel = {
    "OPEN_ACCESS": "Open Access",
    "CONFIDENTIAL": "Confidential",
    "SENSITIVE": "Sensitive",
};

export const SensitivityLevelMap = {
    "OPEN_ACCESS": 0,
    "CONFIDENTIAL": 1,
    "SENSITIVE": 2,
};

const initializeUppy = (restrictions, cloud) => 
    Uppy.Core({
        autoProceed: false,
        debug: false,
        restrictions: restrictions,
        meta: {
            sensitive: false,
        },
        onBeforeUpload: () => {
            return cloud.receiveAccessTokenOrRefreshIt().then((data) => {
                tusConfig.headers["Authorization"] = `Bearer ${data}`;
            });
        }
    }).use(Uppy.Tus, tusConfig);

export const initObject = (cloud) => ({
    dashboard: {
        favoriteFiles: [],
        recentFiles: [],
        recentAnalyses: [],
        activity: [],
        favoriteLoading: false,
        recentLoading: false,
        analysesLoading: false,
        activityLoading: false,
    },
    files: {
        files: [],
        filesPerPage: 10,
        currentFilesPage: 0,
        loading: false,
        path: "",
        filesInfoPath: "",
        projects: []
    },
    uppy: {
        uppyFiles: initializeUppy({maxNumberOfFiles: false}, cloud),
        uppyFilesOpen: false,
        uppyRunApp: initializeUppy({ maxNumberOfFiles: 1}, cloud),
        uppyRunAppOpen: false
    },
    status: {
        status: DefaultStatus,
        title: ""
    },
    applications: {
        applications: [],
        loading: false,
        applicationsPerPage: 10,
        currentApplicationsPage: 0
    },
    analyses: {
        loading: false,
        analyses: [],
        analysesPerPage: 10,
        pageNumber: 0,
        totalPages: 0
    },
    zenodo: {
        loading: false,
        connected: false,
        publications: []
    }
});