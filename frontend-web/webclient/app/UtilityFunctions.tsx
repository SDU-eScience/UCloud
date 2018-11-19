import swal from "sweetalert2";
import { SensitivityLevel } from "DefaultObjects";
import Cloud from "Authentication/lib";
import { SortBy, SortOrder, File, Acl, FileType } from "Files";
import { dateToString } from "Utilities/DateUtilities";
import {
    getFilenameFromPath,
    fileSizeToString,
    replaceHomeFolder,
    isDirectory
} from "Utilities/FileUtilities";

/**
 * Lowercases the string and capitalizes the first letter of the string
 * @param str string to be lowercased and capitalized
 * @return {string}
 */
export const toLowerCaseAndCapitalize = (str: string): string => str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();

/**
 * Returns a string based on the amount of users associated with the ACL
 * @param {Acl[]} acls - the list of access controls
 * @return {string}
 */
export const getOwnerFromAcls = (acls?: Acl[]): string => {
    if (acls === undefined) return "N/A";
    if (acls.length > 0) {
        return `${acls.length + 1} members`;
    } else {
        return "Only You";
    }
};

/**
 * Renders a failure notification in the upper right corner, with provided text
 * @param {string} title The failure to be rendered
 * @param {number} seconds the amount of seconds the failure is rendered
 */
export const failureNotification = (title: string, seconds: number = 3) => swal({
    toast: true,
    position: "top-end",
    showConfirmButton: false,
    timer: seconds * 1_000,
    type: "error",
    title
});

/**
 * Renders a success notification in the upper right corner, with provided text
 * @param {string} title The success message to be rendered
 * @param {number} seconds the amount of seconds the content is rendered
 */
export const successNotification = (title: string, seconds: number = 3) => swal({
    toast: true,
    position: "top-end",
    showConfirmButton: false,
    timer: seconds * 1_000,
    type: "success",
    title
});

/**
 * Renders an information notification in the upper right corner, with provided text
 * @param {string} title The information to be rendered
 * @param {number} seconds the amount of seconds the content is rendered
 */
export const infoNotification = (title: string, seconds: number = 3) => swal({
    toast: true,
    position: "top-end",
    showConfirmButton: false,
    timer: seconds * 1_000,
    type: "info",
    title
});

export const uploadsNotifications = (finished: number, total: number) => swal({
    title: finished !== total ? `${finished} out of ${total} files uploaded` : "Uploads finished",
    toast: true,
    position: "top",
    timer: 2000,
    showConfirmButton: false,
    type: finished !== total ? "warning" : "success",

});

// FIXME React Semantic UI Forms doesn't seem to allow checkboxes with labels, unless browser native checkboxes
export const shareSwal = () => swal({
    title: "Share",
    input: "text",
    html: `<form class="ui form">
            <div class="three fields">
                <div class="field"><div class="ui checkbox">
                    <input id="read-swal" type="checkbox" /><label>Read</label>
                </div></div>
                <div class="field"><div class="ui checkbox">
                    <input id="write-swal" type="checkbox" /><label>Write</label>
                </div></div>
                <div class="field"><div class="ui checkbox">
                    <input id="execute-swal" type="checkbox" /><label>Execute</label>
                </div></div>
            </div>
          </form>`,
    showCloseButton: true,
    showCancelButton: true,
    inputPlaceholder: "Enter username...",
    focusConfirm: false,
    inputValidator: (value: string) => {
        if (!value) return "Username missing";
        if (!(isElementChecked("read-swal") ||
            isElementChecked("write-swal") ||
            isElementChecked("execute-swal"))) return "Select at least one access right";
        return null;
    }

});

export const isElementChecked = (id: string): boolean => (document.getElementById(id) as HTMLInputElement).checked;

export const inputSwal = (inputName: string) => ({
    title: "Share",
    input: "text",
    showCloseButton: true,
    showCancelButton: true,
    inputPlaceholder: `Enter ${inputName}...`,
    focusConfirm: false,
    inputValidator: (value: string) =>
        (!value && `${toLowerCaseAndCapitalize(inputName)} missing`)
});

export function sortingColumnToValue(sortBy: SortBy, file: File): string {
    switch (sortBy) {
        case SortBy.TYPE:
            return toLowerCaseAndCapitalize(file.fileType);
        case SortBy.PATH:
            return getFilenameFromPath(file.path);
        case SortBy.CREATED_AT:
            return dateToString(file.createdAt);
        case SortBy.MODIFIED_AT:
            return dateToString(file.modifiedAt);
        case SortBy.SIZE:
            return fileSizeToString(file.size);
        case SortBy.ACL:
            if (file.acl !== undefined)
                return getOwnerFromAcls(file.acl)
            else
                return "";
        case SortBy.FAVORITED:
            return file.favorited ? "Favorited" : "";
        case SortBy.SENSITIVITY:
            return SensitivityLevel[file.sensitivityLevel];
    }
}

export const getSortingIcon = (sortBy: SortBy, sortOrder: SortOrder, name: SortBy): ("arrowUp" | "arrowDown" | undefined) => {
    if (sortBy === name) {
        return sortOrder === SortOrder.DESCENDING ? "arrowDown" : "arrowUp";
    };
    return undefined;
};

