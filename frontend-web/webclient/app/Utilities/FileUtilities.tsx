import SDUCloud from "Authentication/lib";
import {Cloud} from "Authentication/SDUCloudObject";
import {SensitivityLevelMap} from "DefaultObjects";
import {dialogStore} from "Dialog/DialogStore";
import {File, FileResource, FileType, SortBy, SortOrder} from "Files";
import {SnackType} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Page} from "Types";
import {UploadPolicy} from "Uploader/api";
import {addStandardDialog, rewritePolicyDialog, sensitivityDialog, shareDialog} from "UtilityComponents";
import * as UF from "UtilityFunctions";
import {defaultErrorHandler} from "UtilityFunctions";
import {ErrorMessage, isError, unwrap} from "./XHRUtils";

function getNewPath(newParentPath: string, currentPath: string): string {
    return `${UF.removeTrailingSlash(resolvePath(newParentPath))}/${getFilenameFromPath(resolvePath(currentPath))}`;
}

export enum CopyOrMove {
    Move,
    Copy
}

export async function copyOrMoveFilesNew(operation: CopyOrMove, files: File[], targetPathFolder: string) {
    const copyOrMoveQuery = operation === CopyOrMove.Copy ? copyFileQuery : moveFileQuery;
    let successes = 0;
    let failures = 0;
    const failurePaths: string[] = [];
    let applyToAll = false;
    let policy = UploadPolicy.REJECT;
    let allowRewrite = false;

    for (let i = 0; i < files.length; i++) {
        const f = files[i];
        const {exists, newPathForFile, allowOverwrite} = await moveCopySetup({
            targetPath: targetPathFolder,
            path: f.path,
            cloud: Cloud
        });
        if (exists && !applyToAll) {
            const result = await rewritePolicyDialog({
                path: newPathForFile,
                homeFolder: Cloud.homeFolder,
                filesRemaining: files.length - i,
                allowOverwrite
            });
            if (result !== false) {
                allowRewrite = !!result.policy;
                policy = result.policy as UploadPolicy;
                if (files.length - i > 1) applyToAll = result.applyToAll;
            }
        }
        if (applyToAll) allowRewrite = true;
        if ((exists && allowRewrite) || !exists) {
            try {
                const {request} = await Cloud.post(copyOrMoveQuery(f.path, newPathForFile, policy));
                successes++;
                if (request.status === 202) snackbarStore.addSnack({
                    message: `Operation for ${f.path} is in progress.`,
                    type: SnackType.Success
                });
            } catch {
                failures++;
                failurePaths.push(getFilenameFromPath(f.path));
            }
        }
    }

    if (!failures && successes) {
        onOnlySuccess({operation: operation === CopyOrMove.Copy ? "Copied" : "Moved", fileCount: files.length});
    } else if (failures) {
        snackbarStore.addFailure(
            `Failed to ${operation === CopyOrMove.Copy ? "copy" : "move"} files: ${failurePaths.join(", ")}`
        );
    }
}

interface MoveCopySetup {
    targetPath: string;
    path: string;
    cloud: SDUCloud;
}

async function moveCopySetup({targetPath, path, cloud}: MoveCopySetup) {
    const newPathForFile = getNewPath(targetPath, path);
    const stat = await statFileOrNull(newPathForFile);
    return {exists: stat !== null, newPathForFile, allowOverwrite: stat ? stat.fileType !== "DIRECTORY" : true};
}

function onOnlySuccess({operation, fileCount}: { operation: string, fileCount: number }): void {
    snackbarStore.addSnack({message: `${operation} ${fileCount} files`, type: SnackType.Success});
}

export const statFileOrNull = async (path: string): Promise<File | null> => {
    try {
        return (await Cloud.get<File>(statFileQuery(path))).response;
    } catch (e) {
        return null;
    }
};

export const checkIfFileExists = async (path: string, cloud: SDUCloud): Promise<boolean> => {
    try {
        await cloud.get(statFileQuery(path));
        return true;
    } catch (e) {
        // FIXME: in the event of other than 404
        return !(e.request.status === 404);
    }
};

export type AccessRight = "READ" | "WRITE";

function hasAccess(accessRight: AccessRight, file: File) {
    const username = Cloud.activeUsername;
    if (file.ownerName === username) return true;
    if (file.acl === null) return true; // If ACL is null, we are still fetching the ACL

    const relevantEntries = file.acl.filter(item => !item.group && item.entity === username);
    return relevantEntries.some(entry => entry.rights.includes(accessRight));
}

export const allFilesHasAccessRight = (accessRight: AccessRight, files: File[]) =>
    files.every(f => hasAccess(accessRight, f));

