import { Cloud } from "Authentication/SDUCloudObject";
import SDUCloud from "Authentication/lib";
import { File, MoveCopyOperations, Operation, SortOrder, SortBy, PredicatedOperation, FileType } from "Files";
import { Page } from "Types";
import { History } from "history";
import swal from "sweetalert2";
import * as UF from "UtilityFunctions";
import { projectViewPage } from "Utilities/ProjectUtilities";
import { SensitivityLevel, SensitivityLevelMap } from "DefaultObjects";
import { unwrap, isError, ErrorMessage } from "./XHRUtils";

export function copy(files: File[], operations: MoveCopyOperations, cloud: SDUCloud): void {
    let i = 0;
    operations.setDisallowedPaths(files.map(f => f.path));
    operations.showFileSelector(true);
    operations.setFileSelectorCallback((file: File) => {
        const newPath = file.path;
        operations.showFileSelector(false);
        operations.setFileSelectorCallback(undefined);
        operations.setDisallowedPaths([]);
        files.forEach(f => {
            const currentPath = f.path;
            const newPathForFile = `${UF.removeTrailingSlash(newPath)}/${getFilenameFromPath(currentPath)}`;
            cloud.post(`/files/copy?path=${encodeURIComponent(currentPath)}&newPath=${encodeURIComponent(newPathForFile)}`).then(() => i++)
                .catch(() => UF.failureNotification(`An error occured copying file ${currentPath}.`))
                .finally(() => {
                    if (i === files.length) {
                        operations.fetchPageFromPath(newPathForFile);
                        UF.successNotification("File copied.");
                    }
                });
        }); // End forEach
    }); // End Callback
};

export function move(files: File[], operations: MoveCopyOperations, cloud: SDUCloud): void {
    operations.showFileSelector(true);
    const parentPath = getParentPath(files[0].path);
    operations.setDisallowedPaths([parentPath].concat(files.map(f => f.path)));
    operations.setFileSelectorCallback((file: File) => {
        const newPath = file.path;
        files.forEach((f) => {
            const currentPath = f.path;
            const newPathForFile = `${UF.removeTrailingSlash(newPath)}/${getFilenameFromPath(currentPath)}`;
            cloud.post(`/files/move?path=${encodeURIComponent(currentPath)}&newPath=${encodeURIComponent(newPathForFile)}`).then(() => {
                const fromPath = getFilenameFromPath(currentPath);
                const toPath = replaceHomeFolder(newPathForFile, cloud.homeFolder);
                UF.successNotification(`${fromPath} moved to ${toPath}`);
                operations.fetchPageFromPath(newPathForFile);
            }).catch(() => UF.failureNotification("An error occurred, please try again later"));
            operations.showFileSelector(false);
            operations.setFileSelectorCallback(undefined);
            operations.setDisallowedPaths([]);
        });
    });
};

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

export const allFilesHasAccessRight = (accessRight: AccessRight, files: File[]) => {
    return files.every(f => hasAccess(accessRight, f));
};

/**
 * @returns Share and Download operations for files
 */
export const StateLessOperations = (): Operation[] => [
    {
        text: "Share",
        onClick: (files: File[], cloud: SDUCloud) => shareFiles(files, cloud),
        disabled: (files: File[], cloud: SDUCloud) => !allFilesHasAccessRight("WRITE", files) || !allFilesHasAccessRight("READ", files),
        icon: "share",
        color: undefined
    },
    {
        text: "Download",
        onClick: (files: File[], cloud: SDUCloud) => downloadFiles(files, cloud),
        disabled: (files: File[], cloud: SDUCloud) => !UF.downloadAllowed(files) || !allFilesHasAccessRight("READ", files),
        icon: "download",
        color: undefined
    }
];

/**
 * @returns Move and Copy operations for files
 */
