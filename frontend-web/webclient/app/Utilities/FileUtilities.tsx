import Cloud from "Authentication/lib";
import { File, MoveCopyOperations, FileOperation, SortOrder, SortBy } from "Files";
import { Page } from "Types";
import { History } from "history";
import * as UF from "UtilityFunctions";


export function copy(files: File[], operations: MoveCopyOperations, cloud: Cloud): void {
    let i = 0;
    operations.setDisallowedPaths(files.map(f => f.path));
    operations.showFileSelector(true);
    operations.setFileSelectorCallback((file) => {
        const newPath = file.path;
        operations.showFileSelector(false);
        operations.setFileSelectorCallback(null);
        operations.setDisallowedPaths([]);
        files.forEach((f) => {
            const currentPath = f.path;
            cloud.get(`/files/stat?path=${newPath}/${UF.getFilenameFromPath(currentPath)}`).then(({ request }) => {
                if (request.status === 200) UF.failureNotification("File already exists");
            }).catch(({ request }) => {
                if (request.status === 404) {
                    const newPathForFile = `${newPath}/${UF.getFilenameFromPath(currentPath)}`;
                    cloud.post(`/files/copy?path=${currentPath}&newPath=${newPathForFile}`).then(() => i++).catch(() => UF.failureNotification(`An error occured copying file ${currentPath}.`)).then(() => {
                        if (i === files.length) {
                            operations.fetchPageFromPath(newPathForFile);
                            UF.successNotification("File copied.");
                        }
                    });
                }
                else {
                    UF.failureNotification(`An error occurred, please try again later.`);
                }
            }); // End Catch
        }); // End forEach
    }); // End Callback
};

export function move(files: File[], operations: MoveCopyOperations, cloud: Cloud): void {
    operations.showFileSelector(true);
    const parentPath = UF.getParentPath(files[0].path);
    operations.setDisallowedPaths([parentPath].concat(files.map(f => f.path)));
    operations.setFileSelectorCallback((file) => {
        const newPath = file.path;
        files.forEach((f) => {
            const currentPath = f.path;
            const newPathForFile = `${newPath}/${UF.getFilenameFromPath(currentPath)}`;
            cloud.post(`/files/move?path=${currentPath}&newPath=${newPathForFile}`).then(() => {
                const fromPath = UF.getFilenameFromPath(currentPath);
                const toPath = UF.replaceHomeFolder(newPathForFile, cloud.homeFolder);
                UF.successNotification(`${fromPath} moved to ${toPath}`);
                operations.fetchPageFromPath(newPathForFile);
            }).catch(() => UF.failureNotification("An error occurred, please try again later"));
            operations.showFileSelector(false);
            operations.setFileSelectorCallback(null);
            operations.setDisallowedPaths([]);
        });
    })
};

export const startRenamingFiles = (files: File[], page: Page<File>) => {
    const paths = files.map(it => it.path);
    // FIXME Very slow
    page.items.forEach(it => {
        if (paths.some(p => p === it.path)) {
            it.beingRenamed = true
        }
    });
    return page;
}

/**
 * @returns Share and Download operations for files
 */
export const StateLessOperations = (): FileOperation[] => [
    { text: "Share", onClick: (files: File[], cloud: Cloud) => UF.shareFiles(files, cloud), disabled: (files: File[], cloud: Cloud) => false, icon: "share alternate", color: null },
    { text: "Download", onClick: (files: File[], cloud: Cloud) => UF.downloadFiles(files, cloud), disabled: (files: File[], cloud: Cloud) => !UF.downloadAllowed(files), icon: "download", color: null }
];

/**
 * @returns Move and Copy operations for files
 */
