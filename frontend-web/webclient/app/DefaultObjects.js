import {Cloud} from "../authentication/SDUCloudObject"
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

const initializeUppy = (restrictions) => 
    Uppy.Core({
        autoProceed: false,
        debug: false,
        restrictions: restrictions,
        meta: {
            sensitive: false,
        },
        onBeforeUpload: () => {
            return Cloud.receiveAccessTokenOrRefreshIt().then((data) => {
                tusConfig.headers["Authorization"] = `Bearer ${data}`;
            });
        }
    }).use(Uppy.Tus, tusConfig);

export const initObject = {
    files: {
        files: [],
        filesShown: 10,
        filesPerPage: 10,
        currentFilesPage: 0,
        loading: false,
        path: "",
        filesInfoPath: "",
        filesInfoFile: null,
        projects: []
    },
    uppy: {
        uppyFiles: initializeUppy({maxNumberOfFiles: false}),
        uppyFilesOpen: false,
        uppyRunApp: initializeUppy({ maxNumberOfFiles: 1}),
        uppyRunAppOpen: false
    },
    status: {
        status: DefaultStatus,
        title: ""
    }
};