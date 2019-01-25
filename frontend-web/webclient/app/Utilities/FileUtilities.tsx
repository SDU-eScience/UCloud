import { Cloud } from "Authentication/SDUCloudObject";
import SDUCloud from "Authentication/lib";
import { File, MoveCopyOperations, Operation, SortOrder, SortBy, PredicatedOperation, FileType } from "Files";
import { Page } from "Types";
import { History } from "history";
import swal, { SweetAlertResult } from "sweetalert2";
import * as UF from "UtilityFunctions";
import { projectViewPage } from "Utilities/ProjectUtilities";
import { SensitivityLevelMap } from "DefaultObjects";
import { unwrap, isError, ErrorMessage } from "./XHRUtils";
import { UploadPolicy } from "Uploader/api";

export function copy(files: File[], operations: MoveCopyOperations, cloud: SDUCloud, setLoading: () => void): void {
    let iteration = 0;
    operations.setDisallowedPaths(files.map(f => f.path));
    operations.showFileSelector(true);
    operations.setFileSelectorCallback(async (targetPathFolder: File) => {
        let { failurePaths, applyToAll, policy } = initialSetup(operations);
        for (let i = 0; i < files.length; i++) {
            let f = files[i];
            let { exists, allowRewrite, newPathForFile } = await moveCopySetup(targetPathFolder.path, f.path, cloud);
            if (exists && !applyToAll) {
                allowRewrite = await canRewrite(newPathForFile, cloud.homeFolder, files.length - i);
                policy = UF.selectValue("policy") as UploadPolicy;
                if (files.length - i > 1) applyToAll = UF.elementValue("applyToAll");
            }
            if (applyToAll) allowRewrite = true;
            if ((exists && allowRewrite) || !exists) {
                cloud.post(copyFileQuery(f.path, newPathForFile, policy))
                    .catch(() => UF.failureNotification(`An error occured copying file ${resolvePath(f.path)}.`))
                    .finally(() => {
                        if (++iteration === files.length) {
                            setLoading();
                            operations.fetchPageFromPath(newPathForFile);
                            UF.successNotification(`File${files.length > 1 ? "s" : ""} copied.`);
                        }
                    });
            }
        }; // End for
    }); // End Callback
};

// FIXME, find better name
interface InitialSetup { failurePaths: string[]; applyToAll: boolean; policy: UploadPolicy; }
function initialSetup(operations: MoveCopyOperations): InitialSetup {
    resetFileSelector(operations);
    const failurePaths: string[] = [];
    let applyToAll = false;
    let policy = UploadPolicy.REJECT;
    return { failurePaths, applyToAll, policy };
}

async function canRewrite(newPath: string, homeFolder: string, filesRemaining: number): Promise<boolean> {
    return !!(await rewritePolicy(newPath, homeFolder, filesRemaining)).value;
}

function getNewPath(newParentPath: string, currentPath: string): string {
    return `${UF.removeTrailingSlash(resolvePath(newParentPath))}/${getFilenameFromPath(resolvePath(currentPath))}`
}

export function move(files: File[], operations: MoveCopyOperations, cloud: SDUCloud, setLoading: () => void): void {
    let successes = 0, failures = 0;
    operations.showFileSelector(true);
    operations.setDisallowedPaths([getParentPath(files[0].path)].concat(files.map(f => f.path)));
    operations.setFileSelectorCallback(async (targetPathFolder: File) => {
        let { failurePaths, applyToAll, policy } = initialSetup(operations);
        for (let i = 0; i < files.length; i++) {
            let f = files[i];
            var { exists, allowRewrite, newPathForFile } = await moveCopySetup(targetPathFolder.path, f.path, cloud);
            if (exists && !applyToAll) {
                allowRewrite = await canRewrite(newPathForFile, cloud.homeFolder, files.length - i);
                policy = UF.selectValue("policy") as UploadPolicy;
                if (files.length - i > 1) applyToAll = UF.elementValue("applyToAll");
            }
            if (applyToAll) allowRewrite = true;
            if ((exists && allowRewrite) || !exists) {
                cloud.post(moveFileQuery(f.path, newPathForFile, policy)).then(() => successes++).catch(() => {
                    failures++;
                    failurePaths.push(getFilenameFromPath(f.path))
                    UF.failureNotification(`An error occurred moving ${f.path}`)
                }).finally(() => {
                    if (successes + failures === files.length) {
                        setLoading();
                        if (successes) operations.fetchPageFromPath(newPathForFile);
                        if (!failures)
                            onSuccess(f, newPathForFile, cloud, files);
                        else
                            UF.failureNotification(`Failed to move files: ${failurePaths.join(", ")}`, 10)
                    }
                });
            }
        };
    });
};