export const FileSelectorOperations = (fileSelectorOperations: MoveCopyOperations): FileOperation[] => [
    { text: "Copy", onClick: (files: File[], cloud: Cloud) => copy(files, fileSelectorOperations, cloud), disabled: (files: File[], cloud: Cloud) => UF.getCurrentRights(files, cloud).rightsLevel < 3, icon: "copy", color: null },
    { text: "Move", onClick: (files: File[], cloud: Cloud) => move(files, fileSelectorOperations, cloud), disabled: (files: File[], cloud: Cloud) => UF.getCurrentRights(files, cloud).rightsLevel < 3 || files.some(f => UF.isFixedFolder(f.path, cloud.homeFolder)), icon: "move", color: null }
];

/**
 * 
 * @param onDeleted To be called on completed deletion of files
 * @returns the Delete operation
 */
export const DeleteFileOperation = (onDeleted: () => void): FileOperation[] => [
    { text: "Delete", onClick: (files: File[], cloud: Cloud) => UF.batchDeleteFiles(files, cloud, onDeleted), disabled: (files: File[], cloud: Cloud) => UF.getCurrentRights(files, cloud).rightsLevel < 3 || files.some(f => UF.isFixedFolder(f.path, cloud.homeFolder)), icon: "trash", color: "red" }
];

/**
 * @returns Properties and Project Operations for files.
 */
export const HistoryFilesOperations = (history: History): FileOperation[] => [
    { text: "Properties", onClick: (files: File[], cloud: Cloud) => history.push(`/fileInfo/${files[0].path}/`), disabled: (files: File[], cloud: Cloud) => files.length !== 1, icon: "settings", color: "blue" },
    {
        predicate: (files: File[], cloud: Cloud) => UF.isProject(files[0]),
        onTrue: { text: "Edit Project", onClick: (files: File[], cloud: Cloud) => history.push(`/metadata/${files[0].path}/`), disabled: (files: File[], cloud: Cloud) => files.length !== 1 && !UF.canBeProject(files, cloud.homeFolder), icon: "group", color: "blue" },
        onFalse: { text: "Create Project", onClick: (files: File[], cloud: Cloud) => UF.createProject(files[0].path, cloud, (projectPath: string) => history.push(`/metadata/${projectPath}`)), disabled: (files: File[], cloud: Cloud) => files.length !== 1 && !UF.canBeProject(files, cloud.homeFolder), icon: "group", color: "blue" },
    }
];

export function AllFileOperations(stateless?: boolean, fileSelectorOps?: MoveCopyOperations, onDeleted?: () => void, history?: History) {
    const stateLessOperations = stateless ? StateLessOperations() : [];
    const fileSelectorOperations = !!fileSelectorOps ? FileSelectorOperations(fileSelectorOps) : [];
    const deleteOperation = !!onDeleted ? DeleteFileOperation(onDeleted) : [];
    const historyOperations = !!history ? HistoryFilesOperations(history) : [];
    return [...stateLessOperations, ...fileSelectorOperations, ...deleteOperation, ...historyOperations];
};

export const filepathQuery = (path: string, page: number, itemsPerPage: number, order: SortOrder = SortOrder.DESCENDING, sortBy: SortBy = SortBy.PATH): string =>
    `files?path=${path}&itemsPerPage=${itemsPerPage}&page=${page}&order=${order}&sortBy=${sortBy}`;

export const fileLookupQuery = (path: string, itemsPerPage: number, order: SortOrder = SortOrder.DESCENDING, sortBy: SortBy = SortBy.PATH): string =>
    `files/lookup?path=${path}&itemsPerPage=${itemsPerPage}&order=${order}&sortBy=${sortBy}`;



export const newMockFolder = (): File => ({
    type: "DIRECTORY",
    path: "",
    createdAt: new Date().getMilliseconds(),
    modifiedAt: new Date().getMilliseconds(),
    ownerName: "",
    size: 0,
    acl: [],
    favorited: false,
    sensitivityLevel: "OPEN ACCESS",
    isChecked: false,
    beingRenamed: true,
    link: false,
    annotations: [],
    isMockFolder: true
});