export const FileSelectorOperations = (fileSelectorOperations: MoveCopyOperations): Operation[] => [
    {
        text: "Copy",
        onClick: (files: File[], cloud: SDUCloud) => copy(files, fileSelectorOperations, cloud),
        disabled: (files: File[], cloud: SDUCloud) => !allFilesHasAccessRight("WRITE", files),
        icon: "copy",
        color: undefined
    },
    {
        text: "Move",
        onClick: (files: File[], cloud: SDUCloud) => move(files, fileSelectorOperations, cloud),
        disabled: (files: File[], cloud: SDUCloud) => !allFilesHasAccessRight("WRITE", files) || files.some(f => isFixedFolder(f.path, cloud.homeFolder)),
        icon: "move",
        color: undefined
    }
];

/**
 * 
 * @param onMoved To be called on completed deletion of files
 * @returns the Delete operation in an array
 */
export const MoveFileToTrashOperation = (onMoved: () => void): Operation[] => [
    {
        text: "Move to Trash",
        onClick: (files, cloud) => moveToTrash(files, cloud, onMoved),
        disabled: (files: File[], cloud: SDUCloud) => (!allFilesHasAccessRight("WRITE", files) || files.some(f => isFixedFolder(f.path, cloud.homeFolder)) || files.every(({ path }) => inTrashDir(path, cloud))),
        icon: "trash",
        color: "red"
    },
    {
        text: "Clear Trash",
        onClick: (files, cloud) => clearTrash(cloud, onMoved),
        disabled: (files, cloud) => !files.every(f => UF.addTrailingSlash(f.path) === cloud.trashFolder) && !files.every(f => getParentPath(f.path) === cloud.trashFolder),
        icon: "trash",
        color: "red"
    },
    {
        text: "Delete Files",
        onClick: (files, cloud) => batchDeleteFiles(files, cloud, onMoved),
        disabled: (files, cloud) => !files.every(f => getParentPath(f.path) === cloud.trashFolder),
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
                    (projectPath: string) => history.push(projectViewPage(projectPath))
                ),
            disabled: (files: File[], cloud: SDUCloud) =>
                files.length !== 1 || !canBeProject(files, cloud.homeFolder) || 
                !allFilesHasAccessRight("READ", files) || !allFilesHasAccessRight("WRITE", files),
            icon: "projects",
            color: "blue"
        },
    }
];

export const fileInfoPage = (path: string): string => `/files/info?path=${encodeURIComponent(path)}`;
export const fileTablePage = (path: string): string => `/files?path=${encodeURIComponent(path)}`;

export function AllFileOperations(stateless: boolean, fileSelectorOps: MoveCopyOperations | false, onDeleted: (() => void) | false, history: History | false) {
    const stateLessOperations = stateless ? StateLessOperations() : [];
    const fileSelectorOperations = !!fileSelectorOps ? FileSelectorOperations(fileSelectorOps) : [];
    const deleteOperation = !!onDeleted ? MoveFileToTrashOperation(onDeleted) : [];
    const historyOperations = !!history ? HistoryFilesOperations(history) : [];
    return [...stateLessOperations, ...fileSelectorOperations, ...deleteOperation, ...historyOperations];
};

export const filepathQuery = (path: string, page: number, itemsPerPage: number, order: SortOrder = SortOrder.ASCENDING, sortBy: SortBy = SortBy.PATH): string =>
    `files?path=${encodeURIComponent(path)}&itemsPerPage=${itemsPerPage}&page=${page}&order=${encodeURIComponent(order)}&sortBy=${encodeURIComponent(sortBy)}`;

// FIXME: UF.removeTrailingSlash(path) shouldn't be unnecessary, but otherwise causes backend issues
export const fileLookupQuery = (path: string, itemsPerPage: number = 25, order: SortOrder = SortOrder.DESCENDING, sortBy: SortBy = SortBy.PATH): string =>
    `files/lookup?path=${encodeURIComponent(UF.removeTrailingSlash(path))}&itemsPerPage=${itemsPerPage}&order=${encodeURIComponent(order)}&sortBy=${encodeURIComponent(sortBy)}`;

export const advancedFileSearch = "/file-search/advanced"