interface ResetFileSelector {
    showFileSelector: (v: boolean) => void
    setFileSelectorCallback: (v: any) => void
    setDisallowedPaths: (array: string[]) => void;
}

async function moveCopySetup(targetPath: string, path: string, cloud: SDUCloud) {
    const newPathForFile = getNewPath(targetPath, path);
    const exists = await checkIfFileExists(newPathForFile, cloud);
    return { exists, allowRewrite: false, newPathForFile };
}

function onSuccess(f: File, newPathForFile: string, cloud: SDUCloud, files: File[]) {
    const fromPath = getFilenameFromPath(f.path);
    const toPath = replaceHomeFolder(newPathForFile, cloud.homeFolder);
    if (files.length === 1)
        UF.successNotification(`${fromPath} moved to ${toPath}`);
    else
        UF.successNotification(`Moved ${files.length} files`);
}

function resetFileSelector(operations: ResetFileSelector) {
    operations.showFileSelector(false);
    operations.setFileSelectorCallback(undefined);
    operations.setDisallowedPaths([]);
}

export const checkIfFileExists = async (path: string, cloud: SDUCloud): Promise<boolean> => {
    try {
        await cloud.get(statFileQuery(path))
        return true;
    } catch (e) {
        // FIXME: in the event of other than 404
        return !(e.request.status === 404);
    }
}

function rewritePolicy(path: string, homeFolder: string, filesRemaining: number): Promise<SweetAlertResult> {
    return swal({
        title: "File exists",
        text: ``,
        html: `<div>${replaceHomeFolder(path, homeFolder)} already exists. Do you want to overwrite it ?</div> <br/>
                    <select id="policy" defaultValue="RENAME">
                        <option value="RENAME">Rename</option>
                        <option value="OVERWRITE">Overwrite</option>
                    </select>
                ${ filesRemaining > 1 ? `<br/>
                <label><input id="applyToAll" type="checkbox"/> Apply to all</label>` : ""} `,
        allowEscapeKey: true,
        allowOutsideClick: true,
        allowEnterKey: false,
        showConfirmButton: true,
        showCancelButton: true,
        cancelButtonText: "No",
        confirmButtonText: "Yes",
    });
}

export const startRenamingFiles = (files: File[], page: Page<File>) => {
    const paths = files.map(it => it.path);
    // FIXME Very slow
    page.items.forEach(file => {
        if (paths.some(p => p === file.path)) {
            file.beingRenamed = true
        }
    });
    return page;
}

export type AccessRight = "READ" | "WRITE" | "EXECUTE";
const hasAccess = (accessRight: AccessRight, file: File) => {
    const username = Cloud.username;
    if (file.ownerName === username) return true;
    if (file.acl === undefined) return false;

    const relevantEntries = file.acl.filter(item => !item.group && item.entity === username);
    return relevantEntries.some(entry => entry.rights.includes(accessRight));
};

export const allFilesHasAccessRight = (accessRight: AccessRight, files: File[]) =>
    files.every(f => hasAccess(accessRight, f));

