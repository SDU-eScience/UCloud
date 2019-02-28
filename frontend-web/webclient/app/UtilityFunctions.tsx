import swal from "sweetalert2";
import { SensitivityLevel } from "DefaultObjects";
import Cloud from "Authentication/lib";
import { SortBy, SortOrder, File, Acl, FileType } from "Files";
import { dateToString } from "Utilities/DateUtilities";
import {
    getFilenameFromPath,
    sizeToString,
    replaceHomeFolder,
    isDirectory
} from "Utilities/FileUtilities";
import { HTTP_STATUS_CODES } from "Utilities/XHRUtils";

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
export function failureNotification(title: string, seconds: number = 3) {
    return swal({
        toast: true,
        position: "top-end",
        showConfirmButton: false,
        timer: seconds * 1_000,
        type: "error",
        title
    });
}

/**
 * Renders a success notification in the upper right corner, with provided text
 * @param {string} title The success message to be rendered
 * @param {number} seconds the amount of seconds the content is rendered
 */
export function successNotification(title: string, seconds: number = 3) {
    return swal({
        toast: true,
        position: "top-end",
        showConfirmButton: false,
        timer: seconds * 1_000,
        type: "success",
        title
    });
}
/**
 * Renders an information notification in the upper right corner, with provided text
 * @param {string} title The information to be rendered
 * @param {number} seconds the amount of seconds the content is rendered
 */
export function infoNotification(title: string, seconds: number = 3) {
    return swal({
        toast: true,
        position: "top-end",
        showConfirmButton: false,
        timer: seconds * 1_000,
        type: "info",
        title
    });
}

export function uploadsNotifications(finished: number, total: number) {
    return swal({
        title: finished !== total ? `${finished} out of ${total} files uploaded` : "Uploads finished",
        toast: true,
        position: "top",
        timer: 2000,
        showConfirmButton: false,
        type: finished !== total ? "warning" : "success",
    });
}

export function overwriteSwal() {
    return swal({
        allowEscapeKey: true,
        allowOutsideClick: true,
        showCancelButton: true,
        title: "Warning",
        type: "warning",
        text: "The existing file is being overwritten. Cancelling now will corrupt the file. Continue?",
        cancelButtonText: "Continue",
        confirmButtonText: "Cancel Upload"
    });
}

export function shareSwal() {
    return swal({
        title: "Share",
        input: "text",
        html: `<div>
                <input name="access" type="radio" value="read" id="read"/>
                <label for="read">Can View</label>
                <span style="margin-left:20px" />
                <input name="access" type="radio" value="read_edit" id="read_edit"/>
                <label for="read_edit">Can View and Edit</label>
            </div>`,
        showCloseButton: true,
        showCancelButton: true,
        inputPlaceholder: "Enter username...",
        focusConfirm: false,
        inputValidator: (value: string) => {
            if (!value) return "Username missing";
            if (!(elementValue("read") || elementValue("read_edit"))) return "Select at least one access right";
            return null;
        }

    });
}

export function sensitivitySwal() {
    return swal({
        title: "Change Sensitivity",
        input: "select",
        inputOptions: {
            "INHERIT": "Inherit",
            "PRIVATE": "Private",
            "CONFIDENTIAL": "Confidential",
            "SENSITIVE": "Sensitive"
        },
        showCloseButton: true,
        showCancelButton: true,
        focusConfirm: false,
        inputValidator: (value: string) => {
            return null;
        }
    });
}

export const elementValue = (id: string): boolean => (document.getElementById(id) as HTMLInputElement).checked;
export const selectValue = (id: string): string => (document.getElementById(id) as HTMLSelectElement).value;

