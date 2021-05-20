import {snackbarStore} from "Snackbar/SnackbarStore";
import {Notification} from "Notifications";
import {History} from "history";
import {HTTP_STATUS_CODES} from "Utilities/XHRUtils";
import {ProjectName} from "Project";
import {getStoredProject} from "Project/Redux";
import {JWT} from "Authentication/lib";
import {useGlobal} from "Utilities/ReduxHooks";
import {useEffect, useState} from "react";
import CONF from "../site.config.json";

/**
 * Toggles CSS classes to use dark theme.
 * @param light the theme to be changed to.
 */
export function toggleCssColors(light: boolean): void {
    const html = document.querySelector("html")!;
    if (light) {
        html.classList.remove("dark");
        html.classList.add("light");
    } else {
        html.classList.remove("light");
        html.classList.add("dark");
    }
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
 * Capitalizes the input string.
 * @param str string to be capitalized
 * @return {string}
 */
export const capitalized = (str: string): string => str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();

export const extensionTypeFromPath = (path: string): ExtensionType => extensionType(extensionFromPath(path));
export const extensionFromPath = (path: string): string => {
    const splitString = path.split(".");
    return splitString[splitString.length - 1];
};

export type ExtensionType =
    | null
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
        case "log":
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

export function isExtPreviewSupported(ext: string): boolean {
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
        case "log":
        case "f":
        case "for":
        case "f90":
        case "f95":
        case "ini":
            return true;
        default:
            return false;
    }
}

/**
 * Calculates if status number is in a given range.
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

/**
 * Capitalizes the input string and replaces _ (underscores) with whitespace.
 * @param str input string.
 */
export const prettierString = (str: string): string => capitalized(str).replace(/_/g, " ");

export function extractErrorCode(e: unknown): number {
    if (typeof e === "object") {
        if (e != null && "request" in e) {
            const req = e["request"];
            if ("status" in req) {
                const status = req.status;
                if (typeof status === "number") {
                    return status;
                }
            }
        }
    }

    return 500;
}

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

/**
 * Requests full screen on an HTML element. Handles both Safari fullscreen, and Chrome/Firefox.
 * @param el The element to be fullscreened.
 * @param onFailure Method called if fullscreen can't be done.
 */
export function requestFullScreen(el: Element, onFailure: () => void): void {
    // @ts-expect-error - Handles the fullscreen request for Safari.
    if (el.webkitRequestFullScreen) el.webkitRequestFullScreen();
    else if (el.requestFullscreen) el.requestFullscreen();
    else onFailure();
}

export function timestampUnixMs(): number {
    return window.performance &&
        window.performance["now"] &&
        window.performance.timing &&
        window.performance.timing.navigationStart ?
        window.performance.now() + window.performance.timing.navigationStart :
        Date.now();
}

/**
 * Used to format numbers to a more human readable number by dividing it up by thousands and using custom delimiters.
 * @param value numerical value to be formatted.
 * @param sectionDelim used for deliminate every thousand. Default: ,
 * @param decimalDelim used to deliminate the decimals. Default: .
 * @param numDecimals number of decimals in the formatted number. Default: 2
 */
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