export const createFileLink = (file: File, cloud: SDUCloud, setLoading: () => void, pageFromPath: (p: string) => void) => {
    const fileName = getFilenameFromPath(file.path);
    const linkPath = file.path.replace(fileName, `Link to ${fileName} `)
    setLoading();
    cloud.post("/files/create-link", {
        linkTargetPath: file.path,
        linkPath: linkPath
    }).then(it => pageFromPath(linkPath)).catch(it => UF.failureNotification("An error occurred creating link."));
};

/**
 * @returns Share and Download operations for files
 */
export const StateLessOperations = (setLoading: () => void): Operation[] => [
    {
        text: "Share",
        onClick: (files: File[], cloud: SDUCloud) => shareFiles(files, setLoading, cloud),
        disabled: (files: File[], cloud: SDUCloud) => !allFilesHasAccessRight("WRITE", files) || !allFilesHasAccessRight("READ", files),
        icon: "share",
        color: undefined
    },
    {
        text: "Download",
        onClick: (files: File[], cloud: SDUCloud) => downloadFiles(files, setLoading, cloud),
        disabled: (files: File[], cloud: SDUCloud) => !UF.downloadAllowed(files) || !allFilesHasAccessRight("READ", files),
        icon: "download",
        color: undefined
    }
];

export const CreateLinkOperation = (fetchPageFromPath: (p: string) => void, setLoading: () => void) => [{
    text: "Create link",
    onClick: (files: File[], cloud: SDUCloud) => createFileLink(files[0], cloud, setLoading, fetchPageFromPath),
    disabled: (files: File[], cloud: SDUCloud) => files.length > 1,
    icon: "link",
    color: undefined
}]

/**
 * @returns Move and Copy operations for files
 */
export const FileSelectorOperations = (fileSelectorOperations: MoveCopyOperations, setLoading: () => void): Operation[] => [
    {
        text: "Copy",
        onClick: (files: File[], cloud: SDUCloud) => copy(files, fileSelectorOperations, cloud, setLoading),
        disabled: (files: File[], cloud: SDUCloud) => !allFilesHasAccessRight("WRITE", files),
        icon: "copy",
        color: undefined
    },
    {
        text: "Move",
        onClick: (files: File[], cloud: SDUCloud) => move(files, fileSelectorOperations, cloud, setLoading),
        disabled: (files: File[], cloud: SDUCloud) => !allFilesHasAccessRight("WRITE", files) || files.some(f => isFixedFolder(f.path, cloud.homeFolder)),
        icon: "move",
        color: undefined
    }
];

export const archiveExtensions: string[] = [".tar.gz", ".zip"]
export const isArchiveExtension = (fileName: string): boolean => archiveExtensions.some(it => fileName.endsWith(it));

/**
 * 
 * @param onFinished called when extraction is completed successfully.
 */
export const ExtractionOperation = (onFinished: () => void): Operation[] => [
    {
        text: "Extract archive",
        onClick: (files, cloud) => extractArchive(files, cloud, onFinished),
        disabled: (files, cloud) => files.length > 1 || !isArchiveExtension(files[0].path),
        icon: "open",
        color: undefined
    }
];

/**
 * 
 * @param onMoved To be called on completed deletion of files
 * @returns the Delete operation in an array
 */
export const MoveFileToTrashOperation = (onMoved: () => void, setLoading: () => void): Operation[] => [
    {
        text: "Move to Trash",
        onClick: (files, cloud) => moveToTrash(files, cloud, setLoading, onMoved),
        disabled: (files: File[], cloud: SDUCloud) => (!allFilesHasAccessRight("WRITE", files) || files.some(f => isFixedFolder(f.path, cloud.homeFolder)) || files.every(({ path }) => inTrashDir(path, cloud))),
        icon: "trash",
        color: "red"
    },
    {
        text: "Delete Files",
        onClick: (files, cloud) => batchDeleteFiles(files, cloud, setLoading, onMoved),
        disabled: (files, cloud) => !files.every(f => getParentPath(f.path) === cloud.trashFolder),
        icon: "trash",
        color: "red"
    }
];

