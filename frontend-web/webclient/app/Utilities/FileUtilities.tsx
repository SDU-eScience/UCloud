import { Cloud } from "Authentication/SDUCloudObject";
import SDUCloud from "Authentication/lib";
import { File, MoveCopyOperations, Operation, SortOrder, SortBy, FileType, FileOperation } from "Files";
import { Page } from "Types";
import { History } from "history";
import swal, { SweetAlertResult } from "sweetalert2";
import * as UF from "UtilityFunctions";
import { projectViewPage } from "Utilities/ProjectUtilities";
import { SensitivityLevelMap } from "DefaultObjects";
import { unwrap, isError, ErrorMessage } from "./XHRUtils";
import { UploadPolicy } from "Uploader/api";
import { AddSnackOperation, SnackType, Snack } from "Snackbar/Snackbars";

function initialSetup(operations: MoveCopyOperations) {
    resetFileSelector(operations);
    return { failurePaths: [] as string[], applyToAll: false, policy: UploadPolicy.REJECT as UploadPolicy };
}

async function canRewrite(newPath: string, homeFolder: string, filesRemaining: number): Promise<boolean> {
    return !!(await rewritePolicy({ path: newPath, homeFolder, filesRemaining })).value;
}

function getNewPath(newParentPath: string, currentPath: string): string {
    return `${UF.removeTrailingSlash(resolvePath(newParentPath))}/${getFilenameFromPath(resolvePath(currentPath))}`
}

enum CopyOrMove {
    Move,
    Copy
}

interface CopyOrMoveFiles extends AddSnackOperation {
    operation: CopyOrMove
    files: File[]
    copyMoveOps: MoveCopyOperations
    cloud: SDUCloud
    setLoading: () => void
}

export function copyOrMoveFiles({ operation, files, copyMoveOps, cloud, setLoading, addSnack }: CopyOrMoveFiles): void {
    const copyOrMoveQuery = operation === CopyOrMove.Copy ? copyFileQuery : moveFileQuery;
    let successes = 0, failures = 0, pathToFetch = "";
    copyMoveOps.showFileSelector(true);
    copyMoveOps.setDisallowedPaths([getParentPath(files[0].path)].concat(files.map(f => f.path)));
    copyMoveOps.setFileSelectorCallback(async (targetPathFolder: File) => {
        let { failurePaths, applyToAll, policy } = initialSetup(copyMoveOps);
        for (let i = 0; i < files.length; i++) {
            let f = files[i];
            let { exists, allowRewrite, newPathForFile } = await moveCopySetup({ targetPath: targetPathFolder.path, path: f.path, cloud });
            if (exists && !applyToAll) {
                allowRewrite = await canRewrite(newPathForFile, cloud.homeFolder, files.length - i);
                policy = UF.selectValue("policy") as UploadPolicy;
                if (files.length - i > 1) applyToAll = UF.elementValue("applyToAll");
            }
            if (applyToAll) allowRewrite = true;
            if ((exists && allowRewrite) || !exists) {
                const request = await cloud.post(copyOrMoveQuery(f.path, newPathForFile, policy))
                if (UF.inSuccessRange(request.request.status)) {
                    successes++;
                    pathToFetch = newPathForFile;
                    if (request.request.status === 202) addSnack({
                        message: `Operation for ${f.path} is in progress.`,
                        type: SnackType.Success
                    })
                } else {
                    failures++;
                    failurePaths.push(getFilenameFromPath(f.path))
                    addSnack({
                        message: `An error occurred ${operation === CopyOrMove.Copy ? "copying" : "moving"} ${f.path}`,
                        type: SnackType.Failure
                    });
                }
            }
        }
        if (successes) {
            setLoading();
            if (policy === UploadPolicy.RENAME) copyMoveOps.fetchFilesPage(getParentPath(pathToFetch));
            else copyMoveOps.fetchPageFromPath(pathToFetch);
        }
        if (!failures && successes) onOnlySuccess({ operation: operation === CopyOrMove.Copy ? "Copied" : "Moved", fileCount: files.length, addSnack });
        else if (failures)
            addSnack({
                message: `Failed to ${operation === CopyOrMove.Copy ? "copy" : "move"} files: ${failurePaths.join(", ")}`,
                type: SnackType.Failure
            });
    });
};

