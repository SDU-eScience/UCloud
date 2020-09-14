import {Client as currentClient} from "Authentication/HttpClientInstance";
import {Acl, File, FileType, SortBy, UserEntity} from "Files";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Notification} from "Notifications";
import {History} from "history";
import {
    getFilenameFromPath, isFavoritesFolder, isJobsFolder, isMyPersonalFolder, isPersonalRootFolder,
    isSharesFolder, isTrashFolder
} from "Utilities/FileUtilities";
import {HTTP_STATUS_CODES} from "Utilities/XHRUtils";
import {ProjectName} from "Project";
import {getStoredProject} from "Project/Redux";
import {APIError} from "Authentication/DataHook";

export function toggleCssColors(light: boolean): void {
    if (light) {
        setCSSVariable("--white", "#fff");
        setCSSVariable("--tableRowHighlight", "var(--lightBlue, #f00)");
        setCSSVariable("--black", "#000");
        setCSSVariable("--text", "#1e252e");
        setCSSVariable("--lightGray", "#f5f7f9");
        setCSSVariable("--lightBlue", "#f0f6ff");
        setCSSVariable("--midGray", "#c9d3df");
        setCSSVariable("--paginationDisabled", "var(--lightGray, #f00)");
        setCSSVariable("--paginationHoverColor", "var(--lightBlue, #f00)");
        setCSSVariable("--appCard", "#ebeff3");
        setCSSVariable("--borderGray", "var(--midGray, #f00)");
        setCSSVariable("--invertedThemeColor", "#000");
    } else {
        setCSSVariable("--white", "#282c35");
        setCSSVariable("--tableRowHighlight", "#000");
        setCSSVariable("--black", "#a4a5a9");
        setCSSVariable("--text", "#e5e5e6");
        setCSSVariable("--lightGray", "#111");
        setCSSVariable("--lightBlue", "#000");
        setCSSVariable("--midGray", "#555");
        setCSSVariable("--paginationDisabled", "#111");
        setCSSVariable("--paginationHoverColor", "#444");
        setCSSVariable("--appCard", "#060707");
        setCSSVariable("--borderGray", "#111");
        setCSSVariable("--invertedThemeColor", "#fff");
    }
}

function setCSSVariable(varName: string, value: string): void {
    document.documentElement.style.setProperty(varName, value);
}

/**
 * Sets theme based in input. Either "light" or "dark".
 * @param {boolean} isLightTheme Signifies if the currently selected theme is "light".
 */
export const setSiteTheme = (isLightTheme: boolean): void => {
    const lightTheme = isLightTheme ? "light" : "dark";
    toggleCssColors(lightTheme === "light");
    window.localStorage.setItem("theme", lightTheme);
};

/**
 * Returns whether the value "light" or "dark" is stored.
 * If neither are, the OS theme preference is used.
 * @returns {boolean} True if "light" or null is stored, otherwise "dark".
 */
export function isLightThemeStored(): boolean {
    const theme = window.localStorage.getItem("theme");
    if (theme == null) return getUserThemePreference() === "light";
    return theme === "light";
}

/**
 * Capitalizes the input string
 * @param str string to be capitalized
 * @return {string}
 */
export const capitalized = (str: string): string => str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();

/**
 * Returns a string based on the amount of users associated with the ACL
 * @param {Acl[]} acls - the list of access controls
 * @return {string}
 */
export const getMembersString = (acls: Acl[]): string => {
    const withoutProjectAcls = acls.filter(it => typeof it.entity === "string" || "username" in it.entity);
    const filteredAcl = withoutProjectAcls
        .filter(it => (it.entity as UserEntity).username !== currentClient.activeUsername);
    if (filteredAcl.length > 0) {
        return `${acls.length + 1} members`;
    } else {
        return "Only You";
    }
};

export const extensionTypeFromPath = (path: string): ExtensionType => extensionType(extensionFromPath(path));
export const extensionFromPath = (path: string): string => {
    const splitString = path.split(".");
    return splitString[splitString.length - 1];
};

export type ExtensionType =
    null
    | "code"
    | "image"
    | "text"
    | "audio"
    | "video"
    | "archive"
    | "pdf"
    | "binary"
    | "markdown"
    | "application";