export function mergeFilePages(basePage: Page<File>, additionalPage: Page<File>, attributesToCopy: FileResource[]) {
    const items = basePage.items.map(base => {
        const additionalFile = additionalPage.items.find(it => it.fileId === base.fileId);
        if (additionalFile !== undefined) {
            return mergeFile(base, additionalFile, attributesToCopy);
        } else {
            return base;
        }
    });

    return {...basePage, items};
}

export function mergeFile(base: File, additional: File, attributesToCopy: FileResource[]): File {
    const result: File = {...base};
    attributesToCopy.forEach(attr => {
        switch (attr) {
            case FileResource.FAVORITED:
                result.favorited = additional.favorited;
                break;
            case FileResource.FILE_TYPE:
                result.fileType = additional.fileType;
                break;
            case FileResource.PATH:
                result.path = additional.path;
                break;
            case FileResource.CREATED_AT:
                result.createdAt = additional.createdAt;
                break;
            case FileResource.MODIFIED_AT:
                result.modifiedAt = additional.modifiedAt;
                break;
            case FileResource.OWNER_NAME:
                result.ownerName = additional.ownerName;
                break;
            case FileResource.SIZE:
                result.size = additional.size;
                break;
            case FileResource.ACL:
                result.acl = additional.acl;
                break;
            case FileResource.SENSITIVITY_LEVEL:
                result.sensitivityLevel = additional.sensitivityLevel;
                break;
            case FileResource.OWN_SENSITIVITY_LEVEL:
                result.ownSensitivityLevel = additional.ownSensitivityLevel;
                break;
            case FileResource.FILE_ID:
                result.fileId = additional.fileId;
                break;
            case FileResource.CREATOR:
                result.creator = additional.creator;
                break;
        }
    });
    return result;
}

/**
 * Used for resolving paths, which contain either "." or "..", and returning the resolved path.
 * @param path The current input path, which can include relative paths
 */
export function resolvePath(path: string) {
    const components = path.split("/");
    const result: string[] = [];
    components.forEach(it => {
        if (it === "") {
            return;
        } else if (it === ".") {
            return;
        } else if (it === "..") {
            result.pop();
        } else {
            result.push(it);
        }
    });
    return "/" + result.join("/");
}

const toAttributesString = (attrs: FileResource[]) =>
    attrs.length > 0 ? `&attributes=${encodeURIComponent(attrs.join(","))}` : "";

export const filepathQuery = (path: string, page: number, itemsPerPage: number, order: SortOrder = SortOrder.ASCENDING, sortBy: SortBy = SortBy.PATH, attrs: FileResource[] = []): string =>
    `files?path=${encodeURIComponent(resolvePath(path))}&itemsPerPage=${itemsPerPage}&page=${page}&order=${encodeURIComponent(order)}&sortBy=${encodeURIComponent(sortBy)}${toAttributesString(attrs)}`;

export const fileLookupQuery = (path: string, itemsPerPage: number = 25, order: SortOrder = SortOrder.DESCENDING, sortBy: SortBy = SortBy.PATH, attrs: FileResource[]): string =>
    `files/lookup?path=${encodeURIComponent(resolvePath(path))}&itemsPerPage=${itemsPerPage}&order=${encodeURIComponent(order)}&sortBy=${encodeURIComponent(sortBy)}${toAttributesString(attrs)}`;

export const advancedFileSearch = "/file-search/advanced";

export const recentFilesQuery = "/files/stats/recent";

export function moveFileQuery(path: string, newPath: string, policy?: UploadPolicy): string {
    let query = `/files/move?path=${encodeURIComponent(resolvePath(path))}&newPath=${encodeURIComponent(newPath)}`;
    if (policy) query += `&policy=${policy}`;
    return query;
}

export function copyFileQuery(path: string, newPath: string, policy: UploadPolicy): string {
    let query = `/files/copy?path=${encodeURIComponent(resolvePath(path))}&newPath=${encodeURIComponent(newPath)}`;
    if (policy) query += `&policy=${policy}`;
    return query;
}

export const statFileQuery = (path: string): string => `/files/stat?path=${encodeURIComponent(path)}`;
export const favoritesQuery = (page: number = 0, itemsPerPage: number = 25): string =>
    `/files/favorite?page=${page}&itemsPerPage=${itemsPerPage}`;

export const MOCK_RENAME_TAG = "rename";
export const MOCK_VIRTUAL = "virtual";
export const MOCK_RELATIVE = "relative";