/**
 * Copies a string to the users clipboard.
 * @param param contains the value to be copied and the message to show the user on success.
 */
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
    err: {request: XMLHttpRequest; response: any} | {status: number; response: string} | string,
    defaultMessage: string
): string {
    if (!navigator.onLine) return "You seem to be offline.";
    try {
        if (typeof err === "string") return err;
        if ("status" in err) {
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

/**
 * A function used to check if the DEVELOPMENT_ENV variable is set to true. Used to block features that are in progress,
 * even if the code may be deployed on production.
 */
export const inDevEnvironment = (): boolean => DEVELOPMENT_ENV;
export const onDevSite = (): boolean => window.location.host === CONF.DEV_SITE || window.location.hostname === "localhost"
    || window.location.hostname === "127.0.0.1";

export const generateId = ((): (target: string) => string => {
    const store = new Map<string, number>();
    return (target = "default-target"): string => {
        const idCount = (store.get(target) ?? 0) + 1;
        store.set(target, idCount);
        return `${target}${idCount}`;
    };
})();

/**
 * Helper function intended for use in cases: `e => e.stopPropagation` so one instead can write `stopPropagation`.
 * @param e Event object to stop propagation for.
 */
export function stopPropagation(e: {stopPropagation(): void}): void {
    e.stopPropagation();
}

/**
 * Helper function intended for use in cases: `e => e.preventDefault` so one instead can write `preventDefault`.
 * @param e Event object to preventDefault for.
 */
export function preventDefault(e: {preventDefault(): void}): void {
    e.preventDefault();
}

/**
 * A no op function.
 */
export function doNothing(): void {
    return undefined;
}

/**
 * Calls both stopProgation and preventDefault for
 * @param e to stop propagation and preventdefault for.
 */
export function stopPropagationAndPreventDefault(e: {preventDefault(): void; stopPropagation(): void}): void {
    preventDefault(e);
    stopPropagation(e);
}

export function displayErrorMessageOrDefault(e: any, fallback: string): void {
    snackbarStore.addFailure(errorMessageOrDefault(e, fallback), false);
}

/**
 * Used to know to hide the header and sidebar in some cases.
 */
export function useFrameHidden(): boolean {
    const [frameHidden] = useGlobal("frameHidden", false);
    const legacyHide =
        ["/app/login", "/app/login/wayf", "/app/login/selection"].includes(window.location.pathname) ||
        window.location.search === "?dav=true" ||
        window.location.search === "?hide-frame";
    return legacyHide || frameHidden;
}

export function useNoFrame(): void {
    const [frameHidden, setFrameHidden] = useGlobal("frameHidden", false);
    useEffect(() => {
        const wasFrameHidden = frameHidden;
        setFrameHidden(true);
        return () => {
            setFrameHidden(wasFrameHidden);
        };
    }, []);
}

/**
 * Matches the CSS rule to see if the user's system uses a dark theme.
 * Used to set the theme variable if the user has not explicitely set the theme on the site.
 */
export function getUserThemePreference(): "light" | "dark" {
    // options: dark, light and no-preference
    if (window.matchMedia("(prefers-color-scheme: dark)").matches) return "dark";
    return "light";
}

/**
 * Used when a user clicks a notification to handle what happens based on the notification type.
 * @param history used for redirection in some cases.
 * @param setProject used for setting the active project for some cases.
 * @param notification the notification that is being handled.
 * @param projectNames existing project names, used to map id to shown project name.
 * @param markAsRead used to mark the notification as read.
 */
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
        case "GRANT_APPLICATION_UPDATED": {
            const {meta} = notification;
            history.push(`/project/grants/view/${meta.appId}`);
            break;
        }
        case "PROJECT_ROLE_CHANGE": {
            const {projectId} = notification.meta;
            if (currentProject !== projectId) {
                setProject(projectId);
                const projectName = projectNames.find(it => it.projectId === projectId)?.title ?? projectId;
                snackbarStore.addInformation(`${projectName} is now active.`, false);
            }
            history.push("/project/members");
            break;
        }
        default:
            console.warn("unhandled");
            console.warn(notification);
            break;
    }
    markAsRead(notification.id);
}

function b64DecodeUnicode(str) {
    // Going backwards: from bytestream, to percent-encoding, to original string.
    return decodeURIComponent(atob(str).split('').map(function(c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));
}


/**
 * Used to parse and validate the structure of the JWT. If the JWT is invalid, the function returns null, otherwise as
 * a an object.
 * @param encodedJWT The JWT sent from the backend, in the form of a string.
 */
export function parseJWT(encodedJWT: string): JWT | null {
    const [, right] = encodedJWT.split(".");
    if (right == null) return null;

    const decoded = b64DecodeUnicode(right);
    const parsed = JSON.parse(decoded);
    const isValid = "sub" in parsed &&
        "uid" in parsed &&
        "aud" in parsed &&
        "role" in parsed &&
        "iss" in parsed &&
        "exp" in parsed &&
        "extendedByChain" in parsed &&
        "iat" in parsed &&
        "principalType" in parsed;
    if (!isValid) return null;

    return parsed;
}

export type PropType<TObj, TProp extends keyof TObj> = TObj[TProp];
export type GetElementType<T extends Array<any>> = T extends (infer U)[] ? U : never;
export type GetArrayReturnType<T> = T extends () => (infer U)[] ? U : never;

export function joinToString(elements: string[], separator = ","): string {
    let result = "";
    let first = true;
    for (const tag of elements) {
        if (!first) result += separator;
        else first = false;
        result += tag;
    }
    return result;
}

export function useDidMount(): boolean {
    const [didMount, setDidMount] = useState(false);
    useEffect(() => {
        setDidMount(true);
    });
    return didMount;
}

export function useEffectSkipMount(fn: () => (void | (() => void | undefined)), deps: ReadonlyArray<any>): void {
    const didMount = useDidMount();
    useEffect(() => {
        if (didMount) {
            return fn();
        }
    }, deps);
}

export function isAbsoluteUrl(url: string): boolean {
    return url.indexOf("http://") === 0 || url.indexOf("https://") === 0 ||
        url.indexOf("ws://") === 0 || url.indexOf("wss://") === 0;
}