export const ClearTrashOperations = (toHome: () => void, setLoading: () => void): Operation[] => [
    {
        text: "Clear Trash",
        onClick: (files, cloud) => clearTrash(cloud, setLoading, toHome),
        disabled: (files, cloud) => !files.every(f => UF.addTrailingSlash(f.path) === cloud.trashFolder) && !files.every(f => getParentPath(f.path) === cloud.trashFolder),
        icon: "trash",
        color: "red"
    }
];

/**
 * @returns Properties and Project Operations for files.
 */
export const HistoryFilesOperations = (history: History): [Operation, PredicatedOperation] => [
    {
        text: "Properties",
        onClick: (files: File[], cloud: SDUCloud) => history.push(fileInfoPage(files[0].path)),
        disabled: (files: File[], cloud: SDUCloud) => files.length !== 1,
        icon: "properties", color: "blue"
    },
    {
        predicate: (files: File[], cloud: SDUCloud) => isProject(files[0]),
        onTrue: {
            text: "Edit Project",
            onClick: (files: File[], cloud: SDUCloud) => history.push(projectViewPage(files[0].path)),
            disabled: (files: File[], cloud: SDUCloud) => !canBeProject(files, cloud.homeFolder),
            icon: "projects", color: "blue"
        },
        onFalse: {
            text: "Create Project",
            onClick: (files: File[], cloud: SDUCloud) =>
                UF.createProject(
                    files[0].path,
                    cloud,
                    projectPath => history.push(projectViewPage(projectPath))
                ),
            disabled: (files: File[], cloud: SDUCloud) =>
                files.length !== 1 || !canBeProject(files, cloud.homeFolder) ||
                !allFilesHasAccessRight("READ", files) || !allFilesHasAccessRight("WRITE", files),
            icon: "projects",
            color: "blue"
        },
    }
];

export const fileInfoPage = (path: string): string => `/files/info?path=${encodeURIComponent(resolvePath(path))}`;
export const filePreviewPage = (path: string) => `/files/preview?path=${encodeURIComponent(resolvePath(path))}`;
export const fileTablePage = (path: string): string => `/files?path=${encodeURIComponent(resolvePath(path))}`;

export function AllFileOperations(
    stateless: boolean,
    fileSelectorOps: MoveCopyOperations | false,
    onDeleted: (() => void) | false,
    onExtracted: (() => void) | false,
    onClearTrash: (() => void) | false,
    onLinkCreate: ((p: string) => void) | false,
    history: History | false,
    setLoading: () => void
) {
    const stateLessOperations = stateless ? StateLessOperations(setLoading) : [];
    const fileSelectorOperations = !!fileSelectorOps ? FileSelectorOperations(fileSelectorOps, setLoading) : [];
    const deleteOperation = !!onDeleted ? MoveFileToTrashOperation(onDeleted, setLoading) : [];
    const clearTrash = !!onClearTrash ? ClearTrashOperations(onClearTrash, setLoading) : [];
    const historyOperations = !!history ? HistoryFilesOperations(history) : [];
    const createLink = !!onLinkCreate ? CreateLinkOperation(onLinkCreate, setLoading) : [];
    const extractionOperations = !!onExtracted ? ExtractionOperation(onExtracted) : [];
    return [
        ...stateLessOperations,
        ...fileSelectorOperations,
        ...deleteOperation,
        ...extractionOperations,
        // ...clearTrash,
        ...historyOperations,
        ...createLink
    ];
};


/**
 * Used for resolving paths, which contain either "." or "..", and returning the resolved path.
 * @param path The current input path, which can include relative paths
 */
export function resolvePath(path: string) {
    const components = path.split("/");
    const result: string[] = [];
    components.forEach(it => {
        if (it === ".") {
            return;
        } else if (it === "..") {
            result.pop();
        } else {
            result.push(it);
        }
    });
    return result.join("/");
}

