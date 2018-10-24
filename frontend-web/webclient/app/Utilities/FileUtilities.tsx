import Cloud from "Authentication/lib";
import { File, MoveCopyOperations, Operation, SortOrder, SortBy, Annotation, AnnotationsMap, PredicatedOperation } from "Files";
import { Page } from "Types";
import { History } from "history";
import swal from "sweetalert2";
import * as UF from "UtilityFunctions";

export function copy(files: File[], operations: MoveCopyOperations, cloud: Cloud): void {
    let i = 0;
    operations.setDisallowedPaths(files.map(f => f.path));
    operations.showFileSelector(true);
    operations.setFileSelectorCallback((file) => {
        const newPath = file.path;
        operations.showFileSelector(false);
        operations.setFileSelectorCallback(undefined);
        operations.setDisallowedPaths([]);
        files.forEach((f) => {
            const currentPath = f.path;
            const newPathForFile = `${UF.removeTrailingSlash(newPath)}/${getFilenameFromPath(currentPath)}`;
            cloud.post(`/files/copy?path=${currentPath}&newPath=${newPathForFile}`).then(() => i++)
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

export function move(files: File[], operations: MoveCopyOperations, cloud: Cloud): void {
    operations.showFileSelector(true);
    const parentPath = getParentPath(files[0].path);
    operations.setDisallowedPaths([parentPath].concat(files.map(f => f.path)));
    operations.setFileSelectorCallback((file) => {
        const newPath = file.path;
        files.forEach((f) => {
            const currentPath = f.path;
            const newPathForFile = `${UF.removeTrailingSlash(newPath)}/${getFilenameFromPath(currentPath)}`;
            cloud.post(`/files/move?path=${currentPath}&newPath=${newPathForFile}`).then(() => {
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
    page.items.forEach((file: File) => {
        if (paths.some((p: string) => p === file.path)) {
            file.beingRenamed = true
        }
    });
    return page;
}

type AccessRight = "READ" | "WRITE" | "EXECUTE";
const hasAccess = (accessRight: AccessRight, file: File) => file.acl.every(acl => acl.rights.includes(accessRight));
const allFilesHasAccessRight = (accessRight: AccessRight, files: File[]) => files.every(f => hasAccess(accessRight, f));

/**
 * @returns Share and Download operations for files
 */
export const StateLessOperations = (): Operation[] => [
    { text: "Share", onClick: (files: File[], cloud: Cloud) => shareFiles(files, cloud), disabled: (files: File[], cloud: Cloud) => false, icon: "share alternate", color: undefined },
    { text: "Download", onClick: (files: File[], cloud: Cloud) => downloadFiles(files, cloud), disabled: (files: File[], cloud: Cloud) => !UF.downloadAllowed(files), icon: "download", color: undefined }
];

/**
 * @returns Move and Copy operations for files
 */
export const FileSelectorOperations = (fileSelectorOperations: MoveCopyOperations): Operation[] => [
    { text: "Copy", onClick: (files: File[], cloud: Cloud) => copy(files, fileSelectorOperations, cloud), disabled: (files: File[], cloud: Cloud) => !allFilesHasAccessRight("WRITE", files), icon: "copy outline", color: undefined },
    { text: "Move", onClick: (files: File[], cloud: Cloud) => move(files, fileSelectorOperations, cloud), disabled: (files: File[], cloud: Cloud) => !allFilesHasAccessRight("WRITE", files) || files.some(f => isFixedFolder(f.path, cloud.homeFolder)), icon: "move", color: undefined }
];

/**
 * 
 * @param onDeleted To be called on completed deletion of files
 * @returns the Delete operation in an array
 */
export const DeleteFileOperation = (onDeleted: () => void): Operation[] => [
    { text: "Delete", onClick: (files: File[], cloud: Cloud) => batchDeleteFiles(files, cloud, onDeleted), disabled: (files: File[], cloud: Cloud) => !allFilesHasAccessRight("WRITE", files) || files.some(f => isFixedFolder(f.path, cloud.homeFolder)), icon: "trash alternate outline", color: "red" }
];

/**
 * @returns Properties and Project Operations for files.
 */
export const HistoryFilesOperations = (history: History): [Operation, PredicatedOperation] => [
    { text: "Properties", onClick: (files: File[], cloud: Cloud) => history.push(`/fileInfo/${files[0].path}/`), disabled: (files: File[], cloud: Cloud) => files.length !== 1, icon: "settings", color: "blue" },
    {
        predicate: (files: File[], cloud: Cloud) => isProject(files[0]),
        onTrue: { text: "Edit Project", onClick: (files: File[], cloud: Cloud) => history.push(`/metadata/${files[0].path}/`), disabled: (files: File[], cloud: Cloud) => !canBeProject(files, cloud.homeFolder), icon: "group", color: "blue" },
        onFalse: { text: "Create Project", onClick: (files: File[], cloud: Cloud) => UF.createProject(files[0].path, cloud, (projectPath: string) => history.push(`/metadata/${projectPath}`)), disabled: (files: File[], cloud: Cloud) => files.length !== 1 || !canBeProject(files, cloud.homeFolder), icon: "group", color: "blue" },
    }
];

export function AllFileOperations(stateless: boolean, fileSelectorOps: MoveCopyOperations | false, onDeleted: (() => void) | false, history: History | false) {
    const stateLessOperations = stateless ? StateLessOperations() : [];
    const fileSelectorOperations = !!fileSelectorOps ? FileSelectorOperations(fileSelectorOps) : [];
    const deleteOperation = !!onDeleted ? DeleteFileOperation(onDeleted) : [];
    const historyOperations = !!history ? HistoryFilesOperations(history) : [];
    return [...stateLessOperations, ...fileSelectorOperations, ...deleteOperation, ...historyOperations];
};

export const filepathQuery = (path: string, page: number, itemsPerPage: number, order: SortOrder = SortOrder.ASCENDING, sortBy: SortBy = SortBy.PATH): string =>
    `files?path=${path}&itemsPerPage=${itemsPerPage}&page=${page}&order=${order}&sortBy=${sortBy}`;

// FIXME: UF.removeTrailingSlash(path) shouldn't be unnecessary, but otherwise causes backend issues
export const fileLookupQuery = (path: string, itemsPerPage: number = 25, order: SortOrder = SortOrder.DESCENDING, sortBy: SortBy = SortBy.PATH): string =>
    `files/lookup?path=${UF.removeTrailingSlash(path)}&itemsPerPage=${itemsPerPage}&order=${order}&sortBy=${sortBy}`;

export const fileSearchQuery = (search: string, pageNumber: number, itemsPerPage: number) =>
    `/file-search?query=${search}&page=${pageNumber}&itemsPerPage=${itemsPerPage}`


export const newMockFolder = (path: string = "", beingRenamed: boolean = true): File => ({
    fileType: "DIRECTORY",
    path,
    createdAt: new Date().getMilliseconds(),
    modifiedAt: new Date().getMilliseconds(),
    ownerName: "",
    size: 0,
    acl: [],
    favorited: false,
    sensitivityLevel: "OPEN ACCESS",
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
    const existingName = filePaths.some((it) => it === path);
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
    ].some((it) => UF.removeTrailingSlash(it) === filePath)
};

/**
 * Used for favoriting files based on a path and page consisting of files.
 * @param {Page<File>} page The page of files to be searched through
 * @param {File[]} filesToFavorite Files to be favorited
 * @param {Cloud} cloud The instance of a Cloud object used for requests
 * @returns {Page<File>} The page of files with the file favorited
 */
export const favoriteFileFromPage = (page: Page<File>, filesToFavorite: File[], cloud: Cloud): Page<File> => {
    filesToFavorite.forEach(f => {
        const file = page.items.find((file: File) => file.path === f.path);
        if (file) favoriteFile(file, cloud);
    });
    return page;
};

/**
 * Used to favorite/defavorite a file based on its current state.
 * @param {File} file The single file to be favorited
 * @param {Cloud} cloud The cloud instance used to changed the favorite state for the file
 */
export const favoriteFile = (file: File, cloud: Cloud): void => {
    file.favorited = !file.favorited;
    if (file.favorited)
        cloud.post(`/files/favorite?path=${file.path}`, {}); // FIXME: Error handling
    else
        cloud.delete(`/files/favorite?path=${file.path}`, {}); // FIXME: Error handling
}

export const canBeProject = (files: File[], homeFolder: string): boolean =>
    files.length === 1 && files.every((f) => isDirectory(f)) && !isFixedFolder(files[0].path, homeFolder) && !isLink(files[0]);

export const previewSupportedExtension = (path: string) => false;

export const isProject = (file: File) => file.fileType === "DIRECTORY" && file.annotations.some(it => it === "P");

export const toFileText = (selectedFiles: File[]): string =>
    `${selectedFiles.length} file${selectedFiles.length > 1 ? "s" : ""} selected.`

export const isLink = (file: File) => file.link;
export const isDirectory = (file: File) => file.fileType === "DIRECTORY";
export const replaceHomeFolder = (path: string, homeFolder: string) => path.replace(UF.addTrailingSlash(homeFolder), "Home/");

export const showFileDeletionPrompt = (filePath: string, cloud: Cloud, callback: () => void) =>
    deletionSwal([filePath]).then((result: any) => {
        if (result.dismiss) {
            return;
        } else {
            cloud.delete("/files", { path: filePath }).then(() => callback ? callback() : null);
        }
    });

export const getParentPath = (path: string): string => {
    if (!path) {
        return "";
    }
    let splitPath = path.split("/");
    splitPath = splitPath.filter(path => path);
    let parentPath = "/";
    for (let i = 0; i < splitPath.length - 1; i++) {
        parentPath += splitPath[i] + "/";
    }
    return parentPath;
};

export const getFilenameFromPath = (path: string): string => {
    const p = path.split("/").filter(p => p).pop();
    if (p) return p; else return "";
}

export const downloadFiles = (files: File[], cloud: Cloud) =>
    files.map(f => f.path).forEach(p =>
        cloud.createOneTimeTokenWithPermission("downloadFile,irods").then((token: string) => {
            const element = document.createElement("a");
            element.setAttribute("href", `/api/files/download?path=${encodeURI(p)}&token=${encodeURI(token)}`);
            element.style.display = "none";
            document.body.appendChild(element);
            element.click();
            document.body.removeChild(element);
        }));


export const fetchFileContent = (path: string, cloud: Cloud) =>
    cloud.createOneTimeTokenWithPermission("downloadFile,irods").then((token: string) =>
        fetch(`/api/files/download?path=${encodeURI(path)}&token=${encodeURI(token)}`)
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

export const shareFiles = (files: File[], cloud: Cloud) =>
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
            cloud.put(`/shares/`, body).then((): void => { if (++i === paths.length) UF.successNotification("Files shared successfully") })
                .catch(() => UF.failureNotification(`${getFilenameFromPath(path)} could not be shared at this time. Please try again later.`));
        });
    }); // FIXME Error handling

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

export const batchDeleteFiles = (files: File[], cloud: Cloud, callback: () => void) => {
    const paths = files.map(f => f.path);
    deletionSwal(paths).then((result: any) => {
        if (result.dismiss) {
            return;
        } else {
            let i = 0;
            paths.forEach((p) => {
                cloud.delete("/files", { path: p }).then(() => ++i === paths.length ? callback() : null)
                    .catch(() => i++);
            });
        }
    })
};

export const moveFile = (oldPath: string, newPath: string, cloud: Cloud, onSuccess: () => void) =>
    cloud.post(`/files/move?path=${oldPath}&newPath=${newPath}`).then(({ request }) => {
        if (UF.inSuccessRange(request.status)) {
            onSuccess()
        }
    }).catch(() => UF.failureNotification("An error ocurred trying to rename the file."));

export const createFolder = (path: string, cloud: Cloud, onSuccess: () => void) =>
    cloud.post("/files/directory", { path }).then(({ request }) => {
        if (UF.inSuccessRange(request.status)) {
            onSuccess()
        }
    }).catch(() => UF.failureNotification("An error ocurred trying to creating the file."));

export const annotationToString = (annotation: Annotation) => AnnotationsMap[annotation];