export const recentFilesQuery = "/files/stats/recent";

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
    sensitivityLevel: "OPEN_ACCESS",
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
        const file = page.items.find((file: File) => file.path === f.path)!;
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


export const clearTrash = (cloud: SDUCloud, callback: () => void) =>
    clearTrashSwal().then((result: any) => {
        if (result.dismiss) {
            return;
        } else {
            cloud.post("/files/trash/clear", {}).then(() => callback ? callback() : null);
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

export const getFilenameFromPath = (path: string): string => path.split("/").filter(p => p).pop()!;

export const downloadFiles = (files: File[], cloud: SDUCloud) =>
    files.map(f => f.path).forEach(p =>
        cloud.createOneTimeTokenWithPermission("files.download:read").then((token: string) => {
            const element = document.createElement("a");
            element.setAttribute("href", `/api/files/download?path=${encodeURIComponent(p)}&token=${encodeURIComponent(token)}`);
            element.style.display = "none";
            document.body.appendChild(element);
            element.click();
            document.body.removeChild(element);
        }));


export const fetchFileContent = (path: string, cloud: SDUCloud) =>
    cloud.createOneTimeTokenWithPermission("files.download:read").then((token: string) =>
        fetch(`/api/files/download?path=${encodeURIComponent(path)}&token=${encodeURIComponent(token)}`)
    );

export const fileSizeToString = (bytes: number): string => {
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

export const shareFiles = (files: File[], cloud: SDUCloud) =>
    UF.shareSwal().then((input) => {
        if (input.dismiss) return;
        const rights: string[] = [];
        if (UF.isElementChecked("read-swal")) rights.push("READ");
        if (UF.isElementChecked("write-swal")) rights.push("WRITE");
        if (UF.isElementChecked("execute-swal")) rights.push("EXECUTE");
        let i = 0;
        files.map((f) => f.path).forEach((path, i, paths) => {
            const body = {
                sharedWith: input.value,
                path,
                rights
            };
            cloud.put(`/shares/`, body).then(() => { if (++i === paths.length) UF.successNotification("Files shared successfully") })
                .catch(({ response }) => UF.failureNotification(`${response.why}`));
        });
    }); // FIXME Error handling

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

export const moveToTrash = (files: File[], cloud: SDUCloud, callback: () => void) => {
    const paths = files.map(f => f.path);
    moveToTrashSwal(paths).then((result: any) => {
        if (result.dismiss) {
            return;
        } else {
            cloud.post("/files/trash/", { files: paths })
                .then(() => callback())
                .catch(({ request }) => { UF.failureNotification("An error occurred moving to trash"); callback() });
        }
    })
};

export const batchDeleteFiles = (files: File[], cloud: SDUCloud, callback: () => void) => {
    const paths = files.map(f => f.path);
    deletionSwal(paths).then((result: any) => {
        if (result.dismiss) {
            return;
        } else {
            let i = 0;
            paths.forEach(path => {
                cloud.delete("/files", { path }).then(() => ++i === paths.length ? callback() : null)
                    .catch(() => i++);
            });
        }
    })
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

export const moveFile = (oldPath: string, newPath: string, cloud: SDUCloud, onSuccess: () => void) =>
    cloud.post(`/files/move?path=${encodeURIComponent(oldPath)}&newPath=${encodeURIComponent(newPath)}`).then(({ request }) => {
        if (UF.inSuccessRange(request.status)) {
            onSuccess()
        }
    }).catch(() => UF.failureNotification("An error ocurred trying to rename the file."));

export const createFolder = (path: string, cloud: SDUCloud, onSuccess: () => void) =>
    cloud.post("/files/directory", { path }).then(({ request }) => {
        if (UF.inSuccessRange(request.status)) {
            onSuccess()
        }
    }).catch(() => UF.failureNotification("An error ocurred trying to creating the file."));


const inTrashDir = (path: string, cloud: SDUCloud): boolean => getParentPath(path) === cloud.trashFolder;