export function mockFile(props: { path: string, type: FileType, fileId?: string, tag?: string }): File {
    const username = Cloud.activeUsername ? Cloud.activeUsername : "";
    return {
        fileType: props.type,
        path: props.path,
        creator: username,
        ownerName: username,
        createdAt: new Date().getTime(),
        modifiedAt: new Date().getTime(),
        size: 0,
        acl: [],
        favorited: false,
        sensitivityLevel: SensitivityLevelMap.PRIVATE,
        fileId: props.fileId ? props.fileId : "fileId" + new Date(),
        ownSensitivityLevel: null,
        mockTag: props.tag
    };
}

interface IsInvalidPathname {
    path: string;
    filePaths: string[];
}

/**
 * Checks if a pathname is legal/already in use
 * @param {string} path The path being tested
 * @param {string[]} filePaths the other file paths path is being compared against
 * @param {() => void} addSnack used to add a message to SnackBar
 * @returns whether or not the path is invalid
 */
export const isInvalidPathName = ({path, filePaths}: IsInvalidPathname): boolean => {
    if (["..", "/"].some((it) => path.includes(it))) {
        snackbarStore.addSnack({message: "Folder name cannot contain '..' or '/'", type: SnackType.Failure});
        return true;
    }
    if (path === "" || path === ".") {
        snackbarStore.addSnack({message: "Folder name cannot be empty or be \".\"", type: SnackType.Failure});
        return true;
    }
    const existingName = filePaths.some(it => it === path);
    if (existingName) {
        snackbarStore.addSnack({message: "File with that name already exists", type: SnackType.Failure});
        return true;
    }
    return false;
};

/**
 * Checks if the specific folder is a fixed folder, meaning it can not be removed, renamed, deleted, etc.
 * @param {string} filePath the path of the file to be checked
 * @param {string} homeFolder the path for the homefolder of the current user
 */
export const isFixedFolder = (filePath: string, homeFolder: string): boolean => {
    return [ // homeFolder contains trailing slash
        `${homeFolder}Favorites`,
        `${homeFolder}Jobs`,
        `${homeFolder}Trash`
    ].some(it => UF.removeTrailingSlash(it) === filePath);
};

/**
 * Used to favorite/defavorite a file based on its current state.
 * @param {File} file The single file to be favorited
 * @param {Cloud} cloud The cloud instance used to changed the favorite state for the file
 */
export const favoriteFile = async (file: File, cloud: SDUCloud): Promise<File> => {
    try {
        await cloud.post(favoriteFileQuery(file.path), {})
    } catch (e) {
        UF.errorMessageOrDefault(e, "An error occurred favoriting file.");
        throw e;
    }
    file.favorited = !file.favorited;
    return file;
};

const favoriteFileQuery = (path: string) => `/files/favorite?path=${encodeURIComponent(path)}`;

interface ReclassifyFile {
    file: File
    sensitivity: SensitivityLevelMap
    cloud: SDUCloud
}

export const reclassifyFile = async ({file, sensitivity, cloud}: ReclassifyFile): Promise<File> => {
    const serializedSensitivity = sensitivity === SensitivityLevelMap.INHERIT ? null : sensitivity;
    const callResult = await unwrap(cloud.post<void>("/files/reclassify", {
        path: file.path,
        sensitivity: serializedSensitivity
    }));
    if (isError(callResult)) {
        snackbarStore.addSnack({message: (callResult as ErrorMessage).errorMessage, type: SnackType.Failure});
        return file;
    }
    return {...file, sensitivityLevel: sensitivity, ownSensitivityLevel: sensitivity};
};

export const toFileText = (selectedFiles: File[]): string =>
    `${selectedFiles.length} file${selectedFiles.length > 1 ? "s" : ""} selected`;

export const isDirectory = (file: { fileType: FileType }): boolean => file.fileType === "DIRECTORY";
export const replaceHomeFolder = (path: string, homeFolder: string): string => path.replace(homeFolder, "Home/");
export const expandHomeFolder = (path: string, homeFolder: string): string => {
    if (path.startsWith("/Home/"))
        return path.replace("/Home/", homeFolder);
    return path;
};

const extractFilesQuery = "/files/extract";

interface ExtractArchive {
    files: File[];
    cloud: SDUCloud;
    onFinished: () => void;
}

export const extractArchive = async ({files, cloud, onFinished}: ExtractArchive) => {
    for (const f of files) {
        try {
            await cloud.post(extractFilesQuery, {path: f.path});
            snackbarStore.addSnack({message: "File extracted", type: SnackType.Success});
        } catch (e) {
            snackbarStore.addSnack({
                message: UF.errorMessageOrDefault(e, "An error occurred extracting the file."),
                type: SnackType.Failure
            });
        }
    }
    onFinished();
};

