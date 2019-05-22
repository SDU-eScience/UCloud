import { SensitivityLevel } from "DefaultObjects";
import Cloud from "Authentication/lib";
import { SortBy, SortOrder, File, Acl, FileType } from "Files";
import { dateToString } from "Utilities/DateUtilities";
import { getFilenameFromPath, sizeToString, replaceHomeFolder, isDirectory } from "Utilities/FileUtilities";
import { HTTP_STATUS_CODES } from "Utilities/XHRUtils";
import { SnackType, AddSnackOperation, Snack } from "Snackbar/Snackbars";

/**
 * Capitalizes the input string
 * @param str string to be lowercased and capitalized
 * @return {string}
 */
export const capitalized = (str: string): string => str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();

/**
 * Returns a string based on the amount of users associated with the ACL
 * @param {Acl[]} acls - the list of access controls
 * @return {string}
 */
export const getOwnerFromAcls = (acls?: Acl[]): string => {
    if (acls === undefined) return "N/A";
    if (acls.length > 0) {
        return `${acls.length} members`;
    } else {
        return "Only You";
    }
};

export function sortingColumnToValue(sortBy: SortBy, file: File): string {
    switch (sortBy) {
        case SortBy.FILE_TYPE:
            return capitalized(file.fileType);
        case SortBy.PATH:
            return getFilenameFromPath(file.path);
        case SortBy.CREATED_AT:
            return dateToString(file.createdAt!);
        case SortBy.MODIFIED_AT:
            return dateToString(file.modifiedAt!);
        case SortBy.SIZE:
            return sizeToString(file.size!);
        case SortBy.ACL:
            if (file.acl !== null)
                return getOwnerFromAcls(file.acl);
            else
                return "";
        case SortBy.SENSITIVITY_LEVEL:
            return SensitivityLevel[file.sensitivityLevel!];
    }
}

export const getSortingIcon = (sortBy: SortBy, sortOrder: SortOrder, name: SortBy): ("arrowUp" | "arrowDown" | undefined) => {
    if (sortBy === name) {
        return sortOrder === SortOrder.DESCENDING ? "arrowDown" : "arrowUp";
    }
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
        case "h":
        case "cc":
        case "hh":
        case "c++":
        case "h++":
        case "hpp":
        case "cpp":
        case "cxx":
        case "hxx":
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
};

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


interface CreateProject extends AddSnackOperation {
    filePath: string
    cloud: Cloud
    navigate: (path: string) => void
}
// FIXME Remove navigation when backend support comes.
export const createProject = ({ filePath, cloud, navigate, addSnack }: CreateProject) => {
    cloud.put("/projects", { fsRoot: filePath }).then(() => {
        redirectToProject({ path: filePath, cloud, navigate, remainingTries: 5, addSnack });
    }).catch(() => addSnack({ message: `An error occurred creating project ${filePath}`, type: SnackType.Failure }));
};

interface RedirectToProject extends AddSnackOperation {
    path: string
    cloud: Cloud
    navigate: (path: string) => void
    remainingTries: number
}

const redirectToProject = ({ path, cloud, navigate, remainingTries, addSnack }: RedirectToProject) => {
    cloud.get(`/metadata/by-path?path=${encodeURIComponent(path)}`).then(() => navigate(path)).catch(_ => {
        if (remainingTries > 0)
            setTimeout(() => redirectToProject({ path, cloud, navigate, remainingTries: remainingTries - 1, addSnack }), 400);
        else
            addSnack({ message: `Project ${path} is being created.`, type: SnackType.Success });
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
};

export const shortUUID = (uuid: string): string => uuid.substring(0, 8).toUpperCase();
export const is5xxStatusCode = (status: number) => inRange({ status, min: 500, max: 599 });
export const blankOrUndefined = (value?: string): boolean => value == null || value.length == 0 || /^\s*$/.test(value);

export const ifPresent = (f: any, handler: (f: any) => void) => {
    if (f) handler(f)
};

// FIXME The frontend can't handle downloading multiple files currently. When fixed, remove === 1 check.
export const downloadAllowed = (files: File[]) =>
    files.length === 1 && files.every(f => f.sensitivityLevel !== "SENSITIVE");

/**
 * Capizalises the input string and replaces _ (underscores) with whitespace.
 * @param str
 */
export const prettierString = (str: string) => capitalized(str).replace(/_/g, " ");

export function defaultErrorHandler(error: { request: XMLHttpRequest, response: any }, addSnack: (snack: Snack) => void): number {
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

        addSnack({ message: why, type: SnackType.Failure });
        return request.status;
    }
    return 500;
}

export function sortByToPrettierString(sortBy: SortBy): string {
    switch (sortBy) {
        case SortBy.ACL:
            return "Members";
        case SortBy.FILE_TYPE:
            return "File Type";
        case SortBy.CREATED_AT:
            return "Created at";
        case SortBy.MODIFIED_AT:
            return "Modified at";
        case SortBy.PATH:
            return "Path";
        case SortBy.SIZE:
            return "Size";
        case SortBy.SENSITIVITY_LEVEL:
            return "File sensitivity";
        default:
            return prettierString(sortBy);
    }
}

export function requestFullScreen(el: Element, onFailure: () => void) {
    //@ts-ignore
    if (el.webkitRequestFullScreen) el.webkitRequestFullscreen();
    else if (el.requestFullscreen) el.requestFullscreen();
    else onFailure();
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

interface CopyToClipboard extends AddSnackOperation {
    value: string | undefined
    message: string
}

export function copyToClipboard({ value, message, addSnack }: CopyToClipboard) {
    const input = document.createElement("input");
    input.value = value || "";
    document.body.appendChild(input);
    input.select();
    document.execCommand("copy");
    document.body.removeChild(input);
    addSnack({ message, type: SnackType.Success });
}

export function errorMessageOrDefault(err: { request: XMLHttpRequest, response: any } | { status: number, response: string }, defaultMessage: string): string {
    if ("status" in err) {
        return err.response;
    } else {
        if (err.response.why) return err.response.why;
        return HTTP_STATUS_CODES[err.request.status] || defaultMessage;
    }
}

export const inDevEnvironment = () => process.env.NODE_ENV === "development";