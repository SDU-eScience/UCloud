import {useEffect, useState} from "react";
import CONF from "../site.config.json";
import {UPLOAD_LOCALSTORAGE_PREFIX} from "@/Files/ChunkedFileReader";
import {BulkRequest, BulkResponse, PageV2} from "./UCloud";

// HACK(Dan): Breaks a circular dependency between the snackstore and utility functions
let successCallback: (message: string) => void = doNothing;
let failureCallback: (message: string) => void = doNothing;
export function hackySetFailureCallback(callback: (message: string) => void) {
    failureCallback = callback;
}

export function hackySetSuccessCallback(callback: (message: string) => void) {
    successCallback = callback;
}

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
export function setSiteTheme(isLightTheme: boolean): void {
    const lightTheme = isLightTheme ? "light" : "dark";
    toggleCssColors(lightTheme === "light");
    window.localStorage.setItem("theme", lightTheme);
}

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
export function extensionFromPath(path: string): string {
    const splitString = path.split(".");
    return splitString[splitString.length - 1];
}

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

export const commonFileExtensions = [
    "app", "application", "md", "markdown", "markdown", "zig", "swift", "kt", "kts", "js", "jsx", "ts", "tsx",
    "java", "py", "python", "tex", "r", "c", "h", "cc", "hh", "c++", "h++", "hpp", "cpp", "cxx", "jai", "hxx",
    "html", "htm", "lhs", "hs", "sql", "sh", "iol", "ol", "col", "bib", "toc", "jar", "exe", "xml", "json",
    "yml", "ini", "sbatch", "code", "png", "gif", "tiff", "eps", "ppm", "svg", "jpg", "jpeg", "image", "txt",
    "doc", "docx", "log", "csv", "tsv", "plist", "out", "err", "text", "pdf", "pdf", "wav", "mp3", "ogg", "aac",
    "pcm", "audio", "mpg", "mp4", "avi", "mov", "wmv", "video", "gz", "zip", "tar", "tgz", "tbz", "bz2", "archive",
    "dat", "binary", "rs",
];

const languages = {
    "md": "markdown",
    "kt": "kotlin",
    "kts": "kotlin",
    "js": "javascript",
    "jsx": "javascript",
    "ts": "typescript",
    "tsx": "typescript",
    "py": "python",
    "h": "c",
    "cc": "c++",
    "hh": "c++",
    "h++": "c++",
    "hpp": "c++",
    "cpp": "c++",
    "cxx": "c++",
    "hxx": "c++",
    "htm": "html",
    "lhs": "haskell",
    "hs": "haskell",
    "sh": "shell",
    "bib": "tex",
    "yml": "yaml",
    "sbatch": "shell",
    "rs": "rust",
};

export function languageFromExtension(ext: string): string {
    return languages[ext.toLowerCase()] ?? ext.toLowerCase();
}

export function typeFromMime(mimeType: string): ExtensionType {
    const mime = mimeType.toLowerCase();

    if (mime.startsWith("video")) {
        return "video";
    } else if (mime.startsWith("audio")) {
        return "audio";
    } else if (mime.startsWith("image")) {
        return "image";
    } else {
        return null;
    }
}

export function extensionType(ext: string): ExtensionType {
    switch (ext.toLowerCase()) {
        case "app":
            return "application";
        case "md":
        case "markdown":
            return "markdown";
        case "rs":
        case "zig":
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
        case "jai":
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
        case "yaml":
        case "ini":
        case "sbatch":
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
        case "tsv":
        case "plist":
        case "out":
        case "err":
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
}

export function isExtPreviewSupported(ext: string): boolean {
    return extensionType(ext) !== null;
}

/**
 * Calculates if status number is in a given range.
 * @param params: { status, min, max } (both inclusive)
 */
export const inRange = ({status, min, max}: {status: number; min: number; max: number}): boolean =>
    status >= min && status <= max;
export const inSuccessRange = (status: number): boolean => inRange({status, min: 200, max: 299});
export const removeTrailingSlash = (path: string): string => path.endsWith("/") ? path.slice(0, path.length - 1) : path;
export function addTrailingSlash(path: string): string {
    if (!path) return path;
    else return path.endsWith("/") ? path : `${path}/`;
}

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
            if (hasStatus(req)) {
                const status = req.status;
                if (typeof status === "number") {
                    return status;
                }
            }
        }
    }

    return 500;
}