export const inputSwal = (inputName: string) => ({
    title: "Share",
    input: "text",
    showCloseButton: true,
    showCancelButton: true,
    inputPlaceholder: `Enter ${inputName}...`,
    focusConfirm: false,
    inputValidator: (value: string) => (!value && `${toLowerCaseAndCapitalize(inputName)} missing`)
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
            return sizeToString(file.size);
        case SortBy.ACL:
            if (file.acl !== undefined)
                return getOwnerFromAcls(file.acl)
            else
                return "";
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

export const extensionTypeFromPath = (path: string) => extensionType(extensionFromPath(path));
export const extensionFromPath = (path: string): string => {
    const splitString = path.split(".");
    return splitString[splitString.length - 1];
};

type ExtensionType = null | "code" | "image" | "text" | "audio" | "video" | "archive" | "pdf" | "binary"
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
        case "lhs":
        case "hs":
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
        case "xml":
        case "json":
        case "csv":
        case "yml":
        case "plist":
            return "text";
        case "pdf":
            return "pdf";
        case "wav":
        case "mp3":
            return "audio";
        case "mpg":
        case "mp4":
        case "avi":
            return "video";
        case "gz":
        case "zip":
        case "tar":
        case "tgz":
        case "tbz":
        case "bz2":
            return "archive";
        case "dat":
            return "binary";
        default:
            return null;
    }
}

export interface FtIconProps {
    type: FileType;
    ext?: string;
}

export const iconFromFilePath = (filePath: string, type: FileType, homeFolder: string): FtIconProps => {
    let icon: FtIconProps = { type: "FILE" };
    if (isDirectory({ fileType: type })) {
        const homeFolderReplaced = replaceHomeFolder(filePath, homeFolder);
        switch (homeFolderReplaced) {
            case "Home/Jobs":
                icon.type = "RESULTFOLDER";
                break;
            case "Home/Favorites":
                icon.type = "FAVFOLDER";
                break;
            case "Home/Trash":
                icon.type = "TRASHFOLDER";
                break;
            default:
                icon.type = "DIRECTORY";
        }
        return icon;
    }

    const filename = getFilenameFromPath(filePath);
    if (!filename.includes(".")) {
        return icon;
    }
    icon.ext = extensionFromPath(filePath);

    return icon;
};

// FIXME Remove navigation when backend support comes.
export const createProject = (filePath: string, cloud: Cloud, navigate: (path: string) => void) => {
    cloud.put("/projects", { fsRoot: filePath }).then(() => {
        redirectToProject(filePath, cloud, navigate, 5);
    }).catch(() => failureNotification(`An error occurred creating project ${filePath}`));
}

const redirectToProject = (path: string, cloud: Cloud, navigate: (path: string) => void, remainingTries: number) => {
    cloud.get(`/metadata/by-path?path=${encodeURIComponent(path)}`).then(() => navigate(path)).catch(_ => {
        remainingTries > 0 ?
            setTimeout(() => redirectToProject(path, cloud, navigate, remainingTries - 1), 400) :
            successNotification(`Project ${path} is being created.`)
    });
};

/**
 * 
 * @param params: { status, min, max } (both inclusive)
 */
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

export function sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
}

export function sortByToPrettierString(sortBy: SortBy): string {
    switch (sortBy) {
        case SortBy.ACL:
            return "Members";
        default:
            return prettierString(sortBy);
    }
}

export function timestampUnixMs(): number {
    return window.performance &&
        window.performance.now &&
        window.performance.timing &&
        window.performance.timing.navigationStart ?
        window.performance.now() + window.performance.timing.navigationStart :
        Date.now();
}

export function humanReadableNumber(
    number: number,
    sectionDelim: string = ",",
    decimalDelim: string = ".",
    numDecimals: number = 2
): string {
    const regex = new RegExp("\\d(?=(\\d{3})+" + (numDecimals > 0 ? "\\D" : "$") + ")", "g");
    const fixedNumber = number.toFixed(numDecimals);

    return fixedNumber
        .replace('.', decimalDelim)
        .replace(regex, '$&' + sectionDelim);
}

export function errorMessageOrDefault(err: { request: XMLHttpRequest, response: any }, defaultMessage: string): string {
    if (err.response.why) return err.response.why;
    return HTTP_STATUS_CODES[err.request.status] || defaultMessage;
}