export const clearTrash = ({cloud, callback}: { cloud: SDUCloud, callback: () => void }) =>
    clearTrashDialog({
        onConfirm: async () => {
            await cloud.post("/files/trash/clear", {});
            callback();
            dialogStore.success();
        }
    });

export const getParentPath = (path: string): string => {
    if (path.length === 0) return path;
    let splitPath = path.split("/");
    splitPath = splitPath.filter(p => p);
    let parentPath = "/";
    for (let i = 0; i < splitPath.length - 1; i++) {
        parentPath += splitPath[i] + "/";
    }
    return parentPath;
};

const goUpDirectory = (
    count: number,
    path: string
): string => count ? goUpDirectory(count - 1, getParentPath(path)) : path;

const toFileName = (path: string): string => {
    const lastSlash = path.lastIndexOf("/");
    if (lastSlash !== -1 && path.length > lastSlash + 1) {
        return path.substring(lastSlash + 1);
    } else {
        return path;
    }
};

export function getFilenameFromPath(path: string): string {
    const replacedHome = replaceHomeFolder(path, Cloud.homeFolder);
    const fileName = toFileName(replacedHome);
    if (fileName === "..") return `.. (${toFileName(goUpDirectory(2, replacedHome))})`;
    if (fileName === ".") return `. (Current folder)`;
    return fileName;
}

export function downloadFiles(files: File[], setLoading: () => void, cloud: SDUCloud) {
    files.map(f => f.path).forEach(p =>
        cloud.createOneTimeTokenWithPermission("files.download:read").then((token: string) => {
            const element = document.createElement("a");
            element.setAttribute(
                "href",
                Cloud.computeURL(
                    "/api",
                    `/files/download?path=${encodeURIComponent(p)}&token=${encodeURIComponent(token)}`
                )
            );
            element.style.display = "none";
            document.body.appendChild(element);
            element.click();
            document.body.removeChild(element);
        }));
}

interface UpdateSensitivity {
    files: File[];
    cloud: SDUCloud;
    onSensitivityChange?: () => void;
}

export async function updateSensitivity({files, cloud, onSensitivityChange}: UpdateSensitivity) {
    const input = await sensitivityDialog();
    if ("cancelled" in input) return;
    try {
        await Promise.all(files.map(file => reclassifyFile({file, sensitivity: input.option, cloud})));
    } catch (e) {
        snackbarStore.addSnack({
            message: UF.errorMessageOrDefault(e, "Could not reclassify file"),
            type: SnackType.Failure
        });
    } finally {
        if (!!onSensitivityChange) onSensitivityChange();
    }
}

export const fetchFileContent = async (path: string, cloud: SDUCloud): Promise<Response> => {
    const token = await cloud.createOneTimeTokenWithPermission("files.download:read");
    return fetch(Cloud.computeURL(
        "/api",
        `/files/download?path=${encodeURIComponent(path)}&token=${encodeURIComponent(token)}`)
    );
};

export const sizeToString = (bytes: number | null): string => {
    if (bytes === null) return "";
    if (bytes < 0) return "Invalid size";
    if (bytes < 1000) {
        return `${bytes} B`;
    } else if (bytes < 1000 ** 2) {
        return `${(bytes / 1000).toFixed(2)} KB`;
    } else if (bytes < 1000 ** 3) {
        return `${(bytes / 1000 ** 2).toFixed(2)} MB`;
    } else if (bytes < 1000 ** 4) {
        return `${(bytes / 1000 ** 3).toFixed(2)} GB`;
    } else if (bytes < 1000 ** 5) {
        return `${(bytes / 1000 ** 4).toFixed(2)} TB`;
    } else if (bytes < 1000 ** 6) {
        return `${(bytes / 1000 ** 5).toFixed(2)} PB`;
    } else {
        return `${(bytes / 1000 ** 6).toFixed(2)} EB`;
    }
};

export const directorySizeQuery = "/files/stats/directory-sizes";

interface ShareFiles {
    files: File[];
    cloud: SDUCloud;
}

export const shareFiles = async ({files, cloud}: ShareFiles) => {
    const input = await shareDialog();
    if ("cancelled" in input) return;
    const rights: string[] = [];
    if (input.access.includes("read")) rights.push("READ");
    if (input.access.includes("read_edit")) rights.push("WRITE");
    let iteration = 0;
    // Replace with Promise.all
    files.map(f => f.path).forEach((path, _, paths) => {
        const body = {
            sharedWith: input.username,
            path,
            rights
        };
        cloud.put(`/shares/`, body)
            .then(() => {
                if (++iteration === paths.length) snackbarStore.addSnack({
                    message: "Files shared successfully",
                    type: SnackType.Success
                });
            })
            .catch(({response}) => snackbarStore.addSnack({message: `${response.why}`, type: SnackType.Failure}));
    });
};