interface MoveCopySetup {
    targetPath: string
    path: string
    cloud: SDUCloud
}

async function moveCopySetup({ targetPath, path, cloud }: MoveCopySetup) {
    const newPathForFile = getNewPath(targetPath, path);
    const exists = await checkIfFileExists(newPathForFile, cloud);
    return { exists, allowRewrite: false, newPathForFile };
}

interface OnOnlySuccess extends AddSnackOperation {
    operation: string
    fileCount: number
}

function onOnlySuccess({ operation, fileCount, addSnack }: OnOnlySuccess) {
    addSnack({ message: `${operation} ${fileCount} files`, type: SnackType.Success });
}

interface ResetFileSelector {
    showFileSelector: (v: boolean) => void
    setFileSelectorCallback: (v: any) => void
    setDisallowedPaths: (array: string[]) => void;
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

interface RewritePolicy {
    path: string
    homeFolder: string
    filesRemaining: number
}

function rewritePolicy({ path, homeFolder, filesRemaining }: RewritePolicy): Promise<SweetAlertResult> {
    return swal({
        title: "File exists",
        text: ``,
        html: `<div>${replaceHomeFolder(path, homeFolder)} already exists. Do you want to overwrite it ?</div> <br/>
                    <select id="policy" defaultValue="RENAME">
                        <option value="RENAME">Rename</option>
                        <option value="OVERWRITE">Overwrite</option>
                    </select>
                ${filesRemaining > 1 ? `<br/>
                <label><input id="applyToAll" type="checkbox"/> Apply to all</label>` : ""}`,
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
    page.items.forEach(file => {
        if (paths.includes(file.path)) file.beingRenamed = true
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


interface CreateFileLink extends AddSnackOperation {
    file: File
    cloud: SDUCloud
    setLoading: () => void
    fetchPageFromPath: (p: string) => void
}

export const createFileLink = async ({ file, cloud, setLoading, fetchPageFromPath, addSnack }: CreateFileLink) => {
    const fileName = getFilenameFromPath(file.path);
    const linkPath = file.path.replace(fileName, `Link to ${fileName} `)
    setLoading();
    try {
        await cloud.post("/files/create-link", {
            linkTargetPath: file.path,
            linkPath
        });
        fetchPageFromPath(linkPath);
    } catch {
        addSnack({ message: "An error occurred creating link.", type: SnackType.Failure });
    }
};

interface StateLessOperations extends AddSnackOperation {
    setLoading: () => void
    onSensitivityChange?: () => void
}
/**
 * @returns Stateless operations for files
 */
export const StateLessOperations = ({ setLoading, onSensitivityChange, addSnack }: StateLessOperations): Operation[] => [
    {
        text: "Share",
        onClick: (files: File[], cloud: SDUCloud) => shareFiles({ files, cloud, addSnack }),
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
    },
    {
        text: "Sensitivity",
        onClick: (files: File[], cloud: SDUCloud) => updateSensitivity({ files, cloud, onSensitivityChange, addSnack }),
        disabled: (files: File[], cloud: SDUCloud) => false,
        icon: "verified",
        color: undefined
    },
    {
        text: "Copy Path",
        onClick: (files: File[], cloud: SDUCloud) => UF.copyToClipboard({ value: files[0].path, message: `${replaceHomeFolder(files[0].path, cloud.homeFolder)} copied to clipboard`, addSnack }),
        disabled: (files: File[], cloud: SDUCloud) => !UF.inDevEnvironment() || files.length !== 1,
        icon: "chat",
        color: undefined
    }
];

interface CreateLinkOperation extends AddSnackOperation {
    fetchPageFromPath: (p: string) => void
    setLoading: () => void
}

export const CreateLinkOperation = ({ fetchPageFromPath, setLoading, addSnack }: CreateLinkOperation) => [{
    text: "Create link",
    onClick: (files: File[], cloud: SDUCloud) => createFileLink({ file: files[0], cloud, setLoading, fetchPageFromPath, addSnack }),
    disabled: (files: File[], cloud: SDUCloud) => files.length > 1,
    icon: "link",
    color: undefined
}]


interface FileSelectorOperations {
    fileSelectorOperations: MoveCopyOperations
    setLoading: () => void
    addSnack: (snack: Snack) => void
}
/**
 * @returns Move and Copy operations for files
 */
export const FileSelectorOperations = ({ fileSelectorOperations, setLoading, addSnack }: FileSelectorOperations): Operation[] => [
    {
        text: "Copy",
        onClick: (files: File[], cloud: SDUCloud) => copyOrMoveFiles({ operation: CopyOrMove.Copy, files, copyMoveOps: fileSelectorOperations, cloud, setLoading, addSnack }),
        disabled: (files: File[], cloud: SDUCloud) => !allFilesHasAccessRight("WRITE", files),
        icon: "copy",
        color: undefined
    },
    {
        text: "Move",
        onClick: (files: File[], cloud: SDUCloud) => copyOrMoveFiles({ operation: CopyOrMove.Move, files, copyMoveOps: fileSelectorOperations, cloud, setLoading, addSnack }),
        disabled: (files: File[], cloud: SDUCloud) => !allFilesHasAccessRight("WRITE", files) || files.some(f => isFixedFolder(f.path, cloud.homeFolder)),
        icon: "move",
        color: undefined
    }
];

export const archiveExtensions: string[] = [".tar.gz", ".zip"]
export const isArchiveExtension = (fileName: string): boolean => archiveExtensions.some(it => fileName.endsWith(it));


interface ExtractionOperation extends AddSnackOperation {
    onFinished: () => void
}

/**
 * 
 * @param onFinished called when extraction is completed successfully.
 */
export const ExtractionOperation = ({ onFinished, addSnack }: ExtractionOperation): Operation[] => [
    {
        text: "Extract archive",
        onClick: (files, cloud) => extractArchive({ files, cloud, onFinished, addSnack }),
        disabled: (files, cloud) => !files.every(it => isArchiveExtension(it.path)),
        icon: "open",
        color: undefined
    }
];


interface MoveFileToTrashOperation extends AddSnackOperation {
    onMoved: () => void
    setLoading: () => void
}
/**
 * 
 * @param onMoved To be called on completed deletion of files
 * @returns the Delete operation in an array
 */
export const MoveFileToTrashOperation = ({ onMoved, setLoading, addSnack }: MoveFileToTrashOperation): Operation[] => [
    {
        text: "Move to Trash",
        onClick: (files, cloud) => moveToTrash({ files, cloud, setLoading, callback: onMoved, addSnack }),
        disabled: (files: File[], cloud: SDUCloud) => (!allFilesHasAccessRight("WRITE", files) || files.some(f => isFixedFolder(f.path, cloud.homeFolder)) || files.every(({ path }) => inTrashDir(path, cloud))),
        icon: "trash",
        color: "red"
    },
    {
        text: "Delete Files",
        onClick: (files, cloud) => batchDeleteFiles({ files, cloud, setLoading, callback: onMoved, addSnack }),
        disabled: (files, cloud) => !files.every(f => getParentPath(f.path) === cloud.trashFolder),
        icon: "trash",
        color: "red"
    }
];

export const ClearTrashOperations = (toHome: () => void): Operation[] => [{
    text: "Clear Trash",
    onClick: (files, cloud) => clearTrash(cloud, toHome),
    disabled: (files, cloud) => !files.every(f => UF.addTrailingSlash(f.path) === cloud.trashFolder) && !files.every(f => getParentPath(f.path) === cloud.trashFolder),
    icon: "trash",
    color: "red"
}];

/**
 * @returns Properties and Project Operations for files.
 */
export const HistoryFilesOperations = (history: History, addSnack: (snack: Snack) => void): FileOperation[] => {
    let ops: FileOperation[] = [{
        text: "Properties",
        onClick: (files: File[], cloud: SDUCloud) => history.push(fileInfoPage(files[0].path)),
        disabled: (files: File[], cloud: SDUCloud) => files.length !== 1,
        icon: "properties", color: "blue"
    }]

    if (process.env.NODE_ENV === "development")
        ops.push({
            predicate: (files: File[], cloud: SDUCloud) => false /* FIXME */,
            onTrue: {
                text: "Edit Project",
                onClick: (files: File[], cloud: SDUCloud) => history.push(projectViewPage(files[0].path)),
                disabled: (files: File[], cloud: SDUCloud) => !canBeProject(files, cloud.homeFolder),
                icon: "projects", color: "blue"
            },
            onFalse: {
                text: "Create Project",
                onClick: (files: File[], cloud: SDUCloud) =>
                    UF.createProject({
                        filePath: files[0].path,
                        cloud,
                        navigate: projectPath => history.push(projectViewPage(projectPath)),
                        addSnack
                    }),
                disabled: (files: File[], cloud: SDUCloud) =>
                    files.length !== 1 || !canBeProject(files, cloud.homeFolder) ||
                    !allFilesHasAccessRight("READ", files) || !allFilesHasAccessRight("WRITE", files),
                icon: "projects",
                color: "blue"
            },
        });
    return ops;
}

export const fileInfoPage = (path: string): string => `/files/info?path=${encodeURIComponent(resolvePath(path))}`;
export const filePreviewPage = (path: string) => `/files/preview?path=${encodeURIComponent(resolvePath(path))}`;
export const fileTablePage = (path: string): string => `/files?path=${encodeURIComponent(resolvePath(path))}`;


interface AllFileOperations extends AddSnackOperation {
    stateless?: boolean,
    fileSelectorOperations?: MoveCopyOperations
    onDeleted?: () => void
    onExtracted?: () => void
    onClearTrash?: () => void
    onSensitivityChange?: () => void
    history?: History,
    setLoading: () => void
}
export function allFileOperations({
    stateless,
    fileSelectorOperations,
    onDeleted,
    onExtracted,
    onClearTrash,
    history,
    setLoading,
    onSensitivityChange,
    addSnack
}: AllFileOperations) {
    const stateLessOperations = stateless ? StateLessOperations({ setLoading, addSnack, onSensitivityChange }) : [];
    const fileSelectorOps = !!fileSelectorOperations ? FileSelectorOperations({ fileSelectorOperations, setLoading, addSnack }) : [];
    const deleteOperation = !!onDeleted ? MoveFileToTrashOperation({ onMoved: onDeleted, setLoading, addSnack }) : [];
    const clearTrash = !!onClearTrash ? ClearTrashOperations(onClearTrash) : [];
    const historyOperations = !!history ? HistoryFilesOperations(history, addSnack) : [];
    const extractionOperations = !!onExtracted ? ExtractionOperation({ onFinished: onExtracted, addSnack }) : [];
    return [
        ...stateLessOperations,
        ...fileSelectorOps,
        ...deleteOperation,
        ...extractionOperations,        // ...clearTrash,
        ...historyOperations,
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
export const favoritesQuery = (page: number = 0, itemsPerPage: number = 25) =>
    `/files/favorite?page=${page}&itemsPerPage=${itemsPerPage}`;

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
    isMockFolder: true
});

interface IsInvalidPathname extends AddSnackOperation {
    path: string
    filePaths: string[]
}

/**
 * Checks if a pathname is legal/already in use
 * @param {string} path The path being tested
 * @param {string[]} filePaths the other file paths path is being compared against
 * @returns whether or not the path is invalid
 */
export const isInvalidPathName = ({ path, filePaths, addSnack }: IsInvalidPathname): boolean => {
    if (["..", "/"].some((it) => path.includes(it))) { addSnack({ message: "Folder name cannot contain '..' or '/'", type: SnackType.Failure }); return true }
    if (path === "" || path === ".") { addSnack({ message: "Folder name cannot be empty or be \".\"", type: SnackType.Failure }); return true; }
    const existingName = filePaths.some(it => it === path);
    if (existingName) {
        addSnack({ message: "File with that name already exists", type: SnackType.Failure });
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
    ].some(it => UF.removeTrailingSlash(it) === filePath)
};

export const favoriteFileFromPage = (page: Page<File>, filesToFavorite: File[], cloud: SDUCloud): Page<File> => {
    filesToFavorite.forEach(f => {
        const i = page.items.findIndex(file => file.path === f.path)!;
        favoriteFile(page.items[i], cloud);
    });
    return page;
};

/**
 * Used to favorite/defavorite a file based on its current state.
 * @param {File} file The single file to be favorited
 * @param {Cloud} cloud The cloud instance used to changed the favorite state for the file
 */
export const favoriteFile = (file: File, cloud: SDUCloud): File => {
    try {
        cloud.post(favoriteFileQuery(file.path), {})
    } catch (e) {
        UF.errorMessageOrDefault(e, "An error occurred favoriting file.");
    }
    file.favorited = !file.favorited;
    return file;
}

/**
 * Used to favorite/defavorite a file based on its current state.
 * @param {File} file The single file to be favorited
 * @param {Cloud} cloud The cloud instance used to changed the favorite state for the file
 */
export const favoriteFileAsync = async (file: File, cloud: SDUCloud): Promise<File> => {
    try {
        await cloud.post(favoriteFileQuery(file.path), {})
    } catch (e) {
        UF.errorMessageOrDefault(e, "An error occurred favoriting file.");
    }
    file.favorited = !file.favorited;
    return file;
}

const favoriteFileQuery = (path: string) => `/files/favorite?path=${encodeURIComponent(path)}`;

interface ReclassifyFile extends AddSnackOperation {
    file: File
    sensitivity: SensitivityLevelMap
    cloud: SDUCloud
}

export const reclassifyFile = async ({ file, sensitivity, cloud, addSnack }: ReclassifyFile): Promise<File> => {
    const serializedSensitivity = sensitivity === SensitivityLevelMap.INHERIT ? null : sensitivity;
    const callResult = await unwrap(cloud.post<void>("/files/reclassify", { path: file.path, sensitivity: serializedSensitivity }));
    if (isError(callResult)) {
        addSnack({ message: (callResult as ErrorMessage).errorMessage, type: SnackType.Failure })
        return file;
    }
    return { ...file, sensitivityLevel: sensitivity, ownSensitivityLevel: sensitivity };
}

export const canBeProject = (files: File[], homeFolder: string): boolean =>
    files.length === 1 && files.every(f => isDirectory(f)) && !isFixedFolder(files[0].path, homeFolder) && !isLink(files[0]);

export const previewSupportedExtension = (path: string) => false;

export const toFileText = (selectedFiles: File[]): string =>
    `${selectedFiles.length} file${selectedFiles.length > 1 ? "s" : ""} selected`

export const isLink = (file: File) => file.link;
export const isDirectory = (file: { fileType: FileType }): boolean => file.fileType === "DIRECTORY";
export const replaceHomeFolder = (path: string, homeFolder: string): string => path.replace(homeFolder, "Home/");
export const expandHomeFolder = (path: string, homeFolder: string): string => {
    if (path.startsWith("Home/"))
        return path.replace("Home/", homeFolder);
    return path;
}

export const showFileDeletionPrompt = (filePath: string, cloud: SDUCloud, callback: () => void) =>
    moveToTrashSwal([filePath]).then((result: any) => {
        if (result.dismiss) {
            return;
        } else {
            cloud.delete("/files", { path: filePath }).then(() => !!callback ? callback() : null);
        }
    });


const extractFilesQuery = "/files/extract";
interface ExtractArchive extends AddSnackOperation {
    files: File[]
    cloud: SDUCloud
    onFinished: () => void
}
export const extractArchive = ({ files, cloud, onFinished, addSnack }: ExtractArchive): void => {
    files.forEach(async f => {
        try {
            await cloud.post(extractFilesQuery, { path: f.path });
            addSnack({ message: "File extracted", type: SnackType.Success });
        } catch (e) {
            addSnack({
                message: UF.errorMessageOrDefault(e, "An error occurred extracting the file."),
                type: SnackType.Failure
            });
        }
    });
    onFinished();
}



export const clearTrash = (cloud: SDUCloud, callback: () => void) =>
    clearTrashSwal().then(result => {
        if (result.dismiss) {
            return;
        } else {
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

const toFileName = (path: string): string => {
    const lastSlash = path.lastIndexOf("/");
    if (lastSlash !== -1 && path.length > lastSlash + 1) {
        return path.substring(lastSlash + 1);
    } else {
        return path;
    }
};

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

interface UpdateSensitivty extends AddSnackOperation {
    files: File[]
    cloud: SDUCloud
    onSensitivityChange?: () => void
}

function updateSensitivity({ files, cloud, onSensitivityChange, addSnack }: UpdateSensitivty) {
    UF.sensitivitySwal().then(input => {
        if (!!input.dismiss) return;
        Promise.all(
            files.map(file => reclassifyFile({ file, sensitivity: input.value as SensitivityLevelMap, cloud, addSnack }))
        ).catch(e =>
            UF.errorMessageOrDefault(e, "Could not reclassify file")
        ).then(() => !!onSensitivityChange ? onSensitivityChange() : null);
    });
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

interface ShareFiles extends AddSnackOperation { files: File[], cloud: SDUCloud }
export const shareFiles = ({ files, cloud, addSnack }: ShareFiles) =>
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
            cloud.put(`/shares/`, body).then(() => { if (++iteration === paths.length) addSnack({ message: "Files shared successfully", type: SnackType.Success }) })
                .catch(({ response }) => addSnack({ message: `${response.why}`, type: SnackType.Failure }));
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

interface ResultToNotification extends AddSnackOperation {
    failures: string[]
    paths: string[]
    homeFolder: string
}

function resultToNotification({ failures, paths, homeFolder, addSnack }: ResultToNotification) {
    const successMessage = successResponse(paths, homeFolder);
    if (failures.length === 0) addSnack({ message: successMessage, type: SnackType.Success });
    else if (failures.length === paths.length) addSnack({ message: "Failed moving all files, please try again later", type: SnackType.Failure });
    else addSnack({ message: `${successMessage}\n Failed to move files: ${failures.join(", ")}`, type: SnackType.Information });
}

const successResponse = (paths: string[], homeFolder: string) =>
    paths.length > 1 ? `${paths.length} files moved to trash.` : `${replaceHomeFolder(paths[0], homeFolder)} moved to trash`;

type Failures = { failures: string[] }
interface MoveToTrash extends AddSnackOperation {
    files: File[]
    cloud: SDUCloud
    setLoading: () => void
    callback: () => void
}
export const moveToTrash = ({ files, cloud, setLoading, callback, addSnack }: MoveToTrash) => {
    const paths = files.map(f => f.path);
    moveToTrashSwal(paths).then((result: any) => {
        if (result.dismiss) return;
        setLoading();
        cloud.post<Failures>("/files/trash/", { files: paths })
            .then(({ response }) => (resultToNotification({
                failures: response.failures, paths, homeFolder: cloud.homeFolder, addSnack
            }), callback()))
            .catch(({ response }) => (addSnack({ message: response.why, type: SnackType.Failure }), callback()));
    });
};

interface BatchDeleteFiles extends AddSnackOperation {
    files: File[]
    cloud: SDUCloud
    setLoading: () => void
    callback: () => void
}

export const batchDeleteFiles = ({ files, cloud, setLoading, callback, addSnack }: BatchDeleteFiles) => {
    const paths = files.map(f => f.path);
    deletionSwal(paths).then((result: any) => {
        if (result.dismiss) return;
        setLoading();
        let i = 0;
        // FIXME: Rewrite using Promise.all
        paths.forEach(path => {
            cloud.delete("/files", { path }).then(() => {
                if (++i === paths.length) {
                    addSnack({ message: "Trash emptied", type: SnackType.Success });
                    callback();
                }
            }).catch(() => i++);
        });
    });
};


const deletionSwal = (filePaths: string[]) => {
    const deletionText = filePaths.length > 1 ? `Delete ${filePaths.length} files?` :
        `Delete file ${getFilenameFromPath(filePaths[0])}`;
    return swal({
        title: "Delete files",
        text: deletionText,
        confirmButtonText: "Delete files",
        type: "warning",
        showCancelButton: true,
        showCloseButton: true,
    });
};

interface MoveFile extends AddSnackOperation {
    oldPath: string
    newPath: string
    cloud: SDUCloud
    setLoading: () => void
    onSuccess: () => void
}

export async function moveFile({ oldPath, newPath, cloud, setLoading, onSuccess, addSnack }: MoveFile): Promise<void> {
    setLoading();
    try {
        await cloud.post(`/files/move?path=${encodeURIComponent(oldPath)}&newPath=${encodeURIComponent(newPath)}`);
        onSuccess();
    } catch {
        addSnack({ message: "An error ocurred trying to rename the file.", type: SnackType.Failure });
    }
}

interface CreateFolder extends AddSnackOperation {
    path: string
    cloud: SDUCloud
    onSuccess: () => void
}

export async function createFolder({ path, cloud, onSuccess, addSnack }: CreateFolder): Promise<void> {
    try {
        await cloud.post("/files/directory", { path })
        onSuccess();
    } catch {
        addSnack({ message: "An error ocurred trying to creating the file.", type: SnackType.Failure });
    }
}

const inTrashDir = (path: string, cloud: SDUCloud): boolean => getParentPath(path) === cloud.trashFolder;