export const filepathQuery = (path: string, page: number, itemsPerPage: number, order: SortOrder = SortOrder.ASCENDING, sortBy: SortBy = SortBy.PATH): string =>
    `files?path=${encodeURIComponent(resolvePath(path))}&itemsPerPage=${itemsPerPage}&page=${page}&order=${encodeURIComponent(order)}&sortBy=${encodeURIComponent(sortBy)}`;

// FIXME: UF.removeTrailingSlash(path) shouldn't be unnecessary, but otherwise causes backend issues
export const fileLookupQuery = (path: string, itemsPerPage: number = 25, order: SortOrder = SortOrder.DESCENDING, sortBy: SortBy = SortBy.PATH): string =>
    `files/lookup?path=${encodeURIComponent(resolvePath(path))}&itemsPerPage=${itemsPerPage}&order=${encodeURIComponent(order)}&sortBy=${encodeURIComponent(sortBy)}`;

export const advancedFileSearch = "/file-search/advanced"

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

export const statFileQuery = (path: string) => `/files/stat?path=${encodeURIComponent(path)}`;

export const newMockFolder = (path: string = "", beingRenamed: boolean = true): File => ({
    fileType: "DIRECTORY",
    path,
    createdAt: new Date().getTime(),
    modifiedAt: new Date().getTime(),
    ownerName: "",
    size: 0,
    acl: [],
    favorited: false,
    sensitivityLevel: SensitivityLevelMap.PRIVATE,
    isChecked: false,
    beingRenamed,
    link: false,
    annotations: [],
    isMockFolder: true
});

/**
 * Checks if a pathname is legal/already in use
 * @param {string} path The path being tested
 * @param {string[]} filePaths the other file paths path is being compared against
 * @returns whether or not the path is invalid
 */
export const isInvalidPathName = (path: string, filePaths: string[]): boolean => {
    if (["..", "/"].some((it) => path.includes(it))) { UF.failureNotification("Folder name cannot contain '..' or '/'"); return true }
    if (path === "" || path === ".") { UF.failureNotification("Folder name cannot be empty or be \".\""); return true; }
    const existingName = filePaths.some(it => it === path);
    if (existingName) { UF.failureNotification("File with that name already exists"); return true; }
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
    ].some(it => UF.removeTrailingSlash(it) === filePath)
};

/**
 * Used for favoriting files based on a path and page consisting of files.
 * @param {Page<File>} page The page of files to be searched through
 * @param {File[]} filesToFavorite Files to be favorited
 * @param {Cloud} cloud The instance of a Cloud object used for requests
 * @returns {Page<File>} The page of files with the file favorited
 */
export const favoriteFileFromPage = (page: Page<File>, filesToFavorite: File[], cloud: SDUCloud): Page<File> => {
    filesToFavorite.forEach(f => {
        const file = page.items.find(file => file.path === f.path)!;
        favoriteFile(file, cloud);
    });
    return page;
};

/**
 * Used to favorite/defavorite a file based on its current state.
 * @param {File} file The single file to be favorited
 * @param {Cloud} cloud The cloud instance used to changed the favorite state for the file
 */
export const favoriteFile = (file: File, cloud: SDUCloud): File => {
    file.favorited = !file.favorited;
    if (file.favorited)
        cloud.post(`/files/favorite?path=${encodeURIComponent(file.path)}`, {}); // FIXME: Error handling
    else
        cloud.delete(`/files/favorite?path=${encodeURIComponent(file.path)}`, {}); // FIXME: Error handling
    return file;
}

export const reclassifyFile = async (file: File, sensitivity: SensitivityLevelMap, cloud: SDUCloud): Promise<File> => {
    const callResult = await unwrap(cloud.post<void>("/files/reclassify", { path: file.path, sensitivity }));
    if (isError(callResult)) {
        UF.failureNotification((callResult as ErrorMessage).errorMessage)
        return file;
    }
    return { ...file, sensitivityLevel: sensitivity };
}