export const extensionType = (ext: string): ExtensionType => {
    switch (ext.toLowerCase()) {
        case "app":
            return "application";
        case "md":
        case "markdown":
            return "markdown";
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
        case "htm":
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
        case "xml":
        case "json":
        case "yml":
            return "code";
        case "png":
        case "gif":
        case "tiff":
        case "eps":
        case "ppm":
        case "svg":
        case "jpg":
        case "jpeg":
            return "image";
        case "txt":
        case "doc":
        case "docx":
        case "csv":
        case "plist":
            return "text";
        case "pdf":
            return "pdf";
        case "wav":
        case "mp3":
        case "ogg":
        case "aac":
        case "pcm":
            return "audio";
        case "mpg":
        case "mp4":
        case "avi":
        case "mov":
        case "wmv":
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

export const isExtPreviewSupported = (ext: string): boolean => {
    switch (ext.toLowerCase()) {
        case "app":
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
        case "png":
        case "gif":
        case "tiff":
        case "eps":
        case "ppm":
        case "svg":
        case "jpg":
        case "txt":
        case "xml":
        case "json":
        case "csv":
        case "yml":
        case "yaml":
        case "plist":
        case "pdf":
        case "wav":
        case "mp3":
        case "ogg":
        case "aac":
        case "pcm":
        case "mp4":
        case "mov":
        case "wmv":
            return true;
        default:
            return false;
    }
};

export interface FtIconProps {
    type: FileType;
    ext?: string;
    name?: string;
}

export const iconFromFilePath = (
    filePath: string,
    type: FileType
): FtIconProps => {
    const icon: FtIconProps = {type: "FILE", name: getFilenameFromPath(filePath, [])};

    switch (type) {
        case "DIRECTORY":
            if (isSharesFolder(filePath)) {
                icon.type = "SHARESFOLDER";
            } else if (isTrashFolder(filePath)) {
                icon.type = "TRASHFOLDER";
            } else if (isJobsFolder(filePath)) {
                icon.type = "RESULTFOLDER";
            } else if (isFavoritesFolder(filePath)) {
                icon.type = "FAVFOLDER";
            } else if (isMyPersonalFolder(filePath)) {
                icon.type = "SHARESFOLDER";
            } else if (isPersonalRootFolder(filePath)) {
                icon.type = "SHARESFOLDER";
            } else {
                icon.type = "DIRECTORY";
            }

            return icon;

        case "FILE":
        default:
            const filename = getFilenameFromPath(filePath, []);
            if (!filename.includes(".")) {
                return icon;
            }
            icon.ext = extensionFromPath(filePath);

            return icon;
    }
};

/**
 *
 * @param params: { status, min, max } (both inclusive)
 */
export const inRange = ({status, min, max}: {status: number; min: number; max: number}): boolean =>
    status >= min && status <= max;
export const inSuccessRange = (status: number): boolean => inRange({status, min: 200, max: 299});
export const removeTrailingSlash = (path: string): string => path.endsWith("/") ? path.slice(0, path.length - 1) : path;
export const addTrailingSlash = (path: string): string => {
    if (!path) return path;
    else return path.endsWith("/") ? path : `${path}/`;
};

export const looksLikeUUID = (uuid: string): boolean =>
    /\b[0-9a-f]{8}\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\b[0-9a-f]{12}\b/.test(uuid);
export const shortUUID = (uuid: string): string => looksLikeUUID(uuid) ? uuid.substring(0, 8).toUpperCase() : uuid;
export const is5xxStatusCode = (status: number): boolean => inRange({status, min: 500, max: 599});
export const blankOrUndefined = (value?: string): boolean => value == null || value.length === 0 || /^\s*$/.test(value);

export function ifPresent<T>(f: T | undefined, handler: (f: T) => void): void {
    if (f) handler(f);
}

export const downloadAllowed = (files: File[]): boolean => files.every(f => f.sensitivityLevel !== "SENSITIVE");

/**
 * Capitalizes the input string and replaces _ (underscores) with whitespace.
 * @param str
 */
export const prettierString = (str: string): string => capitalized(str).replace(/_/g, " ");

export function defaultErrorHandler(
    error: {request: XMLHttpRequest; response: any}
): number {
    const {request} = error;
    // FIXME must be solvable more elegantly
    let why: string | null = error.response?.why;

    if (request) {
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

        snackbarStore.addFailure(why, false);
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
        case SortBy.MODIFIED_AT:
            return "Modified at";
        case SortBy.PATH:
            return "Filename";
        case SortBy.SIZE:
            return "Size";
        case SortBy.SENSITIVITY_LEVEL:
            return "File sensitivity";
        default:
            return prettierString(sortBy);
    }
}

export function requestFullScreen(el: Element, onFailure: () => void): void {
    // @ts-ignore
    if (el.webkitRequestFullScreen) el.webkitRequestFullScreen();
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
    value: number,
    sectionDelim = ",",
    decimalDelim = ".",
    numDecimals = 2
): string {
    const regex = new RegExp("\\d(?=(\\d{3})+" + (numDecimals > 0 ? "\\D" : "$") + ")", "g");
    const fixedNumber = value.toFixed(numDecimals);

    return fixedNumber
        .replace(".", decimalDelim)
        .replace(regex, "$&" + sectionDelim);
}

interface CopyToClipboard {
    value: string | undefined;
    message: string;
}

export function copyToClipboard({value, message}: CopyToClipboard): void {
    const input = document.createElement("input");
    input.value = value ?? "";
    document.body.appendChild(input);
    input.select();
    document.execCommand("copy");
    document.body.removeChild(input);
    snackbarStore.addSuccess(message, true);
}

export function errorMessageOrDefault(
    err: {request: XMLHttpRequest; response: any} | {status: number; response: string} | APIError | string,
    defaultMessage: string
): string {
    if (!navigator.onLine) return "You appear to be offline.";
    try {
        if (typeof err === "string") {
            return err;
        } else if ("why" in err) {
            return err.why;
        } else if ("status" in err) {
            return err.response;
        } else {
            if (err.response.why) return err.response.why;
            return HTTP_STATUS_CODES[err.request.status] ?? defaultMessage;
        }
    } catch {
        return defaultMessage;
    }
}

export function delay(ms: number): Promise<void> {
    return new Promise<void>((resolve) => {
        setTimeout(() => resolve(), ms);
    });
}

export const inDevEnvironment = (): boolean => process.env.NODE_ENV === "development";

export const generateId = ((): (target: string) => string => {
    const store = new Map<string, number>();
    return (target = "default-target"): string => {
        const idCount = (store.get(target) ?? 0) + 1;
        store.set(target, idCount);
        return `${target}${idCount}`;
    };
})();

export function stopPropagation(e: {stopPropagation(): void}): void {
    e.stopPropagation();
}

export function preventDefault(e: {preventDefault(): void}): void {
    e.preventDefault();
}

export function doNothing(): void {
    return undefined;
}

export function stopPropagationAndPreventDefault(e: {preventDefault(): void; stopPropagation(): void}): void {
    preventDefault(e);
    stopPropagation(e);
}

export function displayErrorMessageOrDefault(e: any, fallback: string): void {
    snackbarStore.addFailure(errorMessageOrDefault(e, fallback), false);
}

export function shouldHideSidebarAndHeader(): boolean {
    return ["/app/login", "/app/login/wayf"]
        .includes(window.location.pathname) && window.location.search === "?dav=true";
}

export function getUserThemePreference(): "light" | "dark" {
    // options: dark, light and no-preference
    if (window.matchMedia("(prefers-color-scheme: dark)").matches) return "dark";
    return "light";
}


export function onNotificationAction(
    history: History,
    setProject: (projectId: string) => void,
    notification: Notification,
    projectNames: ProjectName[],
    markAsRead: (id: number) => void
): void {
    const currentProject = getStoredProject();
    switch (notification.type) {
        case "APP_COMPLETE":
            history.push(`/applications/results/${notification.meta.jobId}`);
            break;
        case "SHARE_REQUEST":
            history.push("/shares");
            break;
        case "REVIEW_PROJECT":
        case "PROJECT_INVITE":
            history.push("/projects/");
            break;
        case "NEW_GRANT_APPLICATION":
        case "COMMENT_GRANT_APPLICATION":
        case "GRANT_APPLICATION_RESPONSE":
        case "GRANT_APPLICATION_UPDATED":
            const {meta} = notification;
            history.push(`/project/grants/view/${meta.appId}`);
            break;
        case "PROJECT_ROLE_CHANGE":
            const {projectId} = notification.meta;
            if (currentProject !== projectId) {
                setProject(projectId);
                const projectName = projectNames.find(it => it.projectId === projectId)?.title ?? projectId;
                snackbarStore.addInformation(`${projectName} is now active.`, false);
            }
            history.push("/project/members");
            break;
        default:
            console.warn("unhandled");
            console.warn(notification);
            break;
    }
    markAsRead(notification.id);
}