function hasStatus(req: unknown): req is {status: any} {
    return "status" in (req as any);
}

export function extractErrorMessage(
    error: {request: XMLHttpRequest; response: any}
): string {
    const {request} = error;
    let why: string | null = error.response?.why;
    const defaultErrorMessage = "An error occurred. Please reload the page.";

    if (request) {
        if (!why) {
            switch (request.status) {
                case 403:
                    why = "Permission denied";
                    break;
                default:
                    why = defaultErrorMessage;
                    break;
            }
        }

        if (why === "Bad Request") {
            // 'Bad Request' is meaningless for the end user.
            why = defaultErrorMessage;
        }
    }

    return why ?? defaultErrorMessage;
}

export function defaultErrorHandler(
    error: {request: XMLHttpRequest; response: any}
): number {
    const {request} = error;

    if (request) {
        const why = extractErrorMessage(error)
        failureCallback(why);
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
    if (el["webkitRequestFullScreen"]) el["webkitRequestFullScreen"]();
    else if (el.requestFullscreen) el.requestFullscreen();
    else onFailure();
}

export function timestampUnixMs(): number {
    return Math.floor(
        window.performance &&
        window.performance["now"] &&
        window.performance.timing &&
        window.performance.timing.navigationStart ?
        window.performance.now() + window.performance.timing.navigationStart :
        Date.now()
    );
}

/**
 * UNUSED
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
export async function copyToClipboard({value, message}: CopyToClipboard): Promise<void> {
    await navigator.clipboard.writeText(value ?? "");
    if (message) successCallback(message);
}

export function errorMessageOrDefault(
    err: {request: XMLHttpRequest; response: any} | {status: number; response: string} | string | any,
    defaultMessage: string
): string {
    if (!navigator.onLine) return "You seem to be offline.";
    try {
        if (err instanceof Error) return err.toString();
        if (typeof err === "string") return err;
        if ("status" in err) {
            return err.response;
        } else {
            if (err.response.why) return err.response.why;
            return defaultMessage;
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
    || window.location.hostname === "127.0.0.1" || window.location.hostname === "ucloud.localhost.direct"
    || window.location.hostname === "sandbox.dev.cloud.sdu.dk";

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
    failureCallback(errorMessageOrDefault(e, fallback));
}

/**
 * Used to know to hide the header and sidebar in some cases.
 */
export function useFrameHidden(): boolean {
    return [
        "/app/login",
        "/app/login/wayf",
        "/app/applications/shell/",
        "/app/applications/web/",
        "/app/applications/vnc/",
    ].includes(window.location.pathname) ||
    window.location.search === "?dav=true" ||
    window.location.search.indexOf("?hide-frame") === 0;
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

export function b64DecodeUnicode(str: string) {
    // Going backwards: from bytestream, to percent-encoding, to original string.
    return decodeURIComponent(atob(str).split('').map(function (c) {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(''));
}

export function merge<T>(a: T, b: T): T {
    return {...a, ...b};
}


export type EmptyObject = {
    [K in any]: never
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

// TODO(jonas): Might have to be done, more than once (Currently happens on page load).
export function removeExpiredFileUploads(): void {
    const now = new Date().getTime();
    Object.keys(localStorage).forEach(key => {
        if (key.startsWith(`${UPLOAD_LOCALSTORAGE_PREFIX}:`)) {
            const expiration = JSON.parse(localStorage.getItem(key) ?? "{}")?.expiration ?? now
            if (expiration < now) {
                localStorage.removeItem(key);
            }
        }
    });
}

// NOTE(Dan): Generates a random unique identifier. This identifier is suitable for creating something which is
// extremely likely to be unique. The backend should never trust these for security purposes. This function is also
// not guaranteed to be cryptographically secure, but given its implementation it might be.
export function randomUUID(): string {
    const randomUUID = crypto["randomUUID"]
    if (typeof randomUUID === "function") {
        // Does not work in IE
        return (crypto as any).randomUUID();
    } else {
        // Should work in most versions of IE
        // This is a slightly less cryptic version of: https://stackoverflow.com/a/2117523
        return "10000000-1000-4000-8000-100000000000"
            .replace(
                /[018]/g,
                c => ((Number(c) ^ crypto.getRandomValues(new Uint8Array(1))[0] & 15 >> Number(c) / 4).toString(16))
            );
    }
}

export function grantsLink(client: {hasActiveProject: boolean}): string {
    return "/grants";
}

export function clamp(val: number, lower: number, upper: number): number {
    if (val < lower) return lower;
    if (val > upper) return upper;
    return val;
}

// Note(Jonas): Intended to be some HTML friendly replacement to React where needed.
// Not really tested, so attempt at own risk.
interface HTMLElementContent {
    tagType: keyof HTMLElementTagNameMap;
    style?: Partial<CSSStyleDeclaration>;
    innerText?: string;
    dataTags?: [string, string][];
    handlers?: {onClick?: ((this: GlobalEventHandlers, ev: MouseEvent) => any); onChange?: ((this: GlobalEventHandlers, ev: Event) => any) | null;}
    className?: string;
    children?: HTMLElementContent[];
}

export function createHTMLElements<T extends HTMLElement>({children = [], ...rootEntry}: HTMLElementContent): T {
    const root = document.createElement(rootEntry.tagType);
    if (rootEntry.className) root.className = rootEntry.className;
    if (rootEntry.innerText) root.innerText = rootEntry.innerText;
    if (rootEntry.handlers?.onChange) root.onchange = rootEntry.handlers?.onChange;
    if (rootEntry.handlers?.onClick) root.onclick = rootEntry.handlers?.onClick;
    if (rootEntry.dataTags) for (const tag of rootEntry.dataTags) {
        root.setAttribute(tag[0], tag[1]);
    }

    addStyle(root, rootEntry.style);
    for (const child of children) {
        const childElement = createHTMLElements(child);
        root.appendChild(childElement);
    }

    return root as T;

    function addStyle(el: HTMLElement, style?: Partial<CSSStyleDeclaration>) {
        if (!style) return;
        for (const rule of Object.keys(style)) {
            el.style[rule] = style[rule];
        }
    }
}
export function bulkRequestOf<T>(...items: T[]): BulkRequest<T> {
    return {"type": "bulk", items};
}
export function bulkResponseOf<T>(...items: T[]): BulkResponse<T> {
    return {responses: items};
}
export function pageV2Of<T>(...items: T[]): PageV2<T> {
    return {items, itemsPerPage: items.length, next: undefined};
}

export function stringReverse(text: string): string {
    let builder = "";
    const textLength = text.length;
    let i = textLength - 1;
    while (i >= 0) {
        builder += text[i];
        i--;
    }
    return builder;
}

export function chunkedString(text: string, chunkSize: number, leftToRight: boolean): string[] {
    const result: string[] = [];
    if (leftToRight) {
        let cur = "";
        let i = 0;
        let textLength = text.length;
        while (i < textLength) {
            cur += text[i];
            i++;
            if (i % chunkSize === 0) {
                result.push(cur);
                cur = "";
            }
        }
        if (cur != "") result.push(cur);
        return result;
    } else {
        let cur = "";
        let textLength = text.length;
        let i = textLength - 1;
        while (i >= 0) {
            cur += text[i];
            i--;
            if (((textLength - 1) - i) % chunkSize === 0) {
                result.push(stringReverse(cur));
                cur = "";
            }
        }
        if (cur != "") result.push(stringReverse(cur));
        result.reverse();
        return result;
    }
}