export const canBeProject = (files: File[], homeFolder: string): boolean =>
    files.length === 1 && files.every(f => isDirectory(f)) && !isFixedFolder(files[0].path, homeFolder) && !isLink(files[0]);

export const previewSupportedExtension = (path: string) => false;

export const isProject = (file: File) => file.fileType === "DIRECTORY" && file.annotations.some(it => it === "P");

export const toFileText = (selectedFiles: File[]): string =>
    `${selectedFiles.length} file${selectedFiles.length > 1 ? "s" : ""} selected`

export const isLink = (file: File) => file.link;
export const isDirectory = (file: { fileType: FileType }) => file.fileType === "DIRECTORY";
export const replaceHomeFolder = (path: string, homeFolder: string) => path.replace(homeFolder, "Home/");

export const showFileDeletionPrompt = (filePath: string, cloud: SDUCloud, callback: () => void) =>
    moveToTrashSwal([filePath]).then((result: any) => {
        if (result.dismiss) {
            return;
        } else {
            cloud.delete("/files", { path: filePath }).then(() => callback ? callback() : null);
        }
    });

export const extractArchive = (files: File[], cloud: SDUCloud, onFinished: () => void): void => {
    cloud.post("/files/extract", { path: files[0].path }).then(it => (UF.successNotification("File extracted"), onFinished()))
        .catch(it => UF.failureNotification(`An error occurred extracting file.`))
}



export const clearTrash = (cloud: SDUCloud, setLoading: () => void, callback: () => void) =>
    clearTrashSwal().then(result => {
        if (result.dismiss) {
            return;
        } else {
            setLoading();
            cloud.post("/files/trash/clear", {}).then(() => callback());
        }
    });

export const getParentPath = (path: string): string => {
    if (path.length === 0) return path;
    let splitPath = path.split("/");
    splitPath = splitPath.filter(path => path);
    let parentPath = "/";
    for (let i = 0; i < splitPath.length - 1; i++) {
        parentPath += splitPath[i] + "/";
    }
    return parentPath;
};

const goUpDirectory = (count: number, path: string): string => count ? goUpDirectory(count - 1, getParentPath(path)) : path;

const toFileName = (path: string): string => path.split("/").filter(p => p).pop()!;

export function getFilenameFromPath(path: string): string {
    const replacedHome = replaceHomeFolder(path, Cloud.homeFolder)
    const fileName = toFileName(replacedHome);
    if (fileName === "..") return `.. (${toFileName(goUpDirectory(2, replacedHome))})`
    if (fileName === ".") return `. (Current folder)`
    return fileName;
}

export function downloadFiles(files: File[], setLoading: () => void, cloud: SDUCloud) {
    files.map(f => f.path).forEach(p =>
        cloud.createOneTimeTokenWithPermission("files.download:read").then((token: string) => {
            const element = document.createElement("a");
            element.setAttribute("href", `/api/files/download?path=${encodeURIComponent(p)}&token=${encodeURIComponent(token)}`);
            element.style.display = "none";
            document.body.appendChild(element);
            element.click();
            document.body.removeChild(element);
        }));
}


export const fetchFileContent = async (path: string, cloud: SDUCloud): Promise<Response> => {
    const token = await cloud.createOneTimeTokenWithPermission("files.download:read");
    return fetch(`/api/files/download?path=${encodeURIComponent(path)}&token=${encodeURIComponent(token)}`)
}