export const extensionTypeFromPath = (path) => extensionType(extensionFromPath(path));
export const extensionFromPath = (path: string): string => {
    const splitString = path.split(".");
    return splitString[splitString.length - 1];
};

type ExtensionType = "" | "code" | "image" | "text" | "sound" | "archive"
export const extensionType = (ext: string): ExtensionType => {
    switch (ext) {
        case "md":
        case "swift":
        case "kt":
        case "kts":
        case "js":
        case "jsx":
        case "ts":
        case "tsx":
        case "java":
        case "py":
        case "python":
        case "tex":
        case "r":
        case "c":
        case "cc":
        case "c++":
        case "h++":
        case "cpp":
        case "h":
        case "hh":
        case "hpp":
        case "html":
        case "sql":
        case "sh":
        case "iol":
        case "ol":
        case "col":
        case "bib":
        case "toc":
        case "jar":
        case "exe":
            return "code";
        case "png":
        case "gif":
        case "tiff":
        case "eps":
        case "ppm":
        case "svg":
        case "jpg":
            return "image";
        case "txt":
        case "pdf":
        case "xml":
        case "json":
        case "csv":
        case "yml":
        case "plist":
            return "text";
        case "wav":
        case "mp3":
            return "sound";
        case "gz":
        case "zip":
        case "tar":
            return "archive";
        default:
            return "";
    }
}

type FileIcons = "tasks" | "star" | "trash alternate outline" | "folder" | "file outline" |
    "file code outline" | "image" | "file outline" | "volume up" | "file archive outline";
export const iconFromFilePath = (filePath: string, type: FileType, homeFolder: string): FileIcons => {
    const homeFolderReplaced = replaceHomeFolder(filePath, homeFolder);
    if (homeFolderReplaced === "Home/Jobs") return "tasks";
    if (homeFolderReplaced === "Home/Favorites") return "star";
    if (homeFolderReplaced === "Home/Trash") return "trash alternate outline";
    if (isDirectory({ fileType: type })) return "folder";
    const filename = getFilenameFromPath(filePath);
    if (!filename.includes(".")) {
        return "file outline";
    }
    const extension = extensionFromPath(filePath);
    const fileExtensionType = extensionType(extension);
    switch (fileExtensionType) {
        case "code":
            return "file code outline";
        case "image":
            return "image";
        case "sound":
            return "volume up";
        case "archive":
            return "file archive outline";
        case "text":
        default:
            return "file outline";
    }
};

// FIXME Remove navigation when backend support comes.
export const createProject = (filePath: string, cloud: Cloud, navigate: (path: string) => void) =>
    cloud.put("/projects", { fsRoot: filePath }).then(() => {
        redirectToProject(filePath, cloud, navigate, 5);
    }).catch(() => failureNotification(`An error occurred creating project ${filePath}`));

const redirectToProject = (path: string, cloud: Cloud, navigate: (path: string) => void, remainingTries: number) => {
    cloud.get(`/metadata/by-path?path=${path}`).then(() => navigate(path)).catch(_ => {
        remainingTries > 0 ?
            setTimeout(() => redirectToProject(path, cloud, navigate, remainingTries - 1), 400) :
            successNotification(`Project ${path} is being created.`)
    });
};

export const inRange = ({ status, min, max }: { status: number, min: number, max: number }): boolean =>
    status >= min && status <= max;
export const inSuccessRange = (status: number): boolean => inRange({ status, min: 200, max: 299 });
export const removeTrailingSlash = (path: string) => path.endsWith("/") ? path.slice(0, path.length - 1) : path;
export const addTrailingSlash = (path: string) => {
    if (!path) return path;
    else return path.endsWith("/") ? path : `${path}/`;
}
export const shortUUID = (uuid: string): string => uuid.substring(0, 8).toUpperCase();
export const is5xxStatusCode = (status: number) => inRange({ status, min: 500, max: 599 });
export const blankOrUndefined = (value?: string): boolean => value == null || value.length == 0 || /^\s*$/.test(value);

export const ifPresent = (f: any, handler: (f: any) => void) => {
    if (f) handler(f)
};

// FIXME The frontend can't handle downloading multiple files currently. When fixed, remove === 1 check.
export const downloadAllowed = (files: File[]) =>
    files.length === 1 && files.every(f => f.sensitivityLevel !== "SENSITIVE")

export const prettierString = (str: string) => toLowerCaseAndCapitalize(str).replace(/_/g, " ")

export function defaultErrorHandler(error: { request: XMLHttpRequest, response: any }): number {
    let request: XMLHttpRequest = error.request;
    // FIXME must be solvable more elegantly
    let why: string | null = null;

    if (!!error.response && !!error.response.why) {
        why = error.response.why;
    }

    if (!!request) {
        if (!why) {
            switch (request.status) {
                case 400:
                    why = "Bad request";
                    break;
                case 403:
                    why = "Permission denied";
                    break;
                default:
                    why = "Internal Server Error. Try again later.";
                    break;
            }
        }

        failureNotification(why);
        return request.status;
    }
    return 500;
}