const moveToTrashDialog = ({filePaths, onConfirm}: { onConfirm: () => void, filePaths: string[] }): void => {
    const message = filePaths.length > 1 ? `Move ${filePaths.length} files to trash?` :
        `Move file ${getFilenameFromPath(filePaths[0])} to trash?`;

    addStandardDialog({
        title: "Move files to trash",
        message,
        onConfirm,
        confirmText: "Move files"
    });
};

export function clearTrashDialog({onConfirm}: { onConfirm: () => void }): void {
    addStandardDialog({
        title: "Empty trash?",
        message: "",
        confirmText: "Confirm",
        cancelText: "Cancel",
        onConfirm
    });
}

interface ResultToNotification {
    failures: string[];
    paths: string[];
    homeFolder: string;
}

function resultToNotification({failures, paths, homeFolder}: ResultToNotification) {
    const successMessage = successResponse(paths, homeFolder);
    if (failures.length === 0) {
        snackbarStore.addSnack({message: successMessage, type: SnackType.Success});
    } else if (failures.length === paths.length) {
        snackbarStore.addSnack({
            message: "Failed moving all files, please try again later",
            type: SnackType.Failure
        });
    } else {
        snackbarStore.addSnack({
            message: `${successMessage}\n Failed to move files: ${failures.join(", ")}`,
            type: SnackType.Information
        });
    }
}

const successResponse = (paths: string[], homeFolder: string) =>
    paths.length > 1 ?
        `${paths.length} files moved to trash.` :
        `${replaceHomeFolder(paths[0], homeFolder)} moved to trash`;

interface Failures {
    failures: string[];
}

interface MoveToTrash {
    files: File[];
    cloud: SDUCloud;
    setLoading: () => void;
    callback: () => void;
}

export const moveToTrash = ({files, cloud, setLoading, callback}: MoveToTrash) => {
    const paths = files.map(f => f.path);
    moveToTrashDialog({
        filePaths: paths, onConfirm: async () => {
            try {
                setLoading();
                const {response} = await cloud.post<Failures>("/files/trash/", {files: paths});
                resultToNotification({failures: response.failures, paths, homeFolder: cloud.homeFolder});
                callback();
            } catch (e) {
                snackbarStore.addSnack({message: e.why, type: SnackType.Failure});
                callback();
            }
        }
    });
};

interface MoveFile {
    oldPath: string;
    newPath: string;
    cloud: SDUCloud;
    setLoading: () => void;
    onSuccess: () => void;
}

export async function moveFile({oldPath, newPath, cloud, setLoading, onSuccess}: MoveFile): Promise<void> {
    setLoading();
    try {
        await cloud.post(`/files/move?path=${encodeURIComponent(oldPath)}&newPath=${encodeURIComponent(newPath)}`);
        onSuccess();
    } catch (e) {
        defaultErrorHandler(e);
    }
}

interface CreateFolder {
    path: string;
    cloud: SDUCloud;
    onSuccess: () => void;
}

export async function createFolder({path, cloud, onSuccess}: CreateFolder): Promise<void> {
    try {
        await cloud.post("/files/directory", {path});
        onSuccess();
        snackbarStore.addSnack({message: "Folder created", type: SnackType.Success});
    } catch (e) {
        snackbarStore.addSnack({
            message: UF.errorMessageOrDefault(e, "An error occurred trying to creating the file."),
            type: SnackType.Failure
        });
    }
}

export const inTrashDir = (path: string, cloud: SDUCloud): boolean => getParentPath(path) === cloud.trashFolder;

export function isAnyMockFile(files: File[]): boolean {
    return files.some(it => it.mockTag !== undefined);
}

export function isAnySharedFs(files: File[]): boolean {
    return files.some(it => it.fileType === "SHARED_FS");
}

export const fileInfoPage = (path: string): string => `/files/info?path=${encodeURIComponent(resolvePath(path))}`;
export const fileTablePage = (path: string): string => `/files?path=${encodeURIComponent(resolvePath(path))}`;

export const archiveExtensions: string[] = [".tar.gz", ".zip"];
export const isArchiveExtension = (fileName: string): boolean => archiveExtensions.some(it => fileName.endsWith(it));