export const sizeToString = (bytes: number): string => {
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

export const shareFiles = (files: File[], setLoading: () => void, cloud: SDUCloud) =>
    UF.shareSwal().then(input => {
        if (input.dismiss) return;
        const rights: string[] = [];
        if (UF.elementValue("read")) rights.push("READ")
        if (UF.elementValue("read_edit")) { rights.push("READ"); rights.push("WRITE"); }
        let iteration = 0;
        files.map(f => f.path).forEach((path, i, paths) => {
            const body = {
                sharedWith: input.value,
                path,
                rights
            };
            cloud.put(`/shares/`, body).then(() => { if (++iteration === paths.length) UF.successNotification("Files shared successfully") })
                .catch(({ response }) => UF.failureNotification(`${response.why}`));
        });
    });

const moveToTrashSwal = (filePaths: string[]) => {
    const moveText = filePaths.length > 1 ? `Move ${filePaths.length} files to trash?` :
        `Move file ${getFilenameFromPath(filePaths[0])} to trash?`;
    return swal({
        title: "Move files to trash",
        text: moveText,
        confirmButtonText: "Move files",
        type: "warning",
        showCancelButton: true,
        showCloseButton: true,
    });
};

export const clearTrashSwal = () => {
    return swal({
        title: "Empty trash?",
        confirmButtonText: "Confirm",
        type: "warning",
        showCancelButton: true,
        showCloseButton: true,
    });
};


function resultToNotification(failures: string[], paths: string[], homeFolder: string) {
    const successMessage = successResponse(paths, homeFolder);
    if (failures.length === 0) UF.successNotification(successMessage);
    else if (failures.length === paths.length) UF.failureNotification(`Failed moving all files, please try again later`, 5);
    else UF.infoNotification(`${successMessage}\n Failed to move files: ${failures.join(", ")}`, 15);
}

const successResponse = (paths: string[], homeFolder: string) =>
    paths.length > 1 ? `${paths.length} files moved to trash.` : `${replaceHomeFolder(paths[0], homeFolder)} moved to trash`;

type Failures = { failures: string[] }
export const moveToTrash = (files: File[], cloud: SDUCloud, setLoading: () => void, callback: () => void) => {
    const paths = files.map(f => f.path);
    moveToTrashSwal(paths).then((result: any) => {
        if (result.dismiss) return;
        setLoading();
        cloud.post<Failures>("/files/trash/", { files: paths })
            .then(({ response }) => (resultToNotification(response.failures, paths, cloud.homeFolder), callback()))
            .catch(({ response }) => (UF.failureNotification(response.why), callback()));
    });
};

export const batchDeleteFiles = (files: File[], cloud: SDUCloud, setLoading: () => void, callback: () => void) => {
    const paths = files.map(f => f.path);
    deletionSwal(paths).then((result: any) => {
        if (result.dismiss) return;
        setLoading();
        let i = 0;
        paths.forEach(path => {
            cloud.delete("/files", { path }).then(() => { if (++i === paths.length) { UF.successNotification("Trash emptied"); callback() } })
                .catch(() => i++);
        });
    });
};


const deletionSwal = (filePaths: string[]) => {
    const deletionText = filePaths.length > 1 ? `Delete ${filePaths.length} files?` :
        `Delete file ${getFilenameFromPath(filePaths[0])}`;
    `Move file ${getFilenameFromPath(filePaths[0])} to trash?`;
    return swal({
        title: "Delete files",
        text: deletionText,
        confirmButtonText: "Delete files",
        type: "warning",
        showCancelButton: true,
        showCloseButton: true,
    });
};

export const moveFile = (oldPath: string, newPath: string, cloud: SDUCloud, setLoading: () => void, onSuccess: () => void) => {
    setLoading();
    cloud.post(`/files/move?path=${encodeURIComponent(oldPath)}&newPath=${encodeURIComponent(newPath)}`).then(({ request }) => {
        if (UF.inSuccessRange(request.status)) {
            onSuccess()
        }
    }).catch(() => UF.failureNotification("An error ocurred trying to rename the file."));
}

export const createFolder = (path: string, cloud: SDUCloud, onSuccess: () => void) =>
    cloud.post("/files/directory", { path }).then(({ request }) => {
        if (UF.inSuccessRange(request.status)) onSuccess()
    }).catch(() => UF.failureNotification("An error ocurred trying to creating the file."));


const inTrashDir = (path: string, cloud: SDUCloud): boolean => getParentPath(path) === cloud.trashFolder;